package com.aezer0.initialization.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aezer0.initialization.domain.BlogTag;
import com.aezer0.initialization.dto.BlogTagDTO;
import com.aezer0.initialization.vo.BlogTagVO;

import java.util.List;

public interface BlogTagService extends IService<BlogTag> {

    BlogTagVO createTag(BlogTagDTO dto, Long userId);

    void deleteTag(Long tagId, Long userId);

    List<BlogTagVO> getMyTags(Long userId);

    List<BlogTagVO> getTagsByPostId(Long postId);

    List<BlogTagVO> getAllTags();
}
