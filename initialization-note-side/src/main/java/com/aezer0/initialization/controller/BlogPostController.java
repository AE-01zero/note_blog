package com.aezer0.initialization.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaIgnore;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.aezer0.initialization.dto.BlogPostCreateDTO;
import com.aezer0.initialization.dto.BlogPostQueryDTO;
import com.aezer0.initialization.dto.BlogPostUpdateDTO;
import com.aezer0.initialization.dto.NoteToBlogDTO;
import com.aezer0.initialization.result.PageResult;
import com.aezer0.initialization.result.Result;
import com.aezer0.initialization.service.BlogPostService;
import com.aezer0.initialization.utils.UserUtils;
import com.aezer0.initialization.vo.BlogPostListVO;
import com.aezer0.initialization.vo.BlogPostVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/blog")
public class BlogPostController {

    @Autowired
    private BlogPostService blogPostService;

    @SaCheckLogin
    @PostMapping("/posts")
    public Result<BlogPostVO> createPost(@Valid @RequestBody BlogPostCreateDTO dto) {
        UserUtils.requireDefaultAdmin();
        Long userId = UserUtils.getCurrentUserId();
        return Result.success(blogPostService.createBlogPost(dto, userId));
    }

    @SaCheckLogin
    @PutMapping("/posts/{postId}")
    public Result<BlogPostVO> updatePost(@PathVariable Long postId, @Valid @RequestBody BlogPostUpdateDTO dto) {
        UserUtils.requireDefaultAdmin();
        Long userId = UserUtils.getCurrentUserId();
        return Result.success(blogPostService.updateBlogPost(postId, dto, userId));
    }

    @SaCheckLogin
    @DeleteMapping("/posts/{postId}")
    public Result<Void> deletePost(@PathVariable Long postId) {
        UserUtils.requireDefaultAdmin();
        Long userId = UserUtils.getCurrentUserId();
        blogPostService.deleteBlogPost(postId, userId);
        return Result.success();
    }

    @SaCheckLogin
    @GetMapping("/posts/{postId}")
    public Result<BlogPostVO> getMyPostDetail(@PathVariable Long postId) {
        UserUtils.requireDefaultAdmin();
        Long userId = UserUtils.getCurrentUserId();
        return Result.success(blogPostService.getMyPostDetail(postId, userId));
    }

    @SaCheckLogin
    @GetMapping("/posts/mine")
    public Result<PageResult<BlogPostListVO>> getMyPosts(BlogPostQueryDTO queryDTO) {
        UserUtils.requireDefaultAdmin();
        Long userId = UserUtils.getCurrentUserId();
        IPage<BlogPostListVO> page = blogPostService.getMyPosts(queryDTO, userId);
        return Result.success(PageResult.convert(page));
    }

    @SaCheckLogin
    @PostMapping("/posts/{postId}/publish")
    public Result<Void> publishPost(@PathVariable Long postId) {
        UserUtils.requireDefaultAdmin();
        Long userId = UserUtils.getCurrentUserId();
        blogPostService.publishPost(postId, userId);
        return Result.success();
    }

    @SaCheckLogin
    @PostMapping("/posts/{postId}/unpublish")
    public Result<Void> unpublishPost(@PathVariable Long postId) {
        UserUtils.requireDefaultAdmin();
        Long userId = UserUtils.getCurrentUserId();
        blogPostService.unpublishPost(postId, userId);
        return Result.success();
    }

    @SaCheckLogin
    @PostMapping("/posts/from-note")
    public Result<BlogPostVO> noteToBlog(@Valid @RequestBody NoteToBlogDTO dto) {
        UserUtils.requireDefaultAdmin();
        Long userId = UserUtils.getCurrentUserId();
        return Result.success(blogPostService.noteToBlog(dto, userId));
    }

    @SaIgnore
    @GetMapping("/public")
    public Result<PageResult<BlogPostListVO>> getPublicPosts(BlogPostQueryDTO queryDTO) {
        IPage<BlogPostListVO> page = blogPostService.getPublicPosts(queryDTO);
        return Result.success(PageResult.convert(page));
    }

    @SaIgnore
    @GetMapping("/public/{postId}")
    public Result<BlogPostVO> getPublicPostDetail(@PathVariable Long postId) {
        return Result.success(blogPostService.getPublicPostDetail(postId));
    }

    @SaIgnore
    @GetMapping("/public/{postId}/related")
    public Result<List<BlogPostListVO>> getRelatedPosts(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "6") int limit) {
        return Result.success(blogPostService.getRelatedPosts(postId, Math.min(limit, 20)));
    }
}
