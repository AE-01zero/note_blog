package com.ldd.initialization.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.ldd.initialization.dto.GuidedAnalysisNextDTO;
import com.ldd.initialization.result.Result;
import com.ldd.initialization.service.ai.guided.GuidedAnalysisService;
import com.ldd.initialization.vo.GuidedAnalysisSessionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/ai-analysis/guided")
@Slf4j
public class GuidedAnalysisController {

    @Autowired
    private GuidedAnalysisService guidedAnalysisService;

    @SaCheckLogin
    @PostMapping("/start")
    public Result<GuidedAnalysisSessionVO> startSession(
            @RequestParam("file") MultipartFile file,
            @RequestParam("moduleType") String moduleType,
            @RequestParam(value = "focusAreas", required = false) String focusAreas) {
        try {
            String userId = StpUtil.getLoginIdAsString();
            byte[] fileBytes = file.getBytes();
            String fileName = file.getOriginalFilename();

            GuidedAnalysisSessionVO session = guidedAnalysisService.createSession(
                    fileBytes, fileName, moduleType, focusAreas, userId);

            return Result.success(session);
        } catch (IllegalStateException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("创建分析会话失败", e);
            return Result.error("创建分析会话失败: " + e.getMessage());
        }
    }

    @SaCheckLogin
    @GetMapping(value = "/stream/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamSession(@PathVariable String sessionId) {
        return guidedAnalysisService.getSessionStream(sessionId);
    }

    @SaCheckLogin
    @GetMapping("/session/{sessionId}")
    public Result<GuidedAnalysisSessionVO> getSession(@PathVariable String sessionId) {
        try {
            GuidedAnalysisSessionVO vo = guidedAnalysisService.getSessionState(sessionId);
            if (vo == null) {
                return Result.error("会话不存在");
            }
            return Result.success(vo);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @SaCheckLogin
    @PostMapping("/{sessionId}/next")
    public Result<Void> nextStep(@PathVariable String sessionId,
                                  @RequestBody(required = false) GuidedAnalysisNextDTO dto) {
        try {
            String instruction = dto != null ? dto.getUserInstruction() : null;
            guidedAnalysisService.advanceStep(sessionId, instruction);
            return Result.success();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("执行下一步失败", e);
            return Result.error("执行失败: " + e.getMessage());
        }
    }

    @SaCheckLogin
    @PostMapping("/{sessionId}/skip")
    public Result<Void> skipStep(@PathVariable String sessionId) {
        try {
            guidedAnalysisService.skipStep(sessionId);
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @SaCheckLogin
    @PostMapping("/{sessionId}/terminate")
    public Result<Void> terminateSession(@PathVariable String sessionId) {
        try {
            guidedAnalysisService.terminateSession(sessionId);
            return Result.success();
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
