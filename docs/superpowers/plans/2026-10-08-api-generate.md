# POST /api/generate 实施计划（已完成）

日期：2026-10-08 设计：docs/superpowers/specs/2026-10-08-api-generate-design.md

## 实施顺序（TDD，实际执行）

1. ✅ InvoiceAmountCalculator + 单测（7 用例：单行乘法、HALF_UP、多税率分组
   Σbasis==taxBasis（BR-CO-13）、0% 税率、同 rate 不同 category 分组、求和精度、
   组级税额舍入）
2. ✅ 四个 DTO + Jakarta 注解（record + compact constructor 处理默认值）
3. ✅ XmlInvoiceGenerator（StAX，对照样例逐段；PaymentMeans 只输出一次）
4. ✅ ApiExceptionHandler（assignableTypes=GenerateController）
5. ✅ GenerateController（鉴权 → BR-CO-26 检查 → 生成 → 自校验 → 响应）
6. ✅ GenerateControllerTest（14 用例，含响应 bytes 回喂 FacturxValidator 断言 valid）
7. ✅ `mvn -pl api -am test` 全绿（38 测试）+ 本地 curl 双端点冒烟
8. ✅ spec/plan 文档

## 本地冒烟结果（2026-10-08）

- compose db（127.0.0.1:5433）+ `mvn -pl api spring-boot:run`（DB_* 环境变量）
- POST /api/generate 合法 JSON → 200 application/xml + Content-Disposition
- 生成 XML 回喂 POST /api/validate → `{"valid":true}`（双端点闭环）
- 负路径：无 key 401、坏 key 401、卖方无标识符 400（BR-CO-26 文案）

## 踩坑记录

1. **`produces=application/xml` 会让 Map 错误体 500**：无 XML message converter。
   解决：映射不声明 produces，成功响应显式 contentType。
2. **本地 pgdata 卷残留导致 Flyway 起不来**：api_keys 表在但 flyway_api_history
   只有 baseline 0、无 V1 记录 → V1 重跑 CREATE TABLE 冲突。解决：手工补一条
   V1 历史行（checksum 借 web 模块同脚本的历史行）。生产无此问题（部署时已对齐）。
3. **JAVA_HOME 必须显式指向 `C:\Program Files\Java\jdk-17`**（PATH 里 javapath
   是 Java 17 但 mvn 无 JAVA_HOME 时 fork 出 Java 8，报 class file version 52.0）。
4. **BR-S-05**：category S + rate 0 非法；0% 须配 category Z。

## 后续

- 生产部署：本地 mvn package → scp → compose up -d --build（compose 不重建 JAR）
- Caddy 白名单需放行 /api/generate（当前只放行 /api/validate + /api/health）
- 配额/计费钩子（LemonSqueezy 上架前置）
