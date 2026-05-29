package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.config.SystemConfig;
import claygminx.worshipppt.common.entity.ScriptureEntity;
import claygminx.worshipppt.common.entity.ScriptureNumberEntity;
import claygminx.worshipppt.components.ScriptureService;
import claygminx.worshipppt.exception.PPTLayoutException;
import claygminx.worshipppt.exception.ScriptureNumberException;
import claygminx.worshipppt.exception.ScriptureServiceException;
import claygminx.worshipppt.exception.WorshipStepException;
import claygminx.worshipppt.util.TextUtil;
import claygminx.worshipppt.util.ScriptureUtil;
import claygminx.worshipppt.common.Dict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.xslf.usermodel.*;
import org.w3c.dom.Text;

/**
 * 宣召阶段
 */
public class SummonStep extends AbstractWorshipStep {

    private final static Logger logger = LoggerFactory.getLogger(SummonStep.class);

    private final String scriptureNumber;
    private final ScriptureService scriptureService;

    public SummonStep(XMLSlideShow ppt, String layout, ScriptureService scriptureService, String scriptureNumber) {
        super(ppt, layout);
        this.scriptureService = scriptureService;
        this.scriptureNumber = scriptureNumber;
    }

    @Override
    public void execute() throws WorshipStepException, PPTLayoutException {
        ScriptureNumberEntity scriptureNumberEntity;
        try {
            scriptureNumberEntity = ScriptureUtil.parseNumber(scriptureNumber);
        } catch (ScriptureNumberException e) {
            throw new WorshipStepException("宣召:" + "解析经文编号 [" + scriptureNumber + "] 时出错！", e);
        }
        boolean validateResult = scriptureService.validateNumber(scriptureNumberEntity);
        if (!validateResult) {
            throw new ScriptureServiceException("宣召:" + "解析经文编号 [" + scriptureNumber + "] 时出错！");
        }

        // 从配置获取需按照的形式
        ScriptureEntity scriptureEntity = scriptureService.getScriptureWithFormat(
                scriptureNumberEntity, SystemConfig.getString(Dict.ScriptureProperty.FORMAT5));

        XMLSlideShow ppt = getPpt();
        XSLFSlideLayout layout = ppt.findLayout(getLayout());
        XSLFSlide slide = ppt.createSlide(layout);

        // 宣召经文标题
        XSLFTextShape placeholder = TextUtil.getPlaceholderSafely(slide, 0, getLayout(), "标题部分");

        XSLFTextRun titleTextRun = TextUtil.clearAndCreateTextRun(placeholder);
        titleTextRun.setFontSize(AbstractWorshipStep.DEFAULT_TITLE_FONT_SIZE);
        titleTextRun.setText(scriptureNumber);
        titleTextRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
        TextUtil.setScriptureFontColor(titleTextRun, TextUtil.FontColor.RGB_FONT_COLOR_WHITE);

        // 经文部分
        double scriptureFontSize = SystemConfig.getUserConfigOrDefault(Dict.PPTProperty.SUMMON_SCRIPTURE_FONT_SIZE, AbstractWorshipStep.DEFAULT_SCRIPTURE_FONT_SIZE);

        placeholder = TextUtil.getPlaceholderSafely(slide, 1, getLayout(), "正文部分");
        placeholder.clearText();
        XSLFTextParagraph paragraph = placeholder.addNewTextParagraph();
        useCustomLanguage(paragraph);
        // 制表符
//        XSLFTextRun scriptureTextRun = paragraph.addNewTextRun();
//        scriptureTextRun.setText("\t");
        // 用换行符分段
        String[] scriptures = scriptureEntity.getScripture().split("\r\n");

        // 循环写入经文

        for (int i = 0; i < scriptures.length; i++) {

            // 经文
            XSLFTextRun scriptureTextRun = paragraph.addNewTextRun();
            scriptureTextRun.setText(scriptures[i]);
            // 字号颜色
            scriptureTextRun.setFontSize(scriptureFontSize);
            if (i % 2 == 0) {
                // 奇数段经文 用主领的黑色
                TextUtil.setScriptureFontColor(scriptureTextRun, TextUtil.FontColor.RGB_FONT_COLOR_BLACK);

            } else {
                // 偶数段经文 用会众的蓝色
                TextUtil.setScriptureFontColor(scriptureTextRun, TextUtil.FontColor.RGB_FONT_COLOR_BLUE);
            }
        }


        // 第二段
//        paragraph = placeholder.addNewTextParagraph();
//        scriptureTextRun = paragraph.addNewTextRun();
//        scriptureTextRun.setText("\t");
        // 回应 目前的宣召是启应形式
//        scriptureTextRun = paragraph.addNewTextRun();
//        scriptureTextRun.setText("我们当赞美耶和华！");
//        scriptureTextRun.setBold(true);
//        scriptureTextRun.setUnderlined(true);
//        scriptureTextRun.setFontSize(scriptureFontSize);
//        TextUtil.setScriptureFontColor(scriptureTextRun, TextUtil.FontColor.RGB_FONT_COLOR_BLUE);

        logger.info("宣召幻灯片制作完成");
    }
}
