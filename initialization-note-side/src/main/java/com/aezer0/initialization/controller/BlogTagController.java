package com.aezer0.initialization.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.aezer0.initialization.dto.BlogTagDTO;
import com.aezer0.initialization.result.Result;
import com.aezer0.initialization.service.BlogTagService;
import com.aezer0.initialization.utils.UserUtils;
import com.aezer0.initialization.vo.BlogTagVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/blog/tags")
public class BlogTagController {

    @Autowired
    private BlogTagService blogTagService;

    @SaCheckLogin
    @PostMapping
    public Result<BlogTagVO> create(@Valid @RequestBody BlogTagDTO dto) {
        UserUtils.requireDefaultAdmin();
        Long userId = UserUtils.getCurrentUserId();
        return Result.success(blogTagService.createTag(dto, userId));
    }

    @SaCheckLogin
    @DeleteMapping("/{tagId}")
    public Result<Void> delete(@PathVariable Long tagId) {
        UserUtils.requireDefaultAdmin();
        Long userId = UserUtils.getCurrentUserId();
        blogTagService.deleteTag(tagId, userId);
        return Result.success();
    }

    // @SaCheckLogin 无需登录直接get标签即可
    // 后台管理标签但前台公开展示 
    @GetMapping
    public Result<List<BlogTagVO>> getAllTags() {
        // Long userId = UserUtils.getCurrentUserId();
        return Result.success(blogTagService.getAllTags());
    }
}
