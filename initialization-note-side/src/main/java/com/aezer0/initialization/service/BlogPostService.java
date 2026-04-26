package com.aezer0.initialization.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aezer0.initialization.domain.BlogPost;
import com.aezer0.initialization.dto.BlogPostCreateDTO;
import com.aezer0.initialization.dto.BlogPostQueryDTO;
import com.aezer0.initialization.dto.BlogPostUpdateDTO;
import com.aezer0.initialization.dto.NoteToBlogDTO;
import com.aezer0.initialization.vo.BlogPostListVO;
import com.aezer0.initialization.vo.BlogPostVO;

import java.util.List;

public interface BlogPostService extends IService<BlogPost> {

    BlogPostVO createBlogPost(BlogPostCreateDTO dto, Long userId);

    BlogPostVO updateBlogPost(Long postId, BlogPostUpdateDTO dto, Long userId);

    void deleteBlogPost(Long postId, Long userId);

    BlogPostVO getMyPostDetail(Long postId, Long userId);

    IPage<BlogPostListVO> getMyPosts(BlogPostQueryDTO queryDTO, Long userId);

    IPage<BlogPostListVO> getPublicPosts(BlogPostQueryDTO queryDTO);

    BlogPostVO getPublicPostDetail(Long postId);

    void publishPost(Long postId, Long userId);

    void unpublishPost(Long postId, Long userId);

    BlogPostVO noteToBlog(NoteToBlogDTO dto, Long userId);

    List<BlogPostListVO> getRelatedPosts(Long postId, int limit);
}
