package com.aezer0.initialization.utils;

import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider;
import com.itextpdf.io.font.FontProgram;
import com.itextpdf.io.font.FontProgramFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 文档转PDF工具类
 * 支持将TXT和DOC（仅文本内容）转换为PDF文档，使用iText7，支持中文字体。
 * 注意：DOC文件转换仅提取纯文本内容，不保留原文档的复杂格式（如图片、表格、字体样式等）。
 *
 * 作者：模仿原代码结构编写
 */
@Slf4j
public class DocumentToPdfUtils {

    /**
     * 将TXT文件内容转换为PDF
     *
     * @param title 标题
     * @param txtInputStream TXT文件输入流
     * @param author 作者
     * @return PDF字节数组
     */
    public static byte[] convertTxtToPdf(String title, InputStream txtInputStream, String author) {
        try {
            log.info("开始转换TXT到PDF: title={}, author={}", title, author);

            // 1. 读取TXT内容
            String txtContent = new String(txtInputStream.readAllBytes(), StandardCharsets.UTF_8);

            // 2. 将TXT内容转换为HTML (使用 <pre> 标签保留原始格式)
            String htmlContent = convertPlainTextToHtml(txtContent);

            // 3. 构建完整的HTML页面
            String fullHtml = buildFullHtml(title, htmlContent, author);

            // 4. 将HTML转换为PDF
            byte[] pdfBytes = convertHtmlToPdf(fullHtml);

            log.info("TXT转PDF成功，大小: {} bytes", pdfBytes.length);
            return pdfBytes;

        } catch (Exception e) {
            log.error("TXT转PDF失败: title={}, error={}", title, e.getMessage(), e);
            throw new RuntimeException("PDF生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将DOC文件内容转换为PDF (仅提取纯文本内容)
     * 注意：此方法仅提取DOC文件的纯文本内容，不保留原文档的复杂格式（如图片、表格、字体样式等）。
     *
     * @param title 标题
     * @param docInputStream DOC文件输入流
     * @param author 作者
     * @return PDF字节数组
     */
    public static byte[] convertDocToPdf(String title, InputStream docInputStream, String author) {
        try {
            log.info("开始转换DOC到PDF (仅文本内容): title={}, author={}", title, author);

            // 1. 使用Apache POI提取DOC文件的纯文本内容
            String docContent = extractTextFromDoc(docInputStream);

            // 2. 将提取的文本内容转换为HTML (使用 <pre> 标签保留原始格式)
            String htmlContent = convertPlainTextToHtml(docContent);

            // 3. 构建完整的HTML页面
            String fullHtml = buildFullHtml(title, htmlContent, author);

            // 4. 将HTML转换为PDF
            byte[] pdfBytes = convertHtmlToPdf(fullHtml);

            log.info("DOC转PDF成功 (仅文本内容)，大小: {} bytes", pdfBytes.length);
            return pdfBytes;

        } catch (Exception e) {
            log.error("DOC转PDF失败: title={}, error={}", title, e.getMessage(), e);
            throw new RuntimeException("PDF生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将纯文本内容转换为HTML，保留换行符和空格，并进行HTML转义。
     * 使用 <pre> 标签来更好地保留原始文本的格式。
     */
    private static String convertPlainTextToHtml(String plainText) {
        if (plainText == null || plainText.trim().isEmpty()) {
            return "<p>内容为空</p>";
        }
        // 将特殊字符转义，防止XSS和破坏HTML结构
        String escapedText = escapeHtml(plainText);
        // 使用 <pre> 标签可以更好地保留原始文本的格式，包括空格和换行
        return "<pre style=\"white-space: pre-wrap; word-wrap: break-word;\">" + escapedText + "</pre>";
    }

    /**
     * 提取DOC文件的纯文本内容
     */
    private static String extractTextFromDoc(InputStream docInputStream) throws IOException {
        try (HWPFDocument document = new HWPFDocument(docInputStream)) {
            WordExtractor extractor = new WordExtractor(document);
            String text = extractor.getText();
            extractor.close(); // 关闭提取器
            return text;
        }
    }

    /**
     * HTML转义，防止XSS和破坏HTML结构
     */
    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * 构建完整的HTML页面 - 优化中文字体支持
     * (此方法与您提供的PdfUtils中的方法基本相同，直接复用其样式和结构)
     */
    private static String buildFullHtml(String title, String htmlContent, String author) {
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        return String.format("""
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
                <title>%s</title>
                <style>
                    @page {
                        size: A4;
                        margin: 2cm;
                    }

                    body {
                        font-family: "SimSun", "宋体", "Microsoft YaHei", "微软雅黑", "PingFang SC", "Hiragino Sans GB", sans-serif;
                        font-size: 14px;
                        line-height: 1.8;
                        color: #333;
                        margin: 0;
                        padding: 0;
                        background-color: #fff;
                    }

                    .document-container {
                        max-width: 100%%;
                        margin: 0 auto;
                        padding: 20px;
                    }

                    .header {
                        text-align: center;
                        border-bottom: 3px solid #2c3e50;
                        padding-bottom: 20px;
                        margin-bottom: 40px;
                    }

                    .title {
                        font-size: 28px;
                        font-weight: bold;
                        margin-bottom: 15px;
                        color: #2c3e50;
                        line-height: 1.4;
                    }

                    .meta {
                        font-size: 12px;
                        color: #666;
                        margin-bottom: 8px;
                    }

                    .content {
                        text-align: justify;
                        word-wrap: break-word;
                        word-break: break-word;
                    }

                    h1, h2, h3, h4, h5, h6 {
                        color: #2c3e50;
                        margin-top: 35px;
                        margin-bottom: 20px;
                        font-weight: bold;
                        line-height: 1.4;
                    }

                    h1 {
                        font-size: 24px;
                        border-bottom: 2px solid #3498db;
                        padding-bottom: 10px;
                    }
                    h2 {
                        font-size: 20px;
                        border-bottom: 1px solid #bdc3c7;
                        padding-bottom: 8px;
                    }
                    h3 { font-size: 18px; }
                    h4 { font-size: 16px; }
                    h5 { font-size: 15px; }
                    h6 { font-size: 14px; }

                    p {
                        margin-bottom: 16px;
                        text-indent: 2em;
                        line-height: 1.8;
                    }

                    pre { /* 为纯文本内容定制的样式 */
                        background-color: #f8f9fa;
                        padding: 20px;
                        border-radius: 8px;
                        overflow-x: auto;
                        margin: 20px 0;
                        border: 1px solid #e9ecef;
                        white-space: pre-wrap; /* 确保文本自动换行 */
                        word-wrap: break-word; /* 确保长单词也能断开 */
                        font-family: "Consolas", "Monaco", "Courier New", monospace;
                        font-size: 13px;
                        line-height: 1.5;
                        text-indent: 0; /* 移除 <pre> 标签的文本缩进 */
                    }

                    ul, ol {
                        margin-bottom: 16px;
                        padding-left: 40px;
                    }

                    li {
                        margin-bottom: 8px;
                        line-height: 1.6;
                    }

                    blockquote {
                        border-left: 4px solid #3498db;
                        padding-left: 20px;
                        margin: 25px 0;
                        font-style: italic;
                        color: #555;
                        background-color: #f8f9fa;
                        padding: 15px 20px;
                        border-radius: 0 5px 5px 0;
                    }

                    code {
                        background-color: #f1f2f6;
                        padding: 3px 6px;
                        border-radius: 4px;
                        font-family: "Consolas", "Monaco", "Courier New", monospace;
                        font-size: 13px;
                        color: #e74c3c;
                    }

                    pre code {
                        background-color: transparent;
                        padding: 0;
                        color: #333;
                        font-size: 13px;
                    }

                    table {
                        border-collapse: collapse;
                        width: 100%%;
                        margin: 20px 0;
                        border: 1px solid #ddd;
                    }

                    th, td {
                        border: 1px solid #ddd;
                        padding: 12px 15px;
                        text-align: left;
                        vertical-align: top;
                    }

                    th {
                        background-color: #f2f2f2;
                        font-weight: bold;
                        color: #2c3e50;
                    }

                    tr:nth-child(even) {
                        background-color: #f9f9f9;
                    }

                    strong {
                        font-weight: bold;
                        color: #2c3e50;
                    }

                    em {
                        font-style: italic;
                        color: #555;
                    }

                    .footer {
                        margin-top: 60px;
                        padding-top: 25px;
                        border-top: 2px solid #ecf0f1;
                        text-align: center;
                        font-size: 12px;
                        color: #7f8c8d;
                    }
                </style>
            </head>
            <body>
                <div class="document-container">
                    <div class="header">
                        <div class="title">%s</div>
                        <div class="meta">作者：%s</div>
                        <div class="meta">生成时间：%s</div>
                    </div>
                    <div class="content">
                        %s
                    </div>
                    <div class="footer">
                        <p>本文档由笔记系统自动生成</p>
                    </div>
                </div>
            </body>
            </html>
            """, title, title, author != null ? author : "未知", currentTime, htmlContent);
    }

    /**
     * 将HTML转换为PDF - 使用iText7 HTML2PDF
     * (此方法与您提供的PdfUtils中的方法完全相同，直接复用)
     */
    private static byte[] convertHtmlToPdf(String htmlContent) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            // 创建转换器属性
            ConverterProperties properties = new ConverterProperties();

            // 创建字体提供器并添加中文字体支持
            DefaultFontProvider fontProvider = new DefaultFontProvider(true, true, true);

            // 尝试添加系统中文字体
            try {
                // Windows系统字体路径 (您可以根据实际部署环境调整或添加更多字体)
                String[] fontPaths = {
                    "C:/Windows/Fonts/simsun.ttc",      // 宋体
                    "C:/Windows/Fonts/msyh.ttc",        // 微软雅黑
                    "C:/Windows/Fonts/simhei.ttf",      // 黑体
                    "C:/Windows/Fonts/simkai.ttf",      // 楷体
                    "C:/Windows/Fonts/arial.ttf",       // Arial
                    "C:/Windows/Fonts/times.ttf"        // Times
                };

                for (String fontPath : fontPaths) {
                    try {
                        FontProgram fontProgram = FontProgramFactory.createFont(fontPath);
                        fontProvider.addFont(fontProgram);
                        log.debug("成功添加字体: {}", fontPath);
                    } catch (Exception e) {
                        log.debug("字体文件不存在或无法加载: {}", fontPath);
                    }
                }

            } catch (Exception e) {
                log.warn("添加系统字体失败，使用默认字体: {}", e.getMessage());
            }

            // 设置字体提供器
            properties.setFontProvider(fontProvider);

            // 设置字符集
            properties.setCharset(StandardCharsets.UTF_8.name());

            // 执行HTML到PDF的转换
            HtmlConverter.convertToPdf(htmlContent, outputStream, properties);

            return outputStream.toByteArray();
        }
    }

    /**
     * 创建一个MultipartFile对象，用于文件上传
     * (此方法与您提供的PdfUtils中的方法完全相同，直接复用)
     */
    public static MultipartFile createMultipartFile(byte[] content, String filename) {
        return new CustomMultipartFile(content, filename, "application/pdf");
    }

    /**
     * 自定义MultipartFile实现
     * (此内部类与您提供的PdfUtils中的完全相同，直接复用)
     */
    public static class CustomMultipartFile implements MultipartFile {
        private final byte[] content;
        private final String filename;
        private final String contentType;

        public CustomMultipartFile(byte[] content, String filename, String contentType) {
            this.content = content;
            this.filename = filename;
            this.contentType = contentType;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return filename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content == null || content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() throws IOException {
            return content;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            throw new UnsupportedOperationException("Transfer to file not supported");
        }
    }
}
