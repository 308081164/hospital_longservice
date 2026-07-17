# 管理员账号与客户编辑权限

**日期：** 2026-07-17

## 问题现象

`user1` 登录后进入「客户管理 → 编辑」，页面显示 **业务员视图**，表单只读（`:disabled="isReadOnlyConfig && !!editingId"`）。

## 根因

前端 `useBillingPermission.ts` 中，**编辑客户配置** 需要 `isConfigurator`（配置员视图）：

- `is_superuser === true`，或
- 角色含 `billing_configurator` / `R_SUPER` / `R_ADMIN` / `R_BILLING_CONFIG` 等

种子用户 `user1` / `user2` 仅有 `R_USER`，`is_superuser=false`，因此被判定为业务员（只读）。

后端 `CustomerController` 无 `@PreAuthorize` 限制；问题在前端权限门控。

## 修复

| 变更 | 说明 |
|------|------|
| `AdminUserInitializer.java` | 启动时若不存在则创建 `admin` / `admin123`，`is_superuser=true` |
| `DataInitializer.java` | 全新库初始化时一并创建 admin |
| `useBillingPermission.ts` | 对齐后端账单角色 `R_BILLING_CONFIG` / `R_BILLING_OPERATOR` / `R_BILLING_REVIEWER` |

## 凭据

| 用户名 | 密码 | 权限 |
|--------|------|------|
| **admin** | **admin123** | 超级管理员，配置员视图，可编辑客户 |
| user1 | 123456 | 普通用户，业务员视图（只读编辑，符合设计） |
| user2 | 123456 | 同上 |

## UI 验证步骤

1. 重建并重启后端（已有数据库也会自动补建 admin）：
   ```powershell
   docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build backend
   ```
2. 浏览器打开 http://localhost:8080 ，**退出** 当前 session（若已登录 user1）。
3. 使用 **admin / admin123** 登录。
4. 进入 **主数据 → 客户管理**，点击任一客户 **编辑**。
5. 抽屉标题旁应显示绿色标签 **配置员视图**（非「业务员视图」）。
6. 表单字段、商品规则「添加」按钮应可编辑。

## API 快速验证

```powershell
$base = "http://localhost:8080"
$login = Invoke-RestMethod -Uri "$base/api/v1/base/access_token" -Method Post `
  -ContentType "application/json" -Body '{"username":"admin","password":"admin123"}'
$token = $login.data.access_token
$info = Invoke-RestMethod -Uri "$base/api/v1/base/userinfo" -Headers @{ Authorization = "Bearer $token" }
$info.data | Select-Object username, is_superuser, roles
# 期望: is_superuser = True
```
