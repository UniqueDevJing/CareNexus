<template>
  <section class="workspace records-workspace" aria-label="我的检查">
    <section class="page-intro"><div><span class="eyebrow">MY EXAMS</span><h2>我的检查</h2><p>检查单、缴费状态和结果集中在一处，结果出来后再查看详细内容。</p></div><button class="secondary-button" @click="load">刷新检查</button></section>
    <section class="billing-list">
      <article v-for="exam in exams" :key="exam.id" class="surface billing-card">
        <header><div class="billing-type"><span>检</span><div><span class="eyebrow">EXAM / #{{ exam.id }}</span><h3>{{ exam.item_name }}</h3></div></div><span :class="['queue-status', statusClass(exam)]">{{ statusLabel(exam) }}</span></header>
        <div class="billing-meta"><span>{{ exam.doctor_name || '门诊医生' }} · {{ exam.create_time || '—' }}</span><span>¥ {{ amount(exam.fee) }}</span></div>
        <footer><span>{{ exam.bill_status === 'UNPAID' ? '完成缴费后进入检查流程' : exam.status === 'RESULT_READY' ? '结果已归档，可查看检查所见' : '检查单已提交' }}</span><router-link v-if="exam.bill_status === 'UNPAID'" class="primary-button" to="/patient/bills">去缴费 →</router-link><button v-else-if="exam.status === 'RESULT_READY'" class="secondary-button" @click="toggle(exam.id)">{{ expanded === exam.id ? '收起结果' : '查看结果' }}</button></footer>
        <div v-if="expanded === exam.id" class="exam-result"><b>检查结果</b><p>{{ exam.result_text || '暂无结果文本' }}</p></div>
      </article>
      <div v-if="!loading && !exams.length" class="surface empty-state"><span class="empty-icon">✓</span><b>还没有检查单</b><p>医生开具检查后，会显示在这里。</p></div>
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
async function load() { loading.value = true; try { exams.value = await api.get('/patient/exam-orders') } catch (e) { ElMessage.error(e.response?.data?.msg || e.message || '检查加载失败') } finally { loading.value = false } }
function toggle(id) { expanded.value = expanded.value === id ? null : id }
function statusLabel(exam) { if (exam.status === 'RESULT_READY') return '已出结果'; if (exam.bill_status === 'UNPAID') return '待缴费'; if (exam.status === 'SAMPLE_COLLECTED') return '检验中'; return '已提交' }
function statusClass(exam) { return exam.status === 'RESULT_READY' ? 'done' : exam.bill_status === 'UNPAID' ? 'pending' : 'waiting' }
function amount(v) { return Number(v || 0).toFixed(2) }
onMounted(load)
</script>

<style scoped>
.exam-result { margin-top: 14px; padding: 14px; border-radius: 12px; background: var(--surface-muted); color: var(--ink); line-height: 1.7; }
.exam-result p { margin: 6px 0 0; white-space: pre-wrap; }
</style>
