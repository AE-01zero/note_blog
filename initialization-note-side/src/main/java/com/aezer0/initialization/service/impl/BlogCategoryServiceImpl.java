package com.aezer0.initialization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aezer0.initialization.config.exception.BizException;
import com.aezer0.initialization.domain.BlogCategory;
import com.aezer0.initialization.dto.BlogCategoryDTO;
import com.aezer0.initialization.mapper.BlogCategoryMapper;
import com.aezer0.initialization.result.BizResponseCode;
import com.aezer0.initialization.service.BlogCategoryService;
import com.aezer0.initialization.vo.BlogCategoryVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BlogCategoryServiceImpl extends ServiceImpl<BlogCategoryMapper, BlogCategory> implements BlogCategoryService {

    @Override
    public BlogCategoryVO createCategory(BlogCategoryDTO dto, Long userId) {
        // 检查同名分类
        long count = count(new LambdaQueryWrapper<BlogCategory>()
                .eq(BlogCategory::getName, dto.getName())
                .eq(BlogCategory::getUserId, userId));
        if (count > 0) {
            throw new BizException(BizResponseCode.ERROR_1, "分类名称已存在");
        }

        BlogCategory category = new BlogCategory();
        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        category.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        category.setUserId(userId);
        category.setCreateTime(LocalDateTime.now());
        category.setUpdateTime(LocalDateTime.now());
        save(category);
        return convert(category);
    }

    @Override
    public BlogCategoryVO updateCategory(Long categoryId, BlogCategoryDTO dto, Long userId) {
        BlogCategory category = getOwnedCategory(categoryId, userId);

        // 检查同名（排除自身）
        long count = count(new LambdaQueryWrapper<BlogCategory>()
                .eq(BlogCategory::getName, dto.getName())
                .eq(BlogCategory::getUserId, userId)
                .ne(BlogCategory::getId, categoryId));
        if (count > 0) {
            throw new BizException(BizResponseCode.ERROR_1, "分类名称已存在");
        }

        category.setName(dto.getName());
        category.setDescription(dto.getDescription());
        if (dto.getSortOrder() != null) {
            category.setSortOrder(dto.getSortOrder());
        }
        category.setUpdateTime(LocalDateTime.now());
        updateById(category);
        return convert(category);
    }

    @Override
    public void deleteCategory(Long categoryId, Long userId) {
        BlogCategory category = getOwnedCategory(categoryId, userId);
        removeById(category.getId());
    }

    @Override
    public List<BlogCategoryVO> getMyCategories(Long userId) {
        List<BlogCategory> list = list(new LambdaQueryWrapper<BlogCategory>()
                .eq(BlogCategory::getUserId, userId)
                .orderByAsc(BlogCategory::getSortOrder)
                .orderByDesc(BlogCategory::getCreateTime));
        return list.stream().map(this::convert).collect(Collectors.toList());
    }

    @Override
    public List<BlogCategoryVO> getAllCategories() {
        List<BlogCategory> list = list(new LambdaQueryWrapper<BlogCategory>()
                .orderByAsc(BlogCategory::getSortOrder)
                .orderByDesc(BlogCategory::getCreateTime));
        return list.stream().map(this::convert).collect(Collectors.toList());
    }

    private BlogCategory getOwnedCategory(Long categoryId, Long userId) {
        BlogCategory category = getById(categoryId);
        if (category == null || !userId.equals(category.getUserId())) {
            throw new BizException(BizResponseCode.ERROR_1, "分类不存在或无权限");
        }
        return category;
    }

    private BlogCategoryVO convert(BlogCategory category) {
        BlogCategoryVO vo = new BlogCategoryVO();
        BeanUtils.copyProperties(category, vo);
        return vo;
    }
}
