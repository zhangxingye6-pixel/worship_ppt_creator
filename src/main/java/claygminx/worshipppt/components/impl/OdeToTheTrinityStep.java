package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.Dict;
import claygminx.worshipppt.common.config.SystemConfig;
import claygminx.worshipppt.exception.PPTLayoutException;
import claygminx.worshipppt.util.TextUtil;
import org.apache.poi.xslf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 三一颂阶段
 */
public class OdeToTheTrinityStep extends AbstractWorshipStep {

    private final static Logger logger = LoggerFactory.getLogger(OdeToTheTrinityStep.class);

    private final String title;

    public OdeToTheTrinityStep(XMLSlideShow ppt, String layout, String title) {
        super(ppt, layout);
        this.title = title;
    }

    @Override
    public void execute() throws PPTLayoutException {
        XMLSlideShow ppt = getPpt();
        XSLFSlideLayout layout = ppt.findLayout(getLayout());
        XSLFSlide slide = ppt.createSlide(layout);

        XSLFTextShape placeholder = TextUtil.getPlaceholderSafely(slide, 0, getLayout(), "");
        XSLFTextRun textRun = TextUtil.clearAndCreateTextRun(placeholder);
        textRun.setText(" " + title);
        textRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
        // 三一颂标题使用统一标题字号；未配置时回退到父类默认值。
        textRun.setFontSize(SystemConfig.getUserConfigOrDefault(
                Dict.PPTProperty.GENERAL_TITLE_FONT_SIZE,
                AbstractWorshipStep.DEFAULT_TITLE_FONT_SIZE));
        setDefaultTitleLineSpacing(textRun);
        // 两个流程中的三一颂标题统一使用非粗体样式。
        textRun.setBold(false);
        TextUtil.setScriptureFontColor(textRun, TextUtil.FontColor.RGB_FONT_COLOR_WHITE);
        logger.info("三一颂幻灯片制作完成");
    }
}
