import { http } from "./client";

/** 系统角色。角色决定功能权限，团队/项目成员关系决定数据范围。 */
export interface SystemRole {
  id: number;
  name: string;
  code: string;
  sort: number;
  status: number;
  remark?: string;
}

/** 可分配的细粒度权限项。 */
export interface SystemPermission {
  id: number;
  name: string;
  code: string;
  module: string;
  action: string;
  description?: string;
}

export const permissionApi = {
  roles: () => http.get<never, SystemRole[]>("/api/system/role/list"),
  catalog: () => http.get<never, SystemPermission[]>("/api/system/permissions"),
  rolePermissionIds: (roleId: number) =>
    http.get<never, number[]>(`/api/system/permissions/roles/${roleId}`),
  replaceRolePermissions: (roleId: number, permissionIds: number[]) =>
    http.put<never, boolean>(`/api/system/permissions/roles/${roleId}`, { permissionIds }),
  assignUserRole: (userId: number, roleId: number) =>
    http.post<never, boolean>("/api/system/user/assign-role", undefined, { params: { userId, roleId } }),
  removeUserRole: (userId: number, roleId: number) =>
    http.post<never, boolean>("/api/system/user/remove-role", undefined, { params: { userId, roleId } }),
};
