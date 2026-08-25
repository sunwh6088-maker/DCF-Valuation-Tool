package com.dcf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DCF 估值工具（Java 版）入口。
 * 技术栈：Spring Boot 3 + Thymeleaf + Bootstrap + ECharts + Apache POI。
 */
@SpringBootApplication
public class DcfApplication {

    public static void main(String[] args) {
        SpringApplication.run(DcfApplication.class, args);
    }
}