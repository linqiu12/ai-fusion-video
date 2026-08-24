"use client";

import { useEffect, useMemo, useState } from "react";
import { KeyRound, Loader2, Save, ShieldCheck } from "lucide-react";
import { toast } from "sonner";
import { permissionApi, type SystemPermission, type SystemRole } from "@/lib/api/permission";
import { toastApiError } from "@/lib/api/toast-api-error";
import { useAuthStore } from "@/lib/store/auth-store";

const moduleLabels: Record<string, string> = {
  novel: "小说创作", skill: "Skills", risk: "内容风控", publishing: "内容发布",
  system: "系统管理", tenant: "租户管理",
};

/**
 * RBAC 权限矩阵。保存采用“整组替换”语义，后端在同一事务内写入权限关系、
 * 清理登录权限缓存并追加审计事件，确保权限变更可追溯、可复盘。
 */
export default function PermissionSettingsPage() {
  const isAdmin = useAuthStore(s => s.user?.roles?.includes("admin") ?? false);
  const [roles, setRoles] = useState<SystemRole[]>([]);
  const [catalog, setCatalog] = useState<SystemPermission[]>([]);
  const [roleId, setRoleId] = useState<number | null>(null);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!isAdmin) return;
    Promise.all([permissionApi.roles(), permissionApi.catalog()]).then(([r, p]) => {
      setRoles(r); setCatalog(p); setRoleId(r[0]?.id ?? null);
    }).catch(e => toastApiError(e, "加载权限目录失败")).finally(() => setLoading(false));
  }, [isAdmin]);

  useEffect(() => {
    if (!roleId) return;
    permissionApi.rolePermissionIds(roleId).then(ids => setSelected(new Set(ids)))
      .catch(e => toastApiError(e, "加载角色权限失败"));
  }, [roleId]);

  const groups = useMemo(() => Object.entries(catalog.reduce<Record<string, SystemPermission[]>>((result, permission) => {
    const module = permission.module || "other";
    (result[module] ??= []).push(permission);
    return result;
  }, {})), [catalog]);
  const toggle = (id: number) => setSelected(current => {
    const next = new Set(current); next.has(id) ? next.delete(id) : next.add(id); return next;
  });
  const save = async () => {
    if (!roleId) return;
    setSaving(true);
    try { await permissionApi.replaceRolePermissions(roleId, [...selected]); toast.success("角色权限已保存，缓存已刷新并写入审计记录"); }
    catch (e) { toastApiError(e, "保存角色权限失败"); } finally { setSaving(false); }
  };

  if (!isAdmin) return <div className="rounded-xl border p-8"><h1 className="text-xl font-semibold">无权访问</h1><p className="mt-2 text-muted-foreground">仅租户管理员可配置角色权限。</p></div>;
  if (loading) return <div className="flex justify-center p-16"><Loader2 className="animate-spin"/></div>;

  return <div className="space-y-6">
    <div><h1 className="flex items-center gap-2 text-2xl font-semibold"><ShieldCheck/>权限管理</h1><p className="mt-2 text-sm text-muted-foreground">角色控制功能能力，团队和项目成员关系控制租户数据范围。所有变更均进入审计账本。</p></div>
    <div className="flex flex-wrap gap-2">{roles.map(role => <button key={role.id} onClick={() => setRoleId(role.id)} className={`rounded-lg border px-4 py-2 text-sm ${roleId === role.id ? "border-primary bg-primary/10 text-primary" : "hover:bg-muted"}`}><KeyRound className="mr-2 inline h-4 w-4"/>{role.name} ({role.code})</button>)}</div>
    <div className="grid gap-4 lg:grid-cols-2">{groups.map(([module, permissions]) => <section key={module} className="rounded-xl border bg-card/40 p-5"><div className="mb-4 flex items-center justify-between"><h2 className="font-medium">{moduleLabels[module] ?? module}</h2><button className="text-xs text-primary" onClick={() => setSelected(current => { const next = new Set(current); const all = permissions.every(p => next.has(p.id)); permissions.forEach(p => all ? next.delete(p.id) : next.add(p.id)); return next; })}>全选/取消</button></div><div className="space-y-3">{permissions.map(permission => <label key={permission.id} className="flex cursor-pointer gap-3 rounded-lg border p-3 hover:bg-muted/30"><input type="checkbox" checked={selected.has(permission.id)} onChange={() => toggle(permission.id)} className="mt-1"/><span><span className="block text-sm font-medium">{permission.name}</span><code className="text-xs text-muted-foreground">{permission.code}</code>{permission.description && <span className="mt-1 block text-xs text-muted-foreground">{permission.description}</span>}</span></label>)}</div></section>)}</div>
    <div className="sticky bottom-4 flex justify-end"><button disabled={saving || !roleId} onClick={save} className="flex items-center gap-2 rounded-lg bg-primary px-5 py-2.5 text-primary-foreground shadow-lg disabled:opacity-50">{saving ? <Loader2 className="h-4 w-4 animate-spin"/> : <Save className="h-4 w-4"/>}保存角色权限</button></div>
  </div>;
}
