package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.entity.ScriptureNumberEntity;
import claygminx.worshipppt.components.ScriptureService;
import claygminx.worshipppt.exception.PPTLayoutException;
import claygminx.worshipppt.exception.ScriptureNumberException;
import claygminx.worshipppt.exception.WorshipStepException;
import claygminx.worshipppt.util.ScriptureUtil;
import claygminx.worshipppt.util.TextUtil;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 认罪经文阶段
 */
public class ConfessScriptureStep extends AbstractWorshipStep {

    // 默认字号常量
    private final static double DEFAULT_FONT_SIZE = 32.0;
    private final static double DEFAULT_TITLE_SIZE = 40.0;

    private final static Logger logger = LoggerFactory.getLogger(ConfessScriptureStep.class);

    private final String scriptureNumber;
    private final ScriptureService scriptureService;

    public ConfessScriptureStep(XMLSlideShow ppt, String layout, ScriptureService scriptureService, String scriptureNumber) {
        super(ppt, layout);
        this.scriptureService = scriptureService;
        this.scriptureNumber = scriptureNumber;
    }

    @Override
    public void execute() throws WorshipStepException, PPTLayoutException {
        try {
            List<ScriptureNumberEntity> scriptureNumberList = ScriptureUtil.parseNumbers(scriptureNumber);
            fillTitleAndScripture("认罪经文", scriptureService, scriptureNumberList);
            applyScriptureFormatting();
            logger.info("认罪经文幻灯片制作完成");
        } catch (ScriptureNumberException e) {
            throw new WorshipStepException("解析认罪经文 [" + scriptureNumber + "] 编号时出错！", e);
        }
    }

    /** 仅调整本阶段正文的行距和字重，不影响父类渲染的其他经文页面。 */
    private void applyScriptureFormatting() throws PPTLayoutException {
        XMLSlideShow ppt = getPpt();
        XSLFSlide slide = ppt.getSlides().get(ppt.getSlides().size() - 1);
        XSLFTextShape contentPlaceholder = TextUtil.getPlaceholderSafely(slide, 1, getLayout(), "正文部分");

        for (XSLFTextParagraph paragraph : contentPlaceholder.getTextParagraphs()) {
            paragraph.setLineSpacing(120.0);
            for (XSLFTextRun textRun : paragraph.getTextRuns()) {
                textRun.setBold(true);
                // 颜色逻辑统一 会众即使在单独一页中也使用蓝色文本
                TextUtil.setScriptureFontColor(textRun, TextUtil.FontColor.RGB_FONT_COLOR_BLUE);
            }
        }
    }
}
