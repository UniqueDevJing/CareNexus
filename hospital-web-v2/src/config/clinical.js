export const availabilityLabels = {
  AVAILABLE: '可预约',
  NO_SLOTS: '暂无号源',
  PENDING_REVIEW: '等待人工核对',
  EMERGENCY: '急诊优先'
}

export const nextActionLabels = {
  CONTACT_EMERGENCY: '联系急诊',
  WAIT_FOR_REVIEW: '等待临床复核',
  WAIT_FOR_CLINICAL_REVIEW: '等待临床复核',
  CHOOSE_OTHER_SLOT: '刷新或重新匹配',
  SELECT_SLOT: '选择就诊时间',
  PATIENT_CAN_BOOK: '选择可预约时段',
  WAIT_FOR_PATIENT: '等待你补充信息',
  CONTACT_HUMAN: '联系门诊服务台'
}

export const starterPrompts = [
  { label: '胸闷 / 呼吸不适', value: '我从昨晚开始胸闷，活动后更明显' },
  { label: '发热 / 咳嗽', value: '这两天一直发热，伴有咳嗽' },
  { label: '腹痛 / 消化不适', value: '我最近反复胃痛，饭后更明显' }
]
