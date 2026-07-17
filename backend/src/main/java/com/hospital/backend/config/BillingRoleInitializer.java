package com.hospital.backend.config;

import com.hospital.backend.entity.Role;
import com.hospital.backend.mapper.RoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * NFR-04：初始化特色账单细分角色（配置员 / 业务员 / 审核员）。
 */
@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
public class BillingRoleInitializer implements CommandLineRunner {

    private final RoleMapper roleMapper;

    @Override
    public void run(String... args) {
        ensureRole("R_BILLING_CONFIG", "特色账单配置员");
        ensureRole("R_BILLING_OPERATOR", "特色账单业务员");
        ensureRole("R_BILLING_REVIEWER", "特色账单审核员");
    }

    private void ensureRole(String name, String description) {
        if (roleMapper.selectByName(name) != null) {
            return;
        }
        Role role = new Role();
        role.setName(name);
        role.setDescription(description);
        roleMapper.insert(role);
        log.info("Seeded billing role: {}", name);
    }
}
