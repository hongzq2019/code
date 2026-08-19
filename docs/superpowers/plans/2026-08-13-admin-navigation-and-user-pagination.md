# 管理导航拆分与待审核用户分页 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将管理员的数据同步与用户审核拆分为独立入口，并以 MyBatis-Plus 在 PostgreSQL 中分页查询待审核用户。

**Architecture:** 保留 JPA 管理开奖数据，同时新增 MyBatis-Plus 访问既有 `sys_user` 表。`AuthService` 从内存 Map 迁移为 Mapper 驱动的账户服务；管理员接口返回统一的分页响应。前端分别承载同步与审核页面，旧 `/admin` 路由重定向到数据同步页。

**Tech Stack:** Spring Boot 3.4、Java 21、PostgreSQL、Flyway、MyBatis-Plus 3.5.17、Vue 3、TypeScript、Pinia、Vue Router、Element Plus。

## Global Constraints

- 账户数据使用既有 PostgreSQL `sys_user` 表，不新建重复账户表。
- 使用 `mybatis-plus-spring-boot3-starter:3.5.17` 与 `mybatis-plus-jsqlparser:3.5.17`；分页插件使用 PostgreSQL `DbType.POSTGRE_SQL`。
- 待审核接口默认第 1 页、每页 10 条，`size` 最大为 100；固定由前端传入 10，不提供页大小选择器。
- 保留 `/admin`，并重定向至 `/admin/sync`；两个新页面必须使用既有 `auth` 与 `admin` 守卫。
- 不新增测试文件；需要适配现有 `AuthServiceTest` 以保持 Maven 测试编译和执行通过。
- 当前工作目录不是 Git 仓库，不执行提交。

---

## 文件结构

| 文件 | 责任 |
| --- | --- |
| `backend/pom.xml` | 引入 MyBatis-Plus 和分页解析器依赖。 |
| `backend/src/main/java/com/dlt/predictor/config/MybatisPlusConfiguration.java` | 扫描账户 Mapper 并注册 PostgreSQL 分页拦截器。 |
| `backend/src/main/java/com/dlt/predictor/auth/UserAccountEntity.java` | 映射 `sys_user` 表中的账户持久化字段。 |
| `backend/src/main/java/com/dlt/predictor/auth/UserAccountMapper.java` | 提供 `sys_user` 的 MyBatis-Plus CRUD 与分页查询能力。 |
| `backend/src/main/java/com/dlt/predictor/auth/AuthService.java` | 使用 Mapper 完成引导管理员、注册、登录、审批及待审分页。 |
| `backend/src/main/java/com/dlt/predictor/auth/PendingUserPageResponse.java` | 定义管理员用户列表的分页 API 响应。 |
| `backend/src/main/java/com/dlt/predictor/admin/AdminController.java` | 接收分页参数并返回分页审核列表。 |
| `backend/src/test/java/com/dlt/predictor/auth/AuthServiceTest.java` | 使用 Mapper mock 适配账户服务构造方法。 |
| `forend/src/views/AdminSync.vue` | 只显示官方开奖同步操作及结果。 |
| `forend/src/views/PendingUsers.vue` | 显示待审核用户表格、分页与审批操作。 |
| `forend/src/views/Admin.vue` | 删除，避免保留聚合管理页。 |
| `forend/src/router.ts` | 新增两个管理员路由及旧路由重定向。 |
| `forend/src/App.vue` | 显示两个管理员菜单。 |
| `forend/src/api/admin.ts` | 将审核列表请求改为分页调用。 |
| `forend/src/types/admin.ts` | 定义审核列表的分页请求和响应类型。 |

### Task 1: 接入 MyBatis-Plus 并迁移账户存储

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/dlt/predictor/config/MybatisPlusConfiguration.java`
- Create: `backend/src/main/java/com/dlt/predictor/auth/UserAccountEntity.java`
- Create: `backend/src/main/java/com/dlt/predictor/auth/UserAccountMapper.java`
- Modify: `backend/src/main/java/com/dlt/predictor/auth/AuthService.java`
- Modify: `backend/src/test/java/com/dlt/predictor/auth/AuthServiceTest.java`

**Interfaces:**
- Consumes: 既有 PostgreSQL 表 `sys_user`，其字段定义来自 `V1__initial_schema.sql`。
- Produces: `AuthService#pendingUsers(int page, int size): Page<UserAccountEntity>`，以及现有 `register`、`login`、`approve` 的持久化实现。

- [ ] **Step 1: 添加 MyBatis-Plus 依赖**

在 `backend/pom.xml` 的依赖区加入：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.17</version>
</dependency>
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-jsqlparser</artifactId>
    <version>3.5.17</version>
</dependency>
```

- [ ] **Step 2: 配置 Mapper 扫描和 PostgreSQL 分页插件**

创建 `MybatisPlusConfiguration`：

```java
@Configuration
@MapperScan("com.dlt.predictor.auth")
public class MybatisPlusConfiguration {

    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }
}
```

- [ ] **Step 3: 定义账户实体和 Mapper**

使用单独的实体保留现有 `UserAccount` 作为领域登录模型：

```java
@TableName("sys_user")
public class UserAccountEntity {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private String username;
    @TableField("password_hash")
    private String passwordHash;
    private String role;
    private String status;
    @TableField("must_change_password")
    private boolean mustChangePassword;
    @TableField("created_at")
    private Instant createdAt;

    UserAccount toDomain() {
        return new UserAccount(username, passwordHash, UserAccount.Role.valueOf(role),
                UserAccount.Status.valueOf(status), mustChangePassword, createdAt);
    }

    static UserAccountEntity from(UserAccount account) {
        UserAccountEntity entity = new UserAccountEntity();
        entity.setUsername(account.username());
        entity.setPasswordHash(account.passwordHash());
        entity.setRole(account.role().name());
        entity.setStatus(account.status().name());
        entity.setMustChangePassword(account.mustChangePassword());
        entity.setCreatedAt(account.createdAt());
        return entity;
    }

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public Instant getCreatedAt() { return createdAt; }
    public void setUsername(String value) { username = value; }
    public void setPasswordHash(String value) { passwordHash = value; }
    public void setRole(String value) { role = value; }
    public void setStatus(String value) { status = value; }
    public void setMustChangePassword(boolean value) { mustChangePassword = value; }
    public void setCreatedAt(Instant value) { createdAt = value; }
}

@Mapper
interface UserAccountMapper extends BaseMapper<UserAccountEntity> {
}
```

对 `passwordHash` 添加 `@TableField("password_hash")`，对 `mustChangePassword` 添加 `@TableField("must_change_password")`，对 `createdAt` 添加 `@TableField("created_at")`，避免依赖全局命名策略。

- [ ] **Step 4: 将 AuthService 的内存流程替换为 Mapper 调用**

构造器注入 `UserAccountMapper`，在 `@PostConstruct` 中按用户名查询并只在不存在时插入初始管理员。注册、登录和审批使用 Lambda 查询/更新包装器：

```java
public Page<UserAccountEntity> pendingUsers(int page, int size) {
    Page<UserAccountEntity> result = new Page<>(page, size);
    return userAccountMapper.selectPage(result,
            Wrappers.<UserAccountEntity>lambdaQuery()
                    .eq(UserAccountEntity::getStatus, UserAccount.Status.PENDING.name())
                    .orderByDesc(UserAccountEntity::getCreatedAt));
}

public void approve(String username, boolean approved) {
    int affected = userAccountMapper.update(null,
            Wrappers.<UserAccountEntity>lambdaUpdate()
                    .eq(UserAccountEntity::getUsername, username)
                    .set(UserAccountEntity::getStatus,
                            approved ? UserAccount.Status.APPROVED.name() : UserAccount.Status.REJECTED.name()));
    if (affected == 0) {
        throw new IllegalArgumentException(USER_NOT_FOUND_MESSAGE);
    }
}
```

注册前按用户名查询；登录按用户名查询并将实体转换为 `UserAccount` 后复用原有状态和 BCrypt 校验。删除 `ConcurrentHashMap` 和无参构造器。

- [ ] **Step 5: 适配现有账户服务测试**

不创建新测试文件。更新 `AuthServiceTest`，使用 Mockito mock 的 `UserAccountMapper` 构造服务。测试中的关键结构为：

```java
@Test
void registersPendingUserThenAllowsLoginAfterApproval() {
    UserAccountMapper mapper = mock(UserAccountMapper.class);
    UserAccountEntity account = new UserAccountEntity();
    account.setUsername("reader");
    account.setPasswordHash(new BCryptPasswordEncoder().encode("password1"));
    account.setRole(UserAccount.Role.USER.name());
    account.setStatus(UserAccount.Status.PENDING.name());
    account.setCreatedAt(Instant.now());
    when(mapper.selectOne(any())).thenReturn(null, account, account);
    when(mapper.insert(any(UserAccountEntity.class))).thenReturn(1);
    when(mapper.update(isNull(), any())).thenReturn(1);

    AuthService service = new AuthService(mapper);
    service.register("reader", "password1");
    assertThrows(IllegalArgumentException.class, () -> service.login("reader", "password1"));
    service.approve("reader", true);
    account.setStatus(UserAccount.Status.APPROVED.name());
    assertEquals("reader", service.login("reader", "password1").username());
}
```

- [ ] **Step 6: 执行后端校验**

运行：`mvn package`

预期：现有测试通过，且 Spring Boot 可打包；不得新增测试源文件。

### Task 2: 提供待审核用户的 MyBatis-Plus 分页接口

**Files:**
- Create: `backend/src/main/java/com/dlt/predictor/auth/PendingUserPageResponse.java`
- Modify: `backend/src/main/java/com/dlt/predictor/admin/AdminController.java`
- Modify: `backend/src/main/java/com/dlt/predictor/auth/PendingUserResponse.java`

**Interfaces:**
- Consumes: `AuthService#pendingUsers(int page, int size)` 返回的 `Page<UserAccountEntity>`。
- Produces: `GET /api/v1/admin/users?page={page}&size={size}` 返回 `ApiResponse<PendingUserPageResponse>`，结构为 `items`、`page`、`size`、`total`。

- [ ] **Step 1: 定义分页响应类型**

创建响应 record 并提供页面转换工厂：

```java
public record PendingUserPageResponse(
        List<PendingUserResponse> items,
        int page,
        int size,
        long total) {

    static PendingUserPageResponse from(Page<UserAccountEntity> source) {
        return new PendingUserPageResponse(
                source.getRecords().stream().map(PendingUserResponse::from).toList(),
                Math.toIntExact(source.getCurrent()),
                Math.toIntExact(source.getSize()),
                source.getTotal());
    }
}
```

将 `PendingUserResponse.from` 的入参改为 `UserAccountEntity`，确保密码哈希不进入响应。

- [ ] **Step 2: 修改管理员列表接口**

将控制器方法改为：

```java
@GetMapping("/users")
ApiResponse<PendingUserPageResponse> pending(
        @RequestParam(defaultValue = "1") @Min(1) int page,
        @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
    return ApiResponse.ok(PendingUserPageResponse.from(authService.pendingUsers(page, size)));
}
```

保留审批路径 `POST /api/v1/admin/users/{username}/approval` 和请求体不变。

- [ ] **Step 3: 执行后端校验**

运行：`mvn package`

预期：请求类型和控制器均可编译，已有测试不失败。

### Task 3: 拆分前端管理员页面与菜单

**Files:**
- Create: `forend/src/views/AdminSync.vue`
- Create: `forend/src/views/PendingUsers.vue`
- Delete: `forend/src/views/Admin.vue`
- Modify: `forend/src/router.ts`
- Modify: `forend/src/App.vue`

**Interfaces:**
- Consumes: 既有 `runDltSync()`；Task 4 中的 `listPendingUsers({ page, size })` 和 `approveUser()`。
- Produces: `/admin/sync`、`/admin/reviews` 两个管理员页面；`/admin` 至 `/admin/sync` 的兼容重定向。

- [ ] **Step 1: 创建数据同步页面**

从旧 `Admin.vue` 提取同步状态和 `sync()` 方法到 `AdminSync.vue`，保留以下行为：

```vue
<el-card>
  <template #header>数据同步</template>
  <p>生产环境请配置合法的数据源、访问频率与重试策略。</p>
  <el-button type="primary" :loading="syncing" @click="sync">手动同步</el-button>
  <el-alert v-if="syncResult" type="success" :closable="false" :title="syncResult.message" />
</el-card>
```

异常通过 `toUserMessage` 写入页面的 `PageState` 或错误提示；不得改变 `runDltSync` 的请求路径。

- [ ] **Step 2: 创建待审核用户页面骨架**

从旧 `Admin.vue` 提取待审核表格、`load()` 和 `approve()` 到 `PendingUsers.vue`。表格继续展示 `username` 与 `createdAt`，审批成功后调用当前页刷新函数。

- [ ] **Step 3: 配置路由和导航**

替换单一路由为：

```ts
{ path: '/admin', redirect: '/admin/sync' },
{ path: '/admin/sync', component: AdminSyncView, meta: { auth: true, admin: true } },
{ path: '/admin/reviews', component: PendingUsersView, meta: { auth: true, admin: true } },
```

并在 `App.vue` 中将原“管理”菜单替换为：

```vue
<el-menu-item v-if="session.role === 'ADMIN'" index="/admin/sync">数据同步</el-menu-item>
<el-menu-item v-if="session.role === 'ADMIN'" index="/admin/reviews">待审核用户</el-menu-item>
```

- [ ] **Step 4: 移除旧聚合页面并执行前端构建**

删除 `forend/src/views/Admin.vue`，确认没有旧导入；运行：`npm run build`

预期：Vue 类型检查和 Vite 生产构建通过。

### Task 4: 连接前端服务端分页与审批后回退

**Files:**
- Modify: `forend/src/types/admin.ts`
- Modify: `forend/src/api/admin.ts`
- Modify: `forend/src/views/PendingUsers.vue`

**Interfaces:**
- Consumes: `GET /api/v1/admin/users?page={page}&size={size}` 返回 `{ items, page, size, total }`。
- Produces: `listPendingUsers(params: PendingUserQuery): Promise<PendingUserPage>` 及可翻页、可审批的审核列表。

- [ ] **Step 1: 定义前端分页类型并更新 API 客户端**

在 `types/admin.ts` 加入：

```ts
export interface PendingUserQuery {
  page: number
  size: number
}

export interface PendingUserPage {
  items: PendingUser[]
  page: number
  size: number
  total: number
}
```

将 API 改为：

```ts
export async function listPendingUsers(params: PendingUserQuery): Promise<PendingUserPage> {
  const response = await client.get<ApiEnvelope<PendingUserPage>>('/api/v1/admin/users', { params })
  return unwrap(response)
}
```

- [ ] **Step 2: 实现固定 10 条的页码请求与 Element Plus 分页控件**

在 `PendingUsers.vue` 定义 `const page = ref(1)`、`const pageSize = 10`、`const total = ref(0)`。加载成功后写入 `users` 和 `total`；模板在表格下加入：

```vue
<el-pagination
  v-if="total > pageSize"
  v-model:current-page="page"
  :page-size="pageSize"
  :total="total"
  layout="prev, pager, next, total"
  @current-change="load"
/>
```

- [ ] **Step 3: 实现审批后的空页回退**

审批成功后先加载当前页；如果响应的 `items` 为空、`page > 1` 且 `total < (page - 1) * pageSize + 1`，将 `page` 减 1 并再次加载。随后显示“已通过”或“已拒绝”提示。

- [ ] **Step 4: 进行端到端构建校验**

运行：

```powershell
cd D:\codeWorkSpace\backend
mvn package
cd D:\codeWorkSpace\forend
npm run build
```

预期：后端 6 个既有测试通过、后端打包成功，前端类型检查和生产构建成功。

## 自检

- 规格中的双菜单、两条受限路由、`/admin` 重定向、服务端分页、MyBatis-Plus、`sys_user` 账户持久化、固定每页 10 条和空页回退均有对应任务。
- 本计划没有占位项；使用了明确的文件、接口、命令和代码片段。
- 接口类型一致：后端 `PendingUserPageResponse` 与前端 `PendingUserPage` 都使用 `items`、`page`、`size`、`total`。
