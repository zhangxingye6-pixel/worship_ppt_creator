package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.entity.PreachEntity;
import claygminx.worshipppt.exception.PPTLayoutException;
import claygminx.worshipppt.util.TextUtil;
import org.apache.poi.xslf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 证道摘要阶段
 */
public class PreachSummaryStep extends AbstractWorshipStep {

    private final static Logger logger = LoggerFactory.getLogger(PreachSummaryStep.class);

    private final PreachEntity preachEntity;

    public PreachSummaryStep(XMLSlideShow ppt, String layout, PreachEntity preachEntity) {
        super(ppt, layout);
        this.preachEntity = preachEntity;
    }

    @Override
    public void execute() throws PPTLayoutException {
        XMLSlideShow ppt = getPpt();
        XSLFSlideLayout layout = ppt.findLayout(getLayout());
        XSLFSlide slide = ppt.createSlide(layout);


        XSLFTextShape placeholder = TextUtil.getPlaceholderSafely(slide, 0, getLayout(), "标题部分");

        XSLFTextRun textRun = TextUtil.clearAndCreateTextRun(placeholder);
        textRun.setText(preachEntity.getTitle());
        textRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
        textRun.setFontSize(AbstractWorshipStep.DEFAULT_STEP_COVER_FONT_SIZE);
        textRun.setBold(true);
        TextUtil.setScriptureFontColor(textRun, TextUtil.FontColor.RGB_FONT_COLOR_BLACK);

        placeholder = TextUtil.getPlaceholderSafely(slide, 1, getLayout(), "经文编号部分");
        textRun = TextUtil.clearAndCreateTextRun(placeholder);
        textRun.setText("证道经文: " + preachEntity.getScriptureNumber());
        textRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
        textRun.setFontSize(AbstractWorshipStep.DEFAULT_SCRIPTURE_FONT_SIZE);
        textRun.setBold(true);
        TextUtil.setScriptureFontColor(textRun, TextUtil.FontColor.RGB_FONT_COLOR_BLACK);

        logger.info("证道摘要幻灯片制作完成");
    }
}
