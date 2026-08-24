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

/** 内容来源凭证，记录模型、提示词和供应商任务等生成事实。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "afv_content_provenance", autoResultMap = true)
public class ContentProvenance {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String contentType;
    private Long contentId;
    private Integer revisionNo;
    private String sourceType;
    private String sourceRef;
    private String contentSha256;
    private Long modelId;
    private String providerRequestId;
    private String promptSha256;
    private Long operatorUserId;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String metadataJson;
    private LocalDateTime createTime;
}
