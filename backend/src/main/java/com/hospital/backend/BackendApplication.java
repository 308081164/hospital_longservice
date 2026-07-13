package com.hospital.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 医院发票系统后端服务启动类
 *
 * 使用 @SpringBootApplication 组合注解，等价于以下三个注解的集合：
 * - @Configuration：将该类标记为 Java Config 配置类，允许定义 @Bean
 * - @EnableAutoConfiguration：根据 classpath 依赖自动配置 Spring Boot
 * - @ComponentScan：自动扫描 com.hospital.backend 包及其子包下的组件
 *
 * 应用配置分层加载：
 * - application.yml：通用配置（所有环境共享）
 * - application-dev.yml：开发环境配置（覆盖通用配置）
 * - application-prod.yml：生产环境配置（覆盖通用配置）
 *
 * 启动入口：运行此类即可启动嵌入式 Tomcat 并加载整个 Spring 上下文。
 */
@SpringBootApplication
public class BackendApplication {
    /**
     * 应用入口方法
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
