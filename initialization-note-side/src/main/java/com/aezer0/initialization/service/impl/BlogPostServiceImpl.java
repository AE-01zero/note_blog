package com.aezer0.initialization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aezer0.initialization.config.exception.BizException;
import com.aezer0.initialization.domain.BlogCategory;
import com.aezer0.initialization.domain.BlogPost;
import com.aezer0.initialization.domain.BlogPostTag;
import com.aezer0.initialization.domain.Note;
import com.aezer0.initialization.dto.BlogPostCreateDTO;
import com.aezer0.initialization.dto.BlogPostQueryDTO;
import com.aezer0.initialization.dto.BlogPostUpdateDTO;
import com.aezer0.initialization.dto.NoteToBlogDTO;
import com.aezer0.initialization.mapper.BlogPostMapper;
import com.aezer0.initialization.mapper.BlogPostTagMapper;
import com.aezer0.initialization.result.BizResponseCode;
import com.aezer0.initialization.service.BlogCategoryService;
import com.aezer0.initialization.service.BlogPostService;
import com.aezer0.initialization.service.BlogTagService;
import com.aezer0.initialization.service.NoteService;
import com.aezer0.initialization.vo.BlogPostListVO;
import com.aezer0.initialization.vo.BlogPostVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BlogPostServiceImpl extends ServiceImpl<BlogPostMapper, BlogPost> implements BlogPostService {

    @Autowired
    private BlogPostTagMapper blogPostTagMapper;

    @Autowired
    private BlogTagService blogTagService;

    @Autowired
    private BlogCategoryService blogCategoryService;

    @Autowired
    private NoteService noteService;

    @Override
    @Transactional
    public BlogPostVO createBlogPost(BlogPostCreateDTO dto, Long userId) {
        BlogPost post = new BlogPost();
        post.setTitle(dto.getTitle());
        post.setSummary(dto.getSummary());
        post.setContentMd(dto.getContentMd());
        post.setCoverUrl(dto.getCoverUrl());
        post.setCategoryId(dto.getCategoryId());
        post.setUserId(userId);
        post.setStatus(0);
        post.setViewCount(0);
        post.setIsTop(dto.getIsTop() != null && dto.getIsTop());
        post.setCreateTime(LocalDateTime.now());
        post.setUpdateTime(LocalDateTime.now());
        save(post);

        savePostTags(post.getId(), dto.getTagIds());
        return convertToVO(post);
    }

    @Override
    @Transactional
    public BlogPostVO updateBlogPost(Long postId, BlogPostUpdateDTO dto, Long userId) {
        BlogPost post = getOwnedPost(postId, userId);
        post.setTitle(dto.getTitle());
        post.setSummary(dto.getSummary());
        post.setContentMd(dto.getContentMd());
        post.setCoverUrl(dto.getCoverUrl());
        post.setCategoryId(dto.getCategoryId());
        post.setIsTop(dto.getIsTop() != null && dto.getIsTop());
        post.setUpdateTime(LocalDateTime.now());
        updateById(post);

        blogPostTagMapper.delete(new LambdaQueryWrapper<BlogPostTag>()
                .eq(BlogPostTag::getPostId, postId));
        savePostTags(postId, dto.getTagIds());

        return convertToVO(post);
    }

    @Override
    @Transactional
    public void deleteBlogPost(Long postId, Long userId) {
        BlogPost post = getOwnedPost(postId, userId);
        blogPostTagMapper.delete(new LambdaQueryWrapper<BlogPostTag>()
                .eq(BlogPostTag::getPostId, postId));
        removeById(post.getId());
    }

    @Override
    public BlogPostVO getMyPostDetail(Long postId, Long userId) {
        BlogPost post = getOwnedPost(postId, userId);
        return convertToVO(post);
    }

    @Override
    public IPage<BlogPostListVO> getMyPosts(BlogPostQueryDTO queryDTO, Long userId) {
        Page<BlogPost> page = buildPage(queryDTO);

        LambdaQueryWrapper<BlogPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BlogPost::getUserId, userId);
        applyQueryFilters(wrapper, queryDTO);
        wrapper.orderByDesc(BlogPost::getIsTop)
                .orderByDesc(BlogPost::getUpdateTime);

        IPage<BlogPost> postPage = page(page, wrapper);
        return convertToListPage(postPage);
    }

    @Override
    public IPage<BlogPostListVO> getPublicPosts(BlogPostQueryDTO queryDTO) {
        Page<BlogPost> page = buildPage(queryDTO);

        LambdaQueryWrapper<BlogPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BlogPost::getStatus, 1);
        wrapper.le(BlogPost::getPublishTime, LocalDateTime.now());
        wrapper.eq(queryDTO.getUserId() != null, BlogPost::getUserId, queryDTO.getUserId());
        wrapper.eq(queryDTO.getCategoryId() != null, BlogPost::getCategoryId, queryDTO.getCategoryId());

        if (queryDTO.getTagId() != null) {
            List<Long> postIds = getPostIdsByTagId(queryDTO.getTagId());
            if (postIds.isEmpty()) {
                return new Page<>(page.getCurrent(), page.getSize(), 0);
            }
            wrapper.in(BlogPost::getId, postIds);
        }

        if (StringUtils.hasText(queryDTO.getKeyword())) {
            wrapper.and(w -> w.like(BlogPost::getTitle, queryDTO.getKeyword())
                    .or()
                    .like(BlogPost::getSummary, queryDTO.getKeyword()));
        }
        wrapper.orderByDesc(BlogPost::getIsTop)
                .orderByDesc(BlogPost::getPublishTime);

        IPage<BlogPost> postPage = page(page, wrapper);
        return convertToListPage(postPage);
    }

    @Override
    public BlogPostVO getPublicPostDetail(Long postId) {
        BlogPost post = getById(postId);
        if (post == null || post.getStatus() == null || post.getStatus() != 1) {
            throw new BizException(BizResponseCode.ERROR_1, "博客不存在或未发布");
        }
        if (post.getPublishTime() != null && post.getPublishTime().isAfter(LocalDateTime.now())) {
            throw new BizException(BizResponseCode.ERROR_1, "博客尚未发布");
        }

        post.setViewCount((post.getViewCount() == null ? 0 : post.getViewCount()) + 1);
        updateById(post);
        return convertToVO(post);
    }

    @Override
    public List<BlogPostListVO> getRelatedPosts(Long postId, int limit) {
        BlogPost current = getById(postId);
        if (current == null || current.getStatus() == null || current.getStatus() != 1) {
            return List.of();
        }

        List<BlogPostListVO> result = new java.util.ArrayList<>();

        // 优先推荐同分类文章
        if (current.getCategoryId() != null) {
            LambdaQueryWrapper<BlogPost> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(BlogPost::getStatus, 1)
                    .le(BlogPost::getPublishTime, LocalDateTime.now())
                    .eq(BlogPost::getCategoryId, current.getCategoryId())
                    .ne(BlogPost::getId, postId)
                    .orderByDesc(BlogPost::getViewCount)
                    .last("LIMIT " + limit);
            List<BlogPost> sameCategoryPosts = list(wrapper);
            result.addAll(sameCategoryPosts.stream().map(this::convertToListVO).collect(Collectors.toList()));
        }

        // 不足则补充热门文章
        if (result.size() < limit) {
            List<Long> excludeIds = result.stream().map(BlogPostListVO::getId).collect(Collectors.toList());
            excludeIds.add(postId);

            LambdaQueryWrapper<BlogPost> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(BlogPost::getStatus, 1)
                    .le(BlogPost::getPublishTime, LocalDateTime.now())
                    .notIn(BlogPost::getId, excludeIds)
                    .orderByDesc(BlogPost::getViewCount)
                    .last("LIMIT " + (limit - result.size()));
            List<BlogPost> hotPosts = list(wrapper);
            result.addAll(hotPosts.stream().map(this::convertToListVO).collect(Collectors.toList()));
        }

        return result;
    }

    @Override
    @Transactional
    public void publishPost(Long postId, Long userId) {
        BlogPost post = getOwnedPost(postId, userId);
        post.setStatus(1);
        if (post.getPublishTime() == null) {
            post.setPublishTime(LocalDateTime.now());
        }
        post.setUpdateTime(LocalDateTime.now());
        updateById(post);
    }

    @Override
    @Transactional
    public void unpublishPost(Long postId, Long userId) {
        BlogPost post = getOwnedPost(postId, userId);
        post.setStatus(0);
        post.setUpdateTime(LocalDateTime.now());
        updateById(post);
    }

    @Override
    @Transactional
    public BlogPostVO noteToBlog(NoteToBlogDTO dto, Long userId) {
        Note note = noteService.getById(dto.getNoteId());
        if (note == null || !userId.equals(note.getUserId())) {
            throw new BizException(BizResponseCode.ERROR_1, "笔记不存在或无权限");
        }

        BlogPost post = new BlogPost();
        post.setTitle(note.getTitle());
        post.setContentMd(note.getContentMd());
        post.setSummary(dto.getSummary());
        post.setCoverUrl(dto.getCoverUrl());
        post.setCategoryId(dto.getCategoryId());
        post.setUserId(userId);
        post.setSourceNoteId(note.getId());
        post.setStatus(0);
        post.setViewCount(0);
        post.setIsTop(dto.getIsTop() != null && dto.getIsTop());
        post.setCreateTime(LocalDateTime.now());
        post.setUpdateTime(LocalDateTime.now());
        save(post);

        savePostTags(post.getId(), dto.getTagIds());
        return convertToVO(post);
    }

    // ========== 私有方法 ==========

    private BlogPost getOwnedPost(Long postId, Long userId) {
        BlogPost post = getById(postId);
        if (post == null || !userId.equals(post.getUserId())) {
            throw new BizException(BizResponseCode.ERROR_1, "博客不存在或无权限");
        }
        return post;
    }

    private void savePostTags(Long postId, List<Long> tagIds) {
        if (CollectionUtils.isEmpty(tagIds)) {
            return;
        }
        for (Long tagId : tagIds) {
            BlogPostTag pt = new BlogPostTag();
            pt.setPostId(postId);
            pt.setTagId(tagId);
            blogPostTagMapper.insert(pt);
        }
    }

    private List<Long> getPostIdsByTagId(Long tagId) {
        LambdaQueryWrapper<BlogPostTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BlogPostTag::getTagId, tagId);
        wrapper.select(BlogPostTag::getPostId);
        return blogPostTagMapper.selectList(wrapper)
                .stream()
                .map(BlogPostTag::getPostId)
                .collect(Collectors.toList());
    }

    private Page<BlogPost> buildPage(BlogPostQueryDTO queryDTO) {
        int current = (queryDTO.getPage() == null || queryDTO.getPage() < 1) ? 1 : queryDTO.getPage();
        int size = (queryDTO.getSize() == null || queryDTO.getSize() < 1 || queryDTO.getSize() > 100) ? 10 : queryDTO.getSize();
        return new Page<>(current, size);
    }

    private void applyQueryFilters(LambdaQueryWrapper<BlogPost> wrapper, BlogPostQueryDTO queryDTO) {
        wrapper.eq(queryDTO.getStatus() != null, BlogPost::getStatus, queryDTO.getStatus());
        wrapper.eq(queryDTO.getCategoryId() != null, BlogPost::getCategoryId, queryDTO.getCategoryId());

        if (queryDTO.getTagId() != null) {
            List<Long> postIds = getPostIdsByTagId(queryDTO.getTagId());
            if (postIds.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(BlogPost::getId, postIds);
            }
        }

        if (StringUtils.hasText(queryDTO.getKeyword())) {
            wrapper.and(w -> w.like(BlogPost::getTitle, queryDTO.getKeyword())
                    .or()
                    .like(BlogPost::getSummary, queryDTO.getKeyword()));
        }
    }

    private BlogPostVO convertToVO(BlogPost post) {
        BlogPostVO vo = new BlogPostVO();
        BeanUtils.copyProperties(post, vo);
        if (post.getCategoryId() != null) {
            BlogCategory category = blogCategoryService.getById(post.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
        }
        vo.setTags(blogTagService.getTagsByPostId(post.getId()));
        return vo;
    }

    private IPage<BlogPostListVO> convertToListPage(IPage<BlogPost> source) {
        Page<BlogPostListVO> target = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        target.setRecords(source.getRecords()
                .stream()
                .map(this::convertToListVO)
                .collect(Collectors.toList()));
        return target;
    }

    private BlogPostListVO convertToListVO(BlogPost post) {
        BlogPostListVO vo = new BlogPostListVO();
        BeanUtils.copyProperties(post, vo);
        if (post.getCategoryId() != null) {
            BlogCategory category = blogCategoryService.getById(post.getCategoryId());
            if (category != null) {
                vo.setCategoryName(category.getName());
            }
        }
        vo.setTags(blogTagService.getTagsByPostId(post.getId()));
        return vo;
    }
}
