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
        backfillLegacyEvidence();
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

    /**
     * Existing production volumes contain the expanded 11-base catalog from an older seed.
     * Backfill provenance by stable title, without replaying data.sql or overwriting a manual
     * clinician decision. SOURCE_VERIFIED means the source is registered, not clinician-approved.
     */
    private void backfillLegacyEvidence() {
        for (LegacyEvidence doc : legacyEvidence()) {
            String sql = "UPDATE knowledge_doc SET source_type=?, authority_level=?, "
                    + "source_organization=COALESCE(NULLIF(source_organization,''),?), "
                    + "source_url=COALESCE(NULLIF(source_url,''),?), source_version=COALESCE(NULLIF(source_version,''),?), "
                    + "jurisdiction=COALESCE(NULLIF(jurisdiction,''),?), population=COALESCE(NULLIF(population,''),?), "
                    + "evidence_grade=COALESCE(NULLIF(evidence_grade,''),?), review_status=CASE "
                    + "WHEN review_status='PENDING_REVIEW' THEN 'SOURCE_VERIFIED' ELSE review_status END, "
                    + "review_note=COALESCE(NULLIF(review_note,''),?), license_note=COALESCE(NULLIF(license_note,''),?) "
                    + "WHERE title=?";
            jdbc.update(sql, doc.sourceType(), doc.authorityLevel(), doc.organization(), doc.url(), doc.version(),
                    doc.jurisdiction(), doc.population(), doc.evidenceGrade(),
                    "来源链接已登记；摘要仍需执业医师审核后用于临床决策。", doc.licenseNote(), doc.title());
        }
    }

    /** The legacy catalog is intentionally explicit so future additions require provenance review. */
    private List<LegacyEvidence> legacyEvidence() {
        String official = "OFFICIAL_GUIDELINE";
        String government = "GOVERNMENT_GUIDANCE";
        String international = "国际循证指南；具体章节和本地流程需人工复核";
        String china = "中国；特殊人群和本地流程需人工复核";
        String license = "仅保存来源摘要和链接；完整资料的复制、展示和商业使用须遵守发布方许可。";
        return List.of(
                legacy("支气管哮喘防治要点", official, "Global Initiative for Asthma (GINA)", "https://ginasthma.org/2026-gina-strategy-report/", "2026", international, "成人、青少年及儿童；具体适用范围以原指南章节为准", "GINA 循证策略报告", license),
                legacy("慢性阻塞性肺疾病诊治要点", official, "Global Initiative for Chronic Obstructive Lung Disease (GOLD)", "https://goldcopd.org/2026-gold-report-and-pocket-guide/", "2026", international, "成人 COPD；需结合肺功能和暴露史", "GOLD 循证策略报告", license),
                legacy("社区获得性肺炎诊治要点", official, "IDSA/ATS", "https://www.idsociety.org/practice-guideline/community-acquired-pneumonia-cap-in-adults/", "2019", international, "成人社区获得性肺炎；儿童和重症流程需使用专门指南", "IDSA/ATS 临床实践指南", license),
                legacy("肺血栓栓塞症识别与处理", official, "European Society of Cardiology (ESC)", "https://www.escardio.org/guidelines/clinical-practice-guidelines/pocket-guidelines/acute-pulmonary-embolism/", "2019", international, "成人急性肺栓塞；妊娠和特殊人群需专门评估", "ESC/ERS 临床实践指南", license),
                legacy("高血压基层管理与用药要点", official, "中国高血压防治指南修订委员会等", "https://cjournal.hep.com.cn/1673-7245/CN/1160171857581368285", "2024 修订版", china, "中国成人高血压患者；特殊人群需按专门章节处理", "中国多学会循证临床指南", license),
                legacy("急性胸痛的鉴别与处置", official, "AHA/ACC Joint Committee on Clinical Practice Guidelines", "https://professional.heart.org/en/guidelines-statements/2021-ahaaccasechestsaemscctscmr-guideline-for-the-evaluation-and-diagnosis-ofcir0000000000001029", "2021", international, "成人急性胸痛；不能替代当地急诊评估", "多学会临床实践指南", license),
                legacy("心房颤动与卒中预防", official, "AHA/ACC/ACCP/HRS", "https://www.heart.org/-/media/Files/Professional/Quality-Improvement/Get-With-The-Guidelines/Get-With-The-Guidelines-AFIB/AFib-Month/joglaretal20232023accahaaccphrsguidelineforthediagnosisandmanagementofatrialfibrillation.pdf", "2023", international, "成人房颤；抗凝决策必须由医生结合血栓和出血风险判断", "房颤诊断和管理指南", license),
                legacy("心力衰竭规范治疗", official, "AHA/ACC/HFSA", "https://professional.heart.org/en/guidelines-statements/2022-ahaacchfsa-guideline-for-the-management-of-heart-failure-a-report-of-thecir0000000000001063", "2022", international, "成人心力衰竭；药物和剂量需由医生个体化制定", "心力衰竭管理指南", license),
                legacy("急性脑卒中的识别与溶栓", government, "中华人民共和国国家卫生健康委员会", "https://www.nhc.gov.cn/wjw/c100175/202108/df1789a9ba174678b1979bc14117b58f.shtml", "2021", china, "疑似卒中须立即进入线下急救流程", "中国脑卒中防治指导规范", license),
                legacy("头痛的鉴别与处理", official, "International Headache Society", "https://ichd-3.org/", "ICHD-3", international, "头痛分类和诊断标准；危险信号需线下评估", "国际头痛疾病分类", license),
                legacy("癫痫与发作性疾病", official, "International League Against Epilepsy (ILAE)", "https://www.ilae.org/guidelines/definition-and-classification", "2022", international, "癫痫诊断、分类和用药需由神经科医生确认", "ILAE 分类和临床指南", license),
                legacy("帕金森病与运动障碍", official, "International Parkinson and Movement Disorder Society", "https://www.movementdisorders.org/MDS/Resources/Clinical-Practice-Guidelines.htm", "现行版", international, "运动障碍诊断和治疗需专科评估", "MDS 临床实践资源", license),
                legacy("失眠的认知行为治疗", official, "American Academy of Sleep Medicine", "https://aasm.org/resources/clinical-guidelines/", "现行版", international, "成人失眠；合并睡眠呼吸障碍或精神疾病需专科评估", "AASM 临床实践指南", license),
                legacy("2型糖尿病的诊治路径", official, "American Diabetes Association Professional Practice Committee", "https://diabetesjournals.org/docm-care/collection/18339/2026-Abridged-Standards-of-Care", "Standards of Care 2026", international, "非妊娠成人为主；儿童和妊娠需专门章节", "ADA 年度循证临床标准", license),
                legacy("糖尿病急性并发症与甲状腺疾病", official, "American Diabetes Association; American Thyroid Association", "https://diabetesjournals.org/docm-care/collection/18339/2026-Abridged-Standards-of-Care", "2026", international, "急性代谢异常和甲状腺疾病需及时线下检查", "ADA/ATA 专业指南索引", license),
                legacy("骨质疏松与钙磷代谢", official, "International Osteoporosis Foundation", "https://www.osteoporosis.foundation/health-professionals/clinical-guidelines", "现行版", international, "骨折风险、用药和钙磷代谢需结合检查", "IOF 临床指南资源", license),
                legacy("儿童发热的家庭处理与就医指征", official, "National Institute for Health and Care Excellence (NICE)", "https://www.nice.org.uk/guidance/ng143/chapter/Recommendations", "NG143（2021 修订）", "英国；需结合中国本地流程", "5 岁以下儿童发热初筛与危险信号", "NICE 临床指南", license),
                legacy("儿童呼吸道感染与雾化治疗", official, "National Institute for Health and Care Excellence (NICE)", "https://www.nice.org.uk/guidance/ng9", "NG9", "英国；儿童用药和雾化适应证需儿科医生复核", "儿童呼吸道感染与雾化治疗", "儿童细支气管炎指南", license),
                legacy("手足口病与常见传染病", government, "World Health Organization (WHO)", "https://www.who.int/health-topics/hand-foot-and-mouth-disease", "现行版", international, "传染病判断和报告要求以当地疾控流程为准", "WHO 疾病信息", license),
                legacy("儿童生长、营养与计划免疫", government, "World Health Organization (WHO)", "https://www.who.int/tools/child-growth-standards/standards", "现行版", international, "生长曲线、营养和免疫需结合国家免疫程序", "WHO 儿童生长标准", license),
                legacy("胃痛、反酸与幽门螺杆菌", official, "American College of Gastroenterology", "https://gi.org/guideline/management-of-helicobacter-pylori-infection/", "2024", international, "幽门螺杆菌检测和根除方案需医生确认", "ACG 临床指南", license),
                legacy("急性腹痛、腹泻与胆囊疾病", official, "American College of Gastroenterology", "https://gi.org/clinical-guidance/", "现行版", international, "急腹症、脱水和胆道疾病需线下检查", "ACG 临床指南索引", license),
                legacy("肝脏疾病与脂肪肝管理", official, "American Association for the Study of Liver Diseases", "https://www.aasld.org/practice-guidelines/nafld", "现行版", international, "脂肪肝和肝病分层需结合实验室与影像", "AASLD 实践指南", license),
                legacy("颈腰痛的评估与保守治疗", official, "National Institute for Health and Care Excellence (NICE)", "https://www.nice.org.uk/guidance/ng59/chapter/Recommendations", "NG59", international, "神经功能缺损、外伤或全身症状需及时就医", "NICE 临床指南", license),
                legacy("骨折、骨质疏松性骨折与关节置换", official, "American Academy of Orthopaedic Surgeons", "https://www.aaos.org/quality/quality-programs/clinical-practice-guidelines/", "现行版", international, "影像、骨折分型和手术适应证需骨科医生确认", "AAOS 临床实践指南索引", license),
                legacy("常见运动损伤的急性期处理", official, "American College of Sports Medicine", "https://www.acsm.org/education-resources/trending-topics-resources/resource-library", "现行版", international, "疑似骨折、脱位或神经血管损伤需急诊评估", "ACSM 运动医学资源", license),
                legacy("月经异常与常见妇科疾病", official, "American College of Obstetricians and Gynecologists", "https://www.acog.org/clinical/clinical-guidance", "现行版", international, "异常出血、妊娠可能和剧烈疼痛需妇科评估", "ACOG 临床指南索引", license),
                legacy("妊娠期保健与危险信号", official, "American College of Obstetricians and Gynecologists", "https://www.acog.org/giving/programs/quality-and-safety/resources", "现行版", international, "孕期危险信号须按当地产科急救流程处理", "ACOG 妊娠安全资源", license),
                legacy("尿路结石与前列腺疾病", official, "European Association of Urology", "https://uroweb.org/guidelines", "现行版", international, "结石梗阻、感染、血尿和排尿困难需泌尿科评估", "EAU 指南资源", license),
                legacy("尿路感染与慢性肾脏病管理", official, "European Association of Urology; KDIGO", "https://kdigo.org/guidelines/ckd-evaluation--and-management/", "KDIGO 2024", international, "尿路感染和慢性肾病需结合尿检、肾功能和影像", "KDIGO/EAU 指南资源", license),
                legacy("贫血与常见血液病", government, "World Health Organization (WHO)", "https://www.who.int/publications/i/item/9789240088542", "2024", international, "贫血分型和治疗需结合血常规、铁代谢等检查", "WHO 贫血诊断阈值指南", license),
                legacy("过敏性皮肤病与湿疹管理", official, "American Academy of Dermatology", "https://www.aad.org/member/clinical-quality/guidelines/atopic-dermatitis", "2025 focused update", international, "皮疹诊断和系统用药需皮肤科医生判断", "AAD 特应性皮炎指南", license),
                legacy("眼科常见急症与慢性病", official, "American Academy of Ophthalmology", "https://www.aao.org/young-ophthalmologists/yo-info/article/essentials-of-top-10-eye-emergencies", "现行版", international, "视力骤降、眼外伤和化学伤须立即眼科急诊", "AAO 眼科急症资源", license),
                legacy("正确挂号与就诊流程", government, "中华人民共和国国家卫生健康委员会", "https://www.nhc.gov.cn/", "现行版", china, "具体挂号、转诊和急诊流程以就诊机构规则为准", "国家卫生健康委公开信息", license),
                legacy("检查前后的注意事项", government, "中华人民共和国国家卫生健康委员会", "https://www.nhc.gov.cn/", "现行版", china, "检查准备和禁忌以具体检查单及医疗机构说明为准", "国家卫生健康委公开信息", license),
                legacy("家庭药箱与安全用药", government, "国家药品监督管理局", "https://www.nmpa.gov.cn/", "现行版", china, "药品说明书、禁忌和相互作用优先于通用建议", "国家药品监督管理局公开信息", license),
                legacy("疫苗与预防保健", government, "World Health Organization (WHO)", "https://www.who.int/teams/immunization-vaccines-and-biologicals/policies/position-papers", "现行版", international, "接种程序以中国国家免疫规划和接种机构要求为准", "WHO 免疫立场文件", license)
        );
    }

    private LegacyEvidence legacy(String title, String authorityLevel, String organization, String url,
                                  String version, String jurisdiction, String population, String evidenceGrade,
                                  String licenseNote) {
        return new LegacyEvidence(title, "GUIDELINE_SUMMARY", authorityLevel, organization, url, version,
                jurisdiction, population, evidenceGrade, licenseNote);
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

    private record LegacyEvidence(String title, String sourceType, String authorityLevel, String organization,
                                  String url, String version, String jurisdiction, String population,
                                  String evidenceGrade, String licenseNote) {}

    private record SegmentEvidence(String sectionRef, String citationKey, String content) {}
}
