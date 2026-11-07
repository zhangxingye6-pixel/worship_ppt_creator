package claygminx.worshipppt.util;

import claygminx.worshipppt.common.Dict;
import claygminx.worshipppt.exception.PPTLayoutException;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.sl.usermodel.Placeholder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 文本类工具
 * 为了逻辑清晰，各个部分的默认值存放在各自的常量中
 */
public class TextUtil {
    private final static Logger logger = LoggerFactory.getLogger(ScriptureUtil.class);

    /**
     * 字体颜色RGBs
     */
    public static class FontColor{
        public final static int[] RGB_FONT_COLOR_BLUE = {63, 114, 191};
        public final static int[] RGB_FONT_COLOR_RED = {245, 101, 81};
        public final static int[] RGB_FONT_COLOR_BLACK = {64, 64, 64};
        public final static int[] RGB_FONT_COLOR_WHITE = {255, 255, 255};

    }

    // 禁止外部实例化
    private TextUtil() {
    }

    /**
     * 设置文本段的经文字体颜色
     *
     * @param textRun 文本段对象
     * @param rgbs    RGB颜色数组
     * @return
     */
    public static void setScriptureFontColor(XSLFTextRun textRun, int[] rgbs) {
        // 参数校验
        if (textRun == null) {
            logger.info("未找到指定文本框，经文字体颜色设置失败");
            return;
        }
        textRun.setFontColor(new Color(rgbs[0], rgbs[1], rgbs[2]));
        logger.info("经文字体颜色设置成功");
    }

    /**
     * 清除占位符，并通过占位符创建单独的文字段
     *
     * @param placeholder
     * @return 创建的textRun对象
     */
    public static XSLFTextRun clearAndCreateTextRun(XSLFTextShape placeholder) {
        placeholder.clearText();
        return placeholder.addNewTextParagraph().addNewTextRun();
    }

    /**
     * 将厘米长度值转换为磅
     * @param centimetre 厘米长度
     * @return 磅
     */
    public static double convertToPoints(double centimetre) {
        double points = centimetre / 0.035275;
        if (logger.isDebugEnabled()) {
            logger.debug("{}cm => {} points", centimetre, points);
        }
        return points;
    }

    /**
     * 安全获取幻灯片指定索引的占位符，失败则抛自定义异常
     * @param slide 幻灯片对象
     * @param index 占位符索引
     * @param layout 母版布局名称（用于错误提示）
     * @param placeholderDesc 占位符描述（标题/正文）
     * @return 占位符对象
     * @throws PPTLayoutException 占位符获取失败时抛出
     */
    public static XSLFTextShape getPlaceholderSafely(XSLFSlide slide, int index, String layout, String placeholderDesc) throws PPTLayoutException {
        try {
            XSLFTextShape placeholder = slide.getPlaceholder(index);
            // 额外兜底：有些POI版本不会抛异常，只会返回null，需补充判断
            if (placeholder == null) {
                throw new PPTLayoutException("PPT母版-" + layout + "-" + placeholderDesc + "缺少必要的占位符（索引" + index + "），请添加后重新制作");
            }
            return placeholder;
        } catch (Exception e) {
            throw new PPTLayoutException("PPT母版-" + layout + "-" + placeholderDesc + "缺少必要的占位符（索引" + index + "），请添加后重新制作", e);
        }
    }

    /**
     * 获取布局中的唯一正文占位符。
     *
     * <p>正文占位符只接受 PowerPoint 的 BODY 或 CONTENT 类型。标题占位符、
     * 普通文本框和装饰文本框不会被当作正文目标，从而避免渲染内容覆盖母版标题。</p>
     *
     * @param slide 幻灯片对象
     * @param layout 母版布局名称
     * @param placeholderDesc 占位符描述，用于错误提示
     * @return 唯一正文占位符
     * @throws PPTLayoutException 未找到或找到多个正文占位符时抛出
     */
    public static XSLFTextShape getUniqueContentPlaceholder(XSLFSlide slide,
                                                              String layout,
                                                              String placeholderDesc)
            throws PPTLayoutException {
        if (slide == null) {
            throw new PPTLayoutException("PPT母版-" + layout + "-" + placeholderDesc + "无法读取幻灯片，请检查模板文件");
        }

        List<XSLFTextShape> contentPlaceholders = new ArrayList<>();
        for (XSLFTextShape placeholder : slide.getPlaceholders()) {
            if (placeholder == null) {
                continue;
            }
            Placeholder type = placeholder.getTextType();
            if (type == Placeholder.BODY || type == Placeholder.CONTENT) {
                contentPlaceholders.add(placeholder);
            }
        }

        if (contentPlaceholders.size() != 1) {
            throw new PPTLayoutException("PPT母版-" + layout + "-" + placeholderDesc
                    + "需要且只能有一个正文占位符（类型为BODY或CONTENT），当前找到"
                    + contentPlaceholders.size() + "个，请在母版中添加或调整后重新制作");
        }
        return contentPlaceholders.get(0);
    }

}
