package com.stonewu.fusion.entity.novel;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.stonewu.fusion.common.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 统一 IP 项目下的小说主体。
 *
 * <p>正文不保存在本表，而是由 {@link NovelChapter} 按章节管理，便于长篇创作、
 * 版本追踪和 Elasticsearch 增量索引。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("afv_novel")
public class Novel extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String title;
    private String genre;
    private String synopsis;
    private String worldSetting;
    private String status;
    private Integer currentRevision;
}
