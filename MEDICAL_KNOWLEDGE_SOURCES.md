# CareNexus 医学知识库来源说明

当前仓库保存的是经过人工整理的“来源摘要”，不是任何指南的全文复制。每条摘要都应该能回溯到 `knowledge_doc` 的来源机构、版本、URL、DOI（如有）、适用人群和审核状态。

## 已登记来源

| 主题 | 来源 | 版本 | 适用边界 |
| --- | --- | --- | --- |
| 哮喘 | [GINA 2026 Strategy Report](https://ginasthma.org/2026-gina-strategy-report/) | 2026 | 具体药物路径、年龄和严重程度必须回到原指南章节 |
| COPD | [GOLD 2026 Report and Pocket Guide](https://goldcopd.org/2026-gold-report-and-pocket-guide/) | 2026 | 需要肺功能、暴露史、症状和急性加重史综合评估 |
| 高血压 | [中国高血压防治指南（2024 年修订版）](https://cjournal.hep.com.cn/1673-7245/CN/1160171857581368285) | 2024 修订版 | 中国成人适用；特殊人群需按专门章节处理 |
| 急性胸痛 | [AHA/ACC 2021 Chest Pain Guideline](https://professional.heart.org/en/guidelines-statements/2021-ahaaccasechestsaemscctscmr-guideline-for-the-evaluation-and-diagnosis-ofcir0000000000001029) | 2021 | 不能替代当地急诊、心电图、肌钙蛋白和影像检查 |
| 糖尿病 | [ADA Standards of Care 2026 — Diagnosis and Classification](https://diabetesjournals.org/care/article/49/Supplement_1/S27/163926/2-Diagnosis-and-Classification-of-Diabetes) | 2026 | 妊娠、儿童和特殊合并症需要使用对应章节 |
| 儿童发热 | [NICE NG143](https://www.nice.org.uk/guidance/ng143/chapter/Recommendations) | 2021 修订 | 英国指南；转诊、用药和急救流程需由中国本地儿科医生复核 |
| 儿童肺炎支原体肺炎 | [国家卫生健康委《儿童肺炎支原体肺炎诊疗指南（2025 年版）》](https://www.nhc.gov.cn/yzygj/c100068/202509/19e7145436b049a68d4ba8c2060dfa56/files/%E5%84%BF%E7%AB%A5%E8%82%BA%E7%82%8E%E6%94%AF%E5%8E%9F%E4%BD%93%E8%82%BA%E7%82%8E%E8%AF%8A%E7%96%97%E6%8C%87%E5%8D%97%EF%BC%882025%E5%B9%B4%E7%89%88%EF%BC%89.pdf) | 2025 | 具体病原学、重症分层和治疗必须由儿科医生判断 |

## 审核语义

- `SOURCE_VERIFIED`：官方或专业学会来源信息已登记；不等于本项目已经获得临床医生签字批准。
- `CLINICIAN_APPROVED`：由具备资质的临床审核人确认摘要、适用范围和安全边界后，才可使用此状态。
- `PENDING_REVIEW`：上传内容默认状态，不进入默认临床检索。
- `RETIRED`：来源过期、撤回或被新版本替代，不应继续召回。

## 历史知识库迁移

生产环境曾经使用过 11 个知识库的扩展目录。应用启动时会按文档标题执行幂等来源回填，补齐历史资料的来源机构、链接、版本和适用边界；不会重放 `data.sql`，也不会覆盖已经完成的医生审核结果。来源回填后的 `SOURCE_VERIFIED` 只表示来源信息已登记，仍需医生在管理端确认摘要内容后再使用 `CLINICIAN_APPROVED`。

## 生产使用边界

知识库只能为分诊和医患沟通提供可追溯的决策支持，不能独立完成诊断、处方或急症排除。来源全文的下载、复制、展示和商业使用必须遵守各发布机构的许可条款；仓库只保留摘要和来源链接，避免把受版权保护的全文当作自有资料发布。
