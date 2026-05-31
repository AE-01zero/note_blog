package com.ldd.initialization.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ldd.initialization.domain.AiModelConfig;
import com.ldd.initialization.dto.AiModelConfigUpdateDTO;
import com.ldd.initialization.vo.AiModelReloadResultVO;

import java.util.List;

public interface AiModelConfigService extends IService<AiModelConfig> {

    List<AiModelConfig> listAll();

    AiModelConfig getByType(String type);

    AiModelReloadResultVO saveConfig(String type, AiModelConfigUpdateDTO dto);

    void reloadModel(String type);
}
