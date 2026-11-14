package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.config.SystemConfig;
import claygminx.worshipppt.common.entity.ScriptureEntity;
import claygminx.worshipppt.common.entity.ScriptureNumberEntity;
import claygminx.worshipppt.components.ScriptureService;
import claygminx.worshipppt.components.WorshipStep;
import claygminx.worshipppt.exception.PPTLayoutException;
import claygminx.worshipppt.exception.ScriptureServiceException;
import claygminx.worshipppt.common.Dict;
import claygminx.worshipppt.util.TextUtil;
import org.apache.poi.xslf.usermodel.*;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextCharacterProperties;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextParagraph;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextParagraphProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.util.List;

/**
 * 通用的敬拜阶段抽象类
 */
public abstract class AbstractWorshipStep implements WorshipStep {

    private final static Logger logger = LoggerFactory.getLogger(AbstractWorshipStep.class);

    private final XMLSlideShow ppt;
    private final String layout;

    // 默认文本常量
    public final static double DEFAULT_TITLE_FONT_SIZE = 28.0;
    /** PowerPoint 使用百分比表示段落行距，105 表示 1.05 倍行距。 */
    protected static final double DEFAULT_TITLE_LINE_SPACING = 105.0;
    public final static double DEFAULT_SCRIPTURE_FONT_SIZE = 30.0;
    public final static double DEFAULT_FAMILY_REPORT_FONT_SIZE = 28.0;
    public final static String DEFAULT_FONT_FAMILY = "Microsoft YaHei";
    public final static double DEFAULT_STEP_COVER_FONT_SIZE = 48.0;

    /**
     * 通用的构造器
     * @param ppt PPT对象
     * @param layout 幻灯片母版中版式的布局名称
     */
    public AbstractWorshipStep(XMLSlideShow ppt, String layout) {
        this.ppt = ppt;
        this.layout = layout;
    }

    public XMLSlideShow getPpt() {
        return ppt;
    }

    public String getLayout() {
        return layout;
    }

    /**
     * 获取自定义的占位符
     * <p>制作幻灯片时，你可以将此占位符替换为其它字符串。</p>
     * @return 文本框中的占位符
     */
    public String getCustomPlaceholder() {
        return SystemConfig.getString(Dict.PPTProperty.GENERAL_PLACEHOLDER);
    }

    public String getFontFamily() {
        return SystemConfig.getString(Dict.PPTProperty.GENERAL_FONT_FAMILY);
    }

    /**
     * 读取全局标题字号，配置缺失或无效时统一回退到父类默认值。
     */
    protected double getTitleFontSize() {
        return SystemConfig.getUserConfigOrDefault(
                Dict.PPTProperty.GENERAL_TITLE_FONT_SIZE,
                DEFAULT_TITLE_FONT_SIZE);
    }

    /**
     * 读取全局经文正文字号，配置缺失或无效时统一回退到父类默认值。
     */
    protected double getScriptureFontSize() {
        return SystemConfig.getUserConfigOrDefault(
                Dict.PPTProperty.GENERAL_SCRIPTURE_FONT_SIZE,
                DEFAULT_SCRIPTURE_FONT_SIZE);
    }

    /**
     * 读取全局阶段封面字号，配置缺失或无效时统一回退到父类默认值。
     */
    protected double getStepCoverFontSize() {
        return SystemConfig.getUserConfigOrDefault(
                Dict.PPTProperty.GENERAL_STEP_COVER_FONT_SIZE,
                DEFAULT_STEP_COVER_FONT_SIZE);
    }

    /**
     * 获取标题和经文
     * @param scriptureService 经文服务对象
     * @param scriptureNumberList 经文编号实体对象列表
     * @return 数组。第一个元素时经文编号，作为标题；第二个元素是经文，作为内容。
     */
    protected String[] getTitleAndScripture(ScriptureService scriptureService, List<ScriptureNumberEntity> scriptureNumberList) {
        StringBuilder titleBuilder = new StringBuilder();
        StringBuilder scriptureBuilder = new StringBuilder();
        if (logger.isDebugEnabled()) {
            logger.debug("{}个经文编号实体对象", scriptureNumberList.size());
        }
        for (ScriptureNumberEntity scriptureNumberEntity : scriptureNumberList) {
            if (logger.isDebugEnabled()) {
                logger.debug(scriptureNumberEntity.toString());
            }
            boolean validateResult = scriptureService.validateNumber(scriptureNumberEntity);
            if (!validateResult) {
                throw new ScriptureServiceException("经文编号格式错误！");
            }
            titleBuilder.append(scriptureNumberEntity.getValue());
            ScriptureEntity scriptureEntity = scriptureService.getScriptureWithFormat(scriptureNumberEntity, SystemConfig.getString(Dict.ScriptureProperty.FORMAT1));
            if (logger.isDebugEnabled()) {
                logger.debug(scriptureEntity.toString());
            }
            scriptureBuilder.append("\t").append(scriptureEntity.getScripture()).append('\n');
        }
        scriptureBuilder.setLength(scriptureBuilder.length() - 1);
        char lastChar = scriptureBuilder.charAt(scriptureBuilder.length() - 1);
        if (lastChar == '，' || lastChar == '；') {
            scriptureBuilder.setCharAt(scriptureBuilder.length() - 1, '。');
        }

        return new String[] {titleBuilder.toString(), scriptureBuilder.toString()};
    }

    /**
     * 填充标题和经文
     * @param layoutName 幻灯片母版中版式的布局名称
     * @param scriptureService 经文服务对象
     * @param scriptureNumberList 经文编号对象列表
     */
    protected void fillTitleAndScripture(String layoutName, ScriptureService scriptureService, List<ScriptureNumberEntity> scriptureNumberList) throws PPTLayoutException {
        String[] titleAndScripture = getTitleAndScripture(scriptureService, scriptureNumberList);

        XMLSlideShow ppt = getPpt();
        XSLFSlideLayout layout = ppt.findLayout(layoutName);
        XSLFSlide slide = ppt.createSlide(layout);
        // 填充标题
        XSLFTextShape titlePlaceHolder = TextUtil.getPlaceholderSafely(slide, 0, getLayout(), "");
        XSLFTextRun titleTextRun = TextUtil.clearAndCreateTextRun(titlePlaceHolder);
        titleTextRun.setText(titleAndScripture[0]);
        TextUtil.setScriptureFontColor(titleTextRun, TextUtil.FontColor.RGB_FONT_COLOR_WHITE);
        double titleFontSize = getTitleFontSize();
        titleTextRun.setFontSize(titleFontSize);
        setDefaultTitleLineSpacing(titleTextRun);
        if (logger.isDebugEnabled()) {
            logger.debug("填充标题：" + titleAndScripture[0]);
        }

        // 填充经文
        XSLFTextShape contentPlaceHolder = TextUtil.getPlaceholderSafely(slide, 1, getLayout(), "");
        contentPlaceHolder.clearText();
        XSLFTextRun scriptureTextRun = TextUtil.clearAndCreateTextRun(contentPlaceHolder);
        TextUtil.setScriptureFontColor(scriptureTextRun, TextUtil.FontColor.RGB_FONT_COLOR_BLACK);
        scriptureTextRun.setText(titleAndScripture[1]);
        double scriptureFontSize = getScriptureFontSize();
        scriptureTextRun.setFontSize(scriptureFontSize);
        List<XSLFTextParagraph> paragraphs = contentPlaceHolder.getTextParagraphs();
        for (XSLFTextParagraph paragraph : paragraphs) {
            useCustomLanguage(paragraph);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("填充经文：" + titleAndScripture[1]);
        }
    }

    /** 将标题段落的行距统一设置为 1.0 倍，不改变正文段落的排版。 */
    protected void setDefaultTitleLineSpacing(XSLFTextRun titleTextRun) {
        titleTextRun.getParagraph().setLineSpacing(DEFAULT_TITLE_LINE_SPACING);
    }

    /**
     * 使用自定义语言
     * @param paragraph 段落
     */
    protected void useCustomLanguage(XSLFTextParagraph paragraph) {
        CTTextParagraph xmlObject = paragraph.getXmlObject();
        CTTextParagraphProperties pPr = xmlObject.getPPr();
        if (pPr == null) {
            pPr = xmlObject.addNewPPr();
        }

        // 按中文习惯控制首尾字符
        pPr.setEaLnBrk(true);

        // 允许标点溢出边界
        pPr.setHangingPunct(true);

        CTTextCharacterProperties defRPr = pPr.getDefRPr();
        if (defRPr == null) {
            defRPr = pPr.addNewDefRPr();
        }
        // 设置语言
        String lang = SystemConfig.getString(Dict.PPTProperty.GENERAL_LANGUAGE);
        defRPr.setLang(lang);
        defRPr.setAltLang(lang);

        CTTextCharacterProperties endParaRPr = xmlObject.getEndParaRPr();
        if (endParaRPr == null) {
            endParaRPr = xmlObject.addNewEndParaRPr();
        }
        // 设置语言
        endParaRPr.setLang(lang);
        endParaRPr.setAltLang(lang);
    }

    /**
     * 测量文本在指定字体和字号下的显示宽度，用于计算经文续行的悬挂缩进。
     *
     * <p>PowerPoint 的换行由段落的左边距和悬挂缩进控制，POI 本身不会返回
     * 文本在 PowerPoint 中的实际排版宽度。因此这里先使用 Java 字体引擎按字形
     * 宽度测量，避免仅按字符数量估算导致中文、数字和标点混排时产生偏移。</p>
     *
     * <p>在运行环境缺少目标字体或字体引擎测量失败时，回退到中英文字符宽度估算，
     * 保证无图形环境或跨平台打包环境仍能生成 PPT。</p>
     *
     * @param text       待测量文本
     * @param fontSize   字号，单位为磅
     * @param fontFamily 字体名称
     * @return 文本宽度，单位为磅
     */
    protected double measureTextWidth(String text, double fontSize, String fontFamily) {
        if (text == null || text.isEmpty() || fontSize <= 0) {
            return 0.0;
        }

        try {
            Font font = new Font(fontFamily, Font.PLAIN, 1).deriveFont((float) fontSize);
            // Java 找不到目标字体时会静默替换为逻辑字体。逻辑字体的字形宽度
            // 可能与 PPT 母版使用的中文字体差异很大，不能直接用于设置缩进。
            if (!isFontFamilyAvailable(fontFamily, font.getFamily())) {
                return estimateTextWidthByCharacter(text, fontSize);
            }
            FontRenderContext renderContext = new FontRenderContext(null, true, true);
            double measuredWidth = new TextLayout(text, font, renderContext).getAdvance();
            if (measuredWidth > 0.0) {
                return measuredWidth;
            }
        } catch (RuntimeException e) {
            logger.debug("无法使用字体引擎测量文本宽度，将回退到字符宽度估算", e);
        }

        return estimateTextWidthByCharacter(text, fontSize);
    }

    /** 判断请求字体或其系统字体族名称是否真实存在，避免误用 Java 逻辑字体。 */
    private boolean isFontFamilyAvailable(String requestedFamily, String resolvedFamily) {
        if (requestedFamily == null || requestedFamily.trim().isEmpty()) {
            return false;
        }
        try {
            for (String availableFamily : GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames()) {
                if (requestedFamily.equalsIgnoreCase(availableFamily)
                        || resolvedFamily.equalsIgnoreCase(availableFamily)) {
                    return true;
                }
            }
        } catch (RuntimeException e) {
            logger.debug("无法读取系统字体列表，将使用字符宽度估算", e);
        }
        return false;
    }

    /** 按中英文字符的大致显示宽度进行跨平台回退估算。 */
    private double estimateTextWidthByCharacter(String text, double fontSize) {
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
}
