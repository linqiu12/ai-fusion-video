ALTER TABLE `afv_project`
  ADD COLUMN `project_type` varchar(32) NOT NULL DEFAULT 'VIDEO' COMMENT '项目类型：VIDEO、NOVEL、IP';

ALTER TABLE `afv_team`
  ADD COLUMN `tenant_key` varchar(64) DEFAULT NULL COMMENT 'SaaS 租户唯一标识',
  ADD COLUMN `plan_code` varchar(32) NOT NULL DEFAULT 'FREE' COMMENT '租户套餐代码',
  ADD COLUMN `expires_at` datetime DEFAULT NULL COMMENT '租户服务到期时间',
  ADD UNIQUE KEY `uk_team_tenant_key` (`tenant_key`, `deleted`);

CREATE TABLE `sys_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) NOT NULL COMMENT '权限名称',
  `code` varchar(128) NOT NULL COMMENT '权限代码',
  `module` varchar(64) NOT NULL COMMENT '所属模块',
  `action` varchar(32) NOT NULL COMMENT '动作',
  `status` int NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `description` varchar(512) DEFAULT NULL COMMENT '权限说明',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='细粒度功能权限';

CREATE TABLE `sys_role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_id` bigint NOT NULL COMMENT '角色 ID',
  `permission_id` bigint NOT NULL COMMENT '权限 ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联';

INSERT INTO `sys_permission` (`name`, `code`, `module`, `action`, `description`) VALUES
  ('读取小说', 'novel:read', 'novel', 'read', '查看小说、章节与版本'),
  ('编辑小说', 'novel:write', 'novel', 'write', '创建小说和保存章节'),
  ('AI 生成小说', 'novel:generate', 'novel', 'generate', '调用用户模型生成章节'),
  ('管理 Skill', 'skill:manage', 'skill', 'manage', '导入、编辑、绑定与删除 Skill'),
  ('查看风控', 'risk:read', 'risk', 'read', '查看项目审核与 AI 痕迹报告'),
  ('人工复核', 'risk:review', 'risk', 'review', '处理高风险内容与申诉'),
  ('执行发布', 'publishing:execute', 'publishing', 'execute', '发布至外部内容平台'),
  ('管理用户', 'system:user:manage', 'system', 'manage', '管理用户和全局角色'),
  ('管理租户', 'tenant:manage', 'tenant', 'manage', '管理团队租户、成员和套餐');

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r CROSS JOIN `sys_permission` p WHERE r.code = 'admin';

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id FROM `sys_role` r CROSS JOIN `sys_permission` p
WHERE r.code = 'user' AND p.code IN ('novel:read', 'novel:write', 'novel:generate', 'skill:manage', 'risk:read');

-- 本地与首次部署的默认管理员。首次登录后应立即修改密码；生产环境可在部署前覆盖此迁移。
INSERT INTO `sys_user` (`username`, `password`, `nickname`, `status`, `deleted`)
-- 默认登录：admin / admin123。生产部署后应立即在个人设置中修改。
SELECT 'admin', '$2a$10$GVM35nOswxTSpiyojWWPmOLDH5kshKLo0nqtJdDSXAePa6BnShfRS', '系统管理员', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `username` = 'admin' AND `deleted` = 0);

INSERT INTO `sys_user_role` (`user_id`, `role_id`, `deleted`)
SELECT u.id, r.id, 0 FROM `sys_user` u CROSS JOIN `sys_role` r
WHERE u.username = 'admin' AND u.deleted = 0 AND r.code = 'admin' AND r.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `sys_user_role` ur WHERE ur.user_id = u.id AND ur.role_id = r.id AND ur.deleted = 0
  );

CREATE TABLE `afv_novel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `project_id` bigint NOT NULL COMMENT '所属统一 IP 项目',
  `title` varchar(255) NOT NULL COMMENT '小说标题',
  `genre` varchar(64) DEFAULT NULL COMMENT '题材',
  `synopsis` text COMMENT '故事简介',
  `world_setting` longtext COMMENT '世界观设定',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT、SERIALIZING、COMPLETED、ARCHIVED',
  `current_revision` int NOT NULL DEFAULT 1 COMMENT '小说元数据修订号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_novel_project_active` (`project_id`, `deleted`),
  KEY `idx_novel_status` (`status`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小说项目';

CREATE TABLE `afv_novel_chapter` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `novel_id` bigint NOT NULL COMMENT '小说 ID',
  `chapter_no` int NOT NULL COMMENT '章节序号',
  `title` varchar(255) NOT NULL COMMENT '章节标题',
  `content` longtext COMMENT '当前生效正文',
  `summary` text COMMENT '章节摘要',
  `status` varchar(32) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT、REVIEW、APPROVED、PUBLISHED',
  `current_revision` int NOT NULL DEFAULT 1 COMMENT '当前修订号',
  `word_count` int NOT NULL DEFAULT 0 COMMENT '正文字符数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_novel_chapter_no` (`novel_id`, `chapter_no`, `deleted`),
  KEY `idx_novel_chapter_status` (`novel_id`, `status`, `chapter_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小说章节当前版本';

CREATE TABLE `afv_novel_chapter_revision` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `chapter_id` bigint NOT NULL COMMENT '章节 ID',
  `revision_no` int NOT NULL COMMENT '修订号',
  `title` varchar(255) NOT NULL COMMENT '该版本章节标题',
  `content` longtext COMMENT '该版本正文',
  `content_sha256` char(64) NOT NULL COMMENT '正文 SHA-256，用于审计与防篡改',
  `source_type` varchar(32) NOT NULL COMMENT '来源：HUMAN、AI_GENERATED、AI_REWRITTEN、IMPORTED',
  `model_id` bigint DEFAULT NULL COMMENT '生成模型 ID',
  `skill_id` varchar(128) DEFAULT NULL COMMENT '生成使用的 Skill 标识',
  `skill_version` varchar(32) DEFAULT NULL COMMENT '生成使用的 Skill 版本',
  `prompt_snapshot` longtext COMMENT '生成提示词快照',
  `operator_user_id` bigint NOT NULL COMMENT '操作用户 ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_chapter_revision` (`chapter_id`, `revision_no`),
  KEY `idx_chapter_revision_created` (`chapter_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小说章节不可变修订记录';

CREATE TABLE `afv_content_provenance` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `content_type` varchar(32) NOT NULL COMMENT '内容类型：NOVEL、CHAPTER、SCRIPT、IMAGE、VIDEO',
  `content_id` bigint NOT NULL COMMENT '内容业务 ID',
  `revision_no` int DEFAULT NULL COMMENT '内容修订号',
  `source_type` varchar(32) NOT NULL COMMENT '来源类型',
  `source_ref` varchar(255) DEFAULT NULL COMMENT '来源任务或外部引用',
  `content_sha256` char(64) NOT NULL COMMENT '内容哈希',
  `model_id` bigint DEFAULT NULL COMMENT '模型 ID',
  `provider_request_id` varchar(255) DEFAULT NULL COMMENT '供应商请求 ID',
  `prompt_sha256` char(64) DEFAULT NULL COMMENT '提示词哈希',
  `operator_user_id` bigint NOT NULL COMMENT '操作用户 ID',
  `metadata_json` json DEFAULT NULL COMMENT '扩展溯源信息',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_provenance_content` (`content_type`, `content_id`, `revision_no`),
  KEY `idx_provenance_operator` (`operator_user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容生成与编辑来源账本';

CREATE TABLE `afv_risk_assessment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `request_id` char(36) NOT NULL COMMENT '一次审核的追踪 ID',
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `project_id` bigint DEFAULT NULL COMMENT '项目 ID',
  `content_type` varchar(32) NOT NULL COMMENT '内容类型',
  `content_id` bigint DEFAULT NULL COMMENT '内容业务 ID',
  `stage` varchar(32) NOT NULL COMMENT '阶段：INPUT、OUTPUT、EXPORT、PUBLISH、SKILL_IMPORT',
  `decision` varchar(32) NOT NULL COMMENT '结论：ALLOW、WARN、REVIEW、BLOCK',
  `risk_level` varchar(32) NOT NULL COMMENT '风险等级：SAFE、LOW、MEDIUM、HIGH、CRITICAL',
  `categories_json` json NOT NULL COMMENT '命中的风险分类和分数',
  `evidence_json` json DEFAULT NULL COMMENT '脱敏后的命中证据',
  `content_sha256` char(64) NOT NULL COMMENT '被审核内容哈希',
  `policy_version` varchar(64) NOT NULL COMMENT '审核策略版本',
  `review_status` varchar(32) NOT NULL DEFAULT 'AUTO_COMPLETED' COMMENT '人工复核状态',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_risk_request` (`request_id`),
  KEY `idx_risk_user_time` (`user_id`, `create_time`),
  KEY `idx_risk_project_time` (`project_id`, `create_time`),
  KEY `idx_risk_decision` (`decision`, `review_status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容安全审核记录';

CREATE TABLE `afv_audit_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `trace_id` char(36) NOT NULL COMMENT '跨模块追踪 ID',
  `user_id` bigint NOT NULL COMMENT '操作用户 ID',
  `project_id` bigint DEFAULT NULL COMMENT '项目 ID',
  `event_type` varchar(64) NOT NULL COMMENT '事件类型',
  `resource_type` varchar(64) NOT NULL COMMENT '资源类型',
  `resource_id` varchar(128) NOT NULL COMMENT '资源 ID',
  `action` varchar(64) NOT NULL COMMENT '动作',
  `result` varchar(32) NOT NULL COMMENT '结果',
  `before_sha256` char(64) DEFAULT NULL COMMENT '变更前哈希',
  `after_sha256` char(64) DEFAULT NULL COMMENT '变更后哈希',
  `details_json` json DEFAULT NULL COMMENT '脱敏审计详情',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_trace` (`trace_id`),
  KEY `idx_audit_resource` (`resource_type`, `resource_id`, `create_time`),
  KEY `idx_audit_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务操作审计事件';

CREATE TABLE `afv_project_skill_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `project_id` bigint NOT NULL COMMENT '项目 ID',
  `user_id` bigint NOT NULL COMMENT 'Skill 所有者用户 ID',
  `skill_name` varchar(64) NOT NULL COMMENT 'Skill 调用名称',
  `content_sha256` char(64) NOT NULL COMMENT '绑定时 Skill 内容哈希，等价于不可变版本',
  `enabled` bit(1) NOT NULL DEFAULT b'1' COMMENT '是否启用',
  `priority` int NOT NULL DEFAULT 100 COMMENT '加载优先级',
  `config_json` json DEFAULT NULL COMMENT '项目级 Skill 参数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_skill` (`project_id`, `user_id`, `skill_name`, `deleted`),
  KEY `idx_project_skill_enabled` (`project_id`, `enabled`, `priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目绑定的用户 Skill 版本';

CREATE TABLE `afv_ai_trace_report` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '发起用户 ID',
  `project_id` bigint DEFAULT NULL COMMENT '项目 ID',
  `content_type` varchar(32) NOT NULL COMMENT '内容类型：NOVEL、SCREENPLAY',
  `content_id` bigint DEFAULT NULL COMMENT '内容业务 ID',
  `content_sha256` char(64) NOT NULL COMMENT '检测内容哈希',
  `risk_score` int NOT NULL COMMENT 'AI 创作痕迹风险分，非真实性概率',
  `risk_level` varchar(32) NOT NULL COMMENT 'LOW、MEDIUM、HIGH',
  `features_json` json NOT NULL COMMENT '可解释的统计特征',
  `detector_version` varchar(64) NOT NULL COMMENT '检测器版本',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ai_trace_content` (`content_type`, `content_id`, `create_time`),
  KEY `idx_ai_trace_project` (`project_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 创作痕迹分析报告';

CREATE TABLE `afv_search_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `aggregate_type` varchar(32) NOT NULL COMMENT '聚合类型',
  `aggregate_id` bigint NOT NULL COMMENT '聚合 ID',
  `event_type` varchar(32) NOT NULL COMMENT '事件类型：UPSERT、DELETE',
  `payload_json` json NOT NULL COMMENT '待索引文档快照',
  `status` varchar(32) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING、PROCESSING、COMPLETED、FAILED',
  `attempts` int NOT NULL DEFAULT 0 COMMENT '尝试次数',
  `last_error` varchar(1000) DEFAULT NULL COMMENT '最后错误',
  `next_retry_at` datetime DEFAULT NULL COMMENT '下次重试时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_search_outbox_dispatch` (`status`, `next_retry_at`, `id`),
  KEY `idx_search_outbox_aggregate` (`aggregate_type`, `aggregate_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Elasticsearch 最终一致索引任务';
