package claygminx.worshipppt.util;

import claygminx.worshipppt.common.config.SystemConfig;
import claygminx.worshipppt.common.entity.ScriptureNumberEntity;
import claygminx.worshipppt.common.entity.ScriptureSectionEntity;
import claygminx.worshipppt.common.entity.confession.ConfessionContentEntity;
import claygminx.worshipppt.components.impl.AbstractWorshipStep;
import claygminx.worshipppt.exception.ScriptureNumberException;
import claygminx.worshipppt.common.Dict;
import claygminx.worshipppt.exception.WorshipStepException;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 经文小工具
 */
public class ScriptureUtil {

    private final static Logger logger = LoggerFactory.getLogger(ScriptureUtil.class);

    private ScriptureUtil() {
    }

    /**
     * 解析可能带有多卷的经文编号
     *
     * @param arg 经文编号
     * @return 经文编号实体列表
     * @throws IllegalArgumentException 若参数为空，抛出此异常
     * @throws ScriptureNumberException 若给定参数不符合经文编号格式，抛出此异常
     */
    public static List<ScriptureNumberEntity> parseNumbers(String arg) throws ScriptureNumberException {
        String[] scriptureNumberArray = arg.split(";");
        List<ScriptureNumberEntity> publicScriptureNumberList = new ArrayList<>(scriptureNumberArray.length);
        for (String scriptureNumberItem : scriptureNumberArray) {
            ScriptureNumberEntity scriptureNumberEntity = ScriptureUtil.parseNumber(scriptureNumberItem);
            publicScriptureNumberList.add(scriptureNumberEntity);
        }
        return publicScriptureNumberList;
    }

    /**
     * 解析经文编号
     *
     * <p>
     *     <ol>
     *         <li>有且仅有一个书卷，书卷名可以是全称，也可以是简称；</li>
     *         <li>书卷名称后面跟着章或节，可以只有章，但不可以只有节；</li>
     *         <li>如果后面仅跟着章，章和章用英文逗号隔开，若章和章是连续的，可以用英文短横线连接；</li>
     *         <li>章和节开始用英文冒号隔开；</li>
     *         <li>节和节的分隔符，跟章和章一样，用英文逗号连接连续节，或者用英文逗号分隔节和节。</li>
     *         <li>可以在章的中途开始查找，亦可以在章节的中途结束；否则出现连续的章节还需要打开圣经查一下前一章的最后一节是多少</li>
     *         <li>无论整章节在前或是在后，都使用“--”连接</li>
     *     </ol>
     * </p>
     *
     * <p>例如：</p>
     * <ol>
     *     <li>创世记1:1</li>
     *     <li>创1:1</li>
     *     <li>诗42-43</li>
     *     <li>诗42,43</li>
     *     <li>创1:1-5,7,9,11-15,2:1-3,5:1-5</li>
     *     <li>新增：创1:10->2:10</li>
     *     <li>新增：创1->2:10</li>
     *     <li>新增：创1:10->2</li>
     * </ol>
     *
     * @param arg 字符串形式的经文编号
     * @return 经文编号实体。注意，经文编号里的章和节是否存在，还没有经过验证。
     * @throws IllegalArgumentException 若参数为空，抛出此异常
     * @throws ScriptureNumberException 若给定参数不符合经文编号格式，抛出此异常
     */
    public static ScriptureNumberEntity parseNumber(String arg) throws ScriptureNumberException {
        // 参数合法性校验
        if (arg == null || arg.isEmpty()) {
            throw new IllegalArgumentException("参数不可为空！");
        }

        logger.debug("开始解析经文编号");
        char[] chars = arg.toCharArray();
        int sectionsStartIndex = -1;
        // 找到首个出现数字的位置
        // 应从第二个字符开始，因为通常而言，第一个字符是书卷名称的一部分
        for (int i = 1; i < chars.length; i++) {
            char c = chars[i];
            if (c >= '1' && c <= '9') {
                sectionsStartIndex = i;
                logger.debug("章的起始索引是" + sectionsStartIndex);
                break;
            }
        }
        if (sectionsStartIndex == -1) {
            throw new ScriptureNumberException("参数异常 - 没有输入章节！");
        }

        ScriptureNumberEntity result = new ScriptureNumberEntity(arg);
        // 确定书卷名称
        String bookName = arg.substring(0, sectionsStartIndex).trim();
        // 确定书卷章节
        String sections = arg.substring(sectionsStartIndex).trim();
        // 获取解析处理后的章节列表
        List<ScriptureSectionEntity> sectionList = parseSections(sections);

        result.setBookFullName(bookName);
        result.setBookShortName(bookName);
        result.setScriptureSections(sectionList);
        logger.debug("经文编号解析完成");

        return result;
    }

    /**
     * 解析章节
     * <p>规则：</p>
     * <ol>
     *     <li>章节的第一分隔符是逗号（,）；</li>
     *     <li>短横线（-）既可以连接章和章，也可以连接节和节，不可以连接章和节；</li>
     *     <li>冒号（:）仅能连接章和节，左边是章，右边是节。</li>
     * </ol>
     *
     * @param sections 字符串形式的章节
     * @return 章节列表
     * @throws ScriptureNumberException 若给定参数不符合经文编号格式，抛出此异常
     */
    public static List<ScriptureSectionEntity> parseSections(String sections) throws ScriptureNumberException {
        logger.debug("开始解析圣经章节");
        // 章节的第一分隔符是逗号
        String[] splitResult = sections.split(",");
        logger.debug("逗号分割后有{}个部分", splitResult.length);
        // 返回结果
        List<ScriptureSectionEntity> sectionList = new ArrayList<>(splitResult.length);
        // 当前解析类型，要么是chapter，要么是verse
        String currentType = "chapter";
        // 遍历分割结果
        for (String splitItem : splitResult) {
            // 处理新增的三种包含箭头->的类型
            if (splitItem.contains("->")) {
                parseSectionWithArrow(splitItem, sectionList);
            } else if (splitItem.contains(":")) {
                parseSectionWithColon(splitItem, sectionList);
                // 将标记改为verse, 因为输入的章节编号是人为有序的，在上一个条件处理完章号之后，后面的数字是节号
                currentType = "verse";
            } else if (splitItem.contains("-")) {
                parseSectionWithinDash(splitItem, currentType, sectionList);
            } else {
                // 如果标记是chapter，说明处理的是出1，2这样的章号；如果标记是verse，说明处理的是2，3，4这样的节号
                parseDigitSection(splitItem, currentType, sectionList);
            }
        }
        return sectionList;
    }


    /**
     * 解析存在箭头->的情况
     * 解析的形式：创1:10->4:10 创1->4:10 创1:10->4
     *
     * @param splitItem
     * @param sectionList
     */
    static void parseSectionWithArrow(String splitItem, List<ScriptureSectionEntity> sectionList) throws ScriptureNumberException {
        // 通过箭头拆分
        String[] split = splitItem.split("->");
        // 存在跨多个章节的情况, 定义章节首尾编号标记
        int startChapter;
        int endChapter;
        try {
            startChapter = Integer.parseInt(split[0].split(":")[0]);
            endChapter = Integer.parseInt(split[1].split(":")[0]);
        } catch (NumberFormatException e) {
            throw new ScriptureNumberException("章节编号格式可能有误，请重新检查！");
        }
        // 检查是否有跨章节
        if (startChapter == endChapter || startChapter > endChapter){
            throw new ScriptureNumberException("请检查章节编号是否正确，或者是否符合跨章分隔符'->'的使用条件");
        }

        // 检查章节列表是否为空，如果为空，则创建一个
        if (sectionList == null) {
            sectionList = new ArrayList<>(endChapter - startChapter + 1);
        }
        for (int i = startChapter; i <= endChapter; i++) {
            // 每次循环创建一个新的章节实体
            ScriptureSectionEntity scriptureSectionEntity = new ScriptureSectionEntity();
            List<Integer> verses = new ArrayList<>();
            scriptureSectionEntity.setChapter(i);
            // 如果有间隔的章节，节列表为null，表示全章；首尾需要特殊处理，使用枚举标记
            if (i == startChapter) {
                // 检查是否存在节号
                if (split[0].contains(":")) {
                    // 将节号加入节列表
                    verses.add(Integer.parseInt(split[0].split(":")[1]));
                    scriptureSectionEntity.setVerses(verses);
                    scriptureSectionEntity.setStatusToEndOfChapter();
                    // 本章本节的信息已经全部找到，添加到章节列表中
                }
            } else if (i != startChapter && i != endChapter) {
                // 是中间章节, 将节列表置空表示全章节
                scriptureSectionEntity.setVerses(null);
            } else if (i == endChapter) {
                if (split[1].contains(":")) {
                    verses.add(Integer.parseInt(split[1].split(":")[1]));
                    scriptureSectionEntity.setVerses(verses);
                    scriptureSectionEntity.setStatusFromStartOfChapter();
                }
            }
            sectionList.add(scriptureSectionEntity);
        }
    }


    /**
     * 精简经文
     *
     * @param scripture 未精简的经文
     * @return 精简后的经文
     */
    public static String simplifyScripture(String scripture) {
        // 通过key取出配置中的正则表达式，去除经文中的某些符号
        String pattern = SystemConfig.getString(Dict.ScriptureProperty.REGEX);
        return scripture.replaceAll(pattern, "");
    }

    /**
     * 解析由冒号（:）连接的章节
     *
     * @param sections    章节
     * @param sectionList 章节列表
     * @throws ScriptureNumberException 若章节格式不是“章:节”或"章:节-节"，则抛出此异常
     */
    private static void parseSectionWithColon(String sections, List<ScriptureSectionEntity> sectionList) throws ScriptureNumberException {
        String[] sectionArray = sections.split(":");
        if (sectionArray.length != 2) {
            throw new ScriptureNumberException("冒号（:）用法错误！");
        }

        // step1: 找到章编号
        int chapter;
        try {
            chapter = Integer.parseInt(sectionArray[0]);
        } catch (NumberFormatException e) {
            throw new ScriptureNumberException(sectionArray[0] + "不是数字！", e);
        }

        List<Integer> verses = new ArrayList<>();
        ScriptureSectionEntity entity = new ScriptureSectionEntity();
        // 将找到的章号添加到列表
        entity.setChapter(chapter);
        entity.setVerses(verses);
        // 将带有章号的章节添加到列表，对列表来说，是一个新元素
        sectionList.add(entity);

        // step2: 找到节编号
        // 因为输入是有顺序的，可以保证单独的节找到所在的章
        // 解析由短横线（-）连接的章节部分
        if (sectionArray[1].contains("-")) {
            parseSectionWithinDash(sectionArray[1], "verse", sectionList);
        } else {
            // 解析出单独的节号，直接添加到节列表
            parseDigitSection(sectionArray[1], "verse", sectionList);
        }
    }

    /**
     * 将章号或节号添加到列表
     *
     * @param digit       字符串形式的章号，或节号
     * @param type        chapter，或verse
     * @param sectionList 章节列表
     * @throws ScriptureNumberException 若{@code digit}不是整型数字，则抛出此异常
     */
    private static void parseDigitSection(String digit, String type, List<ScriptureSectionEntity> sectionList) throws ScriptureNumberException {
        try {
            addSection(Integer.parseInt(digit), type, sectionList);
        } catch (NumberFormatException e) {
            throw new ScriptureNumberException(digit + "不是数字！", e);
        }
    }

    /**
     * 解析由短横线（-）连接的章节部分
     * 要额外处理2种情况 章-章:节 章-节:章-节
     *
     * @param sections    章节
     * @param type        chapter，或verse
     * @param sectionList 章节列表
     * @throws ScriptureNumberException 若给定章节参数的格式不是“数字-数字”，则抛出此异常
     */
    private static void parseSectionWithinDash(String sections, String type, List<ScriptureSectionEntity> sectionList) throws ScriptureNumberException {
        String[] sectionArray = sections.split("-");
        if (sectionArray.length != 2) {
            throw new ScriptureNumberException("短横线（-）用法错误！");
        }

        // 短横线左右的数字表示的节的起始
        int start, end;
        try {
            start = Integer.parseInt(sectionArray[0]);
        } catch (NumberFormatException e) {
            throw new ScriptureNumberException(sectionArray[0] + "不是数字", e);
        }

        try {
            end = Integer.parseInt(sectionArray[1]);
        } catch (NumberFormatException e) {
            throw new ScriptureNumberException(sectionArray[1] + "不是数字", e);
        }

        for (int i = start; i <= end; i++) {
            addSection(i, type, sectionList);
        }
    }

    /**
     * 将章节添加到列表中
     *
     * @param n           章号，或节号
     * @param type        chapter，或verse
     * @param sectionList 章节列表
     */
    private static void addSection(int n, String type, List<ScriptureSectionEntity> sectionList) {
        if ("chapter".equals(type)) {
            ScriptureSectionEntity section = new ScriptureSectionEntity();
            section.setChapter(n);
            sectionList.add(section);
        } else {
            // 取出在parseSectionWithColon()中添加的最后一个章节实体，verse是最后一个实体中章所对应的节
            ScriptureSectionEntity section = sectionList.get(sectionList.size() - 1);
            List<Integer> verses;
            // 在章节实体中没有设置节，有则继续在末尾添加节数
            if (section.getVerses() == null) {
                verses = new ArrayList<>();
                section.setVerses(verses);
            } else {
                verses = section.getVerses();
            }
            verses.add(n);
        }
    }
}
