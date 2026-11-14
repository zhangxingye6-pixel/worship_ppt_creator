package claygminx.worshipppt.components;

import claygminx.worshipppt.common.entity.confession.ConfessionContentEntity;
import claygminx.worshipppt.common.entity.confession.ConfessionQueryRequestEntity;
import claygminx.worshipppt.common.entity.confession.ConfessionQueryResultEntity;
import claygminx.worshipppt.common.entity.confession.ConfessionVerseEntity;
import claygminx.worshipppt.exception.ScriptureNumberException;
import freemarker.template.TemplateException;

import java.io.IOException;
import java.util.List;

public interface ConfessionService {

    /**
     * 单段查询
     * @param chapter 章号
     * @param verse 节号
     * @return 一节信条内容
     */
    ConfessionContentEntity querySingle(int chapter, int verse);

    /**
     * 多段查询
     * @param startChapter 开始章号
     * @param endChapter 结束章号
     * @param startVerse 开始节号
     * @param endVerse 结束节号
     * @return 结果封装对象
     */
    ConfessionQueryResultEntity queryRange(int startChapter, int endChapter, int startVerse, int endVerse);

    /**
     *
     * @param requestEntity 查询封装对象
     * @return 结果封装对象
     */
    ConfessionQueryResultEntity query(ConfessionQueryRequestEntity requestEntity);

    /** 查询第一章中指定题号范围的大要理问答。 */
    ConfessionQueryResultEntity queryLargerCatechism(ConfessionQueryRequestEntity requestEntity)
            throws ScriptureNumberException;

    /** 查询第一章中指定题号范围的小要理问答。 */
    ConfessionQueryResultEntity queryShorterCatechism(ConfessionQueryRequestEntity requestEntity)
            throws ScriptureNumberException;

    /**
     * 获取格式化的String内容列表
     * @param orignList
     * @return
     */
    List<String> getFormatConfessionContent(List<ConfessionVerseEntity> orignList, String format) throws IOException, TemplateException;

    /**
     * 通过章编号获取对应的章名
     * @param chapter
     * @return
     */
    String getChapterNameWithChapter(int chapter);



}
