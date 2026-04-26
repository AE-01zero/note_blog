package com.aezer0.initialization.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aezer0.initialization.config.ai.PgVectorConnectionPool;
import com.aezer0.initialization.domain.FileUpInfo;
import com.aezer0.initialization.domain.User;
import com.aezer0.initialization.dto.ChangePasswordDTO;
import com.aezer0.initialization.dto.LoginDTO;
import com.aezer0.initialization.dto.RegisterDTO;
import com.aezer0.initialization.dto.RegisterSettingsUpdateDTO;
import com.aezer0.initialization.dto.UserProfileUpdateDTO;
import com.aezer0.initialization.result.PageResult;
import com.aezer0.initialization.result.Result;
import com.aezer0.initialization.service.FileInfoService;
import com.aezer0.initialization.service.RegisterSettingService;
import com.aezer0.initialization.service.UserService;
import com.aezer0.initialization.utils.UserUtils;
import com.aezer0.initialization.vo.CaptchaVO;
import com.aezer0.initialization.vo.CurrentUserVO;
import com.aezer0.initialization.vo.RegisterSettingsVO;
import com.aezer0.initialization.vo.UserInfoVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/common")
public class CommonController {

    @Autowired
    private UserService userService;

    @Autowired
    private RegisterSettingService registerSettingService;

    @Autowired
    private FileInfoService fileInfoService;

    @Autowired
    private PgVectorConnectionPool connectionPool;

    @SaIgnore
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO request) {
        userService.register(
                request.getPhone(),
                request.getPassword(),
                request.getCaptcha(),
                request.getCaptchaId(),
                request.getInviteCode()
        );
        return Result.success(null);
    }

    @SaIgnore
    @GetMapping("/register/settings")
    public Result<RegisterSettingsVO> getRegisterSettings() {
        return Result.success(registerSettingService.getPublicSettings());
    }

    @SaCheckLogin
    @GetMapping("/register/admin-settings")
    public Result<RegisterSettingsVO> getAdminRegisterSettings() {
        return Result.success(registerSettingService.getAdminSettings());
    }

    @SaCheckLogin
    @PutMapping("/register/admin-settings")
    public Result<RegisterSettingsVO> updateAdminRegisterSettings(@Valid @RequestBody RegisterSettingsUpdateDTO dto) {
        return Result.success(registerSettingService.updateSettings(dto));
    }

    @SaIgnore
    @GetMapping("/captcha")
    public Result<CaptchaVO> getCaptcha() {
        CaptchaVO captcha = userService.generateCaptcha();
        return Result.success(captcha);
    }

    @SaIgnore
    @PostMapping("/login")
    public Result<String> login(@Valid @RequestBody LoginDTO request) {
        String token = userService.login(
                request.getPhone(),
                request.getPassword(),
                request.getCaptcha(),
                request.getCaptchaId()
        );
        return Result.success(token);
    }

    @SaCheckLogin
    @PostMapping("/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.success(null);
    }

    @SaCheckLogin
    @GetMapping("/user/info")
    public Result<CurrentUserVO> getUserInfo() {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        CurrentUserVO user = userService.getUserInfo(userId);
        return Result.success(user);
    }

    @GetMapping("/user/info/{userId}")
    public Result<UserInfoVO> getUserInfoById(@PathVariable Long userId) {
        UserInfoVO user = userService.getUserInfoById(userId);
        return Result.success(user);
    }

    @SaCheckLogin
    @PutMapping("/user/update")
    public Result<Void> updateUser(@Valid @RequestBody UserProfileUpdateDTO user) {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        userService.updateUserProfile(userId, user);
        return Result.success(null);
    }

    @SaCheckLogin
    @PostMapping("/file/upload")
    public Result<FileUpInfo> uploadFile(@RequestParam("file") MultipartFile file) {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        FileUpInfo fileInfo = fileInfoService.uploadFile(file, userId);
        return Result.success(fileInfo);
    }

    @SaCheckLogin
    @PostMapping("/user/change-password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new com.aezer0.initialization.config.exception.BizException("两次输入的新密码不一致");
        }
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        userService.changePassword(userId, dto.getOldPassword(), dto.getNewPassword());
        return Result.success(null);
    }

    @SaCheckRole("ADMIN")
    @GetMapping("/users")
    public Result<PageResult<User>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer roleType,
            @RequestParam(required = false) String keyword
    ) {
        UserUtils.requireDefaultAdmin();
        Page<User> userPage = userService.listUsers(page, size, roleType, keyword);
        return Result.success(PageResult.convert(userPage));
    }

    @SaCheckRole("ADMIN")
    @GetMapping("/test/connection-pool")
    public Result<Map<String, Object>> testConnectionPool() {
        UserUtils.requireDefaultAdmin();

        Map<String, Object> result = new HashMap<>();
        try {
            result.put("poolStatus", connectionPool.getPoolStatus());
            result.put("connectionTest", connectionPool.testConnection());
            result.put("success", true);
            result.put("message", "连接池状态检查完成");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("message", "连接池状态检查失败");
        }

        return Result.success(result);
    }
}
