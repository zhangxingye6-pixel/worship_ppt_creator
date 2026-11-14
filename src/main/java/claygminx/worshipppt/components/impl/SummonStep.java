package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.config.SystemConfig;
import claygminx.worshipppt.common.entity.ScriptureNumberEntity;
import claygminx.worshipppt.components.ScriptureService;
import claygminx.worshipppt.exception.PPTLayoutException;
import claygminx.worshipppt.exception.ScriptureNumberException;
import claygminx.worshipppt.exception.ScriptureServiceException;
import claygminx.worshipppt.exception.WorshipStepException;
import claygminx.worshipppt.util.TextUtil;
import claygminx.worshipppt.util.ScriptureUtil;
import claygminx.worshipppt.common.Dict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.xslf.usermodel.*;
import java.util.Objects;

/**
 * 宣召阶段
 */
public class SummonStep extends AbstractWorshipStep {

    private final static Logger logger = LoggerFactory.getLogger(SummonStep.class);

    /** PowerPoint 使用百分比表示段落行距，120 表示 1.2 倍行距。 */
    private static final double SUMMON_LINE_SPACING = 120.0;

    /** 宣召角色标签使用单倍行距，避免标签行占用过多垂直空间。 */
    private static final double SUMMON_LABEL_LINE_SPACING = 100.0;
    /** 宣召中的主领与回应标签统一使用 28 号字，不影响经文正文的字号配置。 */
    private static final double SUMMON_LABEL_FONT_SIZE = 28.0;
    /** 宣召回应标题使用 1.5 倍行距，与下方回应内容保持更清晰的层次。 */
    private static final double SUMMON_RESPONSE_LABEL_LINE_SPACING = 150.0;

    /** 宣召正文首行缩进约两个中文字符，与约 8 个半角空格的视觉宽度相当。 */
    private static final double SUMMON_CONTENT_FIRST_LINE_INDENT = 72.0;

    /**
     * 回应方式，取值为“经文”或“自定义”。
     */
    private final String summonMode;

    /**
     * 主领输入内容。经文模式下表示经文编号，自定义模式下表示直接显示的文字。
     */
    private final String leaderContent;

    /**
     * 回应输入内容。经文模式下表示经文编号，自定义模式下表示直接显示的文字。
     */
    private final String responseContent;

    private final ScriptureService scriptureService;

    /**
     * 创建宣召阶段。
     *
     * @param ppt              当前正在生成的演示文稿
     * @param layout           宣召幻灯片版式名称
     * @param scriptureService 经文查询服务
     * @param summonMode       回应方式，支持“经文”和“自定义”
     * @param leaderContent    主领输入内容
     * @param responseContent  回应输入内容
     */
    public SummonStep(XMLSlideShow ppt, String layout, ScriptureService scriptureService,
                      String summonMode, String leaderContent, String responseContent) {
        super(ppt, layout);
        this.scriptureService = scriptureService;
        this.summonMode = summonMode;
        this.leaderContent = leaderContent;
        this.responseContent = responseContent;
    }

    /**
     * 生成宣召幻灯片。
     *
     * <p>主领始终按照 scripture-format1.ftl 查询并格式化；回应根据回应方式
     * 选择经文解析或原样显示。</p>
     *
     * @throws WorshipStepException 生成宣召内容失败
     * @throws PPTLayoutException   宣召版式或占位符不存在
     */
    @Override
    public void execute() throws WorshipStepException, PPTLayoutException {
        // 主领始终输入经文编号并走经文解析流程；回应方式只控制回应内容。
        String leaderText = resolveScriptureContent(leaderContent, "主领");
        String responseText = resolveResponseContent(responseContent);

        XMLSlideShow ppt = getPpt();
        XSLFSlideLayout layout = ppt.findLayout(getLayout());
        XSLFSlide slide = ppt.createSlide(layout);

        // 模板顶部已经有固定的“宣召”标题；这里只写入主领输入的经文编号，避免标题重复。
        XSLFTextShape placeholder = TextUtil.getPlaceholderSafely(slide, 0, getLayout(), "标题部分");

        XSLFTextRun titleTextRun = TextUtil.clearAndCreateTextRun(placeholder);
        // 经文标题优先使用统一配置；未配置时回退到父类默认字号。
        titleTextRun.setFontSize(SystemConfig.getUserConfigOrDefault(
                Dict.PPTProperty.GENERAL_TITLE_FONT_SIZE,
                AbstractWorshipStep.DEFAULT_TITLE_FONT_SIZE));
        setDefaultTitleLineSpacing(titleTextRun);
        titleTextRun.setText(resolveTitle());
        titleTextRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
        TextUtil.setScriptureFontColor(titleTextRun, TextUtil.FontColor.RGB_FONT_COLOR_WHITE);

        // 经文部分
        double scriptureFontSize = SystemConfig.getUserConfigOrDefault(Dict.PPTProperty.SUMMON_SCRIPTURE_FONT_SIZE, AbstractWorshipStep.DEFAULT_SCRIPTURE_FONT_SIZE);

        placeholder = TextUtil.getPlaceholderSafely(slide, 1, getLayout(), "正文部分");
        placeholder.clearText();
        // 标签独占一行，正文另起一行并统一缩进，避免正文换行后回到标签左侧。
        addSummonLabelParagraph(placeholder, "主领:  ", SUMMON_LABEL_FONT_SIZE,
                TextUtil.FontColor.RGB_FONT_COLOR_BLACK, 0.0, SUMMON_LABEL_LINE_SPACING);
        addSummonContentParagraph(placeholder, leaderText, scriptureFontSize,
                TextUtil.FontColor.RGB_FONT_COLOR_BLACK);

        // 保留主领与回应之间的视觉间隔，但不再依赖正文中的换行字符控制布局。
        addSummonLabelParagraph(placeholder, "回应:  ", SUMMON_LABEL_FONT_SIZE,
                TextUtil.FontColor.RGB_FONT_COLOR_BLUE, 12.0, SUMMON_RESPONSE_LABEL_LINE_SPACING);
        addSummonContentParagraph(placeholder, responseText, scriptureFontSize,
                TextUtil.FontColor.RGB_FONT_COLOR_BLUE);

        logger.info("宣召幻灯片制作完成");
    }

    /** 添加主领或回应标签行，标签保持普通字重。 */
    private void addSummonLabelParagraph(XSLFTextShape placeholder,
                                         String label,
                                         double fontSize,
                                         int[] fontColor,
                                         double spaceBefore,
                                         double lineSpacing) {
        XSLFTextParagraph paragraph = placeholder.addNewTextParagraph();
        paragraph.setLineSpacing(lineSpacing);
        paragraph.setSpaceBefore(spaceBefore);
        useCustomLanguage(paragraph);
        XSLFTextRun textRun = paragraph.addNewTextRun();
        textRun.setText(label);
        textRun.setFontSize(fontSize);
        textRun.setBold(false);
        TextUtil.setScriptureFontColor(textRun, fontColor);
    }

    /** 添加宣召正文，正文加粗且仅首行向右缩进。 */
    private void addSummonContentParagraph(XSLFTextShape placeholder,
                                           String content,
                                           double fontSize,
                                           int[] fontColor) {
        XSLFTextParagraph paragraph = placeholder.addNewTextParagraph();
        paragraph.setLineSpacing(SUMMON_LINE_SPACING);
        // 只缩进首行，换行后的经文回到正文区域左侧，符合宣召版式。
        paragraph.setLeftMargin(0.0);
        paragraph.setIndent(SUMMON_CONTENT_FIRST_LINE_INDENT);
        useCustomLanguage(paragraph);
        XSLFTextRun textRun = paragraph.addNewTextRun();
        textRun.setText(content);
        textRun.setFontSize(fontSize);
        textRun.setBold(true);
        textRun.setUnderlined(false);
        TextUtil.setScriptureFontColor(textRun, fontColor);
    }

    /**
     * 解析主领经文内容。
     *
     * @param content 输入内容
     * @param role    内容所属角色，用于异常信息
     * @return 用于写入 PPT 的正文
     * @throws WorshipStepException 经文编号解析失败
     */
    private String resolveScriptureContent(String content, String role) throws WorshipStepException {
        String normalizedContent = content == null ? "" : content.trim();
        ScriptureNumberEntity scriptureNumberEntity;
        try {
            scriptureNumberEntity = ScriptureUtil.parseNumber(normalizedContent);
        } catch (ScriptureNumberException e) {
            throw new WorshipStepException("宣召" + role + "：解析经文编号 [" + content + "] 时出错！", e);
        }

        if (!scriptureService.validateNumber(scriptureNumberEntity)) {
            throw new ScriptureServiceException("宣召" + role + "：经文编号 [" + normalizedContent + "] 不存在！");
        }

        // 经文格式由配置统一指定，当前宣召主领和回应均使用 scripture-format1.ftl。
        return scriptureService.getScriptureWithFormat(
                scriptureNumberEntity,
                SystemConfig.getString(Dict.ScriptureProperty.FORMAT1)
        ).getScripture();
    }

    /**
     * 根据回应方式解析回应内容。
     *
     * <p>自定义模式下保持输入文本原样；经文模式下使用与主领相同的
     * scripture-format1.ftl 模板解析。</p>
     *
     * @param content 回应输入内容
     * @return PPT 正文内容
     * @throws WorshipStepException 经文编号解析失败
     */
    private String resolveResponseContent(String content) throws WorshipStepException {
        if (Objects.equals(Dict.SummonMode.CUSTOM, normalizeSummonMode())) {
            return content == null ? "" : content;
        }
        return resolveScriptureContent(content, "回应");
    }

    /**
     * 统一回应方式的取值，兼容旧缓存中可能出现的空白或空值。
     *
     * @return 标准化后的回应方式
     */
    private String normalizeSummonMode() {
        if (summonMode == null || summonMode.trim().isEmpty()) {
            return Dict.SummonMode.SCRIPTURE;
        }
        return summonMode.trim();
    }

    /**
     * 获取宣召幻灯片标题。
     *
     * <p>主领始终是经文输入，因此沿用主领经文编号作为动态标题。</p>
     *
     * @return 标题占位符文本
     */
    private String resolveTitle() {
        return leaderContent == null ? "" : leaderContent.trim();
    }
}
