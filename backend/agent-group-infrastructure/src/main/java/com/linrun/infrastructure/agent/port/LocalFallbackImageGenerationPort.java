package com.linrun.infrastructure.agent.port;

import com.linrun.domain.academic.runtime.tool.output.AcademicToolFileRef;
import com.linrun.domain.academic.runtime.tool.port.AcademicImageGenerationPort;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class LocalFallbackImageGenerationPort implements AcademicImageGenerationPort {

    private static final Color BACKGROUND = new Color(248, 250, 252);
    private static final Color INK = new Color(24, 31, 42);
    private static final Color MUTED = new Color(91, 101, 118);
    private static final Color BORDER = new Color(205, 213, 225);
    private static final Color[] NODE_COLORS = {
            new Color(36, 99, 235),
            new Color(20, 141, 125),
            new Color(126, 87, 194),
            new Color(217, 119, 6)
    };

    @Override
    public AcademicImageGenerationResult generate(AcademicImageGenerationRequest request) {
        AcademicImageGenerationRequest safeRequest = request == null
                ? new AcademicImageGenerationRequest("", "generate", "1024x1024", 1, List.of(), List.of())
                : request;
        int[] size = parseSize(safeRequest.size());
        int batchCount = Math.max(1, Math.min(4, safeRequest.batchCount()));
        List<AcademicToolFileRef> refs = new ArrayList<>();
        for (int index = 0; index < batchCount; index++) {
            refs.add(render(safeRequest, size[0], size[1], index));
        }
        return new AcademicImageGenerationResult(
                true,
                "local-fallback-renderer",
                "已使用本地降级渲染生成 " + refs.size() + " 张流程图草图",
                true,
                refs,
                "");
    }

    private AcademicToolFileRef render(AcademicImageGenerationRequest request, int width, int height, int index) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(BACKGROUND);
            graphics.fillRect(0, 0, width, height);

            Font titleFont = font(Font.BOLD, Math.max(30, width / 26));
            Font bodyFont = font(Font.PLAIN, Math.max(20, width / 42));
            Font smallFont = font(Font.PLAIN, Math.max(16, width / 58));
            drawHeader(graphics, request, width, titleFont, smallFont);
            drawFlow(graphics, request, width, height, bodyFont, smallFont);
            drawFooter(graphics, request, width, height, smallFont);
        } finally {
            graphics.dispose();
        }
        byte[] png = pngBytes(image);
        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
        String artifactId = "IMG-FALLBACK-" + UUID.randomUUID().toString().replace("-", "");
        String fileName = "local-fallback-image-" + (index + 1) + ".png";
        return AcademicToolFileRef.builder()
                .artifactId(artifactId)
                .fileName(fileName)
                .downloadUrl(dataUrl)
                .previewUrl(dataUrl)
                .contentType("image/png")
                .fileSize((long) png.length)
                .build();
    }

    private void drawHeader(Graphics2D graphics,
                            AcademicImageGenerationRequest request,
                            int width,
                            Font titleFont,
                            Font smallFont) {
        graphics.setColor(INK);
        graphics.setFont(titleFont);
        drawCentered(graphics, "学术 Agent 额度交易一致性草图", width / 2, 82);

        graphics.setFont(smallFont);
        graphics.setColor(MUTED);
        String mode = StringUtils.hasText(request.mode()) ? request.mode().trim() : "generate";
        drawCentered(graphics, "provider: local fallback · mode: " + mode + " · prompt-rendered", width / 2, 122);
    }

    private void drawFlow(Graphics2D graphics,
                          AcademicImageGenerationRequest request,
                          int width,
                          int height,
                          Font bodyFont,
                          Font smallFont) {
        String[] titles = {"支付成功", "等待成团", "成团到账", "退款回滚"};
        String[] descriptions = {
                "直购可进入到账判断",
                "拼团未成团不发放额度",
                "成团或交易完成后发放",
                "退款后按流水回滚余额"
        };
        int margin = Math.max(56, width / 18);
        int gap = Math.max(24, width / 48);
        int nodeWidth = (width - margin * 2 - gap * 3) / 4;
        int nodeHeight = Math.max(156, height / 6);
        int y = Math.max(190, height / 3);
        for (int index = 0; index < titles.length; index++) {
            int x = margin + index * (nodeWidth + gap);
            drawNode(graphics, x, y, nodeWidth, nodeHeight, NODE_COLORS[index], titles[index], descriptions[index],
                    bodyFont, smallFont, index + 1);
            if (index < titles.length - 1) {
                drawArrow(graphics, x + nodeWidth + 5, y + nodeHeight / 2, x + nodeWidth + gap - 5, y + nodeHeight / 2);
            }
        }

        graphics.setFont(smallFont);
        graphics.setColor(MUTED);
        List<String> promptLines = wrap(graphics, "提示词：" + firstText(request.prompt(), "未提供提示词"), width - margin * 2);
        int promptY = y + nodeHeight + 76;
        for (String line : promptLines.stream().limit(4).toList()) {
            graphics.drawString(line, margin, promptY);
            promptY += graphics.getFontMetrics().getHeight() + 6;
        }
    }

    private void drawNode(Graphics2D graphics,
                          int x,
                          int y,
                          int width,
                          int height,
                          Color color,
                          String title,
                          String description,
                          Font bodyFont,
                          Font smallFont,
                          int index) {
        graphics.setColor(Color.WHITE);
        graphics.fillRoundRect(x, y, width, height, 22, 22);
        graphics.setColor(BORDER);
        graphics.setStroke(new BasicStroke(2f));
        graphics.drawRoundRect(x, y, width, height, 22, 22);

        graphics.setColor(color);
        graphics.fillOval(x + 22, y + 24, 48, 48);
        graphics.setColor(Color.WHITE);
        graphics.setFont(bodyFont.deriveFont(Font.BOLD));
        drawCentered(graphics, String.valueOf(index), x + 46, y + 57);

        graphics.setColor(INK);
        graphics.setFont(bodyFont.deriveFont(Font.BOLD));
        graphics.drawString(title, x + 22, y + 104);

        graphics.setColor(MUTED);
        graphics.setFont(smallFont);
        List<String> lines = wrap(graphics, description, width - 44);
        int lineY = y + 136;
        for (String line : lines.stream().limit(2).toList()) {
            graphics.drawString(line, x + 22, lineY);
            lineY += graphics.getFontMetrics().getHeight() + 4;
        }
    }

    private void drawArrow(Graphics2D graphics, int x1, int y1, int x2, int y2) {
        graphics.setColor(new Color(110, 121, 140));
        graphics.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.drawLine(x1, y1, x2, y2);
        int size = 10;
        graphics.fillPolygon(
                new int[]{x2, x2 - size, x2 - size},
                new int[]{y2, y2 - size, y2 + size},
                3);
    }

    private void drawFooter(Graphics2D graphics,
                            AcademicImageGenerationRequest request,
                            int width,
                            int height,
                            Font smallFont) {
        graphics.setFont(smallFont);
        graphics.setColor(MUTED);
        String sourceInfo = "source images: " + safeList(request.sourceImageUrls()).size()
                + " · masks: " + safeList(request.maskImageUrls()).size()
                + " · fallback marked in response metadata";
        drawCentered(graphics, sourceInfo, width / 2, height - 46);
    }

    private int[] parseSize(String size) {
        String value = StringUtils.hasText(size) ? size.trim().toLowerCase(Locale.ROOT) : "1024x1024";
        String[] parts = value.split("x");
        int width = parts.length > 0 ? parsePositive(parts[0], 1024) : 1024;
        int height = parts.length > 1 ? parsePositive(parts[1], 1024) : width;
        return new int[]{Math.max(512, Math.min(1024, width)), Math.max(512, Math.min(1024, height))};
    }

    private int parsePositive(String value, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private byte[] pngBytes(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("local fallback image render failed", e);
        }
    }

    private List<String> wrap(Graphics2D graphics, String text, int maxWidth) {
        String safeText = firstText(text, "");
        FontMetrics metrics = graphics.getFontMetrics();
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int offset = 0; offset < safeText.length(); offset++) {
            char ch = safeText.charAt(offset);
            String candidate = current + String.valueOf(ch);
            if (metrics.stringWidth(candidate) > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(String.valueOf(ch));
            } else {
                current.append(ch);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    private void drawCentered(Graphics2D graphics, String text, int centerX, int baselineY) {
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.drawString(text, centerX - metrics.stringWidth(text) / 2, baselineY);
    }

    private Font font(int style, int size) {
        String[] candidates = {"Microsoft YaHei", "PingFang SC", "Noto Sans CJK SC", "SimHei", "SansSerif"};
        for (String candidate : candidates) {
            Font font = new Font(candidate, style, size);
            if (font.canDisplay('额') && font.canDisplay('度')) {
                return font;
            }
        }
        return new Font(Font.SANS_SERIF, style, size);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }
}
