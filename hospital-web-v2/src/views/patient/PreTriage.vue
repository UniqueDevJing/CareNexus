<template>
  <section class="workspace patient-space" :inert="busy" :aria-busy="busy" aria-label="智能预问诊工作区">
    <header class="patient-page-head">
      <div>
        <span class="eyebrow">患者服务 / 就诊准备</span>
        <h2>把这次不舒服，先说给人听。</h2>
        <p>用几分钟补充症状和健康背景，系统会把信息整理给护士与医生判断。</p>
      </div>
      <div class="patient-head-note"><span class="care-seal"><i></i></span><div><strong>隐私就诊</strong><small>信息只在本次就诊链路内使用</small></div></div>
    </header>

    <nav class="care-steps" aria-label="就诊准备进度">
      <span :class="['care-step', { 'is-current': sessionId && !suggested }]" ><b>01</b><strong>描述需求</strong><small>{{ sessionId ? '正在沟通' : '待开始' }}</small></span>
      <i aria-hidden="true"></i>
      <span :class="['care-step', { 'is-current': suggested && !registered }]" ><b>02</b><strong>选择安排</strong><small>{{ suggested ? '请选择时间' : '自动匹配' }}</small></span>
      <i aria-hidden="true"></i>
      <span :class="['care-step', { 'is-current': registered }]" ><b>03</b><strong>确认预约</strong><small>{{ registered ? '已完成' : '待确认' }}</small></span>
    </nav>

    <section class="patient-consult-layout">
      <article class="consultation-panel">
        <header class="chat-header">
          <div class="care-contact">
            <span class="care-avatar care-avatar--triage" aria-hidden="true"><i></i></span>
            <div><strong>门诊导诊助手</strong><small><i></i>{{ sessionId ? '正在记录本次沟通' : '直接描述需求即可开始' }}</small></div>
          </div>
          <div class="chat-header-actions"><span class="chat-channel">线上预问诊</span><button v-if="sessionId" class="secondary-button" @click="start">开始新咨询</button></div>
        </header>

        <div ref="box" class="chat-thread">
          <div class="chat-day-divider"><span>今天 · 门诊服务中心</span></div>
          <div v-if="!msgs.length" class="chat-empty">
            <div class="chat-empty-mark" aria-hidden="true"><span></span><i></i></div>
            <div><span class="eyebrow">先从最困扰你的地方开始</span><h3>你现在最想解决什么问题？</h3><p>可以告诉我身体部位、出现时间、严重程度，以及有没有发热、胸闷等变化。</p></div>
            <div class="starter-prompts"><button type="button" @click="input = '我从昨晚开始胸闷，活动后更明显'">胸闷 / 呼吸不适</button><button type="button" @click="input = '这两天一直发热，伴有咳嗽'">发热 / 咳嗽</button><button type="button" @click="input = '我最近反复胃痛，饭后更明显'">腹痛 / 消化不适</button></div>
          </div>

          <div v-for="(m, i) in msgs" :key="i" :class="['message-row', m.role]">
            <span v-if="m.role === 'bot'" class="care-avatar care-avatar--triage message-avatar" aria-hidden="true"><i></i></span>
            <div class="message-content"><div class="message-meta"><strong>{{ m.role === 'bot' ? '门诊导诊助手' : '你' }}</strong><small>{{ m.role === 'bot' ? '导诊记录' : '刚刚' }}</small></div><p>{{ m.text }}<i v-if="m.typing" class="typing-caret"></i></p></div>
            <span v-if="m.role === 'user'" class="care-avatar care-avatar--patient message-avatar" aria-hidden="true">患</span>
          </div>
        </div>

        <footer class="chat-composer">
          <textarea v-model="input" rows="2" :disabled="locked || streaming || busy" placeholder="例如：咳嗽三天，想约明天下午的门诊……" @keydown.enter.exact.prevent="send"></textarea>
          <div class="composer-footer"><span>{{ locked ? '本次咨询已完成' : needsReview ? '人工核对中；需要补充时可继续回复' : streaming || busy ? '正在整理并匹配就诊安排…' : 'Enter 发送 · Shift + Enter 换行' }}</span><div class="composer-actions"><button class="send-button" :disabled="locked || streaming || busy || !input.trim()" @click="send">发送 <span aria-hidden="true">↑</span></button></div></div>
        </footer>
      </article>

      <aside class="patient-rail">
        <article v-if="suggested" :class="['care-result-card', { urgent: sug.red_flag }]">
          <header><div><span class="eyebrow">就诊建议</span><h3>{{ sug.red_flag ? '请优先人工处理' : sug.status === 'REJECTED' ? '已转人工处理' : needsReview ? '人工核对中' : '建议就诊安排' }}</h3></div></header>
          <div v-if="sug.red_flag" class="urgent-notice"><i aria-hidden="true">!</i><span>检测到危急征象，请优先联系急诊或人工医生。</span></div>
          <div class="result-department"><span>推荐科室</span><strong>{{ sug.department_name || '待人工确认' }}</strong><small>{{ sug.doctor_name || '系统暂未匹配医生' }} · {{ selectedSlot ? `${selectedSlot.work_date} ${selectedSlot.period === 'AM' ? '上午' : '下午'}` : availabilityLabel }}</small></div>
          <div class="result-reason"><span>推荐依据</span><p>{{ sug.reason || '系统未提供额外说明' }}</p></div>
          <div v-if="needsReview" class="booking-handoff">
            <div class="handoff-status"><span class="handoff-dot" aria-hidden="true"></span><div><strong>{{ reviewLabel() }}</strong><small>{{ sug.queue_code || '已建立人工任务' }} · {{ updatedLabel() }}</small></div></div>
            <p v-if="sug.manual_question"><b>护士想确认：</b>{{ sug.manual_question }}</p>
            <p v-else>科室仅为初步建议，尚未通过人工核对。资料已提交护士，核对完成后再确认就诊安排。</p>
            <p v-if="!sug.suggested_date">当前尚无可确认号源；人工核对与号源安排是两个独立步骤，核对通过也不代表预约成功。</p>
            <small>状态会自动更新，无需手动刷新。</small>
          </div>
          <div v-else-if="sug.status === 'REJECTED'" class="booking-handoff booking-handoff--warning"><strong>当前建议未进入预约</strong><p>系统无法安全确认本次安排，已转人工处理。请继续补充症状，或联系门诊服务台。</p></div>
          <div v-else-if="!registered && sug.can_register !== false" class="slot-picker">
            <div class="slot-picker-heading"><strong>为你优先推荐</strong><small>号源会在确认时再次校验</small></div>
            <div v-if="slots.length" class="slot-list" role="radiogroup" aria-label="选择就诊时间">
              <button v-for="slot in displayedSlots" :key="slot.id" type="button" role="radio" :aria-checked="selectedSlotId === slot.id" :class="['slot-option', { selected: selectedSlotId === slot.id }]" @click="selectedSlotId = slot.id"><strong>{{ slot.work_date }} · {{ slot.period === 'AM' ? '上午' : slot.period === 'PM' ? '下午' : slot.period }}</strong><small>{{ slot.recommendation }} · 余 {{ slot.remain_num }} 个</small></button>
            </div>
            <button v-if="slots.length > 3" type="button" class="secondary-button" @click="showAllSlots = !showAllSlots">{{ showAllSlots ? '收起其他时间' : `查看其他 ${slots.length - 3} 个时间` }}</button>
            <div v-if="!slots.length" class="slot-empty-state"><strong>当前推荐医生暂无可预约号源</strong><p>科室建议仍然有效，但预约需要真实号源。你可以刷新号源，或重新描述需求让系统匹配其他医生。</p><div class="slot-empty-actions"><button type="button" class="secondary-button" :disabled="busy" @click="loadSlots">刷新号源</button><button type="button" class="text-button" :disabled="busy" @click="start">重新匹配</button></div></div>
            <button v-else class="primary-button primary-button--wide" :disabled="!selectedSlotId || busy" @click="doRegister">确认预约所选时间 →</button>
          </div>
          <button v-else-if="registered" class="primary-button primary-button--wide" disabled>预约已完成</button>
          <div v-else class="booking-handoff">当前暂无可约号源，请稍后重试或联系门诊服务台。</div>
          <div v-if="registered" class="booking-proof"><small>预约凭证 · {{ reg.work_date }} {{ reg.period }}</small><span v-if="reg.payment_status !== 'PAID'">挂号已保留，完成支付后进入正式接诊</span><router-link v-if="reg.payment_status !== 'PAID'" to="/patient/bills">去账单支付 →</router-link></div>
        </article>

        <article v-else class="care-safety-card">
          <div class="rail-heading"><span class="safety-seal" aria-hidden="true">✓</span><div><span class="eyebrow">安全边界</span><h3>这不是诊断结论</h3></div></div>
          <p>系统只整理信息、识别风险并匹配资源，最终判断由医护人员完成。</p>
          <ul><li><i></i>发现危急征象，优先提示急诊</li><li><i></i>信息不足时，要求补充或转人工</li><li><i></i>每一次建议都有依据可回看</li></ul>
        </article>

        <details class="patient-profile" open>
          <summary><div><span class="care-avatar care-avatar--patient">{{ (profile.name || '患').slice(0, 1) }}</span><div><strong>{{ profile.name || '完善健康档案' }}</strong><small>{{ profile.age ? `${profile.gender} · ${profile.age} 岁` : '让医护更快了解你' }}</small></div></div><span>编辑</span></summary>
          <div class="profile-form">
            <label>姓名<el-input v-model="profile.name" /></label>
            <div class="profile-pair"><label>性别<el-radio-group v-model="profile.gender"><el-radio-button value="男">男</el-radio-button><el-radio-button value="女">女</el-radio-button></el-radio-group></label><label>年龄<el-input-number v-model="profile.age" :min="0" :max="120" /></label></div>
            <label>慢病史<el-input v-model="profile.past_history" type="textarea" :rows="2" placeholder="如：高血压 8 年" /></label>
            <label>过敏史<el-input v-model="profile.allergic_history" type="textarea" :rows="2" placeholder="如：青霉素过敏" /></label>
            <button class="primary-button primary-button--wide" :disabled="saving" @click="saveProfile">{{ saving ? '保存中…' : '保存健康档案' }}</button>
          </div>
        </details>

        <article class="patient-rail-note"><span class="rail-note-line" aria-hidden="true"></span><div><strong>就诊前准备</strong><p>如果方便，请准备正在服用的药物、过敏信息和既往检查结果。</p></div></article>
      </aside>
    </section>
  </section>
</template>

<script setup>
import { ref, computed, nextTick, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api'
import { createSseParser } from '../../sse'

const sessionId = ref(null)
const msgs = ref([])
const input = ref('')
const streaming = ref(false)
const locked = ref(false)
const suggested = ref(false)
const needsReview = ref(false)
const sug = ref({})
const registered = ref(false)
const reg = ref({})
const slots = ref([])
const selectedSlotId = ref(null)
const selectedSlot = computed(() => slots.value.find(s => s.id === selectedSlotId.value))
const availabilityLabel = computed(() => {
  if (selectedSlot.value) return `${selectedSlot.value.work_date} ${selectedSlot.value.period === 'AM' ? '上午' : '下午'}`
  return ({ AVAILABLE: '可预约', NO_SLOTS: '暂无号源', PENDING_REVIEW: '等待人工核对', EMERGENCY: '急诊优先' }[sug.value.availability_status] || sug.value.suggested_date || '日期待确认')
})
const showAllSlots = ref(false)
const displayedSlots = computed(() => showAllSlots.value ? slots.value : slots.value.slice(0, 3))
const box = ref(null)
const saving = ref(false)
const refreshing = ref(false)
const busy = ref(false)
let disposed = false
let chatAbort
const lastUpdated = ref(null)
const profile = reactive({ name: '', gender: '男', age: null, past_history: '', allergic_history: '' })
let statusTimer

async function loadProfile() { const p = await api.get('/patient/profile'); if (p) Object.assign(profile, { name: p.name || '', gender: p.gender || '男', age: p.age, past_history: p.past_history || '', allergic_history: p.allergic_history || '' }) }
async function saveProfile() { saving.value = true; try { await api.post('/patient/profile/save', null, profile); ElMessage.success('健康档案已保存') } finally { saving.value = false } }
async function restoreSession() {
  const previousSession = sessionId.value
  const saved = localStorage.getItem('carenexus_pretriage_session'); if (!saved) return false
  try {
    const data = await api.get('/patient/session/history', { sessionId: Number(saved) })
    if (disposed || sessionId.value !== previousSession) return true
    sessionId.value = data.sessionId; locked.value = Number(data.locked) === 1 || data.status === 'LOCKED'; msgs.value = (data.messages || []).map(m => ({ role: m.role === 'assistant' ? 'bot' : 'user', text: m.content }))
    if (data.suggestion) { sug.value = data.suggestion; suggested.value = true; needsReview.value = !!data.suggestion.needs_confirmation; registered.value = Number(data.suggestion.registered) === 1 || !!data.registration; if (data.registration) reg.value = data.registration; lastUpdated.value = new Date(); if (!needsReview.value && !registered.value && data.suggestion.status === 'CONFIRMED') await loadSlots() }
    scroll()
    return true
  } catch { localStorage.removeItem('carenexus_pretriage_session'); return false }
}
onMounted(async () => {
  await loadProfile(); await restoreSession()
  statusTimer = window.setInterval(() => {
    if (!sessionId.value || !suggested.value || registered.value) return
    if (needsReview.value) refreshReview(true)
    else if (!slots.value.length) loadSlots()
  }, 30000)
})
onUnmounted(() => { disposed = true; chatAbort?.abort(); window.clearInterval(statusTimer) })
function scroll() { nextTick(() => { if (box.value) box.value.scrollTop = box.value.scrollHeight }) }
async function startRequest() { const r = await api.post('/patient/session'); sessionId.value = r.sessionId; localStorage.setItem('carenexus_pretriage_session', String(r.sessionId)); msgs.value = []; locked.value = false; suggested.value = false; needsReview.value = false; registered.value = false; sug.value = {}; slots.value = []; selectedSlotId.value = null; showAllSlots.value = false; lastUpdated.value = null }
async function start() {
  if (busy.value || streaming.value || refreshing.value || disposed) return
  busy.value = true
  try { await startRequest() } finally { busy.value = false }
}
async function send() {
  if (busy.value || refreshing.value || disposed || locked.value) return
  const text = input.value.trim(); if (!text || streaming.value) return
  if (!sessionId.value) { busy.value = true; try { await startRequest() } catch (e) { ElMessage.error(e.response?.data?.msg || '暂时无法开始对话'); return } finally { busy.value = false } }
  msgs.value.push({ role: 'user', text }); input.value = ''; streaming.value = true
  const assistant = { role: 'bot', text: '', typing: true }; msgs.value.push(assistant); scroll()
  let delivered = false
  try {
    chatAbort = new AbortController()
    const resp = await fetch('/v2/api/patient/chat', { signal: chatAbort.signal, method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sessionId: sessionId.value, msg: text }) })
    if (!resp.ok) throw new Error(`SSE ${resp.status}`)
    const reader = resp.body.getReader(); const decoder = new TextDecoder()
    const parser = createSseParser(data => { if (data === '[DONE]') { assistant.typing = false; return } assistant.text += data; scroll() })
    while (true) { const { done, value } = await reader.read(); if (done) break; parser.push(decoder.decode(value, { stream: true })) }
    parser.push(decoder.decode()); parser.finish()
    assistant.typing = false
    delivered = true
  } catch (e) { assistant.text = '连接暂时中断，请检查网络后重试。'; assistant.typing = false; ElMessage.error('导诊助手连接失败，请稍后重试') }
  if (delivered && needsReview.value && sug.value.status === 'NEED_MORE_INFO') { sug.value = { ...sug.value, status: 'PENDING', manual_question: '' } }
  streaming.value = false
  const userTurns = msgs.value.filter(message => message.role === 'user').length
  if (delivered && !suggested.value && (userTurns >= 3 || /信息已齐全[，,。\s]*正在为您匹配就诊安排/.test(assistant.text))) await doTriage()
}
async function doTriageRequest() { const complaint = input.value.trim() || msgs.value.filter(m => m.role === 'user').map(m => m.text).join('，'); if (!complaint) { ElMessage.warning('请先描述症状'); return } try { const result = await api.post('/patient/triage', { sessionId: sessionId.value, complaint }); sug.value = result; suggested.value = true; needsReview.value = !!result.needs_confirmation; locked.value = !needsReview.value || Number(result.red_flag) === 1; lastUpdated.value = new Date(); if (!needsReview.value && result.can_register) await loadSlots() } catch (e) { ElMessage.error(e.response?.data?.msg || e.message || '暂时无法生成就诊建议') } }
async function loadSlots() { if (!sug.value.suggestion_id) return; try { slots.value = await api.get('/patient/available-slots', { suggestionId: sug.value.suggestion_id }); showAllSlots.value = false; if (!slots.value.some(s => s.id === selectedSlotId.value)) selectedSlotId.value = slots.value[0]?.id || null } catch (e) { slots.value = []; selectedSlotId.value = null; ElMessage.error(e.response?.data?.msg || '暂时无法查询号源') } }
async function doTriage() {
  if (busy.value || streaming.value || refreshing.value || disposed) return
  busy.value = true
  try { await doTriageRequest() } finally { busy.value = false }
}
async function refreshReview(silent = false) {
  if (!sessionId.value || refreshing.value || busy.value || streaming.value || disposed) return
  refreshing.value = true
  try { const restored = await restoreSession(); if (!restored) throw new Error('人工状态暂时不可用'); if (!silent) ElMessage.success(needsReview.value ? '人工状态仍在处理中' : '人工状态已更新') }
  catch (e) { if (!silent) ElMessage.error('暂时无法刷新人工处理状态') }
  finally { refreshing.value = false }
}
async function doRegisterRequest() { if (!selectedSlotId.value) return; try { reg.value = await api.post('/patient/register', { suggestionId: sug.value.suggestion_id, scheduleId: selectedSlotId.value }); registered.value = true; ElMessage.success(`预约成功：${reg.value.work_date} ${reg.value.period}`) } catch (e) { ElMessage.error(e.response?.data?.msg || e.message || '预约失败，号源可能刚刚被占用'); await loadSlots() } }
async function doRegister() {
  if (busy.value || streaming.value || refreshing.value || disposed) return
  busy.value = true
  try { await doRegisterRequest() } finally { busy.value = false }
}
function reviewLabel() { if (sug.value.red_flag) return '已转急诊 / 人工医生'; if (sug.value.status === 'NEED_MORE_INFO') return '等待你补充信息'; if (sug.value.status === 'REJECTED') return '已转人工处理'; return '已提交护士核对' }
function updatedLabel() { return lastUpdated.value ? `最近更新 ${lastUpdated.value.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}` : '状态会自动更新' }
</script>
