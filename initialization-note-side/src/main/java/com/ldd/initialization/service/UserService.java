package com.ldd.initialization.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ldd.initialization.domain.User;
import com.ldd.initialization.dto.UserProfileUpdateDTO;
import com.ldd.initialization.vo.CaptchaVO;
import com.ldd.initialization.vo.CurrentUserVO;
import com.ldd.initialization.vo.UserInfoVO;

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
