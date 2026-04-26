package com.aezer0.initialization.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aezer0.initialization.config.exception.BizException;
import com.aezer0.initialization.domain.User;
import com.aezer0.initialization.dto.UserProfileUpdateDTO;
import com.aezer0.initialization.enums.RoleType;
import com.aezer0.initialization.enums.StatusEnum;
import com.aezer0.initialization.mapper.UserMapper;
import com.aezer0.initialization.result.BizResponseCode;
import com.aezer0.initialization.service.RegisterSettingService;
import com.aezer0.initialization.service.UserService;
import com.aezer0.initialization.vo.CaptchaVO;
import com.aezer0.initialization.vo.CurrentUserVO;
import com.aezer0.initialization.vo.UserInfoVO;
import io.github.yindz.random.RandomSource;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final String CAPTCHA_PREFIX = "captcha:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RegisterSettingService registerSettingService;

    @Override
    public void register(String phone, String password, String captcha, String captchaId, String inviteCode) {
        String storedCaptcha = redisTemplate.opsForValue().get(CAPTCHA_PREFIX + captchaId);
//        if (storedCaptcha == null || !storedCaptcha.equalsIgnoreCase(captcha)) {
//            throw new BizException("验证码错误或已过期");
//        }

        registerSettingService.validateRegistration(inviteCode);

        if (count(new QueryWrapper<User>().eq("phone", phone)) > 0) {
            throw new BizException("手机号已被注册");
        }

        User user = new User();
        user.setPhone(phone);
        user.setPassword(DigestUtil.sha256Hex(password));
        user.setRoleType(RoleType.USER.getValue());
        user.setStatus(StatusEnum.ENABLED.getCode());
        user.setCreateTime(new Date());
        user.setUpdateTime(new Date());

        String name = RandomSource.personInfoSource().randomChineseName();
        user.setAvatarUrl("https://www.keaitupian.cn/cjpic/frombd/0/253/2221658670/3422894636.jpg");
        user.setUsername(name);
        user.setRealName(name);
        save(user);

        redisTemplate.delete(CAPTCHA_PREFIX + captchaId);
    }

    @Override
    public String login(String phone, String password, String captcha, String captchaId) {
        String storedCaptcha = redisTemplate.opsForValue().get(CAPTCHA_PREFIX + captchaId);
//        if (storedCaptcha == null || !storedCaptcha.equalsIgnoreCase(captcha)) {
//            throw new BizException("验证码错误或已过期");
//        }

        User user = getOne(new QueryWrapper<User>().eq("phone", phone));
        if (user == null) {
            throw new BizException("手机号或密码错误");
        }

        String encryptedPassword = DigestUtil.sha256Hex(password);
        if (!encryptedPassword.equals(user.getPassword())) {
            throw new BizException("手机号或密码错误");
        }

        if (Objects.equals(user.getStatus(), StatusEnum.DISABLED.getCode())) {
            throw new BizException("账号已被禁用");
        }

        String roleTypeStr = RoleType.getDescByValue(user.getRoleType());

        StpUtil.login(user.getId());
        StpUtil.getSession().set("role", roleTypeStr);
        StpUtil.getSession().set("user_info", user);

        user.setLastLoginTime(new Date());
        user.setLastLoginIp("");
        updateById(user);

        redisTemplate.delete(CAPTCHA_PREFIX + captchaId);
        return StpUtil.getTokenValue();
    }

    @Override
    public CurrentUserVO getUserInfo(Long userId) {
        User user = getById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        CurrentUserVO vo = new CurrentUserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    @Override
    public void updateUserProfile(Long userId, UserProfileUpdateDTO dto) {
        User user = getById(userId);
        if (user == null) {
            throw new BizException("user not found");
        }

        if (dto.getUsername() != null) {
            String username = StrUtil.trim(dto.getUsername());
            if (StrUtil.isBlank(username)) {
                throw new BizException("username cannot be blank");
            }
            user.setUsername(username);
        }
        if (dto.getAvatarUrl() != null) {
            user.setAvatarUrl(StrUtil.trim(dto.getAvatarUrl()));
        }
        if (dto.getRealName() != null) {
            String realName = StrUtil.trim(dto.getRealName());
            user.setRealName(StrUtil.isBlank(realName) ? null : realName);
        }
        if (dto.getGender() != null) {
            user.setGender(dto.getGender());
        }
        if (dto.getBirthDate() != null) {
            String birthDate = StrUtil.trim(dto.getBirthDate());
            if (StrUtil.isBlank(birthDate)) {
                user.setBirthDate(null);
            } else {
                try {
                    LocalDate localDate = LocalDate.parse(birthDate);
                    user.setBirthDate(java.sql.Date.valueOf(localDate));
                } catch (DateTimeParseException e) {
                    throw new BizException("birthDate must be yyyy-MM-dd");
                }
            }
        }
        if (dto.getWorkYears() != null) {
            user.setWorkYears(dto.getWorkYears());
        }
        if (dto.getEducation() != null) {
            user.setEducation(dto.getEducation());
        }

        user.setUpdateTime(new Date());
        updateById(user);
        StpUtil.getSession().set("user_info", user);
    }

    @Override
    public Page<User> listUsers(int page, int size, Integer roleType, String keyword) {
        Page<User> userPage = new Page<>(page, size);
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        if (roleType != null) {
            wrapper.eq("role_type", roleType);
        }
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(q -> q.like("username", keyword)
                    .or().like("phone", keyword));
        }
        page(userPage, wrapper);
        return userPage;
    }

    @Override
    public void batchDisableUsers(List<Long> userIds) {
        update(null, new LambdaUpdateWrapper<User>()
                .in(User::getId, userIds)
                .set(User::getStatus, StatusEnum.DISABLED.getCode())
                .set(User::getUpdateTime, new Date()));
    }

    @Override
    public CaptchaVO generateCaptcha() {
        LineCaptcha lineCaptcha = CaptchaUtil.createLineCaptcha(120, 40, 4, 20);
        String code = lineCaptcha.getCode();
        String imageBase64 = lineCaptcha.getImageBase64();
        String captchaId = IdUtil.simpleUUID();

        redisTemplate.opsForValue().set(CAPTCHA_PREFIX + captchaId, code, 5, TimeUnit.MINUTES);

        CaptchaVO response = new CaptchaVO();
        response.setCaptchaId(captchaId);
        response.setImageBase64(imageBase64);
        return response;
    }

    @Override
    public UserInfoVO getUserInfoById(Long userId) {
        User user = baseMapper.selectById(userId);
        UserInfoVO userInfoVO = new UserInfoVO();
        BeanUtils.copyProperties(user, userInfoVO);
        return userInfoVO;
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = getById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (!DigestUtil.sha256Hex(oldPassword).equals(user.getPassword())) {
            throw new BizException(BizResponseCode.ERR_10004, "旧密码错误");
        }
        user.setPassword(DigestUtil.sha256Hex(newPassword));
        user.setUpdateTime(new Date());
        updateById(user);
        StpUtil.logout(userId);
    }

    @Override
    public void batchEnableUsers(List<Long> userIds) {
        update(null, new LambdaUpdateWrapper<User>()
                .in(User::getId, userIds)
                .set(User::getStatus, StatusEnum.ENABLED.getCode())
                .set(User::getUpdateTime, new Date()));
    }
}
