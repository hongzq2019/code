# 大乐透历史分析与参考号码推荐系统

目录：`backend/` 是 Java 21 / Spring Boot API，`forend/` 是 Vue 3 前端。推荐结果仅用于历史数据分析与娱乐，不承诺中奖概率或预测准确性。

## 本地开发

1. 启动 PostgreSQL（默认数据库/用户/密码为 `dlt`），后端：`cd backend; mvn spring-boot:run`。默认连接 `jdbc:postgresql://localhost:5432/dlt`，可用 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` 覆盖。
2. 前端：`cd forend; npm install; npm run dev`，打开 `http://localhost:5173`。
3. 管理员初始账户：`admin/admin`；生产环境必须通过 `BOOTSTRAP_ADMIN_PASSWORD` 覆盖。

## Compose

复制 `.env.example` 为 `.env`，填入强密码与 JWT 密钥，然后运行 `docker compose up --build`。前端默认在 `http://localhost:8088` 提供服务。
