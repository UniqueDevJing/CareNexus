<template>
  <section class="workspace doctor-space" aria-label="医生接诊工作区" :inert="busy">
    <header class="work-header doctor-header">
      <div><span class="eyebrow">医生工作台 · 今日接诊</span><h2>{{ currentUser.name || currentUser.username }}，从最早到诊的患者开始</h2><p>队列、风险提示和结构化病历放在同一张接诊桌上，记录完成后再提交。</p></div>
      <div class="work-header-actions"><div class="doctor-shift"><span class="status-pip"></span><div><strong>门诊进行中</strong><small>今日排班 · {{ pendingCount }} 位待接诊</small></div></div><button class="secondary-button" @click="load">刷新队列</button></div>
    </header>

    <section v-if="agentTasks.length" class="agent-task-strip surface" aria-label="智能体临床接管任务">
      <div class="agent-task-strip__heading"><span class="eyebrow">智能体接管</span><strong>{{ agentTasks.length }} 项需要临床判断</strong><small>只展示已通过权限过滤的任务</small></div>
      <button v-for="task in agentTasks" :key="task.task_id" class="agent-task" type="button" @click="focusTask(task)">
        <span :class="['agent-task__priority', String(task.priority || '').toLowerCase()]">{{ task.priority === 'URGENT' ? '急' : '审' }}</span>
        <span><b>{{ task.title }}</b><small>{{ task.patient_name || '患者' }} · {{ task.detail || '请打开对应就诊记录' }}</small></span>
        <span class="agent-task__arrow" aria-hidden="true">→</span>
      </button>
    </section>

    <section class="clinical-grid">
      <aside class="patient-queue surface">
        <header class="panel-head"><div><span class="eyebrow">今日队列</span><h3>待接诊患者</h3></div><span class="queue-count">{{ regs.length }}</span></header>
        <div class="queue-filter" role="tablist" aria-label="患者状态筛选"><button :class="{ active: queueFilter === 'all' }" @click="queueFilter = 'all'">全部 <small>{{ regs.length }}</small></button><button :class="{ active: queueFilter === 'pending' }" @click="queueFilter = 'pending'">待接诊 <small>{{ pendingCount }}</small></button><button :class="{ active: queueFilter === 'done' }" @click="queueFilter = 'done'">已完成 <small>{{ regs.length - pendingCount }}</small></button></div>
        <div class="queue-list">
          <div v-if="loading" class="empty-state" role="status"><span class="empty-icon">…</span><b>正在同步接诊队列</b><p>不会展示上一位患者的旧数据。</p></div>
          <div v-else-if="loadError" class="empty-state empty-state--error" role="alert"><span class="empty-icon">!</span><b>接诊队列暂时无法加载</b><p>{{ loadError }}</p><button class="text-button" type="button" @click="load">重新加载</button></div>
          <button v-else v-for="row in visibleRegs" :key="row.registration_id" :class="['patient-row', { active: emr.registrationId === row.registration_id }]" @click="openEmr(row)">
            <span class="care-avatar care-avatar--patient">{{ (row.patient_name || '患').slice(0, 1) }}</span>
            <span class="patient-summary"><b>{{ row.patient_name }}</b><small>{{ row.gender }} · {{ row.age }} 岁 · {{ row.period }}</small></span>
            <span :class="['queue-status', row.status === 'DONE' ? 'done' : 'waiting']">{{ row.status === 'DONE' ? '已完成' : '待接诊' }}</span>
          </button>
          <div v-if="!loading && !loadError && !visibleRegs.length" class="empty-state"><span class="empty-icon">✓</span><b>{{ regs.length ? '这个筛选下暂无患者' : '当前没有待诊患者' }}</b><p>{{ regs.length ? '切换筛选查看完整队列。' : '新挂号会自动进入这里。' }}</p></div>
        </div>
      </aside>

      <article class="clinical-canvas surface">
        <template v-if="activePatient">
          <header class="patient-hero">
            <div class="patient-identity"><span class="care-avatar care-avatar--patient patient-avatar--large">{{ (activePatient.patient_name || '患').slice(0, 1) }}</span><div><span class="eyebrow">就诊档案 / #{{ activePatient.registration_id }}</span><h3>{{ activePatient.patient_name }}</h3><p>{{ activePatient.gender }} · {{ activePatient.age }} 岁 · {{ activePatient.department }} · {{ activePatient.work_date }} {{ activePatient.period }}</p></div></div>
            <span :class="['queue-status', activePatient.status === 'DONE' ? 'done' : 'waiting']">{{ activePatient.status === 'DONE' ? '已结诊' : '接诊中' }}</span>
          </header>
          <div class="clinical-alerts"><div><span>既往史</span><b>{{ activePatient.past_history || '暂无既往史记录' }}</b></div><div :class="{ alert: activePatient.allergic_history }"><span>过敏史</span><b>{{ activePatient.allergic_history || '暂无过敏记录' }}</b></div></div>
          <div class="record-progress" aria-label="病历填写进度"><span v-for="(label, i) in recordSections" :key="label" :class="{ filled: sectionFilled(i) }"><i>{{ i + 1 }}</i>{{ label }}</span></div>
          <div v-if="detailError" class="workspace-inline-error" role="alert"><strong>病历详情暂时无法读取</strong><span>{{ detailError }}</span><button class="text-button" type="button" @click="retryActive">重试</button></div>
          <div class="record-form" :class="{ locked: emr.locked }">
            <div class="record-form-title"><div><span class="eyebrow">结构化病历</span><h4>本次接诊记录</h4></div><span>{{ emr.locked ? '已提交锁定' : '草稿自动保留在当前会话' }}</span></div>
            <label><span><b>01</b>主诉</span><el-input v-model="emr.chief_complaint" :disabled="emr.locked" placeholder="症状、持续时间与就诊原因" /></label>
            <label><span><b>02</b>现病史</span><el-input v-model="emr.present_illness" :disabled="emr.locked" type="textarea" :rows="3" placeholder="按时间顺序记录症状变化、诱因和伴随症状" /></label>
            <div class="record-pair"><label><span><b>03</b>既往史</span><el-input v-model="emr.past_history" :disabled="emr.locked" type="textarea" :rows="2" /></label><label><span><b>04</b>过敏史</span><el-input v-model="emr.allergy_history" :disabled="emr.locked" type="textarea" :rows="2" /></label></div>
            <label><span><b>05</b>辅助检查</span><el-input v-model="emr.assistant_exam" :disabled="emr.locked" type="textarea" :rows="2" placeholder="血常规、影像或其他检查结果" /></label>
            <label><span><b>06</b>诊断</span><el-input v-model="emr.diagnosis" :disabled="emr.locked" placeholder="明确或初步诊断" /></label>
            <label><span><b>07</b>处理意见</span><el-input v-model="emr.treatment" :disabled="emr.locked" type="textarea" :rows="3" placeholder="用药、处置、复诊与注意事项" /></label>
          </div>
          <section class="clinical-orders" aria-label="检查与处方医嘱">
            <header class="record-form-title"><div><span class="eyebrow">临床医嘱</span><h4>检查与处方</h4></div><span>医嘱会自动生成患者账单</span></header>
            <div class="order-columns">
              <div class="order-card">
                <div class="order-card-head"><strong>开立检查</strong><small>结果由检验人员回填</small></div>
                <div class="order-fields"><el-input v-model="examForm.itemName" :disabled="emr.locked || orderBusy" placeholder="检查项目，如：血常规" /><el-input v-model="examForm.itemCode" :disabled="emr.locked || orderBusy" placeholder="项目编码（可选）" /><el-input v-model="examForm.fee" :disabled="emr.locked || orderBusy" type="number" min="0" step="0.01" placeholder="费用（元）" /></div>
                <button class="secondary-button" :disabled="emr.locked || orderBusy" @click="createExam">开立检查</button>
                <div v-if="ordersError" class="workspace-inline-error workspace-inline-error--compact" role="alert"><span>{{ ordersError }}</span><button class="text-button" type="button" @click="loadOrders(emr.registrationId)">重试</button></div>
                <div v-else-if="ordersLoading" class="order-empty" role="status">正在读取检查医嘱…</div>
                <div v-else-if="exams.length" class="order-list"><template v-for="exam in exams" :key="exam.id"><button class="order-line" type="button" @click="toggleExam(exam.id)"><span>{{ exam.item_name }}</span><small>{{ exam.status === 'RESULT_READY' ? '结果已回填' : exam.bill_status === 'PAID' ? '待检查' : '待支付' }} · {{ expandedExamId === exam.id ? '收起' : '详情' }}</small></button><div v-if="expandedExamId === exam.id" class="order-detail"><span>状态：{{ exam.status || '—' }}</span><span>费用：¥{{ exam.fee ?? '0.00' }}</span><span>结果：{{ exam.result_text || '尚未回填' }}</span><span>创建时间：{{ exam.create_time || '—' }}</span></div></template></div>
                <small v-else class="order-empty">本次就诊还没有检查医嘱</small>
              </div>
              <div class="order-card">
                <div class="order-card-head"><strong>开立处方</strong><small>支付后进入药房队列</small></div>
                <div class="order-fields"><el-input v-model="rxForm.drugName" :disabled="emr.locked || orderBusy" placeholder="药品名称" /><el-input v-model="rxForm.dosage" :disabled="emr.locked || orderBusy" placeholder="用法用量，如：每次1片" /><el-input v-model="rxForm.frequency" :disabled="emr.locked || orderBusy" placeholder="频次，如：每日2次" /><el-input v-model="rxForm.days" :disabled="emr.locked || orderBusy" type="number" min="1" max="365" placeholder="天数" /><el-input v-model="rxForm.quantity" :disabled="emr.locked || orderBusy" type="number" min="0.01" step="0.01" placeholder="数量" /><el-input v-model="rxForm.unitPrice" :disabled="emr.locked || orderBusy" type="number" min="0" step="0.01" placeholder="单价（元）" /></div>
                <button class="secondary-button" :disabled="emr.locked || orderBusy" @click="createPrescription">开立处方</button>
                <div v-if="ordersError" class="workspace-inline-error workspace-inline-error--compact" role="alert"><span>{{ ordersError }}</span><button class="text-button" type="button" @click="loadOrders(emr.registrationId)">重试</button></div>
                <div v-else-if="ordersLoading" class="order-empty" role="status">正在读取处方明细…</div>
                <div v-else-if="prescriptions.length" class="order-list"><template v-for="rx in prescriptions" :key="rx.id"><button class="order-line" type="button" @click="togglePrescription(rx.id)"><span>处方 #{{ rx.id }}</span><small>{{ rx.bill_status === 'PAID' ? '已支付' : '待支付' }} · ¥{{ rx.total_amount }} · {{ expandedPrescriptionId === rx.id ? '收起' : '详情' }}</small></button><div v-if="expandedPrescriptionId === rx.id" class="order-detail"><span v-for="item in (rx.items || [])" :key="item.id">{{ item.drug_name }} · {{ item.dosage }} · {{ item.frequency }} · {{ item.days }} 天 · {{ item.quantity }} 件</span><span v-if="!rx.items?.length">处方暂未返回药品明细</span></div></template></div>
                <small v-else class="order-empty">本次就诊还没有处方</small>
              </div>
            </div>
          </section>
          <footer class="record-actions"><span>{{ emr.locked ? '病历已提交并锁定，患者端将在归档后可见。' : '提交后病历将锁定，无法再次编辑。' }}</span><div><button v-if="!emr.id && !emr.locked" class="text-button" @click="finish(activePatient)">直接结诊</button><button v-if="!emr.locked" class="secondary-button" @click="saveDraft">保存草稿</button><button v-if="!emr.locked" class="primary-button" @click="submit">提交并完成接诊 <span aria-hidden="true">→</span></button></div></footer>
        </template>
        <div v-else class="canvas-placeholder"><div class="medical-cross" aria-hidden="true">+</div><span class="eyebrow">接诊桌已准备</span><h3>从左侧选择一位患者</h3><p>患者健康背景、风险提示与结构化病历将在这里展开。</p></div>
      </article>
    </section>
  </section>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api'

const currentUser = JSON.parse(localStorage.getItem('hospital_user') || '{}')
const regs = ref([])
const activePatient = computed(() => regs.value.find(r => r.registration_id === emr.registrationId))
const pendingCount = computed(() => regs.value.filter(r => r.status !== 'DONE').length)
const queueFilter = ref('all')
const busy = ref(false)
const loading = ref(false)
const loadError = ref('')
const detailError = ref('')
const ordersError = ref('')
const ordersLoading = ref(false)
let detailSequence = 0
const visibleRegs = computed(() => queueFilter.value === 'all' ? regs.value : regs.value.filter(r => queueFilter.value === 'done' ? r.status === 'DONE' : r.status !== 'DONE'))
const recordSections = ['主诉', '现病史', '既往史', '过敏史', '检查', '诊断', '处理']
const emr = reactive({ locked: false, id: null, registrationId: null, chief_complaint: '', present_illness: '', past_history: '', allergy_history: '', assistant_exam: '', diagnosis: '', treatment: '' })
const exams = ref([])
const prescriptions = ref([])
const agentTasks = ref([])
const orderBusy = ref(false)
const expandedExamId = ref(null)
const expandedPrescriptionId = ref(null)
const examForm = reactive({ itemName: '', itemCode: '', fee: '' })
const rxForm = reactive({ drugName: '', dosage: '', frequency: '', days: '3', quantity: '1', unitPrice: '0' })

onMounted(load)
async function load() {
  loading.value = true
  loadError.value = ''
  const previousRegistrationId = emr.registrationId
  ++detailSequence
  ordersLoading.value = false
  ordersError.value = ''
  // 清空旧队列，防止刷新失败时把上一轮权限范围内的数据继续展示。
  regs.value = []
  agentTasks.value = []
  try {
    const [registrationRows, taskRows] = await Promise.all([
      api.get('/doctor/registrations'),
      api.get('/doctor/agent-tasks')
    ])
    regs.value = registrationRows || []
    agentTasks.value = taskRows || []
    if (previousRegistrationId && !regs.value.some(row => row.registration_id === previousRegistrationId)) resetActiveEmr()
  } catch (e) {
    resetActiveEmr()
    loadError.value = e.response?.data?.msg || e.message || '工作台数据加载失败'
    ElMessage.error(e.response?.data?.msg || e.message || '工作台数据加载失败')
  } finally {
    loading.value = false
  }
}
function focusTask(task) {
  const row = regs.value.find(item => String(item.patient_id) === String(task.patient_id))
  if (row) openEmr(row)
  else ElMessage.info('该接管任务尚未形成预约记录，请先处理临床复核')
}
async function openEmr(row) {
  if (busy.value) return
  const sequence = ++detailSequence
  emr.locked = row.record_status === 'SUBMITTED' || row.status === 'DONE'
  detailError.value = ''
  ordersError.value = ''
  expandedExamId.value = null
  expandedPrescriptionId.value = null
  emr.id = row.record_id || null; emr.registrationId = row.registration_id
  for (const key of ['chief_complaint', 'present_illness', 'assistant_exam', 'diagnosis', 'treatment']) emr[key] = ''
  emr.past_history = row.past_history || ''; emr.allergy_history = row.allergic_history || ''
  exams.value = []; prescriptions.value = []
  resetOrderForms()
  if (row.record_id) {
    try {
      const record = await api.get('/doctor/record', { recordId: row.record_id })
      if (sequence !== detailSequence) return
      for (const key of ['chief_complaint', 'present_illness', 'past_history', 'allergy_history', 'assistant_exam', 'diagnosis', 'treatment']) emr[key] = record[key] || ''
    } catch (e) { if (sequence === detailSequence) { emr.locked = true; detailError.value = e.response?.data?.msg || e.message || '病历加载失败，请重试' } ElMessage.error('病历加载失败，请重新选择患者') }
  }
  await loadOrders(row.registration_id, sequence)
}
async function loadOrders(registrationId, sequence = detailSequence) {
  if (!registrationId) return
  ordersLoading.value = true
  ordersError.value = ''
  try {
    const [examRows, prescriptionRows] = await Promise.all([
      api.get('/doctor/exam-orders', { registrationId }),
      api.get('/doctor/prescriptions', { registrationId })
    ])
    if (sequence === detailSequence) { exams.value = examRows || []; prescriptions.value = prescriptionRows || [] }
  } catch (e) { if (sequence === detailSequence) { exams.value = []; prescriptions.value = []; ordersError.value = e.response?.data?.msg || e.message || '医嘱加载失败'; ElMessage.error(ordersError.value) } }
  finally { if (sequence === detailSequence) ordersLoading.value = false }
}
function resetActiveEmr() {
  ++detailSequence
  emr.locked = false; emr.id = null; emr.registrationId = null
  for (const key of ['chief_complaint', 'present_illness', 'past_history', 'allergy_history', 'assistant_exam', 'diagnosis', 'treatment']) emr[key] = ''
  exams.value = []; prescriptions.value = []; detailError.value = ''; ordersError.value = ''
  expandedExamId.value = null; expandedPrescriptionId.value = null
}
function retryActive() { const row = activePatient.value; if (row) openEmr(row) }
function toggleExam(id) { expandedExamId.value = expandedExamId.value === id ? null : id }
function togglePrescription(id) { expandedPrescriptionId.value = expandedPrescriptionId.value === id ? null : id }
function resetOrderForms() {
  Object.assign(examForm, { itemName: '', itemCode: '', fee: '' })
  Object.assign(rxForm, { drugName: '', dosage: '', frequency: '', days: '3', quantity: '1', unitPrice: '0' })
}
async function createExam() {
  if (orderBusy.value || emr.locked || !emr.registrationId) return
  if (!examForm.itemName.trim() || Number(examForm.fee) < 0) return ElMessage.warning('请填写检查项目和有效费用')
  orderBusy.value = true
  try {
    await api.post('/doctor/exam-orders', null, { registrationId: emr.registrationId, patientId: currentPatientId(), itemName: examForm.itemName, itemCode: examForm.itemCode, fee: examForm.fee })
    ElMessage.success('检查医嘱已开立'); resetOrderForms(); await loadOrders(emr.registrationId)
  } catch (e) { ElMessage.error(e.response?.data?.msg || e.message || '检查医嘱开立失败') }
  finally { orderBusy.value = false }
}
async function createPrescription() {
  if (orderBusy.value || emr.locked || !emr.registrationId) return
  if (!rxForm.drugName.trim() || !rxForm.dosage.trim() || !rxForm.frequency.trim() || Number(rxForm.days) < 1 || Number(rxForm.quantity) <= 0 || Number(rxForm.unitPrice) < 0) return ElMessage.warning('请完整填写药品、用法、频次、天数、数量和单价')
  orderBusy.value = true
  try {
    await api.post('/doctor/prescriptions', null, { registrationId: emr.registrationId, patientId: currentPatientId(), items: [{ drugName: rxForm.drugName, dosage: rxForm.dosage, frequency: rxForm.frequency, days: rxForm.days, quantity: rxForm.quantity, unitPrice: rxForm.unitPrice }] })
    ElMessage.success('处方已开立'); resetOrderForms(); await loadOrders(emr.registrationId)
  } catch (e) { ElMessage.error(e.response?.data?.msg || e.message || '处方开立失败') }
  finally { orderBusy.value = false }
}
function sectionFilled(index) { return Boolean(emr[['chief_complaint', 'present_illness', 'past_history', 'allergy_history', 'assistant_exam', 'diagnosis', 'treatment'][index]]) }
async function saveDraft() {
  if (busy.value || emr.locked) return
  busy.value = true
  try {
    const r = await api.post('/doctor/record/save', null, { ...emr, registration_id: emr.registrationId, patient_id: currentPatientId() })
    emr.id = r.recordId; ElMessage.success('草稿已保存'); await load()
  } catch (e) { ElMessage.error(e.response?.data?.msg || e.message || '保存失败') }
  finally { busy.value = false }
}
async function submit() {
  if (busy.value || emr.locked) return
  busy.value = true
  try {
    // 提交前保存屏幕上的最新内容，已有草稿也不能跳过更新。
    {
      const r = await api.post('/doctor/record/save', null, { ...emr, registration_id: emr.registrationId, patient_id: currentPatientId() })
      emr.id = r.recordId
    }
    await api.post('/doctor/record/submit', { recordId: emr.id, registrationId: emr.registrationId })
    emr.locked = true
    ElMessage.success('病历已提交，接诊已完成')
    await load()
  } catch (e) { ElMessage.error(e.response?.data?.msg || e.message || '提交失败') }
  finally { busy.value = false }
}
async function finish(row) {
  if (busy.value) return
  busy.value = true
  try {
    await api.post('/doctor/registration/done', { registrationId: row.registration_id })
    if (emr.registrationId === row.registration_id) emr.locked = true
    ElMessage.success('已结诊'); await load()
  } catch (e) { ElMessage.error(e.response?.data?.msg || e.message || '结诊失败') }
  finally { busy.value = false }
}
function currentPatientId() { const row = regs.value.find(r => r.registration_id === emr.registrationId); return row ? row.patient_id : null }
</script>
