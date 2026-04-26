package com.aezer0.initialization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aezer0.initialization.config.exception.BizException;
import com.aezer0.initialization.domain.Notebook;
import com.aezer0.initialization.dto.NotebookCreateDTO;
import com.aezer0.initialization.mapper.NotebookMapper;
import com.aezer0.initialization.result.BizResponseCode;
import com.aezer0.initialization.service.NotebookService;
import com.aezer0.initialization.vo.NotebookVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 笔记本Service实现类
 */
@Service
public class NotebookServiceImpl extends ServiceImpl<NotebookMapper, Notebook> implements NotebookService {

    @Override
    @Transactional
    public NotebookVO createNotebook(NotebookCreateDTO createDTO, Long userId) {
        // 检查用户是否已有同名笔记本
        LambdaQueryWrapper<Notebook> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notebook::getUserId, userId)
               .eq(Notebook::getName, createDTO.getName());
        
        if (this.count(wrapper) > 0) {
            throw new BizException(BizResponseCode.ERROR_1, "笔记本名称已存在");
        }

        // 创建笔记本
        Notebook notebook = new Notebook();
        BeanUtils.copyProperties(createDTO, notebook);
        notebook.setUserId(userId);

        // 如果没有设置排序字段，设置为当前时间戳
        if (notebook.getSortOrder() == null) {
            notebook.setSortOrder(0);
        }

        this.save(notebook);

        return buildNotebookVO(notebook.getId(), userId);
    }

    @Override
    public List<NotebookVO> getUserNotebooks(Long userId) {
        return this.baseMapper.selectNotebooksByUserId(userId);
    }

    @Override
    @Transactional
    public NotebookVO updateNotebook(Long notebookId, NotebookCreateDTO createDTO, Long userId) {
        // 验证笔记本是否存在且属于用户
        Notebook notebook = this.getById(notebookId);
        if (notebook == null || !notebook.getUserId().equals(userId)) {
            throw new BizException(BizResponseCode.ERROR_1, "笔记本不存在或无权限");
        }

        // 检查是否有同名笔记本（排除自己）
        LambdaQueryWrapper<Notebook> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notebook::getUserId, userId)
               .eq(Notebook::getName, createDTO.getName())
               .ne(Notebook::getId, notebookId);
        
        if (this.count(wrapper) > 0) {
            throw new BizException(BizResponseCode.ERROR_1, "笔记本名称已存在");
        }

        // 更新笔记本信息
        BeanUtils.copyProperties(createDTO, notebook);
        this.updateById(notebook);

        return buildNotebookVO(notebookId, userId);
    }

    @Override
    @Transactional
    public void deleteNotebook(Long notebookId, Long userId) {
        // 验证笔记本是否存在且属于用户
        if (!isNotebookOwner(notebookId, userId)) {
            throw new BizException(BizResponseCode.ERROR_1, "笔记本不存在或无权限");
        }

        // 删除笔记本（由于设置了外键级联删除，笔记也会被删除）
        this.removeById(notebookId);
    }

    @Override
    public boolean isNotebookOwner(Long notebookId, Long userId) {
        LambdaQueryWrapper<Notebook> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notebook::getId, notebookId)
               .eq(Notebook::getUserId, userId);
        return this.count(wrapper) > 0;
    }

    private NotebookVO buildNotebookVO(Long notebookId, Long userId) {
        Optional<NotebookVO> notebookVO = this.baseMapper.selectNotebooksByUserId(userId).stream()
                .filter(item -> item.getId().equals(notebookId))
                .findFirst();

        if (notebookVO.isPresent()) {
            return notebookVO.get();
        }

        Notebook notebook = this.getById(notebookId);
        NotebookVO fallback = new NotebookVO();
        BeanUtils.copyProperties(notebook, fallback);
        fallback.setNoteCount(0);
        return fallback;
    }
} 
