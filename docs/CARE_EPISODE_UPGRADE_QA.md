# CareNexus 门诊任务智能体升级质检记录

日期：2026-11-27
分支：`codex/care-episode-agent-upgrade`
范围：后端状态与数据链路、临床接管、患者任务恢复、医生工作台、前端入口与构建。

## 质检结论

本轮完成 8 轮产品级质检。核心链路已经从“页面展示分诊结果”升级为“一次就医任务”主线：

```text
患者会话 → care_episode → 风险/分诊 → 号源 → 预约 → 临床接管任务 → 医生接诊
```

确认通过的结果：

- 预问诊会话、分诊建议、可用号源和预约均可关联到同一个 `care_episode`。
- 状态推进使用服务端白名单、乐观版本和事件时间线；重复回调不会重复创建开放任务。
- 低置信度、风险征象和信息不足会生成临床接管任务，医生工作台新增任务入口。
- 临床复核从患者回复到重新入队的状态闭环已接通。
- 患者端真实浏览器可以输入、发送 SSE 对话，并看到就医任务卡片；输入框不会因页面常态状态被错误禁用。
- 比赛展示页、护士前端入口和护士演示账号已从产品前端移除；旧 `/api/nurse/**` 后端接口仅作为存量客户端兼容层保留。
- Element Plus 的表格、表单、弹窗、上传等低频组件改为异步分包；构建产物不再出现 900KB 级 `vendor-ui` 单块。

## 8 轮证据

| 轮次 | 检查内容 | 命令/证据 | 结论 |
| --- | --- | --- | --- |
| 1 | 基线与依赖 | `git diff --check`、`mvn -DskipTests compile`、`npm run build` | **CONFIRMED**；编译和生产构建通过 |
| 2 | 数据模型与迁移 | 检查 `schema.sql`、`CareEpisodeSchemaInitializer`；H2 启动上下文成功 | **CONFIRMED**；新表支持首次初始化和既有 MySQL 幂等迁移 |
| 3 | 状态不变量 | `HospitalV2FlowTest.care_episode_rejects_illegal_direct_transition` | **CONFIRMED**；非法直达 `COMPLETED` 被拒绝 |
| 4 | 业务链路与幂等 | `HospitalV2FlowTest.care_episode_links_intake_triage_slots_and_is_idempotent` | **CONFIRMED**；会话→分诊→号源和重复回调验证通过 |
| 5 | 权限与跨角色边界 | 全量 `mvn test`；`PatientNurseBoundaryTest`、`AuthSecurityTest` | **CONFIRMED**；患者归属、角色隔离、认证回归通过 |
| 6 | 异常、并发、资金与医嘱 | 全量测试中的 `ConcurrencyAndRecordTest`、`AgentBudgetTest`、`BookingPreferenceServiceTest`、`SensitiveDataCryptoTest` | **CONFIRMED**；现有并发、Agent 步数、加密和账务回归通过 |
| 7 | 前端交互与产品入口 | `npm run build`；真实浏览器登录、患者输入、SSE 发送、医生工作台加载 | **CONFIRMED**；无护士/比赛前端入口，输入框可用，医生接口返回成功 |
| 8 | 最终构建与现场冒烟 | 后端 `mvn -q -DskipTests package`；前端 Vite dev + Playwright 快照；异步 UI 分包构建 | **CONFIRMED**；本地环境可启动并完成冒烟，最大前端 JS 分包约 123.8KB，未再出现 500KB 以上分包警告 |

## 关键实现检查

### 数据一致性

- `pre_triage_session.episode_id` 将历史会话挂到就医任务。
- `care_episode_event` 使用 `(episode_id, idempotency_key)` 唯一约束记录状态变化。
- `agent_task` 保存下一步动作、优先级、分配角色和完成时间。
- 预约仍由原有服务做患者归属、号源余量、重复预约和事务校验；就医任务只在预约成功后推进到 `BOOKED`。

### 临床安全边界

- 风险规则优先于模型输出；风险分支不会进入普通预约。
- 模型只提供理解和建议，不能直接修改号源、预约、账单或病历。
- 临床接管任务由医生工作台处理；旧护士 API 未作为新产品页面入口暴露。

### 当前需要人工复核的项

- **UNVERIFIED**：真实 MySQL 既有生产库的升级演练尚未在本地执行；上线前需用备份副本验证 `ALTER TABLE`、索引和回滚窗口。
- **UNVERIFIED**：公网 Nginx/systemd 生产实例未在本轮重启后复验；不能把本地冒烟结果当作公网部署成功证据。
- **UNVERIFIED**：真实阿里云模型超时、限流和异常返回的现场演练未执行；当前测试覆盖确定性 Mock 和服务端异常边界。

## 现实业务边界依据

设计参考了公开门诊流程：国家卫生健康相关材料将智能导诊、预约、签到、接诊、检查、支付、报告、取药和随访放在连续服务链中，同时强调智能导诊不能替代医生判断；地方医院公开流程也将挂号、分诊、就诊、检查报告、取药和复诊作为连续路径。参见：[国家卫生健康相关门诊服务材料](https://www.nhc.gov.cn/mohwsbwstjxxzx/s2908/201911/734c26d5b30f4c27b4b5e437f049c858/files/1740021154678_46976.pdf)、[烟台市医院门诊就医流程](https://www.yantai.gov.cn/art/2025/1/30/art_90122_3022873.html)、[泉州一次挂号管一次就医流程](https://www.qzlj.gov.cn/zwgk/zdgkxx/jbylws/wjdt/202511/t20251103_3227284.htm)。
