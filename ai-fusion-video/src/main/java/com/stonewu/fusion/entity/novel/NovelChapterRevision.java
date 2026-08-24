package com.stonewu.fusion.entity.novel;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 章节不可变修订记录。
 *
 * <p>它同时保存模型、Skill 与提示词快照，使任意版本都能解释“谁在何时用什么生成”。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("afv_novel_chapter_revision")
public class NovelChapterRevision {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long chapterId;
    private Integer revisionNo;
    private String title;
    private String content;
    private String contentSha256;
    private String sourceType;
    private Long modelId;
    private String skillId;
    private String skillVersion;
    private String promptSnapshot;
    private Long operatorUserId;
    private LocalDateTime createTime;
}
