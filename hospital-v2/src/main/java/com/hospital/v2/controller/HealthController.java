package com.hospital.v2.controller;

import com.hospital.v2.common.Result;
import com.hospital.v2.dao.Db;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 供反向代理和发布流程使用的最小健康检查，不返回业务数据。 */
@RestController
public class HealthController {
    private final Db db;

    public HealthController(Db db) {
        this.db = db;
    }

    @GetMapping("/api/health")
    public Result<?> health() {
        db.queryOne("SELECT 1 AS ok", Map.of());
        return Result.ok(Map.of("status", "UP"));
    }
}
