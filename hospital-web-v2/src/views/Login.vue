<template>
  <main ref="page" class="login-page">
    <section class="login-story" aria-labelledby="login-story-title">
      <div class="login-story-top">
        <div class="login-brand">
          <span class="brand-mark" aria-hidden="true"><i></i></span>
          <span>CareNexus</span>
        </div>
        <span class="login-location">门诊服务中心 · 2026</span>
      </div>

      <div class="login-story-main" data-enter>
        <span class="login-kicker">一条完整的就诊链路</span>
        <h1 id="login-story-title">先听见患者，<br><em>再开始治疗。</em></h1>
        <p>把患者的描述、护士的判断、医生的记录放在同一条可追溯的就诊路径里。</p>
      </div>

      <div class="clinical-poster" aria-label="CareNexus 就诊链路示意">
        <div class="poster-heading"><span>就诊路径</span><strong>CARE PATH 01</strong></div>
        <div class="poster-line" aria-hidden="true"><i></i><b></b><i></i><b></b><i></i></div>
        <div class="poster-steps">
          <span><b>01</b><strong>患者描述</strong><small>先把不适说清楚</small></span>
          <span><b>02</b><strong>护士核对</strong><small>需要人判断时接手</small></span>
          <span><b>03</b><strong>医生接诊</strong><small>形成结构化病历</small></span>
        </div>
        <div class="poster-footer"><span><i></i> 今日门诊路径已准备</span><small>隐私保护 · 权限隔离 · 全程留痕</small></div>
      </div>

      <div class="login-story-bottom"><span>Care before code.</span><span>© CareNexus</span></div>
    </section>

    <section class="login-panel" data-enter aria-label="身份认证">
      <div class="login-panel-inner">
        <div class="login-panel-heading">
          <span class="login-panel-label">进入门诊工作台</span>
          <h2>{{ mode === 'login' ? '欢迎回来' : '建立患者档案' }}</h2>
          <p>{{ mode === 'login' ? '请选择你的工作身份，继续今天的就诊流程。' : '创建账号后，可以保存健康档案和就诊记录。' }}</p>
        </div>

        <div class="identity-strip" aria-label="支持的工作身份">
          <span><i class="identity-dot identity-dot--patient">患</i>患者</span>
          <span><i class="identity-dot identity-dot--nurse">导</i>护士</span>
          <span><i class="identity-dot identity-dot--doctor">医</i>医生</span>
          <span><i class="identity-dot identity-dot--admin">运</i>运营</span>
        </div>

        <div class="login-tabs" role="tablist" aria-label="登录或注册">
          <button :class="{ active: mode === 'login' }" role="tab" :aria-selected="mode === 'login'" @click="mode = 'login'">登录工作台</button>
          <button :class="{ active: mode === 'register' }" role="tab" :aria-selected="mode === 'register'" @click="mode = 'register'">患者注册</button>
        </div>

        <form class="login-form" @submit.prevent="submit">
          <label>用户名<input v-model="form.username" autocomplete="username" placeholder="输入用户名" required></label>
          <label>密码<div class="password-field"><input v-model="form.password" type="password" autocomplete="current-password" placeholder="输入密码" required></div></label>
          <template v-if="mode === 'register'">
            <label>姓名<input v-model="form.name" autocomplete="name" placeholder="输入真实姓名" required></label>
            <label>手机号（可选）<input v-model="form.phone" autocomplete="tel" placeholder="用于就诊联系"></label>
          </template>
          <button class="login-submit" type="submit" :disabled="loading">
            <span>{{ loading ? '正在验证…' : mode === 'login' ? '进入我的工作台' : '创建患者账号' }}</span>
            <b aria-hidden="true">→</b>
          </button>
        </form>

        <div v-if="showDemoAccounts" class="demo-accounts"><span>本地演示账号</span><code>admin</code><code>nurse</code><code>doctor</code><code>patient</code><small>仅开发环境可用</small></div>
        <p class="login-privacy"><i aria-hidden="true"></i> 医疗信息仅在对应角色权限范围内可见</p>
      </div>
    </section>
  </main>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { gsap } from 'gsap'
import { api } from '../api'

const router = useRouter()
const page = ref(null)
const mode = ref('login')
const loading = ref(false)
const showDemoAccounts = import.meta.env.DEV
const form = reactive({ username: '', password: '', name: '', phone: '' })
let motion

onMounted(() => {
  motion = gsap.matchMedia()
  motion.add('(prefers-reduced-motion: no-preference)', () => {
    const targets = gsap.utils.toArray('[data-enter]', page.value)
    gsap.fromTo(targets, { autoAlpha: 0, y: 14 }, { autoAlpha: 1, y: 0, duration: 0.62, stagger: 0.08, ease: 'power2.out', clearProps: 'all' })
  }, page.value)
})
onUnmounted(() => motion?.revert())

async function submit() {
  loading.value = true
  try {
    const user = await api.post(`/auth/${mode.value}`, null, form)
    const { token: _token, ...safeUser } = user
    localStorage.setItem('hospital_user', JSON.stringify(safeUser))
    ElMessage.success(mode.value === 'login' ? '已进入工作台' : '患者档案已建立')
    router.replace(user.role === 'admin' ? '/admin/dashboard' : user.role === 'nurse' ? '/nurse/triage' : user.role === 'doctor' ? '/doctor/console' : '/patient/pre')
  } catch (e) { ElMessage.error(e.response?.data?.msg || e.message || '操作失败，请检查账号信息') }
  finally { loading.value = false }
}
</script>
