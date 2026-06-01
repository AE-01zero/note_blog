package com.aezer0.initialization.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.aezer0.initialization.result.Result;
import com.aezer0.initialization.service.tool.ToolManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 工具管理控制器
 * 提供安全分析工具的上传、状态检查和权限管理
 */
@RestController
@RequestMapping("/api/tools")
@Slf4j
public class ToolController {

    @Autowired
    private ToolManagementService toolService;

    // 默认管理员手机号
    private static final String DEFAULT_ADMIN_PHONE = "13800000000";

    /**
     * 上传工具文件（管理员）
     */
    @SaCheckLogin
    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadTool(
            @RequestParam("toolName") String toolName,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "version", defaultValue = "") String version) {

        // 检查是否为默认管理员账号
        if (!isDefaultAdmin()) {
            return Result.error("只有管理员账号(13800000000)才能上传工具插件");
        }

        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        log.info("管理员 {} 上传工具: {}", userId, toolName);

        try {
            Map<String, Object> result = toolService.uploadTool(toolName, file, version);
            if ((boolean) result.getOrDefault("success", false)) {
                return Result.success(result);
            } else {
                return Result.error((String) result.get("error"));
            }
        } catch (Exception e) {
            log.error("工具上传失败", e);
            return Result.error("工具上传失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有工具状态
     */
    @SaCheckLogin
    @GetMapping("/status")
    public Result<Map<String, Object>> getAllToolsStatus() {
        try {
            Map<String, Object> result = toolService.getAllToolsStatus();
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取工具状态失败", e);
            return Result.error("获取工具状态失败");
        }
    }

    /**
     * 获取指定工具状态
     */
    @SaCheckLogin
    @GetMapping("/status/{toolName}")
    public Result<Map<String, Object>> getToolStatus(@PathVariable String toolName) {
        try {
            Map<String, Object> result = toolService.getToolStatus(toolName);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取工具状态失败", e);
            return Result.error("获取工具状态失败");
        }
    }

    /**
     * 删除工具（管理员）
     */
    @SaCheckLogin
    @DeleteMapping("/{toolName}")
    public Result<Map<String, Object>> deleteTool(@PathVariable String toolName) {
        // 检查是否为默认管理员账号
        if (!isDefaultAdmin()) {
            return Result.error("只有管理员账号(13800000000)才能删除工具插件");
        }

        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        log.info("管理员 {} 删除工具: {}", userId, toolName);

        try {
            Map<String, Object> result = toolService.deleteTool(toolName);
            if ((boolean) result.getOrDefault("success", false)) {
                return Result.success(result);
            } else {
                return Result.error((String) result.get("error"));
            }
        } catch (Exception e) {
            log.error("删除工具失败", e);
            return Result.error("删除工具失败: " + e.getMessage());
        }
    }

    /**
     * 测试工具是否可正常执行（管理员）
     */
    @SaCheckLogin
    @PostMapping("/test/{toolName}")
    public Result<Map<String, Object>> testTool(@PathVariable String toolName) {
        if (!isDefaultAdmin()) {
            return Result.error("只有管理员账号(13800000000)才能测试工具插件");
        }

        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        log.info("管理员 {} 测试工具: {}", userId, toolName);

        try {
            Map<String, Object> result = toolService.testTool(toolName);
            if ((boolean) result.getOrDefault("success", false)) {
                return Result.success(result);
            } else {
                return Result.error((String) result.get("error"));
            }
        } catch (Exception e) {
            log.error("工具测试失败", e);
            return Result.error("工具测试失败: " + e.getMessage());
        }
    }

    /**
     * 获取已启用的功能列表
     */
    @SaCheckLogin
    @GetMapping("/enabled-features")
    public Result<List<String>> getEnabledFeatures() {
        try {
            List<String> features = toolService.getEnabledFeatures();
            return Result.success(features);
        } catch (Exception e) {
            log.error("获取功能列表失败", e);
            return Result.error("获取功能列表失败");
        }
    }

    /**
     * 检查功能权限
     */
    @SaCheckLogin
    @GetMapping("/check-permission")
    public Result<Map<String, Object>> checkFeaturePermission(
            @RequestParam("feature") String feature) {

        String userRole = getUserRole();
        try {
            Map<String, Object> result = toolService.checkFeaturePermission(userRole, feature);
            return Result.success(result);
        } catch (Exception e) {
            log.error("检查权限失败", e);
            return Result.error("检查权限失败");
        }
    }

    /**
     * 检查工具是否可用
     */
    @SaCheckLogin
    @GetMapping("/available/{toolName}")
    public Result<Map<String, Object>> isToolAvailable(@PathVariable String toolName) {
        try {
            boolean available = toolService.isToolAvailable(toolName);
            return Result.success(Map.of("available", available, "toolName", toolName));
        } catch (Exception e) {
            log.error("检查工具可用性失败", e);
            return Result.error("检查工具可用性失败");
        }
    }

    /**
     * 获取当前用户角色
     */
    private String getUserRole() {
        try {
            // 从用户信息中获取角色
            Object role = StpUtil.getSession().get("role");
            if (role != null) {
                return role.toString();
            }
        } catch (Exception e) {
            log.debug("获取用户角色失败", e);
        }
        return "USER";
    }

    /**
     * 检查是否为管理员
     */
    private boolean isAdmin() {
        String role = getUserRole();
        return "ADMIN".equalsIgnoreCase(role);
    }

    /**
     * 检查是否为默认管理员账号(13800000000 且 roleType=1)
     */
    private boolean isDefaultAdmin() {
        try {
            Object userObj = StpUtil.getSession().get("user_info");
            if (userObj instanceof com.aezer0.initialization.domain.User) {
                com.aezer0.initialization.domain.User user = (com.aezer0.initialization.domain.User) userObj;
                return DEFAULT_ADMIN_PHONE.equals(user.getPhone())
                        && user.getRoleType() != null
                        && user.getRoleType() == 1;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
