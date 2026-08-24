package com.stonewu.fusion.service.search;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.config.ElasticsearchProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 小说全文检索适配器。
 *
 * <p>索引协议保持在本模块内，Agent、小说领域和 UI 都只依赖稳定的 SearchHit。
 * 后续加入 dense_vector、RRF 与 reranker 时无需改动业务层。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchNovelSearchService {

    private final ElasticsearchProperties properties;
    private HttpClient client;

    @PostConstruct
    void initialize() {
        client = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
    }

    public boolean enabled() {
        return properties.isEnabled();
    }

    public void ensureIndex() {
        if (!enabled()) return;
        String mapping = """
                {"mappings":{"properties":{
                  "projectId":{"type":"long"},"novelId":{"type":"long"},"chapterId":{"type":"long"},
                  "chapterNo":{"type":"integer"},"revisionNo":{"type":"integer"},
                  "title":{"type":"text"},"summary":{"type":"text"},"content":{"type":"text"},
                  "contentSha256":{"type":"keyword"},"updatedAt":{"type":"date"}
                }}}
                """;
        try {
            send("PUT", "/" + properties.getNovelIndex(), mapping, true);
        } catch (RuntimeException failure) {
            log.warn("Elasticsearch 索引初始化暂不可用，将由 Outbox 重试: {}", failure.getMessage());
        }
    }

    @CacheEvict(value = "novelSearch", allEntries = true)
    public void indexChapter(long chapterId, String payloadJson) {
        requireEnabled();
        send("PUT", "/" + properties.getNovelIndex() + "/_doc/" + chapterId, payloadJson, false);
    }

    @Cacheable(value = "novelSearch", key = "#projectId + ':' + #query + ':' + #limit")
    public List<SearchHit> search(long projectId, String query, int limit) {
        requireEnabled();
        int safeLimit = Math.max(1, Math.min(limit, 50));
        JSONObject request = JSONUtil.createObj()
                .set("size", safeLimit)
                .set("query", JSONUtil.createObj().set("bool", JSONUtil.createObj()
                        .set("filter", List.of(JSONUtil.createObj().set("term",
                                JSONUtil.createObj().set("projectId", projectId))))
                        .set("must", List.of(JSONUtil.createObj().set("multi_match", JSONUtil.createObj()
                                .set("query", query)
                                .set("fields", List.of("title^3", "summary^2", "content")))))))
                .set("highlight", JSONUtil.createObj().set("fields", Map.of("content", Map.of())));
        JSONObject response = JSONUtil.parseObj(send("POST",
                "/" + properties.getNovelIndex() + "/_search", request.toString(), false));
        JSONArray hits = response.getByPath("hits.hits", JSONArray.class);
        List<SearchHit> result = new ArrayList<>();
        if (hits == null) return result;
        for (Object raw : hits) {
            JSONObject hit = (JSONObject) raw;
            JSONObject source = hit.getJSONObject("_source");
            JSONArray highlights = hit.getByPath("highlight.content", JSONArray.class);
            String excerpt = highlights == null || highlights.isEmpty()
                    ? abbreviate(source.getStr("content"), 260)
                    : highlights.getStr(0);
            result.add(new SearchHit(source.getLong("chapterId"), source.getInt("chapterNo"),
                    source.getStr("title"), excerpt, hit.getDouble("_score", 0D),
                    source.getInt("revisionNo"), source.getStr("contentSha256")));
        }
        return result;
    }

    private String send(String method, String path, String body, boolean allowAlreadyExists) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint() + path))
                .timeout(properties.getRequestTimeout())
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) return response.body();
            if (allowAlreadyExists && response.statusCode() == 400
                    && response.body().contains("resource_already_exists_exception")) return response.body();
            throw new IllegalStateException("Elasticsearch HTTP " + response.statusCode() + ": "
                    + abbreviate(response.body(), 500));
        } catch (IOException e) {
            throw new IllegalStateException("无法连接 Elasticsearch", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Elasticsearch 请求被中断", e);
        }
    }

    private void requireEnabled() {
        if (!enabled()) throw new BusinessException(503, "Elasticsearch 检索未启用");
    }

    private String endpoint() {
        String value = properties.getEndpoint();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String abbreviate(String value, int limit) {
        if (value == null || value.length() <= limit) return value == null ? "" : value;
        return value.substring(0, limit) + "…";
    }

    public record SearchHit(Long chapterId, Integer chapterNo, String title, String excerpt,
                            Double score, Integer revisionNo, String contentSha256) {
    }
}
