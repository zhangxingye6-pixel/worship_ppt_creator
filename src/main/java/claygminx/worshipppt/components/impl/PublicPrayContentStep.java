package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.exception.PPTLayoutException;
import claygminx.worshipppt.util.TextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.util.List;

/**
 * 公祷内容阶段
 */
public class PublicPrayContentStep extends AbstractWorshipStep {

    private final static Logger logger = LoggerFactory.getLogger(PublicPrayContentStep.class);

    /** PowerPoint 使用百分比表示段落行距，120 表示1.2倍行距。 */
    private static final double LINE_SPACING = 120.0;

    private final List<String> contents;

    public PublicPrayContentStep(XMLSlideShow ppt, String layout, List<String> contents) {
        super(ppt, layout);
        this.contents = contents;
    }

    @Override
    public void execute() throws PPTLayoutException {
        XMLSlideShow ppt = getPpt();
        XSLFSlideLayout layout = ppt.findLayout(getLayout());
        XSLFSlide slide = ppt.createSlide(layout);
        XSLFTextShape placeholder = TextUtil.getPlaceholderSafely(slide, 0, getLayout(), "公祷事项");
        placeholder.clearText();

        int displayIndex = 1;
        if (contents != null) {
            for (int i = 0; i < contents.size(); i++) {
                String content = contents.get(i);
                if (content == null || content.trim().isEmpty()) {
                    continue;
                }

                XSLFTextParagraph paragraph = placeholder.addNewTextParagraph();
                paragraph.setLineSpacing(LINE_SPACING);
                useCustomLanguage(paragraph);

                XSLFTextRun textRun = paragraph.addNewTextRun();
                textRun.setText(displayIndex++ + "." + content.trim());
                textRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
                textRun.setFontSize(AbstractWorshipStep.DEFAULT_SCRIPTURE_FONT_SIZE);
                textRun.setBold(false);
                TextUtil.setScriptureFontColor(textRun, TextUtil.FontColor.RGB_FONT_COLOR_BLACK);
            }
        }

        logger.info("公祷事项幻灯片制作完成");
    }
}
