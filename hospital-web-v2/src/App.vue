<template>
  <router-view v-if="$route.meta.public" />
  <div v-else class="app-shell evidence-ledger-shell" :class="{ 'nav-open': navOpen }" :data-role="role || 'workspace'">
    <a class="skip-link" href="#main-workspace">跳到主要工作区</a>
    <button v-if="navOpen" class="nav-scrim" aria-label="关闭导航" @click="navOpen = false"></button>

    <aside class="sidebar" aria-label="CareNexus 主导航">
      <div class="brand">
        <span class="brand-mark" aria-hidden="true"><i></i></span>
        <div class="brand-copy"><b>CareNexus</b><small>患者智能体</small></div>
      </div>

      <div class="nav-context">
        <span>{{ roleConfig.label }}</span>
        <strong>{{ roleConfig.description }}</strong>
        <small>{{ user.name || user.username || '当前用户' }}</small>
      </div>

      <template v-for="section in roleConfig.sections || [{ title: '工作入口', links: roleConfig.links }]" :key="section.title">
        <div class="nav-section">{{ section.title }}</div>
        <router-link v-for="item in section.links" :key="item.path" :to="item.path" class="nav-item" :class="{ active: $route.path === item.path }" @click="navOpen = false">
          <span class="nav-item-index">{{ item.tag }}</span>
          <span class="nav-item-copy"><b>{{ item.title }}</b><small>{{ item.hint }}</small></span>
        </router-link>
      </template>

      <div v-if="roleConfig.note" class="nav-note"><span class="nav-note-line" aria-hidden="true"></span><p>{{ roleConfig.note }}</p></div>
      <div class="sidebar-foot"><span><i aria-hidden="true"></i>安全会话</span><button class="logout-button" @click="logout">退出登录</button></div>
    </aside>

    <main id="main-workspace" class="main" tabindex="-1">
      <header class="topbar">
        <button class="menu-button" :aria-expanded="navOpen" :aria-label="navOpen ? '关闭导航' : '打开导航'" @click="navOpen = !navOpen"><span></span><span></span></button>
        <div class="page-head"><span class="page-kicker">CareNexus · Evidence Ledger · {{ roleConfig.label }}</span><h1>{{ currentTitle }}</h1></div>
        <div class="topbar-session"><span class="topbar-date">{{ todayLabel }}</span><span class="session-state"><i aria-hidden="true"></i>{{ roleConfig.session }}</span><div class="user-chip"><span class="user-avatar role-avatar" :data-role="role">{{ roleConfig.avatar }}</span><div><strong>{{ user.name || user.username }}</strong><small>{{ roleConfig.label }}</small></div></div></div>
      </header>
      <div ref="routeStage" class="route-stage"><router-view /></div>
    </main>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { gsap } from 'gsap'
import { getNavigation, getRoleMeta } from './config/navigation'

const route = useRoute()
const routeStage = ref(null)
const navOpen = ref(false)
const user = computed(() => {
  route.fullPath
  try { return JSON.parse(localStorage.getItem('hospital_user') || '{}') } catch { return {} }
})
const role = computed(() => user.value.role)
const roleConfig = computed(() => ({ ...getRoleMeta(role.value), ...getNavigation(role.value) }))
const currentTitle = computed(() => route.meta?.title || '智慧医院')
const todayLabel = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' }).format(new Date())
let motion

function animateRoute() {
  motion?.revert()
  nextTick(() => {
    if (!routeStage.value) return
    motion = gsap.matchMedia()
    motion.add('(prefers-reduced-motion: no-preference)', () => {
      const targets = Array.from(routeStage.value.children)
      if (targets.length) gsap.fromTo(targets, { autoAlpha: 0, y: 10 }, { autoAlpha: 1, y: 0, duration: 0.32, ease: 'power2.out', clearProps: 'all' })
    }, routeStage.value)
  })
}

onMounted(animateRoute)
watch(() => route.fullPath, animateRoute)
onUnmounted(() => motion?.revert())

function logout() {
  fetch('/v2/api/auth/logout', { method: 'POST', credentials: 'same-origin' }).finally(() => {
    try {
      const current = JSON.parse(localStorage.getItem('hospital_user') || '{}')
      const identity = current.userId || current.username
      if (identity) localStorage.removeItem(`carenexus_pretriage_session:${identity}`)
    } catch { /* 登录态损坏时仍继续清理并退出 */ }
    localStorage.removeItem('carenexus_pretriage_session')
    localStorage.removeItem('hospital_user')
    window.location.assign('/CareNexus/login')
  })
}
</script>
