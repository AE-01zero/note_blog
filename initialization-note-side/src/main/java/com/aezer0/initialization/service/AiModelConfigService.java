package com.aezer0.initialization.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aezer0.initialization.domain.AiModelConfig;
import com.aezer0.initialization.dto.AiModelConfigUpdateDTO;
import com.aezer0.initialization.vo.AiModelReloadResultVO;

import java.util.List;

public interface AiModelConfigService extends IService<AiModelConfig> {

    List<AiModelConfig> listAll();

    AiModelConfig getByType(String type);

    AiModelReloadResultVO saveConfig(String type, AiModelConfigUpdateDTO dto);

    void reloadModel(String type);
}
