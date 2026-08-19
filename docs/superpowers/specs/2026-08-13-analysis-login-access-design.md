# 历史点位分析登录访问设计

## 目标

让“历史点位分析”仅在用户登录后可见和访问。

## 访问控制

- 在 `forend/src/App.vue` 中，为“历史点位分析”菜单项增加 `v-if="session.loggedIn"`。
- 在 `forend/src/router.ts` 中，为 `/analysis` 路由增加 `meta: { auth: true }`。
- 复用现有全局路由守卫：未登录用户直接访问 `/analysis` 时返回 `/login`。
- 已登录用户继续可从菜单进入分析页，不要求管理员角色。

## 非范围

- 不修改登录、会话存储、后端权限或数据接口。
- 不增加登录后的原页面回跳参数。
- 不影响历史开奖等公开页面，以及管理员菜单的角色控制。
- 不新增测试文件。

## 验证

- 执行 `npm run build`，确认 TypeScript 与 Vite 构建通过。
- 静态确认菜单使用 `session.loggedIn`，且 `/analysis` 具有 `meta: { auth: true }`。
