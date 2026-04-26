package com.aezer0.initialization.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aezer0.initialization.domain.User;
import com.aezer0.initialization.dto.UserProfileUpdateDTO;
import com.aezer0.initialization.vo.CaptchaVO;
import com.aezer0.initialization.vo.CurrentUserVO;
import com.aezer0.initialization.vo.UserInfoVO;

import java.util.List;

public interface UserService extends IService<User> {

    void register(String phone, String password, String captcha, String captchaId, String inviteCode);

    String login(String phone, String password, String captcha, String captchaId);

    CurrentUserVO getUserInfo(Long userId);

    void updateUserProfile(Long userId, UserProfileUpdateDTO dto);

    Page<User> listUsers(int page, int size, Integer roleType, String keyword);

    void batchDisableUsers(List<Long> userIds);

    CaptchaVO generateCaptcha();

    UserInfoVO getUserInfoById(Long userId);

    void batchEnableUsers(List<Long> userIds);

    void changePassword(Long userId, String oldPassword, String newPassword);
}
