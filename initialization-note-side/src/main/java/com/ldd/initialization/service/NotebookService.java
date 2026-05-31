package com.ldd.initialization.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ldd.initialization.domain.Notebook;
import com.ldd.initialization.dto.NotebookCreateDTO;
import com.ldd.initialization.vo.NotebookVO;

import java.util.List;

/**
 * 笔记本Service接口
 */
public interface NotebookService extends IService<Notebook> {

    /**
     * 创建笔记本
     * @param createDTO 创建DTO
     * @param userId 用户ID
     * @return 笔记本信息
     */
    NotebookVO createNotebook(NotebookCreateDTO createDTO, Long userId);

    /**
     * 获取用户的笔记本列表
     * @param userId 用户ID
     * @return 笔记本列表
     */
    List<NotebookVO> getUserNotebooks(Long userId);

    /**
     * 更新笔记本
     * @param notebookId 笔记本ID
     * @param createDTO 更新DTO
     * @param userId 用户ID
     * @return 笔记本信息
     */
    NotebookVO updateNotebook(Long notebookId, NotebookCreateDTO createDTO, Long userId);

    /**
     * 删除笔记本
     * @param notebookId 笔记本ID
     * @param userId 用户ID
     */
    void deleteNotebook(Long notebookId, Long userId);

    /**
     * 验证笔记本是否属于用户
     * @param notebookId 笔记本ID
     * @param userId 用户ID
     * @return 是否属于用户
     */
    boolean isNotebookOwner(Long notebookId, Long userId);
} 