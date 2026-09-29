# VPS 部署方案（factur-x-api.com 上线）

> 2026-09-29 ｜ 前置：M2 工具页已合入 main；Dockerfile（单阶段拷贝预构建 JAR）+ docker-compose.yml 已就绪并在本机冒烟通过

## 1. 选型

目标市场是法国，机房选欧洲（法国/德国优先），不需要考虑 GFW 对法国访客的影响；只影响你从大陆管理 VPS 的体验（SSH 到欧洲一般直连可用）。

| 厂商 | 机型参考 | 价格区间 | 大陆注册/付款 | 备注 |
|---|---|---|---|---|
| **Hetzner**（德/芬） | CAX11 ARM 2C/4G 或 CPX11 x86 2C/2G | ~€4-5/月 | ⚠️ 有风控先例：新账户可能要求身份验证甚至拒单，建议备 PayPal | 性价比标杆，首选尝试 |
| **Netcup**（德） | VPS 2G | ~€3-5/月 | 注册门槛较低 | 备选 |
| **Contabo**（德） | VPS S | ~€5/月 | 风控松 | 便宜但超售口碑一般 |
| **Scaleway**（法） | Stardust/小型 | ~€3-6/月 | 需验证卡 | 备选 |

- **推荐路径**：先试 Hetzner（PayPal 或 Visa/MC）；被风控拒 → Netcup → Contabo。
- **架构**：x86 优先（避免 ARM 上 JRE/镜像的意外兼容问题），2G 内存够：JVM `-Xmx512m` + Postgres + Caddy。
- OS：Debian 12 或 Ubuntu 24.04。
- **付款风控提示**（同收款 SOP 经验）：注册账号 IP 环境干净些；不要一注册就开多台；Hetzner 若要求视频验证属正常流程。

## 2. 生产拓扑

```
法国访客 ─ HTTPS ─> Caddy(80/443, 自动 Let's Encrypt) ─> web:8080 (Spring Boot fat JAR)
                                                        ─> db:5432 (Postgres 16, 仅内网)
```

- 现有 `docker-compose.yml` 已有 app + db；生产需**新增 `docker-compose.prod.yml`**：
  - 加 Caddy 服务 + Caddyfile（`factur-x-api.com`, `www` → 反代 `web:8080`）
  - db **不映射端口到宿主机**（本地开发用的 `127.0.0.1:5433` 仅开发）
  - app `SPRING_PROFILES_ACTIVE=prod`（预留；当前无 profile 差异）
  - pgdata 用 named volume（现有）
- TLS 全自动：Caddy 申请/续期 Let's Encrypt，零配置。

## 3. 部署流程（沿用现有约定：JAR 本地构建，不依赖服务器拉 GitHub）

```bash
# 本地（大陆侧）
export JAVA_HOME="C:\Program Files\Java\jdk-17"
/c/MyWork/tools/apache-maven-3.9.16/bin/mvn -pl web -am package -DskipTests
scp web/target/web-0.1.0-SNAPSHOT.jar vps:~/factur-x-api/
scp docker-compose.prod.yml Caddyfile vps:~/factur-x-api/

# VPS 上（欧洲侧，Docker Hub 直连无墙）
cd ~/factur-x-api && docker compose -f docker-compose.prod.yml up -d --build
```

- DNS：Namecheap 加 A 记录 `@` 和 `www` → VPS IP（提前配好，TTL 300，等生效再起 Caddy，避免 Let's Encrypt 申请失败）
- 注意既有约定：**compose 不重建 JAR**，更新 = 重传 JAR + `up -d --build`（build 只是重打镜像层）

## 4. 服务器初始化（一次性）

1. `ssh-copy-id` + 禁密码登录（`PasswordAuthentication no`）
2. `ufw`：只放 22/80/443
3. 装 Docker（官方脚本，欧洲机房直连）
4. Postgres 备份：cron `pg_dump` → 本地目录，每日一档（当前库几乎为空，先最简）

## 5. 上线后验收

- [ ] `https://factur-x-api.com/validateur-factur-x` 浏览器手测拖拽（valid XML → 绿）
- [ ] `GET /` → 302；`GET /health` → 200
- [ ] 上传 >10MB 文件 → 400 法语提示
- [ ] Search Console：添加资源（Domain）→ DNS TXT 验证 → 提交 sitemap（sitemap/robots 属 SEO 任务，可先提交首页）
- [ ] CDN 注意事项：Tailwind/htmx 走 unpkg/tailwindcss CDN（限制已登记），欧洲访客无碍

## 6. 成本与节奏

- VPS ~€4-5/月 + 域名已付（~$10/年）——上线前固定成本仅此两项
- 建议顺序：**买 VPS → 初始化 → DNS → 生产 compose 上线 → 验收 → Search Console**
- 邮件捕获（Postgres 表单）不阻塞上线，上线后紧随其后做
