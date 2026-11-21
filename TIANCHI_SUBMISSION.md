# CareNexus｜把一次预问诊变成可核验的就诊流程

CareNexus 是我独立完成的医院就诊安全工作台。它不把 Agent 做成一个只会聊天的入口，而是把患者主诉、护士导诊、医生接诊、医学来源和运营审计连接成一条可追踪的业务链路。

作品入口：<https://carenexus-safepath-yxmrb2k9vhk.qoder.website/>

代码仓库：<https://github.com/UniqueDevJing/CareNexus/tree/contest/carenexus-independent-2026>

真实工作台：<http://114.215.186.113/CareNexus/>

## 一、我解决的真实问题

医院预问诊并不缺一个聊天框，真正困难的是：患者描述往往不完整，风险等级不能只靠生成式回答决定；科室和号源需要实时查询；低置信度结果必须有人接管；医生需要看到结构化、可回放的就诊上下文；医学来源必须知道从哪里来、是否经过审核、何时可以进入检索。

CareNexus 的核心设计是“信息不足先补齐，证据不足先停下，风险异常先转人工”。因此系统不会把不确定结果直接变成预约或临床结论。

## 二、四端如何协同

### 患者端

患者提交主诉、补充健康档案，获得就医建议、预约状态和后续处理状态。患者只能访问自己的会话、预约和就诊记录。

### 护士端

护士端不是一个装饰页面，而是 Agent 失去足够证据时的人工接管台。它接收信息不足、风险异常、工具失败、模型未收敛等任务，可以向患者提出具体补问，也可以将任务转给医生或结束在线流程。护士不能修改管理规则和医学来源。

### 医生端

医生端查看候诊资料、历史就诊信息和结构化病历草稿，完成临床确认与提交。患者归属、病历状态和提交锁定由服务端重新校验，前端页面不能越过这些边界。

### 管理端

管理端维护科室、医生、排班、工具、模型配置、提示词、医学来源、知识片段和 Agent 执行记录。它负责让规则和证据可运营、可追踪，但不把管理操作包装成医生诊断。

四端关系可以概括为：患者提供信息，Agent 组织路径，护士处理不确定性，医生完成临床确认，管理端维护规则与证据。每一步都有状态和去向，不依赖某一个页面上的按钮来保证安全。

## 三、Agent 的工程边界

Agent 采用受限工具链推进任务：先理解主诉，再查询症状标签、科室、医生和号源。它可以选择下一步工具，但不能绕开服务端直接写入预约。

- 红旗征象在 Agent 循环前由确定性规则拦截，不把高风险判断交给自由生成。
- 每次工具返回都会进入下一轮上下文，最终结果还要重新核对科室存在性、医生归属和实际号源。
- application.yml 将最大执行步数设为 7，超限进入止损路径。
- 信息不足、证据不足、工具失败或模型异常时，状态进入 PENDING 或 NEED_MORE_INFO，由护士队列接管。
- 业务写入仍由服务端完成角色校验、患者归属校验、状态校验和事务控制。

对应实现文件包括 ReActTriageAgent.java、ManualReviewService.java、KnowledgeService.java 和 ToolCenter.java。

## 四、知识库与真实来源

知识库不是把网页全文直接塞给模型，而是保存来源登记、版本、官方链接、短摘要、审核状态和知识片段。当前仓库登记 7 条来源、30 个来源摘要；检索服务只接受 SOURCE_VERIFIED 或 CLINICIAN_APPROVED 的内容，PENDING_REVIEW 和未核验上传资料不能进入决策支持。

当前登记的来源为：

1. Global Initiative for Asthma，GINA Strategy Report 2026：<https://ginasthma.org/2026-gina-strategy-report/>
2. Global Initiative for Chronic Obstructive Lung Disease，GOLD Report and Pocket Guide 2026：<https://goldcopd.org/2026-gold-report-and-pocket-guide/>
3. 中国高血压防治指南 2024 修订版，DOI：<https://doi.org/10.16439/j.issn.1673-7245.2024.07.002>
4. 2021 AHA/ACC Chest Pain Guideline：<https://professional.heart.org/en/guidelines-statements/2021-ahaaccasechestsaemscctscmr-guideline-for-the-evaluation-and-diagnosis-ofcir0000000000001029>
5. American Diabetes Association，Standards of Care in Diabetes 2026，DOI：<https://doi.org/10.2337/dc26-S002>
6. NICE，Fever in under 5s: assessment and initial management，NG143：<https://www.nice.org.uk/guidance/ng143/chapter/Recommendations>
7. 国家卫生健康委员会等，儿童肺炎支原体肺炎诊疗指南（2025 年版）：<https://www.nhc.gov.cn/yzygj/c100068/202509/19e7145436b049a68d4ba8c2060dfa56/files/%E5%84%BF%E7%AB%A5%E8%82%BA%E7%82%8E%E6%94%AF%E5%8E%9F%E4%BD%93%E8%82%BA%E7%82%8E%E8%AF%8A%E7%96%97%E6%8C%87%E5%8D%97%EF%BC%882025%E5%B9%B4%E7%89%88%EF%BC%89.pdf>

知识库内容只承担决策支持，不替代医生诊断；来源链接由发布机构负责，系统不收录受版权保护的全文。页面中的来源数量、状态和链接均可在 hospital-v2/src/main/resources/db/data.sql 与 MEDICAL_KNOWLEDGE_SOURCES.md 中核验。

## 五、Qoder 环境中的完整开发流程

以下是本作品实际采用的开发闭环，工作目录为 Windows Qoder 环境中的 CareNexus 仓库。

### 1. 获取最新工程状态

~~~powershell
git fetch origin --prune
git status --short
git log origin/refactor/production-workspace-refresh -1 --oneline
~~~

先确认生产分支的最新提交，再读取前端路由、后端 Agent、人工导诊服务、知识库 schema 和已有部署入口，页面内容只从这些真实实现中提取。

### 2. 读取业务边界

~~~powershell
rg -n "patient|nurse|doctor|admin|PENDING|NEED_MORE_INFO|CONFIRMED" hospital-web-v2 hospital-v2
rg -n "source_organization|source_url|review_status" hospital-v2
~~~

这一步确定四端职责、人工接管状态、来源审核状态和服务端校验点，避免页面把一个 Agent 过程说成自动诊断。

### 3. 重构公开作品页

在 hospital-web-v2 中新增公开 /contest 路由，并增加 contest 构建模式。页面采用医疗工作台的纸张、墨绿、陶土红和衬线标题体系，把四端链路、Agent 止损、来源登记、开发过程和验证入口放在一条阅读路径上。公开入口不读取患者输入，不调用业务 API，不写入数据库。

### 4. 本地构建与后端验证

~~~powershell
cd hospital-web-v2
npm ci
npm run build:contest
npm run build

cd ..\hospital-v2
mvn -q test

cd ..
git diff --check
~~~

两种前端产物分别服务公开根路径和真实工作台子路径；后端测试验证业务边界；差异检查保证提交内容没有格式错误。

### 5. Qoder 浏览器验收

使用 Qoder 中的 Playwright 浏览器工具检查：

- 根路径是否能进入 /contest；
- 页面标题和导航锚点是否正确；
- GitHub、真实工作台、官方来源和天池文章链接是否可打开；
- 1440px、1024px、768px、390px 视口是否出现横向溢出；
- 控制台是否有错误，页面是否缺少资源。

浏览器验收只证明页面和链接可访问，不把它包装成医疗效果评测。

### 6. GitHub Actions 与 Qoder Sites 发布

~~~powershell
git add README.md TIANCHI_SUBMISSION.md QUALITY_AUDIT_REPORT.md .github/workflows/ci.yml hospital-web-v2
git commit -m "feat: publish independent CareNexus contest work"
git push -u origin contest/carenexus-independent-2026

qoder sites prepare_site
qoder sites publish_site
qoder sites get_publish_status
~~~

发布前由 Qoder Sites 准备 hospital-web-v2/dist，发布后重新检查公开地址的 HTTP 状态和页面内容。GitHub Actions 负责持续执行后端测试、后端打包、普通前端构建和赛事前端构建。

## 六、验证结果与边界

本次提交的验收记录写入 QUALITY_AUDIT_REPORT.md。当前可确认的工程事实包括：前端赛事构建通过、普通前端构建通过、后端 Maven 测试通过、代码差异检查通过、公开页面可访问、四端入口和来源外链均能从页面找到。

以下事项不在本作品页中宣称已经完成：真实医院的临床验证、真实支付机构签名回调、生产 MySQL 迁移、TLS 证书、模型服务可用性和医疗指南覆盖率。真实上线仍需要医院信息科、临床人员、安全人员和运维人员共同验收。

CareNexus 展示的是一个能被代码、数据来源、状态机和发布记录逐层核验的医疗软件工程，而不是一张只展示回答效果的聊天页面。
