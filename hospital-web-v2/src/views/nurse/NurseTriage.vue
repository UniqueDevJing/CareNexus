<template>
  <section class="workspace nurse-space" aria-label="护士人工导诊工作区" :inert="submitting">
    <header class="work-header nurse-header">
      <div><span class="eyebrow">护士工作台 / 异常接管</span><h2>只把需要人判断的患者，交到你手里。</h2><p>护士不是重复阅读聊天记录，而是补齐改变就医路径的那一个关键信息。</p></div>
      <div class="work-header-actions"><div class="nurse-shift"><span class="status-pip"></span><div><strong>导诊服务在线</strong><small>队列每 30 秒刷新 · {{ actionableCount }} 条待处理</small></div></div><button class="secondary-button" @click="load">刷新队列</button></div>
    </header>

    <section class="metric-grid metric-grid--four nurse-metrics" aria-label="导诊队列概览">
      <article class="metric-tile"><span>待护士处理</span><strong>{{ actionableCount }}</strong><small>需要现在判断</small></article>
      <article class="metric-tile metric-tile--alert"><span>危急征象</span><strong>{{ redFlagCount }}</strong><small>不可确认普通预约</small></article>
      <article class="metric-tile"><span>等待患者补充</span><strong>{{ waitingPatientCount }}</strong><small>已发出关键追问</small></article>
      <article class="metric-tile metric-tile--quiet"><span>当前视图</span><strong>{{ filteredQueue.length }}</strong><small>{{ filterLabel }}</small></article>
    </section>

    <section class="nurse-layout">
      <aside class="nurse-queue surface">
        <header class="panel-head"><div><span class="eyebrow">人工队列</span><h3>需要人判断的患者</h3></div><span class="queue-count">{{ filteredQueue.length }}</span></header>
        <div class="queue-filter nurse-filter" role="tablist" aria-label="导诊状态筛选">
          <button :class="{ active: filter === 'all' }" @click="filter = 'all'">全部 <small>{{ queue.length }}</small></button>
          <button :class="{ active: filter === 'risk' }" @click="filter = 'risk'">危急 <small>{{ redFlagCount }}</small></button>
          <button :class="{ active: filter === 'review' }" @click="filter = 'review'">待处理 <small>{{ actionableCount - redFlagCount }}</small></button>
          <button :class="{ active: filter === 'waiting' }" @click="filter = 'waiting'">等补充 <small>{{ waitingPatientCount }}</small></button>
        </div>
        <div class="nurse-queue-list">
          <button v-for="item in filteredQueue" :key="item.suggestion_id" :class="['nurse-case-row', { active: selected?.suggestion_id === item.suggestion_id }]" @click="selectCase(item)">
            <span :class="['risk-mark', item.red_flag ? 'risk-mark--critical' : item.status === 'NEED_MORE_INFO' ? 'risk-mark--waiting' : 'risk-mark--review']">{{ item.red_flag ? '急' : item.status === 'NEED_MORE_INFO' ? '等' : '审' }}</span>
            <span><b>{{ item.patient_name || '未命名患者' }}</b><small>{{ item.queue_code }} · {{ statusLabel(item.status) }}</small></span>
            <span class="case-arrow" aria-hidden="true">→</span>
          </button>
          <div v-if="!filteredQueue.length" class="empty-state"><span class="empty-icon">✓</span><b>{{ queue.length ? '当前筛选下暂无记录' : '人工队列已清空' }}</b><p>{{ queue.length ? '切换筛选查看其他待处理项。' : '新的异常分诊会自动进入这里。' }}</p></div>
        </div>
      </aside>

      <article v-if="selected" class="nurse-case surface">
        <header class="case-header"><div class="case-person"><span class="care-avatar care-avatar--patient patient-avatar--large">{{ (selected.patient_name || '患').slice(0, 1) }}</span><div><span class="eyebrow">人工接管 / {{ selected.queue_code }}</span><h3>{{ selected.patient_name || '未命名患者' }}</h3><p>{{ selected.gender || '—' }} · {{ selected.age || '—' }} 岁 · {{ selected.department_name || '待匹配科室' }}</p></div></div><span :class="['risk-badge', selected.red_flag ? 'risk-badge--critical' : selected.status === 'NEED_MORE_INFO' ? 'risk-badge--waiting' : 'risk-badge--review']">{{ selected.red_flag ? '危急征象' : statusLabel(selected.status) }}</span></header>
        <div class="case-body">
          <section class="case-section case-section--primary"><span class="eyebrow">患者最新原话</span><p class="case-complaint">{{ selected.last_user_message || '患者未留下可识别的主诉，请通过人工渠道补充。' }}</p></section>
          <section v-if="selected.status === 'NEED_MORE_INFO'" class="nurse-question-card"><span class="eyebrow">已发出的关键追问</span><strong>{{ selected.manual_question || '等待患者补充关键信息' }}</strong><small>患者回复后，这条记录会自动回到“待处理”队列。</small></section>
          <div class="case-columns"><section class="case-section"><span class="eyebrow">系统建议</span><strong>{{ selected.department_name || '暂无科室建议' }}</strong><p>{{ selected.doctor_name || '暂无医生建议' }}</p></section><section class="case-section"><span class="eyebrow">证据置信度</span><strong>{{ confidence(selected.confidence) }}</strong><p>{{ selected.reason || '系统未提供额外说明' }}</p></section></div>
          <div class="case-alerts"><div><span>既往史</span><b>{{ selected.past_history || '暂无记录' }}</b></div><div :class="{ alert: selected.allergic_history }"><span>过敏史</span><b>{{ selected.allergic_history || '暂无记录' }}</b></div></div>
          <label v-if="selected.status === 'PENDING'" class="case-note"><span>{{ selected.red_flag ? '处理依据' : '人工处理 / 追问内容' }} <b>必填</b></span><textarea v-model="note" rows="4" :placeholder="selected.red_flag ? '记录风险判断，并说明转急诊或人工医生的依据……' : '如果信息不足，请写成患者能直接回答的具体问题；确认时记录你核对的依据……'"></textarea></label>
        </div>
        <footer class="case-actions">
          <span v-if="selected.status === 'NEED_MORE_INFO'">护士已经发出追问，当前不需要重复处理；患者回复后会自动重新入队。</span>
          <span v-else-if="selected.red_flag">危急征象不可直接确认普通预约，请转急诊或人工医生处理。</span>
          <span v-else>确认后患者可继续预约；信息不足时只追问会改变路径的关键问题。</span>
          <div v-if="selected.status === 'PENDING'"><button class="secondary-button" @click="resolve(selected.red_flag ? 'REJECT' : 'REQUEST_INFO')">{{ selected.red_flag ? '转急诊 / 人工医生' : '向患者追问' }}</button><button v-if="!selected.red_flag" class="primary-button" @click="resolve('CONFIRM')">确认分诊 <span aria-hidden="true">→</span></button></div>
          <div v-else><button class="secondary-button" @click="load">刷新患者状态</button></div>
        </footer>
      </article>
      <article v-else class="nurse-case surface nurse-case-empty"><div class="medical-cross" aria-hidden="true">+</div><span class="eyebrow">人工导诊台</span><h3>从左侧选择一条待处理记录</h3><p>患者原话、关键追问、风险提示和处理依据会在这里展开。</p></article>
    </section>
  </section>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api'

const queue = ref([])
const selected = ref(null)
const filter = ref('all')
const note = ref('')
const submitting = ref(false)
let refreshTimer

const actionableCount = computed(() => queue.value.filter(item => item.status === 'PENDING').length)
const redFlagCount = computed(() => queue.value.filter(item => Number(item.red_flag) === 1).length)
const waitingPatientCount = computed(() => queue.value.filter(item => item.status === 'NEED_MORE_INFO').length)
const filteredQueue = computed(() => {
  if (filter.value === 'risk') return queue.value.filter(item => Number(item.red_flag) === 1)
  if (filter.value === 'review') return queue.value.filter(item => item.status === 'PENDING' && Number(item.red_flag) !== 1)
  if (filter.value === 'waiting') return queue.value.filter(item => item.status === 'NEED_MORE_INFO')
  return queue.value
})
const filterLabel = computed(() => ({ all: '全部人工状态', risk: '危急征象', review: '待护士处理', waiting: '等待患者补充' }[filter.value]))

function statusLabel(status) { return ({ PENDING: '待护士处理', NEED_MORE_INFO: '等待患者补充', CONFIRMED: '已确认', REJECTED: '已转人工', SUPERSEDED: '已被新建议替代' }[status] || '待核对') }
function confidence(value) { const number = Number(value); return Number.isFinite(number) && number > 0 ? `${Math.round(number * 100)}%` : '未提供' }
function selectCase(item) { selected.value = item; note.value = '' }
async function load() {
  if (submitting.value) return
  try {
    queue.value = await api.get('/nurse/triage/queue')
    if (selected.value) selected.value = queue.value.find(item => item.suggestion_id === selected.value.suggestion_id) || null
  } catch (error) { ElMessage.error(error.response?.data?.msg || error.message || '导诊队列加载失败') }
}
async function resolve(action) {
  if (submitting.value) return
  if (!selected.value || !note.value.trim()) { ElMessage.warning(action === 'REQUEST_INFO' ? '请写清楚要患者补充的具体问题' : '请先填写处理依据'); return }
  submitting.value = true
  try {
    await api.post('/nurse/triage/resolve', null, { suggestionId: selected.value.suggestion_id, action, note: note.value.trim() })
    ElMessage.success(action === 'CONFIRM' ? '分诊已确认，患者可继续预约' : action === 'REQUEST_INFO' ? '追问已发出，等待患者补充' : '已转急诊 / 人工医生')
    selected.value = null; note.value = ''
  } catch (error) { ElMessage.error(error.response?.data?.msg || error.message || '处理失败，请稍后重试') }
  finally { submitting.value = false; await load() }
}
onMounted(() => { load(); refreshTimer = window.setInterval(load, 30000) })
onUnmounted(() => window.clearInterval(refreshTimer))
</script>
