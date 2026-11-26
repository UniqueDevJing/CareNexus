export const navigation = {
  patient: {
    label: '患者服务',
    description: '先把今天要解决的事说清楚',
    links: [
      { path: '/patient/pre', title: '智能导诊', tag: '01', hint: '描述不适，获得下一步' },
      { path: '/patient/records', title: '就诊记录', tag: '02', hint: '查看已确认的就诊' },
      { path: '/patient/bills', title: '费用与预约', tag: '03', hint: '确认支付与凭证' }
    ]
  },
  doctor: {
    label: '医生接诊',
    description: '只处理需要临床判断的事项',
    links: [{ path: '/doctor/console', title: '接诊工作台', tag: '01', hint: '查看待处理患者' }]
  },
  nurse: {
    label: '人工复核',
    description: '接住智能体无法安全确认的事项',
    links: [
      { path: '/nurse/triage', title: '复核队列', tag: '01', hint: '确认风险与就诊方向' },
      { path: '/nurse/pharmacy', title: '药房事项', tag: '02', hint: '查看待处理处方' }
    ]
  },
  admin: {
    label: '运营与安全',
    description: '只保留运行所需的配置入口',
    links: [
      { path: '/admin/dashboard', title: '运营概览', tag: '01', hint: '查看服务状态' },
      { path: '/admin/agent-runs', title: '执行记录', tag: '02', hint: '追踪每次智能体运行' },
      { path: '/admin/usage', title: '调用观测', tag: '03', hint: '检查模型与接口健康' }
    ]
  }
}

export const roleMeta = {
  patient: { label: '患者服务', description: '先把今天要解决的事说清楚', avatar: '患', session: '隐私连接已保护' },
  nurse: { label: '人工复核', description: '接住智能体无法安全确认的事项', avatar: '导', session: '复核队列已保护' },
  doctor: { label: '医生接诊', description: '只处理需要临床判断的事项', avatar: '医', session: '临床会话安全' },
  admin: { label: '运营与安全', description: '只保留运行所需的配置入口', avatar: '运', session: '运行数据已同步' }
}

export function getNavigation(role) {
  return navigation[role] || navigation.patient
}

export function getRoleMeta(role) {
  return roleMeta[role] || roleMeta.patient
}
