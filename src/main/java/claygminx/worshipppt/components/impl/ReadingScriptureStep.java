package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.config.SystemConfig;
import claygminx.worshipppt.common.entity.ScriptureEntity;
import claygminx.worshipppt.common.entity.ScriptureBookEntity;
import claygminx.worshipppt.common.entity.ScriptureVerseEntity;
import claygminx.worshipppt.common.config.FreeMarkerConfig;
import claygminx.worshipppt.components.ScriptureService;
import claygminx.worshipppt.exception.PPTLayoutException;
import claygminx.worshipppt.exception.ScriptureNumberException;
import claygminx.worshipppt.exception.WorshipStepException;
import claygminx.worshipppt.common.Dict;
import claygminx.worshipppt.util.TextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.xslf.usermodel.*;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 读经阶段
 */
public class ReadingScriptureStep extends AbstractWorshipStep {

    private final static Logger logger = LoggerFactory.getLogger(ReadingScriptureStep.class);
    // 动态控制经文行数时使用
//    private final static int BEST_LINE_COUNT = 4;
//    private final static int BEST_HEIGHT = 207;
//    private final static int MAX_CHAR_COUNT = 32;\
    private final static int BEST_VERSE_NUMBER = 2;
    private final static Pattern SCRIPTURE_REFERENCE_PREFIX = Pattern.compile("^【[^】]+】");
    private final static Pattern SCRIPTURE_REFERENCE = Pattern.compile("^【(.+?)(\\d+):(\\d+)】(.*)$");

    private final ScriptureService scriptureService;
    private final List<String> scriptureNumbers;

    public ReadingScriptureStep(XMLSlideShow ppt, String layout, ScriptureService scriptureService, String scriptureNumber) {
        this(ppt, layout, scriptureService, Collections.singletonList(scriptureNumber));
    }

    public ReadingScriptureStep(XMLSlideShow ppt, String layout, ScriptureService scriptureService, List<String> scriptureNumbers) {
        super(ppt, layout);
        this.scriptureService = scriptureService;
        this.scriptureNumbers = scriptureNumbers == null ? Collections.emptyList() : scriptureNumbers;
    }

    /**
     * 解析读经经文，并制作读经幻灯片
     *
     * @throws WorshipStepException
     */
    @Override
    public void execute() throws WorshipStepException, PPTLayoutException {
        if (scriptureNumbers.isEmpty()) {
            return;
        }
        logger.info("开始读经，共{}个经文章节", scriptureNumbers.size());

        XMLSlideShow ppt = getPpt();
        XSLFSlideLayout layout = ppt.findLayout(getLayout());


        // 按预设的读经模板，每一行最多32个中文字符，每一页行数最佳是4行，尽量控制最多6行
        // 根据当前的需要，为了使字号最大化，读经部分的幻灯片每页只保留两节经文
        XSLFSlide slide = null;
        XSLFTextShape placeholder = null;
//        int lineCount = 0;      // 行计数器 动态控制时使用
        int slideCount = 0;     // 页计数器 动态控制时使用
        int verseIndex = 0;
        double scriptureFontSize = SystemConfig.getUserConfigOrDefault(Dict.PPTProperty.READING_SCRIPTURE_FONT_SIZE, DEFAULT_SCRIPTURE_FONT_SIZE);

        List<String> validScriptureNumbers = new ArrayList<>();
        for (String scriptureNumber : scriptureNumbers) {
            if (scriptureNumber != null && !scriptureNumber.trim().isEmpty()) {
                validScriptureNumbers.add(scriptureNumber.trim());
            }
        }
        if (validScriptureNumbers.isEmpty()) {
            return;
        }
        // 所有读经章节共用同一个标题占位符，使用分号区分不同输入项。
        String readingTitle = String.join(";", validScriptureNumbers);
        // 先将所有输入章节解析成一个连续经文列表，再统一处理角色标记。
        // 不能逐段套用模板，否则每段都会从“主领”重新开始奇偶计数。
        List<String> scriptureItems = new ArrayList<>();
        for (String scriptureNumber : validScriptureNumbers) {
            ScriptureEntity scriptureEntity;
            try {
                // 使用带章节标识的格式取得每一节，再去除标识，保留完整经文顺序。
                scriptureEntity = scriptureService.getScriptureWithFormat(
                        scriptureNumber,
                        SystemConfig.getString(Dict.ScriptureProperty.FORMAT3));
            } catch (ScriptureNumberException e) {
                throw new WorshipStepException("解析经文编号 [" + scriptureNumber + "] 时出错！", e);
            }
            if (scriptureEntity == null) {
                continue;
            }
            String[] scriptureArray = scriptureEntity.getScripture().replaceAll("\r", "").split("\n");
            logger.info("章节 [{}] 共{}段经文", scriptureNumber, scriptureArray.length);
            for (String scriptureItem : scriptureArray) {
                if (!scriptureItem.trim().isEmpty()) {
                    scriptureItems.add(scriptureItem.trim());
                }
            }
        }

        if (scriptureItems.isEmpty()) {
            return;
        }

        // 将完整经文列表一次性交给 scripture-format6.ftl，避免各段模板重新计算奇偶顺序。
        String formattedScripture = formatCombinedScripture(scriptureItems);
        String[] formattedScriptureArray = formattedScripture.replace("\r", "").split("\n");
        for (int itemIndex = 0; itemIndex < formattedScriptureArray.length; itemIndex++) {
                verseIndex++;
                String scriptureItem = formattedScriptureArray[itemIndex];
                // 创建新一页；多个输入框解析后的经文会按列表顺序继续追加。
                if ((verseIndex - 1) % BEST_VERSE_NUMBER == 0) {
                    slideCount++;
                    slide = ppt.createSlide(layout);
                    placeholder = TextUtil.getPlaceholderSafely(slide, 0, getLayout(), "标题部分");
                    XSLFTextRun textRun = TextUtil.clearAndCreateTextRun(placeholder);
                    textRun.setFontSize(AbstractWorshipStep.DEFAULT_TITLE_FONT_SIZE);
                    textRun.setFontFamily(AbstractWorshipStep.DEFAULT_FONT_FAMILY);
                    TextUtil.setScriptureFontColor(textRun, TextUtil.FontColor.RGB_FONT_COLOR_WHITE);
                    textRun.setText(readingTitle);

                    // 获取并清空占位符1中的默认文字，然后继续追加当前章节内容。
                    placeholder = TextUtil.getPlaceholderSafely(slide, 1, getLayout(), "正文部分");
                    placeholder.clearText();
                    logger.info("开始第{}张读经幻灯片...", slideCount);
                }

                // 往正文添加一段经文，注意，可能因为经文字数较多，一行容不下。
                placeholder = TextUtil.getPlaceholderSafely(slide, 1, getLayout(), "正文部分");
                XSLFTextParagraph paragraph = placeholder.addNewTextParagraph();
                useCustomLanguage(paragraph);
                XSLFTextRun textRun = paragraph.addNewTextRun();
                String trimScriptureItem = scriptureItem.trim();
                textRun.setText(trimScriptureItem);
                textRun.setFontSize(scriptureFontSize);


                // format6 已经写入章节标识和角色前缀，这里只负责按角色设置 PPT 字体颜色。
                if (trimScriptureItem.startsWith("会众：")) {
                    TextUtil.setScriptureFontColor(textRun, TextUtil.FontColor.RGB_FONT_COLOR_BLUE);
                } else if (trimScriptureItem.startsWith("主领：")) {
                    TextUtil.setScriptureFontColor(textRun, TextUtil.FontColor.RGB_FONT_COLOR_BLACK);
                } else {
                    TextUtil.setScriptureFontColor(textRun, TextUtil.FontColor.RGB_FONT_COLOR_RED);
                }
        }
    }

    /**
     * 将多个输入章节合并为一个经文清单，并统一使用 scripture-format6.ftl 渲染。
     *
     * @param scriptureItems 按输入顺序合并的经节文本
     * @return 带主领、会众、合读前缀的完整经文
     */
    private String formatCombinedScripture(List<String> scriptureItems) throws WorshipStepException {
        ScriptureBookEntity bookEntity = new ScriptureBookEntity();
        List<ScriptureVerseEntity> verseList = new ArrayList<>();
        for (String scriptureItem : scriptureItems) {
            java.util.regex.Matcher matcher = SCRIPTURE_REFERENCE.matcher(scriptureItem.trim());
            if (!matcher.matches()) {
                continue;
            }
            ScriptureVerseEntity verseEntity = new ScriptureVerseEntity();
            verseEntity.setBookShortName(matcher.group(1));
            verseEntity.setChapter(Integer.valueOf(matcher.group(2)));
            verseEntity.setVerse(Integer.valueOf(matcher.group(3)));
            verseEntity.setScripture(matcher.group(4));
            verseList.add(verseEntity);
        }
        bookEntity.setScriptureVerseList(verseList);

        try (StringWriter writer = new StringWriter()) {
            Template template = FreeMarkerConfig.getConfiguration().getTemplate(
                    SystemConfig.getString(Dict.ScriptureProperty.FORMAT6));
            template.process(bookEntity, writer);
            return writer.toString();
        } catch (IOException | TemplateException e) {
            throw new WorshipStepException("合并读经经文并使用 scripture-format6.ftl 渲染时出错！", e);
        }
    }
}
