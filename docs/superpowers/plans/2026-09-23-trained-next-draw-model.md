# 大乐透下一期开奖训练模型实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有单组相似开奖推荐替换为逻辑回归、分区向量相似转移和动态融合三组下一期开奖结果，并把定时及手动采集默认扩展到 30 页。

**Architecture:** PostgreSQL 继续作为开奖和模型版本的主库，Milvus 同时维护现有 47 维联合向量以及前区 35 维、后区 12 维分区向量。训练编排器使用 PostgreSQL 时间序列执行 5 折扩展窗口回测，生成两个基础模型和一份融合配置，再以同一批次原子激活；预测请求只加载生效批次并执行推理，不在线训练。

**Tech Stack:** Java 21、Spring Boot 3.4.5、Spring Data JPA、Flyway、Milvus Java SDK 2.4.11、PostgreSQL、Vue 3、TypeScript、Element Plus、Maven、Vitest-free frontend build verification

**Spec:** `docs/superpowers/specs/2026-09-23-trained-next-draw-model-design.md`

## Global Constraints

- PostgreSQL 是数据主库，Milvus 仅作为可重建的检索索引；预测详情始终回查 PostgreSQL。
- 定时采集与手动采集默认 `page-size=30`、`page-count=30`；手动 `pageCount` 允许 1 至 100。
- 训练至少需要 100 期有效开奖，特征窗口固定 20 期，相似转移每个分区使用最多 30 个有效近邻。
- 逻辑回归必须使用纯 Java、L2 正则化、固定超参数和确定性初始化，不引入 Python 或机器学习平台。
- 回测使用最早 60% 作为初始窗口，剩余 40% 分成 5 个连续扩展窗口验证折，任何算法不得读取目标期或未来开奖。
- 三类模型必须共享训练批次和训练水位并原子激活；失败时保留上一完整生效批次。
- 前区、后区独立计算；分数相同时号码升序，最终号码也升序；不得用随机号码补齐。
- Java 文档注释必须位于注解上方，并保持项目约定的 `/**`、空 `*` 行、说明、空 `*` 行、`*/` 格式。
- 模型结果只表示历史数据分析，不承诺中奖率。

## Review Focus

- 官方接口中途出现空页或跨页重复期号：应提前停止并按期号稳定去重，不能重复写库或重复计数；Task 1 覆盖。
- 恰好 100 期、不能被 5 整除的验证集和小折边界：必须生成非空、无重叠、无未来泄漏的折；Task 4 覆盖。
- 历史开奖被修正：联合向量与两个分区向量必须一起重建，且只触发一次自动训练；Task 2、Task 6 覆盖。
- 相似命中没有后继期、总权重为零或 Milvus 不可用：不能随机补齐，预测应返回 503；Task 5、Task 7 覆盖。
- 新批次只有部分模型成功或融合引用版本不一致：不得部分激活或混用水位；Task 3、Task 6、Task 7 覆盖。

---

### Task 1: 将官方开奖采集扩展为默认 30 页

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/java/com/dlt/predictor/lottery/SportteryDltProperties.java`
- Modify: `backend/src/main/java/com/dlt/predictor/lottery/SportteryDltClient.java`
- Modify: `backend/src/main/java/com/dlt/predictor/lottery/DltCrawlerService.java`
- Modify: `backend/src/main/java/com/dlt/predictor/admin/AdminController.java`
- Modify: `backend/src/main/java/com/dlt/predictor/admin/SyncResponse.java`
- Test: `backend/src/test/java/com/dlt/predictor/lottery/SportteryDltClientTest.java`
- Test: `backend/src/test/java/com/dlt/predictor/lottery/DltCrawlerServiceTest.java`

**Interfaces:**
- Produces: `SportteryDltClient.fetchRecentDraws(int pageCount): List<OfficialDraw>`
- Produces: `DltCrawlerService.importRecentDraws(): DltCrawlerResult`
- Produces: `DltCrawlerService.importRecentDraws(int pageCount): DltCrawlerResult`
- Produces: `POST /api/v1/admin/dlt/sync?pageCount={1..100}`

- [ ] **Step 1: Write failing pagination tests**

Use `MockRestServiceServer` with a `RestClient.Builder` to return two populated pages followed by an empty page. Assert request page numbers are `1,2,3`, duplicate `issueNo` values preserve the first occurrence, and page 4 is never requested. Add cases for invalid page counts `0` and `101` throwing `IllegalArgumentException`.

```java
assertThat(client.fetchRecentDraws(3))
        .extracting(OfficialDraw::issueNo)
        .containsExactly("2026003", "2026002", "2026001");
assertThatThrownBy(() -> client.fetchRecentDraws(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("1 至 100");
```

- [ ] **Step 2: Run the focused tests and verify failure**

Run: `cd backend; mvn -Dtest=SportteryDltClientTest,DltCrawlerServiceTest test`

Expected: compilation fails because the page-count overload and property do not exist.

- [ ] **Step 3: Implement configuration and paged fetching**

Change the configuration record and YAML to:

```java
public record SportteryDltProperties(String sourceUrl, int pageSize, int pageCount) {
}
```

```yaml
lottery:
  dlt:
    page-size: ${DLT_FETCH_PAGE_SIZE:30}
    page-count: ${DLT_FETCH_PAGE_COUNT:30}
```

In `SportteryDltClient`, make the no-argument method delegate to `properties.pageCount()`. Loop from page 1 through the requested count, stop on an empty list, and accumulate into `LinkedHashMap<String, OfficialDraw>` with `putIfAbsent`. Keep each row's exact page URL in `sourceUrl`. Reject values outside 1–100 before making a request.

- [ ] **Step 4: Thread the optional override through service and controller**

Implement:

```java
public DltCrawlerResult importRecentDraws() {
    return importRecentDraws(properties.pageCount());
}

public DltCrawlerResult importRecentDraws(int pageCount) {
    List<OfficialDraw> officialDraws = sportteryDltClient.fetchRecentDraws(pageCount);
    // existing idempotent upsert and vector synchronization
}
```

Inject `SportteryDltProperties` into `DltCrawlerService`. Update the admin endpoint to accept nullable `@RequestParam(required = false) @Min(1) @Max(100) Integer pageCount`, use the configured default when absent, and include both `fetchedCount` and `changedCount` in `SyncResponse`. The scheduled importer continues to call the no-argument method.

- [ ] **Step 5: Run focused and existing backend tests**

Run: `cd backend; mvn -Dtest=SportteryDltClientTest,DltCrawlerServiceTest test`

Expected: PASS; pagination stops on the empty page and defaults resolve to 30.

- [ ] **Step 6: Commit the acquisition change**

```powershell
git add backend/src/main/resources/application.yml backend/src/main/java/com/dlt/predictor/lottery backend/src/main/java/com/dlt/predictor/admin backend/src/test/java/com/dlt/predictor/lottery
git commit -m "feat: fetch thirty pages of dlt draws"
```

### Task 2: 同步联合向量与前后区分区向量

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/java/com/dlt/predictor/prediction/MilvusProperties.java`
- Modify: `backend/src/main/java/com/dlt/predictor/prediction/FeatureEncoder.java`
- Modify: `backend/src/main/java/com/dlt/predictor/prediction/VectorStore.java`
- Modify: `backend/src/main/java/com/dlt/predictor/prediction/VectorSearchHit.java`
- Modify: `backend/src/main/java/com/dlt/predictor/prediction/MilvusVectorStore.java`
- Modify: `backend/src/main/java/com/dlt/predictor/prediction/DrawVectorSyncService.java`
- Modify: `backend/src/main/java/com/dlt/predictor/prediction/SimilarDrawSearchService.java`
- Test: `backend/src/test/java/com/dlt/predictor/prediction/FeatureEncoderTest.java`
- Test: `backend/src/test/java/com/dlt/predictor/prediction/DrawVectorSyncServiceTest.java`

**Interfaces:**
- Produces: `FeatureEncoder.encodeJoint(DltNumbers): float[47]`
- Produces: `FeatureEncoder.encodeFront(DltNumbers): float[35]`
- Produces: `FeatureEncoder.encodeBack(DltNumbers): float[12]`
- Produces: `VectorStore.upsert(long, float[], float[], float[]): void`
- Produces: `VectorStore.searchJoint(float[], int): List<VectorSearchHit>`
- Produces: `VectorStore.searchFront(float[], int): List<VectorSearchHit>`
- Produces: `VectorStore.searchBack(float[], int): List<VectorSearchHit>`

- [ ] **Step 1: Write failing encoder and all-or-failed synchronization tests**

Assert that front hit positions equal `1/sqrt(5)`, back hit positions equal `1/sqrt(2)`, all other positions are zero, and both vectors have unit norm. Mock `VectorStore.upsert` to fail after receiving one draw and assert `markVectorReady` is not called while `markVectorFailed` is called once.

```java
assertThat(FeatureEncoder.encodeFront(numbers)).hasSize(35);
assertThat(norm(FeatureEncoder.encodeFront(numbers))).isCloseTo(1.0, within(1.0e-6));
verify(vectorStore).upsert(eq(7L), any(float[].class), any(float[].class), any(float[].class));
verify(drawStore, never()).markVectorReady(7L);
```

- [ ] **Step 2: Run tests and verify failure**

Run: `cd backend; mvn -Dtest=FeatureEncoderTest,DrawVectorSyncServiceTest test`

Expected: compilation fails on the new encoder and store signatures.

- [ ] **Step 3: Add the transition collection configuration and schema**

Add `transition-collection-name: ${MILVUS_TRANSITION_COLLECTION_NAME:dlt_draw_transition_vectors}` and extend `MilvusProperties` with `transitionCollectionName`.

Keep the existing collection's `draw_id + vector(47)` schema. Lazily create the transition collection with fields `draw_id`, `front_vector(35)`, and `back_vector(12)` and create a separate `AUTOINDEX/IP` index for each vector field. Load each collection once behind its own synchronized initialization flag.

- [ ] **Step 4: Change one logical upsert to write both collections**

Implement `VectorStore.upsert` so `MilvusVectorStore` first upserts the existing joint row, then a transition row containing both partition vectors. Any exception must escape as `VectorStoreException`; `DrawVectorSyncService` calls `markVectorReady` only after the whole method returns.

Make `VectorStore` and `VectorSearchHit` public so the isolated `prediction.model` package can consume the search boundary. Update legacy `SimilarDrawSearchService` to call `encodeJoint` and a renamed `searchJoint`, keeping it compiling until Task 7 removes the old prediction path.

- [ ] **Step 5: Run tests**

Run: `cd backend; mvn -Dtest=FeatureEncoderTest,DrawVectorSyncServiceTest test`

Expected: PASS, including the partial-write failure state.

- [ ] **Step 6: Commit partition vector support**

```powershell
git add backend/src/main/resources/application.yml backend/src/main/java/com/dlt/predictor/prediction backend/src/test/java/com/dlt/predictor/prediction
git commit -m "feat: index dlt front and back vectors"
```

### Task 3: 持久化并原子激活模型批次

**Files:**
- Create: `backend/src/main/resources/db/migration/V3__create_prediction_model.sql`
- Modify: `backend/src/main/resources/db/complete_schema_and_seed.sql`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/PredictionModelType.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/PredictionModelStatus.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/ModelMetrics.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/ModelSnapshot.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/PredictionModelEntity.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/PredictionModelRepository.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/PredictionModelStore.java`
- Test: `backend/src/test/java/com/dlt/predictor/prediction/model/PredictionModelStoreTest.java`

**Interfaces:**
- Produces: `PredictionModelStore.activeBatch(): Optional<List<ModelSnapshot>>`
- Produces: `PredictionModelStore.activateBatch(List<ModelSnapshot>): List<ModelSnapshot>`
- Produces: `PredictionModelStore.saveInactive(ModelSnapshot): ModelSnapshot`
- Produces: model types `LOGISTIC_REGRESSION`, `VECTOR_TRANSITION`, `DYNAMIC_ENSEMBLE`

- [ ] **Step 1: Write failing store tests for batch consistency**

Use a mocked repository to assert `activeBatch` is empty unless exactly one active row exists for every model type and all rows share the same `batchVersion`, `trainedThroughDrawId`, and `trainedThroughIssueNo`. Assert `activateBatch` rejects missing or duplicate types before changing repository state.

```java
assertThat(store.activeBatch()).isEmpty();
assertThatThrownBy(() -> store.activateBatch(List.of(logistic, vector)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("三种模型");
```

- [ ] **Step 2: Run test and verify failure**

Run: `cd backend; mvn -Dtest=PredictionModelStoreTest test`

Expected: compilation fails because model persistence classes do not exist.

- [ ] **Step 3: Add the Flyway schema**

Create `prediction_model` with columns: `id`, `model_type`, `model_version`, `algorithm_version`, `batch_version`, `status`, `config_json`, `feature_window`, `draw_count`, `sample_count`, `trained_through_draw_id`, `trained_through_issue_no`, `trained_through_date`, `trained_at`, `front_log_loss`, `back_log_loss`, `front_mean_hits`, `back_mean_hits`, `front_baseline_log_loss`, `back_baseline_log_loss`, `front_baseline_hits`, `back_baseline_hits`, `front_beats_baseline`, and `back_beats_baseline`. Add a unique `(model_type, model_version)` constraint and a partial unique index on `model_type WHERE status='ACTIVE'`. Mirror the table in `complete_schema_and_seed.sql`.

- [ ] **Step 4: Implement immutable model snapshots and JSON artifacts**

Use `ModelSnapshot` as the service boundary:

```java
public record ModelSnapshot(
        PredictionModelType type,
        String modelVersion,
        String algorithmVersion,
        String batchVersion,
        PredictionModelStatus status,
        String configJson,
        int featureWindow,
        int drawCount,
        int sampleCount,
        long trainedThroughDrawId,
        String trainedThroughIssueNo,
        LocalDate trainedThroughDate,
        Instant trainedAt,
        ModelMetrics metrics) {
}
```

`ModelMetrics` contains the four measured metrics, four baselines, and front/back baseline booleans. Keep JSON serialization in trainers through Jackson; the store treats `configJson` as opaque versioned data.

- [ ] **Step 5: Implement transactional batch activation**

Annotate `activateBatch` with `@Transactional`. Validate the three snapshots first, save candidates as inactive, update all current active rows to inactive, then update the three new rows to active. Do not catch persistence exceptions; rollback must leave the old batch active.

- [ ] **Step 6: Run tests and validate migration syntax through the application test context**

Run: `cd backend; mvn -Dtest=PredictionModelStoreTest,CoreDomainTest test`

Expected: PASS; incomplete/mixed batches are rejected before activation.

- [ ] **Step 7: Commit persistence**

```powershell
git add backend/src/main/resources/db backend/src/main/java/com/dlt/predictor/prediction/model backend/src/test/java/com/dlt/predictor/prediction/model
git commit -m "feat: persist prediction model batches"
```

### Task 4: 构建无泄漏特征、逻辑回归和滚动验证

**Files:**
- Modify: `backend/src/main/java/com/dlt/predictor/lottery/DrawRepository.java`
- Modify: `backend/src/main/java/com/dlt/predictor/lottery/DrawStore.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/NumberZone.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/BinarySample.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/CandidateScores.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/TrainingDatasetBuilder.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/LogisticRegressionTrainer.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/PredictionMetricsCalculator.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/RollingFold.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/RollingFoldBuilder.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/NextDrawModelTrainer.java`
- Test: `backend/src/test/java/com/dlt/predictor/prediction/model/TrainingDatasetBuilderTest.java`
- Test: `backend/src/test/java/com/dlt/predictor/prediction/model/LogisticRegressionTrainerTest.java`
- Test: `backend/src/test/java/com/dlt/predictor/prediction/model/RollingFoldBuilderTest.java`
- Test: `backend/src/test/java/com/dlt/predictor/prediction/model/PredictionMetricsCalculatorTest.java`

**Interfaces:**
- Produces: `DrawStore.allChronological(): List<Draw>`
- Produces: `TrainingDatasetBuilder.samples(List<Draw>, NumberZone, int targetFrom, int targetUntil): List<BinarySample>`
- Produces: `TrainingDatasetBuilder.latestFeatures(List<Draw>, NumberZone): Map<Integer, double[]>`
- Produces: `LogisticRegressionTrainer.train(List<BinarySample>): double[]`
- Produces: `LogisticRegressionTrainer.predict(double[], double[]): double`
- Produces: `NextDrawModelTrainer.train(List<Draw>, List<RollingFold>): TrainedLogisticModel`

- [ ] **Step 1: Write failing feature tests that prove target isolation**

Build 21 synthetic draws, generate candidate features for target index 20, mutate only draw 20, and assert every feature remains identical while the label changes. Assert the six features are: previous hit, 5/10/20 frequency, omission capped at 20 and divided by 20, and recent-5 frequency minus previous-15 frequency.

- [ ] **Step 2: Write failing deterministic regression and metric tests**

Use a separable sample set and assert two training runs produce identical seven-element weight arrays (bias plus six features), all predicted probabilities lie strictly between 0 and 1 after epsilon clamping, and weighted binary log loss decreases from zero weights. Test top-5/top-2 tie ordering by number.

- [ ] **Step 3: Write failing fold-boundary tests**

For exactly 100 draws, assert there are 80 predictable target periods after the 20-draw feature window, the first 48 targets form the initial window, and the remaining 32 are distributed across five folds as `7,7,6,6,6`. Assert every fold's training end is before its validation start and all validation targets occur exactly once.

- [ ] **Step 4: Run the focused tests and verify failure**

Run: `cd backend; mvn -Dtest=TrainingDatasetBuilderTest,LogisticRegressionTrainerTest,RollingFoldBuilderTest,PredictionMetricsCalculatorTest test`

Expected: compilation fails because the training classes do not exist.

- [ ] **Step 5: Implement chronological data access and feature construction**

Add `findAllByOrderByDrawDateAscIdAsc` to `DrawRepository` and expose it as `DrawStore.allChronological`. Implement candidates as 1–35 or 1–12 without using the number value as a feature. Feature methods take an explicit target index and only slice `[targetIndex-20, targetIndex)`.

- [ ] **Step 6: Implement deterministic weighted L2 logistic regression**

Use zero initialization, 800 full-batch gradient steps, learning rate `0.05`, L2 lambda `0.001`, and positive class weights `30.0/5.0` for front and `10.0/2.0` for back. Do not regularize the bias. Clamp sigmoid inputs to `[-35,35]` and probabilities to `[1e-12, 1-1e-12]` for log loss.

- [ ] **Step 7: Implement rolling folds, scoring and production artifact**

`NextDrawModelTrainer` retrains at the start of each fold, scores every candidate in the fold, accumulates `ModelMetrics`, then trains once on all predictable targets. Serialize this exact versioned artifact:

```java
public record LogisticModelArtifact(
        int artifactVersion,
        int featureWindow,
        double[] frontWeights,
        double[] backWeights) {
}
```

Set `artifactVersion=1`, `featureWindow=20`, algorithm version `logistic-l2-v1`.

- [ ] **Step 8: Run focused tests**

Run: `cd backend; mvn -Dtest=TrainingDatasetBuilderTest,LogisticRegressionTrainerTest,RollingFoldBuilderTest,PredictionMetricsCalculatorTest test`

Expected: PASS with deterministic weights and leakage checks.

- [ ] **Step 9: Commit the trained model core**

```powershell
git add backend/src/main/java/com/dlt/predictor/lottery backend/src/main/java/com/dlt/predictor/prediction/model backend/src/test/java/com/dlt/predictor/prediction/model
git commit -m "feat: train next draw logistic model"
```

### Task 5: 实现分区向量转移回测和动态融合

**Files:**
- Modify: `backend/src/main/java/com/dlt/predictor/lottery/DrawRepository.java`
- Modify: `backend/src/main/java/com/dlt/predictor/lottery/DrawStore.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/VectorTransitionPredictor.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/VectorTransitionModelTrainer.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/DynamicEnsembleTrainer.java`
- Test: `backend/src/test/java/com/dlt/predictor/prediction/model/VectorTransitionPredictorTest.java`
- Test: `backend/src/test/java/com/dlt/predictor/prediction/model/DynamicEnsembleTrainerTest.java`

**Interfaces:**
- Consumes: partition encoders and `VectorStore.searchFront/searchBack`
- Consumes: fold scores from `NextDrawModelTrainer`
- Produces: `VectorTransitionPredictor.predict(List<Draw>, int targetIndex, NumberZone, int limit): CandidateScores` for backtests
- Produces: `VectorTransitionPredictor.predictLatest(List<Draw>, NumberZone, int limit): CandidateScores` for Milvus-backed production inference
- Produces: `DynamicEnsembleTrainer.train(RollingPredictionSet, RollingPredictionSet): TrainedEnsembleModel`

- [ ] **Step 1: Write failing transition tests**

Create a short draw sequence where the nearest source draw and its successor contain different numbers. Assert only successor numbers receive weight. Add a future-leakage case where the nearest source successor is the target itself and assert it is excluded. Add a zero-total-weight case that throws `ModelUnavailableException`.

- [ ] **Step 2: Write failing ensemble grid-search tests**

Provide fixed logical and vector probabilities. Assert front and back weights are selected independently from increments of 0.05, sum to 1, a component losing to both random baselines receives weight 0 when the other beats a baseline, and log-loss/hits/logic-weight tie breaks are deterministic.

- [ ] **Step 3: Run tests and verify failure**

Run: `cd backend; mvn -Dtest=VectorTransitionPredictorTest,DynamicEnsembleTrainerTest test`

Expected: compilation fails because transition and ensemble classes do not exist.

- [ ] **Step 4: Implement historical and production transition lookup**

For backtests, calculate normalized vector inner products in memory only across source indices whose successor index is strictly less than the target index. Sort by similarity descending and source draw ID ascending, take 30, then normalize successor-number weights by total similarity.

For production, ask Milvus for at most the current PostgreSQL history count, then filter to the first 30 valid transitions. Resolve each hit through PostgreSQL and query its chronological successor using `(drawDate > date) OR (drawDate = date AND id > id)`, ordered by date then ID. Exclude the latest input draw and sources without successors. A zero total similarity throws `ModelUnavailableException`.

- [ ] **Step 5: Implement transition metrics and artifact**

Run the same five validation folds without future access. Persist algorithm version `vector-transition-v1` and:

```java
public record VectorTransitionArtifact(
        int artifactVersion,
        int neighborCount,
        String vectorVersion,
        long indexedThroughDrawId) {
}
```

- [ ] **Step 6: Implement deterministic dynamic fusion**

For each zone, enumerate logistic weight 0.00–1.00 inclusive in 0.05 increments and combine candidate scores before top-number selection. Apply the baseline exclusion and activation eligibility rules from the spec. Serialize:

```java
public record EnsembleArtifact(
        int artifactVersion,
        double frontLogisticWeight,
        double backLogisticWeight,
        String logisticModelVersion,
        String vectorModelVersion) {
}
```

Use algorithm version `dynamic-ensemble-v1`.

- [ ] **Step 7: Run focused tests**

Run: `cd backend; mvn -Dtest=VectorTransitionPredictorTest,DynamicEnsembleTrainerTest test`

Expected: PASS, including future-transition exclusion and zero-weight behavior.

- [ ] **Step 8: Commit transition and ensemble algorithms**

```powershell
git add backend/src/main/java/com/dlt/predictor/lottery backend/src/main/java/com/dlt/predictor/prediction/model backend/src/test/java/com/dlt/predictor/prediction/model
git commit -m "feat: add vector transition ensemble model"
```

### Task 6: 编排训练、自动触发和管理员手动重训

**Files:**
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/TrainingRunStatus.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/ModelTrainingResult.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/TrainingRunResult.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/ModelTrainingCoordinator.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/ModelTrainingConfiguration.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/ModelTrainingStartupRunner.java`
- Modify: `backend/src/main/java/com/dlt/predictor/prediction/VectorSyncStartupRunner.java`
- Modify: `backend/src/main/java/com/dlt/predictor/lottery/DrawUpsertResult.java`
- Modify: `backend/src/main/java/com/dlt/predictor/lottery/DrawStore.java`
- Modify: `backend/src/main/java/com/dlt/predictor/lottery/DltCrawlerService.java`
- Modify: `backend/src/main/java/com/dlt/predictor/admin/AdminController.java`
- Create: `backend/src/main/java/com/dlt/predictor/admin/ModelTrainingResponse.java`
- Test: `backend/src/test/java/com/dlt/predictor/prediction/model/ModelTrainingCoordinatorTest.java`
- Test: `backend/src/test/java/com/dlt/predictor/lottery/DltCrawlerServiceTest.java`

**Interfaces:**
- Produces: `ModelTrainingCoordinator.retrainNow(): TrainingRunResult`
- Produces: `ModelTrainingCoordinator.requestRetrain(): TrainingRunStatus`
- Produces: `ModelTrainingCoordinator.requestIfStale(): TrainingRunStatus`
- Produces: `POST /api/v1/admin/dlt/model/retrain`

- [ ] **Step 1: Write failing coordinator lifecycle tests**

Assert fewer than 100 draws returns `INSUFFICIENT_DATA`; an occupied `ReentrantLock.tryLock()` returns `TRAINING_IN_PROGRESS`; any trainer failure leaves `activateBatch` untouched; three successful candidates activate once with the same UUID batch and latest draw waterline; a correction with `vectorRefreshRequired=true` calls async request exactly once.

- [ ] **Step 2: Run tests and verify failure**

Run: `cd backend; mvn -Dtest=ModelTrainingCoordinatorTest,DltCrawlerServiceTest test`

Expected: compilation fails because the coordinator and result records do not exist.

- [ ] **Step 3: Implement one-JVM training coordination**

Use a fair `ReentrantLock` and `tryLock`, not a blocking lock. `retrainNow` loads a single chronological snapshot, checks 100 draws, builds folds, trains logistic, backtests vector transition, chooses ensemble weights, creates one `batchVersion`, then calls `activateBatch` once. If the ensemble is ineligible and an active batch already exists, save the three candidates as inactive and retain the old batch; allow the first complete batch to activate while marking its baseline flags false. Log exceptions without deactivating the old batch.

`ModelTrainingConfiguration` exposes a dedicated single-thread `ThreadPoolTaskExecutor` named `modelTrainingExecutor`, with queue capacity 1 and graceful shutdown enabled. `requestRetrain` submits `retrainNow` to this executor so scheduled and import requests do not remain open during training. `requestIfStale` compares the active batch waterline with the latest PostgreSQL draw before submitting.

- [ ] **Step 4: Wire all mutation and startup triggers**

Make `DrawUpsertResult` public and let manual import inspect `vectorRefreshRequired`. After vector sync:

```java
if (changedCount > 0) {
    modelTrainingCoordinator.requestRetrain();
}
```

Apply the same rule to manual import. `ModelTrainingStartupRunner` runs after `VectorSyncStartupRunner` using `@Order`, calls `requestIfStale`, and never aborts application startup on training failure.

- [ ] **Step 5: Add the manual retrain endpoint and response**

`POST /api/v1/admin/dlt/model/retrain` calls `retrainNow` and returns batch status, batch version, message, and three per-model summaries containing model version, draw count, sample count, waterline, and metrics. Preserve `TRAINING_IN_PROGRESS` and `INSUFFICIENT_DATA` as successful HTTP responses with explicit domain status; only unexpected failures use HTTP 500.

- [ ] **Step 6: Run focused tests**

Run: `cd backend; mvn -Dtest=ModelTrainingCoordinatorTest,DltCrawlerServiceTest test`

Expected: PASS; failure never partially activates a batch.

- [ ] **Step 7: Commit training orchestration**

```powershell
git add backend/src/main/java/com/dlt/predictor backend/src/test/java/com/dlt/predictor
git commit -m "feat: automate dlt model retraining"
```

### Task 7: 用三模型推理替换旧预测接口

**Files:**
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/ModelUnavailableException.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/model/NextDrawPredictionService.java`
- Modify: `backend/src/main/java/com/dlt/predictor/prediction/PredictionController.java`
- Modify: `backend/src/main/java/com/dlt/predictor/prediction/dto/PredictionRequest.java`
- Modify: `backend/src/main/java/com/dlt/predictor/prediction/dto/PredictionResponse.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/dto/ModelPredictionResponse.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/dto/ModelMetricsResponse.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/dto/EnsembleWeightsResponse.java`
- Modify: `backend/src/main/java/com/dlt/predictor/api/ApiExceptionHandler.java`
- Delete: `backend/src/main/java/com/dlt/predictor/prediction/RecommendationEngine.java`
- Delete: `backend/src/main/java/com/dlt/predictor/prediction/RecommendationResult.java`
- Delete: `backend/src/main/java/com/dlt/predictor/prediction/SimilarDraw.java`
- Delete: `backend/src/main/java/com/dlt/predictor/prediction/SimilarDrawSearchService.java`
- Test: `backend/src/test/java/com/dlt/predictor/prediction/model/NextDrawPredictionServiceTest.java`
- Modify: `backend/src/test/java/com/dlt/predictor/prediction/dto/PredictionResponseTest.java`

**Interfaces:**
- Consumes: `PredictionModelStore.activeBatch`, trained artifacts, latest 20+ draws, Milvus partition search
- Produces: `NextDrawPredictionService.predict(): PredictionResponse`
- Produces: body-optional `POST /api/v1/dlt/predictions`

- [ ] **Step 1: Write failing three-result response tests**

Assert the response order is exactly logistic, vector transition, dynamic ensemble; each has 5/2 sorted numbers and complete 35/12 score maps; ensemble references the exact two active component versions and exposes front/back weights. Add cases for missing model, mixed batch, stale/corrupt artifact, no transition neighbors, and Milvus exception, all mapped to HTTP 503.

- [ ] **Step 2: Run tests and verify failure**

Run: `cd backend; mvn -Dtest=NextDrawPredictionServiceTest,PredictionResponseTest test`

Expected: compilation fails on the new service and DTO shape.

- [ ] **Step 3: Implement the prediction service**

Load and validate one active batch. Decode artifacts by `artifactVersion`; reject unsupported versions. Build latest logistic features from PostgreSQL, run front/back Milvus transition searches, and combine the two score maps using stored ensemble weights. Generate all three `DltNumbers` with the same deterministic top-number helper.

Return:

```java
public record PredictionResponse(
        Instant generatedAt,
        String basedOnIssueNo,
        String disclaimer,
        List<ModelPredictionResponse> predictions) {
}
```

`ModelPredictionResponse` contains type, Chinese name, numbers, full score maps, model version, trained time, draw count, trained-through issue, metrics, nullable ensemble weights, and front/back baseline flags.

- [ ] **Step 4: Simplify the controller contract and errors**

Make `PredictionRequest` an empty record and allow an absent body with `@RequestBody(required=false)`. The controller ignores request content and calls `NextDrawPredictionService.predict()`. Map `ModelUnavailableException` and `VectorStoreException` to HTTP 503 with Chinese messages; keep input validation as 400.

- [ ] **Step 5: Remove the old recommendation path**

Delete the recency-weighted engine and similar-draw DTO/service after all references are gone. Keep the existing joint collection writer and `searchJoint` method because the index remains supported, but the prediction endpoint must not call it.

- [ ] **Step 6: Run focused and complete backend tests**

Run: `cd backend; mvn -Dtest=NextDrawPredictionServiceTest,PredictionResponseTest test`

Expected: PASS.

Run: `cd backend; mvn test`

Expected: all existing authentication, lottery, vector, model, and API tests PASS.

- [ ] **Step 7: Commit the API replacement**

```powershell
git add backend/src/main/java/com/dlt/predictor backend/src/test/java/com/dlt/predictor
git commit -m "feat: serve three next draw predictions"
```

### Task 8: 展示三组预测和管理端训练控制

**Files:**
- Modify: `forend/src/types/prediction.ts`
- Modify: `forend/src/api/prediction.ts`
- Modify: `forend/src/views/Predict.vue`
- Modify: `forend/src/types/admin.ts`
- Modify: `forend/src/api/admin.ts`
- Modify: `forend/src/views/AdminSync.vue`
- Modify: `forend/src/style.css`

**Interfaces:**
- Consumes: `PredictionResponse.predictions` in fixed three-model order
- Consumes: `POST /api/v1/admin/dlt/sync?pageCount=n`
- Consumes: `POST /api/v1/admin/dlt/model/retrain`

- [ ] **Step 1: Replace frontend prediction types**

Remove `PredictionInputType`, custom-number fields, and `SimilarDraw`. Define `PredictionModelType`, `ModelMetrics`, `EnsembleWeights`, `ModelPrediction`, and the top-level response matching Task 7 exactly. Change `createPrediction` to `client.post(..., {})` without caller arguments.

- [ ] **Step 2: Replace the prediction form and result layout**

Remove radio buttons, number inputs, parsing logic, and similar-history table. Keep one “生成下一期参考号码” button. Render `result.predictions` as responsive cards; label `DYNAMIC_ENSEMBLE` as “主推荐”, render `NumberBalls`, model version/waterline/draw count, four validation metrics, and ensemble weights when non-null. Show the shared disclaimer once below the cards.

- [ ] **Step 3: Add manual page count and model retraining APIs**

Change `runDltSync(pageCount = 30)` to send the query parameter. Add:

```ts
export async function retrainModels(): Promise<TrainingRunResult> {
  const response = await client.post<ApiEnvelope<TrainingRunResult>>(
    '/api/v1/admin/dlt/model/retrain',
  )
  return unwrap(response)
}
```

Define the training result fields exactly as Task 6 returns.

- [ ] **Step 4: Update the data-sync page**

Add an Element Plus numeric input constrained to 1–100 and defaulted to 30 beside “手动同步开奖”. Add a separate “手动重训模型” action, independent loading/error/result state, and a three-row training summary table. Do not report collection success as training success.

- [ ] **Step 5: Add responsive styling and run static verification**

Use CSS grid `repeat(auto-fit, minmax(280px, 1fr))` for prediction cards and prevent score/metadata text from overflowing narrow screens.

Run: `cd forend; npm run type-check`

Expected: PASS with no TypeScript errors.

Run: `cd forend; npm run build`

Expected: PASS and Vite emits `dist` assets.

- [ ] **Step 6: Commit frontend integration**

```powershell
git add forend/src
git commit -m "feat: display three prediction models"
```

### Task 9: 完整构建与运行验收

**Files:**
- Modify only if verification exposes an implementation defect in files already listed above.

**Interfaces:**
- Verifies: PostgreSQL migrations, Spring wiring, Milvus configuration, public API contracts, and frontend compilation

- [ ] **Step 1: Run the complete backend verification**

Run: `cd backend; mvn clean test`

Expected: BUILD SUCCESS with every test passing.

Run: `cd backend; mvn package`

Expected: BUILD SUCCESS and `target/dlt-predictor-api-1.0.0.jar` exists.

- [ ] **Step 2: Run the complete frontend verification**

Run: `cd forend; npm run type-check; npm run build`

Expected: both commands exit 0.

- [ ] **Step 3: Inspect the final diff for accidental scope**

Run: `git status --short; git diff --check; git diff --stat`

Expected: only the planned backend, migration, test, frontend, spec, and plan files are changed; `git diff --check` prints no errors.

- [ ] **Step 4: Perform local smoke checks with PostgreSQL and Milvus available**

Start the backend and frontend using the existing project commands. Verify:

1. `POST /api/v1/admin/dlt/sync` fetches the configured 30 pages and returns fetched/changed counts.
2. Changed data starts one training run; repeated unchanged sync does not retrain.
3. `POST /api/v1/admin/dlt/model/retrain` returns three model entries sharing one batch and waterline.
4. `POST /api/v1/dlt/predictions` returns exactly three ordered predictions; ensemble weights sum to 1 in each zone.
5. Stopping Milvus makes prediction return HTTP 503 while PostgreSQL history endpoints remain available.
6. The prediction page shows three cards and the admin page keeps collection and training statuses separate.

- [ ] **Step 5: Commit any verification-only fixes, then report evidence**

If Step 1–4 required fixes, stage only those fixes and commit:

```powershell
git commit -m "fix: complete trained prediction integration"
```

Report the exact Maven/npm command results and any smoke check that could not run because an external service was unavailable.
