# Milvus 开奖向量索引设计

## 目标

以 PostgreSQL 中的 `dlt_draw` 作为开奖数据唯一事实来源，以 Milvus 作为 47 维号码特征的近邻检索索引。预测接口必须通过 Milvus 查询相似开奖数据，不再遍历应用内存中的全部记录。

本次同时实现索引同步状态、失败原因、自动补偿和管理员手动重试。

## 数据职责

PostgreSQL 保存完整开奖业务数据、状态及索引同步状态。Milvus collection 仅保存：

- `draw_id`：PostgreSQL `dlt_draw.id`，作为 Milvus 主键；
- `vector`：由 `FeatureEncoder` 生成的 47 维浮点向量。

Milvus 数据可以由 PostgreSQL 重建，不能作为业务数据来源。

`dlt_draw.vector_status` 使用 `PENDING`、`READY`、`FAILED`：

- 导入或更新开奖数据后为 `PENDING`；
- Milvus 幂等写入成功后为 `READY`；
- 写入或索引操作失败后为 `FAILED`，并保存错误摘要与最近一次尝试时间。

## 组件与数据流

1. JPA repository 管理 PostgreSQL 开奖数据；导入接口写入主库并把记录置为 `PENDING`。
2. `MilvusVectorStore` 封装 collection 创建、加载、upsert 与 search，隔离 Milvus Java SDK。
3. `DrawVectorSyncService` 编码待同步开奖数据，写入 Milvus，并据结果更新同步状态。
4. 应用启动后同步所有 `PENDING` 与 `FAILED` 记录；管理员重试接口触发同一同步服务。
5. 预测接口将输入号码编码并调用 Milvus search，使用命中的 `draw_id` 批量读取 PostgreSQL，按 Milvus 返回的相似度顺序构造响应。

## 检索与异常行为

Milvus 使用内积（IP）距离；现有 0/1 特征向量的内积等于重合号码数量，因此可直接作为相似度。检索数量会额外请求一条，以便排除与输入开奖相同的记录后仍尽量满足 `topK`。

Milvus 不可用、collection 初始化失败或 search 失败时，预测接口返回服务错误，不回退至内存遍历。同步失败不会删除 PostgreSQL 开奖数据；记录保持 `FAILED`，等待下一次启动补偿或管理员重试。

## 验证

单元测试覆盖特征写入、状态转换、失败记录的再同步，以及 Milvus 命中结果按 PostgreSQL 数据映射。集成测试验证导入后进入待同步状态、同步成功变为就绪状态、预测路径使用向量存储而非遍历全部开奖数据。
