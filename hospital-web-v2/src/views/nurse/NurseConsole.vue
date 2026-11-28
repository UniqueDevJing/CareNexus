<template>
  <section class="workspace nurse-space" aria-label="护士工作台">
    <header class="work-header">
      <div><span class="eyebrow">护士工作台 · 临床接管</span><h2>把需要人工确认的事项处理清楚</h2><p>只处理智能体无法安全闭环的分诊和已支付处方，所有状态变化都会回写业务记录。</p></div>
      <button class="secondary-button" type="button" :disabled="loading" @click="load">刷新工作队列</button>
    </header>
    <div class="nurse-tabs" role="tablist" aria-label="护士工作模块">
      <button type="button" :class="{ active: tab === 'triage' }" @click="tab = 'triage'">分诊复核 <small>{{ triage.length }}</small></button>
      <button type="button" :class="{ active: tab === 'pharmacy' }" @click="tab = 'pharmacy'">药房待发药 <small>{{ pharmacy.length }}</small></button>
    </div>

    <div v-if="loading" class="surface module-state" role="status">正在同步护士工作队列…</div>
    <div v-else-if="error" class="surface module-state module-state--error" role="alert"><strong>护士工作队列暂时无法加载</strong><span>{{ error }}</span><button class="text-button" type="button" @click="load">重新加载</button></div>
    <section v-else-if="tab === 'triage'" class="nurse-grid">
      <aside class="surface nurse-list"><header class="panel-head"><div><span class="eyebrow">临床复核 · Clinical review</span><h3>待复核分诊</h3></div><span class="panel-note">{{ triage.length }} 项</span></header><button v-for="item in triage" :key="item.suggestion_id" type="button" :class="['nurse-row', { active: selected?.suggestion_id === item.suggestion_id }]" @click="select(item)"><span class="care-avatar care-avatar--patient">{{ (item.patient_name || '患').slice(0, 1) }}</span><span><b>{{ item.patient_name || '患者' }}</b><small>{{ item.department_name || '待匹配科室' }} · {{ item.status === 'NEED_MORE_INFO' ? '等待补充' : item.red_flag ? '风险优先' : '待确认' }}</small></span><span class="queue-status" :class="item.red_flag ? 'waiting' : ''">{{ item.queue_code }}</span></button><div v-if="!triage.length" class="empty-state"><span class="empty-icon">✓</span><b>当前没有待复核分诊</b><p>患者补充信息后，待复核任务会重新进入队列。</p></div></aside>
      <article class="surface nurse-detail" v-if="selected"><header class="patient-hero"><div class="patient-identity"><span class="care-avatar care-avatar--patient patient-avatar--large">{{ (selected.patient_name || '患').slice(0, 1) }}</span><div><span class="eyebrow">分诊任务 / {{ selected.queue_code }}</span><h3>{{ selected.patient_name || '患者' }}</h3><p>{{ selected.gender || '—' }} · {{ selected.age ?? '—' }} 岁 · {{ selected.department_name || '待匹配科室' }} · {{ selected.doctor_name || '待匹配医生' }}</p></div></div><span class="chip" :class="selected.red_flag ? 'warn' : 'ok'">{{ selected.red_flag ? '风险优先' : '人工确认' }}</span></header><dl class="nurse-facts"><div><dt>患者最新描述</dt><dd>{{ selected.last_user_message || '暂无补充描述' }}</dd></div><div><dt>智能体建议依据</dt><dd>{{ selected.reason || '暂无依据' }}</dd></div><div><dt>既往史 / 过敏史</dt><dd>{{ selected.past_history || '无既往史' }} · {{ selected.allergic_history || '无过敏记录' }}</dd></div></dl><div v-if="selected.status === 'NEED_MORE_INFO'" class="workspace-inline-error"><strong>已向患者追问</strong><span>{{ selected.manual_question || '等待患者补充关键信息' }}</span></div><label class="nurse-note"><span>处理说明</span><el-input v-model="note" type="textarea" :rows="3" maxlength="220" show-word-limit placeholder="记录核验依据、转诊原因或需要补充的问题" /></label><div class="nurse-actions"><button class="secondary-button" type="button" :disabled="actionBusy || selected.status !== 'PENDING'" @click="resolve('REQUEST_INFO')">向患者补问</button><button class="secondary-button" type="button" :disabled="actionBusy || selected.status !== 'PENDING'" @click="resolve('REJECT')">转人工/急诊</button><button class="primary-button" type="button" :disabled="actionBusy || selected.status !== 'PENDING'" @click="resolve('CONFIRM')">确认分诊</button></div></article>
      <div v-else class="surface canvas-placeholder"><div class="medical-cross">+</div><span class="eyebrow">复核桌已准备</span><h3>从左侧选择一项任务</h3><p>患者描述、风险信号和智能体依据会在这里展开。</p></div>
    </section>
    <section v-else class="surface nurse-list pharmacy-list"><header class="panel-head"><div><span class="eyebrow">药房处理 · Pharmacy</span><h3>已支付待发药</h3></div><span class="panel-note">{{ pharmacy.length }} 张处方</span></header><div v-if="!pharmacy.length" class="empty-state"><span class="empty-icon">✓</span><b>当前没有待发药处方</b><p>患者完成支付后，处方会进入这里。</p></div><div v-for="item in pharmacy" :key="item.prescription_id" class="pharmacy-row"><div><b>处方 #{{ item.prescription_id }} · {{ item.patient_name || '患者' }}</b><small>{{ item.doctor_name || '门诊医生' }} · {{ item.create_time || '—' }} · ¥{{ item.total_amount ?? '0.00' }}</small></div><button class="primary-button" type="button" :disabled="actionBusy" @click="dispense(item)">确认发药</button></div></section>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api'

const tab = ref('triage')
const triage = ref([])
const pharmacy = ref([])
const selected = ref(null)
const note = ref('')
const loading = ref(false)
const actionBusy = ref(false)
const error = ref('')

onMounted(load)
async function load() {
  loading.value = true; error.value = ''; selected.value = null; note.value = ''; triage.value = []; pharmacy.value = []
  try { const [triageRows, pharmacyRows] = await Promise.all([api.get('/nurse/triage/queue'), api.get('/nurse/pharmacy/queue')]); triage.value = triageRows || []; pharmacy.value = pharmacyRows || [] }
  catch (e) { error.value = e.response?.data?.msg || e.message || '工作队列加载失败'; ElMessage.error(error.value) }
  finally { loading.value = false }
}
function select(item) { selected.value = item; note.value = item.review_note || '' }
async function resolve(action) {
  if (!selected.value || actionBusy.value) return
  if (action === 'REQUEST_INFO' && note.value.trim().length < 6) return ElMessage.warning('请写清楚需要患者补充的关键问题')
  actionBusy.value = true
  try { await api.post('/nurse/triage/resolve', null, { suggestionId: selected.value.suggestion_id, action, note: note.value }); ElMessage.success(action === 'CONFIRM' ? '分诊已确认' : action === 'REQUEST_INFO' ? '已向患者发出补问' : '已转人工处理'); await load() }
  catch (e) { ElMessage.error(e.response?.data?.msg || e.message || '分诊处理失败') }
  finally { actionBusy.value = false }
}
async function dispense(item) {
  if (actionBusy.value) return
  actionBusy.value = true
  try { await api.post('/nurse/pharmacy/dispense', { prescriptionId: item.prescription_id }); ElMessage.success(`处方 #${item.prescription_id} 已发药`); await load() }
  catch (e) { ElMessage.error(e.response?.data?.msg || e.message || '发药失败') }
  finally { actionBusy.value = false }
}
</script>
