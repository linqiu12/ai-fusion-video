package com.stonewu.fusion.service.search;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.entity.novel.Novel;
import com.stonewu.fusion.entity.novel.NovelChapter;
import com.stonewu.fusion.entity.search.SearchOutboxEvent;
import com.stonewu.fusion.mapper.search.SearchOutboxEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * MySQL Outbox 到 Elasticsearch 的可靠投递器。
 *
 * <p>章节事务只负责写 Outbox；ES 暂时不可用不会丢索引任务，调度器按指数退避重试。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexOutboxService {

    private static final int MAX_ATTEMPTS = 20;

    private final SearchOutboxEventMapper eventMapper;
    private final ElasticsearchNovelSearchService searchService;

    @Transactional
    public void enqueueChapter(Novel novel, NovelChapter chapter, String contentHash) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("projectId", novel.getProjectId());
        payload.put("novelId", novel.getId());
        payload.put("chapterId", chapter.getId());
        payload.put("chapterNo", chapter.getChapterNo());
        payload.put("revisionNo", chapter.getCurrentRevision());
        payload.put("title", chapter.getTitle());
        payload.put("summary", chapter.getSummary());
        payload.put("content", chapter.getContent());
        payload.put("contentSha256", contentHash);
        payload.put("updatedAt", LocalDateTime.now().toInstant(ZoneOffset.UTC).toString());
        eventMapper.insert(SearchOutboxEvent.builder()
                .aggregateType("NOVEL_CHAPTER")
                .aggregateId(chapter.getId())
                .eventType("UPSERT")
                .payloadJson(JSONUtil.toJsonStr(payload))
                .status("PENDING")
                .attempts(0)
                .nextRetryAt(LocalDateTime.now())
                .build());
    }

    @Scheduled(fixedDelayString = "${app.search.elasticsearch.outbox-delay:2000}")
    public void dispatch() {
        if (!searchService.enabled()) return;
        searchService.ensureIndex();
        List<SearchOutboxEvent> events = eventMapper.selectList(new LambdaQueryWrapper<SearchOutboxEvent>()
                .in(SearchOutboxEvent::getStatus, List.of("PENDING", "FAILED"))
                .le(SearchOutboxEvent::getNextRetryAt, LocalDateTime.now())
                .lt(SearchOutboxEvent::getAttempts, MAX_ATTEMPTS)
                .orderByAsc(SearchOutboxEvent::getId)
                .last("LIMIT 20"));
        events.forEach(this::dispatchOne);
    }

    @Transactional
    protected void dispatchOne(SearchOutboxEvent event) {
        event.setStatus("PROCESSING");
        event.setAttempts(event.getAttempts() + 1);
        eventMapper.updateById(event);
        try {
            if ("NOVEL_CHAPTER".equals(event.getAggregateType()) && "UPSERT".equals(event.getEventType())) {
                searchService.indexChapter(event.getAggregateId(), event.getPayloadJson());
            }
            event.setStatus("COMPLETED");
            event.setLastError(null);
            event.setNextRetryAt(null);
        } catch (RuntimeException failure) {
            event.setStatus("FAILED");
            event.setLastError(abbreviate(failure.getMessage(), 1000));
            long seconds = Math.min(300L, 1L << Math.min(8, event.getAttempts()));
            event.setNextRetryAt(LocalDateTime.now().plusSeconds(seconds));
            log.warn("小说索引任务失败，将重试: eventId={}, attempts={}, error={}",
                    event.getId(), event.getAttempts(), event.getLastError());
        }
        eventMapper.updateById(event);
    }

    private String abbreviate(String value, int limit) {
        if (value == null || value.length() <= limit) return value;
        return value.substring(0, limit);
    }
}
