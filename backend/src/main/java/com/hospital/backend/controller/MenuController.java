package com.hospital.backend.controller;

import com.hospital.backend.common.Result;
import com.hospital.backend.entity.Menu;
import com.hospital.backend.mapper.MenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MenuController {

    private final MenuMapper menuMapper;

    @GetMapping("/v3/system/menus")
    public Result<List<Map<String, Object>>> getMenuTree() {
        List<Menu> allMenus = menuMapper.selectAllOrderByOrder();
        List<Map<String, Object>> tree = buildMenuTree(allMenus, 0L);
        return Result.success(tree);
    }

    private List<Map<String, Object>> buildMenuTree(List<Menu> allMenus, Long parentId) {
        return allMenus.stream()
                .filter(menu -> Objects.equals(menu.getParentId(), parentId))
                .map(menu -> {
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("id", menu.getId());
                    node.put("name", menu.getName());
                    node.put("menuType", menu.getMenuType());
                    node.put("icon", menu.getIcon());
                    node.put("path", menu.getPath());
                    node.put("order", menu.getOrder());
                    node.put("parentId", menu.getParentId());
                    node.put("isHidden", menu.getIsHidden());
                    node.put("component", menu.getComponent());
                    node.put("keepalive", menu.getKeepalive());
                    node.put("redirect", menu.getRedirect());

                    List<Map<String, Object>> children = buildMenuTree(allMenus, menu.getId());
                    if (!children.isEmpty()) {
                        node.put("children", children);
                    }

                    return node;
                })
                .collect(Collectors.toList());
    }
}
