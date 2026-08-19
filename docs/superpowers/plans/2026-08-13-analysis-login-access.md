# 历史点位分析登录访问 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 仅向已登录用户显示并开放历史点位分析页面。

**Architecture:** 复用现有 `session.loggedIn` 状态控制导航菜单可见性，并复用路由全局 `meta.auth` 守卫限制直接访问。无需修改后端或会话存储，未登录用户继续由既有守卫跳转到 `/login`。

**Tech Stack:** Vue 3、TypeScript、Vue Router、Pinia、Vite。

## Global Constraints

- 修改 `forend/src/App.vue` 和 `forend/src/router.ts`。
- 分析菜单使用 `session.loggedIn` 控制显示。
- `/analysis` 使用 `meta: { auth: true }`，不要求管理员角色。
- 不增加登录后的原页面回跳参数。
- 不修改登录、会话存储、后端、数据接口或其他公开页面。
- 不新增测试文件；执行 `npm run build` 验证。
- 项目不是 Git 仓库；不创建提交、分支或工作树。

---

### Task 1：限制分析菜单和分析路由访问

**Files:**
- Modify: `forend/src/App.vue`
- Modify: `forend/src/router.ts`

**Interfaces:**
- Consumes: `useSessionStore()` 提供的 `session.loggedIn` 布尔状态，以及路由守卫读取的 `to.meta.auth`。
- Produces: 登录用户可见且可访问的 `/analysis`；未登录用户不见菜单且直接访问时被导航至 `/login`。

- [ ] **Step 1: 为分析菜单增加登录可见性条件。**

  在 `forend/src/App.vue` 将现有菜单项替换为：

  ```vue
  <el-menu-item v-if="session.loggedIn" index="/analysis">历史点位分析</el-menu-item>
  ```

  保留“历史开奖”公开可见，保留管理员菜单现有 `session.role === 'ADMIN'` 条件。

- [ ] **Step 2: 为分析路由增加认证元数据。**

  在 `forend/src/router.ts` 将分析路由替换为：

  ```ts
  { path: '/analysis', component: AnalysisView, meta: { auth: true } },
  ```

  不新增 `admin` 元数据。现有守卫中的以下条件会自动将未登录请求转至登录页：

  ```ts
  if (to.meta.auth && !session.loggedIn) {
    return '/login'
  }
  ```

### Task 2：构建验证与访问范围复核

**Files:**
- Verify: `forend/src/App.vue`
- Verify: `forend/src/router.ts`
- Verify: `forend/package.json`

**Interfaces:**
- Consumes: Task 1 中菜单的 `v-if` 与路由 `meta.auth`。
- Produces: 可通过 TypeScript 和 Vite 生产构建的前端访问控制配置。

- [ ] **Step 1: 执行前端生产构建。**

  在 `forend` 目录执行：

  ```powershell
  npm run build
  ```

  预期：`vue-tsc -b && vite build` 以退出码 0 结束。

- [ ] **Step 2: 静态核对菜单与路由。**

  确认以下代码分别存在：

  ```vue
  <el-menu-item v-if="session.loggedIn" index="/analysis">历史点位分析</el-menu-item>
  ```

  ```ts
  { path: '/analysis', component: AnalysisView, meta: { auth: true } },
  ```

  确认没有为 `/analysis` 添加 `admin: true`，也没有修改登录、会话存储、后端文件或新增测试文件。

