package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.Dict;
import claygminx.worshipppt.common.config.SystemConfig;
import claygminx.worshipppt.exception.PPTLayoutException;
import claygminx.worshipppt.util.TextUtil;
import org.apache.poi.xslf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class FamilyReportStep extends AbstractWorshipStep {

    private final static Logger logger = LoggerFactory.getLogger(FamilyReportStep.class);
    private final List<String> familyReports;

    public FamilyReportStep(XMLSlideShow ppt, String layout, List<String> familyReports) {
        super(ppt, layout);
        this.familyReports = familyReports;
    }

    @Override
    public void execute() throws PPTLayoutException {
        XMLSlideShow ppt = getPpt();
        XSLFSlideLayout layout = ppt.findLayout(getLayout());
        XSLFSlide slide = ppt.createSlide(layout);

        if (familyReports != null && familyReports.size() > 0) {
            XSLFTextShape placeholder = TextUtil.getPlaceholderSafely(slide, 0, getLayout(), "");
            placeholder.clearText();

            logger.debug(familyReports.size() + "项家事报告");
            for (int i = 0; i < familyReports.size(); i++) {
                String item = familyReports.get(i);
                XSLFTextParagraph paragraph = placeholder.addNewTextParagraph();
                XSLFTextRun textRun = paragraph.addNewTextRun();
                textRun.setText((i + 1) + "." + item);
                textRun.setFontSize(SystemConfig.getUserConfigOrDefault(
                        Dict.PPTProperty.FAMILY_REPORT_FONT_SIZE,
                        AbstractWorshipStep.DEFAULT_FAMILY_REPORT_FONT_SIZE));
            }
        }

        logger.info("家事报告幻灯片制作完成");
    }
}
