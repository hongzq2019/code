# Milvus 开奖向量索引 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 以 PostgreSQL 为开奖数据主库，使用 Milvus 持久化 47 维向量索引并驱动预测近邻检索。

**Architecture:** JPA 管理 `dlt_draw` 及同步状态；`MilvusVectorStore` 仅封装 SDK 操作；同步服务负责从主库生成向量、幂等 upsert 及状态转换。预测服务通过索引返回的主键批量读取 PostgreSQL，再按检索顺序构造结果。

**Tech Stack:** Java 21、Spring Boot 3.4、Spring Data JPA、Flyway、Milvus Java SDK v2、JUnit 5、Mockito。

## Global Constraints

- PostgreSQL `dlt_draw` 是唯一业务数据来源；Milvus 数据可由主库重建。
- 向量固定 47 维，Milvus 使用 IP 距离；Milvus 主键为 PostgreSQL `draw_id`。
- 预测查询失败必须报错，禁止回退到内存全表排序。
- 导入或更新数据设为 `PENDING`；成功为 `READY`；异常为 `FAILED` 并保存错误摘要与尝试时间。

---

## File Structure

- `backend/pom.xml`：增加 Milvus Java SDK。
- `backend/src/main/resources/application.yml`：Milvus 主机、端口、collection 与批处理配置。
- `backend/src/main/resources/db/migration/V2__add_vector_sync_metadata.sql`：同步失败原因与尝试时间字段。
- `backend/src/main/java/com/dlt/predictor/lottery/*`：JPA entity、repository、持久化开奖服务与同步状态。
- `backend/src/main/java/com/dlt/predictor/vector/*`：Milvus 配置、SDK 适配器、同步与启动补偿。
- `backend/src/main/java/com/dlt/predictor/prediction/*`：向量检索预测服务。
- `backend/src/test/java/com/dlt/predictor/vector/*`：同步与重试测试。
- `backend/src/test/java/com/dlt/predictor/prediction/*`：预测检索路径测试。

### Task 1: Persist vector-sync metadata and configure the Milvus client

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/db/migration/V2__add_vector_sync_metadata.sql`
- Create: `backend/src/main/java/com/dlt/predictor/vector/MilvusProperties.java`
- Create: `backend/src/main/java/com/dlt/predictor/vector/MilvusConfiguration.java`
- Test: `backend/src/test/java/com/dlt/predictor/vector/MilvusPropertiesTest.java`

- [ ] Add `io.milvus:milvus-sdk-java` compatible with the deployed Milvus 2.4 server.
- [ ] Add `milvus.host`, `milvus.port`, `milvus.collection-name`, and `milvus.sync-batch-size` configuration with Docker-compatible defaults.
- [ ] Add nullable `vector_error` and `vector_attempted_at` columns to `dlt_draw`.
- [ ] Define an immutable `MilvusProperties` configuration properties class and a `MilvusClientV2` bean using `http://{host}:{port}`.
- [ ] Test property binding and run `mvn test -Dtest=MilvusPropertiesTest`.

### Task 2: Replace in-memory draws with PostgreSQL-backed draw persistence

**Files:**
- Create: `backend/src/main/java/com/dlt/predictor/lottery/DrawEntity.java`
- Create: `backend/src/main/java/com/dlt/predictor/lottery/DrawRepository.java`
- Modify: `backend/src/main/java/com/dlt/predictor/lottery/DrawStore.java`
- Test: `backend/src/test/java/com/dlt/predictor/lottery/DrawStoreTest.java`

**Produces:** `DrawStore.upsert(...)`, `all()`, `find(...)`, `findByIdsIn(...)`, and `pendingOrFailedForVectorSync()` backed by PostgreSQL.

- [ ] Write tests asserting imported data is persisted with `PENDING` status and can be fetched by ID preserving requested ordering.
- [ ] Implement entity mapping for `dlt_draw`, parse and format existing comma-separated number fields, and repository queries for pending/failed rows.
- [ ] Replace seeded concurrent map behavior with repository operations while keeping controller-facing methods stable.
- [ ] Run `mvn test -Dtest=DrawStoreTest`.

### Task 3: Add the Milvus vector store and status-aware synchronization

**Files:**
- Create: `backend/src/main/java/com/dlt/predictor/vector/VectorSearchHit.java`
- Create: `backend/src/main/java/com/dlt/predictor/vector/MilvusVectorStore.java`
- Create: `backend/src/main/java/com/dlt/predictor/vector/DrawVectorSyncService.java`
- Create: `backend/src/main/java/com/dlt/predictor/vector/VectorSyncStartupRunner.java`
- Test: `backend/src/test/java/com/dlt/predictor/vector/DrawVectorSyncServiceTest.java`

**Produces:** `MilvusVectorStore.upsert(long, float[])`, `search(float[], int)`, and `DrawVectorSyncService.syncPendingAndFailed()` / `syncDraw(long)`.

- [ ] Write failing tests using a mocked vector store: successful upsert produces `READY`; exception produces `FAILED`, error text, and attempt timestamp; a failed row is retried successfully.
- [ ] Implement collection creation with explicit `draw_id` INT64 primary key and `vector` FLOAT_VECTOR field, 47 dimensions, IP metric, and load it before use.
- [ ] Implement idempotent upsert and search result conversion in the adapter.
- [ ] Implement synchronous batch processing that marks attempts before write and updates status once per draw; register startup compensating sync.
- [ ] Run `mvn test -Dtest=DrawVectorSyncServiceTest`.

### Task 4: Switch prediction to Milvus search and add retry endpoint

**Files:**
- Modify: `backend/src/main/java/com/dlt/predictor/prediction/PredictionController.java`
- Create: `backend/src/main/java/com/dlt/predictor/prediction/SimilarDrawSearchService.java`
- Modify: `backend/src/main/java/com/dlt/predictor/admin/AdminController.java`
- Test: `backend/src/test/java/com/dlt/predictor/prediction/SimilarDrawSearchServiceTest.java`

**Consumes:** `MilvusVectorStore.search(float[], int)` and `DrawStore.findByIdsIn(List<Long>)`.

- [ ] Write a failing test showing returned hits map to database draws in Milvus rank order and exclude an exactly matching input draw.
- [ ] Implement `SimilarDrawSearchService`; request `topK + 1`, batch-fetch PostgreSQL rows, filter exact-number matches, and preserve score order.
- [ ] Make `PredictionController` delegate similarity search to this service; do not retain the cosine/all-draw implementation.
- [ ] Add protected `POST /api/v1/admin/dlt/vectors/retry` that invokes the same pending/failed sync service and returns attempted/succeeded/failed counts.
- [ ] Run focused prediction/admin tests.

### Task 5: Verify application behavior

**Files:**
- Modify: `README.md`

- [ ] Document required Milvus endpoint variables and the administrator vector retry endpoint.
- [ ] Run `mvn test` in `backend`.
- [ ] Run `mvn package` in `backend`.
- [ ] If Docker is available, start the stack and invoke import, retry, and prediction endpoints to verify end-to-end behavior.
