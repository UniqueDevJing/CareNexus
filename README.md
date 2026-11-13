# CareNexus｜基于 LangChain4j + Spring Boot 的智能分诊就医系统

CareNexus 面向医院真实工作流，覆盖患者预问诊、风险拦截、号源预约、医生接诊、结构化病历和运营管理。项目重点不是“聊天页面”，而是把患者描述转成可校验、可落库、可追踪的就诊流程。

## 在线体验（仅演示）

- 体验地址：<http://114.215.186.113/CareNexus/>
- 患者端：`patient` / `123456`
- 医生端：`doctor` / `123456`
- 管理端：`admin` / `123456`

以上账号仅用于公开演示，不包含真实患者数据，也不代表生产权限。生产环境需要单独配置账号、数据库、JWT 密钥和阿里云百炼密钥。

## 项目亮点

- **完整就诊闭环**：患者描述症状 → 风险判断 → 科室/医生匹配 → 患者确认预约 → 医生接诊 → 病历提交。
- **上下文可恢复**：会话消息、健康档案和历史就诊信息统一进入预问诊流程，支持多轮追问和刷新后继续。
- **安全边界清晰**：胸痛、呼吸困难、意识异常等高风险征象先走确定性规则，模型只负责理解和生成建议，不直接决定高风险状态。
- **预约可并发控制**：候选号源、患者确认、事务落库分阶段处理，服务端校验号源状态、患者归属、重复预约和竞争更新。
- **角色工作台**：患者端、医生端、管理端分别提供就诊、接诊和运营视图；权限在接口层和数据归属层同时校验。
- **可运营可追踪**：提供 Agent 执行时间线、工具调用日志、模型配置、提示词、知识资料和运营看板。

## 功能模块

### 患者端

- 注册、登录、会话恢复和健康档案。
- 多轮预问诊、红旗征象提示和就医建议。
- 科室、医生、排班、号源查询与预约确认。
- 预约记录、就诊记录和结构化病历结果查看。

### 医生端

- 今日接诊队列、患者背景和历史就诊信息。
- 结构化病历草稿、保存、提交和提交后锁定。
- 患者归属校验，禁止通过修改请求参数越权读取其他患者。

### 管理端

- 科室、医生、排班、患者和基础字典维护。
- 模型连接、提示词、工具注册、工具日志和医学知识资料。
- 就诊运营、Agent 执行、模型调用和异常观测。

## 技术与架构

| 层次 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot 3.5、Spring Security、Spring JDBC |
| Agent / 模型 | LangChain4j、阿里云百炼兼容接口、SSE |
| 前端 | Vue 3、Vite、Vue Router、Element Plus、GSAP |
| 数据 | MySQL 8（生产）、H2（开发） |
| 部署 | Nginx、Docker Compose、systemd |

核心写入由服务端完成角色校验、患者归属校验、状态校验和事务控制；模型输出必须经过规则、数据和人工边界后才能进入业务状态。

## 目录结构

```text
CareNexus/
├─ hospital-v2/       # Spring Boot 服务、业务规则、数据库访问
├─ hospital-web-v2/   # Vue 前端与角色工作台
├─ deploy/            # Docker、Nginx、环境变量示例
├─ QUALITY_GATE_REPORT.md
└─ CHANGELOG.md
```

## 本地运行

### 后端

```bash
cd hospital-v2
mvn test
mvn spring-boot:run
```

### 前端

```bash
cd hospital-web-v2
npm ci
npm run dev
```

默认入口：<http://127.0.0.1:5192/CareNexus/>。首次运行请按 `.env.example` 配置数据库、会话密钥和模型接口；密钥只通过环境变量注入。

## 部署与验证

生产入口为 `/CareNexus/`，后端接口前缀为 `/v2/api/`。部署前执行：

```bash
cd hospital-v2 && mvn -B clean package -DskipTests
cd ../hospital-web-v2 && npm ci && npm run build
```

生产环境使用 MySQL 8、随机 JWT Secret、独立模型密钥，并由 Nginx 或网关终止 HTTPS。部署说明见 [`deploy/部署指南.md`](deploy/部署指南.md)，质量记录见 [`QUALITY_GATE_REPORT.md`](QUALITY_GATE_REPORT.md)。

## 相关项目

- [MinutesTrace](https://github.com/UniqueDevJing/minutestrace/)：会议资料、转写、证据链纪要和行动项协同工作台。
