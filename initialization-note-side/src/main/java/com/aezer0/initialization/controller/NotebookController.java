package com.aezer0.initialization.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.aezer0.initialization.config.exception.BizException;
import com.aezer0.initialization.dto.NotebookCreateDTO;
import com.aezer0.initialization.result.BizResponseCode;
import com.aezer0.initialization.result.Result;
import com.aezer0.initialization.service.NotebookService;
import com.aezer0.initialization.utils.UserUtils;
import com.aezer0.initialization.vo.NotebookVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 笔记本Controller
 */
@RestController
@RequestMapping("/api/notebooks")
@SaCheckLogin
public class NotebookController {

    @Autowired
    private NotebookService notebookService;

    /**
     * 创建笔记本
     */
    @PostMapping
    public Result<NotebookVO> createNotebook(@Valid @RequestBody NotebookCreateDTO createDTO) {
        Long userId = UserUtils.getCurrentUserId();
        NotebookVO notebook = notebookService.createNotebook(createDTO, userId);
        return Result.success(notebook);
    }

    /**
     * 获取用户的笔记本列表
     */
    @GetMapping
    public Result<List<NotebookVO>> getUserNotebooks() {
        Long userId = UserUtils.getCurrentUserId();
        List<NotebookVO> notebooks = notebookService.getUserNotebooks(userId);
        return Result.success(notebooks);
    }

    /**
     * 获取笔记本详情
     */
    @GetMapping("/{notebookId}")
    public Result<NotebookVO> getNotebook(@PathVariable Long notebookId) {
        Long userId = UserUtils.getCurrentUserId();

        // 验证权限并获取笔记本信息
        List<NotebookVO> notebooks = notebookService.getUserNotebooks(userId);
        NotebookVO notebook = notebooks.stream()
                .filter(nb -> nb.getId().equals(notebookId))
                .findFirst()
                .orElse(null);

        if (notebook == null) {
            return Result.error(new BizException(BizResponseCode.ERROR_1, "笔记本不存在"));
        }

        return Result.success(notebook);
    }

    /**
     * 更新笔记本
     */
    @PutMapping("/{notebookId}")
    public Result<NotebookVO> updateNotebook(@PathVariable Long notebookId,
                                             @Valid @RequestBody NotebookCreateDTO updateDTO) {
        Long userId = UserUtils.getCurrentUserId();
        NotebookVO notebook = notebookService.updateNotebook(notebookId, updateDTO, userId);
        return Result.success(notebook);
    }

    /**
     * 删除笔记本
     */
    @DeleteMapping("/{notebookId}")
    public Result<Void> deleteNotebook(@PathVariable Long notebookId) {
        Long userId = UserUtils.getCurrentUserId();
        notebookService.deleteNotebook(notebookId, userId);
        return Result.success(null);
    }
} 