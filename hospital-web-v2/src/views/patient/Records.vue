<template>
  <section class="workspace records-workspace" aria-label="就诊记录工作区">
    <section class="page-intro records-intro">
      <div>
        <span class="eyebrow">MY CARE JOURNEY</span>
        <h2>我的就诊记录</h2>
        <p>把预约、接诊和病历放在同一条时间线上，每一条记录都可以展开查看。</p>
      </div>
      <button class="secondary-button" type="button" :disabled="loading" @click="load">
        {{ loading ? '正在同步' : '刷新记录' }}
      </button>
    </section>

    <section class="records-overview" aria-label="就诊记录概览">
      <div class="overview-primary">
        <span class="overview-kicker">CARE PATH</span>
        <strong>你的就诊轨迹</strong>
        <p>从预约到医生提交病历，状态和资料都会保留在这里。</p>
      </div>
      <div class="overview-stat"><strong>{{ regs.length }}</strong><span>预约</span></div>
      <div class="overview-stat"><strong>{{ recs.length }}</strong><span>电子病历</span></div>
      <div class="overview-stat overview-stat--accent"><strong>{{ archivedCount }}</strong><span>已归档</span></div>
    </section>

    <section class="records-layout">
      <div class="timeline-panel surface">
        <header class="panel-head records-panel-head">
          <div>
            <span class="eyebrow">TIMELINE</span>
            <h3>就诊时间线</h3>
          </div>
          <span class="panel-note">{{ timelineItems.length }} 条记录</span>
        </header>

        <div v-if="loading" class="records-state" role="status">正在读取你的就诊资料…</div>
        <div v-else-if="error" class="records-state records-state--error" role="alert">
          <strong>记录暂时无法加载</strong>
          <span>{{ error }}</span>
          <button class="text-button" type="button" @click="load">重新加载</button>
        </div>
        <div v-else-if="!timelineItems.length" class="records-state">
          <span class="records-state-mark" aria-hidden="true">＋</span>
          <strong>还没有就诊记录</strong>
          <span>完成首次预问诊与预约后，记录会出现在这里。</span>
        </div>
        <div v-else class="timeline-list">
          <article v-for="item in timelineItems" :key="item.key" class="timeline-item">
            <span class="timeline-marker" :class="`timeline-marker--${item.kind}`" aria-hidden="true">
              {{ item.kind === 'record' ? '病' : '约' }}
            </span>
            <div class="timeline-date">
              <strong>{{ item.date }}</strong>
              <span>{{ item.period }}</span>
            </div>
            <button
              class="timeline-card"
              :class="{ 'timeline-card--active': isSelected(item) }"
              type="button"
              :aria-pressed="isSelected(item)"
              @click="selectItem(item)"
            >
              <span class="timeline-card-top">
                <span class="timeline-type">{{ item.kind === 'record' ? '电子病历' : '预约就诊' }}</span>
                <span class="queue-status" :class="item.statusTone">{{ item.statusLabel }}</span>
              </span>
              <strong class="timeline-title">{{ item.title }}</strong>
              <span class="timeline-summary">{{ item.summary }}</span>
              <span class="timeline-card-footer">
                {{ item.doctorLabel }}
                <span class="timeline-open">{{ isSelected(item) ? '已展开' : '查看详情 →' }}</span>
              </span>
            </button>
          </article>
        </div>
      </div>

      <aside ref="detailPanel" class="record-detail surface" aria-live="polite">
        <template v-if="selected">
          <header class="detail-header">
            <div>
              <span class="eyebrow">{{ selected.kind === 'record' ? 'MEDICAL RECORD' : 'APPOINTMENT' }}</span>
              <h3>{{ selected.kind === 'record' ? (activeRecord.diagnosis || '本次接诊病历') : '预约就诊详情' }}</h3>
            </div>
            <span class="queue-status" :class="selectedStatusTone">{{ selectedStatusLabel }}</span>
          </header>

          <div class="detail-meta">
            <span>{{ selectedDate }}</span>
            <span>{{ selectedPeriod }}</span>
            <span>{{ selectedDoctor }}</span>
          </div>

          <div v-if="detailLoading" class="detail-loading">正在打开完整记录…</div>
          <div v-else-if="detailError" class="detail-loading detail-loading--error">
            {{ detailError }}
            <button class="text-button" type="button" @click="reloadSelected">重试</button>
          </div>

          <template v-else-if="selected.kind === 'registration'">
            <section class="detail-section">
              <div class="detail-section-title"><span>01</span><strong>预约信息</strong></div>
              <dl class="detail-grid">
                <div><dt>就诊科室</dt><dd>{{ selected.data.department_name || `科室 ${selected.data.department_id}` }}</dd></div>
                <div><dt>接诊医生</dt><dd>{{ selectedDoctor }}</dd></div>
                <div><dt>预约日期</dt><dd>{{ selected.data.work_date || '—' }}</dd></div>
                <div><dt>就诊时段</dt><dd>{{ selectedPeriod }}</dd></div>
                <div><dt>挂号状态</dt><dd>{{ selectedStatusLabel }}</dd></div>
                <div><dt>费用状态</dt><dd>{{ paymentLabel(selected.data.payment_status) }}</dd></div>
              </dl>
            </section>
            <section v-if="activeRecord.id" class="linked-record">
              <div>
                <span class="eyebrow">LINKED RECORD</span>
                <strong>{{ activeRecord.diagnosis || '医生已提交病历' }}</strong>
                <p>本次预约已经形成结构化病历，可继续查看七段内容。</p>
              </div>
              <button class="secondary-button" type="button" @click="selectRecord(activeRecord)">查看病历</button>
            </section>
            <div v-else class="detail-empty">医生完成接诊后，结构化病历会出现在这里。</div>
          </template>

          <template v-else>
            <section class="detail-section detail-section--record">
              <div class="detail-section-title"><span>01</span><strong>本次接诊</strong></div>
              <p class="detail-lead">{{ activeRecord.chief_complaint || '暂无主诉记录' }}</p>
            </section>
            <section class="detail-section" aria-label="病历七段内容">
              <div v-for="segment in SEGMENTS" :key="segment.key" class="record-segment">
                <div class="segment-heading"><span>{{ segment.no }}</span><strong>{{ segment.label }}</strong></div>
                <p>{{ activeRecord[segment.key] || '暂无记录' }}</p>
              </div>
            </section>
          </template>
        </template>
        <div v-else class="detail-placeholder">
          <span class="detail-placeholder-mark" aria-hidden="true">＋</span>
          <span class="eyebrow">RECORD DETAIL</span>
          <h3>选择一条记录</h3>
          <p>点击左侧预约或病历，查看完整的就诊信息、状态和医生记录。</p>
        </div>
      </aside>
    </section>
  </section>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api'

const SEGMENTS = [
  { key: 'chief_complaint', no: '02', label: '主诉' },
  { key: 'present_illness', no: '03', label: '现病史' },
  { key: 'past_history', no: '04', label: '既往史' },
  { key: 'allergy_history', no: '05', label: '过敏史' },
  { key: 'assistant_exam', no: '06', label: '辅助检查' },
  { key: 'diagnosis', no: '07', label: '诊断' },
  { key: 'treatment', no: '08', label: '处理意见' }
]

const regs = ref([])
const recs = ref([])
const loading = ref(false)
const error = ref('')
const selected = ref(null)
const detail = ref(null)
const detailLoading = ref(false)
const detailError = ref('')
const detailPanel = ref(null)
let requestSequence = 0

const archivedCount = computed(() => recs.value.filter(row => row.status === 'SUBMITTED').length)
const timelineItems = computed(() => [
  ...regs.value.map(row => ({
    key: `registration-${row.id}`,
    kind: 'registration',
    data: row,
    date: row.work_date || '待定日期',
    period: periodLabel(row.period),
    title: row.department_name || `科室 ${row.department_id}`,
    summary: `${row.doctor_name || `医生 ${row.doctor_id || '待定'}`} · ${paymentLabel(row.payment_status)}`,
    doctorLabel: row.doctor_name ? `${row.doctor_name} 医生` : `医生 ${row.doctor_id || '待定'}`,
    statusLabel: registrationStatus(row.status),
    statusTone: row.status === 'DONE' ? 'done' : 'waiting'
  })),
  ...recs.value.map(row => ({
    key: `record-${row.id}`,
    kind: 'record',
    data: row,
    date: row.work_date || '接诊记录',
    period: periodLabel(row.period),
    title: row.diagnosis || '医生接诊记录',
    summary: row.chief_complaint || '暂无主诉记录',
    doctorLabel: row.doctor_name ? `${row.doctor_name} 医生` : `医生 ${row.doctor_id || '待定'}`,
    statusLabel: row.status === 'SUBMITTED' ? '已归档' : '书写中',
    statusTone: row.status === 'SUBMITTED' ? 'done' : 'waiting'
  }))
].sort((a, b) => `${b.date}-${b.kind}`.localeCompare(`${a.date}-${a.kind}`)))

const activeRecord = computed(() => detail.value || (selected.value?.kind === 'record' ? selected.value.data : findLinkedRecord(selected.value?.data)) || {})
const selectedStatusLabel = computed(() => selected.value?.kind === 'record'
  ? (activeRecord.value.status === 'SUBMITTED' ? '已归档' : '书写中')
  : registrationStatus(selected.value?.data?.status))
const selectedStatusTone = computed(() => selectedStatusLabel.value === '已归档' || selectedStatusLabel.value === '已完成' ? 'done' : 'waiting')
const selectedDate = computed(() => selected.value?.data?.work_date || '日期待定')
const selectedPeriod = computed(() => periodLabel(selected.value?.data?.period))
const selectedDoctor = computed(() => selected.value?.data?.doctor_name || `医生 ${selected.value?.data?.doctor_id || '待定'}`)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [registrationRows, recordRows] = await Promise.all([
      api.get('/patient/registrations'),
      api.get('/patient/records')
    ])
    regs.value = Array.isArray(registrationRows) ? registrationRows : []
    recs.value = Array.isArray(recordRows) ? recordRows : []
    if (selected.value) {
      const next = timelineItems.value.find(item => item.key === selected.value.key)
      if (next) selected.value = next
    }
  } catch (e) {
    error.value = e.response?.data?.msg || e.message || '请稍后重试'
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}

async function selectItem(item) {
  if (item.kind === 'record') return selectRecord(item.data, item)
  selected.value = item
  detail.value = null
  detailError.value = ''
  const linked = findLinkedRecord(item.data)
  if (linked) await fetchDetail(linked.id)
  await focusDetail()
}

async function selectRecord(row, item = null) {
  selected.value = item || timelineItems.value.find(entry => entry.kind === 'record' && entry.data.id === row.id)
  detail.value = null
  detailError.value = ''
  await fetchDetail(row.id)
  await focusDetail()
}

async function fetchDetail(recordId) {
  const sequence = ++requestSequence
  detailLoading.value = true
  try {
    const result = await api.get('/patient/record/detail', { recordId })
    if (sequence === requestSequence) detail.value = result
  } catch (e) {
    if (sequence === requestSequence) detailError.value = e.response?.data?.msg || e.message || '病历详情加载失败'
  } finally {
    if (sequence === requestSequence) detailLoading.value = false
  }
}

async function reloadSelected() {
  if (!selected.value) return
  const linked = selected.value.kind === 'record' ? selected.value.data : findLinkedRecord(selected.value.data)
  if (linked) await fetchDetail(linked.id)
}

function findLinkedRecord(registration) {
  if (!registration) return null
  return recs.value.find(record => String(record.registration_id) === String(registration.id)) || null
}

function isSelected(item) { return selected.value?.key === item.key }
function registrationStatus(status) { return status === 'DONE' ? '已完成' : status === 'CANCEL' ? '已取消' : '待就诊' }
function paymentLabel(status) { return status === 'PAID' ? '费用已支付' : status === 'REFUNDED' ? '已退款' : '待支付' }
function periodLabel(period) { return period === 'AM' ? '上午' : period === 'PM' ? '下午' : period || '时段待定' }
async function focusDetail() {
  await nextTick()
  if (window.innerWidth < 900) detailPanel.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

onMounted(load)
</script>

<style scoped>
.records-workspace { gap: 24px; }
.records-intro { align-items: center; }
.records-intro h2 { margin-bottom: 8px; }
.records-overview { display: grid; grid-template-columns: minmax(0, 1.7fr) repeat(3, minmax(120px, .55fr)); gap: 0; overflow: hidden; border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--surface); box-shadow: var(--shadow); }
.overview-primary, .overview-stat { min-height: 108px; padding: 20px 22px; }
.overview-primary { border-right: 1px solid var(--line); }
.overview-kicker { display: block; color: var(--teal-dark); font: 600 10px/1.2 var(--mono); letter-spacing: .12em; }
.overview-primary strong { display: block; margin-top: 9px; font: 500 22px/1.2 var(--display); letter-spacing: -.035em; }
.overview-primary p { margin: 7px 0 0; color: var(--ink-soft); font-size: 12px; }
.overview-stat { display: flex; flex-direction: column; justify-content: center; border-right: 1px solid var(--line); }
.overview-stat:last-child { border-right: 0; }
.overview-stat strong { color: var(--ink); font: 500 30px/1 var(--mono); font-variant-numeric: tabular-nums; }
.overview-stat span { margin-top: 9px; color: var(--ink-soft); font-size: 11px; }
.overview-stat--accent strong { color: var(--teal-dark); }
.records-layout { display: grid; grid-template-columns: minmax(0, 1.08fr) minmax(360px, .92fr); gap: 20px; align-items: start; }
.timeline-panel, .record-detail { min-width: 0; padding: 22px; }
.timeline-panel { min-height: 620px; }
.records-panel-head { margin-bottom: 14px; }
.records-panel-head h3 { margin-top: 5px; }
.timeline-list { position: relative; display: grid; gap: 2px; padding: 8px 0 4px 3px; }
.timeline-list::before { position: absolute; top: 14px; bottom: 16px; left: 15px; width: 1px; content: ''; background: var(--line); }
.timeline-item { position: relative; display: grid; grid-template-columns: 27px 86px minmax(0, 1fr); gap: 12px; align-items: start; padding: 8px 0; }
.timeline-marker { z-index: 1; display: grid; width: 27px; height: 27px; place-items: center; color: var(--teal-dark); border: 4px solid var(--surface); border-radius: 50%; background: var(--sage); font: 600 9px/1 var(--mono); }
.timeline-marker--record { color: #805e2e; background: #f1e4c4; }
.timeline-date { padding-top: 4px; }
.timeline-date strong, .timeline-date span { display: block; }
.timeline-date strong { color: var(--ink); font: 500 11px/1.2 var(--mono); font-variant-numeric: tabular-nums; }
.timeline-date span { margin-top: 5px; color: var(--ink-soft); font-size: 10px; }
.timeline-card { display: grid; min-width: 0; gap: 8px; padding: 14px 16px; color: var(--ink); border: 1px solid var(--line); border-radius: 10px; outline: 0; background: var(--surface-muted); text-align: left; cursor: pointer; transition: border-color .16s ease, background .16s ease, transform .16s ease; }
.timeline-card:hover { border-color: var(--sage-strong); background: #fbfcf8; transform: translateY(-1px); }
.timeline-card:focus-visible { outline: 2px solid var(--gold); outline-offset: 2px; }
.timeline-card--active { border-color: var(--teal); background: #f1f7f3; box-shadow: inset 3px 0 var(--teal); }
.timeline-card-top, .timeline-card-footer { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.timeline-type { color: var(--teal-dark); font: 600 10px/1.2 var(--mono); letter-spacing: .08em; }
.queue-status { display: inline-flex; min-height: 22px; align-items: center; padding: 0 8px; border-radius: 12px; color: #93634c; background: #f6e9df; font-size: 10px; white-space: nowrap; }
.queue-status.done { color: var(--teal-dark); background: var(--sage); }
.timeline-title { overflow: hidden; color: var(--ink); font-size: 16px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.timeline-summary { overflow: hidden; color: var(--ink-soft); font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.timeline-card-footer { padding-top: 8px; color: var(--ink-soft); border-top: 1px solid var(--line); font-size: 11px; }
.timeline-open { color: var(--teal-dark); font-weight: 600; white-space: nowrap; }
.records-state { display: grid; min-height: 360px; place-content: center; justify-items: center; gap: 7px; color: var(--ink-soft); text-align: center; }
.records-state strong { color: var(--ink); font-size: 14px; }
.records-state span { font-size: 12px; }
.records-state-mark, .detail-placeholder-mark { display: grid; width: 44px; height: 44px; margin-bottom: 5px; place-items: center; color: var(--teal-dark); border: 1px solid var(--sage-strong); border-radius: 50%; background: var(--sage); font-size: 26px; }
.records-state--error strong { color: var(--rust); }
.record-detail { position: sticky; top: 112px; min-height: 620px; }
.detail-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 15px; padding-bottom: 16px; border-bottom: 1px solid var(--line); }
.detail-header h3 { max-width: 350px; margin: 7px 0 0; color: var(--ink); font: 500 24px/1.25 var(--display); letter-spacing: -.035em; }
.detail-meta { display: flex; flex-wrap: wrap; gap: 8px 18px; padding: 14px 0 17px; color: var(--ink-soft); font-size: 11px; }
.detail-meta span { position: relative; }
.detail-meta span + span::before { position: absolute; top: 50%; left: -11px; width: 3px; height: 3px; content: ''; border-radius: 50%; background: var(--gold); transform: translateY(-50%); }
.detail-section { padding: 17px 0; border-top: 1px solid var(--line); }
.detail-section-title { display: flex; align-items: center; gap: 9px; margin-bottom: 13px; }
.detail-section-title span, .segment-heading span { color: var(--teal); font: 600 10px/1 var(--mono); }
.detail-section-title strong { color: var(--ink); font-size: 13px; }
.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px 18px; margin: 0; }
.detail-grid div { min-width: 0; }
.detail-grid dt { color: var(--ink-soft); font-size: 10px; }
.detail-grid dd { margin: 4px 0 0; color: var(--ink); font-size: 13px; }
.linked-record { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin-top: 4px; padding: 14px; border: 1px solid #d4e1d7; border-radius: 9px; background: #f4f8f2; }
.linked-record strong { display: block; margin-top: 5px; color: var(--ink); font-size: 14px; }
.linked-record p { margin: 5px 0 0; color: var(--ink-soft); font-size: 11px; line-height: 1.6; }
.detail-empty, .detail-loading { padding: 18px 0; color: var(--ink-soft); font-size: 12px; line-height: 1.7; }
.detail-loading--error { color: var(--rust); }
.detail-lead { margin: 0; padding: 14px 15px; color: var(--ink); border-left: 3px solid var(--teal); background: var(--surface-muted); font-size: 14px; line-height: 1.75; }
.record-segment { display: grid; grid-template-columns: 92px minmax(0, 1fr); gap: 12px; padding: 13px 0; border-bottom: 1px solid var(--line); }
.record-segment:last-child { border-bottom: 0; }
.segment-heading { display: flex; align-items: baseline; gap: 7px; }
.segment-heading strong { color: var(--ink); font-size: 12px; }
.record-segment p { margin: 0; color: var(--ink); font-size: 12px; line-height: 1.75; white-space: pre-wrap; word-break: break-word; }
.detail-placeholder { display: grid; min-height: 570px; place-content: center; justify-items: center; padding: 30px; text-align: center; }
.detail-placeholder h3 { margin: 11px 0 7px; color: var(--ink); font: 500 22px/1.2 var(--display); }
.detail-placeholder p { max-width: 280px; margin: 0; color: var(--ink-soft); font-size: 12px; line-height: 1.75; }

@media (max-width: 900px) {
  .records-layout { grid-template-columns: 1fr; }
  .record-detail { position: static; min-height: 0; }
  .detail-placeholder { min-height: 280px; }
}

@media (max-width: 600px) {
  .records-overview { grid-template-columns: repeat(3, 1fr); }
  .overview-primary { grid-column: 1 / -1; min-height: 94px; border-right: 0; border-bottom: 1px solid var(--line); }
  .overview-stat { min-height: 84px; padding: 15px 14px; }
  .overview-stat strong { font-size: 23px; }
  .timeline-panel, .record-detail { padding: 17px 14px; }
  .timeline-item { grid-template-columns: 27px minmax(0, 1fr); gap: 10px; }
  .timeline-date { grid-column: 2; grid-row: 1; padding: 0 0 2px; }
  .timeline-card { grid-column: 2; grid-row: 2; }
  .timeline-list::before { bottom: 18px; }
  .detail-grid { gap: 13px 10px; }
  .linked-record { align-items: flex-start; flex-direction: column; }
  .record-segment { grid-template-columns: 78px minmax(0, 1fr); gap: 8px; }
}
</style>
