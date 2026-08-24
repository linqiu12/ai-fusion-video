package com.stonewu.fusion.service.risk;

import cn.hutool.json.JSONUtil;
import com.stonewu.fusion.entity.risk.AiTraceReport;
import com.stonewu.fusion.mapper.risk.AiTraceReportMapper;
import com.stonewu.fusion.service.audit.ContentHashService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 可解释的 AI 创作痕迹分析器。
 *
 * <p>统计特征只能用于提示编辑风险，不得表述为“由 AI 创作的真实概率”。平台内生成内容
 * 的权威依据仍是 {@code afv_content_provenance} 来源账本。</p>
 */
@Service
@RequiredArgsConstructor
public class AiTraceAnalysisService {

    public static final String DETECTOR_VERSION = "zh-creative-statistical-1.0.0";
    public static final String DISCLAIMER = "该结果是写作特征风险分析，不能作为作者身份、侵权或学术诚信认定依据。";

    private static final List<String> TEMPLATE_PHRASES = List.of(
            "值得注意的是", "不可否认", "与此同时", "总而言之", "不禁让人", "仿佛在诉说");

    private final AiTraceReportMapper reportMapper;
    private final ContentHashService hashService;

    @CacheEvict(value = "aiTraceReport", allEntries = true)
    public AnalysisResult analyze(Long userId, Long projectId, String contentType,
                                  Long contentId, String content) {
        String safeContent = content == null ? "" : content.trim();
        String[] paragraphs = Arrays.stream(safeContent.split("(?:\\r?\\n){2,}"))
                .map(String::trim).filter(text -> !text.isEmpty()).toArray(String[]::new);
        String[] sentences = Arrays.stream(safeContent.split("[。！？!?]+"))
                .map(String::trim).filter(text -> !text.isEmpty()).toArray(String[]::new);

        double sentenceVariation = variation(Arrays.stream(sentences).mapToInt(String::length).toArray());
        double paragraphVariation = variation(Arrays.stream(paragraphs).mapToInt(String::length).toArray());
        long templateHits = TEMPLATE_PHRASES.stream().mapToLong(phrase -> occurrences(safeContent, phrase)).sum();
        double dialogueRatio = safeContent.isEmpty() ? 0D
                : (occurrences(safeContent, "“") + occurrences(safeContent, "\"") / 2D) / Math.max(1D, sentences.length);

        int score = 15;
        if (sentences.length >= 8 && sentenceVariation < 0.35D) score += 25;
        if (paragraphs.length >= 4 && paragraphVariation < 0.30D) score += 20;
        score += (int) Math.min(25, templateHits * 5);
        if (sentences.length >= 12 && dialogueRatio < 0.08D) score += 10;
        score = Math.min(100, score);
        String level = score >= 70 ? "HIGH" : score >= 40 ? "MEDIUM" : "LOW";

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("sentenceLengthVariation", round(sentenceVariation));
        features.put("paragraphLengthVariation", round(paragraphVariation));
        features.put("templatePhraseHits", templateHits);
        features.put("dialogueRatio", round(dialogueRatio));
        features.put("sentenceCount", sentences.length);
        features.put("paragraphCount", paragraphs.length);

        AiTraceReport report = AiTraceReport.builder()
                .userId(userId)
                .projectId(projectId)
                .contentType(contentType)
                .contentId(contentId)
                .contentSha256(hashService.sha256(safeContent))
                .riskScore(score)
                .riskLevel(level)
                .featuresJson(JSONUtil.toJsonStr(features))
                .detectorVersion(DETECTOR_VERSION)
                .build();
        reportMapper.insert(report);
        return new AnalysisResult(report.getId(), score, level, features, DETECTOR_VERSION, DISCLAIMER);
    }

    private double variation(int[] values) {
        if (values.length < 2) return 1D;
        double mean = Arrays.stream(values).average().orElse(0D);
        if (mean == 0D) return 0D;
        double variance = Arrays.stream(values).mapToDouble(value -> Math.pow(value - mean, 2)).average().orElse(0D);
        return Math.sqrt(variance) / mean;
    }

    private long occurrences(String content, String needle) {
        if (content.isEmpty() || needle.isEmpty()) return 0;
        long count = 0;
        int cursor = 0;
        while ((cursor = content.indexOf(needle, cursor)) >= 0) {
            count++;
            cursor += needle.length();
        }
        return count;
    }

    private double round(double value) {
        return Math.round(value * 1000D) / 1000D;
    }

    public record AnalysisResult(Long reportId, int riskScore, String riskLevel,
                                 Map<String, Object> features, String detectorVersion,
                                 String disclaimer) {
    }
}
