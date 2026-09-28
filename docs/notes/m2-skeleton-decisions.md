# M2 Web 骨架决策与已知限制

日期：2026-09-28　范围：web 模块从空占位 pom → 可运行、可 Docker 化骨架（不含页面/邮件捕获/Flyway/DataSource，api 模块未动）。

## 关键决策

1. **规则资产打包**：`samples/schema` 是单一事实源（golden 测试依赖 `../samples` 文件路径，不搬）。
   web pom 用 `maven-resources-plugin` 在 `process-resources` 阶段把 `samples/schema` 复制进
   `target/classes/schema`，随 JAR 打包。
2. **运行时解压**：`XmlRuleValidator` 构造需要真实文件 `Path`，fat JAR 内 classpath 资源不是文件。
   `ValidatorConfig` 启动时把 10 个规则文件（4 XSD + 6 SCH）从 classpath 解压到临时目录
   （保留目录结构），再以解压后的 Path 构造 `FacturxValidator`。本地 `spring-boot:run` 与
   Docker fat JAR 走同一条路径。解压清单硬编码于 `ValidatorConfig.RULE_FILES`，规则资产增删需同步。
3. **Dockerfile**：单阶段、拷贝预构建 JAR（多阶段首建在大陆网络太慢，CI 时再切）。
   基础镜像 `eclipse-temurin:17-jre-alpine`（自带 wget 做 healthcheck）。
4. **/health**：自定义 `@RestController` 返回 `{"status":"UP"}`；暂不引 actuator（接 DataSource 后再上）。
5. **compose**：`db` = postgres:16-alpine + pg_isready healthcheck + named volume
   `pgdata` + 端口 `127.0.0.1:5433:5432`（避开本机 5432）；`app` `depends_on` 等 db healthy。
   app 端口 `"${SERVER_PORT:-8080}:8080"` 可覆盖。
6. **并发安全结论（M1 遗留项已闭环）**：`ValidatorConcurrencyTest` 8 线程 × 25 次
   XML 路径校验 + 预热，全绿——`SchematronResourcePure` 在本负载下线程安全，
   无需 synchronized 兜底（spike-decision.md 已知限制 4 关闭）。

## 已知限制 / 风险

1. compose 不重建 JAR：改代码后须先 `mvn -pl web -am package -DskipTests` 再 `docker compose up -d --build`（CI 切多阶段后消除）。
2. 基础镜像 `17-jre-alpine` 走 daocloud 首拉可能慢/失败，备选 `17-jre-jammy`（healthcheck 需换命令）。
3. 解压清单硬编码 10 文件，规则资产增删需同步 `ValidatorConfig.RULE_FILES`。
4. 本机 8080 占用时 `SERVER_PORT=8081 docker compose up` 绕过。
5. 临时目录每次启动新建、不复用不清理（进程退出由 OS 回收；解压开销约几十 ms，可接受）。

## 验证记录（2026-09-28）

- `mvn -pl web -am test`：18/18 绿（validator-core 16 + web 2，并发测试含在内）。
- `docker compose up -d --build` 后 app/db 均 healthy，`curl /health` → 200 `{"status":"UP"}`。
- 收尾 `docker compose down`（保留 pgdata volume）。
