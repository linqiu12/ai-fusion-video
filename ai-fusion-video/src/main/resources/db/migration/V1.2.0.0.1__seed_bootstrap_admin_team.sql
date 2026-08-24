-- 为开箱即用的 admin 引导账户创建默认租户；已有团队时保持幂等。
INSERT INTO afv_team (name, description, owner_user_id, tenant_key, plan_code, status, deleted)
SELECT '默认团队', '融光默认 SaaS 租户', u.id, 'default', 'FREE', 1, 0
FROM sys_user u
WHERE u.username = 'admin'
  AND NOT EXISTS (SELECT 1 FROM afv_team t WHERE t.owner_user_id = u.id AND t.deleted = 0);

INSERT INTO afv_team_member (team_id, user_id, role, status, join_time, deleted)
SELECT t.id, u.id, 1, 1, NOW(), 0
FROM sys_user u
JOIN afv_team t ON t.owner_user_id = u.id AND t.deleted = 0
WHERE u.username = 'admin'
  AND NOT EXISTS (
    SELECT 1 FROM afv_team_member tm
    WHERE tm.team_id = t.id AND tm.user_id = u.id AND tm.deleted = 0
  );
