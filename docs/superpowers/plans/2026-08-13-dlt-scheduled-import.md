# 大乐透定时采集 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 定时从中国体彩网官方 API 获取大乐透开奖数据，并以期号幂等方式写入 PostgreSQL `dlt_draw` / `DrawEntity`。

**Architecture:** `SportteryDltClient` 通过官方 `gameNo=85` API 获取最近开奖记录并解析为领域输入；`DltCrawlerService` 负责持久化、触发向量同步和记录 `crawler_job_log`；`DltScheduledImporter` 根据配置 cron 调度该服务。

**Tech Stack:** Java 21、Spring Boot 3.4、Spring Web `RestClient`、Jackson、Spring Scheduling、Spring Data JPA。

## Global Constraints

- 中国体彩网为默认且唯一采集源；API URL、页面大小、cron 和时区必须可配置。
- `dlt_draw` 是唯一事实来源，按 `issue_no` 幂等写入；号码或日期变化时才重置 Milvus 向量状态。
- 每次运行都必须写入 `crawler_job_log`，网络或解析失败记录 `FAILED` 并不删除历史数据。
- 不新增测试文件；完成时运行现有 Maven 测试和打包验证。

---

### Task 1: Implement official source client and persisted crawler log

**Files:**
- Create: `backend/src/main/java/com/dlt/predictor/lottery/SportteryDltClient.java`
- Create: `backend/src/main/java/com/dlt/predictor/lottery/CrawlerJobLogEntity.java`
- Create: `backend/src/main/java/com/dlt/predictor/lottery/CrawlerJobLogRepository.java`
- Modify: `backend/src/main/resources/application.yml`

- [ ] Fetch `https://webapi.sporttery.cn/gateway/lottery/getHistoryPageListV1.qry` with `gameNo=85`, parse official issue/date/7-number records, and reject malformed responses.
- [ ] Map crawler job fields to the existing table and expose configuration properties for source URL, page size, timeout, cron, and Asia/Shanghai scheduling.

### Task 2: Add idempotent persistence, scheduler, and administrative execution

**Files:**
- Modify: `backend/src/main/java/com/dlt/predictor/lottery/DrawEntity.java`
- Modify: `backend/src/main/java/com/dlt/predictor/lottery/DrawStore.java`
- Create: `backend/src/main/java/com/dlt/predictor/lottery/DltCrawlerService.java`
- Create: `backend/src/main/java/com/dlt/predictor/lottery/DltScheduledImporter.java`
- Modify: `backend/src/main/java/com/dlt/predictor/DltPredictorApplication.java`
- Modify: `backend/src/main/java/com/dlt/predictor/admin/AdminController.java`

- [ ] Persist source URL/payload, only mark vectors pending when draw date or numbers change, and preserve `READY` for identical imports.
- [ ] Schedule imports; save success, partial, and failed run logs; synchronize changed draws with Milvus.
- [ ] Replace the placeholder `/api/v1/admin/dlt/sync` endpoint with the same crawler service for on-demand execution.

### Task 3: Verify

- [ ] Run `mvn clean test` and `mvn package` in `backend`.
