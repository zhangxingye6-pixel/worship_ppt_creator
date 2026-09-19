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
        titleTextRun.setFontSize(AbstractWorshipStep.DEFAULT_TITLE_FONT_SIZE);
        titleTextRun.setText(resolveTitle());
        titleTextRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
        TextUtil.setScriptureFontColor(titleTextRun, TextUtil.FontColor.RGB_FONT_COLOR_WHITE);

        // 经文部分
        double scriptureFontSize = SystemConfig.getUserConfigOrDefault(Dict.PPTProperty.SUMMON_SCRIPTURE_FONT_SIZE, AbstractWorshipStep.DEFAULT_SCRIPTURE_FONT_SIZE);

        placeholder = TextUtil.getPlaceholderSafely(slide, 1, getLayout(), "正文部分");
        placeholder.clearText();
        XSLFTextParagraph paragraph = placeholder.addNewTextParagraph();
        useCustomLanguage(paragraph);
        // 制表符
        XSLFTextRun beforeScriptureTextRun = paragraph.addNewTextRun();
        beforeScriptureTextRun.setText("主领: ");
        beforeScriptureTextRun.setFontSize(scriptureFontSize);
        // 主领内容
        XSLFTextRun scriptureTextRun = paragraph.addNewTextRun();
        scriptureTextRun.setText(leaderText);
        scriptureTextRun.setFontSize(scriptureFontSize);
        TextUtil.setScriptureFontColor(scriptureTextRun, TextUtil.FontColor.RGB_FONT_COLOR_BLACK);

        // 第二段
        paragraph = placeholder.addNewTextParagraph();
        paragraph.addNewTextRun().setText("\n");
        scriptureTextRun = paragraph.addNewTextRun();
        scriptureTextRun.setText("回应: ");
        scriptureTextRun.setFontSize(scriptureFontSize);
        TextUtil.setScriptureFontColor(scriptureTextRun, TextUtil.FontColor.RGB_FONT_COLOR_BLUE);
        // 回应内容使用蓝色、粗体和下划线，与原有宣召版式保持一致。
        scriptureTextRun = paragraph.addNewTextRun();
        scriptureTextRun.setText(responseText);
        scriptureTextRun.setBold(true);
        scriptureTextRun.setUnderlined(true);
        scriptureTextRun.setFontSize(scriptureFontSize);
        TextUtil.setScriptureFontColor(scriptureTextRun, TextUtil.FontColor.RGB_FONT_COLOR_BLUE);

        logger.info("宣召幻灯片制作完成");
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
