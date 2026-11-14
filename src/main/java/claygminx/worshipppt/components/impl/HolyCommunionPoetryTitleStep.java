package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.entity.PoetryEntity;
import claygminx.worshipppt.exception.PPTLayoutException;
import claygminx.worshipppt.util.TextUtil;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextCharacterProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.xslf.usermodel.*;

import java.util.List;

/**
 * 圣餐诗歌标题阶段
 */
public class HolyCommunionPoetryTitleStep extends AbstractWorshipStep {

    private final static Logger logger = LoggerFactory.getLogger(HolyCommunionPoetryTitleStep.class);

    private final List<PoetryEntity> poetryList;

    public HolyCommunionPoetryTitleStep(XMLSlideShow ppt, String layout, List<PoetryEntity> poetryList) {
        super(ppt, layout);
        this.poetryList = poetryList;
    }

    @Override
    public void execute() throws PPTLayoutException {
        XMLSlideShow ppt = getPpt();
        XSLFSlideLayout layout = ppt.findLayout(getLayout());

        label1:
        for (PoetryEntity poetryEntity : poetryList) {
            XSLFSlide slide = ppt.createSlide(layout);
            XSLFTextShape placeholder = TextUtil.getPlaceholderSafely(slide, 0, getLayout(), "");
            List<XSLFTextParagraph> paragraphs = placeholder.getTextParagraphs();
            for (XSLFTextParagraph paragraph : paragraphs) {
                List<XSLFTextRun> textRuns = paragraph.getTextRuns();
                for (XSLFTextRun textRun : textRuns) {
                    String rawText = textRun.getRawText();
                    if (rawText.contains("圣餐诗歌")){
                        textRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
                        textRun.setFontSize(54.0);
                        textRun.setBold(false);
                        TextUtil.setScriptureFontColor(textRun, TextUtil.FontColor.RGB_FONT_COLOR_BLACK);
                    }
                    // 自定义占位符替换成诗歌名
                    if (rawText.contains(getCustomPlaceholder())) {
                        textRun.setText(rawText.replace(getCustomPlaceholder(), poetryEntity.getName()));
                        textRun.setFontSize(54.0);
                        textRun.setBold(false);
                        TextUtil.setScriptureFontColor(textRun, TextUtil.FontColor.RGB_FONT_COLOR_BLACK);
                        textRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
                        continue label1;
                    }
                }
            }
        }

        logger.info("圣餐诗歌标题幻灯片制作完成");
    }
}
