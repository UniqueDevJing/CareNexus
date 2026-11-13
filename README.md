# CareNexus

CareNexus 是课程 120「AI Agent 智慧医院智能导诊与就诊系统」的独立实现。仓库只包含本项目的 Spring Boot 后端与 Vue 前端，不依赖任何旧健康管理平台、旧多模块工程或旧 Agent 项目。

## 项目结构

```text
CareNexus/
├─ hospital-v2/       # Spring Boot 3.5 + Java 21 + LangChain4j
├─ hospital-web-v2/   # Vue 3 + Vite + Element Plus + GSAP
├─ deploy/             # CareNexus 独立部署配置
├─ docs/               # OpenAPI 与 CareNexus 文档
└─ .github/workflows/  # 仅构建本项目两个子工程
```

## 已实现能力

- 患者注册、登录、个人资料与就诊记录
- AI 预问诊：多轮追问、红旗症状识别、分诊建议与 SSE 流式反馈
- 分诊确认后预约登记、医生队列、电子病历保存与提交
- 管理员驾驶舱、科室/医生/排班/患者/症状标签维护
- AI 模型与 Agent 工具配置、工具测试与调用日志
- 医学知识库、文档上传、切片检索与向量化状态展示
- 使用统计、运行时间线、Agent 运行与工具调用观测

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot 3.5.16、Spring JDBC、Spring Security、LangChain4j 1.15 |
| 数据库 | 本地 H2；生产 MySQL 8 |
| AI | 阿里云百炼 OpenAI 兼容接口（Qwen Chat / Embedding），未配置 Key 时支持离线 Mock |
| 前端 | Vue 3、Vite、Vue Router、Element Plus、GSAP |

## 本地启动

### 后端

```bash
cd hospital-v2
mvn test
mvn spring-boot:run
```

默认服务地址为 `http://127.0.0.1:18092/v2`。生产 MySQL 配置使用：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

### 前端

```bash
cd hospital-web-v2
npm ci
npm run dev
```

开发入口为 `http://127.0.0.1:5192/CareNexus/`，Vite 会将 `/v2/api` 代理到后端。

## 关键环境变量

复制 `.env.example` 为本地环境文件，并至少设置生产环境的 `MYSQL_PASSWORD`、`AUTH_JWT_SECRET` 与阿里云百炼 `LLM_API_KEY`。生产环境建议设置 `LLM_MODE=real`，本地无 Key 时可使用 `LLM_MODE=mock`。

## 构建检查

```bash
cd hospital-v2 && mvn -B test && mvn -B package -DskipTests
cd ../hospital-web-v2 && npm ci && npm run build
```

## 部署

详见 [`deploy/部署指南.md`](deploy/部署指南.md)。线上静态入口为 `/CareNexus/`，后端 API 前缀为 `/v2/api`。

## 项目边界声明

CareNexus 是独立的课程 120 医疗 Agent 项目，仓库边界仅覆盖 `hospital-v2` 与 `hospital-web-v2` 两个可构建子工程。
