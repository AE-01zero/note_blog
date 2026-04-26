package com.aezer0.initialization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aezer0.initialization.config.exception.BizException;
import com.aezer0.initialization.domain.BlogPostTag;
import com.aezer0.initialization.domain.BlogTag;
import com.aezer0.initialization.dto.BlogTagDTO;
import com.aezer0.initialization.mapper.BlogPostTagMapper;
import com.aezer0.initialization.mapper.BlogTagMapper;
import com.aezer0.initialization.result.BizResponseCode;
import com.aezer0.initialization.service.BlogTagService;
import com.aezer0.initialization.vo.BlogTagVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BlogTagServiceImpl extends ServiceImpl<BlogTagMapper, BlogTag> implements BlogTagService {

    @Autowired
    private BlogPostTagMapper blogPostTagMapper;

    @Override
    public BlogTagVO createTag(BlogTagDTO dto, Long userId) {
        long count = count(new LambdaQueryWrapper<BlogTag>()
                .eq(BlogTag::getName, dto.getName())
                .eq(BlogTag::getUserId, userId));
        if (count > 0) {
            throw new BizException(BizResponseCode.ERROR_1, "标签名称已存在");
        }

        BlogTag tag = new BlogTag();
        tag.setName(dto.getName());
        tag.setUserId(userId);
        tag.setCreateTime(LocalDateTime.now());
        save(tag);
        return convert(tag);
    }

    @Override
    public void deleteTag(Long tagId, Long userId) {
        BlogTag tag = getById(tagId);
        if (tag == null || !userId.equals(tag.getUserId())) {
            throw new BizException(BizResponseCode.ERROR_1, "标签不存在或无权限");
        }
        // 删除关联关系
        blogPostTagMapper.delete(new LambdaQueryWrapper<BlogPostTag>()
                .eq(BlogPostTag::getTagId, tagId));
        removeById(tagId);
    }

    @Override
    public List<BlogTagVO> getMyTags(Long userId) {
        List<BlogTag> list = list(new LambdaQueryWrapper<BlogTag>()
                .eq(BlogTag::getUserId, userId)
                .orderByDesc(BlogTag::getCreateTime));
        return list.stream().map(this::convert).collect(Collectors.toList());
    }

    @Override
    public List<BlogTagVO> getTagsByPostId(Long postId) {
        List<BlogPostTag> postTags = blogPostTagMapper.selectList(
                new LambdaQueryWrapper<BlogPostTag>().eq(BlogPostTag::getPostId, postId));
        if (postTags.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> tagIds = postTags.stream()
                .map(BlogPostTag::getTagId)
                .collect(Collectors.toList());
        return listByIds(tagIds).stream()
                .map(this::convert)
                .collect(Collectors.toList());
    }

    @Override
    public List<BlogTagVO> getAllTags() {

        List<BlogTag> list = list(new LambdaQueryWrapper<BlogTag>()
                .orderByDesc(BlogTag::getCreateTime));

        return list.stream().map(this::convert).collect(Collectors.toList());
    }

    private BlogTagVO convert(BlogTag tag) {
        BlogTagVO vo = new BlogTagVO();
        BeanUtils.copyProperties(tag, vo);
        return vo;
    }
}
