# Element Plus 全站中文本地化设计

## 目标

将前端应用的 Element Plus 组件库全局切换为简体中文，使历史点位分析中的日期选择器显示中文月份、星期和操作文案，并让其他现有及后续 Element Plus 组件使用同一语言配置。

## 范围

- 修改 `forend/src/main.ts`。
- 从 `element-plus/es/locale/lang/zh-cn` 导入官方简体中文语言包。
- 将该语言包作为 `ElementPlus` 安装选项传入：

  ```ts
  import zhCn from 'element-plus/es/locale/lang/zh-cn'

  createApp(App).use(createPinia()).use(router).use(ElementPlus, { locale: zhCn }).mount('#app')
  ```

- 日期选择器的日历面板以及所有 Element Plus 内置中文文案统一生效。

## 非范围

- 不逐页传递 `locale` 属性。
- 不创建或维护自定义语言包。
- 不修改后端、开奖分页、历史点位分析数据接口或 ECharts 图表逻辑。
- 不新增测试文件。

## 验证

- 执行 `npm run build`，确认 TypeScript 和 Vite 生产构建通过。
- 手动打开“历史点位分析”页时，日期选择器应显示中文日期控件文案。
