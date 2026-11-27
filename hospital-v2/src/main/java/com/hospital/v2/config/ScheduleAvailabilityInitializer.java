package com.hospital.v2.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 演示环境的号源自愈：数据包中的固定日期会自然过期，导致“智能分诊成功但永远无号源”。
 * 只在 mysql 演示配置开启，且只为没有任何未来可用排班的医生新增未来号源，不修改历史排班。
 */
@Component
@Order(4)
public final class ScheduleAvailabilityInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final HospitalProps props;

    public ScheduleAvailabilityInitializer(JdbcTemplate jdbc, HospitalProps props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureFutureDemoSchedules();
    }

    private void ensureFutureDemoSchedules() {
        if (!props.getSchedule().isSeedDemo()) return;
        List<Map<String, Object>> doctors = jdbc.queryForList("SELECT id, department_id FROM doctor ORDER BY id");
        LocalDate from = LocalDate.now().plusDays(1);
        for (Map<String, Object> doctor : doctors) {
            long doctorId = ((Number) doctor.get("id")).longValue();
            long departmentId = ((Number) doctor.get("department_id")).longValue();
            Integer available = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM doctor_schedule WHERE doctor_id=? AND status=1 "
                            + "AND remain_num>0 AND work_date>=CURRENT_DATE", Integer.class, doctorId);
            if (available != null && available > 0) continue;
            for (int offset = 0; offset < 7; offset++) {
                LocalDate date = from.plusDays(offset);
                addIfMissing(doctorId, departmentId, date, "AM");
                addIfMissing(doctorId, departmentId, date, "PM");
            }
        }
    }

    private void addIfMissing(long doctorId, long departmentId, LocalDate date, String period) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM doctor_schedule WHERE doctor_id=? AND work_date=? AND period=?",
                Integer.class, doctorId, date, period);
        if (exists != null && exists > 0) return;
        jdbc.update("INSERT INTO doctor_schedule "
                        + "(doctor_id, department_id, work_date, period, total_num, remain_num, status) "
                        + "VALUES (?, ?, ?, ?, 20, 20, 1)",
                doctorId, departmentId, date, period);
    }
}
