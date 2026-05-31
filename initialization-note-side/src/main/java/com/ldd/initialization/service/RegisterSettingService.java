package com.ldd.initialization.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ldd.initialization.domain.RegisterSetting;
import com.ldd.initialization.dto.RegisterSettingsUpdateDTO;
import com.ldd.initialization.vo.RegisterSettingsVO;

public interface RegisterSettingService extends IService<RegisterSetting> {

    RegisterSettingsVO getPublicSettings();

    RegisterSettingsVO getAdminSettings();

    RegisterSettingsVO updateSettings(RegisterSettingsUpdateDTO dto);

    void validateRegistration(String inviteCode);
}
