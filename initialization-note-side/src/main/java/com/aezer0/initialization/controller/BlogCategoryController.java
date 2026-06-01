package com.aezer0.initialization.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaIgnore;
import com.aezer0.initialization.dto.BlogCategoryDTO;
import com.aezer0.initialization.result.Result;
import com.aezer0.initialization.service.BlogCategoryService;
import com.aezer0.initialization.utils.UserUtils;
import com.aezer0.initialization.vo.BlogCategoryVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/blog/categories")
public class BlogCategoryController {

    @Autowired
    private BlogCategoryService blogCategoryService;

    @SaCheckLogin
    @PostMapping
    public Result<BlogCategoryVO> create(@Valid @RequestBody BlogCategoryDTO dto) {
        Long userId = UserUtils.getCurrentUserId();
        return Result.success(blogCategoryService.createCategory(dto, userId));
    }

    @SaCheckLogin
    @PutMapping("/{categoryId}")
    public Result<BlogCategoryVO> update(@PathVariable Long categoryId, @Valid @RequestBody BlogCategoryDTO dto) {
        Long userId = UserUtils.getCurrentUserId();
        return Result.success(blogCategoryService.updateCategory(categoryId, dto, userId));
    }

    @SaCheckLogin
    @DeleteMapping("/{categoryId}")
    public Result<Void> delete(@PathVariable Long categoryId) {
        Long userId = UserUtils.getCurrentUserId();
        blogCategoryService.deleteCategory(categoryId, userId);
        return Result.success();
    }

    @SaCheckLogin
    @GetMapping
    public Result<List<BlogCategoryVO>> getMyCategories() {
        Long userId = UserUtils.getCurrentUserId();
        return Result.success(blogCategoryService.getMyCategories(userId));
    }

    /** 公开接口：获取所有分类（无需登录，用于博客首页展示） */
    @SaIgnore
    @GetMapping("/public")
    public Result<List<BlogCategoryVO>> getPublicCategories() {
        return Result.success(blogCategoryService.getAllCategories());
    }
}
