package com.ldd.initialization.service;


import com.baomidou.mybatisplus.extension.service.IService;
import com.ldd.initialization.domain.FileUpInfo;
import org.springframework.web.multipart.MultipartFile;

/**
* @author Administrator
* @description 针对表【file_info】的数据库操作Service
* @createDate 2025-02-21 13:42:24
*/
public interface FileInfoService extends IService<FileUpInfo> {

    FileUpInfo uploadFile(MultipartFile file, Long userId);
}
