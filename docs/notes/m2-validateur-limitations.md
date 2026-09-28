# M2 法语工具页已知限制（/validateur-factur-x）

> 2026-09-28，实现登记。26 测试全绿（core 16 + web 10），冒烟通过（valid XML→绿 / invalid→红 BR-07/10/11 / PDF→绿 / 假 PDF→422 / 垃圾→400 / `/`→302）。

## 1. 法语映射只覆盖常见规则
`RuleMessages` 覆盖 golden 实测 id（BR-07/10/11/BR-CO-16）+ ~30 条常见 EN 16931 规则 + XSD。长尾 BR 码 fallback 显示引擎原始英文 message，页面标注「message technique non traduit」。后续可按线上实际触发频次补映射。

## 2. validator-core 的 catch(Exception) 坑（本任务未修）
`XmlRuleValidator.java:88-90` 会把引擎自身故障伪装成 ruleId=XSD 的 ERROR Issue，web 层以 200 正常渲染（英文引擎文本兜底展示）。另行排期修 validator-core，修完后此处的英文文本路径自然消失。

## 3. Tailwind/htmx 走 CDN
`cdn.tailwindcss.com` + `unpkg.com`。部署到法国 VPS 后欧洲用户访问无碍；但生产化时建议本地化资源（下载进 JAR / self-host），消除第三方 CDN 可用性依赖。Tailwind CDN 官方也声明仅供开发用途。

## 4. warnings 区恒空
SVRL SuccessfulReport（WARNING 级）未接入 validator-core，`ValidationResult.warnings` 恒空。UI 已预留黄色区块与渲染逻辑，接入后自动生效（M1 遗留项）。

## 5. 实现要点备忘（非限制）
- 普通返回路径的 400/422 通过 `HttpServletResponse.setStatus` 显式设置（`@ResponseStatus` 只对 @ExceptionHandler 生效）
- 错误/成功 fragment 按 `HX-Request` 头区分：htmx→fragment swap，禁 JS→整页渲染
- multipart `max-file-size: 10MB` 显式配置（Spring Boot 默认 1MB 会先拦）；`getBytes()` 读内存后不落盘，日志只记元数据
- MockMvc 测试里 `.header()` 返回父类型，链 `.file()` 前需强转回 `MockMultipartHttpServletRequestBuilder`
