<template>
  <section class="workspace billing-workspace" aria-label="我的账单">
    <section class="page-intro">
      <div><span class="eyebrow">MY BILLING</span><h2>费用与支付</h2><p>每一笔费用都有对应的就诊业务和支付流水。</p></div>
      <div class="billing-balance"><span>账户余额</span><strong>¥ {{ formatAmount(balance) }}</strong><button class="text-button" @click="loadBalance">刷新余额</button></div>
    </section>

    <nav class="billing-tabs" aria-label="账单状态">
      <button v-for="tab in tabs" :key="tab.value" :class="{ active: status === tab.value }" @click="switchStatus(tab.value)">{{ tab.label }}</button>
    </nav>

    <section class="billing-list">
      <article v-for="bill in bills" :key="bill.id" class="surface billing-card">
        <header><div class="billing-type"><span>{{ billIcon(bill.bill_type) }}</span><div><span class="eyebrow">{{ bill.bill_type }}</span><h3>{{ bill.description || typeLabel(bill.bill_type) }}</h3></div></div><span :class="['queue-status', bill.status === 'PAID' ? 'done' : bill.status === 'REFUNDED' ? 'waiting' : 'pending']">{{ statusLabel(bill.status) }}</span></header>
        <div class="billing-meta"><span>{{ bill.department_name || '门诊服务' }}<b v-if="bill.doctor_name"> · {{ bill.doctor_name }}医生</b></span><span>{{ bill.create_time || '—' }}</span></div>
        <footer><strong>¥ {{ formatAmount(bill.amount) }}</strong><button v-if="bill.status === 'UNPAID'" class="primary-button" @click="openPay(bill)">立即支付 <span aria-hidden="true">→</span></button><span v-else class="billing-paid">{{ bill.status === 'PAID' ? `支付完成${bill.paid_at ? ` · ${bill.paid_at}` : ''}` : '已完成退费' }}</span></footer>
      </article>
      <div v-if="!loading && !bills.length" class="surface empty-state"><span class="empty-icon">✓</span><b>当前没有{{ tabs.find(t => t.value === status)?.label || '' }}账单</b><p>完成挂号或其他门诊服务后，费用会自动出现在这里。</p></div>
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

onMounted(() => { loadBalance(); loadBills() })
async function loadBalance() { try { balance.value = Number((await api.get('/patient/payment/balance')).balance || 0) } catch (e) { ElMessage.error(e.message || '余额加载失败') } }
async function loadBills() { loading.value = true; try { bills.value = await api.get('/patient/payment/bills', { status: status.value }) } catch (e) { ElMessage.error(e.message || '账单加载失败') } finally { loading.value = false } }
function switchStatus(value) { status.value = value; loadBills() }
function openPay(bill) { selected.value = bill; channel.value = 'BALANCE'; payVisible.value = true }
async function pay() { if (!selected.value) return; paying.value = true; try { await api.post('/patient/payment/pay', null, { billId: selected.value.id, channel: channel.value }); ElMessage.success('支付成功'); payVisible.value = false; await Promise.all([loadBalance(), loadBills()]) } catch (e) { ElMessage.error(e.response?.data?.msg || e.message || '支付失败') } finally { paying.value = false } }
function formatAmount(value) { return Number(value || 0).toFixed(2) }
function typeLabel(type) { return ({ REGISTRATION: '挂号费', EXAMINATION: '检查费', PRESCRIPTION: '处方费', OTHER: '门诊费用' }[type] || '门诊费用') }
function billIcon(type) { return ({ REGISTRATION: '挂', EXAMINATION: '检', PRESCRIPTION: '药' }[type] || '费') }
function statusLabel(value) { return ({ UNPAID: '待支付', PAID: '已支付', REFUNDED: '已退款', CANCELLED: '已取消' }[value] || value) }
</script>
