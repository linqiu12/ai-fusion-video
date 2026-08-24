package com.stonewu.fusion.entity.search;

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

/** MySQL 到 Elasticsearch 的最终一致索引事件。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "afv_search_outbox", autoResultMap = true)
public class SearchOutboxEvent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String aggregateType;
    private Long aggregateId;
    private String eventType;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String payloadJson;
    private String status;
    private Integer attempts;
    private String lastError;
    private LocalDateTime nextRetryAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
