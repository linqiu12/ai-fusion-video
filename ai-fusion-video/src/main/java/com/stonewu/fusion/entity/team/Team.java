package com.stonewu.fusion.entity.team;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.stonewu.fusion.common.BaseEntity;
import lombok.*;
import java.time.LocalDateTime;

/**
 * 团队实体
 * <p>
 * 对应数据库表：afv_team
 * 管理协作团队的基本信息。项目可归属于团队，团队成员共享项目访问权限。
 */
@TableName("afv_team")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Team extends BaseEntity {

    /** 主键ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 团队名称 */
    private String name;

    /** 团队LOGO图片URL */
    private String logo;

    /** 团队描述 */
    private String description;

    /** 创建者用户ID */
    private Long ownerUserId;

    /** SaaS 租户稳定标识，不向业务层暴露自增主键。 */
    private String tenantKey;

    /** 当前订阅套餐代码，配额模块可据此扩展。 */
    private String planCode;

    /** 套餐到期时间；为空表示当前套餐不设固定到期时间。 */
    private LocalDateTime expiresAt;

    /** 状态：0-禁用 1-启用 */
    @Builder.Default
    private Integer status = 1;
}
