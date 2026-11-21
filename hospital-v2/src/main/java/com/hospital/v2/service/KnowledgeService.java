package com.hospital.v2.service;

import com.hospital.v2.common.BizException;
import com.hospital.v2.config.EmbeddingGateway;
import com.hospital.v2.config.HospitalProps;
import com.hospital.v2.dao.Db;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 医学知识库 RAG：来源审核 + 按库收窄 + 内存余弦 TopK。 */
@Service
public class KnowledgeService {
    private final Db db;
    private final EmbeddingGateway embed;
    private final HospitalProps props;

    public KnowledgeService(Db db, EmbeddingGateway embed, HospitalProps props) {
        this.db = db;
        this.embed = embed;
        this.props = props;
    }

    public List<Map<String, Object>> listBases() {
        return db.query("SELECT * FROM knowledge_base ORDER BY id", Map.of());
    }

    public List<Map<String, Object>> listDocs(Long baseId) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("base_id", baseId);
        return db.query("SELECT * FROM knowledge_doc WHERE base_id=:base_id ORDER BY id", p);
    }

    public Map<String, Object> reviewDoc(long docId, String reviewStatus, String note, String reviewer) {
        Set<String> allowed = Set.of("PENDING_REVIEW", "SOURCE_VERIFIED", "CLINICIAN_APPROVED", "RETIRED");
        if (!allowed.contains(reviewStatus)) throw new BizException("不支持的知识审核状态");
        Map<String, Object> doc = db.queryOne("SELECT id, source_url, source_organization FROM knowledge_doc WHERE id=:id",
                Map.of("id", docId));
        if (doc == null) throw new BizException("知识文档不存在");
        if ("CLINICIAN_APPROVED".equals(reviewStatus)
                && (doc.get("source_url") == null || String.valueOf(doc.get("source_url")).isBlank()
                || doc.get("source_organization") == null || String.valueOf(doc.get("source_organization")).isBlank())) {
            throw new BizException("医生审核前必须登记权威来源机构和链接");
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", docId);
        p.put("status", reviewStatus);
        p.put("note", note == null ? "" : note.trim());
        p.put("reviewer", reviewer == null ? "admin" : reviewer);
        db.update("UPDATE knowledge_doc SET review_status=:status, review_note=:note, reviewed_by=:reviewer, "
                + "reviewed_at=CURRENT_TIMESTAMP WHERE id=:id", p);
        return db.queryOne("SELECT * FROM knowledge_doc WHERE id=:id", Map.of("id", docId));
    }

    /** 新增文档默认进入待审核状态，避免未经来源核验的上传内容直接进入临床检索。 */
    public long addDoc(long baseId, String title, String content) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("base_id", baseId);
        d.put("title", title);
        long docId = db.insert("INSERT INTO knowledge_doc (base_id, title, source_type, status, authority_level, review_status) "
                + "VALUES (:base_id,:title,'UPLOAD','READY','UNVERIFIED','PENDING_REVIEW')", d);
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("doc_id", docId);
        s.put("base_id", baseId);
        s.put("content", content);
        s.put("safety_note", "未完成来源和临床审核，不进入默认临床检索。仅供管理员审阅。");
        long segId = db.insert("INSERT INTO knowledge_segment (doc_id, base_id, content, vector_status, safety_note) "
                + "VALUES (:doc_id,:base_id,:content,'PENDING',:safety_note)", s);
        embedSegment(segId, content);
        return docId;
    }

    /** 把一条 PENDING 片段向量化并落库。 */
    private void embedSegment(long segId, String content) {
        float[] v = embed.embed(content);
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("emb", EmbeddingGateway.toJson(v));
        u.put("id", segId);
        db.update("UPDATE knowledge_segment SET embedding=:emb, vector_status='READY' WHERE id=:id", u);
    }

    /** 应用启动时补齐所有 PENDING 向量。 */
    public void embedPending() {
        List<Map<String, Object>> pending = db.query(
                "SELECT id, content FROM knowledge_segment WHERE vector_status='PENDING'", Map.of());
        for (Map<String, Object> row : pending) {
            embedSegment(((Number) row.get("id")).longValue(), (String) row.get("content"));
        }
    }

    /** 检索：按知识库收窄候选 → 余弦 TopK → 阈值过滤。 */
    public List<Map<String, Object>> retrieve(String query, Long baseId, int topK) {
        Map<String, Object> p = new LinkedHashMap<>();
        if (baseId != null) {
            p.put("base_id", baseId);
        }
        String sql = "SELECT s.id, s.doc_id, s.base_id, s.content, s.embedding, s.section_ref, s.page_ref, "
                + "s.citation_key, s.safety_note, d.title source_title, d.source_type, d.authority_level, "
                + "d.source_organization, d.source_url, d.source_doi, d.source_version, d.source_published_at, "
                + "d.jurisdiction, d.population, d.evidence_grade, d.review_status, d.review_note, d.license_note "
                + "FROM knowledge_segment s JOIN knowledge_doc d ON d.id=s.doc_id "
                + "WHERE s.vector_status='READY' AND d.status='READY' "
                + "AND d.review_status IN ('SOURCE_VERIFIED','CLINICIAN_APPROVED')";
        if (baseId != null) sql += " AND s.base_id=:base_id";
        List<Map<String, Object>> cands = db.query(sql, p);

        float[] q = embed.embed(query);
        int k = Math.min(topK <= 0 ? props.getRag().getTopK() : topK, cands.size());
        List<Map<String, Object>> scored = new ArrayList<>();
        for (Map<String, Object> c : cands) {
            float[] v = EmbeddingGateway.fromJson((String) c.get("embedding"));
            double sim = EmbeddingGateway.cosine(q, v);
            Map<String, Object> item = new LinkedHashMap<>(c);
            item.put("score", Math.round(sim * 1000.0) / 1000.0);
            item.put("clinical_use", "DECISION_SUPPORT_ONLY");
            item.put("source_notice", "来源摘要不能替代医生诊断、检查或处方；遇到危险信号应立即线下就医。");
            scored.add(item);
        }
        scored.sort((a, b) -> Double.compare((Double) b.get("score"), (Double) a.get("score")));
        List<Map<String, Object>> out = new ArrayList<>();
        double th = props.getRag().getScoreThreshold();
        for (Map<String, Object> m : scored) {
            if (out.size() >= k) break;
            if ((Double) m.get("score") >= th) out.add(m);
        }
        return out;
    }
}
