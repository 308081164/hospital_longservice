package com.hospital.backend.controller;

import com.hospital.backend.common.Result;
import com.hospital.backend.dto.request.UserCreateRequest;
import com.hospital.backend.entity.Role;
import com.hospital.backend.entity.User;
import com.hospital.backend.mapper.RoleMapper;
import com.hospital.backend.mapper.UserMapper;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/v1/users")
    @Transactional
    public Result<User> createUser(@Valid @RequestBody UserCreateRequest request) {
        if (userMapper.existsByUsername(request.getUsername())) {
            return Result.fail(400, "用户名已存在");
        }
        if (userMapper.existsByEmail(request.getEmail())) {
            return Result.fail(400, "邮箱已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        user.setIsSuperuser(request.getIsSuperuser() != null ? request.getIsSuperuser() : false);

        userMapper.insert(user);

        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            List<Role> roles = roleMapper.selectAllByIds(request.getRoleIds());
            user.setRoles(new HashSet<>(roles));
            for (Long roleId : request.getRoleIds()) {
                userMapper.insertUserRole(user.getId(), roleId);
            }
        } else {
            Role defaultRole = roleMapper.selectByName("R_USER");
            if (defaultRole != null) {
                user.setRoles(Set.of(defaultRole));
                userMapper.insertUserRole(user.getId(), defaultRole.getId());
            }
        }

        return Result.success(user);
    }
}
