package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.Dict;
import claygminx.worshipppt.common.config.SystemConfig;
import claygminx.worshipppt.common.entity.PoetryEntity;
import claygminx.worshipppt.exception.PPTLayoutException;
import claygminx.worshipppt.exception.PoetrySourcesNotExistException;
import claygminx.worshipppt.exception.SystemException;
import claygminx.worshipppt.exception.WorshipStepException;
import claygminx.worshipppt.util.PoetryFileLocator;
import claygminx.worshipppt.util.TextUtil;
import claygminx.worshipppt.util.PictureUtil;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.xslf.usermodel.*;

import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 常规诗歌阶段
 */
public class RegularPoetryStep extends AbstractWorshipStep {

    /**
     * 页码文本框的参数
     */
    private static final double FIX_LEFT = 375.0;       // 距左侧固定距离
    private static final double FIX_TOP = 510.0;        // 距顶部固定距离（底部位置）
    private static final double FIX_WIDTH = 210.0;      // 固定宽度
    private static final double FIX_HEIGHT = 30.0;      // 固定高度
    private static final double PAGE_NUMBER_FONT_SIZE = 25.0;       // 页码字号

    private final static Logger logger = LoggerFactory.getLogger(RegularPoetryStep.class);

    private final List<PoetryEntity> poetryList;
    private final String albumName;
    private final int mode;
    private final List<PoetryEntity> fullPoetryList;

    public RegularPoetryStep(XMLSlideShow ppt, String layout, List<PoetryEntity> poetryList) {
        this(ppt, layout, poetryList, null, 0, null);
    }

    public RegularPoetryStep(XMLSlideShow ppt, String layout, List<PoetryEntity> poetryList,
                             String albumName, int mode, List<PoetryEntity> fullPoetryList) {
        super(ppt, layout);
        this.poetryList = poetryList;
        this.albumName = albumName;
        this.mode = mode;
        this.fullPoetryList = fullPoetryList;
    }

    /**
     * 增加检查ppt子目录
     *
     * @throws WorshipStepException
     * @throws PPTLayoutException
     * @throws PoetrySourcesNotExistException
     */
    @Override
    public void execute() throws WorshipStepException, PPTLayoutException, PoetrySourcesNotExistException {
        List<PoetryEntity> poetryList = getPoetryList();

        // 2-1模式：敬拜诗歌特殊处理（前两首连续，第三首替换原第二首）
        if ("敬拜诗歌".equals(albumName) && mode == 1 && fullPoetryList != null && fullPoetryList.size() >= 3) {
            int startIndex = fullPoetryList.indexOf(poetryList.get(0));
            if (startIndex == 0) {
                // 第一首歌谱步骤：处理第1首，再追加第2首的标题和歌谱
                for (PoetryEntity poetry : poetryList) {
                    renderPoetry(poetry);
                }
                // 第2首标题
                String secondPoetryName = fullPoetryList.get(1).getName();
                PoetryTitleStep titleStep = new PoetryTitleStep(getPpt(), "诗歌标题",
                        "以诗歌颂扬敬拜上帝", secondPoetryName);
                titleStep.execute();
                // 第2首歌谱
                renderPoetry(fullPoetryList.get(1));
                return;
            } else if (startIndex == 1) {
                // 原来的第2首歌谱步骤 → 改为处理第3首
                renderPoetry(fullPoetryList.get(2));
                return;
            }
        }

        // 原始逻辑
        for (PoetryEntity poetry : poetryList) {
            renderPoetry(poetry);
        }
        logger.info("诗歌幻灯片制作完成");
    }

    private void renderPoetry(PoetryEntity poetry) throws WorshipStepException, PPTLayoutException, PoetrySourcesNotExistException {
        File directory = poetry.getDirectory();
        try {
            checkDirectory(directory);
        } catch (IllegalArgumentException | FileNotFoundException e) {
            throw new WorshipStepException("检查文件夹：" + e.getMessage(), e);
        }
        File[] poetryFiles = PoetryFileLocator.getPoetryFiles(directory);

        // 超过一张图片，进行排序
        if (poetryFiles.length > 1) {
            sortFiles(poetryFiles);
        }

        // 下面开始一张张地制作幻灯片
        try {
            makeSlides(poetryFiles);
        } catch (SystemException e) {
            throw new WorshipStepException("系统异常：" + e.getMessage(), e);
        } catch (PPTLayoutException e) {
            throw new PPTLayoutException(e.getMessage(), e);
        } catch (Exception e) {
            throw new WorshipStepException("未知异常！", e);
        }
    }

    public String getFileExtensionName() {
        return SystemConfig.getString(Dict.PPTProperty.POETRY_EXTENSION);
    }

    public double getLeft() {
        return SystemConfig.getDouble(Dict.PPTProperty.POETRY_LEFT);
    }

    public double getTop() {
        return SystemConfig.getDouble(Dict.PPTProperty.POETRY_TOP);
    }

    public double getPictureLength() {
        return SystemConfig.getDouble(Dict.PPTProperty.POETRY_WIDTH);
    }

    public List<PoetryEntity> getPoetryList() {
        return poetryList;
    }

    /**
     * 制作幻灯片
     *
     * @param files 简谱图片
     * @throws IOException 添加图片到幻灯片时可能发生的异常
     */
    private void makeSlides(File[] files) throws IOException, PPTLayoutException {
        XSLFPictureData.PictureType pictureType = PictureUtil.getPictureType(getFileExtensionName());
        if (pictureType == null) {
            throw new SystemException("文件扩展名[" + getFileExtensionName() + "]错误！");
        }

        XMLSlideShow ppt = getPpt();
        XSLFSlideLayout layout = ppt.findLayout(getLayout());
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            XSLFSlide slide = ppt.createSlide(layout);
            XSLFPictureData picData = ppt.addPicture(file, pictureType);
            XSLFPictureShape picture = slide.createPicture(picData);
            resizePicture(picture);
//            Rectangle2D anchor = picture.getAnchor();
//            double top = anchor.getMaxY();
            setPageNumber(slide, files.length, i + 1);
        }
    }


    /**
     * 检查诗歌所在的文件夹
     */
    private void checkDirectory(File directory) throws IllegalArgumentException, FileNotFoundException {
        if (directory == null) {
            throw new IllegalArgumentException("未指定简谱所在的文件夹！");
        }
        if (!directory.exists()) {
            throw new FileNotFoundException("简谱文件夹[" + directory.getAbsolutePath() + "]不存在！");
        }
        if (!directory.isDirectory()) {
            throw new IllegalArgumentException("[" + directory.getAbsolutePath() + "]不是文件夹！");
        }
    }

    /**
     * 排序文件
     */
    private void sortFiles(File[] files) {
        Arrays.sort(files, (o1, o2) -> {
            int i1 = getFileIndex(o1.getName());
            int i2 = getFileIndex(o2.getName());
            return i1 - i2;
        });
    }

    /**
     * 获取文件序号
     * <p>文件名称的格式是：[诗歌名称]_Page[序号].png</p>
     *
     * @param filename 文件名称
     * @return 文件序号
     */
    private int getFileIndex(String filename) {
        String[] split = filename.split("_");
        if (split.length > 1) {
            String last = split[split.length - 1];
            split = last.split("[.]");
            String page = split[0];
            return Integer.parseInt(page.substring(4));
        }
        return 1;
    }

    /**
     * 调整图片尺寸
     * <p>预设图片宽度是24.3厘米，但是程序的长度单位是磅，转换公式是 1磅=0.035275</p>
     *
     * @param picture 简谱图片
     */
    private void resizePicture(XSLFPictureShape picture) {
        double width = TextUtil.convertToPoints(getPictureLength());
        double left = TextUtil.convertToPoints(getLeft());
        double top = TextUtil.convertToPoints(getTop());
        Rectangle2D anchor = picture.getAnchor();
        double ratio = anchor.getHeight() / anchor.getWidth();// 保持宽高比
        picture.setAnchor(new Rectangle2D.Double(left, top, width, width * ratio));
    }

    /**
     * 设置页码
     * 将占位符替换为文本框，不可被移动
     *
     * @param slide
     * @param totalCount
     * @param current
     */
    private void setPageNumber(XSLFSlide slide, int totalCount, int current) throws PPTLayoutException {
        XSLFTextShape placeholder = TextUtil.getPlaceholderSafely(slide, 0, getLayout(), "页码部分");

        // step1：删除原系统占位符
        slide.removeShape(placeholder);
        logger.info("敬拜诗歌占位符删除成功");
        // step2：新建普通文本框(文本框不会移位)
        XSLFTextBox fixedTextBox = slide.createTextBox();
        // step3：强制设置固定坐标和大小
        fixedTextBox.setAnchor(new Rectangle2D.Double(
                FIX_LEFT, FIX_TOP, FIX_WIDTH, FIX_HEIGHT
        ));
        // step4：添加文本段落并设置格式
        fixedTextBox.clearText();
        XSLFTextParagraph paragraph = fixedTextBox.addNewTextParagraph();
        XSLFTextRun textRun = paragraph.addNewTextRun();
        paragraph.setTextAlign(TextParagraph.TextAlign.CENTER);
        textRun.setText(current + "/" + totalCount);
        textRun.setFontSize(PAGE_NUMBER_FONT_SIZE);      // 字号
        textRun.setBold(true);          // 加粗
        TextUtil.setScriptureFontColor(textRun, TextUtil.FontColor.RGB_FONT_COLOR_BLACK);

    }

}
