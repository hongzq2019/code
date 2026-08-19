# 管理导航拆分与待审核用户分页设计

## 目标

将当前聚合在一个“管理”页面中的“数据同步”和“待审核用户”拆分为两个独立的管理员入口；待审核用户列表改为 PostgreSQL 上的 MyBatis-Plus 服务端分页查询。

## 范围

- 新增管理员页面：数据同步页与待审核用户页。
- 顶部导航为管理员显示“数据同步”和“待审核用户”两个菜单项。
- 保留旧 `/admin` 路由，并重定向到数据同步页。
- 将账户读写从 `AuthService` 的内存 `ConcurrentHashMap` 迁移到现有 PostgreSQL `sys_user` 表。
- 采用 MyBatis-Plus 的 `Page` 与分页拦截器查询待审核账户。

不修改开奖数据、Milvus 同步、预测逻辑，也不新增测试文件。

## 前端设计

### 路由与导航

| 路径 | 页面 | 权限 |
| --- | --- | --- |
| `/admin` | 重定向至 `/admin/sync` | 无额外页面内容 |
| `/admin/sync` | 数据同步 | 已登录管理员 |
| `/admin/reviews` | 待审核用户 | 已登录管理员 |

根布局仅对管理员显示“数据同步”和“待审核用户”。两个新路由沿用现有 `auth` 与 `admin` 元数据，由全局路由守卫统一限制。

### 页面职责

- `AdminSync.vue`：保留现有手动同步按钮、同步中的禁用状态、成功摘要和错误展示。
- `PendingUsers.vue`：加载分页数据，展示用户名、注册时间和审批操作；底部使用 Element Plus 分页组件，默认每页 10 条，不提供页大小选择。

审批成功后重新请求当前页；若该页变为空且当前页大于第一页，则请求前一页，确保管理员不会停留在空页。

### API 与类型

前端 `listPendingUsers` 改为接收 `page` 和固定的 `size`，返回 `{ items, page, size, total }`。审批接口与数据同步接口的路径、请求体和响应语义保持不变。

## 后端设计

### MyBatis-Plus 账户持久化

现有 Flyway `V1__initial_schema.sql` 已包含 `sys_user` 表，因此不增加重复账户表。新增 MyBatis-Plus 依赖、`@MapperScan` 配置和 `MybatisPlusInterceptor`；拦截器只配置 PostgreSQL 分页插件。

新增账户持久化对象和 Mapper，字段映射到 `sys_user` 的 `id`、`username`、`password_hash`、`role`、`status`、`must_change_password` 和 `created_at`。`AuthService` 改为通过 Mapper 完成：

- 启动时按用户名幂等创建初始管理员；密码仍从 `BOOTSTRAP_ADMIN_PASSWORD` 读取并通过 BCrypt 存储。
- 注册时检查用户名唯一性并写入 `PENDING` 状态。
- 登录时按用户名查询并验证状态及 BCrypt 密码。
- 审批时仅更新审核状态。

### 待审核分页接口

`GET /api/v1/admin/users` 接受 `page`（默认 1）和 `size`（默认 10，最大 100）查询参数。服务层按 `created_at` 降序读取 `PENDING` 账户，使用 MyBatis-Plus `Page` 返回当前页记录和总数。

控制器将结果包装为现有 API 信封中的分页响应。越界页返回空 `items` 和真实 `total`；前端审批后的回退逻辑负责提升交互体验。

## 错误处理与兼容性

- 用户名唯一性冲突继续返回现有的参数错误语义。
- 未找到用户、未审批账户和密码错误继续使用现有错误消息。
- 数据库异常交由 Spring 的默认异常处理链记录，前端继续通过统一错误信息展示。
- 旧 `/admin` 的重定向避免书签和已有入口失效。

## 验证

- 不新增测试文件，遵循既有要求。
- 执行 `mvn package`，验证 Java 编译、已有测试以及 Spring Boot 打包。
- 执行 `npm run build`，验证 Vue 类型检查与生产构建。
- 代码审查确认旧 `/admin` 正确重定向，两个新页面均受管理员路由守卫保护，审核列表 API 使用 MyBatis-Plus `Page`。
