package com.stonewu.fusion.controller.system;

import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.entity.system.Permission;
import com.stonewu.fusion.security.SecurityUtils;
import com.stonewu.fusion.service.system.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理员 RBAC 权限配置 API。 */
@Tag(name = "权限管理")
@RestController
@RequestMapping("/api/system/permissions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasAuthority('system:user:manage')")
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @Operation(summary = "获取权限目录")
    public CommonResult<List<Permission>> list() {
        return CommonResult.success(permissionService.list());
    }

    @GetMapping("/roles/{roleId}")
    @Operation(summary = "获取角色已有权限")
    public CommonResult<List<Long>> rolePermissions(@PathVariable Long roleId) {
        return CommonResult.success(permissionService.rolePermissionIds(roleId));
    }

    @PutMapping("/roles/{roleId}")
    @Operation(summary = "替换角色权限并写入审计账本")
    public CommonResult<Boolean> replaceRolePermissions(@PathVariable Long roleId,
                                                        @Valid @RequestBody RolePermissionsRequest request) {
        permissionService.replaceRolePermissions(SecurityUtils.requireCurrentUserId(), roleId,
                request.permissionIds());
        return CommonResult.success(true);
    }

    public record RolePermissionsRequest(@NotNull List<Long> permissionIds) {
    }
}
