# 大乐透项目代码可维护性重构设计

## 目标

在不改变现有 REST 路径、请求/响应形状、前端路由、页面功能或业务算法的前提下，整理 `backend` 与 `forend` 的代码结构和格式，使代码可读、可测试且便于后续扩展。

## 后端设计

- 保留现有模块边界：`api`、`auth`、`lottery`、`prediction`、`admin`、`config`。
- 每个 HTTP 接口的请求/响应 DTO 使用显式 record 定义，消除控制器中的裸 `Map` 和未命名结构。
- 控制器只负责参数绑定与响应；`AuthService`、`DrawStore`、`RecommendationEngine` 保留业务规则，提取可复用的校验和映射方法。
- 将 `UserAccount`、认证请求、审批请求、导入请求、预测请求等类型独立到合适的包内。
- 使用一致的 Java 格式、访问修饰符、构造器注入、命名和异常消息；不改变开发环境的内存数据实现，也不引入新持久层行为。

## 前端设计

- 新增 `types` 用于 API 和领域类型，`api` 按身份、开奖、预测、管理分模块；页面不得直接构造 URL 或使用 `any`。
- 新增可复用的 `NumberBalls`、`RiskDisclaimer` 组件，统一号码展示与合规提示。
- 将页面异步加载、错误反馈与加载状态以一致模式处理；保持页面路径、当前交互和 API 调用不变。
- 用 CSS 变量统一颜色、间距与文字颜色，格式化所有 Vue、TypeScript、JSON 和 YAML 文件。

## 兼容性约束

- 后端路径保持 `/api/v1/**`，统一响应维持 `{ code, message, data, traceId }`。
- 前端保持 `/`、`/draws`、`/predict`、`/login`、`/admin` 路由。
- 号码限制、47 维向量编码、加权推荐公式和娱乐免责声明不得修改。
- 默认 PostgreSQL 连接、Flyway 迁移及 Compose 服务结构不得修改。

## 验证

- 后端：`mvn test`，并额外添加/保留 HTTP 契约与 DTO 映射的测试。
- 前端：`npm run type-check`、`npm run build`；若环境无法下载 npm 包，保留完整的错误证据并继续完成可静态审查的代码整理。
- 部署：`docker compose --env-file .env.example config --quiet`。

## 自检

- 无 TBD、TODO 或未定义的重构边界。
- 所有公开接口和前端路由均明确为兼容性约束。
- 仅覆盖代码组织、格式和类型安全，不扩展为完整认证或持久化功能重写。
