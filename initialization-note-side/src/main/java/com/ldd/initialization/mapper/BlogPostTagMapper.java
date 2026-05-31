package com.ldd.initialization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ldd.initialization.domain.BlogPostTag;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BlogPostTagMapper extends BaseMapper<BlogPostTag> {
}
