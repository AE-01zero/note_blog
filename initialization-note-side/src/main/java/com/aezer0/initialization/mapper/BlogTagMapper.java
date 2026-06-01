package com.aezer0.initialization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aezer0.initialization.domain.BlogTag;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BlogTagMapper extends BaseMapper<BlogTag> {
}
