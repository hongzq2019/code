# 大乐透项目代码可维护性重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不改变大乐透 MVP 的 API、页面路由或业务行为的前提下，整理后端和前端结构、格式和类型边界。

**Architecture:** 后端保留模块化单体包结构，控制器使用明确 DTO 并把 HTTP 映射从领域服务中移出。前端将 API 类型、请求函数、可复用展示组件与视图分离，使用统一 CSS 变量和异步状态模式。

**Tech Stack:** Java 21, Spring Boot 3, JUnit 5, Maven, Vue 3, TypeScript, Vite, Pinia, Element Plus, Axios, Docker Compose.

## Global Constraints

- REST 路径必须继续使用 `/api/v1/**`，响应必须为 `{ code, message, data, traceId }`。
- 前端路由必须保留 `/`、`/draws`、`/predict`、`/login`、`/admin`。
- 前区 1–35 选 5、后区 1–12 选 2、47 维特征和加权推荐公式不得改变。
- 所有推荐展示必须保留“仅供数据分析与娱乐，不构成中奖概率承诺”的含义。
- 默认 PostgreSQL、Flyway 和 Compose 服务定义不得改变。
- 当前目录不是 Git 仓库；每项完成后运行验证命令，但不执行提交。

---

## 文件结构

| 路径 | 职责 |
|---|---|
| `backend/.../api/ApiResponse.java` | 统一 API 响应与 trace id |
| `backend/.../lottery/dto/*.java` | 开奖请求、分页响应与领域到 HTTP 映射 |
| `backend/.../prediction/dto/*.java` | 预测请求与响应映射 |
| `backend/.../auth/dto/*.java`、`admin/dto/*.java` | 认证、审批、导入等显式 HTTP 契约 |
| `backend/.../*Controller.java` | 仅绑定 HTTP、调用服务、返回 DTO |
| `backend/.../*Service.java`、`DrawStore.java` | 业务规则和内存样例数据 |
| `forend/src/types/*.ts` | 所有 API、开奖、推荐和用户类型 |
| `forend/src/api/*.ts` | 按领域划分的 Axios 请求函数 |
| `forend/src/components/*.vue` | 号码球和风险提示复用组件 |
| `forend/src/views/*.vue` | 页面组合、表单、加载和错误状态 |
| `forend/src/styles/*.css` | 变量、基础样式和页面布局 |

### Task 1: 锁定后端 HTTP 映射契约

**Files:**
- Create: `backend/src/test/java/com/dlt/predictor/lottery/dto/DrawResponseTest.java`
- Create: `backend/src/test/java/com/dlt/predictor/prediction/dto/PredictionResponseTest.java`
- Create: `backend/src/main/java/com/dlt/predictor/lottery/dto/DrawResponse.java`
- Create: `backend/src/main/java/com/dlt/predictor/lottery/dto/DrawPageResponse.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/dto/PredictionResponse.java`
- Modify: `backend/src/main/java/com/dlt/predictor/lottery/LotteryController.java`
- Modify: `backend/src/main/java/com/dlt/predictor/prediction/PredictionController.java`

**Interfaces:**
- Consumes: `Draw(Long id, String issueNo, LocalDate drawDate, DltNumbers numbers, String sourceUrl)`.
- Produces: `DrawResponse(Long id, String issueNo, LocalDate drawDate, DltNumbers numbers, String sourceUrl)` and `DrawPageResponse(List<DrawResponse> items, int page, int size, long total)`.
- Produces: `PredictionResponse(DltNumbers numbers, Map<Integer, Double> frontScores, Map<Integer, Double> backScores, List<SimilarDraw> similarDraws, String modelVersion, Instant generatedAt, String disclaimer)`.

- [ ] **Step 1: Write the failing draw DTO mapping test**

```java
@Test
void maps_draw_without_changing_json_field_values() {
    Draw draw = new Draw(7L, "2025007", LocalDate.of(2025, 1, 1),
            DltNumbers.of(List.of(1, 2, 3, 4, 5), List.of(1, 2)), "manual");

    DrawResponse response = DrawResponse.from(draw);

    assertEquals(7L, response.id());
    assertEquals("2025007", response.issueNo());
    assertEquals(draw.numbers(), response.numbers());
}
```

- [ ] **Step 2: Verify it fails because `DrawResponse` is absent**

Run: `mvn test -Dtest=DrawResponseTest`

Expected: test compilation fails with `cannot find symbol DrawResponse`.

- [ ] **Step 3: Write the failing prediction DTO mapping test**

```java
@Test
void keeps_recommendation_payload_fields() {
    SimilarDraw draw = new SimilarDraw(1L, "2025001", LocalDate.now().minusDays(8),
            DltNumbers.of(List.of(1, 2, 3, 4, 5), List.of(1, 2)), 0.9);
    RecommendationResult result = new RecommendationEngine().recommend(List.of(draw), LocalDate.now());

    PredictionResponse response = PredictionResponse.from(result);

    assertEquals("mvp-1.0", response.modelVersion());
    assertEquals(result.disclaimer(), response.disclaimer());
}
```

- [ ] **Step 4: Implement the DTO records and update controllers**

```java
public record DrawResponse(Long id, String issueNo, LocalDate drawDate,
                           DltNumbers numbers, String sourceUrl) {
    public static DrawResponse from(Draw draw) {
        return new DrawResponse(draw.id(), draw.issueNo(), draw.drawDate(),
                draw.numbers(), draw.sourceUrl());
    }
}

return ApiResponse.ok(new DrawPageResponse(items, page, boundedSize, total));
```

Use the same field names as the previous serialised `Draw` and `RecommendationResult` records. Keep query parameter names and `topK` behavior unchanged.

- [ ] **Step 5: Run focused and full backend tests**

Run: `mvn test -Dtest=DrawResponseTest,PredictionResponseTest` then `mvn test`

Expected: both focused tests and the full suite pass.

### Task 2: 整理后端认证、管理和领域代码

**Files:**
- Create: `backend/src/main/java/com/dlt/predictor/auth/dto/CredentialsRequest.java`
- Create: `backend/src/main/java/com/dlt/predictor/auth/dto/LoginResponse.java`
- Create: `backend/src/main/java/com/dlt/predictor/admin/dto/ApprovalRequest.java`
- Create: `backend/src/main/java/com/dlt/predictor/admin/dto/ImportDrawRequest.java`
- Create: `backend/src/test/java/com/dlt/predictor/auth/AuthServiceTest.java`
- Modify: `backend/src/main/java/com/dlt/predictor/auth/AuthController.java`
- Modify: `backend/src/main/java/com/dlt/predictor/auth/AuthService.java`
- Modify: `backend/src/main/java/com/dlt/predictor/auth/UserAccount.java`
- Modify: `backend/src/main/java/com/dlt/predictor/admin/AdminController.java`
- Modify: `backend/src/main/java/com/dlt/predictor/lottery/DltNumbers.java`
- Modify: `backend/src/main/java/com/dlt/predictor/lottery/DrawStore.java`
- Modify: `backend/src/main/java/com/dlt/predictor/prediction/FeatureEncoder.java`
- Modify: `backend/src/main/java/com/dlt/predictor/prediction/RecommendationEngine.java`

**Interfaces:**
- Consumes: `CredentialsRequest(String username, String password)` with the current Bean Validation constraints.
- Produces: `LoginResponse(String accessToken, String role, boolean mustChangePassword)`.
- Consumes: `ApprovalRequest(boolean approved)` and `ImportDrawRequest(String issueNo, LocalDate drawDate, List<Integer> frontNumbers, List<Integer> backNumbers)`.
- Produces: unchanged registration message, login payload, approval outcome and import payload field names.

- [ ] **Step 1: Write the authentication characterization test**

```java
@Test
void registers_pending_user_then_allows_login_after_approval() {
    service.register("reader", "password1");
    assertThrows(IllegalArgumentException.class, () -> service.login("reader", "password1"));

    service.approve("reader", true);

    assertEquals("reader", service.login("reader", "password1").username());
}
```

- [ ] **Step 2: Verify the characterization test is green before purely structural extraction**

Run: `mvn test -Dtest=AuthServiceTest`

Expected: PASS, proving existing behavior before moving DTOs and formatting code.

- [ ] **Step 3: Replace anonymous HTTP payloads with named records**

```java
public record ImportDrawRequest(
        @NotBlank String issueNo,
        @NotNull LocalDate drawDate,
        @NotNull List<Integer> frontNumbers,
        @NotNull List<Integer> backNumbers) { }

public Draw importDraw(ImportDrawRequest request) {
    return drawStore.upsert(request.issueNo(), request.drawDate(),
            request.frontNumbers(), request.backNumbers());
}
```

Keep `AuthService` methods package-visible/public as currently consumed by tests and controllers. Split every compressed class into conventional multi-line Java formatting, add `private final` fields and descriptive local names.

- [ ] **Step 4: Extract small pure helpers without changing algorithms**

```java
private static double recencyAdjustedWeight(SimilarDraw draw, LocalDate referenceDate) {
    long ageDays = max(0, DAYS.between(draw.drawDate(), referenceDate));
    return draw.similarity() * exp(-ageDays / 730.0);
}
```

Retain the exact range rules in `DltNumbers`, index positions in `FeatureEncoder`, and score ordering in `RecommendationEngine`.

- [ ] **Step 5: Run full backend verification**

Run: `mvn test` and `mvn -q -DskipTests package`

Expected: both commands exit with status 0.

### Task 3: 建立前端类型与领域 API 客户端

**Files:**
- Create: `forend/src/types/api.ts`
- Create: `forend/src/types/lottery.ts`
- Create: `forend/src/types/prediction.ts`
- Create: `forend/src/types/auth.ts`
- Create: `forend/src/api/client.ts`
- Create: `forend/src/api/auth.ts`
- Create: `forend/src/api/lottery.ts`
- Create: `forend/src/api/prediction.ts`
- Create: `forend/src/api/admin.ts`
- Modify: `forend/src/api.ts`
- Modify: `forend/src/stores/session.ts`
- Modify: `forend/src/router.ts`

**Interfaces:**
- `ApiEnvelope<T>` is `{ code: number; message: string; data: T; traceId: string }`.
- `Draw` remains `{ id, issueNo, drawDate, numbers }` to match the backend response.
- `createPrediction(request: PredictionRequest): Promise<PredictionResult>` sends the unchanged `/api/v1/dlt/predictions` payload.
- `useSessionStore().login(username, password)` and `useSessionStore().logout()` retain their current public names.

- [ ] **Step 1: Add compile-time types before moving any page call sites**

```ts
export interface ApiEnvelope<T> {
  code: number
  message: string
  data: T
  traceId: string
}

export interface DltNumbers {
  front: number[]
  back: number[]
}
```

- [ ] **Step 2: Define one Axios unwrap helper and domain request functions**

```ts
export async function listDraws(params: DrawQuery): Promise<DrawPage> {
  const response = await client.get<ApiEnvelope<DrawPage>>('/api/v1/dlt/draws', { params })
  return response.data.data
}
```

Do not change base URL, Authorization header storage, request paths, fields, timeout or router guard rules.

- [ ] **Step 3: Replace old broad `api.ts` imports incrementally**

```ts
const page = await listDraws({ issueNo: issueNo.value, size: 100 })
rows.value = page.items
total.value = page.total
```

Remove all `any` from views and the session store by using `UserAccount`, `PendingUser`, `PredictionResult`, `SyncResult` and `ImportDrawRequest` types.

- [ ] **Step 4: Run the type checker**

Run: `npm run type-check`

Expected: exits 0 with no implicit `any`, missing export, or response-shape errors. If node modules cannot be installed, record the exact npm failure and manually run `rg -n '\bany\b' src` to verify no application `any` remains.

### Task 4: 提取前端复用组件和统一页面结构

**Files:**
- Create: `forend/src/components/NumberBalls.vue`
- Create: `forend/src/components/RiskDisclaimer.vue`
- Create: `forend/src/components/PageState.vue`
- Create: `forend/src/styles/tokens.css`
- Create: `forend/src/styles/base.css`
- Modify: `forend/src/App.vue`
- Modify: `forend/src/style.css`
- Modify: `forend/src/views/Home.vue`
- Modify: `forend/src/views/Draws.vue`
- Modify: `forend/src/views/Predict.vue`
- Modify: `forend/src/views/Login.vue`
- Modify: `forend/src/views/Admin.vue`

**Interfaces:**
- `NumberBalls` accepts `front: number[]`, `back: number[]`, and optional `size?: 'small' | 'default'`.
- `RiskDisclaimer` accepts optional `message?: string` and defaults to the existing disclaimer meaning.
- `PageState` accepts `loading: boolean`, `error?: string`, and `empty: boolean`; named `default` slot contains loaded data.

- [ ] **Step 1: Create number presentation components with no behavior change**

```vue
<NumberBalls :front="draw.numbers.front" :back="draw.numbers.back" size="small" />
```

Render numbers with `String(number).padStart(2, '0')`, retain front/back colour meanings, and do not sort or alter received data.

- [ ] **Step 2: Replace repeated disclaimer and number-ball markup in all views**

```vue
<RiskDisclaimer :message="result.disclaimer" />
```

`Home.vue`, `Draws.vue`, and `Predict.vue` must use `NumberBalls`; `App.vue` must continue showing a global disclaimer in the footer.

- [ ] **Step 3: Standardize asynchronous view state**

```ts
const loading = ref(false)
const errorMessage = ref('')

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const page = await listDraws({ issueNo: issueNo.value, size: 100 })
    rows.value = page.items
    total.value = page.total
  } catch (error) {
    errorMessage.value = toUserMessage(error)
  } finally {
    loading.value = false
  }
}
```

Use this pattern in historical draws, prediction submission, pending-user loading and manual sync. Preserve the same buttons and outcomes.

For `Predict.vue`, assign `result.value = await createPrediction(request)`; for `Admin.vue`, assign `users.value = await listPendingUsers()` and `sync.value = await runDltSync()`; retain the existing approval call then invoke `load()`.

- [ ] **Step 4: Consolidate stylesheet tokens and format Vue files**

```css
:root {
  --color-primary: #155eef;
  --color-front-ball: #e63545;
  --color-back-ball: #2563eb;
  --color-text-muted: #667085;
  --space-4: 1rem;
}
```

Move base resets and shared layout to `styles/base.css`; preserve visual colors, breakpoints and layout behavior. Format every `.vue`, `.ts`, `.json`, `.yml` and Java file with conventional multi-line indentation.

- [ ] **Step 5: Run frontend verification**

Run: `npm run type-check` then `npm run build`

Expected: both exit 0 and `forend/dist/index.html` exists.

### Task 5: 回归与部署配置验证

**Files:**
- Modify: `README.md` only if commands/paths need correction after file movement.
- Modify: `.gitignore` only if new local formatter/cache output must be ignored.
- Verify: `docker-compose.yml`, `backend/Dockerfile`, `forend/Dockerfile`, `forend/nginx.conf`.

- [ ] **Step 1: Check compatibility mechanically**

Run: `rg -n '"/api/v1|/api/v1|path:' backend/src/main/java forend/src; rg -n 'LATEST_DRAW|CUSTOM_NUMBERS|mvp-1\.0|娱乐' backend/src forend/src`

Expected: API roots, input types, model version and disclaimer remain present.

- [ ] **Step 2: Execute complete backend verification**

Run: `mvn test && mvn -q -DskipTests package`

Expected: both commands exit 0.

- [ ] **Step 3: Execute complete frontend verification**

Run: `npm run type-check && npm run build`

Expected: both commands exit 0. If package installation remains blocked, report the exact installation error and do not claim frontend build success.

- [ ] **Step 4: Validate deployment manifest**

Run: `$env:DOCKER_CONFIG=(Join-Path $PWD '.docker-config'); docker compose --env-file .env.example config --quiet`

Expected: exits 0.

## Plan self-review

- Spec coverage: Task 1–2 cover backend DTO boundaries, controller/service separation and Java formatting; Task 3–4 cover frontend types, domain APIs, components, asynchronous states and CSS variables; Task 5 covers compatibility and all required verification.
- Placeholder scan: no unresolved implementation placeholders; each task names files, interfaces and verification commands.
- Type consistency: frontend request types preserve `inputType`, `topK`, `frontNumbers`, `backNumbers`; backend DTOs preserve existing field names used by the front end.
