import { http } from "./client";
import type { PageResult } from "./types";

export interface Tenant {
  id: number; name: string; tenantKey: string; planCode: "FREE" | "PRO" | "ENTERPRISE";
  expiresAt?: string; ownerUserId: number; status: number; createTime: string;
}

export const tenantApi = {
  page: () => http.get<never, PageResult<Tenant>>("/api/system/tenants", { params: { pageNo: 1, pageSize: 100 } }),
  updatePlan: (tenantId: number, planCode: Tenant["planCode"], expiresAt?: string) =>
    http.put<never, Tenant>(`/api/system/tenants/${tenantId}/plan`, { planCode, expiresAt: expiresAt || null }),
};
