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
import org.apache.poi.sl.usermodel.TextParagraph;

import java.util.ArrayList;
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
            for (String rawContent : contents) {
                for (String content : splitItems(rawContent)) {
                    if (content.isEmpty()) {
                        continue;
                    }

                    addContentParagraph(placeholder, content, displayIndex,
                            AbstractWorshipStep.DEFAULT_SCRIPTURE_FONT_SIZE);
                    displayIndex++;
                }
            }
        }

        logger.info("公祷事项幻灯片制作完成");
    }

    /**
     * 渲染一条公祷事项，使用与读经、证道经文相同的测量方式设置续行缩进。
     *
     * <p>编号与正文之间不额外插入空格，仅测量实际渲染的编号前缀；段落左边距
     * 与首行负缩进相互抵消，使首行编号保持原位，续行与正文起点对齐。</p>
     *
     * @param placeholder 公祷事项正文文本框
     * @param content 已去除输入编号的正文
     * @param displayIndex 程序生成的显示序号
     * @param fontSize 实际用于正文渲染的字号，单位为磅
     * @return 已设置样式及动态悬挂缩进的段落
     */
    XSLFTextParagraph addContentParagraph(XSLFTextShape placeholder, String content,
                                          int displayIndex, double fontSize) {
        XSLFTextParagraph paragraph = placeholder.addNewTextParagraph();
        paragraph.setLineSpacing(LINE_SPACING);
        useCustomLanguage(paragraph);

        String numberPrefix = displayIndex + ".";
        double hangingIndent = measureTextWidth(numberPrefix, fontSize,
                AbstractWorshipStep.DEFAULT_FONT_FAMILY);
        paragraph.setTextAlign(TextParagraph.TextAlign.LEFT);
        paragraph.setLeftMargin(hangingIndent);
        paragraph.setIndent(-hangingIndent);

        XSLFTextRun textRun = paragraph.addNewTextRun();
        textRun.setText(numberPrefix + content);
        textRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
        textRun.setFontSize(fontSize);
        textRun.setBold(false);
        TextUtil.setScriptureFontColor(textRun, TextUtil.FontColor.RGB_FONT_COLOR_BLACK);
        return paragraph;
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
