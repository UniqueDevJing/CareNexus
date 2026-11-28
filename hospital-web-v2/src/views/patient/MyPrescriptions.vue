<template>
  <section class="workspace records-workspace" aria-label="我的处方">
    <section class="page-intro"><div><span class="eyebrow">用药记录 · My prescriptions</span><h2>我的处方</h2><p>查看药品、用法、费用和配药进度，避免重复询问门诊人员。</p></div><button class="secondary-button" @click="load">刷新处方</button></section>
    <section class="billing-list">
      <div v-if="loading" class="surface records-state" role="status">正在同步处方与用药明细…</div>
      <div v-else-if="error" class="surface records-state records-state--error" role="alert"><strong>处方资料暂时无法加载</strong><span>{{ error }}</span><button class="text-button" type="button" @click="load">重新加载</button></div>
      <article v-for="rx in prescriptions" :key="rx.id" class="surface billing-card">
        <header><div class="billing-type"><span>药</span><div><span class="eyebrow">RX / #{{ rx.id }}</span><h3>{{ rx.doctor_name || '门诊处方' }}</h3></div></div><span :class="['queue-status', rx.status === 'DISPENSED' ? 'done' : rx.bill_status === 'UNPAID' ? 'pending' : 'waiting']">{{ statusLabel(rx) }}</span></header>
        <div class="prescription-progress"><span :class="{ active: true }">开方</span><i></i><span :class="{ active: rx.bill_status === 'PAID' || rx.status === 'DISPENSED' }">缴费</span><i></i><span :class="{ active: rx.status === 'DISPENSED' }">取药</span></div>
        <div class="rx-summary"><span>{{ rx.items?.length || 0 }} 项药品 · {{ rx.note || '按医生处方使用' }}</span><button class="text-button" type="button" @click="toggle(rx.id)">{{ expanded === rx.id ? '收起详情' : '查看完整处方 →' }}</button></div>
        <footer><strong>¥ {{ amount(rx.total_amount) }}</strong><router-link v-if="rx.bill_status === 'UNPAID'" class="primary-button" to="/patient/bills">去缴费 →</router-link><span v-else-if="rx.status === 'DISPENSED'" class="billing-paid">已发药，请到药房取药</span><span v-else>费用已支付，等待药房配药</span></footer>
        <section v-if="expanded === rx.id" class="prescription-detail" aria-live="polite">
          <div class="inline-detail-head"><span class="eyebrow">处方详情 · Prescription detail</span><strong>{{ statusLabel(rx) }}</strong></div>
          <dl class="detail-grid"><div><dt>开方医生</dt><dd>{{ rx.doctor_name || '门诊医生' }}</dd></div><div><dt>开方时间</dt><dd>{{ rx.create_time || '—' }}</dd></div><div><dt>费用状态</dt><dd>{{ billLabel(rx.bill_status) }}</dd></div><div><dt>处方状态</dt><dd>{{ statusLabel(rx) }}</dd></div></dl>
          <div class="rx-items"><span class="eyebrow">药品明细 · Medications</span><div v-for="item in (rx.items || [])" :key="item.id"><strong>{{ item.drug_name }}</strong><span>{{ item.dosage }} · {{ item.frequency }} · {{ item.days }} 天 · {{ item.quantity }} 份 · ¥{{ amount(item.amount) }}</span></div><p v-if="!rx.items?.length" class="detail-empty">处方明细尚未同步，请刷新后重试。</p></div>
          <p v-if="rx.note" class="prescription-note">用药说明：{{ rx.note }}</p>
        </section>
      </article>
      <div v-if="!loading && !error && !prescriptions.length" class="surface empty-state"><span class="empty-icon">✓</span><b>还没有处方</b><p>医生开具处方后，会显示在这里。</p></div>
    </section>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api'

const prescriptions = ref([])
const loading = ref(false)
const error = ref('')
const expanded = ref(null)
async function load() { loading.value = true; error.value = ''; expanded.value = null; try { prescriptions.value = await api.get('/patient/prescriptions') } catch (e) { error.value = e.response?.data?.msg || e.message || '处方加载失败'; prescriptions.value = []; ElMessage.error(error.value) } finally { loading.value = false } }
function toggle(id) { expanded.value = expanded.value === id ? null : id }
function statusLabel(rx) { if (rx.status === 'DISPENSED') return '已发药'; if (rx.bill_status === 'UNPAID') return '待缴费'; return '配药中' }
function billLabel(value) { return value === 'PAID' ? '已支付' : value === 'REFUNDED' ? '已退款' : '待支付' }
function amount(v) { return Number(v || 0).toFixed(2) }
onMounted(load)
</script>

<style scoped>
.prescription-progress { display: flex; align-items: center; gap: 8px; margin: 14px 0; color: var(--ink-soft); font-size: 12px; }
.prescription-progress i { flex: 0 0 30px; height: 1px; background: var(--line); }
.prescription-progress span.active { color: var(--teal-dark); font-weight: 700; }
.rx-summary { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 11px 0; color: var(--ink-soft); border-top: 1px solid var(--line); border-bottom: 1px solid var(--line); font-size: 11px; }
.rx-summary .text-button { padding: 0; white-space: nowrap; }
.prescription-detail { display: grid; gap: 14px; padding-top: 15px; }
.inline-detail-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.inline-detail-head strong { color: var(--teal-dark); font-size: 13px; }
.detail-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px 18px; margin: 0; }
.detail-grid div { min-width: 0; }
.detail-grid dt { color: var(--ink-soft); font-size: 10px; }
.detail-grid dd { margin: 4px 0 0; color: var(--ink); font-size: 12px; word-break: break-word; }
.rx-items { display: grid; gap: 8px; padding: 14px 0 0; border-top: 1px solid var(--line); }
.rx-items div { display: flex; justify-content: space-between; gap: 16px; font-size: 12px; }
.rx-items span { color: var(--ink-soft); text-align: right; }
.rx-items .eyebrow { color: var(--teal-dark); text-align: left; }
.detail-empty { margin: 0; color: var(--ink-soft); font-size: 11px; }
.prescription-note { margin: 0; padding: 11px 12px; color: var(--ink-soft); border-left: 3px solid var(--gold); background: var(--surface-muted); font-size: 11px; line-height: 1.6; }
@media (max-width: 600px) { .rx-summary { align-items: flex-start; flex-direction: column; }.detail-grid { gap: 10px; }.rx-items div { align-items: flex-start; flex-direction: column; gap: 3px; }.rx-items span { text-align: left; } }
</style>
