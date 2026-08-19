# 开奖分页与历史点位分析 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将历史开奖列表改为每页 20 条的分页查询，并新增可按日期范围查看前、后区号码散点分布的“历史点位分析”页面。

**Architecture:** PostgreSQL 仍是开奖数据主库。后端在现有 `DrawRepository`、`DrawStore` 和 `LotteryController` 上增加按开奖日期区间查询的只读接口；前端复用现有开奖 DTO 与请求客户端，使用 Apache ECharts 呈现上下分区的时间散点图。历史开奖分页继续调用既有分页接口，不改变入库、预测或 Milvus 索引流程。

**Tech Stack:** Spring Boot 3.4、Java 21、Spring Data JPA、Vue 3、TypeScript、Element Plus、Apache ECharts 6.1。

## Global Constraints

- 不新增测试文件；仅执行构建验证。
- 所有 JavaDoc 使用多行格式，并置于注解之前。
- 保持 PostgreSQL 为主库，Milvus 仅为检索索引；本功能不修改预测、向量同步或官方开奖抓取流程。
- 项目不是 Git 仓库；不创建提交、分支或工作树。

---

## Task 1：增加开奖日期范围查询接口

**Files:**
- Modify: `backend/src/main/java/com/dlt/predictor/lottery/DrawRepository.java`
- Modify: `backend/src/main/java/com/dlt/predictor/lottery/DrawStore.java`
- Modify: `backend/src/main/java/com/dlt/predictor/lottery/LotteryController.java`

- [ ] **Step 1: 声明按开奖日期升序查询的 Repository 方法。**

  在 `DrawRepository` 添加派生查询，令数据库完成日期过滤和排序：

  ```java
  List<DrawEntity> findByDrawDateBetweenOrderByDrawDateAsc(LocalDate startDate, LocalDate endDate);
  ```

- [ ] **Step 2: 在 DrawStore 中暴露领域对象范围查询。**

  在 `DrawStore` 增加以下方法，将实体转换保持在 Store 层：

  ```java
  public List<Draw> findByDateRange(LocalDate startDate, LocalDate endDate) {
      return drawRepository.findByDrawDateBetweenOrderByDrawDateAsc(startDate, endDate)
              .stream()
              .map(DrawEntity::toDraw)
              .toList();
  }
  ```

  为新增公开方法添加置于注解之前的多行 JavaDoc。

- [ ] **Step 3: 增加 `/analysis` 控制器端点。**

  在 `LotteryController` 中、`/{issueNo}` 映射之前增加端点，避免被路径变量映射捕获：

  ```java
  /**
   * 查询指定开奖日期区间内的历史开奖，用于历史点位分析。
   */
  @GetMapping("/analysis")
  public ApiResponse<List<DrawResponse>> analysis(
          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
      if (endDate.isBefore(startDate)) {
          throw new IllegalArgumentException("结束日期不能早于开始日期");
      }
      return ApiResponse.ok(drawStore.findByDateRange(startDate, endDate)
              .stream()
              .map(DrawResponse::from)
              .toList());
  }
  ```

  补充 `DateTimeFormat` 和 `LocalDate` 所需导入。若类已有统一错误处理，沿用其 `IllegalArgumentException` 的响应形式。

## Task 2：为历史开奖页面接入分页控件

**Files:**
- Modify: `forend/src/views/Draws.vue`

- [ ] **Step 1: 固定每页 20 条并维护当前页。**

  在脚本区域增加分页常量和页码状态：

  ```ts
  const PAGE_SIZE = 20
  const page = ref(1)
  ```

  将加载请求改为传递当前页、固定条数及可选期号：

  ```ts
  const result = await listDraws({
    page: page.value,
    size: PAGE_SIZE,
    issueNo: issueNo.value || undefined
  })
  ```

- [ ] **Step 2: 搜索时回到第一页。**

  新增搜索处理函数，并在表单提交时调用：

  ```ts
  function handleSearch(): void {
    page.value = 1
    void load()
  }
  ```

  保持首次 `onMounted(load)` 自动加载。

- [ ] **Step 3: 在表格下方添加 Element Plus 分页器。**

  使用 `total`、`page` 和固定 `PAGE_SIZE`，页码变化时重新读取数据：

  ```vue
  <el-pagination
    v-if="total > 0"
    v-model:current-page="page"
    :page-size="PAGE_SIZE"
    :total="total"
    layout="total, prev, pager, next"
    @current-change="load"
  />
  ```

  将“最多 100 条”之类的旧文案替换为准确的分页文案。

## Task 3：添加历史点位分析接口客户端和 ECharts 组件

**Files:**
- Modify: `forend/package.json`
- Modify: `forend/package-lock.json`
- Modify: `forend/src/api/lottery.ts`
- Create: `forend/src/components/DrawScatterChart.vue`
- Create: `forend/src/views/Analysis.vue`

- [ ] **Step 1: 安装 ECharts 依赖。**

  在 `forend` 目录安装 `echarts@^6.1.0`，由 npm 同步更新 `package.json` 与 `package-lock.json`。组件从 `echarts/core` 按需导入，而不使用完整打包入口。

- [ ] **Step 2: 增加日期范围请求客户端。**

  在 `forend/src/api/lottery.ts` 添加：

  ```ts
  export async function listDrawsForAnalysis(startDate: string, endDate: string): Promise<Draw[]> {
    const response = await client.get<ApiEnvelope<Draw[]>>('/api/v1/dlt/draws/analysis', {
      params: { startDate, endDate }
    })
    return unwrap(response)
  }
  ```

- [ ] **Step 3: 实现双区域散点图组件。**

  `DrawScatterChart.vue` 接收 `Draw[]`，使用树摇导入初始化 ECharts：

  ```ts
  import { init, use, type ECharts, type EChartsCoreOption } from 'echarts/core'
  import { ScatterChart } from 'echarts/charts'
  import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
  import { CanvasRenderer } from 'echarts/renderers'

  use([ScatterChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])
  ```

  将每期 `front` 数字转换为 `[drawDate, number]` 的红色点，`back` 数字转换为同格式的蓝色点。配置两个纵向排列的 `grid`、两个时间 `xAxis` 和分别为 1–35、1–12 的 `yAxis`；tooltip 展示期号、开奖日期与号码。使用 `ResizeObserver` 在容器尺寸变化时调用 `chart.resize()`，并在组件卸载时断开 observer 和 `dispose()` 图表实例。

- [ ] **Step 4: 实现分析页面。**

  `Analysis.vue` 使用 `el-date-picker` 的 `daterange` 与 `value-format="YYYY-MM-DD"`。初始化范围为最近 12 个月（本地今天及一年前同日），并在 `onMounted` 自动查询：

  ```ts
  function formatDate(value: Date): string {
    const year = value.getFullYear()
    const month = String(value.getMonth() + 1).padStart(2, '0')
    const day = String(value.getDate()).padStart(2, '0')
    return `${year}-${month}-${day}`
  }

  function defaultRange(): [string, string] {
    const endDate = new Date()
    const startDate = new Date(endDate)
    startDate.setFullYear(startDate.getFullYear() - 1)
    return [formatDate(startDate), formatDate(endDate)]
  }
  ```

  查询时调用 `listDrawsForAnalysis`；加载、错误和空数据继续使用项目已有的 `PageState`。存在数据时渲染 `<DrawScatterChart :draws="draws" />`，页面说明清楚标注上方为前区（1–35、红色）、下方为后区（1–12、蓝色）。

## Task 4：注册路由与导航入口

**Files:**
- Modify: `forend/src/router.ts`
- Modify: `forend/src/App.vue`

- [ ] **Step 1: 注册公开分析路由。**

  在路由配置导入 `Analysis`，添加无需登录的路由：

  ```ts
  {
    path: '/analysis',
    name: 'analysis',
    component: Analysis
  }
  ```

- [ ] **Step 2: 添加全局导航菜单项。**

  在 `App.vue` 的主导航中加入指向 `/analysis` 的“历史点位分析”入口，与“历史开奖”同级，保持既有管理端“数据同步”和“待审核用户”菜单不变。

## Task 5：构建验证与交付检查

**Files:**
- Verify: `backend/pom.xml`
- Verify: `forend/package.json`

- [ ] **Step 1: 构建后端。**

  在 `backend` 目录执行：

  ```powershell
  mvn package
  ```

  确认新增 JPA 派生查询、日期参数绑定和控制器编译通过。

- [ ] **Step 2: 构建前端。**

  在 `forend` 目录执行：

  ```powershell
  npm run build
  ```

  确认 ECharts 按需导入、散点组件、路由和分页模板均通过 TypeScript 与 Vite 构建。

- [ ] **Step 3: 对照规格复核。**

  确认历史开奖为每页 20 条；分析页默认最近 12 个月；`/analysis` 使用 ISO 日期范围并拒绝结束日期早于开始日期；图表为前区红色上图与后区蓝色下图；没有新增测试文件，也没有改动预测、Milvus 或开奖抓取流程。
