package com.hospital.backend.security;

import com.hospital.backend.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Spring Security 用户详情实现类
 *
 * 实现 UserDetails 接口，将系统中的自定义 User 实体适配为
 * Spring Security 框架可识别的认证主体（Principal）对象。
 * 这是连接自定义用户模型与 Spring Security 认证机制的关键适配器。
 *
 * 关键设计：
 * - 权限分配：根据 isSuperuser 字段分配 ROLE_SUPER 或 ROLE_USER 角色。
 *   SecurityConfig 中可以通过 hasRole('SUPER') 等方法进行基于角色的访问控制。
 * - 账户启用：isEnabled() 方法返回 User 实体的 isActive 字段值。
 *   被禁用的用户即使持有有效 JWT 令牌也无法通过认证。
 * - 账户/凭据过期：isAccountNonExpired()、isAccountNonLocked()、
 *   isCredentialsNonExpired() 均返回 true，
 *   因为 JWT 令牌本身已经包含了过期验证（exp 声明），无需再次检查。
 * - 密码脱敏：虽然构造时接收了密码，但通过在 User 实体中使用 @JsonIgnore
 *   注解，确保密码不会在 API 响应中泄露。
 */
@Getter
public class UserDetailsImpl implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final String email;
    private final Boolean isSuperuser;
    private final Boolean isActive;
    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * 构造方法 - 从 User 实体构建认证对象
     *
     * 权限分配策略：超级管理员授予 ROLE_SUPER，普通用户授予 ROLE_USER。
     * 在 SecurityConfig 中可以通过 hasRole() 方法进行基于角色的访问控制。
     *
     * @param user 系统用户实体
     */
    public UserDetailsImpl(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.email = user.getEmail();
        this.isSuperuser = user.getIsSuperuser();
        this.isActive = user.getIsActive();
        this.authorities = Collections.singletonList(
                new SimpleGrantedAuthority(user.getIsSuperuser() ? "ROLE_SUPER" : "ROLE_USER"));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    /**
     * 账户是否未过期（始终返回 true，JWT 令牌已包含过期检查）
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 账户是否未锁定（始终返回 true，锁定功能通过 isActive 实现）
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * 凭据是否未过期（始终返回 true，JWT 令牌已包含过期检查）
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 账户是否启用（关联用户实体的 isActive 状态）
     *
     * @return false 表示用户已被禁用，无法认证
     */
    @Override
    public boolean isEnabled() {
        return isActive;
    }
}
