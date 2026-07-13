package com.hospital.backend.controller;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.LoginRequest;
import com.hospital.backend.dto.request.RefreshTokenRequest;
import com.hospital.backend.dto.response.LoginResponse;
import com.hospital.backend.dto.response.TokenRefreshResponse;
import com.hospital.backend.dto.response.UserInfoResponse;
import com.hospital.backend.entity.Role;
import com.hospital.backend.entity.User;
import com.hospital.backend.mapper.UserMapper;
import com.hospital.backend.security.JwtTokenProvider;
import com.hospital.backend.security.UserDetailsImpl;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/base")
@RequiredArgsConstructor
public class AuthController {

    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.jwt.access-token-expire-minutes}")
    private long accessTokenExpireMinutes;

    @PostMapping("/access_token")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        User user = userMapper.selectByUsername(request.getUsername());

        if (user == null) {
            return Result.fail(400, "无效的用户名");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return Result.fail(400, "密码错误!");
        }

        if (!user.getIsActive()) {
            return Result.fail(400, "用户已被禁用");
        }

        String accessToken = jwtTokenProvider.createAccessToken(
                user.getId(), user.getUsername(), user.getIsSuperuser());
        String refreshToken = jwtTokenProvider.createRefreshToken(
                user.getId(), user.getUsername(), user.getIsSuperuser());

        LoginResponse data = new LoginResponse(
                accessToken, refreshToken, user.getUsername(),
                "bearer", accessTokenExpireMinutes * 60);

        return Result.success(data);
    }

    @PostMapping("/refresh_token")
    public Result<TokenRefreshResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            Claims claims = jwtTokenProvider.parseToken(request.getRefreshToken());
            String tokenType = claims.get("token_type", String.class);

            if (!"refresh".equals(tokenType)) {
                return Result.fail(401, "令牌类型不正确");
            }

            Long userId = claims.get("user_id", Long.class);
            User user = userMapper.selectById(userId);

            if (user == null || !user.getIsActive()) {
                return Result.fail(401, "用户不存在或已被禁用");
            }

            String newAccessToken = jwtTokenProvider.createAccessToken(
                    user.getId(), user.getUsername(), user.getIsSuperuser());
            String newRefreshToken = jwtTokenProvider.createRefreshToken(
                    user.getId(), user.getUsername(), user.getIsSuperuser());

            TokenRefreshResponse data = new TokenRefreshResponse(
                    newAccessToken, newRefreshToken, "bearer", accessTokenExpireMinutes * 60);

            return Result.success(data);

        } catch (Exception e) {
            return Result.fail(401, "令牌无效或已过期");
        }
    }

    @GetMapping("/userinfo")
    public Result<UserInfoResponse> getUserInfo(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userMapper.selectById(userDetails.getId());

        if (user == null) {
            return Result.fail(401, "用户不存在");
        }

        List<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList());

        UserInfoResponse data = new UserInfoResponse(
                user.getId(), user.getUsername(), user.getEmail(), null,
                user.getIsActive(), user.getIsSuperuser(), roleNames,
                user.getCreatedAt(), user.getUpdatedAt(), null);

        return Result.success(data);
    }

    @GetMapping("/health")
    public Result<?> health() {
        return Result.success(java.util.Map.of(
                "status", "healthy",
                "timestamp", java.time.LocalDateTime.now().toString(),
                "service", "Hospital Backend"
        ));
    }

    @GetMapping("/version")
    public Result<?> version() {
        return Result.success(java.util.Map.of(
                "version", "1.0.0",
                "app_title", "Hospital Backend",
                "project_name", "hospital-backend"
        ));
    }
}
