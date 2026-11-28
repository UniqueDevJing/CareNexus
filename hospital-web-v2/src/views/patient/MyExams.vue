<template>
  <section class="workspace records-workspace" aria-label="我的检查">
    <section class="page-intro"><div><span class="eyebrow">检查记录 · My exams</span><h2>我的检查</h2><p>检查单、缴费状态和结果集中在一处，结果出来后再查看详细内容。</p></div><button class="secondary-button" @click="load">刷新检查</button></section>
    <section class="billing-list">
      <div v-if="loading" class="surface records-state" role="status">正在同步检查单与结果…</div>
      <div v-else-if="error" class="surface records-state records-state--error" role="alert"><strong>检查资料暂时无法加载</strong><span>{{ error }}</span><button class="text-button" type="button" @click="load">重新加载</button></div>
      <article v-for="exam in exams" :key="exam.id" class="surface billing-card">
        <header><div class="billing-type"><span>检</span><div><span class="eyebrow">检查单 · #{{ exam.id }}</span><h3>{{ exam.item_name }}</h3></div></div><span :class="['queue-status', statusClass(exam)]">{{ statusLabel(exam) }}</span></header>
        <div class="billing-meta"><span>{{ exam.doctor_name || '门诊医生' }} · {{ exam.create_time || '—' }}</span><span>¥ {{ amount(exam.fee) }}</span></div>
        <footer><span>{{ exam.bill_status === 'UNPAID' ? '完成缴费后进入检查流程' : exam.status === 'RESULT_READY' ? '结果已归档，可查看检查所见' : '检查单已提交' }}</span><router-link v-if="exam.bill_status === 'UNPAID'" class="primary-button" to="/patient/bills">去缴费 →</router-link><button class="secondary-button" type="button" @click="toggle(exam.id)">{{ expanded === exam.id ? '收起详情' : '查看详情' }}</button></footer>
        <section v-if="expanded === exam.id" class="exam-detail" aria-live="polite">
          <div class="inline-detail-head"><span class="eyebrow">检查详情 · Examination detail</span><strong>{{ statusLabel(exam) }}</strong></div>
          <dl class="detail-grid"><div><dt>检查项目</dt><dd>{{ exam.item_name || '—' }}</dd></div><div><dt>项目编码</dt><dd>{{ exam.item_code || '未登记' }}</dd></div><div><dt>开单时间</dt><dd>{{ exam.create_time || '—' }}</dd></div><div><dt>结果时间</dt><dd>{{ exam.result_time || '尚未出结果' }}</dd></div><div><dt>费用状态</dt><dd>{{ billLabel(exam.bill_status) }}</dd></div><div><dt>执行人员</dt><dd>{{ exam.performed_by || '待检查人员回填' }}</dd></div></dl>
          <div class="exam-result"><span class="eyebrow">检查结果 · Result</span><p>{{ exam.result_text || (exam.status === 'RESULT_READY' ? '暂无结果文本，请联系门诊服务台核对。' : '检查尚未完成，结果生成后会自动归档到这里。') }}</p></div>
        </section>
      </article>
      <div v-if="!loading && !error && !exams.length" class="surface empty-state"><span class="empty-icon">✓</span><b>还没有检查单</b><p>医生开具检查后，会显示在这里。</p></div>
    </section>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api'

const exams = ref([])
const expanded = ref(null)
const loading = ref(false)
const error = ref('')
async function load() { loading.value = true; error.value = ''; expanded.value = null; try { exams.value = await api.get('/patient/exam-orders') } catch (e) { error.value = e.response?.data?.msg || e.message || '检查加载失败'; exams.value = []; ElMessage.error(error.value) } finally { loading.value = false } }
function toggle(id) { expanded.value = expanded.value === id ? null : id }
function statusLabel(exam) { if (exam.status === 'RESULT_READY') return '已出结果'; if (exam.bill_status === 'UNPAID') return '待缴费'; if (exam.status === 'SAMPLE_COLLECTED') return '检验中'; return '已提交' }
function statusClass(exam) { return exam.status === 'RESULT_READY' ? 'done' : exam.bill_status === 'UNPAID' ? 'pending' : 'waiting' }
function billLabel(value) { return value === 'PAID' ? '已支付' : value === 'REFUNDED' ? '已退款' : '待支付' }
function amount(v) { return Number(v || 0).toFixed(2) }
onMounted(load)
</script>

<style scoped>
.exam-detail { display: grid; gap: 14px; padding-top: 15px; border-top: 1px solid var(--line); }
.inline-detail-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.inline-detail-head strong { color: var(--teal-dark); font-size: 13px; }
.detail-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px 18px; margin: 0; }
.detail-grid div { min-width: 0; }
.detail-grid dt { color: var(--ink-soft); font-size: 10px; }
.detail-grid dd { margin: 4px 0 0; color: var(--ink); font-size: 12px; word-break: break-word; }
.exam-result { display: grid; gap: 7px; padding: 14px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface-muted); color: var(--ink); line-height: 1.7; }
.exam-result p { margin: 0; white-space: pre-wrap; }
@media (max-width: 600px) { .detail-grid { gap: 10px; } }
</style>
