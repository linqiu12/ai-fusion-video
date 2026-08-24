"use client";

import { useEffect, useMemo, useState } from "react";
import { Loader2, Search, ShieldCheck, Users } from "lucide-react";
import { motion } from "framer-motion";
import { cn } from "@/lib/utils";
import { useAuthStore } from "@/lib/store/auth-store";
import { userApi } from "@/lib/api/user";
import { permissionApi, type SystemRole } from "@/lib/api/permission";
import { toast } from "sonner";
import { toastApiError } from "@/lib/api/toast-api-error";
import type { UserRespVO } from "@/lib/api/types";
import { containerVariants, itemVariants, settingsTypography } from "../_shared";

function formatDateTime(value?: string | null): string {
  if (!value) return "--";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

export default function SettingsUsersPage() {
  const currentUser = useAuthStore((state) => state.user);
  const isAdmin = currentUser?.roles?.includes("admin") ?? false;
  const [keyword, setKeyword] = useState("");
  const normalizedKeyword = keyword.trim();
  const [loadedKeyword, setLoadedKeyword] = useState<string | null>(null);
  const [users, setUsers] = useState<UserRespVO[]>([]);
  const [total, setTotal] = useState(0);
  const [roles, setRoles] = useState<SystemRole[]>([]);
  const loading = isAdmin && loadedKeyword !== normalizedKeyword;

  useEffect(() => {
    if (!isAdmin) return;

    let cancelled = false;

    userApi.page({
      pageNo: 1,
      pageSize: 100,
      username: normalizedKeyword || undefined,
    })
      .then((result) => {
        if (cancelled) return;
        setUsers(result.list);
        setTotal(result.total);
      })
      .catch((err) => {
        if (!cancelled) {
          console.error("加载用户列表失败:", err);
          toastApiError(err, "加载用户列表失败");
          setUsers([]);
          setTotal(0);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadedKeyword(normalizedKeyword);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [isAdmin, normalizedKeyword]);

  useEffect(() => {
    if (isAdmin) permissionApi.roles().then(setRoles).catch(err => toastApiError(err, "加载角色失败"));
  }, [isAdmin]);

  const toggleRole = async (user: UserRespVO, role: SystemRole) => {
    const assigned = user.roles.includes(role.code);
    try {
      if (assigned) await permissionApi.removeUserRole(user.id, role.id);
      else await permissionApi.assignUserRole(user.id, role.id);
      setUsers(current => current.map(item => item.id === user.id ? {
        ...item, roles: assigned ? item.roles.filter(code => code !== role.code) : [...item.roles, role.code],
      } : item));
      toast.success(`已${assigned ? "移除" : "分配"}角色 ${role.name}，变更已写入审计记录`);
    } catch (err) { toastApiError(err, "变更用户角色失败"); }
  };

  const adminCount = useMemo(
    () => users.filter((user) => user.roles.includes("admin")).length,
    [users]
  );

  if (!isAdmin) {
    return (
      <motion.div
        className="w-full"
        variants={containerVariants}
        initial={false}
        animate="visible"
      >
        <motion.div variants={itemVariants} className="rounded-xl border border-border/30 bg-card/50 backdrop-blur-sm p-6">
          <h1 className={settingsTypography.pageTitle}>用户列表</h1>
          <p className={settingsTypography.pageDescription}>
            只有管理员可以查看全部用户。普通用户仅可在个人设置中维护自己的资料。
          </p>
        </motion.div>
      </motion.div>
    );
  }

  return (
    <motion.div
      className="w-full"
      variants={containerVariants}
      initial={false}
      animate="visible"
    >
      <motion.div variants={itemVariants} className="mb-8 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div>
          <h1 className={settingsTypography.pageTitle}>用户列表</h1>
          <p className={settingsTypography.pageDescription}>
            查看、搜索和管理系统中的全部用户。
          </p>
        </div>

        <div className="relative w-full md:w-[280px]">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground/60" />
          <input
            type="text"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="按用户名搜索"
            className="w-full rounded-xl border border-border/30 bg-card/40 py-2.5 pl-10 pr-4 text-sm outline-none transition-colors focus:border-primary/40"
          />
        </div>
      </motion.div>

      <motion.div variants={itemVariants} className="grid gap-4 md:grid-cols-3">
        <div className="rounded-xl border border-border/30 bg-card/50 backdrop-blur-sm p-5">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Users className="h-4 w-4 text-cyan-500" />
            用户总数
          </div>
          <p className="mt-3 text-3xl font-semibold tracking-tight">{total}</p>
        </div>

        <div className="rounded-xl border border-border/30 bg-card/50 backdrop-blur-sm p-5">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <ShieldCheck className="h-4 w-4 text-emerald-500" />
            管理员
          </div>
          <p className="mt-3 text-3xl font-semibold tracking-tight">{adminCount}</p>
        </div>

        <div className="rounded-xl border border-border/30 bg-card/50 backdrop-blur-sm p-5">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Users className="h-4 w-4 text-blue-500" />
            普通成员
          </div>
          <p className="mt-3 text-3xl font-semibold tracking-tight">{Math.max(total - adminCount, 0)}</p>
        </div>
      </motion.div>

      <motion.div variants={itemVariants} className="mt-6 rounded-xl border border-border/30 bg-card/50 backdrop-blur-sm overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center py-16 text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin" />
          </div>
        ) : users.length === 0 ? (
          <div className="py-16 text-center text-sm text-muted-foreground">
            暂无匹配的用户
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="bg-muted/20 text-muted-foreground">
                <tr>
                  <th className="px-5 py-3 text-left font-medium">用户名</th>
                  <th className="px-5 py-3 text-left font-medium">昵称</th>
                  <th className="px-5 py-3 text-left font-medium">角色</th>
                  <th className="px-5 py-3 text-left font-medium">状态</th>
                  <th className="px-5 py-3 text-left font-medium">注册时间</th>
                </tr>
              </thead>
              <tbody>
                {users.map((user) => {
                  const isUserAdmin = user.roles.includes("admin");
                  return (
                    <tr key={user.id} className="border-t border-border/20">
                      <td className="px-5 py-4 font-medium">{user.username}</td>
                      <td className="px-5 py-4 text-muted-foreground">{user.nickname || "--"}</td>
                      <td className="px-5 py-4"><div className="flex flex-wrap gap-1.5">{roles.map(role => {
                        const assigned = user.roles.includes(role.code);
                        return <button key={role.id} aria-label={`${user.username}-${role.name}`} onClick={() => void toggleRole(user, role)} className={cn("rounded-full border px-2.5 py-1 text-xs", assigned ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-600" : "border-border/30 text-muted-foreground")}>{role.name}</button>;
                      })}{roles.length === 0 && <span>{isUserAdmin ? "管理员" : "成员"}</span>}</div></td>
                      <td className="px-5 py-4">
                        <span
                          className={cn(
                            "inline-flex rounded-full px-2.5 py-1 text-xs font-medium border",
                            user.status === 1
                              ? "border-blue-500/30 bg-blue-500/10 text-blue-600"
                              : "border-rose-500/30 bg-rose-500/10 text-rose-600"
                          )}
                        >
                          {user.status === 1 ? "启用" : "禁用"}
                        </span>
                      </td>
                      <td className="px-5 py-4 text-muted-foreground">{formatDateTime(user.createTime)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </motion.div>
    </motion.div>
  );
}
