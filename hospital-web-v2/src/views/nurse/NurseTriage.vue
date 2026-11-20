<template>
  <section class="workspace nurse-space" aria-label="护士人工导诊工作区">
    <header class="work-header nurse-header">
      <div><span class="eyebrow">护士工作台 / 人工导诊</span><h2>把需要人判断的患者，接到正确的人手里。</h2><p>低证据、异常或命中风险边界的分诊建议，会先来到这里等待核对。</p></div>
      <div class="work-header-actions"><div class="nurse-shift"><span class="status-pip"></span><div><strong>导诊服务在线</strong><small>队列每 30 秒刷新</small></div></div><button class="secondary-button" @click="load">刷新队列</button></div>
    </header>

    <section class="metric-grid metric-grid--four nurse-metrics" aria-label="导诊队列概览">
      <article class="metric-tile"><span>待处理总数</span><strong>{{ queue.length }}</strong><small>等待护士判断</small></article>
      <article class="metric-tile metric-tile--alert"><span>危急征象</span><strong>{{ redFlagCount }}</strong><small>不可直接确认普通预约</small></article>
      <article class="metric-tile"><span>低证据建议</span><strong>{{ lowEvidenceCount }}</strong><small>需要补充或核对信息</small></article>
      <article class="metric-tile metric-tile--quiet"><span>当前视图</span><strong>{{ filteredQueue.length }}</strong><small>{{ filterLabel }}</small></article>
    </section>

    <section class="nurse-layout">
      <aside class="nurse-queue surface">
        <header class="panel-head"><div><span class="eyebrow">人工队列</span><h3>待核对患者</h3></div><span class="queue-count">{{ filteredQueue.length }}</span></header>
        <div class="queue-filter nurse-filter" role="tablist" aria-label="导诊风险筛选"><button :class="{ active: filter === 'all' }" @click="filter = 'all'">全部 <small>{{ queue.length }}</small></button><button :class="{ active: filter === 'risk' }" @click="filter = 'risk'">危急 <small>{{ redFlagCount }}</small></button><button :class="{ active: filter === 'review' }" @click="filter = 'review'">待核对 <small>{{ lowEvidenceCount }}</small></button></div>
        <div class="nurse-queue-list">
          <button v-for="item in filteredQueue" :key="item.suggestion_id" :class="['nurse-case-row', { active: selected?.suggestion_id === item.suggestion_id }]" @click="selected = item">
            <span :class="['risk-mark', item.red_flag ? 'risk-mark--critical' : 'risk-mark--review']">{{ item.red_flag ? '急' : '审' }}</span>
            <span><b>{{ item.patient_name || '未命名患者' }}</b><small>{{ item.department_name || '待匹配科室' }} · {{ item.create_time || '刚刚' }}</small></span>
            <span class="case-arrow" aria-hidden="true">→</span>
          </button>
          <div v-if="!filteredQueue.length" class="empty-state"><span class="empty-icon">✓</span><b>{{ queue.length ? '当前筛选下暂无记录' : '人工队列已清空' }}</b><p>{{ queue.length ? '切换筛选查看其他待处理项。' : '新的异常分诊会自动进入这里。' }}</p></div>
        </div>
      </aside>

      <article v-if="selected" class="nurse-case surface">
        <header class="case-header"><div class="case-person"><span class="care-avatar care-avatar--patient patient-avatar--large">{{ (selected.patient_name || '患').slice(0, 1) }}</span><div><span class="eyebrow">人工核对 / #{{ selected.suggestion_id }}</span><h3>{{ selected.patient_name || '未命名患者' }}</h3><p>{{ selected.gender || '—' }} · {{ selected.age || '—' }} 岁 · {{ selected.department_name || '待匹配科室' }}</p></div></div><span :class="['risk-badge', selected.red_flag ? 'risk-badge--critical' : 'risk-badge--review']">{{ selected.red_flag ? '危急征象' : '需要核对' }}</span></header>
        <div class="case-body">
          <section class="case-section case-section--primary"><span class="eyebrow">患者原话</span><p class="case-complaint">{{ selected.last_user_message || '患者未留下可识别的主诉，请通过人工渠道补充。' }}</p></section>
          <div class="case-columns"><section class="case-section"><span class="eyebrow">系统建议</span><strong>{{ selected.department_name || '暂无科室建议' }}</strong><p>{{ selected.doctor_name || '暂无医生建议' }}</p></section><section class="case-section"><span class="eyebrow">证据置信度</span><strong>{{ confidence(selected.confidence) }}</strong><p>{{ selected.reason || '系统未提供额外说明' }}</p></section></div>
          <div class="case-alerts"><div><span>既往史</span><b>{{ selected.past_history || '暂无记录' }}</b></div><div :class="{ alert: selected.allergic_history }"><span>过敏史</span><b>{{ selected.allergic_history || '暂无记录' }}</b></div></div>
          <label class="case-note"><span>处理依据 <b>必填</b></span><textarea v-model="note" rows="4" placeholder="记录你核对了什么、补充了什么，以及为什么确认或转人工……"></textarea></label>
        </div>
        <footer class="case-actions"><span>{{ selected.red_flag ? '危急征象不可直接确认普通预约，请转急诊或人工医生处理。' : '确认后患者可继续预约；信息不足时请退回补充。' }}</span><div><button class="secondary-button" @click="resolve('REJECT')">{{ selected.red_flag ? '转急诊 / 人工' : '退回补充' }}</button><button v-if="!selected.red_flag" class="primary-button" @click="resolve('CONFIRM')">确认分诊 <span aria-hidden="true">→</span></button></div></footer>
      </article>
      <article v-else class="nurse-case surface nurse-case-empty"><div class="medical-cross" aria-hidden="true">+</div><span class="eyebrow">人工导诊台</span><h3>从左侧选择一条待处理记录</h3><p>患者原话、风险提示、系统建议和处理依据会在这里展开。</p></article>
    </section>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api'

const queue = ref([])
const selected = ref(null)
const filter = ref('all')
const note = ref('')
const redFlagCount = computed(() => queue.value.filter(item => Number(item.red_flag) === 1).length)
const lowEvidenceCount = computed(() => queue.value.filter(item => Number(item.red_flag) !== 1).length)
const filteredQueue = computed(() => filter.value === 'risk' ? queue.value.filter(item => Number(item.red_flag) === 1) : filter.value === 'review' ? queue.value.filter(item => Number(item.red_flag) !== 1) : queue.value)
const filterLabel = computed(() => ({ all: '全部待处理', risk: '危急征象', review: '低证据 / 待核对' }[filter.value]))
function confidence(value) { const number = Number(value); return Number.isFinite(number) && number > 0 ? `${Math.round(number * 100)}%` : '未提供' }
async function load() { try { queue.value = await api.get('/nurse/triage/queue'); if (selected.value) selected.value = queue.value.find(item => item.suggestion_id === selected.value.suggestion_id) || null } catch (error) { ElMessage.error(error.response?.data?.msg || error.message || '导诊队列加载失败') } }
async function resolve(action) { if (!note.value.trim()) { ElMessage.warning('请先填写处理依据'); return } try { await api.post('/nurse/triage/resolve', null, { suggestionId: selected.value.suggestion_id, action, note: note.value.trim() }); ElMessage.success(action === 'CONFIRM' ? '分诊已确认，患者可继续预约' : '已转人工 / 急诊处理'); selected.value = null; note.value = ''; await load() } catch (error) { ElMessage.error(error.response?.data?.msg || error.message || '处理失败，请稍后重试') } }
onMounted(load)
</script>
