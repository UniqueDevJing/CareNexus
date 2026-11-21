# 【CareNexus】把一次预问诊变成可核验的就诊流程｜2026 Qoder 高校创造力大赛

## 体验地址

- 公开作品页：<https://carenexus-clinical-workflow-yxmrb2k9vhk.qoder.website/>
- GitHub 工程：<https://github.com/UniqueDevJing/CareNexus/tree/contest/carenexus-independent-2026>
- 真实工作台：<http://114.215.186.113/CareNexus/>
- 当前文章正文：<https://github.com/UniqueDevJing/CareNexus/blob/contest/carenexus-independent-2026/TIANCHI_SUBMISSION.md>

## 一句话说明

CareNexus 是一个面向医院就诊流程的安全工作台：它把患者信息收集、护士人工接管、医生确认、医学来源和管理审计连接起来，让 Agent 在证据不足时停下来，把不确定任务交给合适的人，而不是直接生成一个看起来完整的答案。

## 当前版本

- Git 分支：contest/carenexus-independent-2026
- 当前发布分支以 GitHub 页面显示的最新提交为准
- 工作区角色：患者端、护士端、医生端、管理端
- 知识来源登记：7 条
- 来源摘要：30 条
- Agent 单次最大执行步数：7
- 公开页面状态：Qoder Sites 已发布，访问策略为 public

上面的数字都是当前工程中的可核验事实，不代表用户规模、诊断准确率或临床效果。公开作品页也不接收患者输入、不读取生产数据库，真实工作台仍需要登录和服务端权限。

## 自我介绍

我负责 CareNexus 的业务梳理、前后端实现、医学来源登记、页面重构、测试验收和公开部署。这个作品的重点不是把一个聊天窗口做得更像聊天窗口，而是把一次不完整的患者描述，经过信息补齐、风险分流、人工接管和医生确认，变成一条能够回放和追责的工作流。

## 一、作品是什么

医院预问诊最容易被误解成“患者问一句，系统答一句”。真实业务中，系统还必须回答几个更重要的问题：患者说得不够清楚时谁来补问？风险异常时谁来接管？没有可靠医学证据时能不能停止自动推进？预约写入前如何确认科室、医生和号源仍然有效？医生接手时能否看到完整上下文？

CareNexus 的公开作品页将这条链路拆成四个角色：

1. 患者提交主诉、补充健康档案，查看分流建议、预约状态和后续处理状态。
2. 护士接收信息不足、风险异常、工具失败和模型未收敛的任务，进行针对性补问、转交或结束在线流程。
3. 医生查看结构化候诊资料和历史上下文，完成临床确认，并决定是否提交后续处理。
4. 管理人员维护科室、医生、排班、工具、模型配置、医学来源、知识片段和执行记录。

这四个角色不是四个互不相干的页面，而是一条有状态流转的业务链：患者提供信息，Agent 组织路径，护士处理不确定性，医生完成临床确认，管理端维护规则、证据和审计。

## 二、为什么做

我在梳理医院就诊流程时发现，单纯增加一个大模型入口并不能解决核心问题：

- 患者的自然语言描述通常缺少时间、程度、既往史和危险信号；
- 科室、医生和号源是实时业务数据，不能由模型自由生成；
- 知识库没有证据时，系统需要明确停下，而不是用语言把不确定性遮住；
- 医护人员需要的是可读、可追踪的上下文，不是几段无法回放的模型输出；
- 管理人员需要知道规则、来源、版本和执行过程发生了什么。

因此，CareNexus 把产品目标定义为“减少无效往返，缩短人工判断前的准备时间，同时保留人工确认权”，而不是“让模型替医生下结论”。

## 三、怎么体验

### 1. 先看公开作品页

打开公开作品地址后，首页按以下顺序展示：

- 一句话说明产品解决的业务问题；
- 患者、护士、医生、管理四端如何接力；
- Agent 可以做什么、不能做什么；
- 医学来源如何登记和进入检索；
- 在 Qoder 环境中如何开发、测试和发布；
- GitHub 工程、真实工作台和文章正文入口。

这条阅读路径面向评审和产品使用者，不要求先登录，也不会让访问者输入患者信息。

### 2. 再看四端的真实工作路径

如果需要了解业务系统本身，可以打开真实工作台。患者端负责提交和查看自己的就诊信息；护士端查看待接管任务并补问；医生端完成专业确认；管理端维护基础数据和来源审查。每个角色的权限由服务端校验，页面上的按钮不能单独改变角色边界。

### 3. 最后查看工程证据

公开页中的“知识来源”可以打开机构原始页面或 DOI；“开发过程”对应仓库中的构建脚本和 CI；“验证结果”对应本地构建、Maven 测试、浏览器验收和 Qoder Sites 发布状态。这样看作品时，先理解业务，再检查代码和结果，不需要把一张静态展示图当成系统能力。

## 四、四端如何协同

### 患者端：把主诉变成可补齐的信息

患者可以提供症状、持续时间、严重程度、既往情况和就诊偏好。系统只收集完成当前分流所需的信息，并把尚未确认的字段标成待补充，而不是假装已经知道答案。患者只能访问自己的会话、预约和就诊记录。

### 护士端：处理系统不能安全决定的任务

护士端是人工接管台，核心不是多一个角色名称，而是给不确定性一个明确去处。护士可以看到触发原因、已经收集的信息、缺失字段、工具返回结果和建议补问；完成补问后，可以继续分流、转交医生或结束在线流程。护士不能修改医学来源和系统级规则。

### 医生端：完成临床确认

医生端展示候诊资料、历史就诊信息、结构化病历草稿和来源线索。医生可以修改、确认或拒绝系统整理的内容；最终提交前，服务端会重新检查患者归属、记录状态、医生权限和必要字段。

### 管理端：维护规则和可追溯性

管理端维护科室、医生、排班、工具、模型配置、提示词、医学来源和执行记录。它负责让系统可运营、可审计，但不把管理人员的配置操作包装成临床诊断，也不能绕过医生确认直接完成医疗结论。

## 五、Agent 如何做决定

CareNexus 中的 Agent 不是一个无限制的自动执行器。它在受限工具链中工作，主要过程是：

1. 读取患者当前主诉和已经确认的信息；
2. 检查是否存在需要确定性规则优先拦截的红旗征象；
3. 判断缺少哪些信息，并生成最小必要的补问；
4. 查询症状标签、科室、医生和号源等受控工具；
5. 根据工具结果决定继续补问、进入人工接管，或形成待确认建议；
6. 对科室存在性、医生归属和号源状态做二次核对；
7. 超过最大执行步数、证据不足或模型异常时进入止损路径。

这里的关键不是让 Agent 多执行几步，而是让每一步都有状态、有边界、有失败去向：

- 高风险信号在 Agent 循环前由确定性规则处理；
- Agent 不能绕过服务端直接写入预约；
- 信息不足、证据不足、工具失败和模型未收敛会进入 PENDING 或 NEED_MORE_INFO；
- 护士可以接管，医生可以确认，失败流程可以终止或回滚；
- application.yml 将单次最大执行步数限制为 7，防止循环消耗和无止境重试。

对应实现可以在 ReActTriageAgent.java、ManualReviewService.java、KnowledgeService.java 和 ToolCenter.java 中核验。

## 六、知识库和业务流程是什么关系

知识库不是独立存在的文章展示区，也不是把网页全文直接交给模型。它在业务流程中承担“证据约束”的角色：

- 患者信息决定需要关注的症状、风险和就诊路径；
- 检索服务只返回处于 SOURCE_VERIFIED 或 CLINICIAN_APPROVED 状态的内容；
- 如果来源不存在、版本过期、审核未完成或证据不能支持当前问题，流程进入待核验或人工接管；
- 医生看到的是结构化上下文、来源线索和待确认项，而不是不可追溯的自由生成答案。

当前登记的 7 条来源为：

1. Global Initiative for Asthma，GINA Strategy Report 2026：<https://ginasthma.org/2026-gina-strategy-report/>
2. Global Initiative for Chronic Obstructive Lung Disease，GOLD Report and Pocket Guide 2026：<https://goldcopd.org/2026-gold-report-and-pocket-guide/>
3. 中国高血压防治指南 2024 修订版，DOI：<https://doi.org/10.16439/j.issn.1673-7245.2024.07.002>
4. 2021 AHA/ACC Chest Pain Guideline：<https://professional.heart.org/en/guidelines-statements/2021-ahaaccasechestsaemscctscmr-guideline-for-the-evaluation-and-diagnosis-ofcir0000000000001029>
5. American Diabetes Association，Standards of Care in Diabetes 2026，DOI：<https://doi.org/10.2337/dc26-S002>
6. NICE，Fever in under 5s: assessment and initial management，NG143：<https://www.nice.org.uk/guidance/ng143/chapter/Recommendations>
7. 国家卫生健康委员会等，儿童肺炎支原体肺炎诊疗指南（2025 年版）：<https://www.nhc.gov.cn/yzygj/c100068/202509/19e7145436b049a68d4ba8c2060dfa56/files/%E5%84%BF%E7%AB%A5%E8%82%BA%E7%82%8E%E6%94%AF%E5%8E%9F%E4%BD%93%E8%82%BA%E7%82%8E%E7%82%8E%E8%AF%8A%E7%96%97%E6%8C%87%E5%8D%97%EF%BC%882025%E5%B9%B4%E7%89%88%EF%BC%89.pdf>

系统保存来源机构、版本、官方链接、短摘要、审核状态和知识片段，不收录受版权保护的指南全文。页面展示的 7 条来源和 30 个摘要可以在 data.sql、MEDICAL_KNOWLEDGE_SOURCES.md 和知识服务实现中逐项核验。

## 七、我用 Qoder 做了什么

### 先审查，再动手

我先从生产分支获取最新状态，确认现有角色路由、Agent 服务、人工导诊服务、知识库表结构和部署入口：

~~~powershell
git fetch origin --prune
git status --short
git log origin/refactor/production-workspace-refresh -1 --oneline
~~~

接着使用搜索定位业务事实，而不是从页面文案反推功能：

~~~powershell
rg -n "patient|nurse|doctor|admin|PENDING|NEED_MORE_INFO|CONFIRMED" hospital-web-v2 hospital-v2
rg -n "source_organization|source_url|review_status" hospital-v2
~~~

### 把真实业务改成一条阅读路径

原有工作台和赛事展示的目标不同：真实工作台负责登录后的业务操作，赛事页负责把产品价值、角色协同、Agent 边界、知识来源和工程证据讲清楚。因此我新增了 /contest 路由和 contest 构建模式，保留 /CareNexus/ 的真实工作台路径，不让公开阅读页调用患者接口或写入业务数据库。

### 页面和内容一起质检

页面重构完成后，我用构建和浏览器检查以下内容：

- 赛事根路径能否进入 /contest；
- 文章锚点、四端说明、来源外链和工程入口是否完整；
- 1440、1024、768、390 像素视口是否出现横向溢出；
- 控制台是否有错误，静态资源是否正确加载；
- 公开页面是否意外出现患者输入、生产数据或旧入口。

### Qoder 负责发布，工程验收仍由代码和测试完成

Qoder 在本次工程中承担 Windows 开发环境、浏览器验收工具和 Sites 发布入口；产品边界、页面内容、代码修改、测试判断和风险说明由我逐项确认。发布命令为：

~~~powershell
cd hospital-web-v2
npm ci
npm run build
npm run build:contest

cd ..\hospital-v2
mvn -q test

cd ..
git diff --check
~~~

普通工作台构建完成后，必须最后执行 npm run build:contest，确保 dist 使用根路径资源，再通过 Qoder Sites 准备和发布赛事产物，并重新检查公开地址：

~~~powershell
qoder sites prepare_site
qoder sites publish_site
qoder sites get_publish_status
~~~

最后将代码推送到 GitHub 的 contest/carenexus-independent-2026 分支，由 GitHub Actions 重新执行后端测试、后端打包、普通前端构建和赛事前端构建。

## 八、验证结果

本轮真实验证结果如下：

| 验证项 | 结果 |
| --- | --- |
| 赛事前端构建 | 通过，1699 modules，4.31 s |
| 普通前端构建 | 通过，1699 modules，4.84 s |
| Maven 测试 | 通过，进程退出码 0 |
| 浏览器验收 | 1440、1024、768、390 四个视口横向溢出均为 0，标题正确，控制台错误 0 |
| Qoder Sites | publication_completed，访问策略 public |
| 公网入口 | HTTP 200，Content-Type 为 text/html |
| GitHub | contest/carenexus-independent-2026 分支已推送，提交 265f33a |

完整记录见 QUALITY_AUDIT_REPORT.md。上述结果只证明工程构建、页面访问和当前边界检查通过，不等同于临床试验或医疗器械合规认证。

## 九、边界和后续计划

CareNexus 当前展示的是一套可核验的医院就诊软件工程，不能替代医生诊断，也不能把来源登记当成医学结论。真实医院上线前仍需要：

- 医院信息科确认身份、权限、日志和数据保留策略；
- 临床人员审查风险规则、补问内容和人工接管标准；
- 安全人员审查隐私、接口、密钥、审计和传输加密；
- 运维人员完成生产 MySQL、模型服务、HTTPS、监控和备份验收；
- 持续维护来源版本、审核记录和失效链接。

公开赛事页不接收患者资料，也不宣称已经完成上述生产验收。它要表达的创新点是：把 Agent 从“回答问题的入口”变成“在证据、状态和人工责任边界内推进就诊流程的协作节点”。
