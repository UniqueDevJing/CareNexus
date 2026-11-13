import { createApp } from 'vue'
import {
  ElButton, ElDialog, ElDescriptions, ElDescriptionsItem, ElForm, ElFormItem,
  ElInput, ElInputNumber, ElOption, ElRadioButton, ElRadioGroup, ElSelect,
  ElSwitch, ElTable, ElTableColumn, ElTabs, ElTabPane, ElTag, ElUpload
} from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles.css'

const app = createApp(App)
const components = {
  ElButton, ElDialog, ElDescriptions, ElDescriptionsItem, ElForm, ElFormItem,
  ElInput, ElInputNumber, ElOption, ElRadioButton, ElRadioGroup, ElSelect,
  ElSwitch, ElTable, ElTableColumn, ElTabs, ElTabPane, ElTag, ElUpload
}
Object.entries(components).forEach(([name, component]) => app.component(name, component))
app.use(router).mount('#app')
