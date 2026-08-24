package com.stonewu.fusion.service.risk;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.risk.RiskAssessment;
import com.stonewu.fusion.mapper.risk.RiskAssessmentMapper;
import com.stonewu.fusion.service.audit.ContentHashService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 统一内容安全入口。
 *
 * <p>当前实现提供可本地运行的第一道确定性防线。后续第三方文本/图片/音视频审核模型
 * 以额外 signal provider 接入，最终仍由本服务形成唯一、可版本化的审核结论。</p>
 */
@Service
@RequiredArgsConstructor
public class ContentRiskService {

    public static final String POLICY_VERSION = "cn-public-mvp-1.0.0";

    private static final Map<String, Set<String>> HIGH_RISK_TERMS = Map.of(
            "GAMBLING", Set.of("赌博平台", "赌场代理", "博彩引流", "下注教程"),
            "DRUGS", Set.of("毒品交易", "制毒教程", "贩毒渠道"),
            "FRAUD", Set.of("诈骗话术", "洗钱教程", "跑分平台"),
            "ILLEGAL_INSTRUCTION", Set.of("绕过审核", "规避风控", "违法教程"));

    private static final Map<String, Set<String>> REVIEW_TERMS = Map.of(
            "SEXUAL_CONTENT", Set.of("露骨色情", "强迫性行为"),
            "POLITICAL_SENSITIVITY", Set.of("现实政治人物换脸", "伪造政府公告"),
            "DEEPFAKE", Set.of("冒充真人", "伪造真人视频"));

    private final RiskAssessmentMapper riskAssessmentMapper;
    private final ContentHashService contentHashService;

    @CacheEvict(value = "riskAssessment", allEntries = true)
    public RiskDecision assess(Long userId, Long projectId, String contentType,
                               Long contentId, String stage, String content) {
        String normalized = content == null ? "" : content.toLowerCase(Locale.ROOT);
        List<RiskSignal> signals = new ArrayList<>();
        collectMatches(normalized, HIGH_RISK_TERMS, 0.95D, signals);
        collectMatches(normalized, REVIEW_TERMS, 0.75D, signals);

        String decision = signals.stream().anyMatch(signal -> signal.score() >= 0.9D)
                ? "BLOCK"
                : signals.isEmpty() ? "ALLOW" : "REVIEW";
        String level = "BLOCK".equals(decision) ? "HIGH"
                : "REVIEW".equals(decision) ? "MEDIUM" : "SAFE";
        String requestId = UUID.randomUUID().toString();
        String contentHash = contentHashService.sha256(content);

        RiskAssessment assessment = RiskAssessment.builder()
                .requestId(requestId)
                .userId(userId)
                .projectId(projectId)
                .contentType(contentType)
                .contentId(contentId)
                .stage(stage)
                .decision(decision)
                .riskLevel(level)
                .categoriesJson(JSONUtil.toJsonStr(signals.stream().map(signal -> Map.of(
                        "category", signal.category(), "score", signal.score())).toList()))
                .evidenceJson(JSONUtil.toJsonStr(signals.stream().map(signal -> Map.of(
                        "category", signal.category(), "evidence", signal.maskedEvidence())).toList()))
                .contentSha256(contentHash)
                .policyVersion(POLICY_VERSION)
                .reviewStatus("REVIEW".equals(decision) ? "PENDING" : "AUTO_COMPLETED")
                .build();
        riskAssessmentMapper.insert(assessment);
        return new RiskDecision(assessment.getId(), requestId, decision, level,
                List.copyOf(signals), contentHash, POLICY_VERSION);
    }

    public RiskDecision requireGeneratable(Long userId, Long projectId, String contentType,
                                           String stage, String content) {
        RiskDecision result = assess(userId, projectId, contentType, null, stage, content);
        if ("BLOCK".equals(result.decision())) {
            throw new BusinessException(400, "内容未通过安全审核，审核编号：" + result.requestId());
        }
        if ("REVIEW".equals(result.decision()) && "PUBLISH".equals(stage)) {
            throw new BusinessException(400, "内容需要人工复核后才能发布，审核编号：" + result.requestId());
        }
        return result;
    }

    @Cacheable(value = "riskAssessment", key = "'project:' + #projectId")
    public List<RiskAssessment> listByProject(Long projectId) {
        return riskAssessmentMapper.selectList(new LambdaQueryWrapper<RiskAssessment>()
                .eq(RiskAssessment::getProjectId, projectId)
                .orderByDesc(RiskAssessment::getCreateTime)
                .orderByDesc(RiskAssessment::getId));
    }

    private void collectMatches(String content, Map<String, Set<String>> rules,
                                double score, List<RiskSignal> signals) {
        for (Map.Entry<String, Set<String>> entry : rules.entrySet()) {
            for (String term : entry.getValue()) {
                if (content.contains(term.toLowerCase(Locale.ROOT))) {
                    signals.add(new RiskSignal(entry.getKey(), score, mask(term)));
                }
            }
        }
    }

    private String mask(String term) {
        if (term.length() <= 2) {
            return "**";
        }
        return term.charAt(0) + "***" + term.charAt(term.length() - 1);
    }

    public record RiskSignal(String category, double score, String maskedEvidence) {
    }

    public record RiskDecision(Long assessmentId, String requestId, String decision,
                               String riskLevel, List<RiskSignal> signals,
                               String contentSha256, String policyVersion) {
    }
}
