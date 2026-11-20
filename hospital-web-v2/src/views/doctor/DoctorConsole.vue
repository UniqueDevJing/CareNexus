<template>
  <section class="workspace doctor-space" aria-label="医生接诊工作区">
    <header class="work-header doctor-header">
      <div><span class="eyebrow">医生工作台 / 今日接诊</span><h2>{{ currentUser.name || currentUser.username }}，从最早到诊的患者开始。</h2><p>队列、风险提示和结构化病历放在同一张接诊桌上，记录完成后再提交。</p></div>
      <div class="work-header-actions"><div class="doctor-shift"><span class="status-pip"></span><div><strong>门诊进行中</strong><small>今日排班 · {{ pendingCount }} 位待接诊</small></div></div><button class="secondary-button" @click="load">刷新队列</button></div>
    </header>

    <section class="clinical-grid">
      <aside class="patient-queue surface">
        <header class="panel-head"><div><span class="eyebrow">今日队列</span><h3>待接诊患者</h3></div><span class="queue-count">{{ regs.length }}</span></header>
        <div class="queue-filter" role="tablist" aria-label="患者状态筛选"><button :class="{ active: queueFilter === 'all' }" @click="queueFilter = 'all'">全部 <small>{{ regs.length }}</small></button><button :class="{ active: queueFilter === 'pending' }" @click="queueFilter = 'pending'">待接诊 <small>{{ pendingCount }}</small></button><button :class="{ active: queueFilter === 'done' }" @click="queueFilter = 'done'">已完成 <small>{{ regs.length - pendingCount }}</small></button></div>
        <div class="queue-list">
          <button v-for="row in visibleRegs" :key="row.registration_id" :class="['patient-row', { active: emr.registrationId === row.registration_id }]" @click="openEmr(row)">
            <span class="care-avatar care-avatar--patient">{{ (row.patient_name || '患').slice(0, 1) }}</span>
            <span class="patient-summary"><b>{{ row.patient_name }}</b><small>{{ row.gender }} · {{ row.age }} 岁 · {{ row.period }}</small></span>
            <span :class="['queue-status', row.status === 'DONE' ? 'done' : 'waiting']">{{ row.status === 'DONE' ? '已完成' : '待接诊' }}</span>
          </button>
          <div v-if="!visibleRegs.length" class="empty-state"><span class="empty-icon">✓</span><b>{{ regs.length ? '这个筛选下暂无患者' : '当前没有待诊患者' }}</b><p>{{ regs.length ? '切换筛选查看完整队列。' : '新挂号会自动进入这里。' }}</p></div>
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
          <div class="record-form" :class="{ locked: emr.locked }">
            <div class="record-form-title"><div><span class="eyebrow">结构化病历</span><h4>本次接诊记录</h4></div><span>{{ emr.locked ? '已提交锁定' : '草稿自动保留在当前会话' }}</span></div>
            <label><span><b>01</b>主诉</span><el-input v-model="emr.chief_complaint" :disabled="emr.locked" placeholder="症状、持续时间与就诊原因" /></label>
            <label><span><b>02</b>现病史</span><el-input v-model="emr.present_illness" :disabled="emr.locked" type="textarea" :rows="3" placeholder="按时间顺序记录症状变化、诱因和伴随症状" /></label>
            <div class="record-pair"><label><span><b>03</b>既往史</span><el-input v-model="emr.past_history" :disabled="emr.locked" type="textarea" :rows="2" /></label><label><span><b>04</b>过敏史</span><el-input v-model="emr.allergy_history" :disabled="emr.locked" type="textarea" :rows="2" /></label></div>
            <label><span><b>05</b>辅助检查</span><el-input v-model="emr.assistant_exam" :disabled="emr.locked" type="textarea" :rows="2" placeholder="血常规、影像或其他检查结果" /></label>
            <label><span><b>06</b>诊断</span><el-input v-model="emr.diagnosis" :disabled="emr.locked" placeholder="明确或初步诊断" /></label>
            <label><span><b>07</b>处理意见</span><el-input v-model="emr.treatment" :disabled="emr.locked" type="textarea" :rows="3" placeholder="用药、处置、复诊与注意事项" /></label>
          </div>
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
const visibleRegs = computed(() => queueFilter.value === 'all' ? regs.value : regs.value.filter(r => queueFilter.value === 'done' ? r.status === 'DONE' : r.status !== 'DONE'))
const recordSections = ['主诉', '现病史', '既往史', '过敏史', '检查', '诊断', '处理']
const emr = reactive({ locked: false, id: null, registrationId: null, chief_complaint: '', present_illness: '', past_history: '', allergy_history: '', assistant_exam: '', diagnosis: '', treatment: '' })

onMounted(load)
async function load() { regs.value = await api.get('/doctor/registrations') }
function openEmr(row) { emr.locked = row.record_status === 'SUBMITTED'; emr.id = row.record_id || null; emr.registrationId = row.registration_id; emr.chief_complaint = ''; emr.present_illness = ''; emr.past_history = row.past_history || ''; emr.allergy_history = row.allergic_history || ''; emr.assistant_exam = ''; emr.diagnosis = ''; emr.treatment = ''; if (row.record_id) api.get('/doctor/record', { recordId: row.record_id }).then(d => { emr.chief_complaint = d.chief_complaint || ''; emr.present_illness = d.present_illness || ''; emr.past_history = d.past_history || ''; emr.allergy_history = d.allergy_history || ''; emr.assistant_exam = d.assistant_exam || ''; emr.diagnosis = d.diagnosis || ''; emr.treatment = d.treatment || '' }) }
function sectionFilled(index) { return Boolean(emr[['chief_complaint', 'present_illness', 'past_history', 'allergy_history', 'assistant_exam', 'diagnosis', 'treatment'][index]]) }
async function saveDraft() { const r = await api.post('/doctor/record/save', null, { id: emr.id, registration_id: emr.registrationId, patient_id: currentPatientId(), chief_complaint: emr.chief_complaint, present_illness: emr.present_illness, past_history: emr.past_history, allergy_history: emr.allergy_history, assistant_exam: emr.assistant_exam, diagnosis: emr.diagnosis, treatment: emr.treatment }); emr.id = r.recordId; ElMessage.success('草稿已保存'); load() }
async function submit() { if (!emr.id) { const r = await api.post('/doctor/record/save', null, { id: null, registration_id: emr.registrationId, patient_id: currentPatientId(), chief_complaint: emr.chief_complaint, present_illness: emr.present_illness, past_history: emr.past_history, allergy_history: emr.allergy_history, assistant_exam: emr.assistant_exam, diagnosis: emr.diagnosis, treatment: emr.treatment }); emr.id = r.recordId } await api.post('/doctor/record/submit?1=1', { recordId: emr.id, registrationId: emr.registrationId }); ElMessage.success('病历已提交，接诊已完成'); load() }
async function finish(row) { await api.post('/doctor/registration/done?1=1', { registrationId: row.registration_id }); ElMessage.success('已结诊'); load() }
function currentPatientId() { const row = regs.value.find(r => r.registration_id === emr.registrationId); return row ? row.patient_id : null }
</script>
