package com.hospital.backend.config;

import com.hospital.backend.entity.User;
import com.hospital.backend.mapper.UserMapper;
import com.hospital.backend.security.BillingRoles;
import com.hospital.backend.mapper.RoleMapper;
import com.hospital.backend.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 确保超级管理员账号存在（适用于已有 user1/user2 的数据库，DataInitializer 已跳过的场景）。
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class AdminUserInitializer implements CommandLineRunner {

    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_PASSWORD = "admin123";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userMapper.existsByUsername(ADMIN_USERNAME)) {
            return;
        }

        User admin = new User();
        admin.setUsername(ADMIN_USERNAME);
        admin.setEmail("admin@hospital.com");
        admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setIsActive(true);
        admin.setIsSuperuser(true);
        userMapper.insert(admin);

        assignBillingRoleIfPresent(admin.getId(), BillingRoles.CONFIG);
        assignBillingRoleIfPresent(admin.getId(), BillingRoles.OPERATOR);
        assignBillingRoleIfPresent(admin.getId(), BillingRoles.REVIEWER);

        log.info("Seeded superuser account: {} / {}", ADMIN_USERNAME, ADMIN_PASSWORD);
    }

    private void assignBillingRoleIfPresent(Long userId, String roleName) {
        Role role = roleMapper.selectByName(roleName);
        if (role != null) {
            userMapper.insertUserRole(userId, role.getId());
        }
    }
}
