package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.Dict;
import claygminx.worshipppt.common.config.FreeMarkerConfig;
import claygminx.worshipppt.common.config.SystemConfig;
import claygminx.worshipppt.common.entity.confession.ConfessionContentEntity;
import claygminx.worshipppt.common.entity.confession.ConfessionQueryRequestEntity;
import claygminx.worshipppt.common.entity.confession.ConfessionQueryResultEntity;
import claygminx.worshipppt.common.entity.confession.ConfessionVerseEntity;
import claygminx.worshipppt.components.ConfessionService;
import claygminx.worshipppt.exception.ScriptureNumberException;
import claygminx.worshipppt.exception.SystemException;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 *
 */
public class ConfessionServiceImpl implements ConfessionService {
    private final static Logger logger = LoggerFactory.getLogger(ConfessionServiceImpl.class);

    // 初始化
    static {
        String dbFilePath = SystemConfig.getString(Dict.DatabaseProperty.CONFESSION_SQLITE_PATH);
        File dbFile = new File(dbFilePath);
        if (!dbFile.exists()) {
            // 路径错误
            throw new SystemException("数据库连接异常：" + dbFilePath + "未找到数据库文件");
        }
        // 格式化数据路径
        DB_URL = Dict.DatabaseProperty.JDBC_SQLITE_PREFIX + dbFile.getAbsolutePath();

    }

    private final static String DB_URL;

    private static ConfessionService confessionService;

    /**
     * 禁止外部实例化
     */
    private ConfessionServiceImpl() {

    }

    /**
     * 获取单例服务对象
     */
    public static ConfessionService getInstance() {
        if (confessionService == null) {
            logger.debug("获取信条服务实例");
            confessionService = new ConfessionServiceImpl();
        }
        return confessionService;
    }

    /**
     * 返回单节文本的封装对象
     *
     * @param chapter 章号
     * @param verse   节号
     * @return
     */
    @Override
    public ConfessionContentEntity querySingle(int chapter, int verse) {
        // 获取数据库连接
        try (Connection connection = getConnection()) {
            return getConfessionContentEntity(chapter, verse, connection);

        } catch (SQLException e) {
            throw new RuntimeException("信条数据库连接异常", e);
        }
    }

    /**
     * 返回多节文本的封装对象
     *
     * @param startChapter 开始章号
     * @param endChapter   结束章号
     * @param startVerse   开始节号
     * @param endVerse     结束节号
     * @return
     */
    @Override
    public ConfessionQueryResultEntity queryRange(int startChapter, int endChapter, int startVerse, int endVerse) {
        try (Connection connection = getConnection()) {
            ConfessionQueryResultEntity resultEntity = new ConfessionQueryResultEntity();   // 封装返回对象
            logger.info("处理查询参数startChapter = {}, endChapter = {}, startVerse = {}, endVerse = {}", startChapter, endChapter, startVerse, endVerse);
            if (startChapter == endChapter && startVerse == endVerse) {
                /**
                 * situation1: 单独一节查询
                 */
                List<ConfessionContentEntity> contentEntities = new ArrayList<>();
                ConfessionContentEntity contentEntity = querySingle(startChapter, startVerse);
                contentEntities.add(contentEntity);
                resultEntity.setConfessionContents(contentEntities);
                logger.debug("Query {}, {}, {} and {}, contents: {}",
                        startChapter, startVerse, endChapter, endVerse, contentEntities);
                return resultEntity;

            } else if (startChapter == endChapter && startVerse == 0 && endChapter == 0) {
                /**
                 * situation2: 单独一章内容的查询
                 */
                List<ConfessionContentEntity> continuousChapters = getContinuousChapters(startChapter, endChapter, connection);
                resultEntity.setConfessionContents(continuousChapters);
                return resultEntity;

            } else if (startChapter < endChapter && startVerse == 0 && endVerse == 0) {
                /**
                 * situation3: 连续章查询
                 */
                // 连续多章查询
                List<ConfessionContentEntity> contentEntities = new ArrayList<>();
                contentEntities = getContinuousChapters(startChapter, endChapter, connection);
                resultEntity.setConfessionContents(contentEntities);
                return resultEntity;

            } else if (startChapter == endChapter && startVerse != endVerse) {
                /**
                 * situation4: 同一章连续节查询
                 */
                logger.info("宣信：查询进入situation4");
                // step1: 查询前面的文本
                PreparedStatement preparedStatement = connection.prepareStatement("SELECT Content FROM westminster_confession WHERE Chapter = ? AND Verse >= ? AND Verse <= ?");
                preparedStatement.setInt(1, startChapter);
                preparedStatement.setInt(2, startVerse);
                preparedStatement.setInt(3, endVerse);
                ResultSet resultSet = preparedStatement.executeQuery();
                List<ConfessionContentEntity> contentEntities = new ArrayList<>();
                List<Integer> versesList = new ArrayList<>();
                List<String> contentsList = new ArrayList<>();
                ConfessionContentEntity contentEntity = new ConfessionContentEntity();
                int verseIndex = startVerse;
                while (resultSet.next()){
                    String content = resultSet.getString(1);
                    contentsList.add(content);
                    versesList.add(verseIndex++);
                }
                String chapterName = queryChapterName(connection, startChapter);
                contentEntities.add(contentEntity);
                contentEntity.setChapterName(chapterName);
                contentEntity.setChapter(startChapter);
                contentEntity.setVerses(versesList);
                contentEntity.setContents(contentsList);
                resultEntity.setConfessionContents(contentEntities);
                logger.debug("Query by {}, {}, {} and {}, contents:{}",
                        startChapter, startVerse, endChapter, endVerse, contentEntities);
                return resultEntity;

            } else if (startChapter < endChapter && startVerse == 0 && endChapter != 0) {
                /**
                 * situation4: 从整章开始，到某章某节结束
                 */
                List<ConfessionContentEntity> contentEntities = new ArrayList<>();
                // step1: 先查询前面的整章
                List<ConfessionContentEntity> continuousChapters = getContinuousChapters(startChapter, endChapter - 1, connection);
                // step2: 获取后面的部分内容
                ConfessionContentEntity preContentEntity = getPreContentEntity(endChapter, endVerse, connection);

                contentEntities.add(preContentEntity);
                contentEntities.addAll(continuousChapters);
                resultEntity.setConfessionContents(contentEntities);
                return resultEntity;

            } else if (startChapter < endChapter && startVerse != 0 && endChapter == 0) {
                /**
                 * situation5: 从某章中途开始，到某章某节结束
                 */
                ArrayList<ConfessionContentEntity> contentEntities = new ArrayList<>();
                // step1: 先查询开始的部分
                ConfessionContentEntity postContentEntity = getPostContentEntity(startChapter, startVerse, connection);
                // step2: 查询后面的整章
                List<ConfessionContentEntity> continuousChapters = getContinuousChapters(startChapter + 1, endChapter, connection);
                contentEntities.add(postContentEntity);
                contentEntities.addAll(continuousChapters);
                resultEntity.setConfessionContents(contentEntities);
                return resultEntity;


            } else if (startChapter < endChapter && startChapter != 0 && endChapter != 0) {
                /**
                 * situation2: 有跨完整章的查询
                 */
                List<ConfessionContentEntity> contentEntities = new ArrayList<>();

                // step1: 先查询开始章节的内容
                ConfessionContentEntity preContentEntity = getPreContentEntity(startChapter, startVerse, connection);
                contentEntities.add(preContentEntity);

                // 如果中间有跨越的整章
                if (endChapter - startChapter > 1) {
                    // step2: 查询中间完整的章
                    List<ConfessionContentEntity> continuousChaptersEntities = getContinuousChapters(startChapter + 1, endChapter - 1, connection);
                    contentEntities.addAll(continuousChaptersEntities);
                }

                // step3: 查询后半部分
                ConfessionContentEntity postContentEntity = getPostContentEntity(endChapter, endVerse, connection);
                contentEntities.add(postContentEntity);

                // 返回封装的查询对象
                resultEntity.setConfessionContents(contentEntities);
                logger.debug("Query by {}, {}, {} and {}, contents:{}",
                        startChapter, startVerse, endVerse, endVerse, contentEntities);
                return resultEntity;


            } else {
                /**
                 * situation: 还没有处理这种情况
                 */
                throw new ScriptureNumberException("宣信：目前不支持这种输入，请联系开发者");

            }

        } catch (SQLException e) {
            throw new SystemException("信条查询失败", e);
        } catch (ScriptureNumberException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

    }

    /**
     * 连续几个章节的查询，整章内容查询
     *
     * @param startChapter
     * @param endChapter
     * @param connection
     * @return
     * @throws SQLException
     */
    public static List<ConfessionContentEntity> getContinuousChapters(int startChapter, int endChapter, Connection connection) throws SQLException {
        PreparedStatement statement;
        ResultSet resultSet;
        int verseIndex = -1;
        List<ConfessionContentEntity> entities = new ArrayList<>();
        for (int i = startChapter; i <= endChapter; i++) {
            verseIndex = 0;
            ConfessionContentEntity confessionContentEntity = new ConfessionContentEntity();
            List<Integer> versesList = new ArrayList<>();
            List<String> contentsList = new ArrayList<>();
            statement = connection.prepareStatement("SELECT Content FROM westminster_confession WHERE Chapter = ?");
            statement.setInt(1, i);
            resultSet = statement.executeQuery();
            while (resultSet.next()) {
                versesList.add(++verseIndex);
                contentsList.add(resultSet.getString(1));
            }
            String chapterName = queryChapterName(connection, i);
            confessionContentEntity.setChapterName(chapterName);
            confessionContentEntity.setChapter(i);
            confessionContentEntity.setVerses(versesList);
            confessionContentEntity.setContents(contentsList);
            entities.add(confessionContentEntity);
        }
        return entities;
    }

    /**
     * 通过章节号获取章节名
     *
     * @param connection
     * @param chapter
     * @return
     * @throws SQLException
     */
    public static String queryChapterName(Connection connection, int chapter) throws SQLException {
        PreparedStatement statement;
        ResultSet resultSet;
        String chapterName = null;
        statement = connection.prepareStatement("SELECT chapterName FROM westminster_confession_chapter_name WHERE Chapter = ?");
        statement.setInt(1, chapter);
        resultSet = statement.executeQuery();
        if (resultSet.next()) {
            chapterName = resultSet.getString(1);
        }
        return chapterName;
    }

    /**
     * 查询从startChapter的startVerse到本章末尾
     *
     * @param startChapter
     * @param startVerse
     * @param connection
     * @return
     * @throws SQLException
     */
    public static ConfessionContentEntity getPreContentEntity(int startChapter, int startVerse, Connection connection) throws SQLException {
        ResultSet resultSet;
        PreparedStatement statement;
        statement = connection.prepareStatement("SELECT Content FROM westminster_confession WHERE Chapter = ? AND Verse >= ?");
        statement.setInt(1, startChapter);
        statement.setInt(2, startVerse);
        resultSet = statement.executeQuery();
        int preVerseIndex = startVerse; // 统计节号
        List<Integer> preVersesList = new ArrayList<>();
        ArrayList<String> preContentsList = new ArrayList<>();
        ConfessionContentEntity preContentEntity = new ConfessionContentEntity();
        while (resultSet.next()) {
            preVersesList.add(preVerseIndex++);
            preContentsList.add(resultSet.getString(1));
        }
        String preChapterName = queryChapterName(connection, startChapter);
        preContentEntity.setChapterName(preChapterName);
        preContentEntity.setChapter(startChapter);
        preContentEntity.setVerses(preVersesList);
        preContentEntity.setContents(preContentsList);
        return preContentEntity;
    }


    /**
     * 查询endChapter的开始到endVerse
     *
     * @param endChapter
     * @param endVerse
     * @param connection
     * @return
     * @throws SQLException
     */
    public static ConfessionContentEntity getPostContentEntity(int endChapter, int endVerse, Connection connection) throws SQLException {
        PreparedStatement statement;
        ResultSet resultSet;
        statement = connection.prepareStatement("SELECT Content FROM westminster_confession WHERE Chapter = ? AND Verse <= ?");
        statement.setInt(1, endChapter);
        statement.setInt(2, endVerse);
        resultSet = statement.executeQuery();
        int postVerseIndex = 0;
        ConfessionContentEntity postContentEntity = new ConfessionContentEntity();
        List<String> postContentsList = new ArrayList<>();
        ArrayList<Integer> postVersesList = new ArrayList<>();
        while (resultSet.next()) {
            postVersesList.add(++postVerseIndex);
            postContentsList.add(resultSet.getString(1));
        }
        String postChapterName = queryChapterName(connection, endChapter);
        postContentEntity.setChapterName(postChapterName);
        postContentEntity.setChapter(endChapter);
        postContentEntity.setVerses(postVersesList);
        postContentEntity.setContents(postContentsList);
        return postContentEntity;
    }

    /**
     * 单个节查询
     *
     * @param chapter
     * @param verse
     * @param connection
     * @return
     * @throws SQLException
     */
    public static ConfessionContentEntity getConfessionContentEntity(int chapter, int verse, Connection connection) throws SQLException {
        // 单句查询语句构建
        PreparedStatement preparedStatement = connection.prepareStatement("SELECT Content FROM westminster_confession WHERE Chapter = ? AND Verse = ?");
        preparedStatement.setInt(1, chapter);
        preparedStatement.setInt(2, verse);
        ResultSet resultSet = preparedStatement.executeQuery();
        ConfessionContentEntity confessionContentEntity = new ConfessionContentEntity();
        // 节号
        List<Integer> verseList = new ArrayList<>();
        verseList.add(verse);
        // 信条内容
        List<String> contentsList = new ArrayList<>();
        if (resultSet.next()) {
            String string = resultSet.getString(1);
            logger.debug("Query by {} and {}, content is {}", chapter, verse, string);
            contentsList.add(string);
        }
        String chapterName = queryChapterName(connection, chapter);
        confessionContentEntity.setChapterName(chapterName);
        confessionContentEntity.setChapter(chapter);
        confessionContentEntity.setVerses(verseList);
        confessionContentEntity.setContents(contentsList);
        return confessionContentEntity;
    }

    /**
     * 以请求体查询 TODO没做校验
     *
     * @param requestEntity 查询封装对象
     * @return
     */
    @Override
    public ConfessionQueryResultEntity query(ConfessionQueryRequestEntity requestEntity) {
        int startChapter = requestEntity.getStartChapter();
        int startVerse = requestEntity.getStartVerse();
        int endChapter = requestEntity.getEndChapter();
        int endVerse = requestEntity.getEndVerse();
        return confessionService.queryRange(startChapter, endChapter, startVerse, endVerse);
    }

    /**
     * 将信条节列表格式化
     *
     * @param orignList
     * @param format
     * @return formatedList
     */
    @Override
    public List<String> getFormatConfessionContent(List<ConfessionVerseEntity> orignList, String format) throws IOException, TemplateException {
        // 获取freemarker配置
        Configuration freeMarkerConfig = FreeMarkerConfig.getConfiguration();
        String templatePath = SystemConfig.getString(Dict.ScriptureProperty.CONFESSION_FORMART1);
        Template template = freeMarkerConfig.getTemplate(templatePath);
        // 构建数据化模板的数据模型
        HashMap<String, Object> dataModel = new HashMap<>();
        dataModel.put("confessionVerseList", orignList);
        // 模板渲染，获取格式化的数据
        StringWriter stringWriter = new StringWriter();
        template.process(dataModel, stringWriter);
        String formatedString = stringWriter.toString();

        // 将格式化结果封装为String列表返回
        String[] split = formatedString.split("\n");
        ArrayList<String> formatedStringList = new ArrayList<>(split.length);
        for (String string : split) {
            formatedStringList.add(string);
        }
        return formatedStringList;
    }

    /**
     * 通过章编号获取章名称
     *
     * @param chapter
     * @return
     */
    @Override
    public String getChapterNameWithChapter(int chapter) {
        String chapterName = null;
        try (Connection connection = getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement("SELECT chapterName FROM westminster_confession_chapter_name WHERE chapter = ?");
            preparedStatement.setInt(1, chapter);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                chapterName = resultSet.getString(1);
            }
            logger.debug("Query by {}, chapterName is {}", chapter, chapterName);
            return chapterName;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取jdbc连接对象
     *
     * @return
     */
    private Connection getConnection() {
        try {
            return DriverManager.getConnection(DB_URL);
        } catch (SQLException e) {
            throw new SystemException("与信条数据库获取连接失败", e);
        }
    }
}

