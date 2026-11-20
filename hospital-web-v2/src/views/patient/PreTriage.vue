<template>
  <section class="workspace patient-journey" aria-label="智能预问诊工作区">
    <section class="service-header">
      <div>
        <span class="eyebrow">就诊准备 / 01</span>
        <h2>先把这次不舒服说清楚。</h2>
        <p>用 3–5 分钟补充症状和健康背景，我们会把结果交给合适的医生判断。</p>
      </div>
      <div class="service-meta"><span><i></i>隐私连接已保护</span><span>建议不替代诊断</span></div>
    </section>

    <section class="journey-steps journey-steps--bar" aria-label="就诊准备进度">
      <span class="done"><i>1</i><b>健康档案</b><small>已准备</small></span><b></b><span :class="{ active: sessionId }"><i>2</i><b>症状沟通</b><small>{{ sessionId ? '进行中' : '待开始' }}</small></span><b></b><span :class="{ active: suggested }"><i>3</i><b>就诊建议</b><small>{{ suggested ? '已生成' : '完成沟通后' }}</small></span>
    </section>

    <section class="consultation-shell">
      <article class="conversation-panel">
        <header class="conversation-head">
          <div class="agent-identity"><span class="agent-symbol">01</span><div><b>症状沟通</b><small><i></i>{{ sessionId ? `会话 #${sessionId} 已建立` : '还没有开始本次就诊' }}</small></div></div>
          <button class="secondary-button" @click="start">{{ sessionId ? '重新开始' : '开始预问诊' }}</button>
        </header>

        <div ref="box" class="conversation-stream">
          <div v-if="!msgs.length" class="conversation-welcome">
            <div class="welcome-copy"><span class="welcome-mark">+</span><div><span class="eyebrow">从一句话开始</span><h3>最困扰你的症状是什么？</h3><p>可以先说部位、出现时间、严重程度，以及有没有发热、胸闷或其他变化。</p></div></div>
            <div class="starter-prompts"><button type="button" @click="input = '我从昨晚开始胸闷，活动后更明显'">胸闷 / 呼吸不适</button><button type="button" @click="input = '这两天一直发热，伴有咳嗽'">发热 / 咳嗽</button><button type="button" @click="input = '我最近反复胃痛，饭后更明显'">腹痛 / 消化不适</button></div>
            <button v-if="!sessionId" class="primary-button" @click="start">开始安全预问诊</button>
          </div>
          <div v-for="(m, i) in msgs" :key="i" :class="['message-row', m.role]">
            <span v-if="m.role === 'bot'" class="message-avatar">T</span>
            <div class="message-content"><small>{{ m.role === 'bot' ? '预问诊记录' : '你' }}</small><p>{{ m.text }}<i v-if="m.typing" class="typing-caret"></i></p></div>
          </div>
        </div>

        <footer class="composer">
          <textarea v-model="input" rows="2" :disabled="!sessionId || locked || streaming" placeholder="例如：从昨晚开始持续胸闷，活动后更明显……" @keydown.enter.exact.prevent="send"></textarea>
          <div><span>{{ locked ? '本次会话已结束，可重新开始新的预问诊' : needsReview ? '建议等待人工复核，也可以继续补充信息' : streaming ? '正在生成回复…' : 'Enter 发送 · 对话会保存到本次会话' }}</span><div><button class="secondary-button" :disabled="!sessionId || locked || streaming || (suggested && !needsReview)" @click="doTriage">{{ needsReview ? '补充信息后重新分诊' : '生成建议并查看号源' }}</button><button class="send-button" :disabled="!sessionId || locked || streaming || !input.trim()" @click="send">发送 ↑</button></div></div>
        </footer>
      </article>

      <aside class="journey-side">
        <article v-if="suggested" :class="['result-ticket', { urgent: sug.red_flag }]">
          <header><span class="eyebrow">TRIAGE RESULT</span><span class="confidence">{{ Math.round((sug.confidence || 0) * 100) }}% 置信度</span></header>
          <div v-if="sug.red_flag" class="urgent-notice">检测到危急征象，已切换急诊绿色通道</div>
          <span class="result-label">推荐就诊</span><h3>{{ sug.department_name }}</h3><p>{{ sug.doctor_name }} · {{ sug.suggested_date }}</p>
          <div class="result-reason"><b>为什么这样推荐</b><p>{{ sug.reason }}</p></div>
          <div v-if="sug.needs_confirmation" class="booking-proof booking-proof--warning">当前信息不足或需要人工核实，已进入人工复核；你也可以继续补充信息。</div>
          <button class="primary-button primary-button--wide" :disabled="registered || sug.can_register === false || sug.needs_confirmation" @click="doRegister">{{ registered ? '预约已完成' : sug.needs_confirmation ? '等待人工确认' : sug.can_register === false ? '当前暂无可约号源' : '确认并预约挂号' }}</button>
          <small v-if="sug.can_register === false && !sug.needs_confirmation" class="booking-proof booking-proof--warning">建议重新开始分诊或联系人工服务</small>
          <small v-if="registered" class="booking-proof">预约凭证 · {{ reg.work_date }} {{ reg.period }}</small>
        </article>

        <article v-else class="safety-card">
          <div class="safety-heading"><span class="shield-mark">✓</span><div><span class="eyebrow">安全边界</span><b>每一步都有人和规则兜底</b></div></div>
          <p>系统只负责整理信息和匹配就诊资源，不替代医生诊断。</p>
          <ul><li>发现危急征象，优先提示急诊</li><li>信息不足时，要求补充或转人工</li><li>建议生成过程可回看、可追踪</li></ul>
        </article>

        <details class="profile-card">
          <summary><div><span class="profile-avatar">{{ (profile.name || '患').slice(0, 1) }}</span><div><b>{{ profile.name || '完善健康档案' }}</b><small>{{ profile.age ? `${profile.gender} · ${profile.age} 岁` : '信息将参与安全校验' }}</small></div></div><span>编辑</span></summary>
          <div class="profile-form">
            <label>姓名<el-input v-model="profile.name" /></label>
            <div class="profile-pair"><label>性别<el-radio-group v-model="profile.gender"><el-radio-button value="男">男</el-radio-button><el-radio-button value="女">女</el-radio-button></el-radio-group></label><label>年龄<el-input-number v-model="profile.age" :min="0" :max="120" /></label></div>
            <label>慢病史<el-input v-model="profile.past_history" type="textarea" :rows="2" placeholder="如：高血压 8 年" /></label>
            <label>过敏史<el-input v-model="profile.allergic_history" type="textarea" :rows="2" placeholder="如：青霉素过敏" /></label>
            <button class="primary-button primary-button--wide" :disabled="saving" @click="saveProfile">{{ saving ? '保存中…' : '保存健康档案' }}</button>
          </div>
        </details>

        <article class="aside-note"><span class="eyebrow">就诊前准备</span><b>让医生更快了解你</b><p>请尽量准备既往病史、正在服用的药物和过敏信息。</p></article>
      </aside>
    </section>
  </section>
</template>

<script setup>
import { ref, nextTick, reactive, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api'

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
const box = ref(null)
const saving = ref(false)
const profile = reactive({ name: '', gender: '男', age: null, past_history: '', allergic_history: '' })

async function loadProfile() {
  const p = await api.get('/patient/profile')
  if (p) Object.assign(profile, {
    name: p.name || '', gender: p.gender || '男', age: p.age,
    past_history: p.past_history || '', allergic_history: p.allergic_history || ''
  })
}
async function saveProfile() {
  saving.value = true
  try {
    await api.post('/patient/profile/save', null, profile)
    ElMessage.success('档案已保存')
  } finally { saving.value = false }
}
async function restoreSession() {
  const saved = localStorage.getItem('carenexus_pretriage_session')
  if (!saved) return
  try {
    const data = await api.get('/patient/session/history', { sessionId: Number(saved) })
    sessionId.value = data.sessionId
    locked.value = Number(data.locked) === 1 || data.status === 'LOCKED'
    msgs.value = (data.messages || []).map(m => ({ role: m.role === 'assistant' ? 'bot' : 'user', text: m.content }))
    if (data.suggestion) {
      sug.value = data.suggestion
      suggested.value = true
      needsReview.value = !!data.suggestion.needs_confirmation
      registered.value = Number(data.suggestion.registered) === 1 || !!data.registration
      if (data.registration) reg.value = data.registration
    }
    scroll()
  } catch { localStorage.removeItem('carenexus_pretriage_session') }
}
onMounted(async () => { await loadProfile(); await restoreSession() })

function scroll() { nextTick(() => { if (box.value) box.value.scrollTop = box.value.scrollHeight }) }
async function start() {
  const r = await api.post('/patient/session')
  sessionId.value = r.sessionId
  localStorage.setItem('carenexus_pretriage_session', String(r.sessionId))
  msgs.value = []
  locked.value = false; suggested.value = false; needsReview.value = false; registered.value = false; sug.value = {}
  ElMessage.success('会话已建立')
}
async function send() {
  const text = input.value.trim()
  if (!text || streaming.value) return
  msgs.value.push({ role: 'user', text })
  input.value = ''
  streaming.value = true
  const assistant = { role: 'bot', text: '', typing: true }
  msgs.value.push(assistant)
  scroll()
  try {
    const resp = await fetch('/v2/api/patient/chat', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sessionId: sessionId.value, msg: text })
    })
    if (!resp.ok) throw new Error(`SSE ${resp.status}`)
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buf = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += decoder.decode(value, { stream: true })
      const blocks = buf.split('\n\n'); buf = blocks.pop()
      for (const b of blocks) {
        const line = b.trim()
        if (line.startsWith('data:')) {
          const data = line.slice(5).trim()
          if (data === '[DONE]') { assistant.typing = false; continue }
          assistant.text += data
          scroll()
        }
      }
    }
    assistant.typing = false
  } catch (e) { assistant.text = '（连接中断，请检查网络后重试）'; assistant.typing = false; ElMessage.error('助手连接失败，请稍后重试') }
  streaming.value = false
}
async function doTriage() {
  const complaint = input.value.trim() || msgs.value.filter(m=>m.role==='user').map(m=>m.text).join('，')
  if (!complaint) { ElMessage.warning('请先描述症状'); return }
  try {
    sug.value = await api.post('/patient/triage', { sessionId: sessionId.value, complaint })
    suggested.value = true
    needsReview.value = !!sug.value.needs_confirmation
    locked.value = !needsReview.value || Number(sug.value.red_flag) === 1
  } catch (e) { ElMessage.error(e.response?.data?.msg || e.message || '暂时无法生成预约建议，请稍后重试') }
}
async function doRegister() {
  try {
    reg.value = await api.post('/patient/register', { suggestionId: sug.value.suggestion_id })
    registered.value = true
    ElMessage.success(`预约成功：${reg.value.work_date} ${reg.value.period}`)
  } catch (e) { ElMessage.error(e.response?.data?.msg || e.message || '预约失败，号源可能刚刚被占用') }
}
</script>
