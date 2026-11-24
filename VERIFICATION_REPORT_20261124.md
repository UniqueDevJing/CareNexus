# CareNexus 遗留问题修复与复测

日期：2026-11-24。范围：当前本地工作树。未推送、未部署、未操作生产数据。

## 原报告问题闭环

| 编号 | 修复 | 证据 |
|---|---|---|
| CN-01 | `PreTriageService.triageComplaint` 从服务端历史汇总主诉，追加并保存未发送补充，兼容旧前端传完整历史，避免重复 | `HospitalV2FlowTest` 上下文回归；`ConcurrencyAndRecordTest` 补充持久化测试 |
| CN-02 | `src/sse.js` 逐行处理 SSE，支持任意分块、LF/CRLF 与流结束残留，正文中的 data: 不被误删 | Node 解析器 2 项；真实页面多行事件回归 |
| CN-03 | Agent 工具预算耗尽后额外执行一次无工具收尾；多工具消息只追加一次；非法输出和降级结果进入人工复核；超时标记 FAILED | `AgentBudgetTest` 3 项，覆盖工具收尾、非法/降级输出、供应商超时 |
| CN-04 | 患者端异步操作互斥、离页中止流、恢复请求核对会话；锁定会话拒绝再次分诊 | 患者页面与后端回归、跨角色浏览器链路 |

## 进一步发现并修复

- `RegistrationService`：服务层再次校验患者归属；锁定同一建议防重复并发；扣减时复查排班状态及日期。
- `MedicalRecordService`：锁定挂号行，阻止重复建草稿；提交锁定病历并同步完成就诊；完成后拒绝再编辑。
- `DoctorConsole.vue`：病历请求按序核对，加载失败时锁定编辑；保存、提交、结诊互斥并显示失败；提交已有草稿前保存最新输入，避免最后一次编辑丢失。
- `NurseTriage.vue`：处理请求互斥，处理期间阻止轮询刷新和其他交互覆盖所选患者。
- `PatientController`：恢复预约时带回支付状态，避免刷新后已支付仍提示去付款。
- `ReActTriageAgent`：移除“无号源降低临床置信度”的逻辑。是否需要人工临床核对与运营号源是否可约分别处理。

## 执行结果

| 验证 | 结果 | 日志 |
|---|---|---|
| `hospital-v2: mvn test -q` | 39 项通过；MySQL 专项默认跳过 | `hospital-v2/expanded-test.log` |
| `RUN_ISOLATED_MYSQL_TEST=true` + `mvn test -q -Dtest=MySqlConcurrencyTest` | 5 项通过，MySQL 9.2/InnoDB | `hospital-v2/mysql-test.log` |
| `node --test src/sse.test.js` | 2 项通过 | `hospital-web-v2/sse-test.log` |
| Playwright 四角色、SSE、完整临床写入链路 | 6 项通过 | `hospital-web-v2/clinical-browser.log` |
| 375px/768px 四角色和 SSE | 10 项通过 | `hospital-web-v2/responsive-browser.log` |
| `npm run build` | 通过 | `hospital-web-v2/closure-build.log` |

临床浏览器链路使用真实本地 API 和隔离 MySQL，模型为 mock，工具预算设为 0 来强制进入人工接管分支。实际操作：患者预问诊→护士确认→患者刷新状态→预约→支付→医生保存草稿→修改诊断后提交→患者查看最终内容。不是只检查页面能打开。

MySQL 并发测试：两个患者竞争最后一个号源，断言只有一次成功、余号为 0、仅一条挂号记录；另验证非本人、待确认、被拒绝和被替代的建议均不能预约。

## 复现条件与边界

- 浏览器配置为 `hospital-web-v2/audit.config.js`；需要自行启动本地后端 18093，前端由配置启动。临床写入测试需独立、未占用相同时段的患者测试数据；本次先初始化专用数据库并设置未来排班和测试余额。
- MySQL 专项仅连接 `127.0.0.1:13316/care_audit`，需要全新空库用于 schema 初始化；不得指向生产库。临时实例与正式安装的数据库服务互不影响。
- 375px/768px 是 Chromium 下角色入口和 SSE 验证，不等于全部页面在所有浏览器上的视觉验收。
- 仍存在 Element Plus 大资源包构建告警。本轮未做不影响正确性的依赖替换或全站重构。
- 未调用付费真实 LLM，未检查生产 Nginx、生产迁移和真实患者数据。这里的通过结论仅适用于记录的本地测试，不能代替生产发布验收。
