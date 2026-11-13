# CareNexus 系统级质检与优化报告

## 范围与结论

检查对象为 `hospital-v2` 与 `hospital-web-v2`，覆盖认证、患者预问诊/分诊、挂号、医生病历、知识库/RAG、Agent 工具与管理端、前端构建和生产配置。修复后本地回归测试 15 项全通过，前端生产构建通过，Docker Compose 配置可解析。

当前结论：**代码可进入预发布验收，但尚不能直接宣称生产已验收**。生产还必须用 Java 21 + MySQL 8 + 阿里云百炼真实 Key 做一次冒烟和并发测试。

## 优化优先级清单

| 编号 | 级别 | 问题/风险 | 修复状态 |
|---|---|---|---|
| CN-P0-01 | 致命 | 挂号扣号源、写挂号、标记建议原为多次写库，异常可能产生脏号源 | 已修复：`RegistrationService.register` 增加事务 |
| CN-P0-02 | 致命 | 病历提交与挂号完成原为两次写库，可能出现病历已提交但挂号未完成 | 已修复：`MedicalRecordService.submit/finishVisit` 增加事务 |
| CN-P0-03 | 致命 | Agent 调用失败会遗留 `agent_run=RUNNING`，运行观测与重试失真 | 已修复：`ReActTriageAgent` 统一失败落库 |
| CN-P1-01 | 严重 | 未捕获异常把 JDBC/模型错误信息直接返回客户端 | 已修复：统一返回通用 500，服务端记录堆栈 |
| CN-P1-02 | 严重 | 注册年龄无边界、并发重名可能返回 500 | 已修复：年龄校验与重复键转业务错误 |
| CN-P1-03 | 严重 | 生产 JWT 占位密钥可被误配置后启动 | 已修复：MySQL profile 拒绝占位密钥，并增加测试 |
| CN-P1-04 | 严重 | 知识库上传一次性读入任意文件，存在内存/存储滥用风险 | 已修复：仅 TXT/MD，单文件 10 MB |
| CN-P2-01 | 建议 | Element Plus 初始 UI chunk 约 923 KB | 待后续：按页面拆分组件或引入按需样式构建 |
| CN-P2-02 | 建议 | 生产 Nginx 尚未做真实 HTTPS/HSTS/CSP 验收 | 待部署环境人工复核 |

## 验证记录

- `hospital-v2`：Maven 测试 15 项通过；本机仅有 Java 17，因此使用 `-Dmaven.compiler.release=17` 做编译/逻辑回归，生产目标 Java 21 仍需发布机复跑。
- `hospital-web-v2`：`npm run build` 通过；`npm audit` 从 2 项（含 1 高危 Vite）降为 0 项，Vite 升级到 7.3.8。
- Docker：`docker compose ... config` 通过；未在本机启动 MySQL/百炼服务。

## 上线前人工门禁

1. 使用 Java 21 构建并启动 MySQL profile，确认数据库迁移、连接池、字符集和时区。
2. 注入随机 `AUTH_JWT_SECRET`、数据库密码和百炼 Key；确认不使用 `.env.example` 占位值。
3. 并发压测同一排班号源，验证 `remain_num` 不为负且无重复挂号。
4. 实测百炼超时/429/5xx 时 Agent 状态最终为 FAILED，并能从观测页恢复。
5. 用患者、医生、管理员三类账号逐条验证对象级越权和 SSE 断线重连。
