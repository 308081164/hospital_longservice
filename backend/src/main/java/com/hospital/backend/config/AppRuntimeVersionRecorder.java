package com.hospital.backend.config;

import com.hospital.backend.service.SystemVersionInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 每次 JVM 启动记录运行时版本指纹，供 /version 展示「最近更新」时间（不依赖镜像构建参数是否陈旧）。
 */
@Component
@Order(5)
@RequiredArgsConstructor
public class AppRuntimeVersionRecorder implements CommandLineRunner {

    private final SystemVersionInfoService systemVersionInfoService;

    @Override
    public void run(String... args) {
        systemVersionInfoService.recordRuntimeStartup();
    }
}
