package com.stonewu.fusion.service.audit;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.entity.audit.AuditEvent;
import com.stonewu.fusion.mapper.audit.AuditEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 统一追加式审计账本。
 *
 * <p>业务服务只传递脱敏详情；API Key、完整提示词和高风险原文不得写入本表。</p>
 */
@Service
@RequiredArgsConstructor
public class AuditEventService {

    private final AuditEventMapper auditEventMapper;

    @CacheEvict(value = "auditEvent", allEntries = true)
    public AuditEvent append(Long userId, Long projectId, String eventType,
                             String resourceType, String resourceId, String action,
                             String result, String beforeHash, String afterHash,
                             Map<String, ?> details) {
        AuditEvent event = AuditEvent.builder()
                .traceId(UUID.randomUUID().toString())
                .userId(userId)
                .projectId(projectId)
                .eventType(eventType)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .action(action)
                .result(result)
                .beforeSha256(beforeHash)
                .afterSha256(afterHash)
                .detailsJson(details == null ? null : JSONUtil.toJsonStr(details))
                .build();
        auditEventMapper.insert(event);
        return event;
    }

    @Cacheable(value = "auditEvent", key = "#resourceType + ':' + #resourceId")
    public List<AuditEvent> listByResource(String resourceType, String resourceId) {
        return auditEventMapper.selectList(new LambdaQueryWrapper<AuditEvent>()
                .eq(AuditEvent::getResourceType, resourceType)
                .eq(AuditEvent::getResourceId, resourceId)
                .orderByDesc(AuditEvent::getCreateTime)
                .orderByDesc(AuditEvent::getId));
    }
}
