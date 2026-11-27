export const navigation = {
  patient: {
    label: '患者服务',
    description: '先把今天要解决的事说清楚',
    note: '其他工作台仅向对应角色开放，患者资料会在授权范围内流转。',
    links: [
      { path: '/patient/pre', title: '智能导诊', tag: '01', hint: '描述不适，获得下一步' },
      { path: '/patient/records', title: '就诊记录', tag: '02', hint: '查看已确认的就诊' },
      { path: '/patient/bills', title: '费用与预约', tag: '03', hint: '确认支付与凭证' },
      { path: '/patient/exams', title: '检查结果', tag: '04', hint: '查看已完成的检查' },
      { path: '/patient/prescriptions', title: '我的处方', tag: '05', hint: '查看用药与处方' }
    ],
    sections: [{ title: '患者服务', links: [
      { path: '/patient/pre', title: '智能导诊', tag: '01', hint: '描述不适，获得下一步' },
      { path: '/patient/records', title: '就诊记录', tag: '02', hint: '查看已确认的就诊' },
      { path: '/patient/bills', title: '费用与预约', tag: '03', hint: '确认支付与凭证' },
      { path: '/patient/exams', title: '检查结果', tag: '04', hint: '查看已完成的检查' },
      { path: '/patient/prescriptions', title: '我的处方', tag: '05', hint: '查看用药与处方' }
    ] }]
  },
  doctor: {
    label: '医生接诊',
    description: '只处理需要临床判断的事项',
    note: '预问诊只提供结构化信息，诊断与处置由医生完成。',
    links: [{ path: '/doctor/console', title: '接诊工作台', tag: '01', hint: '查看待处理患者' }],
    sections: [{ title: '临床工作', links: [{ path: '/doctor/console', title: '接诊工作台', tag: '01', hint: '查看待处理患者' }] }]
  },
  admin: {
    label: '运营与安全',
    description: '只保留运行所需的配置入口',
    note: '按运行状态、临床数据、模型配置和审计记录分组。',
    links: [
      { path: '/admin/dashboard', title: '运营概览', tag: '01', hint: '查看服务状态' },
      { path: '/admin/agent-runs', title: '执行记录', tag: '02', hint: '追踪每次智能体运行' },
      { path: '/admin/usage', title: '调用观测', tag: '03', hint: '检查模型与接口健康' },
      { path: '/admin/basedata', title: '基础数据', tag: '04', hint: '维护科室与医生资料' },
      { path: '/admin/symptoms', title: '症状标签库', tag: '05', hint: '维护风险识别词表' },
      { path: '/admin/aiconfig', title: '模型配置', tag: '06', hint: '管理模型连接与参数' },
      { path: '/admin/prompts', title: 'Prompt 模板', tag: '07', hint: '维护业务提示词' },
      { path: '/admin/knowledge', title: '医学知识库', tag: '08', hint: '管理检索资料' },
      { path: '/admin/knowledge/docs', title: '知识文档', tag: '09', hint: '上传与审核文档' },
      { path: '/admin/knowledge/chunks', title: '知识片段', tag: '10', hint: '检查切分结果' },
      { path: '/admin/knowledge/search', title: '向量化与检索', tag: '11', hint: '验证召回质量' },
      { path: '/admin/tools', title: '工具注册表', tag: '12', hint: '管理智能体工具' },
      { path: '/admin/tools/logs', title: '工具调用日志', tag: '13', hint: '审计工具执行' }
    ],
    sections: [
      { title: '运行概览', links: [
        { path: '/admin/dashboard', title: '运营概览', tag: '01', hint: '查看服务状态' },
        { path: '/admin/agent-runs', title: '执行记录', tag: '02', hint: '追踪每次智能体运行' },
        { path: '/admin/usage', title: '调用观测', tag: '03', hint: '检查模型与接口健康' }
      ] },
      { title: '临床数据', links: [
        { path: '/admin/basedata', title: '基础数据', tag: '04', hint: '维护科室与医生资料' },
        { path: '/admin/symptoms', title: '症状标签库', tag: '05', hint: '维护风险识别词表' }
      ] },
      { title: '智能体配置', links: [
        { path: '/admin/aiconfig', title: '模型配置', tag: '06', hint: '管理模型连接与参数' },
        { path: '/admin/prompts', title: 'Prompt 模板', tag: '07', hint: '维护业务提示词' },
        { path: '/admin/knowledge', title: '医学知识库', tag: '08', hint: '管理检索资料' },
        { path: '/admin/knowledge/docs', title: '知识文档', tag: '09', hint: '上传与审核文档' },
        { path: '/admin/knowledge/chunks', title: '知识片段', tag: '10', hint: '检查切分结果' },
        { path: '/admin/knowledge/search', title: '向量化与检索', tag: '11', hint: '验证召回质量' },
        { path: '/admin/tools', title: '工具注册表', tag: '12', hint: '管理智能体工具' },
        { path: '/admin/tools/logs', title: '工具调用日志', tag: '13', hint: '审计工具执行' }
      ] }
    ]
  }
}

export const roleMeta = {
  patient: { label: '患者服务', description: '先把今天要解决的事说清楚', avatar: '患', session: '隐私连接已保护' },
  doctor: { label: '医生接诊', description: '只处理需要临床判断的事项', avatar: '医', session: '临床会话安全' },
  admin: { label: '运营与安全', description: '只保留运行所需的配置入口', avatar: '运', session: '运行数据已同步' }
}

export function getNavigation(role) {
  return navigation[role] || navigation.patient
}

export function getRoleMeta(role) {
  return roleMeta[role] || roleMeta.patient
}
