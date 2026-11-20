<template>
  <router-view v-if="$route.meta.public" />
  <div v-else ref="shell" class="app-shell" :class="{ 'nav-open': navOpen }" :data-role="role || 'workspace'">
    <a class="skip-link" href="#main-workspace">跳到主要工作区</a>
    <button v-if="navOpen" class="nav-scrim" aria-label="关闭导航" @click="navOpen = false"></button>
    <aside class="sidebar" aria-label="主导航">
      <div class="brand">
        <span class="brand-mark" aria-hidden="true"><i></i></span>
        <div class="brand-copy">
          <b>CareNexus</b>
          <small>就诊服务工作台</small>
        </div>
      </div>

      <div class="nav-context">
        <span>当前工作身份</span>
        <strong>{{ roleLabel }}</strong>
        <small>{{ user.name || user.username }}</small>
      </div>
      <template v-if="role==='admin'">
        <div class="nav-section">运营中心</div>
        <router-link v-for="r in adminGroups.ops" :key="r.path" :to="r.path" class="nav-item" :class="{ active: $route.path === r.path }" @click="navOpen = false"><span>{{ r.title }}</span><span class="tag">{{ r.tag }}</span></router-link>
        <div class="nav-section">业务配置</div>
        <router-link v-for="r in adminGroups.base" :key="r.path" :to="r.path" class="nav-item" :class="{ active: $route.path === r.path }" @click="navOpen = false"><span>{{ r.title }}</span><span class="tag">{{ r.tag }}</span></router-link>
        <div class="nav-section">智能能力</div>
        <router-link v-for="r in adminGroups.ai.concat(adminGroups.rag, adminGroups.agent)" :key="r.path" :to="r.path" class="nav-item" :class="{ active: $route.path === r.path }" @click="navOpen = false"><span>{{ r.title }}</span><span class="tag">{{ r.tag }}</span></router-link>
      </template>

      <div v-if="role==='doctor'" class="nav-section">医生工作区</div>
      <router-link v-for="r in doctorRoutes" :key="r.path" :to="r.path" class="nav-item"
                   v-show="role==='doctor'"
                   :class="{ active: $route.path === r.path }" @click="navOpen = false">
        <span>{{ r.meta.title }}</span>
        <span class="tag">{{ r.meta.tag }}</span>
      </router-link>

      <div v-if="role==='nurse'" class="nav-section">护士工作区</div>
      <router-link v-for="r in nurseRoutes" :key="r.path" :to="r.path" class="nav-item"
                   v-show="role==='nurse'"
                   :class="{ active: $route.path === r.path }" @click="navOpen = false">
        <span>{{ r.meta.title }}</span>
        <span class="tag">{{ r.meta.tag }}</span>
      </router-link>

      <div v-if="role==='patient'" class="nav-section">患者服务</div>
      <router-link v-for="r in patientRoutes" :key="r.path" :to="r.path" class="nav-item"
                   v-show="role==='patient'"
                   :class="{ active: $route.path === r.path }" @click="navOpen = false">
        <span>{{ r.meta.title }}</span>
        <span class="tag">{{ r.meta.tag }}</span>
      </router-link>
      <div class="sidebar-foot">
        <span><i aria-hidden="true"></i>安全会话</span>
        <button class="logout-button" @click="logout">退出登录</button>
      </div>
    </aside>

    <main id="main-workspace" class="main" tabindex="-1">
      <header class="topbar">
        <button class="menu-button" :aria-expanded="navOpen" :aria-label="navOpen ? '关闭导航' : '打开导航'" @click="navOpen = !navOpen">
          <span></span><span></span>
        </button>
        <div class="page-head">
          <span class="page-kicker">CareNexus / {{ roleLabel }}</span>
          <h1>{{ currentTitle }}</h1>
        </div>
        <div class="topbar-session">
          <span class="topbar-date">{{ todayLabel }}</span>
          <span class="session-state"><i aria-hidden="true"></i>{{ sessionLabel }}</span>
          <div class="user-chip">
            <span class="user-avatar role-avatar" :data-role="role">{{ roleAvatar }}</span>
            <div><strong>{{ user.name || user.username }}</strong><small>{{ roleLabel }}</small></div>
          </div>
        </div>
      </header>
      <div ref="routeStage" class="route-stage">
        <router-view />
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { gsap } from 'gsap'
import router from './router'

const routes = router.options.routes
const adminRoutes = routes.filter(r => r.meta && r.meta.group === 'admin')
const doctorRoutes = routes.filter(r => r.meta && r.meta.group === 'doctor')
const patientRoutes = routes.filter(r => r.meta && r.meta.group === 'patient')
const nurseRoutes = routes.filter(r => r.meta && r.meta.group === 'nurse')
const adminGroups = {
  base: [{ path: '/admin/basedata', title: '基础数据总览', tag: 'CRUD' }, { path: '/admin/symptoms', title: '症状标签库', tag: 'RULE' }],
  ai: [{ path: '/admin/aiconfig', title: '模型与策略', tag: 'MODEL' }, { path: '/admin/prompts', title: '提示模板', tag: 'TEXT' }],
  rag: [{ path: '/admin/knowledge', title: '医学知识库', tag: 'RAG' }, { path: '/admin/knowledge/docs', title: '知识文档', tag: 'DOC' }, { path: '/admin/knowledge/chunks', title: '知识片段', tag: 'CHUNK' }, { path: '/admin/knowledge/search', title: '向量化与检索', tag: 'TOPK' }],
  agent: [{ path: '/admin/tools', title: '工具注册表', tag: 'MCP' }, { path: '/admin/tools/logs', title: '工具调用日志', tag: 'LOG' }],
  ops: [{ path: '/admin/dashboard', title: '就诊运营看板', tag: 'KPI' }, { path: '/admin/agent-runs', title: '外部执行记录', tag: 'TRACE' }, { path: '/admin/usage', title: '系统调用观测', tag: 'USAGE' }]
}

const route = useRoute()
const nav = useRouter()
const shell = ref(null)
const routeStage = ref(null)
const navOpen = ref(false)
const user = computed(() => {
  route.fullPath
  return JSON.parse(localStorage.getItem('hospital_user') || '{}')
})
const role = computed(() => user.value.role)
const roleLabel = computed(() => ({ admin: '运营管理', nurse: '护士导诊', doctor: '医生接诊', patient: '患者服务' }[role.value] || '工作台'))
const sessionLabel = computed(() => ({ admin: '运营数据已同步', nurse: '导诊队列已保护', doctor: '临床会话安全', patient: '隐私连接已保护' }[role.value] || '安全会话'))
const roleAvatar = computed(() => ({ admin: '运', nurse: '导', doctor: '医', patient: '患' }[role.value] || 'U'))
const currentTitle = computed(() => (route.meta && route.meta.title) || '智慧医院')
const todayLabel = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' }).format(new Date())
let motion

function animateRoute() {
  motion?.revert()
  nextTick(() => {
    if (!routeStage.value) return
    motion = gsap.matchMedia()
    motion.add('(prefers-reduced-motion: no-preference)', () => {
      const targets = Array.from(routeStage.value.children)
      if (!targets.length) return
      gsap.fromTo(targets,
        { autoAlpha: 0, y: 14 },
        { autoAlpha: 1, y: 0, duration: 0.48, stagger: 0.05, ease: 'power2.out', clearProps: 'all' })
    }, routeStage.value)
  })
}

onMounted(animateRoute)
watch(() => route.fullPath, animateRoute)
onUnmounted(() => motion?.revert())
function logout() {
  fetch('/v2/api/auth/logout', { method: 'POST', credentials: 'same-origin' }).finally(() => {
    localStorage.removeItem('hospital_user'); nav.replace('/login')
  })
}
</script>
