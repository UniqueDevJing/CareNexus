package com.hospital.v2.service;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/** 仅指向独立临时实例，不能复用生产数据库。 */
@EnabledIfEnvironmentVariable(named="RUN_ISOLATED_MYSQL_TEST", matches="true")
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:mysql://127.0.0.1:13316/care_audit?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "spring.datasource.username=root", "spring.datasource.password=", "spring.sql.init.mode=always"
})
class MySqlConcurrencyTest extends ConcurrencyAndRecordTest {}
