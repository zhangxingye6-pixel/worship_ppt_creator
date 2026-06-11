package claygminx.worshipppt.util;

import claygminx.worshipppt.common.entity.confession.ConfessionQueryRequestEntity;
import claygminx.worshipppt.exception.ScriptureNumberException;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;


public class ConfessionUtil {

    private final static Logger logger = LoggerFactory.getLogger(ConfessionUtil.class);

    /**
     * 解析章节
     * <p>规则：</p>
     * <ol>
     *     <li>章节的第一分隔符是逗号（,）；</li>
     *     <li>短横线（-）既可以连接章和章，也可以连接节和节，不可以连接章和节；</li>
     *     <li>冒号（:）仅能连接章和节，左边是章，右边是节。</li>
     *     <li>出1:1-2, 3, 4, 5:4, 6-7</li>
     *     <li>跨章节的情况，如出1:20-2:10，使用->连接，如出1:20->2:10 出1->2:10 出1:10->2</li>
     * </ol>
     *  此方法适用于西敏信条
     *
     * @param chapterNumber 字符串形式的章节
     * @return 章节列表
     * @throws ScriptureNumberException 若给定参数不符合经文编号格式，抛出此异常
     */
    public static List<ConfessionQueryRequestEntity> parseChapterNumber(String chapterNumber) throws ScriptureNumberException {
        logger.info("开始解析西敏信条章节");
        // 章节的第一分隔符是逗号
        String[] splitResult = chapterNumber.split(",");
        logger.debug("逗号分割后有{}个部分", splitResult.length);
        // 当前解析类型，要么是chapter，要么是verse
        String currentType = "chapter";
        List<ConfessionQueryRequestEntity> requestEntities = new ArrayList<>();
        // 遍历分割结果
        for (String splitItem : splitResult) {
            // 处理包含箭头->的类型
            if (splitItem.contains("->")) {
                requestEntities.addAll(parseChapterNumberWithArrow(splitItem));
            } else if (splitItem.contains(":")) {
                requestEntities.addAll(parseChapterNumberWithColon(splitItem));
                // 如出1:1-2, 3, 4, 5:4, 6-7, 会发现6-7无论输入者想表示章或者节都合理，但是在程序中无法识别。所以干脆都按照节来处理，一旦处理过":"，说明后边不在表示章
                currentType = "verse";
            } else if (splitItem.contains("-")) {
                requestEntities.addAll(parseChapterNumberWithinDash(splitItem, currentType, requestEntities));
            } else {
                // 如果标记是chapter，说明处理的是出1，2这样的章号；如果标记是verse，说明处理的是2，3，4这样的节号
                requestEntities.addAll(parseDigitChapterNumber(splitItem, currentType, requestEntities));
            }
        }
        return requestEntities;
    }

    /**
     * 处理3，4，5这种格式的章节编号，当没有进入处理":"的方法时按照章处理，否则按照节处理
     *
     * @param splitItem
     * @param currentType
     * @param requestEntities
     * @return
     */
    private static List<ConfessionQueryRequestEntity> parseDigitChapterNumber(String splitItem, String currentType, List<ConfessionQueryRequestEntity> requestEntities) {
        List<ConfessionQueryRequestEntity> confessionQueryRequestEntities = new ArrayList<>(1);
        ConfessionQueryRequestEntity requestEntity = new ConfessionQueryRequestEntity();
        // 判断是章还是节
        switch (currentType) {
            case "chapter" -> {
                // 按照整章处理
                int startChapter = Integer.parseInt(splitItem);
                requestEntity.setStartChapter(startChapter);
                requestEntity.setEndChapter(startChapter);
                confessionQueryRequestEntities.add(requestEntity);
                return confessionQueryRequestEntities;
            }
            case "verse" -> {
                // 按照节处理
                // step1: 找到所属的章
                if (CollectionUtils.isNotEmpty(requestEntities)) {
                    ConfessionQueryRequestEntity lastRequestEntity = requestEntities.get(requestEntities.size() - 1);
                    int currentChapter = lastRequestEntity.getEndChapter();
                    int verse = Integer.parseInt(splitItem);
                    requestEntity.setStartChapter(currentChapter);
                    requestEntity.setEndChapter(currentChapter);
                    requestEntity.setStartVerse(verse);
                    requestEntity.setEndVerse(verse);
                    confessionQueryRequestEntities.add(requestEntity);
                    return confessionQueryRequestEntities;
                } else {
                    logger.info("宣信：章节编号解析错误, 请检查输入");
                    return confessionQueryRequestEntities;
                }
            }
            default -> {
                logger.info("宣信：章节编号解析，按照常理来说不会进到这里");
                return confessionQueryRequestEntities;
            }
        }

    }

    /**
     * 处理3-4这种格式的章节编号，当没有进入处理":"的方法时按照章处理，否则按照节处理
     *
     * @param splitItem
     * @param currentType
     * @param requestEntities
     * @return
     */
    private static List<ConfessionQueryRequestEntity> parseChapterNumberWithinDash(String splitItem, String currentType, List<ConfessionQueryRequestEntity> requestEntities) {
        ArrayList<ConfessionQueryRequestEntity> confessionQueryRequestEntities = new ArrayList<>(1);
        ConfessionQueryRequestEntity requestEntity = new ConfessionQueryRequestEntity();
        // 判断是章还是节
        switch (currentType) {
            // 处理连续章
            case "chapter" -> {
                // 获取startChapter和endChapter
                int startChapter = Integer.parseInt(splitItem.split("-")[0]);
                int endChapter = Integer.parseInt(splitItem.split("-")[1]);
                requestEntity.setStartChapter(startChapter);
                requestEntity.setEndChapter(endChapter);
                confessionQueryRequestEntities.add(requestEntity);
                return confessionQueryRequestEntities;
            }
            case "verse" -> {
                // 处理连续节
                // step1: 先找到是哪一章
                if (CollectionUtils.isNotEmpty(confessionQueryRequestEntities)) {
                    ConfessionQueryRequestEntity lastRequestEntity = confessionQueryRequestEntities.get(confessionQueryRequestEntities.size() - 1);
                    int currentChapter = lastRequestEntity.getEndChapter();
                    int startVerse = Integer.parseInt(splitItem.split("-")[0]);
                    int endVerse = Integer.parseInt(splitItem.split("-")[1]);
                    requestEntity.setStartChapter(currentChapter);
                    requestEntity.setEndChapter(currentChapter);
                    requestEntity.setStartVerse(startVerse);
                    requestEntity.setEndVerse(endVerse);
                    confessionQueryRequestEntities.add(requestEntity);
                    return confessionQueryRequestEntities;
                } else {
                    logger.info("宣信：章节编号解析错误, 请检查输入");
                    return confessionQueryRequestEntities;
                }

            }
            default -> {
                logger.info("宣信：章节编号解析，按照常理来说不会进到这里");
                return confessionQueryRequestEntities;
            }
        }
    }

    /**
     * 字串中存在":"和"-"， 如信条3:2-3 信条2:3
     *
     * @param splitItem
     * @return
     */
    private static List<ConfessionQueryRequestEntity> parseChapterNumberWithColon(String splitItem) {
        logger.info("开始处理章节编号：" + splitItem);
        // step1: 通过":"拆分，可以获取到startChapter
        String[] split = splitItem.split(":");
        int startChapter = Integer.parseInt(split[0]);
        int startVerse = 0;
        int endVerse = 0;

        if (split[1].contains("-")) {
            // step2: 通过"-"拆分，可以获取到startVerse和endVerse
            String[] verses = split[1].split("-");
            startVerse = Integer.parseInt(verses[0]);
            endVerse = Integer.parseInt(verses[1]);
        }else{
            startVerse = Integer.parseInt(split[1]);
            endVerse = Integer.parseInt(split[1]);
        }

        ConfessionQueryRequestEntity requestEntity = new ConfessionQueryRequestEntity();
        requestEntity.setStartChapter(startChapter);
        requestEntity.setEndChapter(startChapter);
        requestEntity.setStartVerse(startVerse);
        requestEntity.setEndVerse(endVerse);

        ArrayList<ConfessionQueryRequestEntity> requestEntities = new ArrayList<>(1);
        requestEntities.add(requestEntity);
        return requestEntities;
    }

    /**
     * 字串中有->的处理
     *
     * @param splitItem
     * @return
     * @throws ScriptureNumberException
     */
    private static List<ConfessionQueryRequestEntity> parseChapterNumberWithArrow(String splitItem) throws ScriptureNumberException {
        ConfessionQueryRequestEntity confessionQueryRequestEntity = new ConfessionQueryRequestEntity();
        // 通过箭头拆分
        String[] split = splitItem.split("->");
        try {

            // 获取startChapter startVerse
            if (split[0].contains(":")) {
                // 获取startVerse
                String startVerse = split[0].split(":")[1];
                confessionQueryRequestEntity.setStartVerse(Integer.parseInt(startVerse));
            } else {
                // 不存在startVerse
                confessionQueryRequestEntity.setStartChapter(Integer.parseInt(split[0]));
                confessionQueryRequestEntity.setStartVerse(0);
            }

            // 获取endChapter endVerse
            if (split[1].contains(":")) {
                // 获取endVerse
                String endVerse = split[1].split(":")[1];
                confessionQueryRequestEntity.setEndVerse(Integer.parseInt(endVerse));
            } else {
                // 不存在endVerse
                confessionQueryRequestEntity.setEndChapter(Integer.parseInt(split[1]));
                confessionQueryRequestEntity.setEndVerse(0);
            }
            List<ConfessionQueryRequestEntity> requestEntities = new ArrayList<>(1);
            requestEntities.add(confessionQueryRequestEntity);
            return requestEntities;

        } catch (NumberFormatException e) {
            throw new ScriptureNumberException("宣信：->的使用有误，请重新检查");
        }
    }

    /**
     * 在章节编号中获取首个数字的下标
     * @param title
     * @return
     */
    public static int getIndexOfFirstNumber(String title) throws ScriptureNumberException {
        // step1: 查找第一个数字
        int firstNumber = -1;
        char[] charValue = title.toCharArray();
        for (int i = 0; i < charValue.length; i++) {
            if (charValue[i] >= '0' && charValue[i] <= '9') {
                firstNumber = i;
                break;
            }
        }
        // step2: 判断是否有数字
        if (firstNumber == -1) {
            throw new ScriptureNumberException("请输入宣信内容的章节编号");
        }
        return firstNumber;
    }


    /**
     * 禁止外部实例化
     */
    private ConfessionUtil() {

    }

}
