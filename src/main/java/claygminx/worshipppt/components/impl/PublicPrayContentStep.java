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

import java.util.ArrayList;
import java.util.List;

/**
 * 公祷内容阶段
 */
public class PublicPrayContentStep extends AbstractWorshipStep {

    private final static Logger logger = LoggerFactory.getLogger(PublicPrayContentStep.class);

    /** PowerPoint 使用百分比表示段落行距，120 表示1.2倍行距。 */
    private static final double LINE_SPACING = 120.0;
    /** 固定续行缩进，略宽于编号前缀，避免续行相对正文缩进过多。 */
    private static final double CONTINUATION_INDENT = 29.0;

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
            for (String rawContent : contents) {
                for (String content : splitItems(rawContent)) {
                    if (content.isEmpty()) {
                        continue;
                    }

                    XSLFTextParagraph paragraph = placeholder.addNewTextParagraph();
                    paragraph.setLineSpacing(LINE_SPACING);
                    useCustomLanguage(paragraph);

                    String numberPrefix = displayIndex + ".";
                    // 使用悬挂缩进：首行保留编号，自动换行后的内容与正文起始位置对齐。
                    paragraph.setLeftMargin(CONTINUATION_INDENT);
                    paragraph.setIndent(-CONTINUATION_INDENT);

                    XSLFTextRun textRun = paragraph.addNewTextRun();
                    textRun.setText(numberPrefix + content);
                    textRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
                    textRun.setFontSize(AbstractWorshipStep.DEFAULT_SCRIPTURE_FONT_SIZE);
                    textRun.setBold(false);
                    TextUtil.setScriptureFontColor(textRun, TextUtil.FontColor.RGB_FONT_COLOR_BLACK);
                    displayIndex++;
                }
            }
        }

        logger.info("公祷事项幻灯片制作完成");
    }

    /**
     * 将输入内容拆成独立事项，并移除用户手工输入的编号。
     *
     * <p>渲染编号必须由程序统一生成，否则用户输入的 1.、1、等形式会造成重复编号。</p>
     *
     * @param rawContent 原始事项文本
     * @return 清理后的非空事项列表
     */
    private List<String> splitItems(String rawContent) {
        List<String> items = new ArrayList<>();
        if (rawContent == null) {
            return items;
        }
        for (String line : rawContent.split("\\R")) {
            String item = line.replaceFirst("^\\s*\\d+\\s*[.．、)）]\\s*", "").trim();
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }

}
