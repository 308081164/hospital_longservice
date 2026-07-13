# 医院灭菌计费系统 — 操作流程与前后端调用链

本文档详细记录系统每一个用户操作的完整调用链路：从用户点击浏览器按钮开始 → 前端函数 → 网络请求 → 后端 Controller → Repository → 数据库。

---

## 目录

1. [登录与认证](#1-登录与认证)
2. [用户管理](#2-用户管理)
3. [角色管理](#3-角色管理)
4. [菜单管理](#4-菜单管理)
5. [API 权限管理](#5-api-权限管理)
6. [医院计费规则管理](#6-医院计费规则管理)
   - 6.1 [查看规则列表](#61-查看规则列表)
   - 6.2 [获取当前激活的规则](#62-获取当前激活的规则)
   - 6.3 [新建规则](#63-新建规则)
   - 6.4 [编辑规则](#64-编辑规则)
   - 6.5 [激活规则](#65-激活规则)
   - 6.6 [删除规则](#66-删除规则)
7. [医院 Excel 核对](#7-医院-excel-核对)
   - 7.1 [进入页面（自动加载规则）](#71-进入页面自动加载规则)
   - 7.2 [上传 Excel 并解析](#72-上传-excel-并解析)
   - 7.3 [开始校对](#73-开始校对)
   - 7.4 [保存校对记录](#74-保存校对记录)
   - 7.5 [导出账单 Excel](#75-导出账单-excel)
   - 7.6 [导出复核 Excel](#76-导出复核-excel)
   - 7.7 [导出结款函 Excel](#77-导出结款函-excel)
   - 7.8 [打印账单 HTML](#78-打印账单-html)
   - 7.9 [打印结款函 HTML](#79-打印结款函-html)
   - 7.10 [审核管理](#710-审核管理)
   - 7.11 [查看历史记录](#711-查看历史记录)

---

## 1. 登录与认证

### 操作：用户在登录页输入用户名密码，点击登录

```
浏览器操作：输入用户名和密码 → 点击"登录"
```

**前端调用链：**

| 步骤 | 位置 | 函数/文件 | 说明 |
|------|------|-----------|------|
| 1 | 页面 | `src/views/auth/login/index.vue` | 登录表单页面 |
| 2 | Vue | `handleLogin()` | 收集表单数据 `{ userName, password }` |
| 3 | Store | `useUserStore().login()` | Pinia store 的 login action |
| 4 | API | `fetchLogin({ userName, password })` | `src/api/auth.ts` |
| 5 | HTTP | `POST /api/v1/base/access_token?username=xxx&password=xxx` | x-www-form-urlencoded 格式 |

**后端调用链：**

```
POST /api/v1/base/access_token
    │
    └─► AuthController.login(LoginRequest)
            │
            └─► authenticationManager.authenticate()
                    │
                    └─► UserDetailsServiceImpl.loadUserByUsername(username)
                            │
                            └─► UserRepository.findByUsername(username)
                                    │
                                    └─► SQL: SELECT * FROM backend_user WHERE username = ?
            │
            └─► JwtTokenProvider.generateAccessToken(username, roles)
            └─► JwtTokenProvider.generateRefreshToken(username)
            │
            └─► 返回 LoginResponse { accessToken, refreshToken, tokenType, expiresIn }
```

**关键类：**
- 前端 API：`src/api/auth.ts` → `fetchLogin()`
- 后端 Controller：`AuthController.java` → `login()`
- 认证 Filter：`JwtAuthenticationFilter.java`（拦截后续所有带 `Authorization: Bearer xxx` 的请求）
- Token 生成：`JwtTokenProvider.java` → `generateAccessToken()` / `generateRefreshToken()`

---

### 操作：页面刷新后自动获取用户信息

```
浏览器操作：页面加载完成（App.vue 或路由守卫触发）
```

**前端调用链：**

| 步骤 | 位置 | 函数/文件 | 说明 |
|------|------|-----------|------|
| 1 | Store | `useUserStore().getInfo()` | Pinia store 获取用户信息 |
| 2 | API | `fetchGetUserInfo()` | `src/api/auth.ts` |
| 3 | HTTP | `GET /api/v1/base/userinfo` | 请求头携带 `Authorization: Bearer <token>` |

**后端调用链：**

```
GET /api/v1/base/userinfo
    │
    └─► JwtAuthenticationFilter.doFilterInternal()
            │ 解析 Authorization header → 提取 token → 验证 → 加载 UserDetails → 设置 SecurityContext
            │
    └─► AuthController.getUserInfo(Authentication)
            │
            └─► UserRepository.findByUsername(username)
            │
            └─► 返回 UserInfoResponse { id, username, email, isActive, isSuperuser, roles, ... }
```

### 操作：忘记密码（用户自助）

```
用户操作：在登录页点击"忘记密码"链接 → 看到提示"请联系管理员重置密码"
```

| 步骤 | 位置 | 函数/组件 | 说明 |
|------|------|-----------|------|
| 1 | 登录页 | `src/views/auth/login/index.vue` | 底部"忘记密码"链接 (`RouterLink` → `name: 'ForgetPassword'`) |
| 2 | 忘记密码页 | `src/views/auth/forget-password/index.vue` | 显示联系管理员提示 + "返回登录"按钮 |
| 3 | 路由 | `ForgetPassword` (静态路由) | `path: '/auth/forget-password'`，无需认证即可访问 |

**页面内容：**
- 标题：{{ $t('forgetPassword.title') }}（"忘记密码？"）
- 提示文本：{{ $t('forgetPassword.contactAdmin') }}（"请联系管理员重置密码"）
- 管理员路径指引：{{ $t('forgetPassword.adminPath') }}（"管理员路径：系统管理 → 用户管理 → 重置密码"）
- 底部"返回登录"按钮 → `router.push({ name: 'Login' })`

**说明：** 此页不调用任何后端 API。内部系统采用管理员代重置密码方案，用户联系管理员后由管理员在后台操作。

---

## 2. 用户管理

> **导航入口：** 左侧菜单 → **系统管理** → **用户管理**（路径 `/system/user`）
> 页面组件：`src/views/system/user/index.vue`
> 弹窗组件：`src/views/system/user/modules/user-dialog.vue`
> API 层：`src/api/system-manage.ts`

### 操作：查看用户列表

```
用户操作：点击"用户管理"菜单进入页面
```

**前端调用链：**

| 步骤 | 位置 | 函数 | 说明 |
|------|------|------|------|
| 1 | `index.vue` | `useTable()` → `getData()` | 组件挂载后自动加载，`useTable` hook 管理分页/加载状态 |
| 2 | `system-manage.ts` | `fetchGetUserList(params)` | 发送 GET 请求 |
| 3 | HTTP | `GET /api/user/list?current=1&size=20` | 支持筛选: username, email, isActive |

**后端调用链：**

```
GET /api/user/list?current=1&size=20
    │
    └─► UserController.listUsers(username?, email?, isActive?, current, size)
            │
            └─► UserRepository.searchUsers(username, email, isActive, PageRequest)
            │        SQL: SELECT * FROM sys_user WHERE ... ORDER BY created_at DESC LIMIT ?, ?
            │
            └─► 返回 { records: [...], total, current, size }
```

### 操作：创建用户

| 步骤 | 位置 | 函数 | 说明 |
|------|------|------|------|
| 1 | `index.vue` | `showDialog('add')` | 打开弹窗 |
| 2 | `user-dialog.vue` | `handleSubmit()` | 验证表单 → 调用 API |
| 3 | `system-manage.ts` | `createUser({ username, email, password, roleIds })` | POST 请求 |
| 4 | HTTP | `POST /api/v1/users` | body: `UserCreateRequest` |
| 5 | Controller | `UserController.createUser()` | 检查用户名/邮箱唯一性 → BCrypt 加密密码 → 分配角色 → 保存 |
| 6 | SQL | `INSERT INTO sys_user` | |

### 操作：编辑用户

| 步骤 | 位置 | 函数 | 说明 |
|------|------|------|------|
| 1 | `index.vue` | 表格行 → `showDialog('edit', row)` | 打开弹窗并传入行数据 |
| 2 | `user-dialog.vue` | `initFormData()` → 回填表单 | 密码留空（不修改密码则不填） |
| 3 | `system-manage.ts` | `updateUser(id, { username, email, password?, roleIds })` | PUT 请求 |
| 4 | HTTP | `PUT /api/v1/users/{id}` | body: `UserUpdateRequest` |

### 操作：删除用户

| 步骤 | 位置 | 函数 | 说明 |
|------|------|------|------|
| 1 | `index.vue` | `deleteUser(row)` | 弹出确认对话框 |
| 2 | `system-manage.ts` | `deleteUser(id)` | DELETE 请求 |
| 3 | HTTP | `DELETE /api/v1/users/{id}` | |
| 4 | Controller | `UserController.deleteUser(id)` | |

### 操作：重置密码（管理员）

```
用户操作：在用户列表点击🔑图标 → 确认 → 弹窗显示新密码
```

| 步骤 | 位置 | 函数 | 说明 |
|------|------|------|------|
| 1 | `index.vue` | 表格行🔑 → `handleResetPassword(row)` | 弹出确认对话框 |
| 2 | `system-manage.ts` | `resetPassword(id)` | POST 请求 |
| 3 | HTTP | `POST /api/v1/users/{id}/reset-password` | 无 body |
| 4 | Controller | `UserController.resetPassword(id)` | UUID 截取 12 位随机密码 → BCrypt 加密存储 |
| 5 | SQL | `UPDATE backend_user SET password = ? WHERE id = ?` | |
| 6 | 结果弹窗 | `ElMessageBox.alert()` | 明文展示新密码，提示管理员转交用户 |

**后端调用链：**

```
POST /api/v1/users/{id}/reset-password
    │
    └─► UserController.resetPassword(id)
            │
            ├─► UserRepository.findById(id)
            │
            ├─► String newPassword = UUID.randomUUID().toString().replace("-", "").substring(0, 12)
            │
            ├─► user.setPassword(passwordEncoder.encode(newPassword))
            │
            └─► 返回 Result.success({ newPassword: "abc123def456" })
```

**安全说明：** 此接口要求 JWT 认证（管理员必须已登录），但暂无 `@PreAuthorize` 权限检查。
新密码明文只在弹窗展示一次，关掉后无法再次查看，建议用户首次登录后修改密码。

---

## 3. 角色管理

> **导航入口：** 左侧菜单 → **系统管理** → **角色管理**（路径 `/system/role`）
> 页面组件：`src/views/system/role/index.vue`
> 弹窗组件：`src/views/system/role/modules/role-edit-dialog.vue`
> 权限弹窗：`src/views/system/role/modules/role-permission-dialog.vue`

### 操作：查看角色列表

| 步骤 | 位置 | 说明 |
|------|------|------|
| 1 | API | `GET /api/role/list?current=1&size=20` |
| 2 | Controller | `RoleController.listRoles(current, size)` → 内存分页 |
| 3 | Repository | `RoleRepository.findAll(Sort.by("createdAt"))` |
| 4 | SQL | `SELECT * FROM sys_role ORDER BY created_at DESC` |

返回数据包含每个角色的 menus 和 apis 关联权限信息。

### 操作：创建/编辑/删除角色

| 操作 | HTTP | Controller 方法 | 前端函数 |
|------|------|----------------|----------|
| 创建 | `POST /api/v1/role` | `RoleController.createRole(RoleCreateRequest)` | `role-edit-dialog.vue` → `createRole()` |
| 编辑 | `PUT /api/v1/role/{id}` | `RoleController.updateRole(id, RoleUpdateRequest)` | `role-edit-dialog.vue` → `updateRole()` |
| 分配菜单权限 | `PUT /api/v1/role/{id}/menus` | `RoleController.updateRoleMenus(id, RoleUpdateMenusRequest)` | `role-permission-dialog.vue` → `updateRoleMenus()` |
| 删除 | `DELETE /api/v1/role/{id}` | `RoleController.deleteRole(id)` | `index.vue` → `deleteRole()` |

**权限分配流程：**
1. 点击"菜单权限"按钮 → 打开 `role-permission-dialog.vue`
2. 弹窗调用 `fetchGetMenuList()` 加载后端菜单树（`GET /api/v3/system/menus`）
3. 预选中当前角色已有的菜单（根据 `roleData.menus` 中的 id）
4. 保存时调用 `updateRoleMenus(roleId, menuIds)` → `PUT /api/v1/role/{id}/menus`

---

## 4. 菜单管理

> **导航入口：** 左侧菜单 → **系统管理** → **菜单管理**（路径 `/system/menu`）
> 页面组件：`src/views/system/menu/index.vue`
> 弹窗组件：`src/views/system/menu/modules/menu-dialog.vue`

### 操作：查看菜单树

| 步骤 | 位置 | 说明 |
|------|------|------|
| 1 | API | `GET /api/v3/system/menus` |
| 2 | Controller | `MenuController.getMenuTree()` |
| 3 | Repository | `MenuRepository.findAllByOrderByOrder()` |
| 4 | 逻辑 | `buildMenuTree()` — 递归构建父子关系树（parentId = 0 为根） |
| 5 | SQL | `SELECT * FROM sys_menu ORDER BY order ASC` |

返回树形结构，每个节点包含: id, name, menuType, icon, path, order, parentId, isHidden, component, keepalive, redirect, children。

### 操作：创建/编辑/删除菜单

| 操作 | HTTP | Controller 方法 | 前端函数 |
|------|------|----------------|----------|
| 创建 | `POST /api/v1/menu` | `MenuController.createMenu(MenuCreateRequest)` | `menu-dialog.vue` → `createMenu()` |
| 编辑 | `PUT /api/v1/menu/{id}` | `MenuController.updateMenu(id, MenuUpdateRequest)` | `menu-dialog.vue` → `updateMenu()` |
| 删除 | `DELETE /api/v1/menu/{id}` | `MenuController.deleteMenu(id)` | `index.vue` → `handleDeleteMenu()` → `deleteMenu()` |

**创建菜单关键字段：**
- `menuType`: "catalog"（目录）或 "menu"（菜单）
- `parentId`: 0 表示顶级菜单
- `component`: Vue 组件路径（如 "/system/user"）
- `order`: 同级排序序号

---

## 5. API 接口管理

> **导航入口：** 左侧菜单 → **系统管理** → **API管理**（路径 `/system/api`）
> 页面组件：`src/views/system/api/index.vue`

### 操作：查看 API 列表

| 步骤 | 位置 | 说明 |
|------|------|------|
| 1 | API | `GET /api/v1/api?current=1&size=20` |
| 2 | Controller | `ApiController.listApis(current, size)` → 内存分页 |
| 3 | Repository | `ApiRepository.findAll(Sort.by("id"))` |
| 4 | SQL | `SELECT * FROM sys_api ORDER BY id ASC` |

### 操作：刷新 API 列表

| 步骤 | 位置 | 说明 |
|------|------|------|
| 1 | 前端 | 点击"刷新 API 列表"按钮 → `refreshApiList()` |
| 2 | API | `POST /api/v1/api/refresh` |
| 3 | Controller | `ApiController.refreshApis()` → 返回成功消息（Spring Boot 版本手动管理 API 记录） |

---

## 6. 医院计费规则管理

> **导航入口：** 左侧菜单 → **医院计费规则**（路径 `/hospital/pricing-rules`）
> Vue 组件：`src/views/hospital/pricing-rules/index.vue`

### 6.1 查看规则列表

```
用户操作：进入"医院计费规则"页面
```

**前端调用链：**

| 步骤 | 文件 | 函数 | 说明 |
|------|------|------|------|
| 1 | `index.vue` | `onMounted()` / `watch(route.path)` | 组件挂载 / 路由变化时触发 |
| 2 | `index.vue` | `loadRules()` | 调用 API 加载规则列表 |
| 3 | `pricingRulesApi.ts` | `listHospitalPricingRules()` | 发送 HTTP 请求 → 逐条 `normalizeRuleRecord()`（try-catch 保护，失败过滤） |
| 4 | HTTP | `GET /api/hospital-pricing-rules` | REST API |
| 5 | `pricingRulesApi.ts` | `normalizeRuleRecord()` | 每条记录独立 try-catch + `normalizePricingRules()`，失败返回 null 被 `.filter()` 过滤，不中断整个列表 |

**后端调用链：**

```
GET /api/hospital-pricing-rules
    │
    └─► HospitalPricingRuleController.listRules()
            │
            └─► HospitalPricingRuleRepository.findAllByOrderByUpdatedAtDesc()
            │        SQL: SELECT * FROM hospital_pricing_rule ORDER BY updated_at DESC
            │
            └─► stream().map(this::toPricingRuleResponse)
                    │
                    └─► JsonUtils.parseToMap(rule.getRulesJson())
                        // 将 LONGTEXT JSON 字符串反序列化为 Map<String, Object>
                        // 前端收到后调用 normalizePricingRules() 规整化
```

**返回数据示例：**
```json
[
  {
    "id": 1,
    "name": "标准灭菌计费规则",
    "version": "v1.0",
    "description": "标准高温/低温灭菌计费规则...",
    "isActive": true,
    "rules": {
      "version": "v1.0",
      "highTemperature": { "nonWoven": {...}, "paperPlastic": {...} },
      "lowTemperature": { "nonWoven": {...}, "paperPlastic": {...} },
      "packaging": {...},
      "needle": {...},
      "cleaning": {...},
      "logistics": {...},
      "settlementLetter": {...},
      "exportOptions": {...}
    },
    "createdAt": "2026-05-04T10:00:00",
    "updatedAt": "2026-05-04T10:00:00"
  }
]
```

**命名规范变更：** `PricingRuleResponse` DTO 中的 `isActive`、`createdAt`、`updatedAt` 字段已移除 `@JsonProperty("is_active")` / `@JsonProperty("created_at")` / `@JsonProperty("updated_at")` 注解，后端直接序列化为 camelCase。前端 `normalizeRuleRecord()` 兼容两种格式（`record.is_active ?? record.isActive`），确保向后兼容。

**页面状态（index.vue）：**
- 加载中：显示 "正在加载规则列表..."
- 加载失败：显示错误信息 + "重新加载"按钮 + 提示 "请确认后端服务已启动（localhost:8000）"
- 规则列表为空：显示 "还没有定价方案，请点击左侧「新建方案」开始" + "新建方案"按钮
- 有规则但未选择：显示 "请从左侧「可选方案」列表中选择一个方案"

**Element Plus 兼容性修复：** 页面中所有 `ElCollapseItem` 组件和 JSON 预览 `ElCard` 的 `v-show` 均已改为 `v-if`。`v-show` 与 `ElCollapseItem` 内部样式冲突（均使用 CSS `display` 控制可见性），导致折叠面板内容无法正确渲染。

**前端规整化（normalizePricingRules）：**

```
normalizePricingRules(raw)
    │ 文件：src/api/hospital/pricingRules.ts (第392行)
    │
    ├─► readRuleObject(raw) — 校验 raw 是对象
    ├─► 判断是否含 highTemperature 和 lowTemperature
    │   ├─ 是 → 直接解析（新格式）
    │   └─ 否 → convertLegacyRules()（兼容旧格式，自动构建缺失的配置段）
    │
    ├─► 逐字段解析并填充默认值：
    │   ├─ nonWoven → { minCharge, flatPerPackagePrice, flatRateThreshold }
    │   ├─ paperPlastic → { bagSizes, perPackagePrice, minCharge }
    │   ├─ packaging → normalizePackagingItems()
    │   ├─ needle → { threshold, foldRatio, keywords }
    │   ├─ cleaning → { removeFirstRow, dropSummaryRows, summaryKeywords, ... }
    │   ├─ logistics → { enabled, feePerTrip, dayBoundaryHour, ... }
    │   ├─ settlementLetter → { companyName, rowHeight, templates, feeItems, ... }
    │   └─ exportOptions → { billFilePrefix, settlementFilePrefix, ... }
    │
    └─► validatePricingRules(rules) — 必填字段、范围校验
        │  needle.threshold < 0 才视为无效（0 表示"未配置"，不报错）
        │  needle.foldRatio < 0 才视为无效
        │  keywords 数组允许为空（不强制配置关键词）
        │
        └─ 不通过 → throw Error("规则验证失败：...")
```

**错误处理机制：**
- `normalizeRuleRecord()` 使用 try-catch 包裹每条记录的规整化过程，单个记录解析失败时返回 `null`
- `listHospitalPricingRules()` 通过 `.filter()` 过滤掉返回 `null` 的记录，不会因单条记录异常导致整个列表加载失败
- 单记录操作（`getActiveHospitalPricingRule` / `createHospitalPricingRule` / `updateHospitalPricingRule` / `activateHospitalPricingRule`）使用 `ensureNormalized()`，规整化失败时抛出明确错误信息 "规则数据格式异常"

---

### 6.2 获取当前激活的规则

```
用户操作：进入医院核对页面时自动调用（无需手动操作）
```

**前端调用链：**

| 步骤 | 文件 | 函数 | 说明 |
|------|------|------|------|
| 1 | `reconciliation/index.vue` | `onMounted()` 第1111行 | 页面加载 |
| 2 | `reconciliation/index.vue` | `activeRule.value = await getActiveHospitalPricingRule()` | 第1116行 |
| 3 | `pricingRulesApi.ts` | `getActiveHospitalPricingRule()` | API 调用 |
| 4 | HTTP | `GET /api/hospital-pricing-rules/active` | 后端接口 |

**后端调用链：**

```
GET /api/hospital-pricing-rules/active
    │
    └─► HospitalPricingRuleController.getActiveRule()
            │
            └─► HospitalPricingRuleRepository.findByIsActiveTrue()
            │        SQL: SELECT * FROM hospital_pricing_rule WHERE is_active = 1
            │
            └─► toPricingRuleResponse(rule) → PricingRuleResponse
                    │
                    └─► JsonUtils.parseToMap(rule.getRulesJson())
```

**特殊处理：** 如果数据库中没有激活的规则，返回 `404 Not Found`。前端 catch 后设置 `activeRule = null`，页面显示红色警告：
> "当前未加载到后端医院规则，已禁止按本地默认规则计算。请先检查规则接口或确认已启用规则。"

---

### 6.3 新建规则

```
用户操作：点击"新建规则" → 填写表单 → 点击"保存"
```

**前端调用链：**

| 步骤 | 文件 | 函数 | 说明 |
|------|------|------|------|
| 1 | `index.vue` | 新建按钮 → `handleCreate()` | 初始化表单 |
| 2 | `index.vue` | `defaultEmptyRules()` 第806行 | 创建空规则模板 |
| 3 | `index.vue` | 用户填写规则内容 | 在左右分栏编辑器中配置 |
| 4 | `index.vue` | 保存按钮 → `handleSave()` | 收集 formData + currentRule |
| 5 | `pricingRulesApi.ts` | `createHospitalPricingRule(payload)` | API 调用 |
| 6 | HTTP | `POST /api/hospital-pricing-rules` | body: `SavePricingRuleRequest` |

**后端调用链：**

```
POST /api/hospital-pricing-rules
    │
    └─► HospitalPricingRuleController.createRule(@Valid SavePricingRuleRequest)
            │
            ├─► HospitalPricingRuleRepository.deactivateAll()
            │       // ★ @Transactional 保障原子性
            │       SQL: UPDATE hospital_pricing_rule SET is_active = 0
            │       // 新规则默认激活，先停用所有旧规则
            │
            ├─► JsonUtils.toJson(request.getRules())
            │       // 将 Map<String, Object> 序列化为 JSON 字符串
            │       // 存入 rules_json LONGTEXT 字段
            │
            ├─► HospitalPricingRule rule = new HospitalPricingRule()
            │       rule.setName(request.getName())
            │       rule.setVersion(request.getVersion())
            │       rule.setDescription(request.getDescription())
            │       rule.setIsActive(true)
            │       rule.setRulesJson(rulesJsonString)
            │
            └─► HospitalPricingRuleRepository.save(rule)
                    SQL: INSERT INTO hospital_pricing_rule (...)
```

**注意：** `SavePricingRuleRequest.isActive` 字段已移除 `@JsonProperty("is_active")` 注解，
后端统一使用 camelCase 命名，前端直接发送 `isActive`。

**2026-05-07 变更：** 前端已移除"关联医院"（hospitalName）表单字段。新建规则只需填写规则名称和描述，
不再需要关联医院。医院与规则的匹配改为**按规则名称自动匹配**（见 6.7 节）。

---

### 6.4 编辑规则

```
用户操作：点击规则列表中的"编辑" → 修改 → 点击"保存"
```

**前端调用链：**

| 步骤 | 文件 | 函数 | 说明 |
|------|------|------|------|
| 1 | `index.vue` | 选择规则 → `selectedRuleId` 变更 | |
| 2 | `index.vue` | `currentRecord` computed | `ruleList.value.find(r => r.id === selectedRuleId)` |
| 3 | `index.vue` | `watch currentRecord → loadRuleIntoEditor()` | 深拷贝规则到编辑器 |
| 4 | `index.vue` | 用户在右侧面板修改 | 高温/低温/包装/物流等各分类下的配置项 |
| 5 | `index.vue` | 保存按钮 → `handleSave()` | 收集 formData + currentRule |
| 6 | `pricingRulesApi.ts` | `updateHospitalPricingRule(id, payload)` | API 调用 |
| 7 | HTTP | `PUT /api/hospital-pricing-rules/{id}` | body: `SavePricingRuleRequest`(部分字段) |

**后端调用链：**

```
PUT /api/hospital-pricing-rules/{id}
    │
    └─► HospitalPricingRuleController.updateRule(id, SavePricingRuleRequest)
            │
            ├─► HospitalPricingRuleRepository.findById(id)
            │       SQL: SELECT * FROM hospital_pricing_rule WHERE id = ?
            │
            ├─► 逐个字段 null 检查，只更新非 null 字段（PATCH 风格）
            │       if (request.getName() != null) rule.setName(...)
            │       if (request.getVersion() != null) rule.setVersion(...)
            │       if (request.getDescription() != null) rule.setDescription(...)
            │       if (request.getRules() != null) rule.setRulesJson(JsonUtils.toJson(...))
            │
            ├─► 如果 isActive 显式设为 true，先 deactivateAll()
            │
            └─► HospitalPricingRuleRepository.save(rule)
                    SQL: UPDATE hospital_pricing_rule SET ... WHERE id = ?
```

---

### 6.5 激活规则

```
用户操作：点击规则列表中的"激活"按钮
```

**前端调用链：**

| 步骤 | 文件 | 函数 | 说明 |
|------|------|------|------|
| 1 | `index.vue` | `handleActivate(id)` | 激活按钮点击 |
| 2 | `pricingRulesApi.ts` | `activateHospitalPricingRule(id)` | API 调用 |
| 3 | HTTP | `POST /api/hospital-pricing-rules/{id}/activate` | 不需要 body |

**后端调用链：**

```
POST /api/hospital-pricing-rules/{id}/activate
    │
    └─► HospitalPricingRuleController.activateRule(id)
            │
            ├─► HospitalPricingRuleRepository.deactivateAll()
            │       SQL: UPDATE hospital_pricing_rule SET is_active = 0
            │
            ├─► HospitalPricingRuleRepository.findById(id)
            │
            ├─► rule.setIsActive(true)
            │
            └─► HospitalPricingRuleRepository.save(rule)
                    SQL: UPDATE hospital_pricing_rule SET is_active = 1 WHERE id = ?
```

**设计说明：** 系统一次只有一条规则激活。激活新规则时先停用所有规则，再激活目标规则，由 `@Transactional` 保障原子性。

---

### 6.6 删除规则

```
用户操作：点击规则列表中的"删除" → 确认 → 删除
```

**前端调用链：**

| 步骤 | 文件 | 函数 | 说明 |
|------|------|------|------|
| 1 | `index.vue` | `handleDelete(id)` | ElMessageBox.confirm 确认 |
| 2 | `pricingRulesApi.ts` | `deleteHospitalPricingRule(id)` | API 调用 |
| 3 | HTTP | `DELETE /api/hospital-pricing-rules/{id}` | |

**后端调用链：**

```
DELETE /api/hospital-pricing-rules/{id}
    │
    └─► HospitalPricingRuleController.deleteRule(id)
            │
            └─► HospitalPricingRuleRepository.deleteById(id)
                    SQL: DELETE FROM hospital_pricing_rule WHERE id = ?
```

---

### 6.7 按规则名称自动匹配（Excel 上传时自动激活）

```
流程触发：用户上传 Excel 后自动执行（无需手动操作）
```

**2026-05-07 新增功能：** 系统不再依赖"关联医院"（hospitalName）字段来匹配医院与计费规则。
改为**按规则名称自动匹配**：上传 Excel 时，用文件名（或 Excel 内容检测到的医院名称）在所有规则中按名称匹配。

**前端调用链（`reconciliation/index.vue`）：**

```
loadHospitalRule(name: string)
    │  文件: src/views/hospital/reconciliation/index.vue
    │
    ├─► if (!name) {
    │       // 无名称 → 直接加载全局激活规则
    │       activeRule = await getActiveHospitalPricingRule()
    │       return
    │   }
    │
    ├─► // 有医院名称 → 按名称匹配
    │   const allRules = await listHospitalPricingRules()
    │   const matched = allRules.find(r =>
    │       r.name === name ||          // 精确匹配
    │       r.name.includes(name) ||    // 规则名包含搜索名
    │       name.includes(r.name)       // 搜索名包含规则名
    │   )
    │
    ├─► if (matched) {
    │       if (!matched.isActive) {
    │           // ★ 找到匹配规则但未激活 → 自动激活
    │           const activated = await activateHospitalPricingRule(matched.id)
    │           activeRule = activated
    │       } else {
    │           // 已激活 → 直接使用
    │           activeRule = matched
    │       }
    │   } else {
    │       // 无匹配 → 回退到全局激活规则
    │       activeRule = await getActiveHospitalPricingRule()
    │   }
```

**匹配逻辑详情：**
1. 前端调用 `listHospitalPricingRules()` 获取**所有**规则（不区分激活状态）
2. 用 `Array.find()` 做三层名称匹配：
   - 规则名 === 医院名（精确）
   - 规则名包含医院名（部分匹配）
   - 医院名包含规则名（反向匹配）
3. 找到匹配 → 如果未激活则自动激活（`POST /api/hospital-pricing-rules/{id}/activate`）
4. 未找到匹配 → 回退到全局激活规则（`GET /api/hospital-pricing-rules/active`）

**设计要点：**
- 匹配成功且规则已激活时不再重复调用 activate API（减少无谓请求）
- 使用 300ms debounce 避免频繁触发（`ruleLoadingTimer`）
- 匹配失败时优雅回退到全局规则，不会阻断用户操作

---

## 7. 医院 Excel 核对

> **导航入口：** 左侧菜单 → **医院 Excel 核对**（路径 `/hospital/reconciliation`）
> Vue 组件：`src/views/hospital/reconciliation/index.vue`（约 1493 行）

这是系统的核心功能页面，完成 "上传医院发货 Excel → 自动解析 → 按规则校对 → 导出账单/结款函 → 审核" 的完整闭环。

### 7.1 进入页面（自动加载规则）

```
用户操作：从菜单进入"医院 Excel 核对"页面
```

**前端调用链：**

```
onMounted() (第1111行)
    │
    ├─► operatorName = userStore.info.userName   // 自动填入当前用户名
    │
    └─► loadHospitalRule('')  // 无医院名称时加载全局激活规则（详见 6.7）
            │
            └─► name 为空字符串 → 直接获取全局激活规则:
                    try {
                        isRuleLoading = true
                        activeRule = await getActiveHospitalPricingRule()
                            │  文件: src/api/hospital/pricingRulesApi.ts
                            │  HTTP: GET /api/hospital-pricing-rules/active
                            │
                            │  后端: HospitalPricingRuleController.getActiveRule()
                            │       → HospitalPricingRuleRepository.findByIsActiveTrue()
                            │       → 返回 PricingRuleResponse
                            │
                            │  前端: normalizeRuleRecord()
                            │       → normalizePricingRules(record.rules)
                            │       → validatePricingRules(rules)
                    } catch {
                        activeRule = null
                    } finally {
                        isRuleLoading = false
                    }
```

**页面状态：**
- 加载中：显示 "正在加载当前启用规则..."
- 加载成功：显示 "当前启用规则：标准灭菌计费规则（v1.0）" + 启用所有操作按钮
- 加载失败：显示红色警告 "当前未加载到后端医院规则，已禁止按本地默认规则计算"

**注意：** `onMounted` 调用 `loadHospitalRule('')`（空字符串），只加载全局激活规则。
上传 Excel 后再次调用 `loadHospitalRule(hospitalName.value)` 才会触发名称匹配和自动激活（见 6.7 节）。

---

### 7.2 上传 Excel 并解析

```
用户操作：拖拽或点击上传 Excel 文件
```

**前端调用链：**

```
ElUpload onChange → handleUploadChange(uploadFile) (第1127行)
    │
    └─► handleHospitalUpload(file) (第1132行)
            │
            ├─► 校验：effectiveRules.value 是否为 null
            │   └─ 为 null → errorMessage = "医院规则尚未加载成功..."
            │
            ├─► workbook = await readHospitalWorkbook(file, effectiveRules.value)
            │       │
            │       │  文件: src/views/hospital/reconciliation/ 相关解析工具函数
            │       │
            │       │  步骤：
            │       │  1. 用 XLSX 库（SheetJS）读取文件为 workbook 对象
            │       │  2. 遍历每个 sheet：
            │       │     a. 寻找表头行（headerRowIndex）—— 按 cleaning.summaryKeywords 跳过汇总行
            │       │     b. 识别正式数据行（dataRows）
            │       │     c. 提取各列字段：发货日期、订单号、类型(高温/低温)、包名、包装材料等
            │       │  3. 生成 sheetMetas（标题文本、日期范围、医院展示名）
            │       │  4. 返回 hospitalWorkbook { sheetNames, rows, previews, sheetMetas, fileName }
            │       │
            │       └─► 更新状态：uploadedFile, hospitalWorkbook
            │
            ├─► // ★ 医院名称检测：优先使用文件名，Excel 内容检测作为后备
            │   const detected = file.name.replace(/\.[^.]+$/, '')
            │       || resolveSettlementHospitalName(workbook.sheetMetas)
            │       || ''
            │   hospitalName.value = detected
            │
            └─► // ★ 按检测到的医院名称自动匹配并激活计费规则
                loadHospitalRule(hospitalName.value)
                    │
                    └─► 详见 6.7 节 — 按规则名称自动匹配逻辑
```

**注意：** 这一步纯粹在前端完成，使用 SheetJS (xlsx) 库在浏览器中解析 Excel。没有调用后端。

**解析结果展示：**
- 显示上传文件名
- 显示每个 sheet 的总行数、明细行数、表头行位置
- 可供用户预览后再点"开始校对"

---

### 7.3 开始校对

```
用户操作：点击"开始校对"按钮
```

**前端调用链：**

```
handleProcess() (第1152行)
    │
    ├─► 校验：hospitalWorkbook 和 activeRule 都必须存在
    │
    └─► processedRows = hospitalWorkbook.rows.map(row =>
            processHospitalRow(row, activeRule.rules)
        )
            │
            │  文件: src/views/hospital/reconciliation/ 相关的校对函数
            │
            │  processHospitalRow 对每一行执行以下逻辑：
            │
            │  1. cleaning 预处理：
            │     - trimPackagingMaterial → 去除包装材料字段前后空格
            │     - dropSummaryRows → 跳过含"合计/小计/总计"的汇总行
            │
            │  2. 类型识别（高温 vs 低温）：
            │     - 根据"类型"列判断属于高温灭菌还是低温灭菌
            │
            │  3. 包装材料识别：
            │     - 无纺布 → 走 nonWoven 定价
            │     - 纸塑袋 → 走 paperPlastic 定价
            │     - selfPackedKeywords → 识别医院自行打包标记
            │
            │  4. 高温灭菌定价（按 nonWoven 或 paperPlastic）：
            │     - 计算单包价格 flatPerPackagePrice
            │     - 应用阶梯阈值 flatRateThreshold
            │     - 计算最低收费保护 minCharge
            │     - 纸塑袋额外加袋子费（根据袋型尺寸 bagSizes 匹配）
            │
            │  5. 低温灭菌定价（按 nonWoven 或 paperPlastic）：
            │     - 按阶梯价格 tierPrices 查找对应档位
            │     - 应用最低单件收费 minSingleCharge
            │     - 纸塑袋额外加袋子费
            │
            │  6. 包装收费（packaging）：
            │     - 按关键词匹配包装收费项目
            │     - 按选项匹配对应价格
            │
            │  7. 小件识别（needle）：
            │     - 按关键词匹配小件器械
            │     - 超过阈值 threshold 的触发折算 foldRatio
            │
            │  8. 物流费用（logistics）：
            │     - 按天合并，每天 feePerTrip
            │     - 跨天时间点 dayBoundaryHour 判断
            │
            │  9. 状态标记：
            │     - corrected（已更正）— 价格有变化
            │     - unchanged（未变更）— 价格无变化
            │     - warning（警告）— 数据异常但可处理
            │     - skipped（跳过）— 无法处理
            │
            └─► 返回 ProcessedRow { ...rawData, status, expectedUnitPrice,
                                     correctedTotalPrice, difference, pricingRule, notes }
```

**重要：** "校对"只用前端 JavaScript 计算，不调后端。activeRule 中加载的规则内容在浏览器本地执行定价算法。这保证了即使后端不可用，用户也能在本地完成校对预览。

**校对结果展示：**
- 表格显示：行号、工作表、发货日期、包名、包装材料、器械数、原单价、规则单价、原总价、修正总价、差额、状态
- 状态标签：绿色"已更正"、灰色"未变更"、黄色"警告"、红色"跳过"
- 支持排序、搜索、分页

---

### 7.4 保存校对记录

```
用户操作：校对完成后，输入医院名称，点击"保存本次校对"
```

**前端调用链：**

```
handleSaveHistory() (第1370行)
    │
    ├─► 校验：uploadedFile、activeRule、processedRows 都存在
    │
    ├─► 构建 payload：
    │       file: File                      ← 原始 Excel 文件
    │       hospitalName: string
    │       operatorName: string
    │       ruleId: activeRule.id
    │       ruleName: activeRule.name
    │       ruleVersion: activeRule.version
    │       summary: { total, corrected, unchanged, warning, skipped, totalDifference }
    │       rows: ProcessedRow[]            ← 全部校对行
    │
    └─► saveHospitalReconciliation(payload)
            │  文件: src/api/hospital/reconciliationsApi.ts (第3行)
            │
            │  ★ Multipart 上传：
            │     FormData.append("payload_json", JSON.stringify({ hospitalName, operatorName, ... }))
            │     FormData.append("source_file", file)
            │
            │  HTTP: POST /api/hospital-reconciliations
            │       Content-Type: multipart/form-data
```

**后端调用链：**

```
POST /api/hospital-reconciliations (Multipart)
    │
    └─► HospitalReconciliationController.saveJob(
            @RequestPart("payload_json") String payloadJson,
            @RequestPart("source_file") MultipartFile sourceFile
        )
            │
            ├─► 解析 payloadJson → 各字段提取
            │
            ├─► 保存上传文件：
            │       uploadDir/hospitalName/versionNo_timestamp_originalFileName.xlsx
            │       path = Paths.get(appConfig.getUpload().getDir())
            │           .resolve(hospitalName)
            │           .resolve(versionNo + "_" + timestamp + "_" + originalFileName)
            │       Files.createDirectories(path.getParent())
            │       sourceFile.transferTo(path)
            │
            ├─► 计算版本号（同一医院自增）：
            │       Optional<Integer> maxVerNo = jobRepo.findMaxVersionNoByHospitalName(hospitalName)
            │       versionNo = maxVerNo.orElse(0) + 1
            │
            ├─► 构建并保存核对任务（Job）：
            │       HospitalReconciliationJob job = new HospitalReconciliationJob()
            │       job.setHospitalName(...)
            │       job.setSourceFileName(...)
            │       job.setSourceFilePath(...)
            │       job.setRuleId(...)           // 规则快照
            │       job.setRuleName(...)
            │       job.setRuleVersion(...)
            │       job.setVersionNo(versionNo)
            │       job.setTotalRows(...)       // 统计信息
            │       job.setCorrectedRows(...)
            │       job.setUnchangedRows(...)
            │       job.setTotalDifference(...)
            │       job.setRowsJson(JsonUtils.toJson(rows))  // ★ 双写策略
            │
            │       jobRepo.save(job) → jobId
            │
            ├─► 逐行保存核对行（Row）：
            │       rows.forEach(rowPayload → {
            │           HospitalReconciliationRow row = new HospitalReconciliationRow()
            │           row.setJobId(jobId)
            │           row.setSheetName(...)
            │           row.setRowNumber(...)
            │           ... (所有字段)
            │           rowRepo.save(row)
            │       })
            │
            └─► 返回 ReconciliationJobResponse { id, hospitalName, versionNo, ... }
```

**双写策略说明：**
- `rows_json`（LONGTEXT）：在 Job 表中冗余存储一份完整的行数据 JSON。用于快速查询、API 返回时避免 JOIN。
- `hospital_reconciliation_row` 表：结构化存储每行数据。用于精确查询、统计分析、行级别溯源。
- 两处数据保持同步更新。

---

### 7.5 导出账单 Excel

```
用户操作：校对完成后，点击"导出结果"按钮
```

**前端调用链：**

```
handleExport() (第1175行)
    │
    ├─► 构建 rowsPayload（全部校对行数据）
    │
    └─► try {
            blob = await downloadBlob(
                '/api/hospital-reconciliations/export-template-bill',
                { hospitalName, rows, sheetMetas }
            )
            │
            │  文件: reconciliation/index.vue (第1454行)
            │  方式: fetch POST，接收 application/octet-stream
            │       headers: { Content-Type: application/json, Authorization: Bearer xxx }
            │       body: JSON.stringify(data)
            │
            triggerDownload(blob, fileName)  // 创建 a 标签下载
            await logExport('result', fileName)  // 记录导出日志
        } catch {
            降级方案 — 用 SheetJS (xlsx) 在前端直接生成 Excel 下载
            ElMessage.success('后端导出失败，已使用前端降级方式导出')
        }
```

**后端调用链：**

```
POST /api/hospital-reconciliations/export-template-bill
    │
    └─► HospitalReconciliationController.exportTemplateBill(HospitalBillTemplateExportRequest)
            │
            ├─► 加载账单模板：
            │       从 application-dev.yml app.template.bill 路径读取模板 .xlsx 文件
            │       例如: ../backend/docs/test/媛尚美账单处理后.xlsx
            │       workbook = new XSSFWorkbook(Files.newInputStream(templatePath))
            │
            ├─► 按 sheet 分组处理数据行：
            │       Map<String, List<BillRowItem>> groupsBySheet
            │
            ├─► 对每个 sheet group：
            │       cloneSheet(0) → 复制模板第一个 sheet
            │       renameSheet → 设置为对应的科室名
            │       writeSheetFromTemplate(sheet, rows)
            │           │
            │           ├─► 过滤掉 skipped 状态的行
            │           ├─► 计算行数差 delta = 数据行数 - 模板行数
            │           ├─► 解除旧的合并单元格
            │           ├─► shiftRows 调整行高
            │           ├─► 逐行写入数据（复制样式）
            │           │     row.setCell(0, instrumentCount)
            │           │     row.setCell(1, packName)
            │           │     ... (按模板列映射)
            │           ├─► 写入标题行
            │           ├─► 写入汇总行
            │           └─► 重新合并单元格
            │
            ├─► 删除模板 sheet（第0个）
            │
            ├─► 添加 Logo（如有）→ K2:N4 区域
            │
            └─► ServletOutputStream outputStream = response.getOutputStream()
                    workbook.write(outputStream)  // ★ 直接写入 HTTP 响应流（二进制）
                    response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    response.setHeader("Content-Disposition", "attachment; filename=账单_xxx.xlsx")

    返回: Blob (Excel .xlsx 文件)
```

---

### 7.6 导出复核 Excel

```
用户操作：点击"导出复核"按钮（只导出 warning 行）
```

**前端调用链：**

```
handleExportWarnings() (第1233行)
    │
    ├─► warnings = processedRows.filter(row => row.status === 'warning')
    │
    └─► 纯前端生成（不调后端）：
            用 SheetJS (xlsx) 创建 workbook
            sheet 名: "异常提醒"
            列: 科室工作表、数据行号、发货日期、发货单号、包名、包装材料、
                器械数、原单价、原总价、规则单价、修正总价、差额、规则、备注
            XLSX.writeFile(wb, fileName)
```

**此操作不调用后端。** 在浏览器中直接生成 Excel 并下载。

---

### 7.7 导出结款函 Excel

```
用户操作：点击"导出结款函"按钮
```

**前端调用链：**

```
handleExportSettlement() (第1257行)
    │
    ├─► buildSettlementLetterData(processedRows, effectiveRules, hospitalName, sheetMetas, fileName)
    │   │
    │   └─► 汇总计算：
    │       - 灭菌费总额（按类型分组汇总）
    │       - 物流费总额
    │       - 大写金额转换
    │       - 生成结款日期范围文本
    │
    ├─► resolveSettlementTemplate(effectiveRules, hospitalName, sheetMetas)
    │       │
    │       └─► 按 hospitalName 和 sheetMetas 匹配结款函模板
    │           - 优先精确匹配 hospitalName
    │           - 其次关键词匹配
    │           - 最后用默认模板
    │
    ├─► 尝试1: downloadBlob('/api/hospital-reconciliations/export-html-settlement', payload)
    │       → 下载结款函 HTML（优先方案）
    │
    ├─► 尝试2（失败时）: downloadBlob('/api/hospital-reconciliations/export-template-settlement', payload)
    │       → 下载结款函 Excel（降级方案）
    │
    └─► logExport('settlement', fileName)  // 记录导出日志
```

**后端调用链（Excel 结款函）：**

```
POST /api/hospital-reconciliations/export-template-settlement
    │
    └─► HospitalReconciliationController.exportTemplateSettlement(HospitalSettlementTemplateExportRequest)
            │
            ├─► 加载结款函模板：
            │       app.template.settlement 配置的路径
            │       例如: ../backend/docs/结款函模板2.xlsx
            │
            ├─► 写入结款函数据：
            │       - 公司名称、标题、日期范围
            │       - 受函单位（医院名称）
            │       - 费用明细行（灭菌费、物流费等 feeItems）
            │       - 大写金额、小写金额
            │       - 银行账户信息
            │       - 结语/说明文字
            │
            ├─► feeItems 行插入逻辑：
            │       shiftRows 在汇总行上方插入费用行
            │       动态调整单元格坐标
            │
            └─► 返回 Excel 二进制流

POST /api/hospital-reconciliations/export-html-settlement
    │
    └─► HospitalReconciliationController.exportHtmlSettlement(HospitalSettlementTemplateExportRequest)
            │
            ├─► 调用 _buildSettlementPrintHtml(request) 生成 HTML 字符串
            │       │
            │       ├─► CSS: A4 打印样式、@page margins、print-color-adjust: exact
            │       ├─► 表格: 6列费用明细表 {序号, 项目, 金额, 备注}
            │       ├─► 银行信息: 开户行、账号
            │       └─► 说明条款: "若月末未收到反馈，默认视为接受本结算"
            │
            └─► 返回 text/html，Content-Disposition: attachment
```

---

### 7.8 打印账单 HTML

```
用户操作：点击"打印账单"按钮
```

**前端调用链：**

```
handlePrintBill() (第1319行)
    │
    └─► printHtml('/api/hospital-reconciliations/print-template-bill', {
            hospitalName, rows, sheetMetas
        })
            │
            │  fetch POST → 获取 HTML 字符串
            │  window.open('_blank') → 新窗口
            │  printWindow.document.write(html) → 写入 HTML
            │  printWindow.document.close() → 触发渲染
            │  浏览器自动弹出打印对话框
```

**后端调用链：**

```
POST /api/hospital-reconciliations/print-template-bill
    │
    └─► HospitalReconciliationController.printTemplateBill(HospitalBillTemplateExportRequest)
            │
            ├─► 调用 _buildBillPrintHtml(request) 生成 HTML
            │       │
            │       ├─► 整体布局: A4 纵向，margin 1cm
            │       ├─► 页眉: 医院名称 + 账单标题
            │       ├─► 表头: 序号、灭菌日期/单号、包名称、数量、单价、金额、备注
            │       ├─► 表身: 按行输出数据（跳过 skipped）
            │       │     列宽比例: 5% / 16% / 30% / 8% / 12% / 12% / 12%
            │       ├─► 表尾: 汇总行（含大写金额）
            │       ├─► CSS print-color-adjust: exact（保留背景色）
            │       └─► thead { display: table-header-group }（跨页重复表头）
            │
            └─► 返回 text/html（直接作为页面内容）
```

---

### 7.9 打印结款函 HTML

```
用户操作：点击"打印结款函"按钮
```

**前端调用链：**

```
handlePrintSettlement() (第1345行)
    │
    └─► 与 7.7 构建相同的 settlement 数据
        └─► printHtml('/api/hospital-reconciliations/print-template-settlement', payload)
```

**后端调用链：**

```
POST /api/hospital-reconciliations/print-template-settlement
    │
    └─► HospitalReconciliationController.printTemplateSettlement(HospitalSettlementTemplateExportRequest)
            │
            └─► 调用 _buildSettlementPrintHtml(request) 生成 HTML
                    │
                    ├─► 银行信息: 公司名称 + 开户行 + 账号
                    ├─► 受函信息: 医院名称 + 日期范围
                    ├─► 费用明细表格
                    ├─► 大写金额 + 小写金额
                    └─► 结语条款
```

---

### 7.10 审核管理

```
用户操作：在历史记录列表中点击"通过"或"拒绝"按钮
```

**前端调用链：**

```
handleUpdateReview(jobId, reviewStatus) (第1413行)
    │
    │  reviewStatus: 'approved' | 'rejected'
    │
    └─► updateHospitalReconciliationReview(jobId, {
            reviewStatus,
            reviewComment: reviewCommentDrafts[jobId] ?? '',
            reviewerName: operatorName,  // 当前操作人姓名
        })
            │  文件: src/api/hospital/reconciliationsApi.ts (第38行)
            │  HTTP: PATCH /api/hospital-reconciliations/{jobId}/review
            │  method: 'PATCH'
```

**后端调用链：**

```
PATCH /api/hospital-reconciliations/{jobId}/review
    │
    └─► HospitalReconciliationController.updateReview(jobId, ReconciliationReviewRequest)
            │
            ├─► HospitalReconciliationJobRepository.findById(jobId)
            │
            ├─► job.setReviewStatus(request.getReviewStatus())  // pending → approved/rejected
            │       job.setReviewComment(request.getReviewComment())
            │       job.setReviewerName(request.getReviewerName())
            │
            └─► HospitalReconciliationJobRepository.save(job)
                    SQL: UPDATE hospital_reconciliation_job
                         SET review_status = ?, review_comment = ?, reviewer_name = ?
                         WHERE id = ?
```

**注意：** `ReconciliationReviewRequest` 中的 `reviewStatus`、`reviewComment`、`reviewerName` 字段已移除 `@JsonProperty` 注解，
后端统一使用 camelCase 命名，前端直接发送驼峰格式字段名。

---

### 7.11 查看历史记录

```
用户操作：进入页面自动加载，或输入医院名称过滤
```

**前端调用链：**

```
loadHistory(hospitalName) (第1430行)
    │
    └─► historyItems = await listHospitalReconciliations(hospitalName)
            │  文件: src/api/hospital/reconciliationsApi.ts (第31行)
            │  HTTP: GET /api/hospital-reconciliations?hospital_name=xxx
```

**后端调用链：**

```
GET /api/hospital-reconciliations?hospital_name=xxx
    │
    └─► HospitalReconciliationController.listJobs(hospitalName)
            │
            ├─► 如果 hospitalName 非空：
            │       HospitalReconciliationJobRepository.findByHospitalNameOrderByCreatedAtDesc(name)
            │   else：
            │       HospitalReconciliationJobRepository.findAllByOrderByCreatedAtDesc()
            │
            └─► 每个 job 构建 ReconciliationJobResponse
                    │
                    ├─► includeRows = false（列表查询不返回行明细，节省带宽）
                    ├─► 加载导出日志 exportLogRepo.findByJobId(jobId)
                    │
                    └─► 返回列表
```

**历史记录展示：**
- 每个记录显示：编号、医院名、版本号、文件名、统计信息、审核状态
- 支持审核操作：通过/拒绝，填写审核意见
- 自动刷新：保存或审核后自动重新加载

---

## 附录 A：前端文件组织结构

```
src/views/hospital/
├── pricing-rules/
│   └── index.vue              # 计费规则管理页面（规则列表 + 左右分栏编辑器）
├── reconciliation/
│   └── index.vue              # Excel 核对主页面（上传 + 校对 + 导出 + 审核）

src/api/hospital/
├── pricingRulesApi.ts         # 规则 CRUD API 函数
├── pricingRules.ts            # 规则规整化（normalizePricingRules、validatePricingRules）
├── reconciliationsApi.ts      # 核对任务 CRUD API 函数
```

## 附录 B：后端关键类一览

```
controller/
├── AuthController.java              # 登录认证、Token 刷新、获取用户信息
├── UserController.java              # 用户 CRUD
├── RoleController.java              # 角色 CRUD + 菜单分配
├── MenuController.java              # 菜单 CRUD + 菜单树构建
├── ApiController.java               # API 权限 CRUD
├── HospitalPricingRuleController.java   # ★ 规则 CRUD + 激活
├── HospitalReconciliationController.java # ★ 核对全流程（上传/保存/审核/导出/打印）

entity/
├── BaseEntity.java                  # 实体基类（id, createdAt, updatedAt）
├── User.java, Role.java, Menu.java, Api.java  # RBAC 实体
├── HospitalPricingRule.java         # ★ 计费规则实体
├── HospitalReconciliationJob.java   # ★ 核对任务实体
├── HospitalReconciliationRow.java   # ★ 核对行数据实体
├── HospitalReconciliationExportLog.java # ★ 导出日志实体

repository/
├── HospitalPricingRuleRepository.java   # 规则查询 + 全量停用
├── HospitalReconciliationJobRepository.java     # job 查询 + 版本号计算
├── HospitalReconciliationRowRepository.java     # row 批量存储
├── HospitalReconciliationExportLogRepository.java # 导出历史查询

dto/request/hospital/
├── SavePricingRuleRequest.java       # 保存规则请求
├── ReconciliationReviewRequest.java  # 审核请求
├── CreateExportLogRequest.java       # 创建导出日志
├── BillRowItem.java                  # 导出行数据
├── BillSheetMeta.java               # sheet 元数据
├── HospitalBillTemplateExportRequest.java     # 账单导出请求
├── SettlementFeeRow.java            # 结款函费用行
├── HospitalSettlementTemplateExportRequest.java # 结款函导出请求

dto/response/hospital/
├── PricingRuleResponse.java          # 规则响应
├── ReconciliationJobResponse.java    # 核对任务响应
├── ReconciliationExportLogResponse.java # 导出日志响应
├── TemplateRefResponse.java          # 模板引用响应

common/
├── JsonUtils.java                    # Jackson JSON 序列化/反序列化工具
├── Result.java                       # 统一响应包装 { code, message, data }
├── GlobalExceptionHandler.java       # 全局异常拦截

config/
├── DataInitializer.java              # ★ 数据库初始化（默认用户/角色/菜单/权限）
├── SecurityConfig.java               # Spring Security 配置

security/
├── JwtTokenProvider.java             # JWT Token 生成/验证
├── JwtAuthenticationFilter.java      # JWT 请求拦截过滤器
├── UserDetailsImpl.java              # Spring Security UserDetails 实现
├── UserDetailsServiceImpl.java       # 从数据库加载用户信息
├── JwtAuthEntryPoint.java            # 未认证请求处理
```

## 附录 C：数据流全景图

```
┌──────────────────────────────────────────────────────────────────────┐
│                           浏览器 (Vue 3)                              │
│                                                                      │
│  ┌─────────────┐   ┌──────────────────┐   ┌──────────────────────┐  │
│  │ 登录页面     │   │ 规则管理页面      │   │ 核对管理页面          │  │
│  │ fetchLogin() │   │ listRules()      │   │ getActiveRule()      │  │
│  │             │   │ createRule()     │   │ upload + parse()     │  │
│  │             │   │ updateRule()     │   │ processRow() ⚡本地   │  │
│  │             │   │ activateRule()   │   │ saveHistory()        │  │
│  │             │   │ deleteRule()     │   │ exportBill()         │  │
│  │             │   │                 │   │ exportSettlement()   │  │
│  └──────┬──────┘   └────────┬─────────┘   └──────────┬───────────┘  │
│         │                   │                         │              │
└─────────┼───────────────────┼─────────────────────────┼──────────────┘
          │                   │                         │
          ▼                   ▼                         ▼
    ┌──────────┐     ┌──────────────┐        ┌───────────────────┐
    │ /api/v1/ │     │ /api/        │        │ /api/             │
    │ base/    │     │ hospital-    │        │ hospital-         │
    │ access_  │     │ pricing-     │        │ reconciliations   │
    │ token    │     │ rules        │        │                   │
    └────┬─────┘     └──────┬───────┘        └────────┬──────────┘
         │                  │                          │
         ▼                  ▼                          ▼
┌──────────────────────────────────────────────────────────────────────┐
│                     Spring Boot 后端 (Java 17)                        │
│                                                                      │
│  ┌───────────┐  ┌────────────────────┐  ┌─────────────────────────┐ │
│  │AuthCtrl   │  │PricingRuleCtrl     │  │ReconciliationCtrl       │ │
│  │ + JWT     │  │ CRUD + Activate    │  │ Save + Review + Export   │ │
│  └─────┬─────┘  └─────────┬──────────┘  │ + Print HTML            │ │
│        │                  │              │ + Template Excel (POI)  │ │
│        │                  │              └───────────┬─────────────┘ │
│        │                  │                          │              │
└────────┼──────────────────┼──────────────────────────┼──────────────┘
         │                  │                          │
         ▼                  ▼                          ▼
┌──────────────────────────────────────────────────────────────────────┐
│                        MySQL 数据库                                   │
│                                                                      │
│  backend_user              hospital_pricing_rule                     │
│  backend_role              hospital_reconciliation_job               │
│  backend_menu              hospital_reconciliation_row               │
│  backend_api               hospital_reconciliation_export_log        │
│  backend_role_menus                                                    │
│  backend_role_apis                                                     │
│  backend_user_roles                                                    │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 附录 D：修复记录

### D.1 导出账单 Excel 显示"文件格式或文件扩展名无效"

**问题：** 点击"导出结果"下载的 xlsx 文件无法打开，显示"文件格式或文件扩展名无效"。

**根因：** 前端 `handleExport()` 发送 POST 请求到后端 `/api/hospital-reconciliations/export-template-bill`，请求体中的 `BillRowItem.notes` 字段类型不匹配：
- 前端 `ProcessedRow.notes` 类型为 `string[]` → 发送 JSON 数组 `["..."]`
- 后端 `BillRowItem.notes` 类型为 `Map<String, Object>` → Jackson 反序列化数组为 Map 时抛出异常 → Spring Boot 返回 HTTP 400

由于前端 `handleExport()` 对外层 try-catch 的 400 错误无 UI 提示（直接走 SheetJS 降级），且降级方案有时也可能产生无效文件，导致用户看到的最终文件不可用。

**修复**（`BillRowItem.java`）：
- `notes` 字段: `Map<String, Object>` → `List<String>`
- 添加 `@JsonIgnoreProperties(ignoreUnknown = true)` 类注解，忽略 JSON 中多余的未知字段

**涉及文件：**
- `src/main/java/com/hospital/backend/dto/request/hospital/BillRowItem.java`

### D.2 `defaultEmptyRules()` 中小件识别配置为空导致计费错误

**问题：** 启用"标准灭菌计费规则"后，更改袋费（纸塑袋）单价后校对得到错误结果（如 27.5 元而非预期的 14.5 元）。

**根因：** `defaultEmptyRules()` 生成的 needle 默认配置为 `{ threshold: 0, foldRatio: 0, keywords: [] }`。当 `threshold = 0` 且 `keywords = []` 时，小件器械（如"针"、"穿刺针"等）无法被识别，导致 foldRatio 折叠折算无法触发，计费时小件按常规器械收费。

**修复**（`pricing-rules/index.vue`）：
- needle 默认值改为 `{ threshold: 5, foldRatio: 5, keywords: ['针', '小件', '探针', '穿刺针', '缝合针'] }`
- 阈值设为 5 件，折叠比例为 1/5
- 优化提示文字从"小件器械（尚未配置）"改为"小件器械（按 5 件=1 件折算）"

**涉及文件：**
- `src/views/hospital/pricing-rules/index.vue` — `defaultEmptyRules()` 函数

### D.3 移除"关联医院"概念，改为按规则名称自动匹配

**问题：** 创建计费规则时需要填写"关联医院"字段，但医院与规则的关联逻辑不清晰，导致许多规则创建时无法确定关联医院，用户体验差。同时医院名称检测使用 Excel 内容提取（如"口腔科"）优先级高于文件名（如"大钰口腔"），导致匹配错误。

**变更内容：**
1. **前端计费规则页面**（`pricing-rules/index.vue`）：
   - 移除"关联医院"（hospitalName）表单字段
   - 移除"关联医院"侧边栏筛选器
   - 移除输入规则名称时自动填充关联医院逻辑
   - 保存时不再发送 hospitalName 字段

2. **前端核对页面**（`reconciliation/index.vue`）：
   - 新增 `loadHospitalRule(name)` 函数，按规则名称自动匹配（见 6.7 节）
   - 自动激活匹配到的规则（无需手动启用）
   - 医院名称检测改为**优先使用文件名**，Excel 内容检测作为后备
   - 医院名称输入框保持可编辑，用户可手动修正

**设计要点：**
- 规则名称可以包含医院名（如"大钰口腔灭菌计费规则"），系统自动匹配
- 匹配规则：精确匹配 → 规则名包含搜索名 → 搜索名包含规则名
- 上传 Excel 后自动检测医院名 → 按名称匹配规则 → 自动激活 → 开始校对

**涉及文件：**
- `src/views/hospital/pricing-rules/index.vue` — 移除 hospitalName 相关 UI 和逻辑
- `src/views/hospital/reconciliation/index.vue` — 新增 loadHospitalRule、名称匹配逻辑
- `src/api/hospital/pricingRulesApi.ts` — 新增 listHospitalPricingRules 导入

### D.4 低温无纺布阶梯余数计费修正

**问题：** 低温无纺布灭菌的阶梯余数（超出整阶梯的零散件数）按错误单价计费。

**根因：** 低温灭菌阶梯计价中，余数部分未按正确的单价计算。

**修复：** 将余数计费基数调整为正确的单价（22 元/件）。

**涉及文件：**
- 后端计费逻辑相关类

> 文档生成时间：2026-05-07
> 对应代码版本：Java backend — Spring Boot 3.2.5 / Vue 3 frontend
