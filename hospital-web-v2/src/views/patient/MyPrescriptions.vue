<template>
  <section class="workspace records-workspace" aria-label="我的处方">
    <section class="page-intro"><div><span class="eyebrow">MY PRESCRIPTIONS</span><h2>我的处方</h2><p>查看药品、用法、费用和配药进度，避免重复询问门诊人员。</p></div><button class="secondary-button" @click="load">刷新处方</button></section>
    <section class="billing-list">
      <article v-for="rx in prescriptions" :key="rx.id" class="surface billing-card">
        <header><div class="billing-type"><span>药</span><div><span class="eyebrow">RX / #{{ rx.id }}</span><h3>{{ rx.doctor_name || '门诊处方' }}</h3></div></div><span :class="['queue-status', rx.status === 'DISPENSED' ? 'done' : rx.bill_status === 'UNPAID' ? 'pending' : 'waiting']">{{ statusLabel(rx) }}</span></header>
        <div class="prescription-progress"><span :class="{ active: true }">开方</span><i></i><span :class="{ active: rx.bill_status === 'PAID' || rx.status === 'DISPENSED' }">缴费</span><i></i><span :class="{ active: rx.status === 'DISPENSED' }">取药</span></div>
        <div class="rx-items"><div v-for="item in (rx.items || [])" :key="item.id"><strong>{{ item.drug_name }}</strong><span>{{ item.dosage }} · {{ item.frequency }} · {{ item.days }} 天 · {{ item.quantity }} 份</span></div></div>
        <footer><strong>¥ {{ amount(rx.total_amount) }}</strong><router-link v-if="rx.bill_status === 'UNPAID'" class="primary-button" to="/patient/bills">去缴费 →</router-link><span v-else-if="rx.status === 'DISPENSED'" class="billing-paid">已发药，请到药房取药</span><span v-else>费用已支付，等待药房配药</span></footer>
      </article>
      <div v-if="!loading && !prescriptions.length" class="surface empty-state"><span class="empty-icon">✓</span><b>还没有处方</b><p>医生开具处方后，会显示在这里。</p></div>
    </section>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api'

const prescriptions = ref([])
const loading = ref(false)
async function load() { loading.value = true; try { prescriptions.value = await api.get('/patient/prescriptions') } catch (e) { ElMessage.error(e.response?.data?.msg || e.message || '处方加载失败') } finally { loading.value = false } }
function statusLabel(rx) { if (rx.status === 'DISPENSED') return '已发药'; if (rx.bill_status === 'UNPAID') return '待缴费'; return '配药中' }
function amount(v) { return Number(v || 0).toFixed(2) }
onMounted(load)
</script>

<style scoped>
.prescription-progress { display: flex; align-items: center; gap: 8px; margin: 14px 0; color: var(--ink-soft); font-size: 12px; }
.prescription-progress i { flex: 0 0 30px; height: 1px; background: var(--line); }
.prescription-progress span.active { color: var(--teal-dark); font-weight: 700; }
.rx-items { display: grid; gap: 8px; padding: 12px 0; border-top: 1px solid var(--line); border-bottom: 1px solid var(--line); }
.rx-items div { display: flex; justify-content: space-between; gap: 16px; font-size: 12px; }
.rx-items span { color: var(--ink-soft); text-align: right; }
</style>
