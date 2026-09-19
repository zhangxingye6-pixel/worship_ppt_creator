package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.config.SystemConfig;
import claygminx.worshipppt.common.entity.ScriptureNumberEntity;
import claygminx.worshipppt.components.ScriptureService;
import claygminx.worshipppt.exception.PPTLayoutException;
import claygminx.worshipppt.exception.ScriptureNumberException;
import claygminx.worshipppt.exception.WorshipStepException;
import claygminx.worshipppt.util.ScriptureUtil;
import claygminx.worshipppt.common.Dict;
import claygminx.worshipppt.util.TextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.xslf.usermodel.*;

import java.awt.geom.Rectangle2D;
import java.util.List;

/**
 * 赦罪经文阶段
 */
public class ForgiveSinsScriptureStep extends AbstractWorshipStep {

    private final static Logger logger = LoggerFactory.getLogger(ForgiveSinsScriptureStep.class);

    private final String scriptureNumber;
    private final ScriptureService scriptureService;

    // 文本常量
    private final static double DEFAULT_LINE_SPACING = 120.0;

    public ForgiveSinsScriptureStep(XMLSlideShow ppt, String layout, ScriptureService scriptureService, String scriptureNumber) {
        super(ppt, layout);
        this.scriptureService = scriptureService;
        this.scriptureNumber = scriptureNumber;
    }

    @Override
    public void execute() throws WorshipStepException, PPTLayoutException {
        List<ScriptureNumberEntity> scriptureNumberList;
        try {
            scriptureNumberList = ScriptureUtil.parseNumbers(scriptureNumber);
        } catch (ScriptureNumberException e) {
            throw new WorshipStepException("解析赦罪经文编号 [" + scriptureNumber + "] 时出错！", e);
        }
        String[] titleAndScripture = getTitleAndScripture(scriptureService, scriptureNumberList);

        XMLSlideShow ppt = getPpt();
        XSLFSlideLayout layout = ppt.findLayout(getLayout());
        XSLFSlide slide = ppt.createSlide(layout);

        // 赦罪经文标题
        XSLFTextShape placeholder = TextUtil.getPlaceholderSafely(slide, 0, getLayout(), "标题部分");
        XSLFTextRun titleTextRun = TextUtil.clearAndCreateTextRun(placeholder);
        titleTextRun.setText(titleAndScripture[0]);
        // 使用父类中的默认文本参数
        titleTextRun.setFontSize(AbstractWorshipStep.DEFAULT_TITLE_FONT_SIZE);
        titleTextRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
        TextUtil.setScriptureFontColor(titleTextRun, TextUtil.FontColor.RGB_FONT_COLOR_WHITE);


        // 赦罪经文
        placeholder = TextUtil.getPlaceholderSafely(slide, 1, getLayout(), "正文部分");
        List<XSLFTextParagraph> paragraphs = placeholder.getTextParagraphs();
        // 获取行距配置
        double lineSpacing = SystemConfig.getUserConfigOrDefault(Dict.PPTProperty.FORGIVE_SINS_SCRIPTURE_LINE_SPACING, DEFAULT_LINE_SPACING);
        // 遍历占位符中的所有文本段落
        boolean breakTag = false;
        for (XSLFTextParagraph paragraph : paragraphs) {
            List<XSLFTextRun> textRuns = paragraph.getTextRuns();
            // 遍历当前文本段落中的所有文本段
            for (XSLFTextRun textRun : textRuns) {
                String rawText = textRun.getRawText();
                logger.info("赦罪：读取到文本段" + rawText);
                if (rawText != null && rawText.contains("主领：")){
                    TextUtil.setScriptureFontColor(textRun, TextUtil.FontColor.RGB_FONT_COLOR_RED);
                    textRun.setFontSize(AbstractWorshipStep.DEFAULT_SCRIPTURE_FONT_SIZE);
                    textRun.setBold(true);
                }
                if (rawText != null && rawText.contains(getCustomPlaceholder())) {

                    textRun.setText(rawText.replace(getCustomPlaceholder(), titleAndScripture[1]));
                    textRun.setFontSize(AbstractWorshipStep.DEFAULT_SCRIPTURE_FONT_SIZE);
                    TextUtil.setScriptureFontColor(textRun, TextUtil.FontColor.RGB_FONT_COLOR_BLACK);
                    paragraph.setLineSpacing(lineSpacing);
                    useCustomLanguage(paragraph);
                    breakTag = true;
                }
                if (rawText != null && rawText.contains("会众：")){
                    textRun.setFontSize(AbstractWorshipStep.DEFAULT_SCRIPTURE_FONT_SIZE);
                    TextUtil.setScriptureFontColor(textRun, TextUtil.FontColor.RGB_FONT_COLOR_BLUE);
                    textRun.setBold(true);
                    if (breakTag) break;
                }
                if (rawText != null && rawText.contains("感谢主赦免我们的罪")){
                    textRun.setFontSize(AbstractWorshipStep.DEFAULT_SCRIPTURE_FONT_SIZE);
                    TextUtil.setScriptureFontColor(textRun, TextUtil.FontColor.RGB_FONT_COLOR_BLACK);
                }
            }
        }
        // 正文占位符的高度会随经文内容变化，下面的两个独立文本框需要作为整体同步下移。
        moveForgivenessFooter(slide, placeholder);
        logger.info("认罪经文幻灯片制作完成");
    }

    /**
     * 根据赦罪经文的实际渲染高度移动“会众”和底部提示文本框。
     *
     * <p>PowerPoint 不会自动让独立文本框跟随占位符变化，因此这里保留模板中
     * 原有的间距和两个文本框之间的相对位置，只调整整个底部文本框组的 Y 坐标。</p>
     *
     * @param slide       当前赦罪幻灯片
     * @param placeholder 已填充经文的正文占位符
     */
    private void moveForgivenessFooter(XSLFSlide slide, XSLFTextShape placeholder) {
        XSLFTextShape congregationShape = null;
        XSLFTextShape reminderShape = null;
        double footerTop = Double.MAX_VALUE;

        for (XSLFShape shape : slide.getShapes()) {
            if (!(shape instanceof XSLFTextShape textShape) || textShape == placeholder) {
                continue;
            }
            String text = textShape.getText();
            if (text == null) {
                continue;
            }
            if (text.contains("会众：")) {
                congregationShape = textShape;
            } else if (text.contains("感谢主赦免我们的罪")) {
                reminderShape = textShape;
            }
            if (text.contains("会众：") || text.contains("感谢主赦免我们的罪")) {
                footerTop = Math.min(footerTop, textShape.getAnchor().getY());
            }
        }

        if (congregationShape == null || reminderShape == null || footerTop == Double.MAX_VALUE) {
            logger.warn("赦罪版式缺少“会众：”或底部提示文本框，跳过底部文本框位置调整");
            return;
        }

        Rectangle2D placeholderAnchor = placeholder.getAnchor();
        double placeholderTextHeight = placeholder.getTextHeight() * 12700.0;
        if (placeholderTextHeight <= 0) {
            placeholderTextHeight = placeholderAnchor.getHeight();
        }

        // 保留正文占位符底部到下方文本框组顶部的模板间距。
        double templateGap = footerTop - (placeholderAnchor.getY() + placeholderAnchor.getHeight());
        double targetFooterTop = placeholderAnchor.getY() + placeholderTextHeight + templateGap;
        double deltaY = targetFooterTop - footerTop;

        moveShapeVertically(congregationShape, deltaY);
        moveShapeVertically(reminderShape, deltaY);
        logger.debug("赦罪底部文本框整体移动 {} EMU", deltaY);
    }

    /** 仅调整文本框的纵坐标，保持宽度、高度及两个文本框之间的相对位置。 */
    private void moveShapeVertically(XSLFTextShape shape, double deltaY) {
        Rectangle2D anchor = shape.getAnchor();
        shape.setAnchor(new Rectangle2D.Double(
                anchor.getX(),
                anchor.getY() + deltaY,
                anchor.getWidth(),
                anchor.getHeight()));
    }
}
