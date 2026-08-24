package com.stonewu.fusion.entity.risk;

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

/** AI 创作痕迹分析报告；riskScore 是风险分，不是作者身份概率。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "afv_ai_trace_report", autoResultMap = true)
public class AiTraceReport {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long projectId;
    private String contentType;
    private Long contentId;
    private String contentSha256;
    private Integer riskScore;
    private String riskLevel;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String featuresJson;
    private String detectorVersion;
    private LocalDateTime createTime;
}
