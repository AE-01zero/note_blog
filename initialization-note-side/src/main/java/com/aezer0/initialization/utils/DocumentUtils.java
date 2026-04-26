package com.aezer0.initialization.utils;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
public class DocumentUtils {
    
    /**
     * 创建文档 - 使用最简单稳定的方式
     */
    public static Document createDocumentWithMetadata(String content, Map<String, Object> metadataMap) {
        // 使用最基本的API：只创建内容，不使用复杂的元数据
        return Document.from(content);
    }
    
    /**
     * 分割文档 - 使用手动分割，最稳定的方式
     */
    public static List<Document> splitDocuments(DocumentSplitter splitter, List<Document> documents) {
        List<Document> result = new java.util.ArrayList<>();
        
        for (Document document : documents) {
            // 直接使用手动分割，避免API兼容性问题
            List<Document> splits = manualSplit(document, 500, 100);
            result.addAll(splits);
        }
        
        return result;
    }
    
    /**
     * 手动分割文档 - 修复了逻辑错误，防止无限循环
     */
    private static List<Document> manualSplit(Document document, int chunkSize, int chunkOverlap) {
        List<Document> result = new java.util.ArrayList<>();
        String text = document.text();
        
        if (text == null || text.length() <= chunkSize) {
            result.add(document);
            return result;
        }
        
        int start = 0;
        int textLength = text.length();
        
        // 确保 chunkOverlap 不超过 chunkSize
        chunkOverlap = Math.min(chunkOverlap, chunkSize - 1);
        
        while (start < textLength) {
            int end = Math.min(start + chunkSize, textLength);
            String chunk = text.substring(start, end);
            
            // 使用最简单的Document创建方式
            Document chunkDoc = Document.from(chunk);
            result.add(chunkDoc);
            
            // 修复逻辑：确保 start 总是向前移动
            int nextStart = end - chunkOverlap;
            if (nextStart <= start) {
                // 防止无限循环：如果计算出的下一个开始位置没有前进，则强制前进
                start = start + Math.max(1, chunkSize - chunkOverlap);
            } else {
                start = nextStart;
            }
            
            // 如果已经到达文本末尾，退出循环
            if (end >= textLength) {
                break;
            }
        }
        
        log.info("手动分割文档成功，原长度: {}, 分割成: {} 个片段", textLength, result.size());
        return result;
    }
    
    /**
     * 验证文档内容
     */
    public static boolean isValidDocument(Document document) {
        return document != null && 
               document.text() != null && 
               !document.text().trim().isEmpty();
    }
    
    /**
     * 获取文档摘要信息
     */
    public static String getDocumentSummary(Document document) {
        if (document == null || document.text() == null) {
            return "空文档";
        }
        
        String text = document.text();
        int length = text.length();
        String preview = length > 100 ? text.substring(0, 100) + "..." : text;
        
        return String.format("文档长度: %d 字符, 预览: %s", length, preview);
    }
} 