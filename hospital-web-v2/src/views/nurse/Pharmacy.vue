<template>
  <section class="workspace nurse-space" aria-label="药房配药工作区" :inert="busy">
    <header class="work-header nurse-header"><div><span class="eyebrow">护士工作台 / 药房配药</span><h2>先核对支付，再完成发药。</h2><p>只展示已支付且仍待配药的处方，避免把未支付医嘱误送入药房。</p></div><button class="secondary-button" @click="load">刷新队列</button></header>
    <section class="metric-grid metric-grid--three"><article class="metric-tile"><span>待配药</span><strong>{{ queue.length }}</strong><small>已支付处方</small></article><article class="metric-tile metric-tile--quiet"><span>今日处理</span><strong>{{ processed }}</strong><small>本页面会话内完成</small></article><article class="metric-tile"><span>安全边界</span><strong>PAY</strong><small>未支付不会进入队列</small></article></section>
    <section class="billing-list"><article v-for="rx in queue" :key="rx.prescription_id" class="surface billing-card"><header><div class="billing-type"><span>药</span><div><span class="eyebrow">RX / #{{ rx.prescription_id }}</span><h3>{{ rx.patient_name || '患者' }}</h3></div></div><span class="queue-status pending">待配药</span></header><div class="billing-meta"><span>{{ rx.doctor_name || '门诊医生' }} · {{ rx.create_time || '—' }}</span><span>¥ {{ amount(rx.total_amount) }}</span></div><footer><span>支付状态：{{ rx.bill_status === 'PAID' ? '已支付' : '待支付' }}</span><button class="primary-button" :disabled="busy" @click="dispense(rx)">完成配药 →</button></footer></article><div v-if="!queue.length" class="surface empty-state"><span class="empty-icon">✓</span><b>药房队列已清空</b><p>新的已支付处方会自动出现在这里。</p></div></section>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api'

const queue = ref([])
const processed = ref(0)
const busy = ref(false)
async function load() { try { queue.value = await api.get('/nurse/pharmacy/queue') } catch (e) { ElMessage.error(e.response?.data?.msg || e.message || '药房队列加载失败') } }
async function dispense(rx) { busy.value = true; try { await api.post('/nurse/pharmacy/dispense', { prescriptionId: rx.prescription_id }); processed.value++; ElMessage.success('已完成配药'); await load() } catch (e) { ElMessage.error(e.response?.data?.msg || e.message || '配药失败') } finally { busy.value = false } }
function amount(v) { return Number(v || 0).toFixed(2) }
onMounted(load)
</script>
