<template>
  <section class="workspace module-workspace" :aria-label="title">
    <section class="page-intro"><div><span class="eyebrow">{{ kicker }}</span><h2>{{ title }}</h2><p>{{ description }}</p></div><button class="secondary-button" @click="load">刷新数据</button></section>
    <section v-if="mode === 'prompts'" class="module-card-grid"><article v-for="p in prompts" :key="p.name" class="surface prompt-module-card"><header><div><span class="eyebrow">PROMPT TEMPLATE</span><h3>{{ p.name }}</h3></div><span class="chip ok">当前版本</span></header><p>{{ p.description }}</p><textarea v-model="p.template" rows="8"></textarea><footer><span>{{ p.variables.join(' · ') }}</span><button class="primary-button" @click="savePrompt(p)">保存版本</button></footer></article></section>
    <section v-else-if="mode === 'knowledgeDocs' || mode === 'knowledgeChunks'" class="surface module-table-card"><header class="panel-head"><div><span class="eyebrow">RAG / {{ mode === 'knowledgeDocs' ? 'DOCUMENTS' : 'CHUNKS' }}</span><h3>{{ mode === 'knowledgeDocs' ? '知识文档' : '知识片段' }}</h3></div><span class="panel-note">{{ docs.length }} 条记录</span></header><div class="table-scroll"><table><thead><tr><th>文档名称</th><th>知识库</th><th>切片数</th><th>向量化</th><th>更新时间</th></tr></thead><tbody><tr v-for="d in docs" :key="d.id"><td><strong>{{ d.title || d.name }}</strong><small>{{ d.file_name || '结构化内容' }}</small></td><td>{{ d.base_name || '医学知识库' }}</td><td>{{ d.chunk_count || d.chunks || '—' }}</td><td><span class="chip ok">已向量化</span></td><td>{{ d.update_time || d.updated_at || '实时' }}</td></tr></tbody></table></div></section>
    <section v-else-if="mode === 'knowledgeSearch'" class="surface retrieval-module"><header class="panel-head"><div><span class="eyebrow">RAG / RETRIEVAL LAB</span><h3>向量化与检索</h3></div><span class="panel-note">按知识库收窄候选 · Top K</span></header><div class="retrieval-search"><input v-model="query" placeholder="输入医学问题，例如：哮喘急性发作如何处理？" @keyup.enter="search"><button class="primary-button" @click="search">执行检索</button></div><div class="retrieval-results"><article v-for="(hit, i) in hits" :key="i"><header><span>RESULT {{ String(i + 1).padStart(2, '0') }}</span><b>{{ hit.score || '—' }}</b></header><p>{{ hit.content || hit.text }}</p><span class="score-bar"><i :style="{width: `${Math.min(Number(hit.score || 0) * 100, 100)}%`}"></i></span></article><div v-if="searched && !hits.length" class="retrieval-placeholder">暂无命中片段</div></div></section>
    <section v-else-if="mode === 'toolLogs' || mode === 'agentRuns'" class="surface module-table-card"><header class="panel-head"><div><span class="eyebrow">AGENT / {{ mode === 'toolLogs' ? 'TOOL CALL LOG' : 'EXECUTION TIMELINE' }}</span><h3>{{ mode === 'toolLogs' ? '工具调用日志' : 'Agent执行时间线' }}</h3></div><span class="panel-note">{{ rows.length }} 条记录</span></header><div class="table-scroll"><table><thead><tr><th>{{ mode === 'toolLogs' ? '工具' : '运行' }}</th><th>状态</th><th>来源</th><th>耗时</th><th>时间</th><th>动作</th></tr></thead><tbody><tr v-for="r in rows" :key="r.id || r.run_id"><td><strong>{{ mode === 'toolLogs' ? r.tool_code : `Run #${r.run_id}` }}</strong><small>{{ r.source || r.final_department_name || 'Agent' }}</small></td><td><span :class="['chip', r.status === 'SUCCESS' || r.status === 'DONE' ? 'ok' : 'warn']">{{ r.status }}</span></td><td>{{ r.source || '系统' }}</td><td>{{ r.cost_ms || r.duration_ms || 0 }} ms</td><td>{{ r.create_time || '实时' }}</td><td><button class="text-button" @click="openDetail(r)">查看详情</button></td></tr></tbody></table></div></section>
    <section v-else class="surface"><div class="module-note-grid"><div><span class="eyebrow">COURSE PARITY</span><h3>模块已接入</h3><p>保留现有业务 API，同时把课程中的管理、RAG、Agent、观测模块拆成可独立访问的页面。</p></div><router-link class="primary-button" to="/admin/dashboard">打开运营看板</router-link></div></section>
    <el-dialog v-model="detailVisible" title="调用详情" width="620px"><pre class="readout">{{ JSON.stringify(detail, null, 2) }}</pre></el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../../api'

const props = defineProps({ mode: { type: String, default: 'prompts' } })
const mode = computed(() => props.mode)
const titles = { prompts: 'Prompt模板', knowledgeDocs: '知识文档', knowledgeChunks: '知识片段', knowledgeSearch: '向量化与检索', toolLogs: '工具调用日志', agentRuns: 'Agent执行时间线' }
const title = computed(() => titles[mode.value] || 'AI能力模块')
const kicker = computed(() => mode.value.startsWith('knowledge') ? '医学知识库 / RAG' : mode.value.startsWith('tool') || mode.value.startsWith('agent') ? 'Agent工具中心 / OBSERVABILITY' : 'AI能力配置')
const description = computed(() => ({ prompts: '把变量、版本和用途放在一个可审计的配置页面。', knowledgeDocs: '上传后的原始文档与解析状态。', knowledgeChunks: '文档切片、向量化状态与召回准备度。', knowledgeSearch: '用真实检索结果验证进入 Agent 上下文的知识片段。', toolLogs: '成功失败都留痕，停用工具后可追溯调用变化。', agentRuns: '按运行实例回放分诊 Agent 的步骤、工具与耗时。' }[mode.value] || 'AI 管理模块'))
const prompts = ref([{ name: '预问诊与分诊 Agent', description: '症状理解、风险识别与科室推荐。', template: '请基于患者症状{{symptoms}}，输出结构化分诊建议。', variables: ['{{symptoms}}'], modelRow: null }])
const docs = ref([]); const rows = ref([]); const hits = ref([]); const query = ref(''); const searched = ref(false); const detailVisible = ref(false); const detail = ref(null)
async function load() {
  try {
    if (mode.value === 'prompts') { const models = await api.get('/admin/ai-models'); prompts.value = (models || []).map(m => ({ name: `${m.name || m.model} · Prompt`, description: `${m.purpose} 模型的运行时提示词。`, template: m.prompt_template || '请基于{{symptoms}}输出结构化、可核验的医疗建议。', variables: ['{{symptoms}}'], modelRow: m })) }
    else if (mode.value === 'knowledgeDocs' || mode.value === 'knowledgeChunks') { const bases = await api.get('/admin/knowledge/bases'); docs.value = bases.length ? await api.get('/admin/knowledge/docs', { baseId: bases[0].id }) : [] }
    else if (mode.value === 'agentRuns') rows.value = await api.get('/admin/dashboard/runs')
    else if (mode.value === 'toolLogs') { const runs = await api.get('/admin/dashboard/runs'); rows.value = runs.length ? await api.get('/admin/dashboard/tool-logs', { runId: runs[0].run_id }) : [] }
  } catch (e) { ElMessage.error(e.message || '加载失败') }
}
async function search() { searched.value = true; try { hits.value = await api.get('/admin/knowledge/retrieve', { q: query.value, topK: 6 }) } catch (e) { ElMessage.error(e.message || '检索失败') } }
async function savePrompt(p) { if (!p.modelRow) return; await api.post('/admin/ai-models/save', null, { ...p.modelRow, prompt_template: p.template }); ElMessage.success(`${p.name} 已保存，下一次 Agent 调用立即生效`) }
function openDetail(r) { detail.value = r; detailVisible.value = true }
onMounted(load)
</script>
