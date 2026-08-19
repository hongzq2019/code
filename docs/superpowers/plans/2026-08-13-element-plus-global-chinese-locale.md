# Element Plus 全站中文本地化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让全站 Element Plus 组件使用官方简体中文语言包。

**Architecture:** 在 Vue 应用唯一的 Element Plus 安装入口导入官方 `zh-cn` 语言包，并通过 `ElementPlus` 插件选项全局注入。各页面不单独传入 locale，因此日期选择器及未来使用的 Element Plus 组件共享同一配置。

**Tech Stack:** Vue 3、TypeScript、Element Plus 2.9.8、Vite。

## Global Constraints

- 仅修改前端应用入口 `forend/src/main.ts`。
- 使用 `element-plus/es/locale/lang/zh-cn` 官方语言包，不创建自定义语言包。
- 不逐页传递 `locale` 属性。
- 不修改后端、开奖分页、历史点位分析接口或 ECharts 图表逻辑。
- 不新增测试文件；执行 `npm run build` 验证。
- 项目不是 Git 仓库；不创建提交、分支或工作树。

---

### Task 1：全局注入简体中文语言包

**Files:**
- Modify: `forend/src/main.ts`

**Interfaces:**
- Consumes: `element-plus` 的默认插件导出和 `element-plus/es/locale/lang/zh-cn` 默认语言包。
- Produces: 全局 Element Plus locale 配置，所有已挂载及后续挂载的 Element Plus 组件可读取简体中文文案。

- [ ] **Step 1: 导入 Element Plus 官方简体中文语言包。**

  在 Element Plus 导入后增加：

  ```ts
  import zhCn from 'element-plus/es/locale/lang/zh-cn'
  ```

- [ ] **Step 2: 以全局 locale 选项安装 Element Plus。**

  将应用挂载链中的 Element Plus 安装调用替换为：

  ```ts
  createApp(App).use(createPinia()).use(router).use(ElementPlus, { locale: zhCn }).mount('#app')
  ```

  保留现有 Pinia、路由、样式导入和挂载顺序。

- [ ] **Step 3: 保持入口注释为多行格式。**

  注释应位于应用挂载调用之前，并说明 Element Plus 已配置简体中文语言包：

  ```ts
  /**
   * 装配状态管理、路由和已配置简体中文的 UI 组件库后挂载 Vue 应用。
   */
  ```

### Task 2：构建验证与范围复核

**Files:**
- Verify: `forend/src/main.ts`
- Verify: `forend/package.json`

**Interfaces:**
- Consumes: Task 1 写入的 `zhCn` 全局 Element Plus 配置。
- Produces: 可由 Vite 打包的前端产物。

- [ ] **Step 1: 执行生产构建。**

  在 `forend` 目录执行：

  ```powershell
  npm run build
  ```

  预期：`vue-tsc -b && vite build` 以退出码 0 结束。

- [ ] **Step 2: 静态核对本地化范围。**

  确认 `main.ts` 同时包含以下两处内容：

  ```ts
  import zhCn from 'element-plus/es/locale/lang/zh-cn'
  ```

  ```ts
  .use(ElementPlus, { locale: zhCn })
  ```

  确认没有修改 `Analysis.vue` 的日期选择器属性，也没有新增测试文件、后端文件或自定义 locale 文件。

