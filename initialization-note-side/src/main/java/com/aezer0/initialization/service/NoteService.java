package com.aezer0.initialization.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aezer0.initialization.domain.Note;
import com.aezer0.initialization.dto.NoteCreateDTO;
import com.aezer0.initialization.dto.NoteUpdateDTO;
import com.aezer0.initialization.result.Result;
import com.aezer0.initialization.vo.NoteBriefVO;
import com.aezer0.initialization.vo.NoteDetailVO;

import java.util.List;
import java.util.Map;

/**
 * 笔记Service接口
 */
public interface NoteService extends IService<Note> {

    /**
     * 创建笔记
     * @param createDTO 创建DTO
     * @param userId 用户ID
     * @return 笔记详情
     */
    NoteDetailVO createNote(NoteCreateDTO createDTO, Long userId);

    /**
     * 分页查询笔记列表
     * @param page 页码
     * @param size 每页大小
     * @param notebookId 笔记本ID
     * @param userId 用户ID
     * @param keyword 搜索关键词
     * @return 笔记列表
     */
    IPage<NoteBriefVO> getNotesByPage(int page, int size, Long notebookId, Long userId, String keyword);

    /**
     * 获取笔记详情
     * @param noteId 笔记ID
     * @param userId 用户ID
     * @return 笔记详情
     */
    NoteDetailVO getNoteDetail(Long noteId, Long userId);

    /**
     * 更新笔记
     * @param noteId 笔记ID
     * @param updateDTO 更新DTO
     * @param userId 用户ID
     * @return 笔记详情
     */
    NoteDetailVO updateNote(Long noteId, NoteUpdateDTO updateDTO, Long userId);

    /**
     * 删除笔记
     * @param noteId 笔记ID
     * @param userId 用户ID
     */
    void deleteNote(Long noteId, Long userId);

    /**
     * 切换笔记置顶状态
     * @param noteId 笔记ID
     * @param userId 用户ID
     */
    void togglePinNote(Long noteId, Long userId);

    /**
     * 获取用户的所有标签
     * @param userId 用户ID
     * @return 标签列表
     */
    List<String> getUserTags(Long userId);

    /**
     * 验证笔记是否属于用户
     * @param noteId 笔记ID
     * @param userId 用户ID
     * @return 是否属于用户
     */
    boolean isNoteOwner(Long noteId, Long userId);

    Result<Map<String, Object>> addNoteToPersonalKnowledgeBase(Long noteId, Long userId);

    /**
     * 从MD文件内容导入笔记
     * @param notebookId 笔记本ID
     * @param fileName 文件名
     * @param content MD文件内容
     * @param userId 用户ID
     * @return 笔记详情
     */
    NoteDetailVO importFromMarkdown(Long notebookId, String fileName, String content, Long userId);

    /**
     * 导出笔记为Markdown内容
     * @param noteId 笔记ID
     * @param userId 用户ID
     * @return MD内容
     */
    String exportToMarkdown(Long noteId, Long userId);

    /**
     * AI优化笔记格式
     * @param noteId 笔记ID
     * @param userId 用户ID
     * @return 优化后的MD内容
     */
    String optimizeNoteFormat(Long noteId, Long userId);
}