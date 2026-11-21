<template>
  <div class="contest-page">
    <a class="contest-skip" href="#content">跳到主要内容</a>

    <div class="contest-notice" role="note">
      CareNexus 2026 Qoder 高校创造力大赛作品 · 页面只展示已提交代码、来源登记和可复核工程记录 · 不展示患者个人信息
    </div>

    <header class="contest-header">
      <div class="contest-wrap contest-header-inner">
        <a class="contest-brand" href="#top" aria-label="CareNexus 作品首页">
          <span class="contest-mark" aria-hidden="true"><i></i><i></i><i></i></span>
          <span><strong>CareNexus</strong><small>医院就诊安全工作台</small></span>
        </a>
        <nav class="contest-nav" aria-label="作品导航">
          <a href="#workflow">四端链路</a>
          <a href="#knowledge">知识来源</a>
          <a href="#process">开发过程</a>
          <a href="#verification">验证记录</a>
        </nav>
        <a class="contest-header-link" :href="githubUrl" target="_blank" rel="noopener noreferrer">查看代码</a>
      </div>
    </header>

    <main id="content" tabindex="-1">
      <section id="top" class="contest-hero" aria-labelledby="hero-title">
        <div class="contest-wrap contest-hero-grid">
          <div class="contest-hero-copy" data-enter>
            <p class="contest-kicker">面向真实门诊协作的医疗信息系统</p>
            <h1 id="hero-title">把一次预问诊，变成可核验的就诊流程。</h1>
            <p class="contest-lead">
              CareNexus 将患者主诉、护士导诊、医生接诊和运营审查串成一条可回放链路。模型只负责在工具和证据边界内提出建议，服务端与医务人员负责确认、止损和写入。
            </p>
            <div class="contest-actions">
              <a class="contest-button contest-button--primary" :href="productionUrl" target="_blank" rel="noopener noreferrer">进入真实工作台</a>
              <a class="contest-button contest-button--quiet" :href="githubUrl" target="_blank" rel="noopener noreferrer">查看 GitHub 代码</a>
            </div>
            <p class="contest-hero-note">公开作品不接收患者输入，不读取生产数据；页面中的事实均来自当前仓库、构建记录和来源登记。</p>
          </div>

          <div class="contest-console" aria-label="CareNexus 四端链路概览" data-enter>
            <div class="console-topline"><span class="console-dot"></span><span>CARE WORKFLOW / LIVE CONTRACT</span><b>安全会话</b></div>
            <div class="console-title"><span>当前链路</span><strong>信息 → 核验 → 决策 → 写入</strong></div>
            <ol class="console-flow">
              <li v-for="(step, index) in flowSteps" :key="step.route" :class="{ 'is-last': index === flowSteps.length - 1 }">
                <span class="console-step">0{{ index + 1 }}</span>
                <span class="console-flow-copy"><b>{{ step.name }}</b><small>{{ step.route }}</small></span>
                <span class="console-flow-state">{{ step.state }}</span>
              </li>
            </ol>
            <div class="console-footer"><span>异常状态自动进入人工队列</span><strong>MAX_AGENT_STEPS = 7</strong></div>
          </div>
        </div>
        <div class="contest-wrap contest-fact-strip" aria-label="当前仓库工程事实">
          <div><strong>4</strong><span>角色工作区</span><small>患者 / 护士 / 医生 / 管理</small></div>
          <div><strong>7</strong><span>来源登记</span><small>官方指南与机构页面</small></div>
          <div><strong>30</strong><span>来源摘要</span><small>写入知识片段并可追溯</small></div>
          <div><strong>7</strong><span>Agent 步数上限</span><small>超限进入止损路径</small></div>
        </div>
      </section>

      <section id="workflow" class="contest-section contest-section--paper" aria-labelledby="workflow-title">
        <div class="contest-wrap">
          <header class="contest-section-head">
            <div><p class="contest-kicker">01 / 业务链路</p><h2 id="workflow-title">四个工作区，合成一条就诊链路。</h2></div>
            <p>四端不是四个孤立页面：患者提交的信息进入风险判断，信息不足时由护士接管，医生完成临床确认，管理端维护规则、来源与审计。</p>
          </header>
          <div class="role-grid">
            <article v-for="role in roles" :key="role.route" class="role-card">
              <div class="role-card-top"><span class="role-index">{{ role.index }}</span><span class="role-tag">{{ role.tag }}</span></div>
              <h3>{{ role.name }}</h3>
              <p>{{ role.summary }}</p>
              <dl><div><dt>入口</dt><dd><code>{{ role.route }}</code></dd></div><div><dt>边界</dt><dd>{{ role.guardrail }}</dd></div></dl>
              <a :href="`${productionUrl}${role.route}`" target="_blank" rel="noopener noreferrer">打开工作区</a>
            </article>
          </div>
          <div class="workflow-rule"><span class="workflow-rule-mark">!</span><p><strong>信息不足不是“继续猜”。</strong> 低置信度、危险信号、模型服务异常和号源校验失败都会留下状态，转入护士人工导诊或医生确认，不会直接生成预约结果。</p></div>
        </div>
      </section>

      <section id="decision" class="contest-section contest-section--ink" aria-labelledby="decision-title">
        <div class="contest-wrap">
          <header class="contest-section-head contest-section-head--light">
            <div><p class="contest-kicker">02 / Agent 边界</p><h2 id="decision-title">Agent 选择路径，系统守住边界。</h2></div>
            <p>这套设计把“会调用模型”变成可审查的决策链：每一步有工具、状态、最大步数和失败后的去向。</p>
          </header>
          <div class="decision-layout">
            <ol class="decision-list">
              <li v-for="(item, index) in decisionSteps" :key="item.title"><span>{{ index + 1 }}</span><div><h3>{{ item.title }}</h3><p>{{ item.detail }}</p><code>{{ item.evidence }}</code></div></li>
            </ol>
            <aside class="state-panel" aria-label="真实系统状态">
              <div class="state-panel-head"><span>STATE MACHINE</span><b>可回放</b></div>
              <div class="state-row" v-for="state in states" :key="state.value"><code>{{ state.value }}</code><span>{{ state.label }}</span><small>{{ state.detail }}</small></div>
              <p class="state-note">最终建议标记为 <code>DECISION_SUPPORT_ONLY</code>。它不能替代诊断、检查、处方或急救调度。</p>
            </aside>
          </div>
        </div>
      </section>

      <section id="knowledge" class="contest-section contest-section--paper" aria-labelledby="knowledge-title">
        <div class="contest-wrap">
          <header class="contest-section-head">
            <div><p class="contest-kicker">03 / 证据链</p><h2 id="knowledge-title">知识库先审来源，再进入检索。</h2></div>
            <p>知识库保存的是来源登记、短摘要和引用信息，不收录受版权保护的全文。未核验上传资料默认不能参与临床决策支持。</p>
          </header>
          <div class="knowledge-layout">
            <div class="source-list">
              <article v-for="source in sources" :key="source.title" class="source-card">
                <div class="source-card-top"><span>{{ source.area }}</span><b>{{ source.status }}</b></div>
                <h3>{{ source.title }}</h3>
                <p>{{ source.organization }}</p>
                <dl><div><dt>版本</dt><dd>{{ source.version }}</dd></div><div><dt>范围</dt><dd>{{ source.population }}</dd></div></dl>
                <a :href="source.url" target="_blank" rel="noopener noreferrer">打开权威来源</a>
              </article>
            </div>
            <aside class="knowledge-contract">
              <div class="contract-label">KNOWLEDGE CONTRACT</div>
              <h3>每条检索结果都带着出处。</h3>
              <ul><li><b>来源状态</b><span><code>SOURCE_VERIFIED</code> 或 <code>CLINICIAN_APPROVED</code> 才能进入检索。</span></li><li><b>上传资料</b><span>新资料默认为 <code>PENDING_REVIEW</code>，不能绕过审核。</span></li><li><b>结果提示</b><span>返回来源机构、版本、链接、DOI、适用人群和安全提示。</span></li><li><b>无法确认</b><span>返回人工核验路径，不用生成无出处结论。</span></li></ul>
              <a class="text-link" href="https://github.com/UniqueDevJing/CareNexus/blob/contest/carenexus-independent-2026/MEDICAL_KNOWLEDGE_SOURCES.md" target="_blank" rel="noopener noreferrer">查看来源登记政策</a>
            </aside>
          </div>
        </div>
      </section>

      <section id="process" class="contest-section contest-section--warm" aria-labelledby="process-title">
        <div class="contest-wrap">
          <header class="contest-section-head">
            <div><p class="contest-kicker">04 / Qoder 开发过程</p><h2 id="process-title">从真实仓库开始，到真实发布结束。</h2></div>
            <p>下面的步骤对应实际文件、命令和发布动作。过程区只呈现可追溯的工程证据，展示页不使用患者数据。</p>
          </header>
          <div class="process-layout">
            <ol class="process-timeline">
              <li v-for="(step, index) in processSteps" :key="step.title"><span class="process-index">0{{ index + 1 }}</span><div><h3>{{ step.title }}</h3><p>{{ step.detail }}</p><code>{{ step.command }}</code></div></li>
            </ol>
            <div class="process-proof">
              <div class="proof-terminal" aria-label="真实开发环境证据"><div class="proof-terminal-bar"><i></i><i></i><i></i><span>Qoder / PowerShell</span></div><pre>{{ terminalText }}</pre></div>
              <p class="proof-note">开发证据来自真实 Qoder 工作环境中的终端记录；发布后的页面再通过浏览器检查链接、资源加载和响应式布局。</p>
              <a class="text-link" :href="githubActionsUrl" target="_blank" rel="noopener noreferrer">查看 GitHub Actions 记录</a>
            </div>
          </div>
        </div>
      </section>

      <section id="verification" class="contest-section contest-section--paper" aria-labelledby="verification-title">
        <div class="contest-wrap">
          <header class="contest-section-head">
            <div><p class="contest-kicker">05 / 可复核结果</p><h2 id="verification-title">评委打开页面，能查到每一层证据。</h2></div>
            <p>工程事实与临床效果分开写。当前公开作品可以证明代码、来源、构建和发布状态，不能替代真实医院上线验收或临床研究。</p>
          </header>
          <div class="verification-grid">
            <article v-for="item in verificationItems" :key="item.label" class="verification-card"><span>{{ item.label }}</span><strong>{{ item.value }}</strong><p>{{ item.detail }}</p><a :href="item.href" target="_blank" rel="noopener noreferrer">{{ item.action }}</a></article>
          </div>
          <div class="final-boundary"><div><span class="contest-kicker">发布边界</span><h3>公开作品展示系统如何工作，不冒充临床结论。</h3></div><p>公开页面不收集患者输入，不显示患者姓名、联系方式或病历。真正的就诊工作台通过登录、角色权限、数据库和服务端校验运行；知识库建议始终标记为决策支持，危险情况应立即线下就医。</p></div>
        </div>
      </section>
    </main>

    <footer class="contest-footer"><div class="contest-wrap contest-footer-inner"><div><strong>CareNexus</strong><p>面向医院真实工作流的就诊安全工作台。</p></div><div class="footer-links"><a :href="githubUrl" target="_blank" rel="noopener noreferrer">GitHub</a><a :href="productionUrl" target="_blank" rel="noopener noreferrer">真实工作台</a><a href="https://tianchi.aliyun.com/forum/post/1100237" target="_blank" rel="noopener noreferrer">赛事文章</a></div><small>来源链接归属各发布机构；知识库只保留来源登记、短摘要和引用信息。</small></div></footer>
  </div>
</template>

<script setup>
import './ContestShowcase.css'

const githubUrl = 'https://github.com/UniqueDevJing/CareNexus/tree/contest/carenexus-independent-2026'
const githubActionsUrl = 'https://github.com/UniqueDevJing/CareNexus/actions'
const productionUrl = 'http://114.215.186.113/CareNexus'

const flowSteps = [
  { name: '患者端', route: '/patient/pre', state: '收集主诉' },
  { name: '护士端', route: '/nurse/triage', state: '处理异常' },
  { name: '医生端', route: '/doctor/console', state: '临床确认' },
  { name: '管理端', route: '/admin/knowledge', state: '维护证据' },
]

const roles = [
  { index: '01', tag: 'PATIENT', name: '患者端', route: '/patient/pre', summary: '记录症状、补充健康档案，查看系统给出的就医建议和待人工处理状态。', guardrail: '只能查看自己的会话、预约和就诊记录。' },
  { index: '02', tag: 'NURSE', name: '护士端', route: '/nurse/triage', summary: '接收低置信度、信息不足或风险异常的任务，向患者补问或转人工医生。', guardrail: '只能处理人工导诊队列，不能修改管理配置。' },
  { index: '03', tag: 'DOCTOR', name: '医生端', route: '/doctor/console', summary: '查看候诊资料、历史就诊信息和结构化病历草稿，完成临床确认和提交。', guardrail: '患者归属、病历状态和提交锁定由服务端校验。' },
  { index: '04', tag: 'ADMIN', name: '管理端', route: '/admin/knowledge', summary: '登记医学来源、审查知识状态、观察 Agent 执行和维护医院基础数据。', guardrail: '来源未核验时不能进入知识检索和决策支持。' },
]

const decisionSteps = [
  { title: '先读主诉，再选工具', detail: 'ReAct 循环按照症状标签、科室、医生和号源顺序推进；模型不能跳过服务端工具直接写预约。', evidence: 'ReActTriageAgent.java / ToolCenter.java' },
  { title: '危险征象由代码硬拦截', detail: '红旗规则在模型循环前执行，命中后直接进入急诊人工边界，不把危险判断交给自由生成。', evidence: 'ReActTriageAgent.checkRedFlag()' },
  { title: '结果必须二次核验', detail: '科室存在性、医生归属、实际号源和建议状态重新校验；没有可用号源时降低确定性并提示人工处理。', evidence: 'ReActTriageAgent.buildVerified()' },
  { title: '异常会留下去向', detail: '循环超限、模型服务异常或证据不足时，建议进入 PENDING / NEED_MORE_INFO，护士队列接管，不静默编造结论。', evidence: 'application.yml / ManualReviewService.java' },
]

const states = [
  { value: 'PENDING', label: '待护士处理', detail: '需要人工核对后才能继续' },
  { value: 'NEED_MORE_INFO', label: '等待补充', detail: '向患者提出具体追问' },
  { value: 'CONFIRMED', label: '已确认', detail: '满足业务校验后才能进入下一步' },
  { value: 'REJECTED', label: '已转人工', detail: '风险或不确定性不允许在线继续' },
]

const sources = [
  { area: '呼吸', title: 'GINA Strategy Report', organization: 'Global Initiative for Asthma', version: '2026', population: '成人、青少年及儿童；按原文分层', status: 'SOURCE_VERIFIED', url: 'https://ginasthma.org/2026-gina-strategy-report/' },
  { area: '呼吸', title: 'GOLD Report and Pocket Guide', organization: 'Global Initiative for Chronic Obstructive Lung Disease', version: '2026', population: '成人 COPD；按原文分层', status: 'SOURCE_VERIFIED', url: 'https://goldcopd.org/2026-gold-report-and-pocket-guide/' },
  { area: '心血管', title: '中国高血压防治指南修订版', organization: '中国高血压防治指南修订委员会等', version: '2024 修订版 / DOI 10.16439/j.issn.1673-7245.2024.07.002', population: '中国成人高血压患者', status: 'SOURCE_VERIFIED', url: 'https://doi.org/10.16439/j.issn.1673-7245.2024.07.002' },
  { area: '急诊', title: '2021 AHA/ACC Chest Pain Guideline', organization: 'AHA/ACC Joint Committee', version: '2021', population: '急性胸痛成人患者', status: 'SOURCE_VERIFIED', url: 'https://professional.heart.org/en/guidelines-statements/2021-ahaaccasechestsaemscctscmr-guideline-for-the-evaluation-and-diagnosis-ofcir0000000000001029' },
  { area: '代谢', title: 'Standards of Care in Diabetes', organization: 'American Diabetes Association', version: '2026 / DOI 10.2337/dc26-S002', population: '糖尿病诊断与分型章节', status: 'SOURCE_VERIFIED', url: 'https://doi.org/10.2337/dc26-S002' },
  { area: '儿科', title: 'Fever in under 5s: assessment and initial management', organization: 'National Institute for Health and Care Excellence', version: 'NG143（2021 修订）', population: '5 岁以下儿童发热初筛', status: 'SOURCE_VERIFIED', url: 'https://www.nice.org.uk/guidance/ng143/chapter/Recommendations' },
  { area: '儿科', title: '儿童肺炎支原体肺炎诊疗指南', organization: '中华人民共和国国家卫生健康委员会等', version: '2025 年版', population: '儿童肺炎支原体肺炎', status: 'SOURCE_VERIFIED', url: 'https://www.nhc.gov.cn/yzygj/c100068/202509/19e7145436b049a68d4ba8c2060dfa56/files/%E5%84%BF%E7%AB%A5%E8%82%BA%E7%82%8E%E6%94%AF%E5%8E%9F%E4%BD%93%E8%82%BA%E7%82%8E%E8%AF%8A%E7%96%97%E6%8C%87%E5%8D%97%EF%BC%882025%E5%B9%B4%E7%89%88%EF%BC%89.pdf' },
]

const processSteps = [
  { title: '读取 GitHub 最新状态', detail: 'Qoder 先检查分支、路由、后端服务、知识库 schema 和当前发布入口，再从真实工程现状决定页面信息。', command: 'git fetch origin --prune && git log origin/refactor/production-workspace-refresh' },
  { title: '把业务边界写进页面', detail: '以患者、护士、医生、管理四端的真实职责为主线，移除固定病例和结果回放，页面只呈现已实现的流程。', command: 'rg "patient|nurse|doctor|admin|PENDING_REVIEW" src hospital-v2' },
  { title: '核对来源登记', detail: '从 data.sql、schema.sql 和 KnowledgeService 读取来源机构、版本、链接、审核状态和安全提示。', command: 'rg "source_organization|source_url|review_status" hospital-v2' },
  { title: '构建并检查', detail: '同时验证赛事根路径构建、普通工作台构建和后端测试；失败时停止发布，不用截图掩盖问题。', command: 'npm ci && npm run build:contest && npm run build && mvn -q test' },
  { title: '推送并公开验收', detail: '将提交推送到 GitHub，发布静态作品，再从公网重新检查页面入口、外链、资源和响应式布局。', command: 'git diff --check && git push origin contest/carenexus-independent-2026' },
]

const terminalText = `PS> git fetch origin --prune
PS> git log origin/refactor/production-workspace-refresh -1
52cb21e feat: add evidence-backed medical knowledge sources

PS> npm run build:contest
vite v7.3.8 building for contest...
✓ built successfully

PS> mvn -q test
✓ tests completed successfully

PS> git diff --check
✓ no whitespace errors`

const verificationItems = [
  { label: '赛事作品', value: '公开可访问', detail: '根路径进入作品页，静态资源不依赖登录和后端写入。', action: '打开作品', href: 'https://carenexus-safepath-yxmrb2k9vhk.qoder.website/' },
  { label: '真实工作台', value: '四端可进入', detail: '患者、护士、医生、管理端沿用真实部署入口和角色权限。', action: '打开系统', href: productionUrl },
  { label: 'GitHub', value: '源码可追踪', detail: '页面、路由、来源登记和构建配置都在仓库内。', action: '打开仓库', href: githubUrl },
  { label: '赛事文章', value: '过程可复核', detail: '文章记录作品目标、工程过程、证据来源和明确边界。', action: '打开文章', href: 'https://tianchi.aliyun.com/forum/post/1100237' },
]
</script>
