<template>
  <section class="workspace billing-workspace" aria-label="我的账单">
    <section class="page-intro">
      <div><span class="eyebrow">费用记录 · My billing</span><h2>费用与支付</h2><p>每一笔费用都有对应的就诊业务和支付流水。</p></div>
      <div class="billing-balance"><span>账户余额</span><strong>¥ {{ formatAmount(balance) }}</strong><button class="text-button" @click="loadBalance">刷新余额</button></div>
    </section>

    <nav class="billing-tabs" aria-label="账单状态">
      <button v-for="tab in tabs" :key="tab.value" :class="{ active: status === tab.value }" @click="switchStatus(tab.value)">{{ tab.label }}</button>
    </nav>

    <section class="billing-list">
      <div v-if="loading" class="surface records-state" role="status">正在同步账单与支付流水…</div>
      <div v-else-if="error" class="surface records-state records-state--error" role="alert"><strong>账单暂时无法加载</strong><span>{{ error }}</span><button class="text-button" type="button" @click="loadBills">重新加载</button></div>
      <article v-for="bill in bills" :key="bill.id" class="surface billing-card">
        <header><button class="billing-summary-button" type="button" :aria-expanded="expandedId === bill.id" @click="toggleDetail(bill)"><span class="billing-type"><span>{{ billIcon(bill.bill_type) }}</span><span><span class="eyebrow">{{ bill.bill_type }} · #{{ bill.id }}</span><strong>{{ bill.description || typeLabel(bill.bill_type) }}</strong></span></span><span class="billing-summary-arrow" aria-hidden="true">{{ expandedId === bill.id ? '收起' : '详情' }} ⌄</span></button><span :class="['queue-status', bill.status === 'PAID' ? 'done' : bill.status === 'REFUNDED' ? 'waiting' : 'pending']">{{ statusLabel(bill.status) }}</span></header>
        <div class="billing-meta"><span>{{ bill.department_name || '门诊服务' }}<b v-if="bill.doctor_name"> · {{ bill.doctor_name }}医生</b></span><span>{{ bill.create_time || '—' }}</span></div>
        <footer><strong>¥ {{ formatAmount(bill.amount) }}</strong><button v-if="bill.status === 'UNPAID'" class="primary-button" @click="openPay(bill)">立即支付 <span aria-hidden="true">→</span></button><span v-else class="billing-paid">{{ bill.status === 'PAID' ? `支付完成${bill.paid_at ? ` · ${bill.paid_at}` : ''}` : '已完成退费' }}</span></footer>
        <section v-if="expandedId === bill.id" class="record-inline-detail" aria-live="polite">
          <div v-if="detailLoading" class="detail-loading">正在读取完整账单…</div>
          <div v-else-if="detailError" class="detail-loading detail-loading--error">{{ detailError }} <button class="text-button" type="button" @click="loadBillDetail(bill.id)">重试</button></div>
          <template v-else-if="activeDetail">
            <div class="inline-detail-head"><span class="eyebrow">支付流水 · Payment trace</span><strong>{{ activeDetail.status === 'PAID' ? '支付已落账' : statusLabel(activeDetail.status) }}</strong></div>
            <dl class="detail-grid"><div><dt>业务类型</dt><dd>{{ typeLabel(activeDetail.bill_type) }}</dd></div><div><dt>业务编号</dt><dd>#{{ activeDetail.source_id || '—' }}</dd></div><div><dt>账单生成</dt><dd>{{ activeDetail.create_time || '—' }}</dd></div><div><dt>支付完成</dt><dd>{{ activeDetail.paid_at || '尚未支付' }}</dd></div></dl>
            <div class="payment-trace"><span class="eyebrow">流水记录</span><template v-if="activeDetail.payments?.length"><div v-for="payment in activeDetail.payments" :key="payment.id" class="payment-trace-row"><strong>{{ payment.transaction_type === 'REFUND' ? '退款' : '支付' }}</strong><span>{{ payment.channel }} · {{ payment.status }}</span><b>¥ {{ formatAmount(payment.amount) }}</b><small>{{ payment.created_at || '—' }}</small></div></template><p v-else class="detail-empty">暂无支付流水，完成支付后会在此保留凭证。</p></div>
          </template>
        </section>
      </article>
      <div v-if="!loading && !error && !bills.length" class="surface empty-state"><span class="empty-icon">✓</span><b>当前没有{{ tabs.find(t => t.value === status)?.label || '' }}账单</b><p>完成挂号或其他门诊服务后，费用会自动出现在这里。</p></div>
    </section>

    <el-dialog v-model="payVisible" title="确认支付" width="430px">
      <template v-if="selected">
        <div class="billing-dialog-summary"><span>{{ selected.description || typeLabel(selected.bill_type) }}</span><strong>¥ {{ formatAmount(selected.amount) }}</strong></div>
        <p class="billing-dialog-note">余额支付会立即扣减账户余额并生成不可抵赖的支付流水。微信、支付宝和银行卡需要后端支付网关回调后才能确认。</p>
        <el-radio-group v-model="channel" class="billing-channel-list">
          <el-radio-button value="BALANCE">余额支付 · ¥{{ formatAmount(balance) }}</el-radio-button>
          <el-radio-button value="WECHAT" disabled>微信支付 · 待接入</el-radio-button>
          <el-radio-button value="ALIPAY" disabled>支付宝 · 待接入</el-radio-button>
        </el-radio-group>
        <p v-if="channel === 'BALANCE' && Number(balance) < Number(selected.amount)" class="billing-warning">余额不足，请联系门诊服务台充值或更换已接入的支付方式。</p>
      </template>
      <template #footer><button class="secondary-button" @click="payVisible = false">取消</button><button class="primary-button" :disabled="paying || channel !== 'BALANCE' || Number(balance) < Number(selected?.amount || 0)" @click="pay">{{ paying ? '支付中…' : '确认支付' }}</button></template>
    </el-dialog>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api'

const tabs = [{ value: 'UNPAID', label: '待支付' }, { value: 'PAID', label: '已支付' }, { value: 'REFUNDED', label: '已退款' }]
const status = ref('UNPAID')
const bills = ref([])
const balance = ref(0)
const loading = ref(false)
const paying = ref(false)
const payVisible = ref(false)
const selected = ref(null)
const channel = ref('BALANCE')
const error = ref('')
const expandedId = ref(null)
const activeDetail = ref(null)
const detailLoading = ref(false)
const detailError = ref('')
let detailSequence = 0

onMounted(() => { loadBalance(); loadBills() })
async function loadBalance() { try { balance.value = Number((await api.get('/patient/payment/balance')).balance || 0) } catch (e) { balance.value = 0; ElMessage.error(e.response?.data?.msg || e.message || '余额加载失败') } }
async function loadBills() { loading.value = true; error.value = ''; expandedId.value = null; activeDetail.value = null; try { bills.value = await api.get('/patient/payment/bills', { status: status.value }) } catch (e) { error.value = e.response?.data?.msg || e.message || '账单加载失败'; bills.value = [] } finally { loading.value = false } }
function switchStatus(value) { status.value = value; loadBills() }
function openPay(bill) { selected.value = bill; channel.value = 'BALANCE'; payVisible.value = true }
function toggleDetail(bill) { if (expandedId.value === bill.id) { expandedId.value = null; activeDetail.value = null; return } expandedId.value = bill.id; loadBillDetail(bill.id) }
async function loadBillDetail(id) { const sequence = ++detailSequence; detailLoading.value = true; detailError.value = ''; activeDetail.value = null; try { const result = await api.get(`/patient/payment/bills/${id}`); if (sequence === detailSequence && expandedId.value === id) activeDetail.value = result } catch (e) { if (sequence === detailSequence) detailError.value = e.response?.data?.msg || e.message || '账单详情加载失败' } finally { if (sequence === detailSequence) detailLoading.value = false } }
async function pay() { if (!selected.value) return; paying.value = true; try { await api.post('/patient/payment/pay', null, { billId: selected.value.id, channel: channel.value }); ElMessage.success('支付成功'); payVisible.value = false; await Promise.all([loadBalance(), loadBills()]) } catch (e) { ElMessage.error(e.response?.data?.msg || e.message || '支付失败') } finally { paying.value = false } }
function formatAmount(value) { return Number(value || 0).toFixed(2) }
function typeLabel(type) { return ({ REGISTRATION: '挂号费', EXAMINATION: '检查费', PRESCRIPTION: '处方费', OTHER: '门诊费用' }[type] || '门诊费用') }
function billIcon(type) { return ({ REGISTRATION: '挂', EXAMINATION: '检', PRESCRIPTION: '药' }[type] || '费') }
function statusLabel(value) { return ({ UNPAID: '待支付', PAID: '已支付', REFUNDED: '已退款', CANCELLED: '已取消' }[value] || value) }
</script>

<style scoped>
.billing-summary-button { display: flex; min-width: 0; flex: 1; align-items: center; padding: 0; color: inherit; border: 0; background: transparent; text-align: left; cursor: pointer; }
.billing-summary-button:focus-visible { outline: 2px solid var(--gold); outline-offset: 3px; border-radius: 6px; }
.billing-summary-button .billing-type { display: flex; align-items: center; justify-content: flex-start; }
.billing-summary-button .billing-type > span:last-child { display: grid; gap: 4px; min-width: 0; }
.billing-summary-button strong { color: var(--ink); font-size: 17px; font-weight: 600; }
.billing-summary-arrow { flex: 0 0 auto; color: var(--teal-dark); font-size: 11px; font-weight: 600; }
.record-inline-detail { display: grid; gap: 14px; padding: 16px 0 2px; border-top: 1px solid var(--line); }
.inline-detail-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.inline-detail-head strong { color: var(--teal-dark); font-size: 13px; }
.detail-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px 18px; margin: 0; }
.detail-grid div { min-width: 0; }
.detail-grid dt { color: var(--ink-soft); font-size: 10px; }
.detail-grid dd { margin: 4px 0 0; color: var(--ink); font-size: 12px; word-break: break-word; }
.payment-trace { display: grid; gap: 8px; padding-top: 12px; border-top: 1px solid var(--line); }
.payment-trace-row { display: grid; grid-template-columns: 52px minmax(0, 1fr) auto auto; align-items: center; gap: 10px; padding: 10px 12px; border-radius: 8px; background: var(--surface-muted); font-size: 11px; }
.payment-trace-row span, .payment-trace-row small { color: var(--ink-soft); }
.payment-trace-row b { color: var(--ink); }
.payment-trace-row small { text-align: right; }
.detail-loading { color: var(--ink-soft); font-size: 12px; line-height: 1.7; }
.detail-loading--error { color: var(--rust); }
.detail-empty { margin: 0; color: var(--ink-soft); font-size: 11px; }
@media (max-width: 600px) { .payment-trace-row { grid-template-columns: 1fr auto; }.payment-trace-row span { grid-column: 1; }.payment-trace-row small { grid-column: 1 / -1; text-align: left; }.detail-grid { gap: 10px; } }
</style>
