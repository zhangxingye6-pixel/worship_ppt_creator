package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.exception.PPTLayoutException;
import claygminx.worshipppt.util.TextUtil;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 证道回应阶段，将用户输入的事项统一编号后写入母版的单一占位符。
 */
public class PreachResponseStep extends AbstractWorshipStep {

    private static final Logger logger = LoggerFactory.getLogger(PreachResponseStep.class);
    /** PowerPoint 使用百分比表示段落行距，120 表示 1.2 倍行距。 */
    private static final double LINE_SPACING = 120.0;
    /** 续行缩进对齐到默认字号下编号“1.”之后的正文起始位置。 */
    private static final double CONTINUATION_INDENT = AbstractWorshipStep.DEFAULT_SCRIPTURE_FONT_SIZE;
    /**
     * 只匹配行首编号，避免误删事项正文中的数字。
     *
     * <p>同时覆盖阿拉伯数字、全角数字、常见中文数字、带括号编号和带“第”字的编号。
     * 编号清理只发生在每一行的开头，正文中的正常数字和括号会原样保留。</p>
     */
    private static final String NUMBER_TOKEN =
            "[0-9０-９〇零一二三四五六七八九十百千万两壹贰叁肆伍陆柒捌玖拾佰仟]+";

    private static final Pattern LEADING_NUMBER = Pattern.compile(
            "^\\s*(?:"
                    + "[（(]\\s*" + NUMBER_TOKEN + "\\s*[）)]\\s*"
                    + "|第\\s*(?:[（(]\\s*" + NUMBER_TOKEN + "\\s*[）)]|"
                    + NUMBER_TOKEN + "(?:\\s*(?:章|节|项|条|点|题))?)"
                    + "(?:\\s*[.．、,:：)）]|\\s+|$)\\s*"
                    + "|" + NUMBER_TOKEN
                    + "\\s*[.．、,:：)）]\\s*"
                    + "|" + NUMBER_TOKEN + "\\s+"
                    + "|[①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳]\\s*[.．、,:：]?\\s*"
                    + ")");

    private final List<String> contents;

    public PreachResponseStep(XMLSlideShow ppt, String layout, List<String> contents) {
        super(ppt, layout);
        this.contents = contents;
    }

    /**
     * 清理各事项开头已有的中文或阿拉伯数字编号，并过滤空行。
     *
     * @param rawContents 用户输入的多行文本
     * @return 未编号的有效事项
     */
    public static List<String> normalizeItems(String rawContents) {
        return rawContents == null ? new ArrayList<>() : normalizeItems(List.of(rawContents));
    }

    /**
     * 对直接传入渲染层的事项列表执行与 GUI 相同的拆行和编号清理。
     *
     * @param rawContents 原始事项列表，列表元素也可能包含多行文本
     * @return 清理后的非空事项
     */
    public static List<String> normalizeItems(List<String> rawContents) {
        List<String> items = new ArrayList<>();
        if (rawContents == null) {
            return items;
        }

        for (String rawContent : rawContents) {
            if (rawContent == null) {
                continue;
            }
            for (String line : rawContent.split("\\R", -1)) {
                String item = stripLeadingNumbering(line).trim();
                if (!item.isEmpty()) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    /** 连续清理行首可能重复输入的编号，直到文本不再变化。 */
    private static String stripLeadingNumbering(String line) {
        String current = line == null ? "" : line;
        String previous;
        do {
            previous = current;
            current = LEADING_NUMBER.matcher(current).replaceFirst("");
        } while (!current.equals(previous));
        return current;
    }

    @Override
    public void execute() throws PPTLayoutException {
        XMLSlideShow ppt = getPpt();
        XSLFSlideLayout layout = ppt.findLayout(getLayout());
        XSLFSlide slide = ppt.createSlide(layout);
        XSLFTextShape placeholder = TextUtil.getUniqueContentPlaceholder(slide, getLayout(), "证道回应正文");
        placeholder.clearText();

        int displayIndex = 1;
        for (String content : normalizeItems(contents)) {
            XSLFTextParagraph paragraph = placeholder.addNewTextParagraph();
            paragraph.setLineSpacing(LINE_SPACING);
            paragraph.setLeftMargin(CONTINUATION_INDENT);
            paragraph.setIndent(-CONTINUATION_INDENT);
            useCustomLanguage(paragraph);

            XSLFTextRun textRun = paragraph.addNewTextRun();
            textRun.setText(displayIndex++ + "." + content);
            textRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
            textRun.setFontSize(AbstractWorshipStep.DEFAULT_SCRIPTURE_FONT_SIZE);
            textRun.setBold(false);
            TextUtil.setScriptureFontColor(textRun, TextUtil.FontColor.RGB_FONT_COLOR_BLACK);
        }

        logger.info("证道回应幻灯片制作完成，共 {} 项", displayIndex - 1);
    }
}
