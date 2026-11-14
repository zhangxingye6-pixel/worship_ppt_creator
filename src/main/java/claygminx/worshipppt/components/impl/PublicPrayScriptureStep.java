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
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.xslf.usermodel.XMLSlideShow;

import java.util.List;

/**
 * 公祷经文阶段
 */
public class PublicPrayScriptureStep extends AbstractWorshipStep {

    private final static Logger logger = LoggerFactory.getLogger(PublicPrayScriptureStep.class);
    /** PowerPoint 使用百分比表示段落行距，120 表示 1.2 倍行距。 */
    private static final double SCRIPTURE_LINE_SPACING = 120.0;

    private final String scriptureNumber;
    private final ScriptureService scriptureService;

    public PublicPrayScriptureStep(XMLSlideShow ppt, String layout, ScriptureService scriptureService, String scriptureNumber) {
        super(ppt, layout);
        this.scriptureService = scriptureService;
        this.scriptureNumber = scriptureNumber;
    }

    @Override
    public void execute() throws WorshipStepException, PPTLayoutException {
        try {
            List<ScriptureNumberEntity> publicScriptureNumberList = ScriptureUtil.parseNumbers(scriptureNumber);
            fillTitleAndScripture(getLayout(), scriptureService, publicScriptureNumberList);
            applyScriptureLineSpacing();
            logger.info("公祷经文幻灯片制作完成");
        } catch (ScriptureNumberException e) {
            throw new WorshipStepException("解析公祷经文编号 [" + scriptureNumber + "] 时出错！", e);
        }
    }

    /** 仅调整刚生成的公祷经文正文行距，不改变通用经文阶段的默认样式。 */
    private void applyScriptureLineSpacing() throws PPTLayoutException {
        XMLSlideShow ppt = getPpt();
        XSLFSlide slide = ppt.getSlides().get(ppt.getSlides().size() - 1);
        XSLFTextShape contentPlaceholder = TextUtil.getPlaceholderSafely(slide, 1, getLayout(), "正文部分");
        for (XSLFTextParagraph paragraph : contentPlaceholder.getTextParagraphs()) {
            paragraph.setLineSpacing(SCRIPTURE_LINE_SPACING);
        }
    }
}
