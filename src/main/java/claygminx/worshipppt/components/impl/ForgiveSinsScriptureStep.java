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
import org.w3c.dom.Text;

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
        logger.info("认罪经文幻灯片制作完成");
    }
}
