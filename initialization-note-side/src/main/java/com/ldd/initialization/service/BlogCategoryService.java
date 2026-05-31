package com.ldd.initialization.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ldd.initialization.domain.BlogCategory;
import com.ldd.initialization.dto.BlogCategoryDTO;
import com.ldd.initialization.vo.BlogCategoryVO;

import java.util.List;

public interface BlogCategoryService extends IService<BlogCategory> {

    BlogCategoryVO createCategory(BlogCategoryDTO dto, Long userId);

    BlogCategoryVO updateCategory(Long categoryId, BlogCategoryDTO dto, Long userId);

    void deleteCategory(Long categoryId, Long userId);

    List<BlogCategoryVO> getMyCategories(Long userId);

    List<BlogCategoryVO> getAllCategories();
}
