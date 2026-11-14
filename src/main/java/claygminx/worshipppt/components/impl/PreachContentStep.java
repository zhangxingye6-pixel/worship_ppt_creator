package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.exception.PPTLayoutException;
import claygminx.worshipppt.common.entity.PreachEntity;
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

    private final List<String> contents;
    private final PreachEntity preachEntity;

    public PreachContentStep(XMLSlideShow ppt, String layout, PreachEntity preachEntity, List<String> contents) {
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

    /** 将单页正文按输入换行拆为独立段落，并套用统一字号和字重。 */
    private void fillContent(XSLFSlide slide, String content) throws PPTLayoutException {
        XSLFTextShape placeholder = TextUtil.getPlaceholderSafely(
                slide, 1, getLayout(), "证道正文");
        placeholder.clearText();

        String[] lines = content.split("\\R", -1);
        for (String line : lines) {
            XSLFTextParagraph paragraph = placeholder.addNewTextParagraph();
            useCustomLanguage(paragraph);
            XSLFTextRun textRun = paragraph.addNewTextRun();
            textRun.setText(line);
            textRun.setFontFamily(DEFAULT_FONT_FAMILY);
            textRun.setFontSize(getTitleFontSize());
            textRun.setBold(false);
        }
    }
}
