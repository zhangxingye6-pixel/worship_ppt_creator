package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.exception.PPTLayoutException;
import claygminx.worshipppt.common.entity.PreachEntity;
import claygminx.worshipppt.common.entity.PreachContentPageEntity;
import claygminx.worshipppt.util.TextUtil;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 按用户输入顺序将证道正文渲染到同数量的证道版式页面。
 */
public class PreachContentStep extends AbstractWorshipStep {

    private static final Logger logger = LoggerFactory.getLogger(PreachContentStep.class);
    /** 单页小标题使用固定的 30 磅字号，区别于页面顶端的证道主题。 */
    private static final double SUBTITLE_FONT_SIZE = 30.0;
    /** 小标题单独使用 0.85 倍行距，不影响下方正文。 */
    private static final double SUBTITLE_LINE_SPACING = 85.0;
    /** PowerPoint 使用百分比表示行距，110 表示正文使用 1.1 倍行距。 */
    private static final double CONTENT_LINE_SPACING = 110.0;

    private final List<PreachContentPageEntity> contents;
    private final PreachEntity preachEntity;

    public PreachContentStep(XMLSlideShow ppt, String layout, PreachEntity preachEntity,
                             List<PreachContentPageEntity> contents) {
        super(ppt, layout);
        this.preachEntity = preachEntity;
        this.contents = contents;
    }

    @Override
    public void execute() throws PPTLayoutException {
        XMLSlideShow ppt = getPpt();
        XSLFSlideLayout layout = ppt.findLayout(getLayout());
        if (layout == null) {
            throw new PPTLayoutException("PPT母版缺少“" + getLayout() + "”版式，请添加后重新制作");
        }

        if (contents.isEmpty()) {
            return;
        }

        // PreachStep 已在流程中紧邻本步骤之前创建默认证道页，首段正文应复用该页。
        XSLFSlide firstSlide = ppt.getSlides().get(ppt.getSlides().size() - 1);
        XSLFSlideLayout firstSlideLayout = firstSlide.getSlideLayout();
        if (firstSlideLayout == null || !getLayout().equals(firstSlideLayout.getName())) {
            throw new PPTLayoutException("无法找到证道阶段刚生成的默认证道页面");
        }

        for (int pageIndex = 0; pageIndex < contents.size(); pageIndex++) {
            XSLFSlide slide = pageIndex == 0 ? firstSlide : ppt.createSlide(layout);
            fillTitle(slide);
            fillContent(slide, contents.get(pageIndex));
        }

        logger.info("证道正文幻灯片制作完成，共 {} 页", contents.size());
    }

    /** 按默认证道页的格式填充证道标题，确保新增页标题一致。 */
    private void fillTitle(XSLFSlide slide) throws PPTLayoutException {
        XSLFTextShape titlePlaceholder = TextUtil.getPlaceholderSafely(
                slide, 0, getLayout(), "证道标题");
        XSLFTextRun titleRun = TextUtil.clearAndCreateTextRun(titlePlaceholder);
        titleRun.setFontFamily(DEFAULT_FONT_FAMILY);
        titleRun.setFontSize(getTitleFontSize());
        TextUtil.setScriptureFontColor(titleRun, TextUtil.FontColor.RGB_FONT_COLOR_WHITE);
        titleRun.setText("《" + preachEntity.getTitle() + "》");
    }

    /** 将本页小标题与正文一起填入正文占位符，保持默认证道页与新增页结构一致。 */
    private void fillContent(XSLFSlide slide, PreachContentPageEntity page) throws PPTLayoutException {
        XSLFTextShape placeholder = TextUtil.getPlaceholderSafely(
                slide, 1, getLayout(), "证道正文");
        renderPageContent(placeholder, page);
    }

    /**
     * 渲染独立的小标题及正文段落：小标题使用 0.85 倍行距，正文使用 1.1 倍。
     *
     * @param placeholder 正文占位符，不改变其位置与尺寸
     * @param page 当前页的小标题及正文
     */
    void renderPageContent(XSLFTextShape placeholder, PreachContentPageEntity page) {
        placeholder.clearText();
        if (!page.getSubtitle().isEmpty()) {
            XSLFTextParagraph subtitleParagraph = placeholder.addNewTextParagraph();
            subtitleParagraph.setLineSpacing(SUBTITLE_LINE_SPACING);
            useCustomLanguage(subtitleParagraph);
            XSLFTextRun subtitleRun = subtitleParagraph.addNewTextRun();
            subtitleRun.setText(page.getSubtitle());
            subtitleRun.setFontFamily(DEFAULT_FONT_FAMILY);
            subtitleRun.setFontSize(SUBTITLE_FONT_SIZE);
            subtitleRun.setBold(true);
            TextUtil.setScriptureFontColor(subtitleRun, TextUtil.FontColor.RGB_FONT_COLOR_RED);
        }

        String[] lines = page.getContent().isEmpty() ? new String[0] : page.getContent().split("\\R", -1);
        for (String line : lines) {
            XSLFTextParagraph paragraph = placeholder.addNewTextParagraph();
            paragraph.setLineSpacing(CONTENT_LINE_SPACING);
            useCustomLanguage(paragraph);
            XSLFTextRun textRun = paragraph.addNewTextRun();
            textRun.setText(line);
            textRun.setFontFamily(DEFAULT_FONT_FAMILY);
            textRun.setFontSize(getTitleFontSize());
            textRun.setBold(false);
        }
    }
}
