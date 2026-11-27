import { createApp, defineAsyncComponent } from 'vue'
import {
  ElButton, ElInput, ElRadioButton, ElRadioGroup, ElTag
} from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles.css'

const app = createApp(App)

// 低频管理组件按路由首次使用时加载，避免首屏静态拉取整套表格、表单和弹窗实现。
const dialogModule = () => import('element-plus/es/components/dialog/index.mjs')
const descriptionsModule = () => import('element-plus/es/components/descriptions/index.mjs')
const formModule = () => import('element-plus/es/components/form/index.mjs')
const inputNumberModule = () => import('element-plus/es/components/input-number/index.mjs')
const selectModule = () => import('element-plus/es/components/select/index.mjs')
const switchModule = () => import('element-plus/es/components/switch/index.mjs')
const tableModule = () => import('element-plus/es/components/table/index.mjs')
const tabsModule = () => import('element-plus/es/components/tabs/index.mjs')
const uploadModule = () => import('element-plus/es/components/upload/index.mjs')
const ElDialog = defineAsyncComponent(() => dialogModule().then(m => m.ElDialog))
const ElDescriptions = defineAsyncComponent(() => descriptionsModule().then(m => m.ElDescriptions))
const ElDescriptionsItem = defineAsyncComponent(() => descriptionsModule().then(m => m.ElDescriptionsItem))
const ElForm = defineAsyncComponent(() => formModule().then(m => m.ElForm))
const ElFormItem = defineAsyncComponent(() => formModule().then(m => m.ElFormItem))
const ElInputNumber = defineAsyncComponent(() => inputNumberModule().then(m => m.ElInputNumber))
const ElOption = defineAsyncComponent(() => selectModule().then(m => m.ElOption))
const ElSelect = defineAsyncComponent(() => selectModule().then(m => m.ElSelect))
const ElSwitch = defineAsyncComponent(() => switchModule().then(m => m.ElSwitch))
const ElTable = defineAsyncComponent(() => tableModule().then(m => m.ElTable))
const ElTableColumn = defineAsyncComponent(() => tableModule().then(m => m.ElTableColumn))
const ElTabs = defineAsyncComponent(() => tabsModule().then(m => m.ElTabs))
const ElTabPane = defineAsyncComponent(() => tabsModule().then(m => m.ElTabPane))
const ElUpload = defineAsyncComponent(() => uploadModule().then(m => m.ElUpload))
const components = {
  ElButton, ElDialog, ElDescriptions, ElDescriptionsItem, ElForm, ElFormItem,
  ElInput, ElInputNumber, ElOption, ElRadioButton, ElRadioGroup, ElSelect,
  ElSwitch, ElTable, ElTableColumn, ElTabs, ElTabPane, ElTag, ElUpload
}
Object.entries(components).forEach(([name, component]) => app.component(name, component))
app.use(router).mount('#app')
