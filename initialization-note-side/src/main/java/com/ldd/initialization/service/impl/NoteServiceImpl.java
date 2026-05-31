package com.ldd.initialization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ldd.initialization.config.exception.BizException;
import com.ldd.initialization.domain.FileUpInfo;
import com.ldd.initialization.domain.Note;
import com.ldd.initialization.domain.NoteTag;
import com.ldd.initialization.dto.NoteCreateDTO;
import com.ldd.initialization.dto.NoteUpdateDTO;
import com.ldd.initialization.enums.FileSourceTypeEnum;
import com.ldd.initialization.mapper.NoteMapper;
import com.ldd.initialization.mapper.NoteTagMapper;
import com.ldd.initialization.result.BizResponseCode;
import com.ldd.initialization.result.Result;
import com.ldd.initialization.domain.BlogCategory;
import com.ldd.initialization.domain.Notebook;
import com.ldd.initialization.service.BlogCategoryService;
import com.ldd.initialization.service.FileInfoService;
import com.ldd.initialization.service.NoteService;
import com.ldd.initialization.service.NotebookService;
import com.ldd.initialization.service.ai.DocumentService;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import com.ldd.initialization.utils.PdfUtils;
import com.ldd.initialization.utils.UserUtils;
import com.ldd.initialization.vo.NoteBriefVO;
import com.ldd.initialization.vo.NoteDetailVO;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 笔记Service实现类
 */
@Service
@Slf4j
public class NoteServiceImpl extends ServiceImpl<NoteMapper, Note> implements NoteService {

    @Autowired
    private NotebookService notebookService;

    @Autowired
    private BlogCategoryService blogCategoryService;

    @Autowired
    private NoteTagMapper noteTagMapper;

    @Autowired
    private DocumentService documentService;
    @Autowired
    private FileInfoService fileInfoService;

    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("openAiChatModel")
    private ChatModel openAiChatModel;
    // Flexmark 配置
    private final MutableDataSet options = new MutableDataSet();
    private final Parser parser = Parser.builder(options).build();
    private final HtmlRenderer renderer = HtmlRenderer.builder(options).build();

    @Override
    @Transactional
    public NoteDetailVO createNote(NoteCreateDTO createDTO, Long userId) {
        // 验证笔记本是否存在且属于用户
        if (!notebookService.isNotebookOwner(createDTO.getNotebookId(), userId)) {
            throw new BizException(BizResponseCode.ERROR_1, "笔记本不存在或无权限");
        }

        // 校验同笔记本内标题唯一性
        LambdaQueryWrapper<Note> titleCheck = new LambdaQueryWrapper<>();
        titleCheck.eq(Note::getNotebookId, createDTO.getNotebookId())
                  .eq(Note::getTitle, createDTO.getTitle());
        if (this.count(titleCheck) > 0) {
            throw new BizException(BizResponseCode.ERROR_1, "该笔记本下已存在同名笔记，请重命名");
        }

        // 创建笔记
        Note note = new Note();
        BeanUtils.copyProperties(createDTO, note);
        note.setUserId(userId);
        note.setViewCount(0);
        note.setWordCount(calculateWordCount(createDTO.getContentMd()));

        // 设置默认值
        if (note.getStatus() == null) {
            note.setStatus(1); // 默认已发布
        }
        if (note.getIsPinned() == null) {
            note.setIsPinned(false);
        }

        this.save(note);

        // 保存标签
        saveTags(note.getId(), createDTO.getTags());

        // 返回详情
        return getNoteDetail(note.getId(), userId);
    }

    @Override
    public IPage<NoteBriefVO> getNotesByPage(int page, int size, Long notebookId, Long userId, String keyword) {
        Page<NoteBriefVO> pageObj = new Page<>(page, size);
        IPage<NoteBriefVO> result = this.baseMapper.selectNotesBriefByPage(pageObj, notebookId, userId, keyword);

        // 为每个笔记添加标签信息
        result.getRecords().forEach(note -> {
            List<String> tags = noteTagMapper.selectTagsByNoteId(note.getId());
            note.setTags(tags);
        });

        return result;
    }

    @Override
    public NoteDetailVO getNoteDetail(Long noteId, Long userId) {
        // 查询笔记详情
        NoteDetailVO noteDetail = this.baseMapper.selectNoteDetailById(noteId, userId);
        if (noteDetail == null) {
            throw new BizException(BizResponseCode.ERROR_1, "笔记不存在或无权限");
        }

        // 转换Markdown为HTML
//        if (StringUtils.hasText(noteDetail.getContentMd())) {
//            Node document = parser.parse(noteDetail.getContentMd());
//            String html = renderer.render(document);
//            noteDetail.setContentHtml(html);
//        }

        // 查询标签
        List<String> tags = noteTagMapper.selectTagsByNoteId(noteId);
        noteDetail.setTags(tags);

        // 增加查看次数
        this.baseMapper.incrementViewCount(noteId);

        return noteDetail;
    }

    @Override
    @Transactional
    public NoteDetailVO updateNote(Long noteId, NoteUpdateDTO updateDTO, Long userId) {
        // 验证笔记是否存在且属于用户
        Note note = this.getById(noteId);
        if (note == null || !note.getUserId().equals(userId)) {
            throw new BizException(BizResponseCode.ERROR_1, "笔记不存在或无权限");
        }

        // 校验同笔记本内标题唯一性（排除自身）
        if (updateDTO.getTitle() != null && !updateDTO.getTitle().equals(note.getTitle())) {
            LambdaQueryWrapper<Note> titleCheck = new LambdaQueryWrapper<>();
            titleCheck.eq(Note::getNotebookId, note.getNotebookId())
                      .eq(Note::getTitle, updateDTO.getTitle())
                      .ne(Note::getId, noteId);
            if (this.count(titleCheck) > 0) {
                throw new BizException(BizResponseCode.ERROR_1, "该笔记本下已存在同名笔记，请重命名");
            }
        }

        // 更新笔记信息
        BeanUtils.copyProperties(updateDTO, note);
        note.setWordCount(calculateWordCount(updateDTO.getContentMd()));
        note.setCreateTime(LocalDateTime.now());
        note.setUpdateTime(LocalDateTime.now());
        this.saveOrUpdate(note);

        // 更新标签
        noteTagMapper.deleteTagsByNoteId(noteId);
        saveTags(noteId, updateDTO.getTags());

        // 返回详情
        return getNoteDetail(noteId, userId);
    }

    @Override
    @Transactional
    public void deleteNote(Long noteId, Long userId) {
        // 验证笔记是否存在且属于用户
        if (!isNoteOwner(noteId, userId)) {
            throw new BizException(BizResponseCode.ERROR_1, "笔记不存在或无权限");
        }

        // 删除笔记（标签会通过外键级联删除）
        this.removeById(noteId);
    }

    @Override
    @Transactional
    public void togglePinNote(Long noteId, Long userId) {
        // 验证笔记是否存在且属于用户
        Note note = this.getById(noteId);
        if (note == null || !note.getUserId().equals(userId)) {
            throw new BizException(BizResponseCode.ERROR_1, "笔记不存在或无权限");
        }

        // 切换置顶状态
        note.setIsPinned(!note.getIsPinned());
        this.updateById(note);
    }

    @Override
    public List<String> getUserTags(Long userId) {
        return noteTagMapper.selectDistinctTagsByUserId(userId);
    }

    @Override
    public boolean isNoteOwner(Long noteId, Long userId) {
        LambdaQueryWrapper<Note> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Note::getId, noteId)
                .eq(Note::getUserId, userId);
        return this.count(wrapper) > 0;
    }

    /**
     * 保存标签
     */
    private void saveTags(Long noteId, List<String> tags) {
        if (!CollectionUtils.isEmpty(tags)) {
            for (String tagName : tags) {
                if (StringUtils.hasText(tagName)) {
                    NoteTag noteTag = new NoteTag();
                    noteTag.setNoteId(noteId);
                    noteTag.setTagName(tagName.trim());
                    noteTagMapper.insert(noteTag);
                }
            }
        }
    }

    /**
     * 计算字数
     */
    private Integer calculateWordCount(String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }
        // 简单的字数统计，去除空格和换行
        return content.replaceAll("\\s+", "").length();
    }

    @Override
    @Transactional
    public Result<Map<String, Object>> addNoteToPersonalKnowledgeBase(Long noteId, Long userId) {
        try {
            // 1. 验证笔记是否存在且属于用户
            Note note = this.getById(noteId);
            if (note == null || !note.getUserId().equals(userId)) {
                log.warn("笔记 {} 不存在或不属于用户 {}", noteId, userId);
                return Result.error("笔记不存在或无权限");
            }

            // 2. 计算当前笔记内容的MD5
            String currentNoteContentMd5 = DigestUtils.md5Hex(note.getContentMd().getBytes(StandardCharsets.UTF_8));

            // 3. 检查是否有旧记录，MD5不变则跳过
            FileUpInfo existingPdfFileInfo = getExistingNotePdfFileInfo(noteId, userId);
            if (existingPdfFileInfo != null) {
                if (currentNoteContentMd5.equals(existingPdfFileInfo.getFileMd5())) {
                    log.info("笔记 {} 内容未更改，跳过重新处理。", noteId);
                    return Result.error("笔记内容未更改，无需重新添加到知识库");
                } else {
                    log.info("笔记 {} 内容已更改，清理旧记录。", noteId);
                    Map<String, Object> deleteVectorResult = documentService.deleteDocumentVectors(
                            existingPdfFileInfo.getId(), userId, 1);
                    if (!(Boolean) deleteVectorResult.getOrDefault("success", false)) {
                        throw new RuntimeException("删除旧向量失败: " + deleteVectorResult.get("message"));
                    }
                    fileInfoService.removeById(existingPdfFileInfo.getId());
                }
            }

            // 4. 上传.md文件并保存记录（同步，快速）
            String filename = note.getTitle() + ".md";
            byte[] mdBytes = note.getContentMd().getBytes(StandardCharsets.UTF_8);
            PdfUtils.CustomMultipartFile mdFile = new PdfUtils.CustomMultipartFile(
                    mdBytes, filename, "text/markdown");
            FileUpInfo newFileInfo = fileInfoService.uploadFile(mdFile, userId);

            newFileInfo.setSourceType(FileSourceTypeEnum.NOTE_TO_PDF.getValue());
            newFileInfo.setSourceNoteId(noteId);
            newFileInfo.setSourceNoteTitle(note.getTitle());
            newFileInfo.setFileMd5(currentNoteContentMd5);
            newFileInfo.setUpdateTime(LocalDateTime.now());
            if (note.getNotebookId() != null) {
                Notebook notebook = notebookService.getById(note.getNotebookId());
                if (notebook != null && notebook.getCategoryId() != null) {
                    BlogCategory category = blogCategoryService.getById(notebook.getCategoryId());
                    if (category != null) {
                        newFileInfo.setCategory(category.getName());
                    }
                }
            }
            fileInfoService.updateById(newFileInfo);

            // 5. 异步执行向量嵌入（耗时的embedding API调用放到后台）
            final Long fileId = newFileInfo.getId();
            final String contentMd = note.getContentMd();
            final String title = note.getTitle();
            CompletableFuture.runAsync(() ->
                    processNoteEmbedding(noteId, contentMd, title, userId, fileId),
                    taskExecutor
            );

            log.info("笔记 {} 文件已上传(fid={}), 向量化任务已提交后台执行", noteId, fileId);

            Map<String, Object> successData = new HashMap<>();
            successData.put("noteId", noteId);
            successData.put("noteTitle", note.getTitle());
            successData.put("fileId", fileId);
            successData.put("fileName", filename);
            successData.put("fileSize", newFileInfo.getFileSize());
            successData.put("status", "processing");
            successData.put("message", "文件已保存，向量化正在后台处理中，稍后可在知识库中检索");
            return Result.success(successData);

        } catch (Exception e) {
            log.error("将笔记 {} 添加到个人知识库失败: {}", noteId, e.getMessage(), e);
            return Result.error("添加失败: " + e.getMessage());
        }
    }

    /**
     * 异步执行笔记向量嵌入 — 耗时的embedding API调用在后台完成
     */
    @Async("taskExecutor")
    public void processNoteEmbedding(Long noteId, String contentMd, String title, Long userId, Long fileId) {
        try {
            log.info("开始异步处理笔记 {} (fid={}) 的向量化...", noteId, fileId);
            Map<String, Object> processResult = documentService.processMarkdownTextForUser(
                    contentMd, title, userId, fileId);
            log.info("笔记 {} (fid={}) 向量化完成: {}", noteId, fileId, processResult);
        } catch (Exception e) {
            log.error("笔记 {} (fid={}) 异步向量化失败: {}", noteId, fileId, e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public NoteDetailVO importFromMarkdown(Long notebookId, String fileName, String content, Long userId) {
        if (!notebookService.isNotebookOwner(notebookId, userId)) {
            throw new BizException(BizResponseCode.ERROR_1, "笔记本不存在或无权限");
        }
        // 从文件名中提取标题（去掉.md后缀）
        String title = fileName.endsWith(".md") ? fileName.substring(0, fileName.length() - 3) : fileName;

        Note note = new Note();
        note.setNotebookId(notebookId);
        note.setUserId(userId);
        note.setTitle(title);
        note.setContentMd(content);
        note.setStatus(1);
        note.setIsPinned(false);
        note.setViewCount(0);
        note.setWordCount(calculateWordCount(content));
        this.save(note);
        return getNoteDetail(note.getId(), userId);
    }

    @Override
    public String exportToMarkdown(Long noteId, Long userId) {
        Note note = this.getById(noteId);
        if (note == null || !note.getUserId().equals(userId)) {
            throw new BizException(BizResponseCode.ERROR_1, "笔记不存在或无权限");
        }
        return note.getContentMd() == null ? "" : note.getContentMd();
    }

    @Override
    public String optimizeNoteFormat(Long noteId, Long userId) {
        Note note = this.getById(noteId);
        if (note == null || !note.getUserId().equals(userId)) {
            throw new BizException(BizResponseCode.ERROR_1, "笔记不存在或无权限");
        }
        String content = note.getContentMd();
        if (!StringUtils.hasText(content)) {
            throw new BizException(BizResponseCode.ERROR_1, "笔记内容为空，无法优化");
        }
        final int CHUNK_SIZE = 3000;
        if (content.length() <= CHUNK_SIZE) {
            // 内容较短，直接优化
            return doOptimize(content, "format_opt_" + noteId + "_" + userId);
        }
        // 内容较长，按段落分块优化后合并
        List<String> chunks = splitIntoChunks(content, CHUNK_SIZE);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            String optimized = doOptimize(chunks.get(i), "format_opt_" + noteId + "_" + userId + "_" + i);
            result.append(optimized);
            if (i < chunks.size() - 1) {
                result.append("\n\n");
            }
        }
        return result.toString();
    }

    private String doOptimize(String content, String memoryId) {
        String systemPrompt = "你是小助手!";
        String userPrompt = "请帮我优化以下Markdown笔记的格式和结构，使其更加清晰、规范、易读。" +
                "要求：\n1. 保持原有内容不变，只优化格式\n2. 合理使用标题层级\n3. 优化列表和代码块格式\n" +
                "4. 确保段落间距合理\n5. 直接返回优化后的Markdown内容，不要添加任何说明。\n\n原文内容：\n" + content;
        return openAiChatModel.chat(
                java.util.List.of(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from(userPrompt)
                )
        ).aiMessage().text();
    }

    private List<String> splitIntoChunks(String content, int chunkSize) {
        List<String> chunks = new java.util.ArrayList<>();
        // 先按标题行分割
        String[] paragraphs = content.split("(?=\\n#{1,6} )");
        StringBuilder current = new StringBuilder();
        for (String para : paragraphs) {
            // 如果单个段落本身超过chunkSize，强制按字符切分
            if (para.length() > chunkSize) {
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                    current = new StringBuilder();
                }
                int start = 0;
                while (start < para.length()) {
                    // 尝试在换行处切分
                    int end = Math.min(start + chunkSize, para.length());
                    if (end < para.length()) {
                        int newline = para.lastIndexOf('\n', end);
                        if (newline > start) end = newline + 1;
                    }
                    chunks.add(para.substring(start, end).trim());
                    start = end;
                }
            } else if (current.length() + para.length() > chunkSize && current.length() > 0) {
                chunks.add(current.toString().trim());
                current = new StringBuilder();
                current.append(para);
            } else {
                current.append(para);
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

/**
 * 检查笔记是否已经转换为PDF并添加到知识库，并返回对应的FileUpInfo。
 * 如果存在，则返回FileUpInfo对象；否则返回null。
 */
public FileUpInfo getExistingNotePdfFileInfo(Long noteId, Long userId) {
    LambdaQueryWrapper<FileUpInfo> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(FileUpInfo::getUserId, userId)
            .eq(FileUpInfo::getSourceType, FileSourceTypeEnum.NOTE_TO_PDF.getValue())
            .eq(FileUpInfo::getSourceNoteId, noteId);
    return fileInfoService.getOne(wrapper); // 使用getOne获取单个记录
}

public boolean isNoteConvertedToPdf(Long noteId, Long userId) {
    LambdaQueryWrapper<FileUpInfo> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(FileUpInfo::getUserId, userId)
            .eq(FileUpInfo::getSourceType, FileSourceTypeEnum.NOTE_TO_PDF.getValue())
            .eq(FileUpInfo::getSourceNoteId, noteId);
    return fileInfoService.count(wrapper) > 0;
}



} 