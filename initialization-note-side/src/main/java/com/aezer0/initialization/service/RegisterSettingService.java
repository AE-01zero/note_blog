package com.aezer0.initialization.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aezer0.initialization.domain.RegisterSetting;
import com.aezer0.initialization.dto.RegisterSettingsUpdateDTO;
import com.aezer0.initialization.vo.RegisterSettingsVO;

public interface RegisterSettingService extends IService<RegisterSetting> {

    RegisterSettingsVO getPublicSettings();

    RegisterSettingsVO getAdminSettings();

    RegisterSettingsVO updateSettings(RegisterSettingsUpdateDTO dto);

    void validateRegistration(String inviteCode);
}
