package com.aezer0.initialization.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aezer0.initialization.domain.FileUpInfo;
import org.apache.ibatis.annotations.Mapper;

/**
* @author aezer0
* @description 针对表【file_info】的数据库操作Mapper
* @createDate 2025-02-21 13:42:24
* @Entity com.aezer0.hire.business.entity.FileInfo
*/
@Mapper
public interface FileInfoMapper extends BaseMapper<FileUpInfo> {

}




