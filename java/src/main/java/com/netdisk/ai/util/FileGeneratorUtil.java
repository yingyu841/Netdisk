package com.netdisk.ai.util;

import com.lowagie.text.Document;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * 二进制文件生成工具类
 */
@Slf4j
public class FileGeneratorUtil {

    /**
     * 根据扩展名生成文件
     */
    public static byte[] generateFile(String filename, String content) throws IOException {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IOException("文件名不能为空");
        }

        String extension = getExtension(filename);
        if (extension == null) {
            extension = "txt";
        }
        extension = extension.toLowerCase(Locale.ROOT);

        switch (extension) {
            case "docx":
                return generateDocx(filename, content);
            case "xlsx":
                return generateXlsx(filename, content);
            case "pdf":
                return generatePdf(filename, content);
            case "jpg":
            case "jpeg":
            case "png":
                return generateImage(filename, content);
            default:
                throw new IOException("不支持生成的文件类型: " + extension + "，支持的类型: docx, xlsx, pdf, jpg, png");
        }
    }

    /**
     * 生成 Word (.docx) 文件
     */
    public static byte[] generateDocx(String filename, String content) throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            // 添加标题
            XWPFParagraph titleParagraph = document.createParagraph();
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText(filename);
            titleRun.setBold(true);
            titleRun.setFontSize(16);

            // 添加分隔线
            document.createParagraph();

            // 解析内容并添加段落
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) {
                    document.createParagraph();
                } else {
                    XWPFParagraph para = document.createParagraph();
                    XWPFRun run = para.createRun();
                    run.setText(line);
                    run.setFontSize(12);
                }
            }

            // 写入字节数组
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.write(baos);
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("generateDocx failed", e);
            throw new IOException("生成Word文档失败: " + e.getMessage());
        }
    }

    /**
     * 生成 Excel (.xlsx) 文件
     * 内容格式: 每行一个条目，用制表符或|分隔列
     */
    public static byte[] generateXlsx(String filename, String content) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("数据");

            // 设置列宽
            sheet.setColumnWidth(0, 6000);
            sheet.setColumnWidth(1, 12000);
            sheet.setColumnWidth(2, 6000);

            // 创建表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            org.apache.poi.xssf.usermodel.XSSFFont headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            int rowNum = 0;
            String[] lines = content.split("\n");

            for (String line : lines) {
                if (line.trim().isEmpty()) continue;

                XSSFRow row = sheet.createRow(rowNum++);

                // 解析制表符或|分隔的内容
                String[] cells;
                if (line.contains("\t")) {
                    cells = line.split("\t", -1);
                } else if (line.contains("|")) {
                    cells = line.split("\\|", -1);
                    // 过滤掉首尾空字符串
                    if (cells.length > 0 && cells[0].trim().isEmpty()) {
                        cells = java.util.Arrays.copyOfRange(cells, 1, cells.length);
                    }
                    if (cells.length > 0 && cells[cells.length - 1].trim().isEmpty()) {
                        cells = java.util.Arrays.copyOf(cells, cells.length - 1);
                    }
                } else {
                    cells = new String[]{line};
                }

                for (int i = 0; i < cells.length; i++) {
                    XSSFCell cell = row.createCell(i);
                    cell.setCellValue(cells[i].trim());
                    if (rowNum == 1) {
                        cell.setCellStyle(headerStyle);
                    }
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("generateXlsx failed", e);
            throw new IOException("生成Excel文档失败: " + e.getMessage());
        }
    }

    /**
     * 生成 PDF 文件
     */
    public static byte[] generatePdf(String filename, String content) throws IOException {
        try {
            Document document = new Document(PageSize.A4);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, baos);

            document.open();

            // 添加标题
            com.lowagie.text.Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Paragraph title = new Paragraph(filename + "\n\n", titleFont);
            document.add(title);

            // 添加内容
            com.lowagie.text.Font contentFont = FontFactory.getFont(FontFactory.COURIER, 10);
            String[] lines = content.split("\n");

            for (String line : lines) {
                if (line.trim().isEmpty()) {
                    document.add(new Paragraph(" "));
                } else {
                    Paragraph para = new Paragraph(line + "\n", contentFont);
                    para.setSpacingAfter(3);
                    document.add(para);
                }
            }

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("generatePdf failed", e);
            throw new IOException("生成PDF文档失败: " + e.getMessage());
        }
    }

    /**
     * 生成图片文件
     * 将文本内容渲染为图片
     */
    public static byte[] generateImage(String filename, String content) throws IOException {
        try {
            // 计算图片尺寸
            int width = 1200;
            int height = Math.max(600, content.split("\n").length * 30 + 100);

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = image.createGraphics();

            // 设置抗锯齿
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 白色背景
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, width, height);

            // 黑色文字
            g2d.setColor(Color.BLACK);

            // 标题
            g2d.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 24));
            g2d.drawString(filename, 40, 50);

            // 分隔线
            g2d.setStroke(new BasicStroke(1));
            g2d.drawLine(40, 65, width - 40, 65);

            // 内容
            g2d.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 14));
            String[] lines = content.split("\n");
            int y = 95;
            int lineHeight = 22;

            for (String line : lines) {
                if (y > height - 30) break; // 防止超出图片高度

                // 截断过长的行
                if (line.length() > 80) {
                    line = line.substring(0, 80) + "...";
                }

                g2d.drawString(line, 40, y);
                y += lineHeight;
            }

            // 添加水印说明
            g2d.setFont(new java.awt.Font("SansSerif", java.awt.Font.ITALIC, 10));
            g2d.setColor(Color.GRAY);
            g2d.drawString("Generated by NetDisk AI", 40, height - 20);

            g2d.dispose();

            // 根据扩展名确定格式
            String format = getExtension(filename);
            if (format == null) format = "png";
            format = format.toLowerCase(Locale.ROOT);
            if (format.equals("jpg") || format.equals("jpeg")) {
                format = "jpg";
            } else {
                format = "png";
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, format, baos);
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("generateImage failed", e);
            throw new IOException("生成图片失败: " + e.getMessage());
        }
    }

    private static String getExtension(String filename) {
        if (filename == null) return null;
        int idx = filename.lastIndexOf('.');
        if (idx <= 0 || idx >= filename.length() - 1) return null;
        return filename.substring(idx + 1);
    }
}
