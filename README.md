# CareNexus

CareNexus 是面向医院运营与患者就诊流程的智能医疗工作台。它把患者预问诊、风险拦截、科室与医生匹配、号源预约、医生接诊和运营观测连接成一条可追踪的业务链路。

## 在线体验

- 产品入口：<http://114.215.186.113/CareNexus/>
- 演示账号：请使用部署环境提供的账号，不要在仓库提交生产凭据
- 关联项目：[MinutesTrace](https://github.com/UniqueDevJing/minutestrace) —— 证据化会议纪要工作台

## 产品能力

### 患者端

- 可恢复的多轮预问诊会话，历史消息持久化
- 基于健康档案、症状上下文和红旗规则的安全追问
- 分诊建议、医生与可用号源匹配
- 用户确认后完成预约，并返回预约日期、时段与凭证
- 就诊记录与个人健康档案管理

### 医生端

- 患者队列与接诊工作台
- 结构化病历编辑、提交和状态流转
- 关联预约、主诉、现病史、既往史和过敏史

### 管理端

- 科室、医生、排班和患者基础数据
- AI 模型、Prompt、工具和医学知识库配置
- Agent 执行时间线、工具调用日志和运营指标

## 技术架构

| 层 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot 3.5、Spring JDBC、Spring Security、LangChain4j |
| 前端 | Vue 3、Vite、Vue Router、Element Plus、GSAP |
| 数据库 | H2（开发）/ MySQL 8（生产） |
| 模型 | 阿里云百炼兼容接口（Qwen Chat / Embedding） |
| 接口 | REST、SSE、JWT HttpOnly Cookie |

## 本地运行

```bash
cd hospital-v2
mvn test
mvn spring-boot:run
```

另开终端启动前端：

```bash
cd hospital-web-v2
npm ci
npm run dev
```

前端入口：`http://127.0.0.1:5192/CareNexus/`

## 关键业务链路

```text
患者描述症状
  → 恢复会话上下文与健康档案
  → 安全追问与红旗征象拦截
  → Agent 调用症状 / 科室 / 医生 / 排班工具
  → 代码侧校验医生归属与可用号源
  → 患者确认后原子扣减号源并生成预约
```

预约不会由模型直接落库，必须经过患者确认；号源扣减、重复预约和并发冲突由后端事务与数据库约束处理。

## 部署

生产部署配置位于 [`deploy/`](deploy/)，静态入口为 `/CareNexus/`，API 前缀为 `/v2/api`。生产环境必须通过环境变量配置数据库密码、JWT Secret 和模型密钥，禁止提交 `.env`。

## 质量检查

```bash
cd hospital-v2 && mvn test && mvn -B package -DskipTests
cd ../hospital-web-v2 && npm ci && npm run build
```

详细质量门禁记录见 [`QUALITY_GATE_REPORT.md`](QUALITY_GATE_REPORT.md)。
