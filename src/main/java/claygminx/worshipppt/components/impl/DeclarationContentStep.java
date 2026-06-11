package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.Dict;
import claygminx.worshipppt.common.config.SystemConfig;
import claygminx.worshipppt.common.entity.DeclarationEntity;
import claygminx.worshipppt.common.entity.confession.ConfessionContentEntity;
import claygminx.worshipppt.common.entity.confession.ConfessionQueryRequestEntity;
import claygminx.worshipppt.common.entity.confession.ConfessionQueryResultEntity;
import claygminx.worshipppt.common.entity.confession.ConfessionVerseEntity;
import claygminx.worshipppt.components.ConfessionService;
import claygminx.worshipppt.exception.ConfessionServiceException;
import claygminx.worshipppt.exception.PPTLayoutException;
import claygminx.worshipppt.exception.ScriptureNumberException;
import claygminx.worshipppt.util.ConfessionUtil;
import claygminx.worshipppt.util.TextUtil;
import freemarker.template.TemplateException;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xslf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 宣信内容阶段 TODO可以使用上下文优化
 */
public class DeclarationContentStep extends AbstractWorshipStep {

    private final static Logger logger = LoggerFactory.getLogger(DeclarationContentStep.class);

    // 在OGNl上下文容器获取服务
    private final ConfessionService confessionService;
    private final DeclarationEntity declarationEntity;


    public DeclarationContentStep(XMLSlideShow ppt, String layout, ConfessionService confessionService, DeclarationEntity declarationEntity) {
        super(ppt, layout);
        this.confessionService = confessionService;
        this.declarationEntity = declarationEntity;
    }

    @Override
    public void execute() throws ScriptureNumberException, PPTLayoutException {
        logger.info("开始制作宣信内容幻灯片");
        switch (DeclarationTitleStep.DECLARATION_METHOD_NAME) {
            case "西敏信条" -> {
                // 解析西敏信条章节参数，获取信条节实体列表， 获取格式化的列表
                List<String> formatConfessionContent = null;
                List<ConfessionVerseEntity> confessionVerseEntities = null;
                try {
                    confessionVerseEntities = validateConfessionNumber(declarationEntity.getTitle());

                    formatConfessionContent = confessionService.getFormatConfessionContent(confessionVerseEntities, Dict.ScriptureProperty.CONFESSION_FORMART1);
                } catch (IOException | TemplateException e) {
                    throw new ConfessionServiceException("宣信：内容格式化失败");
                } catch (ScriptureNumberException e) {
                    throw new ScriptureNumberException(e.getMessage());
                }


                XMLSlideShow ppt = getPpt();
                XSLFSlideLayout layout = ppt.findLayout(getLayout());


                // 遍历列表， 每一节的内容制作一页幻灯片
                for (int i = 0; i < confessionVerseEntities.size(); i++) {
                    XSLFSlide slide = ppt.createSlide(layout);
                    // 制作标题部分

                    XSLFTextShape placeholder = TextUtil.getPlaceholderSafely(slide, 0, getLayout(), "标题部分");
                    XSLFTextRun titleTextRun = TextUtil.clearAndCreateTextRun(placeholder);
                    titleTextRun.setText("《" + confessionVerseEntities.get(i).getChapterName().trim() + "》");
                    titleTextRun.setFontSize(AbstractWorshipStep.DEFAULT_TITLE_FONT_SIZE);
                    titleTextRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
                    TextUtil.setScriptureFontColor(titleTextRun, TextUtil.FontColor.RGB_FONT_COLOR_WHITE);

                    // 制作正文部分
                    placeholder = TextUtil.getPlaceholderSafely(slide, 1, getLayout(), "正文部分");
                    // 删除占位符内的文字
                    placeholder.clearText();
                    // 创建文本段和文本段落填充正文部分
                    XSLFTextParagraph contentParagraph = placeholder.addNewTextParagraph();
                    useCustomLanguage(contentParagraph);
                    XSLFTextRun contentTextRun = contentParagraph.addNewTextRun();
                    contentTextRun.setText(formatConfessionContent.get(i));
                    contentTextRun.setFontSize(AbstractWorshipStep.DEFAULT_SCRIPTURE_FONT_SIZE);
                    contentTextRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
                    TextUtil.setScriptureFontColor(contentTextRun, TextUtil.FontColor.RGB_FONT_COLOR_BLACK);

                }


                logger.info("宣信内容幻灯片制作完成");

            }
            default -> throw new ScriptureNumberException("请输入有效的宣信主题(西敏信条/信条)");

        }
    }

    /**
     * 校验宣信面板的输入
     *
     * @param title
     * @return
     */
    private List<ConfessionVerseEntity> validateConfessionNumber(String title) throws ScriptureNumberException {
        if (StringUtils.isBlank(title) || StringUtils.isEmpty(title)) {
            throw new ScriptureNumberException("宣信内容不能为空，请重新输入后再尝试制作");
        }
        int firstNumber = ConfessionUtil.getIndexOfFirstNumber(title);
        // step3: 判断书名是否输入正确
        String themeName = title.substring(0, firstNumber);
        logger.info("宣信内容：输入的主题名称[{}]", themeName);
        if (!themeName.equals("西敏信条") && !themeName.equals("信条")) {
            // 无效的主题
            throw new ScriptureNumberException("请输入有效的宣信主题(西敏信条/信条)");
        }
        // 获取章节字符串
        String chapterNumber = title.substring(firstNumber);
        // step4: 解析章节编号
        List<ConfessionQueryRequestEntity> requestEntities = ConfessionUtil.parseChapterNumber(chapterNumber);
        // step5: 遍历处理请求体,获取内容实体的列表
        List<ConfessionContentEntity> contentEntities = new ArrayList<>();
        for (ConfessionQueryRequestEntity requestEntity : requestEntities) {
            ConfessionQueryResultEntity result = confessionService.query(requestEntity);
            List<ConfessionContentEntity> confessionContents = result.getConfessionContents();
            contentEntities.addAll(confessionContents);

        }
        // step6: 遍历处理内容实体列表，创建节实体列表
        List<ConfessionVerseEntity> verseEntities = new ArrayList<ConfessionVerseEntity>();
        for (ConfessionContentEntity contentEntity : contentEntities) {
            String chapterName = contentEntity.getChapterName();
            int chapter = contentEntity.getChapter();
            List<Integer> verses = contentEntity.getVerses();
            List<String> contents = contentEntity.getContents();
            for (int i = 0; i < verses.size(); i++) {
                ConfessionVerseEntity confessionVerseEntity = new ConfessionVerseEntity();
                confessionVerseEntity.setChapterName(chapterName);
                confessionVerseEntity.setChapter(chapter);
                confessionVerseEntity.setVerse(verses.get(i));
                confessionVerseEntity.setContent(contents.get(i));
                verseEntities.add(confessionVerseEntity);
            }
        }
        return verseEntities;


        // TODO 加入章节编号的校验


    }
}
