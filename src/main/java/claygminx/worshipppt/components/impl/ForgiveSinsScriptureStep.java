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
    /** 角色标签使用单倍行距，避免标签本身占用过多垂直空间。 */
    private static final double LABEL_LINE_SPACING = 100.0;
    /** 会众标题使用 1.5 倍行距，增加与主领正文之间的视觉分隔。 */
    private static final double CONGREGATION_LABEL_LINE_SPACING = 150.0;
    /** 主领与会众两个段落之间的额外间距，单位为磅。 */
    private static final double CONGREGATION_SPACE_BEFORE = 12.0;

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
        // 经文标题优先使用统一配置；未配置时回退到父类默认字号。
        titleTextRun.setFontSize(SystemConfig.getUserConfigOrDefault(
                Dict.PPTProperty.GENERAL_TITLE_FONT_SIZE,
                AbstractWorshipStep.DEFAULT_TITLE_FONT_SIZE));
        setDefaultTitleLineSpacing(titleTextRun);
        titleTextRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
        TextUtil.setScriptureFontColor(titleTextRun, TextUtil.FontColor.RGB_FONT_COLOR_WHITE);


        // 赦罪经文和会众回应统一写入同一个正文占位符，交由 PowerPoint 统一排版。
        placeholder = TextUtil.getPlaceholderSafely(slide, 1, getLayout(), "正文部分");
        placeholder.clearText();
        double lineSpacing = SystemConfig.getUserConfigOrDefault(Dict.PPTProperty.FORGIVE_SINS_SCRIPTURE_LINE_SPACING, DEFAULT_LINE_SPACING);
        addForgivenessLabelParagraph(placeholder, "主领:  ",
                TextUtil.FontColor.RGB_FONT_COLOR_BLACK, 0.0, LABEL_LINE_SPACING);
        addForgivenessContentParagraph(placeholder, titleAndScripture[1], lineSpacing,
                TextUtil.FontColor.RGB_FONT_COLOR_BLACK);
        addForgivenessLabelParagraph(placeholder, "会众:  ",
                TextUtil.FontColor.RGB_FONT_COLOR_BLUE, CONGREGATION_SPACE_BEFORE,
                CONGREGATION_LABEL_LINE_SPACING);
        addForgivenessContentParagraph(placeholder, "感谢主赦免我们的罪", lineSpacing,
                TextUtil.FontColor.RGB_FONT_COLOR_BLUE);
        logger.info("认罪经文幻灯片制作完成");
    }

    /**
     * 添加赦罪阶段的角色标签。
     *
     * @param placeholder 正文占位符
     * @param label       角色标签
     * @param fontColor   标签颜色
     * @param spaceBefore 段前间距
     */
    private void addForgivenessLabelParagraph(XSLFTextShape placeholder,
                                              String label,
                                              int[] fontColor,
                                              double spaceBefore,
                                              double lineSpacing) {
        XSLFTextParagraph paragraph = placeholder.addNewTextParagraph();
        paragraph.setLineSpacing(lineSpacing);
        paragraph.setSpaceBefore(spaceBefore);
        useCustomLanguage(paragraph);
        XSLFTextRun textRun = paragraph.addNewTextRun();
        textRun.setText(label);
        textRun.setFontSize(AbstractWorshipStep.DEFAULT_SCRIPTURE_FONT_SIZE);
        textRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
        textRun.setBold(false);
        TextUtil.setScriptureFontColor(textRun, fontColor);
    }

    /**
     * 添加赦罪阶段的正文。正文使用固定字号，避免 PowerPoint 根据文本框高度
     * 自动缩小文字；占位符本身负责处理换行和超出内容的排版。
     *
     * @param placeholder 正文占位符
     * @param content     要显示的正文
     * @param lineSpacing 正文行距
     * @param fontColor   正文颜色
     */
    private void addForgivenessContentParagraph(XSLFTextShape placeholder,
                                                String content,
                                                double lineSpacing,
                                                int[] fontColor) {
        XSLFTextParagraph paragraph = placeholder.addNewTextParagraph();
        paragraph.setLineSpacing(lineSpacing);
        paragraph.setLeftMargin(0.0);
        paragraph.setIndent(72.0);
        useCustomLanguage(paragraph);
        XSLFTextRun textRun = paragraph.addNewTextRun();
        textRun.setText(content == null ? "" : content.trim());
        textRun.setFontSize(AbstractWorshipStep.DEFAULT_SCRIPTURE_FONT_SIZE);
        textRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
        textRun.setBold(true);
        TextUtil.setScriptureFontColor(textRun, fontColor);
    }
}
