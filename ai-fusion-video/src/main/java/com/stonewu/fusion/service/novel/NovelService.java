package com.stonewu.fusion.service.novel;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.audit.ContentProvenance;
import com.stonewu.fusion.entity.novel.Novel;
import com.stonewu.fusion.entity.novel.NovelChapter;
import com.stonewu.fusion.entity.novel.NovelChapterRevision;
import com.stonewu.fusion.mapper.audit.ContentProvenanceMapper;
import com.stonewu.fusion.mapper.novel.NovelChapterMapper;
import com.stonewu.fusion.mapper.novel.NovelChapterRevisionMapper;
import com.stonewu.fusion.mapper.novel.NovelMapper;
import com.stonewu.fusion.service.audit.AuditEventService;
import com.stonewu.fusion.service.audit.ContentHashService;
import com.stonewu.fusion.service.project.ProjectService;
import com.stonewu.fusion.service.risk.ContentRiskService;
import com.stonewu.fusion.service.search.SearchIndexOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** 小说聚合服务，集中维护权限、版本、来源、风控与审计的一致性。 */
@Service
@RequiredArgsConstructor
public class NovelService {

    private final NovelMapper novelMapper;
    private final NovelChapterMapper chapterMapper;
    private final NovelChapterRevisionMapper revisionMapper;
    private final ContentProvenanceMapper provenanceMapper;
    private final ProjectService projectService;
    private final ContentRiskService riskService;
    private final ContentHashService hashService;
    private final AuditEventService auditEventService;
    private final SearchIndexOutboxService searchIndexOutboxService;

    @Cacheable(value = "novel", key = "'project:' + #projectId + ':' + #userId")
    public Novel getByProject(Long projectId, Long userId) {
        requireProjectAccess(projectId, userId);
        return novelMapper.selectOne(new LambdaQueryWrapper<Novel>()
                .eq(Novel::getProjectId, projectId)
                .last("LIMIT 1"));
    }

    @Cacheable(value = "novelChapter", key = "#novelId + ':' + #userId")
    public List<NovelChapter> listChapters(Long novelId, Long userId) {
        Novel novel = requireNovel(novelId);
        requireProjectAccess(novel.getProjectId(), userId);
        return chapterMapper.selectList(new LambdaQueryWrapper<NovelChapter>()
                .eq(NovelChapter::getNovelId, novelId)
                .orderByAsc(NovelChapter::getChapterNo));
    }

    public NovelChapter getChapter(Long chapterId, Long userId) {
        NovelChapter chapter = requireChapter(chapterId);
        Novel novel = requireNovel(chapter.getNovelId());
        requireProjectAccess(novel.getProjectId(), userId);
        return chapter;
    }

    public List<NovelChapterRevision> listRevisions(Long chapterId, Long userId) {
        getChapter(chapterId, userId);
        return revisionMapper.selectList(new LambdaQueryWrapper<NovelChapterRevision>()
                .eq(NovelChapterRevision::getChapterId, chapterId)
                .orderByDesc(NovelChapterRevision::getRevisionNo));
    }

    @Transactional
    @CacheEvict(value = {"novel", "novelChapter"}, allEntries = true)
    public Novel createNovel(Long projectId, Long userId, String title,
                             String genre, String synopsis, String worldSetting) {
        requireProjectAccess(projectId, userId);
        if (getByProject(projectId, userId) != null) {
            throw new BusinessException(400, "该项目已创建小说");
        }
        riskService.requireGeneratable(userId, projectId, "NOVEL", "INPUT",
                String.join("\n", safe(title), safe(synopsis), safe(worldSetting)));
        Novel novel = Novel.builder()
                .projectId(projectId)
                .title(requireText(title, "小说标题"))
                .genre(StrUtil.trim(genre))
                .synopsis(StrUtil.trim(synopsis))
                .worldSetting(StrUtil.trim(worldSetting))
                .status("DRAFT")
                .currentRevision(1)
                .build();
        novelMapper.insert(novel);
        auditEventService.append(userId, projectId, "NOVEL_CREATED", "NOVEL",
                String.valueOf(novel.getId()), "CREATE", "SUCCESS", null,
                hashService.sha256(novel.getTitle() + safe(novel.getSynopsis())), Map.of("genre", safe(genre)));
        return novel;
    }

    @Transactional
    @CacheEvict(value = "novelChapter", allEntries = true)
    public NovelChapter saveChapter(Long novelId, Long chapterId, Long userId,
                                    Integer chapterNo, String title, String content,
                                    String summary, String sourceType, Long modelId,
                                    String skillId, String skillVersion, String promptSnapshot) {
        Novel novel = requireNovel(novelId);
        requireProjectAccess(novel.getProjectId(), userId);
        String safeContent = content == null ? "" : content;
        riskService.requireGeneratable(userId, novel.getProjectId(), "CHAPTER", "OUTPUT",
                requireText(title, "章节标题") + "\n" + safeContent);

        NovelChapter chapter;
        String beforeHash = null;
        int revisionNo;
        if (chapterId == null) {
            chapter = NovelChapter.builder()
                    .novelId(novelId)
                    .chapterNo(requireChapterNo(chapterNo))
                    .title(title.trim())
                    .content(safeContent)
                    .summary(StrUtil.trim(summary))
                    .status("DRAFT")
                    .currentRevision(1)
                    .wordCount(codePointCount(safeContent))
                    .build();
            chapterMapper.insert(chapter);
            revisionNo = 1;
        } else {
            chapter = requireChapter(chapterId);
            if (!novelId.equals(chapter.getNovelId())) {
                throw new BusinessException(400, "章节不属于当前小说");
            }
            beforeHash = hashService.sha256(chapter.getContent());
            revisionNo = chapter.getCurrentRevision() + 1;
            chapter.setChapterNo(requireChapterNo(chapterNo));
            chapter.setTitle(title.trim());
            chapter.setContent(safeContent);
            chapter.setSummary(StrUtil.trim(summary));
            chapter.setCurrentRevision(revisionNo);
            chapter.setWordCount(codePointCount(safeContent));
            chapterMapper.updateById(chapter);
        }

        String contentHash = hashService.sha256(safeContent);
        String normalizedSource = normalizeSourceType(sourceType);
        revisionMapper.insert(NovelChapterRevision.builder()
                .chapterId(chapter.getId())
                .revisionNo(revisionNo)
                .title(chapter.getTitle())
                .content(safeContent)
                .contentSha256(contentHash)
                .sourceType(normalizedSource)
                .modelId(modelId)
                .skillId(skillId)
                .skillVersion(skillVersion)
                .promptSnapshot(promptSnapshot)
                .operatorUserId(userId)
                .build());
        provenanceMapper.insert(ContentProvenance.builder()
                .contentType("CHAPTER")
                .contentId(chapter.getId())
                .revisionNo(revisionNo)
                .sourceType(normalizedSource)
                .sourceRef("chapter-revision:" + revisionNo)
                .contentSha256(contentHash)
                .modelId(modelId)
                .promptSha256(promptSnapshot == null ? null : hashService.sha256(promptSnapshot))
                .operatorUserId(userId)
                .metadataJson(JSONUtil.toJsonStr(Map.of("skillId", safe(skillId),
                        "skillVersion", safe(skillVersion))))
                .build());
        searchIndexOutboxService.enqueueChapter(novel, chapter, contentHash);
        auditEventService.append(userId, novel.getProjectId(), "CHAPTER_REVISION_SAVED", "CHAPTER",
                String.valueOf(chapter.getId()), chapterId == null ? "CREATE" : "UPDATE", "SUCCESS",
                beforeHash, contentHash, Map.of("revisionNo", revisionNo, "sourceType", normalizedSource));
        return chapter;
    }

    private Novel requireNovel(Long novelId) {
        Novel novel = novelMapper.selectById(novelId);
        if (novel == null) {
            throw new BusinessException(404, "小说不存在");
        }
        return novel;
    }

    private NovelChapter requireChapter(Long chapterId) {
        NovelChapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BusinessException(404, "章节不存在");
        }
        return chapter;
    }

    private void requireProjectAccess(Long projectId, Long userId) {
        if (!projectService.canAccessProject(projectId, userId)) {
            throw new BusinessException(403, "无权访问该项目");
        }
    }

    private String requireText(String value, String label) {
        if (StrUtil.isBlank(value)) {
            throw new BusinessException(400, label + "不能为空");
        }
        return value.trim();
    }

    private Integer requireChapterNo(Integer value) {
        if (value == null || value < 1) {
            throw new BusinessException(400, "章节序号必须大于 0");
        }
        return value;
    }

    private String normalizeSourceType(String sourceType) {
        String normalized = StrUtil.blankToDefault(sourceType, "HUMAN").toUpperCase();
        return switch (normalized) {
            case "HUMAN", "AI_GENERATED", "AI_REWRITTEN", "IMPORTED" -> normalized;
            default -> throw new BusinessException(400, "不支持的内容来源类型");
        };
    }

    private int codePointCount(String text) {
        return text == null ? 0 : text.codePointCount(0, text.length());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
