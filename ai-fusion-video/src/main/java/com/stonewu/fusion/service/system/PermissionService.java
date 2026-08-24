package com.stonewu.fusion.service.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.stonewu.fusion.common.BusinessException;
import com.stonewu.fusion.entity.system.Permission;
import com.stonewu.fusion.entity.system.Role;
import com.stonewu.fusion.entity.system.RolePermission;
import com.stonewu.fusion.mapper.system.PermissionMapper;
import com.stonewu.fusion.mapper.system.RoleMapper;
import com.stonewu.fusion.mapper.system.RolePermissionMapper;
import com.stonewu.fusion.service.audit.AuditEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 系统 RBAC 权限服务；团队/项目的数据范围仍由各领域 AccessGuard 负责。 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final RoleMapper roleMapper;
    private final AuditEventService auditEventService;

    @Cacheable(value = "userPermission", key = "#userId")
    public Set<String> permissionCodes(Long userId, List<Role> roles) {
        if (roles == null || roles.isEmpty()) return Set.of();
        List<Long> roleIds = roles.stream().map(Role::getId).toList();
        List<Long> permissionIds = rolePermissionMapper.selectList(
                        new LambdaQueryWrapper<RolePermission>().in(RolePermission::getRoleId, roleIds))
                .stream().map(RolePermission::getPermissionId).distinct().toList();
        if (permissionIds.isEmpty()) return Set.of();
        Set<String> codes = new HashSet<>();
        permissionMapper.selectBatchIds(permissionIds).stream()
                .filter(permission -> Integer.valueOf(1).equals(permission.getStatus()))
                .map(Permission::getCode).forEach(codes::add);
        return Set.copyOf(codes);
    }

    @Cacheable(value = "permissionCatalog", key = "'all'")
    public List<Permission> list() {
        return permissionMapper.selectList(new LambdaQueryWrapper<Permission>()
                .eq(Permission::getStatus, 1)
                .orderByAsc(Permission::getModule)
                .orderByAsc(Permission::getCode));
    }

    public List<Long> rolePermissionIds(Long roleId) {
        requireRole(roleId);
        return rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRoleId, roleId))
                .stream().map(RolePermission::getPermissionId).toList();
    }

    @Transactional
    @CacheEvict(value = {"userPermission", "permissionCatalog"}, allEntries = true)
    public void replaceRolePermissions(Long operatorUserId, Long roleId, List<Long> permissionIds) {
        Role role = requireRole(roleId);
        List<Long> safeIds = permissionIds == null ? List.of() : permissionIds.stream().distinct().toList();
        if (!safeIds.isEmpty() && permissionMapper.selectBatchIds(safeIds).size() != safeIds.size()) {
            throw new BusinessException(400, "包含不存在的权限");
        }
        List<Long> before = rolePermissionIds(roleId);
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleId, roleId));
        safeIds.forEach(permissionId -> rolePermissionMapper.insert(RolePermission.builder()
                .roleId(roleId).permissionId(permissionId).build()));
        auditEventService.append(operatorUserId, null, "ROLE_PERMISSIONS_REPLACED", "ROLE",
                String.valueOf(roleId), "REPLACE_PERMISSIONS", "SUCCESS", null, null,
                Map.of("roleCode", role.getCode(), "before", before, "after", safeIds));
    }

    private Role requireRole(Long roleId) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) throw new BusinessException(404, "角色不存在");
        return role;
    }
}
