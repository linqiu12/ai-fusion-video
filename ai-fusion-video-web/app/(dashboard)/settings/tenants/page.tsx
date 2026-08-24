"use client";

import { useEffect, useState } from "react";
import { Building2, Loader2 } from "lucide-react";
import { toast } from "sonner";
import { tenantApi, type Tenant } from "@/lib/api/tenant";
import { toastApiError } from "@/lib/api/toast-api-error";
import { useAuthStore } from "@/lib/store/auth-store";

/** 平台管理员租户控制台：套餐变更由后端审计，tenantKey 用于外部计费系统对账。 */
export default function TenantsPage() {
  const isAdmin = useAuthStore(s => s.user?.roles?.includes("admin") ?? false);
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [loading, setLoading] = useState(true);
  const load = () => tenantApi.page().then(result => setTenants(result.list))
    .catch(e => toastApiError(e, "加载租户失败")).finally(() => setLoading(false));
  useEffect(() => { if (isAdmin) void load(); }, [isAdmin]);
  const changePlan = async (tenant: Tenant, planCode: Tenant["planCode"]) => {
    try { await tenantApi.updatePlan(tenant.id, planCode, tenant.expiresAt); await load(); toast.success("套餐已更新并写入审计记录"); }
    catch (e) { toastApiError(e, "更新套餐失败"); }
  };
  if (!isAdmin) return <div className="rounded-xl border p-8">仅平台管理员可访问租户控制台。</div>;
  return <div className="space-y-6"><div><h1 className="flex items-center gap-2 text-2xl font-semibold"><Building2/>SaaS 租户</h1><p className="mt-2 text-sm text-muted-foreground">管理租户标识、套餐与有效期。团队成员关系负责数据隔离，角色权限负责功能授权。</p></div>{loading ? <Loader2 className="animate-spin"/> : <div className="overflow-hidden rounded-xl border"><table className="w-full text-sm"><thead className="bg-muted/30"><tr><th className="p-3 text-left">租户</th><th className="p-3 text-left">Tenant Key</th><th className="p-3 text-left">套餐</th><th className="p-3 text-left">状态</th></tr></thead><tbody>{tenants.map(tenant => <tr key={tenant.id} className="border-t"><td className="p-3 font-medium">{tenant.name}</td><td className="p-3"><code className="text-xs">{tenant.tenantKey}</code></td><td className="p-3"><select aria-label={`${tenant.name}套餐`} value={tenant.planCode} onChange={e => void changePlan(tenant, e.target.value as Tenant["planCode"])} className="rounded border bg-background px-2 py-1"><option>FREE</option><option>PRO</option><option>ENTERPRISE</option></select></td><td className="p-3">{tenant.status === 1 ? "启用" : "停用"}</td></tr>)}</tbody></table></div>}</div>;
}
