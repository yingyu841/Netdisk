package com.netdisk.ai.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.opc.OPCPackage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 二进制文件解析工具类 - 提取文本内容供AI处理
 */
@Slf4j
public class FileParserUtil {

    private static final long MAX_PARSER_SIZE = 50 * 1024 * 1024; // 50MB

    /**
     * 根据文件扩展名解析文件内容
     */
    public static String parseFile(Path filePath, String extension) throws IOException {
        if (filePath == null || !Files.exists(filePath)) {
            throw new FileNotFoundException("文件不存在");
        }

        long size = Files.size(filePath);
        if (size > MAX_PARSER_SIZE) {
            throw new IOException("文件过大，最大支持解析 " + (MAX_PARSER_SIZE / 1024 / 1024) + "MB");
        }

        if (extension == null) {
            extension = getExtension(filePath.getFileName().toString());
        }
        extension = extension.toLowerCase(Locale.ROOT);

        switch (extension) {
            case "docx":
                return parseDocx(filePath);
            case "xlsx":
                return parseXlsx(filePath);
            case "xls":
                return parseXls(filePath);
            case "pdf":
                return parsePdf(filePath);
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "bmp":
            case "webp":
                return parseImage(filePath);
            default:
                throw new IOException("不支持解析的文件类型: " + extension);
        }
    }

    /**
     * 解析 Word (.docx) 文件
     */
    public static String parseDocx(Path filePath) throws IOException {
        StringBuilder content = new StringBuilder();
        try (InputStream is = Files.newInputStream(filePath);
             OPCPackage pkg = OPCPackage.open(is);
             XWPFDocument document = new XWPFDocument(pkg);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {

            content.append("【Word文档内容】\n\n");
            content.append(extractor.getText());

            // 额外提取图片信息
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                for (XWPFRun run : paragraph.getRuns()) {
                    for (XWPFPicture picture : run.getEmbeddedPictures()) {
                        String pictureName = picture.getPictureData().getFileName();
                        content.append("\n[图片: ").append(pictureName).append("]");
                    }
                }
            }
        } catch (Exception e) {
            log.error("parseDocx failed: {}", filePath, e);
            throw new IOException("解析Word文档失败: " + e.getMessage());
        }
        return content.toString();
    }

    /**
     * 解析 Excel (.xlsx) 文件
     */
    public static String parseXlsx(Path filePath) throws IOException {
        StringBuilder content = new StringBuilder();
        try (InputStream is = Files.newInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(is)) {

            content.append("【Excel工作簿内容】\n\n");

            int sheetCount = workbook.getNumberOfSheets();
            content.append("共 ").append(sheetCount).append(" 个工作表\n\n");

            DataFormatter formatter = new DataFormatter();

            for (int i = 0; i < sheetCount; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                content.append("【工作表: ").append(sheet.getSheetName()).append("】\n");

                int lastRow = sheet.getLastRowNum();
                for (int rowNum = 0; rowNum <= lastRow; rowNum++) {
                    Row row = sheet.getRow(rowNum);
                    if (row == null) continue;

                    StringBuilder rowContent = new StringBuilder();
                    boolean hasData = false;

                    for (Cell cell : row) {
                        String cellValue = formatter.formatCellValue(cell);
                        if (cellValue != null && !cellValue.trim().isEmpty()) {
                            hasData = true;
                            rowContent.append("| ").append(cellValue).append(" ");
                        }
                    }

                    if (hasData) {
                        content.append(rowContent.toString()).append("|\n");
                    }
                }
                content.append("\n");
            }
        } catch (Exception e) {
            log.error("parseXlsx failed: {}", filePath, e);
            throw new IOException("解析Excel文档失败: " + e.getMessage());
        }
        return content.toString();
    }

    /**
     * 解析旧版 Excel (.xls) 文件
     */
    public static String parseXls(Path filePath) throws IOException {
        StringBuilder content = new StringBuilder();
        try (InputStream is = Files.newInputStream(filePath);
             Workbook workbook = new HSSFWorkbook(is)) {

            content.append("【Excel工作簿内容(旧版)】\n\n");

            int sheetCount = workbook.getNumberOfSheets();
            content.append("共 ").append(sheetCount).append(" 个工作表\n\n");

            DataFormatter formatter = new DataFormatter();

            for (int i = 0; i < sheetCount; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                content.append("【工作表: ").append(sheet.getSheetName()).append("】\n");

                int lastRow = sheet.getLastRowNum();
                for (int rowNum = 0; rowNum <= lastRow; rowNum++) {
                    Row row = sheet.getRow(rowNum);
                    if (row == null) continue;

                    StringBuilder rowContent = new StringBuilder();
                    boolean hasData = false;

                    for (Cell cell : row) {
                        String cellValue = formatter.formatCellValue(cell);
                        if (cellValue != null && !cellValue.trim().isEmpty()) {
                            hasData = true;
                            rowContent.append("| ").append(cellValue).append(" ");
                        }
                    }

                    if (hasData) {
                        content.append(rowContent.toString()).append("|\n");
                    }
                }
                content.append("\n");
            }
        } catch (Exception e) {
            log.error("parseXls failed: {}", filePath, e);
            throw new IOException("解析旧版Excel文档失败: " + e.getMessage());
        }
        return content.toString();
    }

    /**
     * 解析 PDF 文件
     */
    public static String parsePdf(Path filePath) throws IOException {
        try (PDDocument document = PDDocument.load(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            StringBuilder content = new StringBuilder();
            content.append("【PDF文档内容】\n\n");
            content.append("页数: ").append(document.getNumberOfPages()).append("\n\n");
            content.append(text);

            if (content.length() > 50000) {
                return content.substring(0, 50000) + "\n\n[内容过长，已截断...]";
            }

            return content.toString();
        } catch (Exception e) {
            log.error("parsePdf failed: {}", filePath, e);
            throw new IOException("解析PDF文档失败: " + e.getMessage());
        }
    }

    /**
     * 解析图片文件 - 提取基本信息
     * 注: 完整OCR需要Tesseract，这里仅提取图片基本信息
     */
    public static String parseImage(Path filePath) throws IOException {
        try {
            BufferedImage image = ImageIO.read(filePath.toFile());
            if (image == null) {
                return "[无法读取的图片格式]";
            }

            StringBuilder content = new StringBuilder();
            content.append("【图片信息】\n");
            content.append("宽度: ").append(image.getWidth()).append(" 像素\n");
            content.append("高度: ").append(image.getHeight()).append(" 像素\n");
            content.append("类型: ").append(getImageType(filePath.getFileName().toString())).append("\n");

            // 简单图片描述
            int width = image.getWidth();
            int height = image.getHeight();
            if (width > 4000 || height > 4000) {
                content.append("这是一张高分辨率图片\n");
            } else if (width > 2000 || height > 2000) {
                content.append("这是一张中等分辨率图片\n");
            } else {
                content.append("这是一张标准分辨率图片\n");
            }

            // 尝试获取ARGB信息判断颜色
            long totalPixels = (long) width * height;
            if (totalPixels > 0) {
                content.append("总像素数: ").append(totalPixels).append("\n");
            }

            return content.toString();
        } catch (Exception e) {
            log.error("parseImage failed: {}", filePath, e);
            throw new IOException("解析图片失败: " + e.getMessage());
        }
    }

    private static String getExtension(String filename) {
        if (filename == null) return null;
        int idx = filename.lastIndexOf('.');
        if (idx <= 0 || idx >= filename.length() - 1) return null;
        return filename.substring(idx + 1);
    }

    private static String getImageType(String filename) {
        String ext = getExtension(filename);
        if (ext == null) return "未知";
        switch (ext.toLowerCase()) {
            case "jpg":
            case "jpeg":
                return "JPEG";
            case "png":
                return "PNG";
            case "gif":
                return "GIF";
            case "bmp":
                return "BMP";
            case "webp":
                return "WebP";
            default:
                return ext.toUpperCase();
        }
    }
}
