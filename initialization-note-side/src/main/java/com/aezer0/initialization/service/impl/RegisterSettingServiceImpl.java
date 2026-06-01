package com.aezer0.initialization.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aezer0.initialization.config.exception.BizException;
import com.aezer0.initialization.domain.RegisterSetting;
import com.aezer0.initialization.dto.RegisterSettingsUpdateDTO;
import com.aezer0.initialization.mapper.RegisterSettingMapper;
import com.aezer0.initialization.service.RegisterSettingService;
import com.aezer0.initialization.utils.UserUtils;
import com.aezer0.initialization.vo.RegisterSettingsVO;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class RegisterSettingServiceImpl extends ServiceImpl<RegisterSettingMapper, RegisterSetting>
        implements RegisterSettingService {

    private static final long SINGLETON_ID = 1L;

    @Override
    public RegisterSettingsVO getPublicSettings() {
        RegisterSetting setting = getOrInit();
        return new RegisterSettingsVO()
                .setRegisterEnabled(Boolean.TRUE.equals(setting.getRegisterEnabled()))
                .setInviteCode(null);
    }

    @Override
    public RegisterSettingsVO getAdminSettings() {
        UserUtils.requireDefaultAdmin();
        return toVO(getOrInit(), true);
    }

    @Override
    public RegisterSettingsVO updateSettings(RegisterSettingsUpdateDTO dto) {
        UserUtils.requireDefaultAdmin();

        RegisterSetting setting = getOrInit();
        String inviteCode = dto.getInviteCode() == null ? null : StrUtil.trim(dto.getInviteCode());
        boolean registerEnabled = Boolean.TRUE.equals(dto.getRegisterEnabled());

        if (registerEnabled && StrUtil.isBlank(inviteCode)) {
            throw new BizException("开启邀请码注册时必须设置邀请码");
        }
        if (StrUtil.length(inviteCode) > 64) {
            throw new BizException("邀请码最大64位");
        }

        setting.setRegisterEnabled(registerEnabled);
        setting.setInviteCode(StrUtil.isBlank(inviteCode) ? null : inviteCode);
        setting.setUpdateTime(new Date());
        saveOrUpdate(setting);
        return toVO(setting, true);
    }

    @Override
    public void validateRegistration(String inviteCode) {
        RegisterSetting setting = getOrInit();
        if (!Boolean.TRUE.equals(setting.getRegisterEnabled())) {
            throw new BizException("当前未开启邀请码注册");
        }

        String normalizedInviteCode = StrUtil.trim(inviteCode);
        if (StrUtil.isBlank(normalizedInviteCode)) {
            throw new BizException("请输入邀请码");
        }
        if (StrUtil.isBlank(setting.getInviteCode())) {
            throw new BizException("邀请码未配置，当前不可注册");
        }
        if (!StrUtil.equals(setting.getInviteCode(), normalizedInviteCode)) {
            throw new BizException("邀请码错误");
        }
    }

    private RegisterSetting getOrInit() {
        RegisterSetting setting = getById(SINGLETON_ID);
        if (setting != null) {
            return setting;
        }

        Date now = new Date();
        setting = new RegisterSetting();
        setting.setId(SINGLETON_ID);
        setting.setRegisterEnabled(Boolean.FALSE);
        setting.setInviteCode(null);
        setting.setCreateTime(now);
        setting.setUpdateTime(now);
        save(setting);
        return setting;
    }

    private RegisterSettingsVO toVO(RegisterSetting setting, boolean includeInviteCode) {
        return new RegisterSettingsVO()
                .setRegisterEnabled(Boolean.TRUE.equals(setting.getRegisterEnabled()))
                .setInviteCode(includeInviteCode ? setting.getInviteCode() : null);
    }
}
