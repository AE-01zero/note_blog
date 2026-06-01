package com.aezer0.initialization.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.aezer0.initialization.dto.AiModelConfigUpdateDTO;
import com.aezer0.initialization.result.Result;
import com.aezer0.initialization.service.AiModelConfigService;
import com.aezer0.initialization.utils.UserUtils;
import com.aezer0.initialization.vo.AiModelConfigVO;
import com.aezer0.initialization.vo.AiModelReloadResultVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai-config")
@SaCheckLogin
public class AiModelConfigController {

    @Autowired
    private AiModelConfigService aiModelConfigService;

    @GetMapping
    public Result<List<AiModelConfigVO>> listAll() {
        UserUtils.requireDefaultAdmin();
        List<AiModelConfigVO> list = aiModelConfigService.listAll()
                .stream()
                .map(AiModelConfigVO::from)
                .collect(Collectors.toList());
        return Result.success(list);
    }

    @PutMapping("/{type}")
    public Result<AiModelReloadResultVO> updateConfig(@PathVariable String type,
                                                      @Valid @RequestBody AiModelConfigUpdateDTO dto) {
        UserUtils.requireDefaultAdmin();
        AiModelReloadResultVO result = aiModelConfigService.saveConfig(type, dto);
        if (result.getStatus() != null && result.getStatus() == 2) {
            return Result.<AiModelReloadResultVO>error(result.getMessage()).setData(result);
        }
        return Result.success(result);
    }
}
