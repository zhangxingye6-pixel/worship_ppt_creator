package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.Dict;
import claygminx.worshipppt.common.config.SystemConfig;
import claygminx.worshipppt.common.entity.PoetryEntity;
import claygminx.worshipppt.exception.PPTLayoutException;
import claygminx.worshipppt.util.TextUtil;
import org.apache.poi.xslf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 诗歌标题阶段
 */
public class PoetryTitleStep extends AbstractWorshipStep {

    private final static Logger logger = LoggerFactory.getLogger(PoetryTitleStep.class);

    private final String slideName;
    private final String poetryName;
    private final String albumName;
    private final int mode;
    private final List<PoetryEntity> fullPoetryList;

    // 字体常量
    private final double DEFAULT_POETRY_COVER_FONT_SIZE = 55.0;
    private final double DEFAULT_POETRY_TITLE_FONT_SIZE = 40.0;

    public PoetryTitleStep(XMLSlideShow ppt, String layout, String slideName, String poetryName) {
        this(ppt, layout, slideName, poetryName, null, 0, null);
    }

    public PoetryTitleStep(XMLSlideShow ppt, String layout, String slideName, String poetryName,
                           String albumName, int mode, List<PoetryEntity> fullPoetryList) {
        super(ppt, layout);
        this.slideName = slideName;
        this.poetryName = poetryName;
        this.albumName = albumName;
        this.mode = mode;
        this.fullPoetryList = fullPoetryList;
    }

    @Override
    public void execute() throws PPTLayoutException {
        String effectivePoetryName = poetryName;
        if ("敬拜诗歌".equals(albumName) && mode == 1 && fullPoetryList != null && fullPoetryList.size() >= 3) {
            if (fullPoetryList.size() > 1 && poetryName.equals(fullPoetryList.get(1).getName())) {
                effectivePoetryName = fullPoetryList.get(2).getName();
            }
        }

        XMLSlideShow ppt = getPpt();
        XSLFSlideLayout layout = ppt.findLayout(getLayout());
        XSLFSlide slide = ppt.createSlide(layout);

        fillPlaceholder(slide, 0, " " + slideName);
        fillPlaceholder(slide, 1, effectivePoetryName);

        logger.info("诗歌标题 - 幻灯片制作完成");
    }

    private void fillPlaceholder(XSLFSlide slide, int idx, String text) throws PPTLayoutException {
        XSLFTextShape placeholder = TextUtil.getPlaceholderSafely(slide, idx, getLayout(), "");
        XSLFTextRun textRun = TextUtil.clearAndCreateTextRun(placeholder);
        textRun.setText(text.trim());
        String fontStyle = SystemConfig.getUserConfigOrDefault(Dict.PPTProperty.POETRY_TITLE_FONT_FAMILT, DEFAULT_FONT_FAMILY);
        textRun.setFontFamily(fontStyle);
        if (idx == 1) {  // 制作诗歌封面
            textRun.setFontSize(SystemConfig.getUserConfigOrDefault(Dict.PPTProperty.POETRY_TITLE_FONT_SIZE, DEFAULT_POETRY_COVER_FONT_SIZE));
            textRun.setFontSize(AbstractWorshipStep.DEFAULT_STEP_COVER_FONT_SIZE);
            TextUtil.setScriptureFontColor(textRun, TextUtil.FontColor.RGB_FONT_COLOR_BLACK);
        } else {     // 制作诗歌标题
            textRun.setFontSize(DEFAULT_POETRY_TITLE_FONT_SIZE);
            TextUtil.setScriptureFontColor(textRun, TextUtil.FontColor.RGB_FONT_COLOR_WHITE);
        }


    }
}
