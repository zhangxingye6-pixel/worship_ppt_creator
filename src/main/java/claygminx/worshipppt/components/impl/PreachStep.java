package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.entity.PreachEntity;
import claygminx.worshipppt.exception.PPTLayoutException;
import claygminx.worshipppt.util.TextUtil;
import org.apache.poi.xslf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 证道阶段
 */
public class PreachStep extends AbstractWorshipStep {

    private final static Logger logger = LoggerFactory.getLogger(PreachStep.class);

    private final PreachEntity preachEntity;

    public PreachStep(XMLSlideShow ppt, String layout, PreachEntity preachEntity) {
        super(ppt, layout);
        this.preachEntity = preachEntity;
    }

    @Override
    public void execute() throws PPTLayoutException {
        XMLSlideShow ppt = getPpt();
        XSLFSlideLayout layout = ppt.findLayout(getLayout());
        XSLFSlide slide = ppt.createSlide(layout);

        XSLFTextShape placeholder = TextUtil.getPlaceholderSafely(slide, 0, getLayout(), "");
        XSLFTextRun preachTitleTextRun = TextUtil.clearAndCreateTextRun(placeholder);
        preachTitleTextRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
        preachTitleTextRun.setFontSize(AbstractWorshipStep.DEFAULT_TITLE_FONT_SIZE);
        TextUtil.setScriptureFontColor(preachTitleTextRun, TextUtil.FontColor.RGB_FONT_COLOR_WHITE);
        preachTitleTextRun.setText("《" + preachEntity.getTitle() + "》");

        // 此页面还有第二个占位符，证道大纲，目前只能手动输入

        logger.info("证道幻灯片制作完成");
    }
}
