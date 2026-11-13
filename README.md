# CareNexus

CareNexus 是一套面向医院日常工作的就诊协同系统。它把患者描述、分诊、号源、接诊和后台管理放在同一个业务闭环里，重点解决“信息到了哪里、谁负责下一步、状态是否已经落库”这三个问题。

## 适用场景

- 患者在就诊前填写症状和健康信息，获得就诊方向和可预约号源。
- 医生在接诊台查看患者队列，补充结构化病历并提交就诊记录。
- 管理人员维护科室、医生、排班、模型配置、提示词、工具和知识资料。
- 运营人员查看预约、接诊、模型调用和异常处理情况。

## 功能一览

### 患者端

- 注册、登录和会话恢复。
- 多轮预问诊：会话内容按用户保存，重新进入后可以继续填写。
- 健康档案：既往史、过敏史、长期用药和常用联系方式。
- 风险提示：对胸痛、呼吸困难、意识异常等高风险描述先行拦截，并给出就医建议。
- 科室、医生和排班查询。
- 预约确认：患者确认后才创建预约，重复预约和号源竞争由服务端校验。
- 预约记录、就诊记录和结果查看。

### 医生端

- 今日接诊队列和预约详情。
- 查看患者主诉、预问诊结果、健康档案和历史就诊记录。
- 编辑结构化病历，保存草稿并提交正式记录。
- 根据权限查看对应患者，不允许通过修改请求参数越权读取其他患者。

### 管理端

- 科室、医生、排班、患者和基础字典维护。
- 模型连接、提示词模板、工具注册和医学知识资料管理。
- Agent 运行时间线、工具调用日志和运营统计。
- 账号角色、权限和操作审计。

## 核心业务链路

```text
患者填写症状
    → 恢复会话与健康档案
    → 规则检查与必要追问
    → 匹配科室、医生和可用号源
    → 患者明确确认
    → 事务内锁定号源并创建预约
    → 医生接诊并提交病历
    → 患者和管理端查看最新状态
```

模型只负责理解文本和生成建议，不直接写入预约、病历或权限数据。最终写入前由服务端完成角色校验、归属校验、状态校验和数据库事务处理。

## 技术组成

| 部分 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot 3.5、Spring JDBC、Spring Security、LangChain4j |
| 前端 | Vue 3、Vite、Vue Router、Element Plus、GSAP |
| 数据库 | H2（开发）/ MySQL 8（生产） |
| 模型接口 | 阿里云百炼兼容接口，支持对话和向量模型 |
| 通信方式 | REST、SSE、JWT HttpOnly Cookie |
| 部署 | Nginx、systemd 或 Docker Compose |

## 目录结构

```text
CareNexus/
├─ hospital-v2/       # Spring Boot 服务、业务规则和数据库访问
├─ hospital-web-v2/   # Vue 前端
├─ deploy/            # Docker、Nginx 和生产环境示例
├─ QUALITY_GATE_REPORT.md
└─ CHANGELOG.md
```

## 本地运行

### 启动后端

```bash
cd hospital-v2
mvn test
mvn spring-boot:run
```

### 启动前端

另开终端执行：

```bash
cd hospital-web-v2
npm ci
npm run dev
```

默认入口：`http://127.0.0.1:5192/CareNexus/`

首次运行请根据 `.env.example` 配置数据库、会话密钥和模型接口。生产密钥只通过环境变量注入，不写入仓库。

## 部署说明

部署文件位于 [`deploy/`](deploy/)，前端路径为 `/CareNexus/`，后端接口前缀为 `/v2/api/`。

生产环境建议：

1. 由网关或 Nginx 终止 HTTPS。
2. 设置 `AUTH_COOKIE_SECURE=true`。
3. 使用 MySQL 8，并为数据库账号设置最小权限。
4. 使用随机 JWT Secret 和独立的模型接口密钥。
5. 通过 systemd 或容器编排保证后端、前端和数据库的启动顺序。

## 测试与质量检查

```bash
cd hospital-v2
mvn test
mvn -B package -DskipTests

cd ../hospital-web-v2
npm ci
npm run build
```

完整检查记录见 [`QUALITY_GATE_REPORT.md`](QUALITY_GATE_REPORT.md)。

## 相关项目

- [MinutesTrace](https://github.com/UniqueDevJing/minutestrace/)：会议资料、转写片段和正式纪要的协同工作台。
