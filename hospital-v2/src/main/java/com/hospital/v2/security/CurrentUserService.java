package com.hospital.v2.security;

import com.hospital.v2.common.BizException;
import com.hospital.v2.dao.Db;
import org.springframework.stereotype.Service;

import java.util.Map;

/** 把登录账号映射到医生/患者业务主键，并阻止通过查询参数越权访问他人数据。 */
@Service
public class CurrentUserService {
    private final Db db;

    public CurrentUserService(Db db) { this.db = db; }

    public long patientId(AuthPrincipal p, Long requested) {
        if ("admin".equals(p.role()) && requested != null) return requested;
        requireRole(p, "patient");
        Map<String, Object> row = db.queryOne("SELECT id FROM patient_profile WHERE user_id=:u", Map.of("u", p.userId()));
        if (row == null) {
            // 兼容早期生产数据：旧患者档案可能只有姓名，没有回填 user_id。
            // 仅允许“同名且唯一、仍未绑定”的档案自动绑定，避免扩大数据访问范围。
            Map<String, Object> account = db.queryOne(
                    "SELECT real_name FROM user_account WHERE id=:u AND role='patient'", Map.of("u", p.userId()));
            String realName = account == null ? null : String.valueOf(account.get("real_name"));
            var candidates = realName == null || realName.isBlank()
                    ? java.util.List.<Map<String, Object>>of()
                    : db.query("SELECT id FROM patient_profile WHERE name=:name AND user_id IS NULL",
                    Map.of("name", realName));
            if (candidates.size() == 1) {
                row = candidates.get(0);
                db.update("UPDATE patient_profile SET user_id=:u WHERE id=:id AND user_id IS NULL",
                        Map.of("u", p.userId(), "id", row.get("id")));
            }
        }
        if (row == null) throw new BizException("当前账号未绑定患者档案");
        long own = ((Number) row.get("id")).longValue();
        if (requested != null && requested != own) throw new BizException("禁止访问其他患者数据");
        return own;
    }

    public long doctorId(AuthPrincipal p, Long requested) {
        if ("admin".equals(p.role()) && requested != null) return requested;
        requireRole(p, "doctor");
        Map<String, Object> row = db.queryOne("SELECT id FROM doctor WHERE user_id=:u", Map.of("u", p.userId()));
        if (row == null) {
            // Older production data can contain a doctor login created before
            // doctor.user_id was introduced. Resolve only an unambiguous name
            // match so the account remains usable without widening access.
            Map<String, Object> account = db.queryOne(
                    "SELECT real_name FROM user_account WHERE id=:u AND role='doctor'",
                    Map.of("u", p.userId()));
            String realName = account == null ? null : String.valueOf(account.get("real_name"));
            var candidates = realName == null || realName.isBlank()
                    ? java.util.List.<Map<String, Object>>of()
                    : db.query("SELECT id FROM doctor WHERE name=:name", Map.of("name", realName));
            if (candidates.size() == 1) row = candidates.get(0);
        }
        if (row == null) throw new BizException("当前账号未绑定医生档案");
        long own = ((Number) row.get("id")).longValue();
        if (requested != null && requested != own) throw new BizException("禁止访问其他医生数据");
        return own;
    }

    public void assertPatientOwnsRecord(AuthPrincipal p, long recordId) {
        if ("admin".equals(p.role())) return;
        long patientId = patientId(p, null);
        assertExists("SELECT id FROM medical_record WHERE id=:id AND patient_id=:owner", recordId, patientId,
                "无权查看该病历");
    }

    public void assertPatientOwnsSession(AuthPrincipal p, long sessionId) {
        if ("admin".equals(p.role())) return;
        long patientId = patientId(p, null);
        assertExists("SELECT id FROM pre_triage_session WHERE id=:id AND patient_id=:owner", sessionId, patientId,
                "无权访问该预问诊会话");
    }

    public void assertPatientOwnsSuggestion(AuthPrincipal p, long suggestionId) {
        if ("admin".equals(p.role())) return;
        long patientId = patientId(p, null);
        assertExists("SELECT id FROM triage_suggestion WHERE id=:id AND patient_id=:owner", suggestionId, patientId,
                "无权使用该分诊建议挂号");
    }

    public void assertDoctorOwnsRecord(AuthPrincipal p, long recordId) {
        if ("admin".equals(p.role())) return;
        long doctorId = doctorId(p, null);
        assertExists("SELECT id FROM medical_record WHERE id=:id AND doctor_id=:owner", recordId, doctorId,
                "无权查看或提交该病历");
    }

    public void assertDoctorOwnsRegistration(AuthPrincipal p, long registrationId) {
        if ("admin".equals(p.role())) return;
        long doctorId = doctorId(p, null);
        assertExists("SELECT id FROM registration WHERE id=:id AND doctor_id=:owner", registrationId, doctorId,
                "无权操作该挂号");
    }

    private void assertExists(String sql, long id, long owner, String message) {
        if (db.queryOne(sql, Map.of("id", id, "owner", owner)) == null) throw new BizException(message);
    }

    private void requireRole(AuthPrincipal p, String role) {
        if (!role.equals(p.role())) throw new BizException("账号角色与资源不匹配");
    }
}
