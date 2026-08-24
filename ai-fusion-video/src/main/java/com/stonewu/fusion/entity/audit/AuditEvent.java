package com.stonewu.fusion.entity.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.stonewu.fusion.common.handler.JsonbTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 追加式业务审计事件，不参与逻辑删除，保证链路可追溯。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "afv_audit_event", autoResultMap = true)
public class AuditEvent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String traceId;
    private Long userId;
    private Long projectId;
    private String eventType;
    private String resourceType;
    private String resourceId;
    private String action;
    private String result;
    private String beforeSha256;
    private String afterSha256;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String detailsJson;
    private LocalDateTime createTime;
}
