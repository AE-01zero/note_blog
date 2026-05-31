package com.ldd.initialization.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ldd.initialization.domain.BlogTag;
import com.ldd.initialization.dto.BlogTagDTO;
import com.ldd.initialization.vo.BlogTagVO;

import java.util.List;

public interface BlogTagService extends IService<BlogTag> {

    BlogTagVO createTag(BlogTagDTO dto, Long userId);

    void deleteTag(Long tagId, Long userId);

    List<BlogTagVO> getMyTags(Long userId);

    List<BlogTagVO> getTagsByPostId(Long postId);

    List<BlogTagVO> getAllTags();
}
