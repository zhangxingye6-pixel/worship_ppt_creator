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

    /** PowerPoint 使用百分比表示段落行距，120 表示 1.2 倍行距。 */
    private static final double CATECHISM_LINE_SPACING = 120.0;
    /** 宣信正文统一使用 32 磅，避免长篇内容在页面中拥挤。 */
    private static final double DECLARATION_CONTENT_FONT_SIZE = 32.0;

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
        switch (declarationEntity.getTheme()) {
            case "西敏信条" -> {
                // 解析西敏信条章节参数，获取信条节实体列表， 获取格式化的列表
                List<String> formatConfessionContent = null;
                List<ConfessionVerseEntity> confessionVerseEntities = null;
                try {
                    confessionVerseEntities = validateConfessionNumber(declarationEntity.getTitle());

                    formatConfessionContent = confessionService.getFormatConfessionContent(confessionVerseEntities, Dict.ScriptureProperty.CONFESSION_FORMART1);
                } catch (IOException | TemplateException e) {
                    throw new ConfessionServiceException("宣信：内容格式化失败：" + e.getMessage(), e);
                } catch (ScriptureNumberException e) {
                    throw new ScriptureNumberException(e.getMessage(), e);
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
                    contentTextRun.setFontSize(DECLARATION_CONTENT_FONT_SIZE);
                    contentTextRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
                    TextUtil.setScriptureFontColor(contentTextRun, TextUtil.FontColor.RGB_FONT_COLOR_BLACK);

                }


                logger.info("宣信内容幻灯片制作完成");

            }
            case "西敏大要理问答", "西敏小要理问答" -> createCatechismContentSlides(declarationEntity.getTheme());
            // 使徒信经等固定宣信内容由 procedure 后续的 FixedStep 负责，当前步骤不生成内容页。
            case "使徒信经", "迦克墩信经", "尼西亚信经", "亚他那修信经" -> {
                logger.debug("宣信方式 [{}] 使用固定版式，跳过要理/信条内容页", declarationEntity.getTheme());
            }
            default -> throw new ScriptureNumberException("请输入有效的宣信方式");

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

    /** 按选中的要理数据库查询题号，并使用统一的宣信模板渲染内容页。 */
    private void createCatechismContentSlides(String catechismName)
            throws ScriptureNumberException, PPTLayoutException {
        List<ConfessionVerseEntity> confessionVerseEntities = validateCatechismNumber(
                declarationEntity.getTitle(), catechismName);
        XMLSlideShow ppt = getPpt();
        XSLFSlideLayout layout = ppt.findLayout(getLayout());
        for (int i = 0; i < confessionVerseEntities.size(); i++) {
            XSLFSlide slide = ppt.createSlide(layout);
            XSLFTextShape placeholder = TextUtil.getPlaceholderSafely(slide, 0, getLayout(), "标题部分");
            XSLFTextRun titleTextRun = TextUtil.clearAndCreateTextRun(placeholder);
            titleTextRun.setText("《" + confessionVerseEntities.get(i).getChapterName().trim() + "》");
            titleTextRun.setFontSize(AbstractWorshipStep.DEFAULT_TITLE_FONT_SIZE);
            titleTextRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
            TextUtil.setScriptureFontColor(titleTextRun, TextUtil.FontColor.RGB_FONT_COLOR_WHITE);

            placeholder = TextUtil.getPlaceholderSafely(slide, 1, getLayout(), "正文部分");
            placeholder.clearText();
            renderCatechismContent(placeholder, confessionVerseEntities.get(i).getContent());
        }
    }

    /**
     * 按数据库中的换行渲染问答内容。每一行使用独立段落，确保“问”和“答”
     * 在 PowerPoint 中保持明确的上下排列，而不是被当作普通空格显示。
     */
    private void renderCatechismContent(XSLFTextShape placeholder, String content) {
        String[] lines = content.replace("\r", "").split("\n", -1);
        for (String line : lines) {
            XSLFTextParagraph paragraph = placeholder.addNewTextParagraph();
            paragraph.setLineSpacing(CATECHISM_LINE_SPACING);
            useCustomLanguage(paragraph);
            XSLFTextRun contentRun = paragraph.addNewTextRun();
            contentRun.setText(line);
            contentRun.setFontSize(DECLARATION_CONTENT_FONT_SIZE);
            contentRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
            // 问题作为小标题突出显示，回答保持普通字重，便于在同一页快速区分问答。
            contentRun.setBold(line.trim().startsWith("问"));
            TextUtil.setScriptureFontColor(contentRun, TextUtil.FontColor.RGB_FONT_COLOR_BLACK);
        }
    }

    /** 解析用户输入的题号，并仅在内部补充数据库复用所需的第1章编号。 */
    private List<ConfessionVerseEntity> validateCatechismNumber(String title, String catechismName)
            throws ScriptureNumberException {
        if (StringUtils.isBlank(title)) {
            throw new ScriptureNumberException(catechismName + "内容不能为空，请填写题号范围");
        }
        String questionNumbers = title.trim();
        if (!questionNumbers.matches("[0-9\\s,，、-]+")) {
            throw new ScriptureNumberException(catechismName + "只需输入题号，例如‘1-3’或‘1,3’");
        }
        String chapterAndQuestionNumbers = "1:" + questionNumbers.replace('，', ',').replace('、', ',');
        List<ConfessionQueryRequestEntity> requests = ConfessionUtil.parseChapterNumber(chapterAndQuestionNumbers);
        List<ConfessionVerseEntity> verses = new ArrayList<>();
        for (ConfessionQueryRequestEntity request : requests) {
            ConfessionQueryResultEntity result = "西敏小要理问答".equals(catechismName)
                    ? confessionService.queryShorterCatechism(request)
                    : confessionService.queryLargerCatechism(request);
            for (ConfessionContentEntity content : result.getConfessionContents()) {
                for (int i = 0; i < content.getVerses().size(); i++) {
                    ConfessionVerseEntity verse = new ConfessionVerseEntity();
                    verse.setChapterName(content.getChapterName());
                    verse.setChapter(content.getChapter());
                    verse.setVerse(content.getVerses().get(i));
                    verse.setContent(content.getContents().get(i));
                    verses.add(verse);
                }
            }
        }
        if (verses.isEmpty()) {
            throw new ScriptureNumberException("没有找到对应的" + catechismName + "题目，请检查题号");
        }
        return verses;
    }
}
