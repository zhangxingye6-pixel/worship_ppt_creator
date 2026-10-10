package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.Dict;
import claygminx.worshipppt.common.config.SystemConfig;
import claygminx.worshipppt.common.entity.PreachEntity;
import claygminx.worshipppt.common.entity.ScriptureEntity;
import claygminx.worshipppt.components.ScriptureService;
import claygminx.worshipppt.exception.PPTLayoutException;
import claygminx.worshipppt.exception.ScriptureNumberException;
import claygminx.worshipppt.exception.WorshipStepException;
import claygminx.worshipppt.util.TextUtil;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 证道经文阶段
 */
public class PreachScriptureStep extends AbstractWorshipStep {
    /** 单页段落数量上限，避免短经文挤得过满。 */
    private static final int MAX_VERSE_COUNT_PER_SLIDE = 4;

    /** PowerPoint 使用百分比表示段落行距，110 表示 1.1 倍行距。 */
    private static final double SCRIPTURE_LINE_SPACING = 110.0;
    /** format3 经文行以【书卷章:节】开头；编号单独渲染，保持普通字重。 */
    private static final Pattern SCRIPTURE_REFERENCE = Pattern.compile("^【([^】]+)】(.*)$");
    /** 续行缩进使用编号前缀实际估算宽度的 95%，与读经版式保持一致。 */
    private static final double SCRIPTURE_HANGING_INDENT_RATIO = 0.95;

    // 经文服务对象
    private final ScriptureService scriptureService;

    private final static Logger logger = LoggerFactory.getLogger(PreachScriptureStep.class);

    private final PreachEntity preachEntity;

    public PreachScriptureStep(XMLSlideShow ppt, String layout, ScriptureService scriptureService, PreachEntity preachEntity) {
        super(ppt, layout);
        this.preachEntity = preachEntity;
        this.scriptureService = scriptureService;
    }

    @Override
    public void execute() throws WorshipStepException, PPTLayoutException {
        String scriptureNumber;
        scriptureNumber = preachEntity.getScriptureNumber();

        logger.info("开始制作证道经文" + scriptureNumber);

        ScriptureEntity scriptureEntity;
        try {
            // 获取经文实体，在证道部分的经文使用FORMART3 - 【约1:1】太初有道……
            logger.info("经文格式" + Dict.ScriptureProperty.FORMAT3);
            scriptureEntity = scriptureService.getScriptureWithFormat(scriptureNumber, SystemConfig.getString(Dict.ScriptureProperty.FORMAT3));
        } catch (ScriptureNumberException e) {
            throw new WorshipStepException("解析经文编号 [" + scriptureNumber + "] 时出错:" + "\n" + "msg:" + e.getMessage(), e);
        }
        if (scriptureEntity == null) {
            return;
        }

        String[] scriptureArray = scriptureEntity.getScripture().replaceAll("\r", "").split("\n");
        logger.info("共{}段经文", scriptureArray.length);

        XMLSlideShow ppt = getPpt();
        XSLFSlideLayout layout = ppt.findLayout(getLayout());

        int slideCount = 0;     // 页面数量计数器
        int verseCountOnSlide = 0;
        XSLFTextShape contentPlaceholder = null;
        for (int i = 0; i < scriptureArray.length; i++) {
            String scriptureItem = scriptureArray[i].trim();
            if (scriptureItem.isEmpty()) {
                continue;
            }

            if (contentPlaceholder == null) {
                slideCount++;
                contentPlaceholder = createScriptureSlide(ppt, layout, scriptureNumber);
                verseCountOnSlide = 0;
                logger.info("开始第{}张证道经文幻灯片...", slideCount);
            }

            XSLFTextParagraph paragraph = addScriptureParagraph(contentPlaceholder, scriptureItem, i);
            boolean reachedVerseLimit = verseCountOnSlide >= MAX_VERSE_COUNT_PER_SLIDE;
            boolean exceededAvailableHeight = exceedsAvailableHeight(contentPlaceholder);
            if (verseCountOnSlide > 0 && (reachedVerseLimit || exceededAvailableHeight)) {
                contentPlaceholder.removeTextParagraph(paragraph);
                slideCount++;
                contentPlaceholder = createScriptureSlide(ppt, layout, scriptureNumber);
                paragraph = addScriptureParagraph(contentPlaceholder, scriptureItem, i);
                verseCountOnSlide = 0;
                logger.info("经文高度或每页段数达到限制，开始第{}张证道经文幻灯片", slideCount);

                if (exceedsAvailableHeight(contentPlaceholder)) {
                    logger.warn("第{}段证道经文单独显示仍超出模板正文区域，将保留完整内容", i + 1);
                }
            } else if (exceededAvailableHeight) {
                logger.warn("第{}段证道经文单独显示仍超出模板正文区域，将保留完整内容", i + 1);
            }
            verseCountOnSlide++;
        }

        logger.info("证道经文幻灯片制作完成");
    }

    /** 创建经文页，并初始化章节标题与正文占位符。 */
    private XSLFTextShape createScriptureSlide(XMLSlideShow ppt,
                                               XSLFSlideLayout layout,
                                               String scriptureNumber) throws PPTLayoutException {
        XSLFSlide slide = ppt.createSlide(layout);
        XSLFTextShape titlePlaceholder = TextUtil.getPlaceholderSafely(slide, 0, getLayout(), "标题部分");
        XSLFTextRun titleTextRun = TextUtil.clearAndCreateTextRun(titlePlaceholder);
        String displayTitle = scriptureNumber.replace("->", "-");
        titleTextRun.setText(displayTitle);
        titleTextRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
        titleTextRun.setFontSize(SystemConfig.getUserConfigOrDefault(
                Dict.PPTProperty.GENERAL_TITLE_FONT_SIZE,
                AbstractWorshipStep.DEFAULT_TITLE_FONT_SIZE));
        TextUtil.setScriptureFontColor(titleTextRun, TextUtil.FontColor.RGB_FONT_COLOR_WHITE);

        XSLFTextShape contentPlaceholder = TextUtil.getPlaceholderSafely(slide, 1, getLayout(), "正文部分");
        contentPlaceholder.clearText();
        return contentPlaceholder;
    }

    /** 添加一段经文，设置统一行距并维持原有的节间颜色。 */
    private XSLFTextParagraph addScriptureParagraph(XSLFTextShape placeholder,
                                                    String scriptureItem,
                                                    int scriptureIndex) {
        XSLFTextParagraph paragraph = placeholder.addNewTextParagraph();
        paragraph.setLineSpacing(SCRIPTURE_LINE_SPACING);
        useCustomLanguage(paragraph);
        paragraph.setTextAlign(TextParagraph.TextAlign.LEFT);
        Matcher matcher = SCRIPTURE_REFERENCE.matcher(scriptureItem);
        XSLFTextRun referenceRun = paragraph.addNewTextRun();
        XSLFTextRun scriptureRun = paragraph.addNewTextRun();
        if (matcher.matches()) {
            String reference = "[" + matcher.group(1) + "]";
            String scripturePrefix = reference + " ";
            double hangingIndent = estimateTextWidth(scripturePrefix)
                    * SCRIPTURE_HANGING_INDENT_RATIO;
            paragraph.setLeftMargin(hangingIndent);
            paragraph.setIndent(-hangingIndent);
            referenceRun.setText(reference);
            referenceRun.setBold(false);
            scriptureRun.setText(" " + matcher.group(2));
            scriptureRun.setBold(true);
        } else {
            // 未匹配到章节标识时保留原文，避免异常格式导致经文内容丢失。
            referenceRun.setText(scriptureItem);
            scriptureRun.setText("");
        }
        referenceRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
        scriptureRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
        if (scriptureIndex % 2 == 1) {
            TextUtil.setScriptureFontColor(referenceRun, TextUtil.FontColor.RGB_FONT_COLOR_BLUE);
            TextUtil.setScriptureFontColor(scriptureRun, TextUtil.FontColor.RGB_FONT_COLOR_BLUE);
        } else {
            TextUtil.setScriptureFontColor(referenceRun, TextUtil.FontColor.RGB_FONT_COLOR_BLACK);
            TextUtil.setScriptureFontColor(scriptureRun, TextUtil.FontColor.RGB_FONT_COLOR_BLACK);
        }
        return paragraph;
    }

    /**
     * 按中英文字符的大致显示宽度估算章节编号前缀，包含正文前的空格。
     * 全角字符按一个字号宽度计算，半角字符按半个字号宽度计算，空格按四分之一计算。
     */
    private double estimateTextWidth(String text) {
        double fontSize = SystemConfig.getUserConfigOrDefault(
                Dict.PPTProperty.GENERAL_SCRIPTURE_FONT_SIZE,
                AbstractWorshipStep.DEFAULT_SCRIPTURE_FONT_SIZE);
        double widthUnits = 0.0;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character == ' ') {
                widthUnits += 0.25;
            } else if (character <= 0x00FF) {
                widthUnits += 0.5;
            } else {
                widthUnits += 1.0;
            }
        }
        return widthUnits * fontSize;
    }

    /** 使用占位符实际排版高度判断是否超出可用正文区域。 */
    private boolean exceedsAvailableHeight(XSLFTextShape placeholder) {
        double availableHeight = placeholder.getAnchor().getHeight()
                - placeholder.getTopInset()
                - placeholder.getBottomInset();
        return placeholder.getTextHeight() > availableHeight;
    }
}
