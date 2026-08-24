package com.stonewu.fusion.controller.system;

import com.stonewu.fusion.common.CommonResult;
import com.stonewu.fusion.common.PageResult;
import com.stonewu.fusion.entity.team.Team;
import com.stonewu.fusion.security.SecurityUtils;
import com.stonewu.fusion.service.audit.AuditEventService;
import com.stonewu.fusion.service.team.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/** 平台级 SaaS 租户管理 API；业务数据访问仍由团队成员关系隔离。 */
@Tag(name = "SaaS 租户管理")
@RestController
@RequestMapping("/api/system/tenants")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasAuthority('tenant:manage')")
public class TenantController {

    private final TeamService teamService;
    private final AuditEventService auditEventService;

    @GetMapping
    @Operation(summary = "分页查看租户")
    public CommonResult<PageResult<Team>> page(@RequestParam(defaultValue = "1") int pageNo,
                                                @RequestParam(defaultValue = "20") int pageSize,
                                                @RequestParam(required = false) String name) {
        return CommonResult.success(teamService.getPage(name, null, pageNo, Math.min(pageSize, 100)));
    }

    @PutMapping("/{tenantId}/plan")
    @Operation(summary = "变更租户套餐并写入审计账本")
    public CommonResult<Team> updatePlan(@PathVariable Long tenantId,
                                          @Valid @RequestBody TenantPlanRequest request) {
        Team before = teamService.getById(tenantId);
        Team updated = teamService.updateTenantPlan(tenantId, request.planCode(), request.expiresAt());
        auditEventService.append(SecurityUtils.requireCurrentUserId(), null, "TENANT_PLAN_CHANGED",
                "TENANT", String.valueOf(tenantId), "UPDATE_PLAN", "SUCCESS", null, null,
                Map.of("beforePlan", before == null || before.getPlanCode() == null ? "" : before.getPlanCode(),
                        "afterPlan", updated.getPlanCode()));
        return CommonResult.success(updated);
    }

    public record TenantPlanRequest(@NotBlank String planCode, LocalDateTime expiresAt) {
    }
}
