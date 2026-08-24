package com.stonewu.fusion.entity.risk;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.stonewu.fusion.common.BaseEntity;
import com.stonewu.fusion.common.handler.JsonbTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** 一次输入、输出、导出或发布阶段的内容安全审核凭证。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "afv_risk_assessment", autoResultMap = true)
public class RiskAssessment extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestId;
    private Long userId;
    private Long projectId;
    private String contentType;
    private Long contentId;
    private String stage;
    private String decision;
    private String riskLevel;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String categoriesJson;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String evidenceJson;
    private String contentSha256;
    private String policyVersion;
    private String reviewStatus;
}
