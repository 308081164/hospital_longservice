# 医疗灭菌计费管理系统 - 后端

基于 Spring Boot 3 + MyBatis + MySQL 的医院灭菌计费管理系统后端服务。

## 技术栈

| 技术 | 版本 |
|------|------|
| Java | 17 |
| Spring Boot | 3.2.5 |
| MyBatis | 3.0.3 |
| MySQL | 8.0+ |
| JWT | 0.12.5 (jjwt) |
| Apache POI | 5.2.5 |
| SpringDoc OpenAPI | 2.5.0 |
| Spring Security | 6.x |
| Maven | 3.x |

## 快速开始

### 环境要求

- JDK 17+
- MySQL 8.0+
- Maven 3.6+

### 数据库初始化

1. 创建 MySQL 数据库：

```sql
CREATE DATABASE IF NOT EXISTS hospital_backend
  DEFAULT CHARSET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

2. 执行建表脚本：

```bash
mysql -u root -p hospital_backend < create_tables.sql
```

### 配置文件

修改 `src/main/resources/application-dev.yml` 中的数据源配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/hospital_backend
    username: root
    password: 你的密码
```

### 启动项目

```bash
# 开发环境
mvn spring-boot:run

# 或编译后运行
mvn clean package -DskipTests
java -jar target/backend-1.0.0.jar
```

服务默认运行在 `http://localhost:8000`。

### 默认用户

首次启动（空数据库）后会自动创建以下测试用户（密码均为 `123456`）：

| 用户名 | 角色 |
|--------|------|
| user1 | R_USER |
| user2 | R_USER |

若数据库中已有用户，`DataInitializer` 会跳过初始化，旧密码不会自动更新。需要重置时执行：

```bash
docker compose down -v
docker compose up -d --build
```

## API 文档

启动后访问 `http://localhost:8000/docs` 查看 Swagger API 文档。

## 项目结构

```
src/main/java/com/hospital/backend/
├── BackendApplication.java          # 应用入口
├── common/                           # 公共工具类
│   ├── Result.java                   # 统一响应封装
│   ├── GlobalExceptionHandler.java   # 全局异常处理
│   └── JsonUtils.java                # JSON 工具
├── config/
│   ├── SecurityConfig.java           # Spring Security 配置
│   └── DataInitializer.java          # 启动数据初始化
├── controller/
│   ├── AuthController.java           # 登录/令牌刷新
│   ├── UserController.java           # 用户管理
│   ├── MenuController.java           # 菜单权限
│   ├── HospitalPricingRuleController.java  # 计费规则配置
│   └── HospitalReconciliationController.java # 对账核心业务
├── dto/
│   ├── request/                      # 请求 DTO
│   └── response/                     # 响应 DTO
├── entity/                           # 数据库实体
├── mapper/                           # MyBatis Mapper 接口
├── security/                         # JWT 安全组件
│   ├── JwtTokenProvider.java         # JWT 令牌生成/解析
│   ├── JwtAuthenticationFilter.java  # JWT 认证过滤器
│   └── UserDetailsServiceImpl.java   # 用户详情加载
└── service/
    └── PricingEngine.java            # 灭菌计费规则引擎

src/main/resources/
├── application.yml                   # 通用配置
├── application-dev.yml               # 开发环境配置
├── schema.sql                        # 简化建表 DDL
└── mapper/                           # MyBatis XML 映射文件
```

## 核心业务模块

### 医院对账流程

1. **上传 Excel**：前端上传医院发货汇总表（支持多文件、多 Sheet）
2. **解析校验**：自动识别表头和明细行，解析为结构化数据
3. **规则配对**：根据激活的计费规则重新计算单价和总价
4. **差异标记**：对比原始金额与规则计算结果，标记异常行
5. **人工修正**：查看异常详情，确认后一键修正并保存
6. **导出报表**：支持导出账单、结款函、分科室价格汇总等

### 灭菌计费规则引擎 (`PricingEngine.java`)

支持复杂的计费规则：

- **高温/低温判定**：根据灭菌类型（低温/ETO）自动分流
- **袋型检测**：自动识别纸塑袋尺寸（10/15/20/25cm）
- **阶梯定价**：低温多件阶梯套价（5件/10件/20件套）
- **小物件折算**：边匹配识别小物件关键词，按比例折算计费
- **无纺布/纸塑袋**：不同的包装材料对应不同计费逻辑
- **"双"袋规则**：自动检测"双"字并追加额外袋费
- **包装耗材收费**：包装材料额外收费叠加

## 安全说明

- 采用无状态 JWT 认证，双令牌机制（access_token + refresh_token）
- access_token 有效期 240 分钟，refresh_token 有效期 7 天
- 密码使用 BCrypt 加密存储
- 禁用 CSRF，允许跨域

## 关键 API 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/base/access_token` | 登录获取令牌 |
| POST | `/api/v1/base/refresh_token` | 刷新令牌 |
| GET | `/api/v1/base/health` | 健康检查 |
| GET | `/api/v1/base/version` | 系统版本 |
| GET | `/api/v1/menu/menus` | 获取菜单树 |
| GET | `/api/v1/user/list` | 用户列表 |
| POST | `/api/v1/hospital/pricing-rules/save` | 保存计费规则 |
| GET | `/api/v1/hospital/pricing-rules/active` | 获取激活规则 |
| POST | `/api/v1/hospital/reconciliation/upload` | 上传对账文件 |
| POST | `/api/v1/hospital/reconciliation/process` | 执行计价处理 |
| GET | `/api/v1/hospital/reconciliation/rows` | 分页查询对账行 |
| POST | `/api/v1/hospital/reconciliation/export` | 导出报表 |

## 生产部署与运维

GitHub Actions 推送 `main` 会自动构建并部署到生产环境。运维经验、P0.6 特色账单开关、MySQL/API 双校验、CI 重跑注意事项等见：

**[deploy/README.md](./deploy/README.md)**

相关：`deploy/PRODUCTION-RECOVERY.md`（网关/连库故障）、`deploy/MIGRATION.md`（Secrets 与首次部署）。
