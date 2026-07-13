package com.hospital.backend.config;

import com.hospital.backend.entity.*;
import com.hospital.backend.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final MenuMapper menuMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userMapper.count() > 0) {
            log.info("数据库已初始化，跳过...");
            return;
        }

        log.info("开始数据初始化...");

        // 创建角色
        log.info("正在创建默认角色...");
        Role userRole = new Role();
        userRole.setName("R_USER");
        userRole.setDescription("普通用户");
        roleMapper.insert(userRole);

        // 创建菜单
        log.info("正在创建系统菜单...");
        Menu trackingCatalog = createMenu("catalog", "menus.hospital.title", "/hospital", 1, 0L,
                "ri:file-excel-2-line", "Layout", true, "/hospital/reconciliation");
        createMenu("menu", "menus.hospital.reconciliation", "reconciliation", 1, trackingCatalog.getId(),
                "ri:file-excel-2-line", "/hospital/reconciliation", true, null);

        Menu settingsCatalog = createMenu("catalog", "menus.settings.title", "/settings", 3, 0L,
                "ri:settings-3-line", "Layout", true, "/settings/pricing-rules");
        createMenu("menu", "menus.settings.pricingRules", "pricing-rules", 1, settingsCatalog.getId(),
                "ri:price-tag-3-line", "/hospital/pricing-rules", true, null);
        createMenu("menu", "menus.settings.versionManagement", "version-management", 2, settingsCatalog.getId(),
                "ri:history-line", "/hospital/version-management", true, null);

        // 分配菜单权限给普通用户角色
        log.info("正在分配菜单权限...");
        List<Menu> allMenus = menuMapper.selectAll();
        for (Menu menu : allMenus) {
            roleMapper.insertRoleMenu(userRole.getId(), menu.getId());
        }

        // 创建默认用户（固定初始密码，便于开发测试）
        log.info("正在创建默认用户...");
        String defaultPassword = "123456";
        String encodedPassword = passwordEncoder.encode(defaultPassword);
        String[][] defaultUsers = {
            {"user1", "user1@hospital.com"},
            {"user2", "user2@hospital.com"},
        };
        for (String[] u : defaultUsers) {
            User user = new User();
            user.setUsername(u[0]);
            user.setEmail(u[1]);
            user.setPassword(encodedPassword);
            user.setIsActive(true);
            user.setIsSuperuser(false);
            userMapper.insert(user);
            userMapper.insertUserRole(user.getId(), userRole.getId());
            user.setRoles(Set.of(userRole));
            log.info("  用户 {} 已创建", u[0]);
        }
        log.info("默认用户初始密码: {}", defaultPassword);

        log.info("数据初始化完成！");
    }

    private Menu createMenu(String menuType, String name, String path, int order,
                            Long parentId, String icon, String component,
                            boolean keepalive, String redirect) {
        Menu menu = new Menu();
        menu.setMenuType(menuType);
        menu.setName(name);
        menu.setPath(path);
        menu.setOrder(order);
        menu.setParentId(parentId);
        menu.setIcon(icon);
        menu.setComponent(component);
        menu.setKeepalive(keepalive);
        menu.setRedirect(redirect);
        menu.setIsHidden(false);
        menuMapper.insert(menu);
        return menu;
    }
}
