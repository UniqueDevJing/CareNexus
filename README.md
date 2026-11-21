# CareNexus｜基于 LangChain4j + Spring Boot 的智能分诊就医系统

CareNexus 面向医院真实工作流，覆盖患者预问诊、风险拦截、号源预约、医生接诊、结构化病历和运营管理。项目重点不是“聊天页面”，而是把患者描述转成可校验、可落库、可追踪的就诊流程。

## 在线体验（仅演示）

- 体验地址：<http://114.215.186.113/CareNexus/>
- 患者端：`patient` / `123456`
- 护士端：`nurse` / `123456`
- 医生端：`doctor` / `123456`
- 管理端：`admin` / `123456`

以上账号仅用于公开演示，不包含真实患者数据，也不代表生产权限。生产环境需要单独配置账号、数据库、JWT 密钥和阿里云百炼密钥。

## 项目亮点

- **完整就诊闭环**：患者描述症状 → 风险判断 → 科室/医生匹配 → 患者确认预约 → 医生接诊 → 病历提交。
- **上下文可恢复**：会话消息、健康档案和历史就诊信息统一进入预问诊流程，支持多轮追问和刷新后继续。
- **安全边界清晰**：胸痛、呼吸困难、意识异常等高风险征象先走确定性规则，模型只负责理解和生成建议，不直接决定高风险状态。
- **预约可并发控制**：候选号源、患者确认、事务落库分阶段处理，服务端校验号源状态、患者归属、重复预约和竞争更新。
- **角色工作台**：患者端、护士端、医生端、管理端分别提供就诊、人工导诊、接诊和运营视图；权限在接口层和数据归属层同时校验。
- **可运营可追踪**：提供 Agent 执行时间线、工具调用日志、模型配置、提示词、知识资料和运营看板。

## Agent 设计取舍（面试版）

CareNexus 的 Agent 不是把模型接到一个聊天框，而是让模型在受限工具链中做分步决策：先确认症状标签，再选择科室、医生和可用号源；每一步都把工具返回结果带回下一轮上下文。

- **决策边界**：模型负责理解主诉、选择查询路径和生成建议；高风险征象、数据归属、号源状态和预约写入由服务端规则与事务控制。
- **止损机制**：设置最大调用步数并持久化 Agent 步骤。模型未收敛、服务降级或命中风险边界时生成 `PENDING` 建议，禁止直接预约，患者可继续补充信息，管理端进入人工导诊队列。
- **二次核验**：最终结果重新校验科室存在性、医生归属和实际号源，工具失败、模型异常和事务异常均记录运行状态与原因。

因此，项目展示的重点是“模型如何在边界内做决策，以及不确定时如何停止”，而不是单纯展示一次模型调用。

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
- 待人工导诊队列：护士端对低证据建议进行确认或退回，管理端保留运营审计视图，并记录操作审计。

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

## 赛事作品（2026 Qoder 高校创造力大赛 · CareNexus）

CareNexus 赛事作品与医院工作台使用同一套真实工程能力，围绕“患者信息如何安全进入医院协作流程”重新组织公开入口。页面展示代码、来源登记、构建结果和发布结果，不接收患者输入，也不读取生产数据。

- 作品入口：<https://carenexus-clinical-workflow-yxmrb2k9vhk.qoder.website/>。根路径进入公开作品页，静态资源不依赖登录或后端写入。
- GitHub 代码：<https://github.com/UniqueDevJing/CareNexus/tree/contest/carenexus-independent-2026>。
- 真实工作台：<http://114.215.186.113/CareNexus/>。该入口需要登录，并按患者、护士、医生、管理角色限制功能范围。
- 天池原帖：<https://tianchi.aliyun.com/forum/post/1100237>；当前文章正文见 [`TIANCHI_SUBMISSION.md`](TIANCHI_SUBMISSION.md)，原帖更新需要已登录的天池账号。
- 作品入口实现：`hospital-web-v2/src/views/contest/ContestShowcase.vue` 与同名 CSS；`npm run build:contest` 使用根路径资源，普通 `npm run build` 仍使用 `/CareNexus/`。
- 仓库事实：4 个角色工作区、7 条来源登记、30 个来源摘要、Agent 最大执行步数 7。数值均来自路由、`data.sql`、服务类和 `application.yml`，不是用户规模、准确率或临床效果指标。
- 来源边界：知识库保留来源机构、版本、链接、短摘要和审核状态；只有 `SOURCE_VERIFIED` 或 `CLINICIAN_APPROVED` 的内容可以参与检索，未核验资料停留在审核状态。

### Qoder 实际开发流程

以下流程对应本次在 Windows Qoder 环境中的实际操作，命令均在仓库工作区执行：

1. **读取仓库现状**：执行 `git fetch origin --prune`、`git log`、`git status`，确认最新生产分支、当前路由、后端服务、知识库表结构和现有发布入口。
2. **确认业务链路**：读取患者、护士、医生、管理四端路由，核对 `ReActTriageAgent`、`ManualReviewService`、`KnowledgeService` 的状态和边界，再按实际职责组织页面信息。
3. **核对来源登记**：从 `hospital-v2/src/main/resources/db/data.sql`、schema 和知识服务读取 7 条来源登记、30 个来源摘要、审核状态和权威链接；页面只呈现来源元数据与外链。
4. **实现公开入口**：新增 `/contest` 公共路由、赛事构建模式和独立视觉页面；不接入患者输入，不创建临床结果，不改变普通工作台的登录和接口流程。
5. **本地构建与测试**：执行 `npm ci`、`npm run build:contest`、`npm run build`、`mvn -q test` 和 `git diff --check`。构建或测试失败时不发布。
6. **浏览器验收**：用 Playwright 检查桌面与移动视口、页面标题、导航、外链、资源加载、控制台错误和横向溢出；再检查公开 Qoder 地址的 HTTP 响应。
7. **GitHub 推送与持续集成**：将代码推送到 `contest/carenexus-independent-2026`，由 GitHub Actions 重新执行后端测试、后端打包、普通前端构建和赛事前端构建。
8. **Qoder Sites 发布**：使用真实 Qoder CLI 的 `qoder sites prepare_site`、`publish_site`、`get_publish_status` 发布 `hospital-web-v2/dist`，完成后重新打开公网地址验收。

完整记录见 [`TIANCHI_SUBMISSION.md`](TIANCHI_SUBMISSION.md) 与 [`QUALITY_AUDIT_REPORT.md`](QUALITY_AUDIT_REPORT.md)。

## 相关项目

- [MinutesTrace](https://github.com/UniqueDevJing/minutestrace/)：会议资料、转写、证据链纪要和行动项协同工作台。
