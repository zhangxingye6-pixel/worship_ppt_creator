package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.ScriptureStatusEnum;
import claygminx.worshipppt.common.config.FreeMarkerConfig;
import claygminx.worshipppt.common.config.SystemConfig;
import claygminx.worshipppt.common.entity.*;
import claygminx.worshipppt.components.ScriptureService;
import claygminx.worshipppt.exception.ScriptureNumberException;
import claygminx.worshipppt.exception.ScriptureServiceException;
import claygminx.worshipppt.exception.SystemException;
import claygminx.worshipppt.util.ScriptureUtil;
import claygminx.worshipppt.common.Dict;
import freemarker.template.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 经文服务实体
 */
public class ScriptureServiceImpl implements ScriptureService {

    static {
        // JDBC驱动会通过SPI自动注册
        String dbFilePath = SystemConfig.getString(Dict.DatabaseProperty.BIBLE_SQLITE_PATH);
        File dbFile = new File(dbFilePath);
        if (!dbFile.exists()) {
            throw new SystemException("数据库连接异常：" + dbFilePath + "未找到数据库文件");
        }
        // 格式化的数据库路径
        DB_URL = Dict.DatabaseProperty.JDBC_SQLITE_PREFIX + dbFile.getAbsolutePath();
    }

    private final static Logger logger = LoggerFactory.getLogger(ScriptureService.class);

    private final static String DB_URL;

    private static ScriptureService scriptureService;

    // 防止外部创建实例
    private ScriptureServiceImpl() {
    }

    /**
     * 获取经文服务实例对象
     *
     * @return 经文服务实例对象
     */
    public static ScriptureService getInstance() {
        if (scriptureService == null) {
            logger.debug("实例化经文服务");
            scriptureService = new ScriptureServiceImpl();
        }
        return scriptureService;
    }

    @Override
    public int getIdFromBookName(String fullName, String shortName) {
        try (Connection connection = getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement("SELECT BookId FROM BookNames WHERE FullName=? OR AbbrName=?");
            preparedStatement.setString(1, fullName);
            preparedStatement.setString(2, shortName);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                int bookId = resultSet.getInt(1);
                logger.debug("Query by {} and {}, BookId is {}", fullName, shortName, bookId);
                return bookId;
            }
        } catch (SQLException e) {
            throw new SystemException("数据库异常！", e);
        }
        return 0;
    }

    @Override
    public String[] getBookNameFromId(int bookId) {
        try (Connection connection = getConnection()) {
            PreparedStatement preparedStatement = connection.prepareStatement("SELECT FullName,AbbrName FROM BookNames WHERE BookId=?");
            preparedStatement.setInt(1, bookId);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                String[] result = new String[2];
                // 获取书卷全称和简称
                result[0] = resultSet.getString(1);
                result[1] = resultSet.getString(2);
                logger.debug("BookId={}, FullName is {}, ShortName is {}", bookId, result[0], result[1]);
                return result;
            }
        } catch (SQLException e) {
            throw new SystemException("数据库异常！", e);
        }
        return null;
    }

    @Override
    public boolean validateNumber(ScriptureNumberEntity scriptureNumberEntity) {
        String bookFullName = Optional.ofNullable(scriptureNumberEntity.getBookFullName()).orElse("");
        String bookShortName = Optional.ofNullable(scriptureNumberEntity.getBookShortName()).orElse("");
        int bookId = getIdFromBookName(bookFullName, bookShortName);
        if (bookId == 0) {
            logger.debug("{} or {} doesn't exist!", bookFullName, bookShortName);
            return false;
        }

        String[] bookNames = getBookNameFromId(bookId);
        scriptureNumberEntity.setBookId(bookId);
        scriptureNumberEntity.setBookFullName(bookNames[0]);
        scriptureNumberEntity.setBookShortName(bookNames[1]);
        return true;
    }

    @Override
    public ScriptureEntity getScriptureWithFormat(String scriptureNumber, String format) throws ScriptureNumberException {
        ScriptureNumberEntity scriptureNumberEntity = ScriptureUtil.parseNumber(scriptureNumber);
        boolean flag = validateNumber(scriptureNumberEntity);
        if (flag) {
            return getScriptureWithFormat(scriptureNumberEntity, format);
        } else {
            throw new ScriptureServiceException("经文编号格式错误！");
        }
    }

    @Override
    public ScriptureEntity getScriptureWithFormat(ScriptureNumberEntity scriptureNumber, String format) {
        // 检查书号，章节号合法性
        checkScriptureNumber(scriptureNumber);
        // 获取对应的章节列表
        List<ScriptureSectionEntity> scriptureSections = scriptureNumber.getScriptureSections();
        // 节列表
        List<ScriptureVerseEntity> scriptureVerseEntityList = new ArrayList<>();
        try (Connection connection = getConnection()) {
            // 通过章节列表获取节
            for (ScriptureSectionEntity scriptureSection : scriptureSections) {
                List<Integer> verses = scriptureSection.getVerses();
                if (verses == null || verses.isEmpty()) {
                    logger.debug("没有写节，那么直接按章来查询经文");
                    PreparedStatement preparedStatement = connection.prepareStatement("SELECT Verse,Scripture FROM Bible WHERE Book=? AND Chapter=? AND Scripture!='-' ORDER BY Id");
                    preparedStatement.setInt(1, scriptureNumber.getBookId());
                    preparedStatement.setInt(2, scriptureSection.getChapter());
                    ResultSet resultSet = preparedStatement.executeQuery();
                    while (resultSet.next()) {
                        String scripture = resultSet.getString(2);
                        scripture = ScriptureUtil.simplifyScripture(scripture);
                        ScriptureVerseEntity scriptureVerseEntity = new ScriptureVerseEntity();
                        scriptureVerseEntity.setBookId(scriptureNumber.getBookId());
                        scriptureVerseEntity.setChapter(scriptureSection.getChapter());
                        scriptureVerseEntity.setVerse(resultSet.getInt(1));
                        scriptureVerseEntity.setScripture(scripture);
                        scriptureVerseEntityList.add(scriptureVerseEntity);
                    }
                } else if (scriptureSection.getStatus() == ScriptureStatusEnum.NOMAL) {
                    logger.debug("普通节，直接按照编号查询经文");
                    // 执行一般流程，节编号所见即所得
                    for (int i = 0; i < verses.size(); i++) {
                        int verseNumber = verses.get(i);
                        PreparedStatement preparedStatement = connection.prepareStatement("SELECT Scripture FROM Bible WHERE Book=? AND Chapter=? AND Verse=? AND Scripture!='-'");
                        preparedStatement.setInt(1, scriptureNumber.getBookId());
                        preparedStatement.setInt(2, scriptureSection.getChapter());
                        preparedStatement.setInt(3, verseNumber);
                        ResultSet resultSet = preparedStatement.executeQuery();
                        if (resultSet.next()) {
                            String scripture = resultSet.getString(1);
                            scripture = ScriptureUtil.simplifyScripture(scripture);
                            ScriptureVerseEntity scriptureVerseEntity = new ScriptureVerseEntity();
                            scriptureVerseEntity.setBookId(scriptureNumber.getBookId());
                            scriptureVerseEntity.setChapter(scriptureSection.getChapter());
                            scriptureVerseEntity.setVerse(verseNumber);
                            scriptureVerseEntity.setScripture(scripture);
                            scriptureVerseEntityList.add(scriptureVerseEntity);
                        }
                    }
                } else if (scriptureSection.getStatus() == ScriptureStatusEnum.FROM_START_OF_CHAPTER) {
                    // 需要从节所在的章第一节开始，到节所对应的节结束
                    logger.debug("存在从头开始的标记，从章的最开始查询");

                    // 取得结束的节号
                    Integer endVerse = verses.get(0);
                    PreparedStatement preparedStatement = connection.prepareStatement("SELECT Scripture FROM Bible WHERE Book=? AND Chapter=? AND Verse<=? AND Scripture!='-'");
                    preparedStatement.setInt(1, scriptureNumber.getBookId());
                    preparedStatement.setInt(2, scriptureSection.getChapter());
                    preparedStatement.setInt(3, endVerse);
                    ResultSet resultSet = preparedStatement.executeQuery();
                    // 定义一个计数器，作为节编号
                    int verseNumber = 1;
                    while (resultSet.next()) {
                        String scripture = resultSet.getString(1);
                        // 简化经文
                        scripture = ScriptureUtil.simplifyScripture(scripture);
                        ScriptureVerseEntity scriptureVerseEntity = new ScriptureVerseEntity();
                        scriptureVerseEntity.setBookId(scriptureNumber.getBookId());
                        scriptureVerseEntity.setChapter(scriptureSection.getChapter());
                        scriptureVerseEntity.setVerse(verseNumber);
                        scriptureVerseEntity.setScripture(scripture);
                        scriptureVerseEntityList.add(scriptureVerseEntity);
                        // 下一节的编号（若存在）
                        verseNumber++;
                    }


                } else if (scriptureSection.getStatus() == ScriptureStatusEnum.TO_END_OF_CHAPTER) {
                    // 需要从节开始，到本章的结束
                    logger.debug("存在到章结束的标记，从对应的节开始查询，直到章的结尾");
                    // 取得开始的节编号
                    Integer startVerse = verses.get(0);

                    PreparedStatement preparedStatement = connection.prepareStatement("SELECT Scripture FROM Bible WHERE Book = ? AND Chapter = ? AND Verse >= ?");
                    preparedStatement.setInt(1, scriptureNumber.getBookId());
                    preparedStatement.setInt(2, scriptureSection.getChapter());
                    preparedStatement.setInt(3, startVerse);
                    ResultSet resultSet = preparedStatement.executeQuery();
                    int verseNumber = 1;
                    while (resultSet.next()) {
                        String verse = resultSet.getString(1);
                        ScriptureVerseEntity scriptureVerseEntity = new ScriptureVerseEntity();
                        scriptureVerseEntity.setVersionId(scriptureNumber.getBookId());
                        scriptureVerseEntity.setChapter(scriptureSection.getChapter());
                        scriptureVerseEntity.setVerse(verseNumber);
                        scriptureVerseEntity.setScripture(verse);
                        scriptureVerseEntityList.add(scriptureVerseEntity);
                        // 下一节的编号（若存在）
                        verseNumber++;
                    }
                }
            }

            if (scriptureVerseEntityList.isEmpty()) {
                logger.warn("查无经文！");
                return null;
            } else {
                ScriptureBookEntity scriptureBookEntity = new ScriptureBookEntity();
                scriptureBookEntity.setBookId(scriptureNumber.getBookId());
                scriptureBookEntity.setBookFullName(scriptureNumber.getBookFullName());
                scriptureBookEntity.setBookShortName(scriptureNumber.getBookShortName());
                scriptureBookEntity.setScriptureVerseList(scriptureVerseEntityList);

                // 格式化获取的经文
                // 获取FreeMarker核心配置
                Configuration configuration = FreeMarkerConfig.getConfiguration();
                // 通过格式的名称加载对应的FreeMarker模板
                Template template = configuration.getTemplate(format);
                try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    Writer out = new OutputStreamWriter(byteArrayOutputStream)) {

                    template.process(scriptureBookEntity, out);
                    byte[] bytes = byteArrayOutputStream.toByteArray();
                    String scripture = new String(bytes);

                    logger.debug("格式化之后的经文[{}]", scripture);

                    ScriptureEntity scriptureEntity = new ScriptureEntity();
                    scriptureEntity.setScriptureNumber(scriptureNumber);
                    scriptureEntity.setScripture(scripture);
                    return scriptureEntity;
                }
            }
        } catch (SQLException e) {
            logger.error("查询经文时发生异常！", e);
            throw new SystemException("查询经文时发生异常！");
        } catch (IOException | TemplateException e) {
            logger.error("格式化经文时发生异常！", e);
            throw new SystemException("格式化经文时发生异常!");
        }
    }

    /**
     * 检查必备参数
     *
     * @param scriptureNumber 经文编号
     */
    private void checkScriptureNumber(ScriptureNumberEntity scriptureNumber) {
        logger.debug("开始检查经文实体参数的有效性");
        if (scriptureNumber.getBookId() == null) {
            throw new ScriptureServiceException("未提供书卷序号！");
        }
        List<ScriptureSectionEntity> scriptureSections = scriptureNumber.getScriptureSections();
        if (scriptureSections == null || scriptureSections.isEmpty()) {
            throw new ScriptureServiceException("未提供章节！");
        }
        logger.debug("检查通过");
    }

    /**
     * 获取SQLite获取连接
     *
     * @return 数据库连接，记得要关闭该连接
     */
    private Connection getConnection() {
        try {
            return DriverManager.getConnection(DB_URL);
        } catch (SQLException e) {
            throw new SystemException("与圣经数据库获取连接失败", e);
        }
    }

}
