package com.aezer0.initialization.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.aezer0.initialization.dto.NoteAutoSaveDTO;
import com.aezer0.initialization.dto.NoteCreateDTO;
import com.aezer0.initialization.dto.NoteUpdateDTO;
import com.aezer0.initialization.result.PageResult;
import com.aezer0.initialization.result.Result;
import com.aezer0.initialization.service.NoteOptimizeTaskService;
import com.aezer0.initialization.service.NoteService;
import com.aezer0.initialization.utils.UserUtils;
import com.aezer0.initialization.vo.NoteBriefVO;
import com.aezer0.initialization.vo.NoteDetailVO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 笔记Controller
 */
@RestController
@RequestMapping("/api/notes")
@SaCheckLogin
public class NoteController {

    @Autowired
    private NoteService noteService;

    @Autowired
    private NoteOptimizeTaskService noteOptimizeTaskService;

    /**
     * 创建笔记
     */
    @PostMapping
    public Result<NoteDetailVO> createNote(@Valid @RequestBody NoteCreateDTO createDTO) {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        NoteDetailVO note = noteService.createNote(createDTO, userId);
        return Result.success(note);
    }

    /**
     * 分页查询笔记列表
     */
    @GetMapping
    public Result<PageResult<NoteBriefVO>> getNotes(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long notebookId,
            @RequestParam(required = false) String keyword) {
        
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        IPage<NoteBriefVO> notePages = noteService.getNotesByPage(page, size, notebookId, userId, keyword);
        return Result.success(PageResult.convert(notePages));
    }

    /**
     * 获取笔记详情
     */
    @GetMapping("/{noteId}")
    public Result<NoteDetailVO> getNoteDetail(@PathVariable Long noteId) {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        NoteDetailVO note = noteService.getNoteDetail(noteId, userId);
        return Result.success(note);
    }

    /**
     * 更新笔记
     */
    @PostMapping("/update")
    public Result<NoteDetailVO> updateNote(@Valid @RequestBody NoteUpdateDTO updateDTO) {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        NoteDetailVO note = noteService.updateNote(updateDTO.getNoteId(), updateDTO, userId);
        return Result.success(note);
    }

    /**
     * 删除笔记
     */
    @DeleteMapping("/{noteId}")
    public Result<Void> deleteNote(@PathVariable Long noteId) {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        noteService.deleteNote(noteId, userId);
        return Result.success(null);
    }

    /**
     * 切换笔记置顶状态
     */
    @PostMapping("/{noteId}/pin")
    public Result<Void> togglePinNote(@PathVariable Long noteId) {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        noteService.togglePinNote(noteId, userId);
        return Result.success(null);
    }

    /**
     * 获取用户的所有标签
     */
    @GetMapping("/tags")
    public Result<List<String>> getUserTags() {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        List<String> tags = noteService.getUserTags(userId);
        return Result.success(tags);
    }

    /**
     * 自动保存笔记内容（用于编辑器自动保存）
     */
    @PostMapping("/autosave")
    public Result<Void> autoSaveNote(@Valid @RequestBody NoteAutoSaveDTO autoSaveDTO) {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        
        // 创建简单的更新DTO，只更新内容
        NoteUpdateDTO updateDTO = new NoteUpdateDTO();
        updateDTO.setNoteId(autoSaveDTO.getNoteId());
        updateDTO.setContentMd(autoSaveDTO.getContentMd());
        
        // 获取当前笔记信息，保留其他字段
        NoteDetailVO currentNote = noteService.getNoteDetail(autoSaveDTO.getNoteId(), userId);
        updateDTO.setTitle(currentNote.getTitle());
        updateDTO.setStatus(currentNote.getStatus());
        updateDTO.setIsPinned(currentNote.getIsPinned());
        updateDTO.setTags(currentNote.getTags());
        
        noteService.updateNote(autoSaveDTO.getNoteId(), updateDTO, userId);
        return Result.success(null);
    }

    /**
     * 将笔记添加到个人知识库
     */
    @PostMapping("/{noteId}/add-to-knowledge-base")
    public Result<Map<String, Object>> addNoteToPersonalKnowledgeBase(@PathVariable Long noteId) {
        Long userId = UserUtils.getCurrentUserId();
       return noteService.addNoteToPersonalKnowledgeBase(noteId, userId);
    }

    /**
     * 导入MD文件为笔记
     */
    @PostMapping("/import-md")
    public Result<NoteDetailVO> importMarkdown(
            @RequestParam Long notebookId,
            @RequestParam MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            return Result.error("上传文件不能为空");
        }
        if (file.getSize() > 100L * 1024 * 1024) {
            return Result.error("文件大小不能超过100MB");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".md")) {
            return Result.error("仅支持导入 .md 文件");
        }

        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        NoteDetailVO note = noteService.importFromMarkdown(notebookId, fileName, content, userId);
        return Result.success(note);
    }

    /**
     * 导出笔记为MD文件
     */
    @GetMapping("/{noteId}/export-md")
    public ResponseEntity<byte[]> exportMarkdown(@PathVariable Long noteId) {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        String content = noteService.exportToMarkdown(noteId, userId);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", "note.md");
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    /**
     * 提交AI优化笔记格式异步任务
     */
    @PostMapping("/{noteId}/optimize-format")
    public Result<String> optimizeNoteFormat(@PathVariable Long noteId) {
        Long userId = Long.valueOf(StpUtil.getLoginId().toString());
        String content = noteService.exportToMarkdown(noteId, userId);
        String taskId = noteOptimizeTaskService.submitTask(noteId, userId, content);
        return Result.success(taskId);
    }

    /**
     * 查询AI优化任务状态和结果（只查不删）
     */
    @GetMapping("/optimize-task/{taskId}")
    public Result<Map<String, Object>> getOptimizeTask(@PathVariable String taskId) {
        NoteOptimizeTaskService.TaskResult task = noteOptimizeTaskService.getTask(taskId);
        if (task == null) {
            return Result.error("任务不存在或已过期");
        }
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("status", task.status);
        data.put("progress", task.progress);
        data.put("totalChunks", task.totalChunks);
        data.put("completedChunks", task.completedChunks);
        data.put("updateTime", task.updateTime);
        data.put("stage", task.stage);
        data.put("stageLabel", task.stageLabel);
        if ("done".equals(task.status)) {
            data.put("applied", true);
        } else if ("error".equals(task.status)) {
            data.put("error", task.error);
        }
        return Result.success(data);
    }

    /**
     * 手动删除优化任务
     */
    @DeleteMapping("/optimize-task/{taskId}")
    public Result<Void> deleteOptimizeTask(@PathVariable String taskId) {
        noteOptimizeTaskService.removeTask(taskId);
        return Result.success(null);
    }
} 
