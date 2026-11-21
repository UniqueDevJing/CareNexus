package com.hospital.v2.boot;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Backfills provenance for the original seed data and upgrades existing volumes
 * without replaying data.sql. Source verification is not a substitute for clinician sign-off.
 */
@Component
@Order(2)
public final class KnowledgeEvidenceMigration implements ApplicationRunner {
    private static final String SOURCE_NOTICE = "来源摘要不能替代医生诊断、检查或处方；遇到危险信号应立即线下就医。";
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public KnowledgeEvidenceMigration(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!tableExists("knowledge_doc")) return;
        addDocColumns();
        addSegmentColumns();
        resizeSourceType();
        updateDocumentEvidence();
        ensurePediatricGuideline();
        updateSegmentEvidence();
    }

    private void addDocColumns() throws Exception {
        addColumnIfMissing("knowledge_doc", "authority_level", "VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED'");
        addColumnIfMissing("knowledge_doc", "source_organization", "VARCHAR(128)");
        addColumnIfMissing("knowledge_doc", "source_url", "VARCHAR(768)");
        addColumnIfMissing("knowledge_doc", "source_doi", "VARCHAR(128)");
        addColumnIfMissing("knowledge_doc", "source_version", "VARCHAR(64)");
        addColumnIfMissing("knowledge_doc", "source_published_at", "DATE");
        addColumnIfMissing("knowledge_doc", "jurisdiction", "VARCHAR(128)");
        addColumnIfMissing("knowledge_doc", "population", "VARCHAR(255)");
        addColumnIfMissing("knowledge_doc", "evidence_grade", "VARCHAR(64)");
        addColumnIfMissing("knowledge_doc", "review_status", "VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW'");
        addColumnIfMissing("knowledge_doc", "review_note", "VARCHAR(512)");
        addColumnIfMissing("knowledge_doc", "license_note", "VARCHAR(512)");
        addColumnIfMissing("knowledge_doc", "reviewed_by", "VARCHAR(64)");
        addColumnIfMissing("knowledge_doc", "reviewed_at", "DATETIME");
    }

    private void addSegmentColumns() throws Exception {
        addColumnIfMissing("knowledge_segment", "section_ref", "VARCHAR(255)");
        addColumnIfMissing("knowledge_segment", "page_ref", "VARCHAR(64)");
        addColumnIfMissing("knowledge_segment", "citation_key", "VARCHAR(64)");
        addColumnIfMissing("knowledge_segment", "safety_note", "VARCHAR(512)");
    }

    private void resizeSourceType() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            boolean h2 = connection.getMetaData().getDatabaseProductName().toLowerCase(java.util.Locale.ROOT).contains("h2");
            try {
                statement.execute(h2
                        ? "ALTER TABLE knowledge_doc ALTER COLUMN source_type VARCHAR(32)"
                        : "ALTER TABLE knowledge_doc MODIFY COLUMN source_type VARCHAR(32)");
            } catch (Exception ignored) {
                // Existing installations may already have the widened column.
            }
        }
    }

    private void updateDocumentEvidence() {
        List<DocEvidence> docs = List.of(
                new DocEvidence("支气管哮喘诊疗要点", "GUIDELINE_SUMMARY", "OFFICIAL_GUIDELINE",
                        "Global Initiative for Asthma (GINA)", "https://ginasthma.org/2026-gina-strategy-report/", null, "2026", null,
                        "国际循证指南", "成人、青少年及儿童；具体适用范围以原指南章节为准", "国际专家委员会循证策略报告",
                        "仅保存摘要与引用信息；完整资料的复制和商业使用须遵守 GINA 版权要求。"),
                new DocEvidence("慢性阻塞性肺疾病指南", "GUIDELINE_SUMMARY", "OFFICIAL_GUIDELINE",
                        "Global Initiative for Chronic Obstructive Lung Disease (GOLD)", "https://goldcopd.org/2026-gold-report-and-pocket-guide/", null, "2026", null,
                        "国际循证指南", "成人 COPD；具体适用范围以原报告章节为准", "循证策略报告，含科学文献引用",
                        "仅保存摘要与引用信息；完整资料的复制和商业使用须遵守 GOLD 版权要求。"),
                new DocEvidence("高血压诊治与随访", "GUIDELINE_SUMMARY", "OFFICIAL_GUIDELINE",
                        "中国高血压防治指南修订委员会等", "https://cjournal.hep.com.cn/1673-7245/CN/1160171857581368285", "10.16439/j.issn.1673-7245.2024.07.002", "2024 修订版", "2024-07-15",
                        "中国", "中国成人高血压患者；特殊人群需按指南分层", "中国多学会循证临床指南",
                        "仅保存摘要与引用信息，使用原文须遵守期刊版权条款。"),
                new DocEvidence("急性胸痛的识别与处理", "GUIDELINE_SUMMARY", "OFFICIAL_GUIDELINE",
                        "AHA/ACC Joint Committee on Clinical Practice Guidelines", "https://professional.heart.org/en/guidelines-statements/2021-ahaaccasechestsaemscctscmr-guideline-for-the-evaluation-and-diagnosis-ofcir0000000000001029", null, "2021", "2021-10-28",
                        "国际临床实践指南", "急性胸痛成人患者；急诊分诊需结合当地急救流程", "多学会临床实践指南",
                        "仅保存摘要与引用信息，使用原文须遵守 AHA/ACC 版权条款。"),
                new DocEvidence("2型糖尿病管理要点", "GUIDELINE_SUMMARY", "OFFICIAL_GUIDELINE",
                        "American Diabetes Association Professional Practice Committee", "https://diabetesjournals.org/care/article/49/Supplement_1/S27/163926/2-Diagnosis-and-Classification-of-Diabetes", "10.2337/dc26-S002", "Standards of Care 2026", null,
                        "国际临床实践指南", "糖尿病诊断与分型章节；妊娠、儿童等人群需使用专门章节", "年度循证临床实践标准",
                        "仅保存摘要与引用信息，使用原文须遵守 ADA/期刊版权条款。"),
                new DocEvidence("儿童发热与呼吸道感染", "GUIDELINE_SUMMARY", "OFFICIAL_GUIDELINE",
                        "National Institute for Health and Care Excellence (NICE)", "https://www.nice.org.uk/guidance/ng143/chapter/Recommendations", null, "NG143（2021 修订）", "2021-11-26",
                        "英国；需结合中国本地流程", "5 岁以下儿童发热初筛与危险信号", "NICE 临床指南",
                        "仅保存摘要与引用信息，使用原文须遵守 NICE 版权条款。"));
        for (DocEvidence doc : docs) {
            String dateSql = doc.publishedAt() == null ? "NULL" : "?";
            String sql = "UPDATE knowledge_doc SET source_type=?, authority_level=?, source_organization=?, source_url=?, "
                    + "source_doi=?, source_version=?, source_published_at=" + dateSql + ", jurisdiction=?, population=?, "
                    + "evidence_grade=?, review_status='SOURCE_VERIFIED', review_note=?, license_note=? WHERE title=?";
            List<Object> params = new ArrayList<>();
            params.add(doc.sourceType());
            params.add(doc.authorityLevel());
            params.add(doc.organization());
            params.add(doc.url());
            params.add(doc.doi());
            params.add(doc.version());
            if (doc.publishedAt() != null) params.add(Date.valueOf(doc.publishedAt()));
            params.add(doc.jurisdiction());
            params.add(doc.population());
            params.add(doc.evidenceGrade());
            params.add("官方来源链接已登记；摘要仍需执业医师审核后用于临床决策。");
            params.add(doc.licenseNote());
            params.add(doc.title());
            jdbc.update(sql, params.toArray());
        }
    }

    private void ensurePediatricGuideline() {
        String title = "儿童肺炎支原体肺炎诊疗要点";
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_doc WHERE title=?", Integer.class, title);
        if (count != null && count > 0) return;
        jdbc.update("INSERT INTO knowledge_doc (base_id, title, source_type, status, authority_level, source_organization, source_url, "
                        + "source_version, jurisdiction, population, evidence_grade, review_status, review_note, license_note) "
                        + "VALUES (4, ?, 'GUIDELINE_SUMMARY', 'READY', 'GOVERNMENT_GUIDANCE', ?, ?, '2025 年版', '中国', ?, ?, 'SOURCE_VERIFIED', ?, ?)",
                title,
                "中华人民共和国国家卫生健康委员会等",
                "https://www.nhc.gov.cn/yzygj/c100068/202509/19e7145436b049a68d4ba8c2060dfa56/files/%E5%84%BF%E7%AB%A5%E8%82%BA%E7%82%8E%E6%94%AF%E5%8E%9F%E4%BD%93%E8%82%BA%E7%82%8E%E8%AF%8A%E7%96%97%E6%8C%87%E5%8D%97%EF%BC%882025%E5%B9%B4%E7%89%88%EF%BC%89.pdf",
                "儿童肺炎支原体肺炎；具体年龄和重症分层以原指南为准",
                "国家卫生健康委组织专家制定的诊疗指南",
                "国家卫生健康委公开文件已登记；摘要仍需儿科医生审核后用于临床决策。",
                "仅保存摘要与引用信息，使用原文须遵守发布方版权和使用要求。");
    }

    private void updateSegmentEvidence() {
        Map<Integer, List<SegmentEvidence>> byDoc = new LinkedHashMap<>();
        byDoc.put(1, List.of(
                segment("哮喘诊断", "GINA-2026-01", "哮喘诊断应结合反复或波动性呼吸道症状与可变气流受限等客观证据；单凭一次咳嗽、喘息或胸闷不能完成诊断。"),
                segment("长期管理", "GINA-2026-02", "哮喘管理应持续进行评估、调整和复评，关注症状控制、急性加重风险、吸入装置使用、依从性、合并症以及患者目标。"),
                segment("药物路径", "GINA-2026-03", "成人和青少年应按当前 GINA 分层选择含吸入糖皮质激素的控制与缓解方案；不能把单一药物或固定剂量推荐给所有患者。"),
                segment("急性加重", "GINA-2026-04", "明显呼吸困难、说话困难、口唇发绀、意识改变或症状快速恶化时，应立即急诊或呼叫急救，不应在家等待聊天机器人继续判断。"),
                segment("就诊准备", "GINA-2026-05", "就诊前可记录症状起始时间、变化趋势、诱因、既往诊断、正在使用的药物和过敏史，并携带既往检查资料；这些信息用于帮助医生判断，不能替代检查。")));
        byDoc.put(2, List.of(
                segment("诊断", "GOLD-2026-01", "COPD 不能仅凭年龄、吸烟史或咳嗽诊断；应由医生结合症状、暴露史并通过规范肺功能检查确认持续性气流受限。"),
                segment("综合评估", "GOLD-2026-02", "COPD 评估应同时考虑症状负担、急性加重史、肺功能、吸烟或其他暴露和合并症，治疗方案需按个体风险分层。"),
                segment("非药物管理", "GOLD-2026-03", "戒烟、疫苗、体力活动和肺康复是重要非药物管理措施；吸入药物、氧疗和急性加重处理必须由医生结合检查结果决定。"),
                segment("家庭血压记录", "GOLD-2026-04", "家庭血压记录应使用合适袖带、规范坐姿并连续记录；不要依据单次读数自行加药或停药，异常读数应结合症状和医生建议处理。")));
        byDoc.put(3, List.of(
                segment("诊断测量", "CHN-HTN-2024-01", "高血压诊断应基于规范测量和重复确认；中国 2024 年修订版指南常用诊室阈值为收缩压≥140mmHg 和/或舒张压≥90mmHg，家庭与动态血压需按相应规范解释。"),
                segment("风险分层", "CHN-HTN-2024-02", "风险分层不能只看一次血压，还应结合心血管危险因素、靶器官损害、糖尿病、慢性肾病及既往心脑血管疾病。"),
                segment("生活方式", "CHN-HTN-2024-03", "生活方式管理通常包括减少钠盐、控制体重、规律运动、戒烟限酒和改善睡眠；具体目标和用药方案需要结合风险分层由医生制定。"),
                segment("危险信号", "CHN-HTN-2024-04", "血压升高同时出现胸痛、呼吸困难、神经功能缺损、意识改变或视力明显变化时，应立即急诊，不要自行加量或停药。")));
        byDoc.put(4, List.of(
                segment("危险病因", "AHA-ACC-CP-2021-01", "急性胸痛应优先排查急性冠脉综合征、主动脉综合征、肺栓塞和气胸等危重疾病；症状轻重或是否典型不能单独排除危险病因。"),
                segment("心电图与肌钙蛋白", "AHA-ACC-CP-2021-02", "AHA/ACC 胸痛指南建议急性胸痛患者到达医疗机构后尽快获取并判读心电图，目标是在 10 分钟内完成；疑似急性冠脉综合征还需尽快检测肌钙蛋白。"),
                segment("急救边界", "AHA-ACC-CP-2021-03", "突发持续胸痛、呼吸困难、晕厥、冷汗、意识改变或疼痛向背部及下颌放射时，应立即呼叫急救，不要自行驾车或等待线上问答。"),
                segment("线上边界", "AHA-ACC-CP-2021-04", "线上分诊不能排除心肌梗死、主动脉夹层或肺栓塞；任何涉及急性胸痛的建议都必须明确线下急诊评估边界。")));
        byDoc.put(5, List.of(
                segment("诊断标准", "ADA-SOC-2026-01", "ADA 2026 对非妊娠人群列出的诊断依据包括 A1C≥6.5%、空腹血糖≥7.0mmol/L、75g OGTT 2 小时血糖≥11.1mmol/L，或典型高血糖症状/危象伴随机血糖≥11.1mmol/L。"),
                segment("确认性检测", "ADA-SOC-2026-02", "没有明确高血糖或高血糖危象时，糖尿病诊断需要确认性检测；不同年龄、妊娠状态、合并症和低血糖风险会改变管理目标。"),
                segment("综合管理", "ADA-SOC-2026-03", "糖尿病管理需要教育、营养、运动、药物、监测和并发症筛查的持续协作；具体药物选择应考虑心血管病、心衰、肾病、低血糖风险和患者偏好。"),
                segment("代谢急症", "ADA-SOC-2026-04", "恶心呕吐、腹痛、深大呼吸、意识改变或明显脱水等可能提示严重代谢失代偿，应立即急诊；不得仅依据聊天内容自行调整胰岛素。"),
                segment("发病期间", "ADA-SOC-2026-05", "糖尿病患者在发热、呕吐、进食减少或血糖异常时应加强监测并及时联系医疗团队；胰岛素和降糖药调整需遵循个体化医嘱。")));
        byDoc.put(6, List.of(
                segment("初筛框架", "NICE-NG143-01", "儿童发热初筛应先判断气道、呼吸、循环和意识，再评估精神状态、进食、尿量、脱水、皮疹和呼吸负担；不能只按体温高低判断严重程度。"),
                segment("高风险年龄", "NICE-NG143-02", "NICE NG143 将 3 月龄以下体温≥38℃列为高风险信号；抽搐、意识下降、非压褪性皮疹、明显呼吸困难、拒食或尿量减少时应尽快急诊。"),
                segment("退热边界", "NICE-NG143-03", "退热药使用应以儿童是否明显不适为主要依据，不能只为了降低温度；年龄、体重、脱水、肝肾功能和合并用药需由医务人员或说明书核对。"),
                segment("呼吸道感染", "NICE-NG143-04", "儿童呼吸道感染病因多样，不能因发热或咳嗽直接推断细菌感染或自行使用抗生素；持续高热、精神差、喘憋或呼吸困难需及时就医。")));
        byDoc.put(7, List.of(
                segment("综合诊断", "NHC-MPP-2025-01", "儿童肺炎支原体肺炎需要结合病史、症状体征、影像学和病原学等信息综合判断，不能仅凭发热或咳嗽在家中自行确诊。"),
                segment("危险信号", "NHC-MPP-2025-02", "儿童出现持续高热、呼吸急促或困难、发绀、精神反应差、反复呕吐、脱水或病情进行性加重时，应及时到医疗机构评估。"),
                segment("抗感染边界", "NHC-MPP-2025-03", "儿童呼吸道感染的抗感染治疗需要结合年龄、病原学、病情严重程度和耐药风险由医生选择，不能依据网络内容自行购买抗生素。"),
                segment("家庭观察", "NHC-MPP-2025-04", "儿童肺炎的家庭观察应关注呼吸负担、饮水进食、尿量、精神状态和症状趋势；任何危险信号出现都应停止线上分诊并转线下就医。")));

        for (Map.Entry<Integer, List<SegmentEvidence>> entry : byDoc.entrySet()) {
            int docId = entry.getKey();
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT id FROM knowledge_segment WHERE doc_id=? ORDER BY id", docId);
            for (int i = 0; i < entry.getValue().size(); i++) {
                SegmentEvidence segment = entry.getValue().get(i);
                if (i < rows.size()) {
                    jdbc.update("UPDATE knowledge_segment SET content=?, section_ref=?, citation_key=?, safety_note=? WHERE id=?",
                            segment.content(), segment.sectionRef(), segment.citationKey(), SOURCE_NOTICE, rows.get(i).get("id"));
                } else {
                    jdbc.update("INSERT INTO knowledge_segment (doc_id, base_id, content, vector_status, section_ref, citation_key, safety_note) "
                                    + "VALUES (?, (SELECT base_id FROM knowledge_doc WHERE id=?), ?, 'PENDING', ?, ?, ?)",
                            docId, docId, segment.content(), segment.sectionRef(), segment.citationKey(), SOURCE_NOTICE);
                }
            }
        }
    }

    private SegmentEvidence segment(String sectionRef, String citationKey, String content) {
        return new SegmentEvidence(sectionRef, citationKey, content);
    }

    private boolean tableExists(String table) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet result = metadata.getTables(connection.getCatalog(), connection.getSchema(), null, new String[]{"TABLE"})) {
                while (result.next()) if (table.equalsIgnoreCase(result.getString("TABLE_NAME"))) return true;
            }
        }
        return false;
    }

    private void addColumnIfMissing(String table, String column, String definition) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet result = metadata.getColumns(connection.getCatalog(), connection.getSchema(), null, null)) {
                while (result.next()) {
                    if (table.equalsIgnoreCase(result.getString("TABLE_NAME"))
                            && column.equalsIgnoreCase(result.getString("COLUMN_NAME"))) return;
                }
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            }
        }
    }

    private record DocEvidence(String title, String sourceType, String authorityLevel, String organization, String url,
                               String doi, String version, String publishedAt, String jurisdiction, String population,
                               String evidenceGrade, String licenseNote) {}

    private record SegmentEvidence(String sectionRef, String citationKey, String content) {}
}
