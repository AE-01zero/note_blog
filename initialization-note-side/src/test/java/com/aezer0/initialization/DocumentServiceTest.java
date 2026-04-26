package com.aezer0.initialization;

import com.aezer0.initialization.service.ai.DocumentService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

@SpringBootTest
@Slf4j
public class DocumentServiceTest {

    @Autowired
    private DocumentService documentService;

    @Test
    public void testDocumentParsing() {
        // 创建一个简单的文本文件进行测试
        String content = "这是一个测试文档。\n\n用于验证文档解析功能是否正常工作。\n\n包含中文内容。";
        MockMultipartFile file = new MockMultipartFile(
                "file", 
                "test.txt", 
                "text/plain", 
                content.getBytes()
        );

        try {
            Map<String, Object> result = documentService.uploadAndProcessDocument(file, 1L, 1, null);
            log.info("测试结果: {}", result);
            
            if ((Boolean) result.get("success")) {
                log.info("文档解析测试成功！");
            } else {
                log.error("文档解析测试失败: {}", result.get("message"));
            }
        } catch (Exception e) {
            log.error("测试过程中发生异常: {}", e.getMessage(), e);
        }
    }

    @Test
    public void testInvalidFileType() {
        // 测试不支持的文件类型
        MockMultipartFile file = new MockMultipartFile(
                "file", 
                "test.exe", 
                "application/octet-stream", 
                "fake content".getBytes()
        );

        try {
            Map<String, Object> result = documentService.uploadAndProcessDocument(file, 1L, 1, null);
            log.info("不支持文件类型测试结果: {}", result);
            
            if (!(Boolean) result.get("success")) {
                log.info("不支持文件类型测试成功！");
            } else {
                log.error("不支持文件类型测试失败，应该拒绝此文件类型");
            }
        } catch (Exception e) {
            log.error("测试过程中发生异常: {}", e.getMessage(), e);
        }
    }
} 