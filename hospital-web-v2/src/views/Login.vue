<template>
  <main ref="page" class="login-page">
    <div class="login-bg-grid" aria-hidden="true"></div>
    <div class="login-orbit login-orbit--one" aria-hidden="true"></div>
    <div class="login-orbit login-orbit--two" aria-hidden="true"></div>
    <section class="login-stage" aria-labelledby="login-title">
      <div class="login-intro" data-enter>
        <div class="login-brand">
          <span class="brand-mark" aria-hidden="true"><i></i></span>
          <span>CareNexus</span>
        </div>
        <p class="login-kicker">三种身份，一套就诊链路</p>
        <h1 id="login-title">AI 智慧医院工作台</h1>
        <p>患者预问诊、医生接诊与运营观测，共用一条安全、可追踪的就诊链路。</p>
      </div>

      <section class="login-panel" data-enter aria-label="身份认证">
        <div class="login-card">
          <div class="login-card-head">
            <span class="status-dot"></span>
            <div><h2>{{ mode === 'login' ? '进入工作台' : '创建患者账号' }}</h2><p>身份和数据权限由角色自动隔离</p></div>
          </div>
          <el-tabs v-model="mode" stretch>
            <el-tab-pane label="登录" name="login" />
            <el-tab-pane label="患者注册" name="register" />
          </el-tabs>
          <el-form label-position="top" @submit.prevent="submit">
            <el-form-item label="用户名"><el-input v-model="form.username" autocomplete="username" placeholder="输入用户名" /></el-form-item>
            <el-form-item label="密码"><el-input v-model="form.password" type="password" autocomplete="current-password" show-password placeholder="输入密码" /></el-form-item>
            <template v-if="mode==='register'">
              <el-form-item label="姓名"><el-input v-model="form.name" autocomplete="name" placeholder="输入真实姓名" /></el-form-item>
              <el-form-item label="手机号（可选）"><el-input v-model="form.phone" autocomplete="tel" placeholder="用于就诊联系" /></el-form-item>
            </template>
            <el-button type="primary" native-type="submit" :loading="loading" class="login-submit">
              {{ mode==='login' ? '进入工作台' : '创建并进入' }}
            </el-button>
          </el-form>
          <div v-if="showDemoAccounts" class="demo-accounts"><span>本地演示账号</span><code>admin</code><code>doctor</code><code>patient</code><small>仅开发环境可用</small></div>
        </div>
      </section>
      <p class="security-note">JWT 安全会话 · 角色权限隔离 · 医疗数据归属校验</p>
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
    if (!targets.length) return
    gsap.fromTo(targets, { autoAlpha: 0, y: 18 },
      { autoAlpha: 1, y: 0, duration: 0.72, stagger: 0.1, ease: 'power3.out', clearProps: 'all' })
  }, page.value)
})
onUnmounted(() => motion?.revert())

async function submit() {
  loading.value = true
  try {
    const user = await api.post(`/auth/${mode.value}`, null, form)
    const { token: _token, ...safeUser } = user
    localStorage.setItem('hospital_user', JSON.stringify(safeUser))
    ElMessage.success('登录成功')
    router.replace(user.role === 'admin' ? '/admin/dashboard' : user.role === 'doctor' ? '/doctor/console' : '/patient/pre')
  } catch (e) { ElMessage.error(e.response?.data?.msg || e.message || '操作失败') }
  finally { loading.value = false }
}
</script>
