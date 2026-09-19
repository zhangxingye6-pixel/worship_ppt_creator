package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.config.SystemConfig;
import claygminx.worshipppt.components.*;
import claygminx.worshipppt.exception.*;
import claygminx.worshipppt.common.entity.*;
import claygminx.worshipppt.util.PoetryFileLocator;
import claygminx.worshipppt.util.ScriptureUtil;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static claygminx.worshipppt.common.Dict.*;

@Slf4j
public class WorshipFormServiceImpl implements WorshipFormService {

    private static WorshipFormServiceImpl instance;
    private final Executor threadPool;
    private final WorshipEntity worshipEntity;
    private final UpgradeService upgradeService;
    private final ScriptureService scriptureService;

    // 组件
    private final JFrame frame;
    private ButtonGroup modelRadioGroup;
    private ButtonGroup poetryModeGroup;
    private ButtonGroup summonModeGroup;
    private ButtonGroup declarationThemeGroup;
    private String selectedDeclarationTheme;
    private Component declarationTitleRow;
    private Box declarationTableBox;
    private javax.swing.Timer declarationTitleAnimationTimer;
    /** 记录主题输入行是否处于展开状态，避免已收起时重复播放收回动画。 */
    private boolean declarationTitleExpanded;
    private String selectedSummonMode;
    private JTextField worshipDateTextField;
    private JTextField churchNameTextField;
    private Map<String, List<JTextField[]>> poetryListMap;
    private Map<String, JTextField> scriptureContentTextFieldMap;
    private List<JTextField> readingScriptureTextFieldList;
    private List<JLabel> readingScriptureLabelList;
    private Map<String, JTextField> declarationTextFieldMap;
    private Map<String, JTextField> preachTextFieldMap;
    private Map<String, JTextField> holyCommunionTextFieldMap;
    private List<JTextField> familyReportsTextFieldList;

    // 布局用的常量
    public final static String APP_TITLE = "Worship PPT";
    public final static Dimension FRAME_SIZE = new Dimension(850, 700);
    public final static Dimension FRAME_MIN_SIZE = new Dimension(730, 550);
    public final static int TABLE_HEADER_HEIGHT = 30;
    public final static int TABLE_ROW_HEIGHT = 36;
    public final static int TEXT_FIELD_HEIGHT = 30;
    public final static int REGULAR_TABLE_LEFT_WIDTH = 70;
    public final static int REGULAR_TABLE_RIGHT_WIDTH = 400;
    /** 宣召内部标签列宽度，比普通标签列更窄，用于收紧层级布局。 */
    public final static int SUMMON_LABEL_WIDTH = 45;
    public final static int POETRY_TABLE_COLUMN_WIDTH_1 = 180;
    public final static int POETRY_TABLE_COLUMN_WIDTH_2 = 300;
    public final static int POETRY_TABLE_COLUMN_WIDTH_3 = 210;
    /** 诗歌操作列仅保留加、减两个按钮，避免右侧保留过大的空白。 */
    public final static int POETRY_OPERATION_COLUMN_WIDTH = 75;
    /** 诗歌表格的固定总宽度，确保表头和数据行使用同一水平基准。 */
    public final static int POETRY_TABLE_WIDTH = POETRY_TABLE_COLUMN_WIDTH_1
            + POETRY_TABLE_COLUMN_WIDTH_2 + POETRY_OPERATION_COLUMN_WIDTH;
    /** 行操作按钮的边长，采用紧凑的圆角方块样式。 */
    public final static int BUTTON_WIDTH = 28;
    public final static int PADDING_LEFT = 6;
    public final static int V_SCROLL_BAR_SPEED = 20;
    public final static int ROW_INDEX_OFFSET = 2;

    // 线程数量
    private final static int THREAD_COUNT = 3;

    private WorshipFormServiceImpl() {
        frame = new JFrame(APP_TITLE);
        threadPool = Executors.newFixedThreadPool(THREAD_COUNT);

        logger.debug("读取缓存...");
        WorshipEntity worshipEntityCache = readWorshipEntity();
        if (worshipEntityCache != null) {
            worshipEntity = worshipEntityCache;
            logger.debug("存在缓存，读取成功");
        } else {
            worshipEntity = new WorshipEntity();
            logger.debug("没有缓存");
        }

        upgradeService = UpgradeServiceImpl.getInstance();
        scriptureService = ScriptureServiceImpl.getInstance();
    }

    /**
     * 获取表单服务实例
     *
     * @return 表单服务实例
     */
    public static WorshipFormService getInstance() {
        if (instance == null) {
            instance = new WorshipFormServiceImpl();
        }
        return instance;
    }

    public void showForm() {
        logger.debug("初始化窗体");
        frame.setSize(FRAME_SIZE);
        frame.setMinimumSize(FRAME_MIN_SIZE);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setIconImage(getImageIcon().getImage());

        logger.debug("检查新版本");
        checkVersion();

        logger.debug("添加根容器");
        Box rootBox = Box.createVerticalBox();
        JScrollPane rootPanel = new JScrollPane(rootBox);// 使窗体有滚动条
        rootPanel.getVerticalScrollBar().setUnitIncrement(V_SCROLL_BAR_SPEED);
        frame.setContentPane(rootPanel);

        logger.debug("添加组件到窗体中");
        addMenus();
        addWorshipModelPanel(rootBox);
        addCoverPanel(rootBox);
        addAllPoetryAlbumPanel(rootBox);
        addScripturePanel(rootBox);
        addDeclarationPanel(rootBox);
        addPreachPanel(rootBox);
        // TODO: 暂时隐藏“领餐名单”面板；相关实体、保存逻辑和 PPT 生成逻辑保留，后续需要时可恢复 GUI。
        addHolyCommunionPanel(null);
        addThanksgivingScripturePanel(rootBox);
        addFamilyReportsPanel(rootBox);
        addSubmitPanel(rootBox);

        logger.debug("添加完毕，展示窗体");
        frame.setVisible(true);
    }

    /**
     * 添加菜单
     */
    private void addMenus() {
        JMenuItem searchScripturesMenuItem = new JMenuItem("找经文");
        ImageIcon searchIcon = getImageIcon("search.png");
        searchScripturesMenuItem.addActionListener(actionEvent -> {
            JTextPane inputMessage = createTextPane("<html>"
                    + "<div>请输入经文编号：</div>"
                    + "<div>经文编号规则：</div>"
                    + "<ol style=\"font-family: Consolas, PingFang SC, Microsoft YaHei\">"
                    + "<li>有且仅有一个书卷，书卷名可以是全称，也可以是简称；</li>"
                    + "<li>书卷名称后面跟着章或节，可以只有章，但不可以只有节；</li>"
                    + "<li>如果后面仅跟着章，章和章用英文逗号隔开，若章和章是连续的，可以用英文短横线连接；</li>"
                    + "<li>章和节开始用英文冒号隔开；</li>"
                    + "<li>节和节的分隔符，跟章和章一样，用英文逗号连接连续节，或者用英文逗号分隔节和节。</li>"
                    + "<li>节和节的分隔符，跟章和章一样，用英文逗号连接连续节，或者用英文逗号分隔节和节。</li>"
                    + "<li>跨章节的情况，如出1:20-2:10，使用->连接，如出1:20->2:10 出1->2:10 出1:10->2</li>"
                    + "</ol>"
                    + "</html>");
            String inputText = (String) JOptionPane.showInputDialog(
                    frame,
                    inputMessage,
                    "找经文",
                    JOptionPane.PLAIN_MESSAGE,
                    searchIcon,
                    null,
                    null);
            if (inputText != null) {
                logger.info(inputText);
                if (!inputText.trim().isEmpty()) {
                    inputText = inputText.trim();

                    try {
                        logger.info("解析经文编号");
                        ScriptureNumberEntity scriptureNumberEntity = ScriptureUtil.parseNumber(inputText);
                        logger.info("解析成功");

                        int bookId = scriptureService.getIdFromBookName(
                                scriptureNumberEntity.getBookFullName(),
                                scriptureNumberEntity.getBookShortName());
                        scriptureNumberEntity.setBookId(bookId);

                        ScriptureEntity scriptureEntity = scriptureService.getScriptureWithFormat(
                                scriptureNumberEntity,
                                SystemConfig.getString(ScriptureProperty.FORMAT3));

                        JTextPane textPane = createTextPane(String.format("<html><pre>%s</pre></html>", scriptureEntity.getScripture()));
                        JScrollPane scrollMsg = new JScrollPane(
                                textPane,
                                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                        scrollMsg.setMaximumSize(new Dimension(600, 400));
                        scrollMsg.setPreferredSize(new Dimension(600, 400));
                        JOptionPane.showMessageDialog(frame, scrollMsg);
                    } catch (ScriptureNumberException | SystemException e) {
                        logger.error("", e);
                        JOptionPane.showMessageDialog(
                                frame,
                                e.getMessage(),
                                "错误提示",
                                JOptionPane.ERROR_MESSAGE);
                    }
                } else {
                    JOptionPane.showMessageDialog(
                            frame,
                            "经文编号不可为空！",
                            "错误提示",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JMenu toolsMenu = new JMenu("工具");
        toolsMenu.add(searchScripturesMenuItem);

        JMenuItem displayConfigMenuItem = new JMenuItem("查看配置信息");
        displayConfigMenuItem.addActionListener(actionEvent -> {
            StringBuilder msgBuilder = new StringBuilder("<html><ul style=\"font-family: Consolas, PingFang SC, Microsoft YaHei; list-style-type: none\">");
            msgBuilder.append("<li>")
                    .append("配置文件路径:")
                    .append(SystemConfig.USER_CONFIG_FILE_PATH)
                    .append("</li>");
            Set<Object> keySet = SystemConfig.properties.keySet();
            for (Object keyObj : keySet) {
                msgBuilder.append("<li>")
                        .append(keyObj)
                        .append(":")
                        .append(SystemConfig.properties.get(keyObj))
                        .append("</li>");
            }
            msgBuilder.append("</ul></html>");

            JTextPane msg = createTextPane(msgBuilder.toString());
            JScrollPane scrollMsg = new JScrollPane(
                    msg,
                    JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                    JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            scrollMsg.setMaximumSize(new Dimension(600, 400));
            scrollMsg.setPreferredSize(new Dimension(600, 400));
            JOptionPane.showMessageDialog(
                    frame,
                    scrollMsg,
                    "配置信息",
                    JOptionPane.INFORMATION_MESSAGE);
        });

        JMenu optionsMenu = new JMenu("选项");
        optionsMenu.add(displayConfigMenuItem);

        JMenuBar menuBar = new JMenuBar();
        menuBar.add(toolsMenu);
        menuBar.add(optionsMenu);

        frame.setJMenuBar(menuBar);
    }

    /**
     * 添加敬拜模式单选框
     *
     * @param rootBox 单选框
     */
    private void addWorshipModelPanel(Box rootBox) {
        logger.debug("创建敬拜模式组件...");
        // 默认选择无圣餐
        CoverEntity cover = worshipEntity.getCover();
        String selectedModel = WorshipModel.WITHOUT_HOLY_COMMUNION;
        if (cover != null && !isEmpty(cover.getModel())) {
            selectedModel = cover.getModel();
        }
        JRadioButton withoutHolyCommunionRadio = new JRadioButton(WorshipModel.WITHOUT_HOLY_COMMUNION, selectedModel.equals(WorshipModel.WITHOUT_HOLY_COMMUNION));
        JRadioButton withinHolyCommunionRadio = new JRadioButton(WorshipModel.WITHIN_HOLY_COMMUNION, selectedModel.equals(WorshipModel.WITHIN_HOLY_COMMUNION));
        JRadioButton withinInitiationRadio = new JRadioButton(WorshipModel.WITHIN_INITIATION, selectedModel.equals(WorshipModel.WITHIN_INITIATION));
        withinInitiationRadio.setToolTipText("入会PPT比较复杂，请更细心制作");

        // 放在同一组里
        modelRadioGroup = new ButtonGroup();
        modelRadioGroup.add(withoutHolyCommunionRadio);
        modelRadioGroup.add(withinHolyCommunionRadio);
        modelRadioGroup.add(withinInitiationRadio);

        Box box = Box.createHorizontalBox();
        int strutWidth = 5;
        box.add(new JLabel("请选择敬拜模式："));
        box.add(Box.createHorizontalStrut(strutWidth));
        box.add(withoutHolyCommunionRadio);
        box.add(Box.createHorizontalStrut(strutWidth));
        box.add(withinHolyCommunionRadio);
        box.add(Box.createHorizontalStrut(strutWidth));
        box.add(withinInitiationRadio);

        JPanel panel = new JPanel();
        panel.add(box);
        rootBox.add(panel);
        logger.debug("创建完毕");
    }

    /**
     * 添加封面
     *
     * @param rootBox 根容器
     */
    private void addCoverPanel(Box rootBox) {
        logger.debug("创建封面组件...");
        Box tableBox = Box.createVerticalBox();
        addTableTitle(tableBox, InputSection.COVER);

        CoverEntity cover = worshipEntity.getCover();

        worshipDateTextField = addRegularTableInputRow(tableBox, CoverKey.WORSHIP_DATE);
        worshipDateTextField.setToolTipText("推荐填写格式：主后yyyy年MM月dd日");
        if (cover != null && !isEmpty(cover.getWorshipDate())) {
            worshipDateTextField.setText(cover.getWorshipDate());
        }

        churchNameTextField = addRegularTableInputRow(tableBox, CoverKey.CHURCH_NAME);
        if (cover != null && !isEmpty(cover.getChurchName())) {
            churchNameTextField.setText(cover.getChurchName());
        }

        JPanel panel = new JPanel();
        panel.add(tableBox);
        rootBox.add(panel);
    }

    /**
     * 添加所有的诗歌集面板
     *
     * @param rootBox 根容器
     */
    private void addAllPoetryAlbumPanel(Box rootBox) {
        String[] albumNames = new String[]{
                PoetryAlbumName.PRAY_POETRY, PoetryAlbumName.PRACTISE_POETRY, PoetryAlbumName.WORSHIP_POETRY,
                PoetryAlbumName.RESPONSE_POETRY, PoetryAlbumName.OFFERTORY_POETRY,
                PoetryAlbumName.HOLY_COMMUNION_POETRY, PoetryAlbumName.INITIATION_POETRY
        };
        poetryListMap = new HashMap<>(albumNames.length, (int) Math.ceil(albumNames.length / 0.75));
        for (String albumName : albumNames) {
            addOnePoetryAlbumPanel(rootBox, albumName);
        }
    }

    /**
     * 添加一个指定诗歌集的面板
     *
     * @param rootBox 根容器
     * @param name    诗歌集的名称
     */
    private void addOnePoetryAlbumPanel(Box rootBox, String name) {
        Box tableBox = Box.createVerticalBox();
        if (!poetryListMap.containsKey(name)) {
            List<JTextField[]> list = new LinkedList<>();
            poetryListMap.put(name, list);
        }
        // 标题
        addTableTitle(tableBox, name);

        // 在标题下方、表头上方添加模式选项
        if (PoetryAlbumName.WORSHIP_POETRY.equals(name)) {
            PoetryContentEntity poetryContent = worshipEntity.getPoetryContent();
            int selectedMode = 0;
            if (poetryContent != null) {
                selectedMode = poetryContent.getMode();
            }
            JRadioButton mode1Radio = new JRadioButton(PoetryMode.MODE_1_1, selectedMode == 0);
            JRadioButton mode2Radio = new JRadioButton(PoetryMode.MODE_2_1, selectedMode != 0);

            poetryModeGroup = new ButtonGroup();
            poetryModeGroup.add(mode1Radio);
            poetryModeGroup.add(mode2Radio);

            Box modeBox = Box.createHorizontalBox();
            int strutWidth = 5;
            modeBox.add(new JLabel("模式："));
            modeBox.add(Box.createHorizontalStrut(strutWidth));
            modeBox.add(mode1Radio);
            modeBox.add(Box.createHorizontalStrut(strutWidth));
            modeBox.add(mode2Radio);
            tableBox.add(modeBox);
        }

        // 表头
        addPoetryTableHeader(tableBox);

        // 表体
        PoetryAlbumEntity album = getPoetryAlbumEntity(name);
        if (album != null && album.getPoetryList() != null && !album.getPoetryList().isEmpty()) {
            List<PoetryEntity> poetryList = album.getPoetryList();
            List<JTextField[]> textFieldsList = poetryListMap.get(name);
            for (int i = 0; i < poetryList.size(); i++) {
                addPoetryTableRow(tableBox, name, i);
                JTextField[] textFields = textFieldsList.get(i);
                textFields[0].setText(poetryList.get(i).getName());
                textFields[1].setText(poetryList.get(i).getDirectory().getAbsolutePath());
            }
        } else {
            addPoetryTableRow(tableBox, name, 0);
        }

        JPanel panel = new JPanel();
        panel.setName(name);
        panel.add(tableBox);
        rootBox.add(panel);
    }

    /**
     * 添加经文面板
     *
     * @param rootBox 根容器
     */
    private void addScripturePanel(Box rootBox) {
        Box tableBox = Box.createVerticalBox();
        addTableTitle(tableBox, InputSection.SCRIPTURE);

        scriptureContentTextFieldMap = new HashMap<>();

        ScriptureContentEntity content = worshipEntity.getScriptureContent();
        selectedSummonMode = content == null ? SummonMode.SCRIPTURE : content.getSummonMode();
        if (!SummonMode.CUSTOM.equals(selectedSummonMode)) {
            selectedSummonMode = SummonMode.SCRIPTURE;
        }

        JRadioButton scriptureRadio = new JRadioButton(SummonMode.SCRIPTURE, SummonMode.SCRIPTURE.equals(selectedSummonMode));
        JRadioButton customRadio = new JRadioButton(SummonMode.CUSTOM, SummonMode.CUSTOM.equals(selectedSummonMode));
        summonModeGroup = new ButtonGroup();
        summonModeGroup.add(scriptureRadio);
        summonModeGroup.add(customRadio);
        scriptureRadio.addActionListener(e -> selectedSummonMode = SummonMode.SCRIPTURE);
        customRadio.addActionListener(e -> selectedSummonMode = SummonMode.CUSTOM);

        Box summonModeBox = Box.createHorizontalBox();
        summonModeBox.add(Box.createHorizontalStrut(SUMMON_LABEL_WIDTH));
        summonModeBox.add(new JLabel("回应方式："));
        summonModeBox.add(Box.createHorizontalStrut(5));
        summonModeBox.add(scriptureRadio);
        summonModeBox.add(Box.createHorizontalStrut(5));
        summonModeBox.add(customRadio);

        /*
         * 宣召是经文面板中的一个横向分组：左侧显示分组标题，右侧承载
         * 主领、回应方式和回应三行内容。这样既能表达标题层级，也能让
         * 主领和回应的输入框右边界与普通经文输入框保持一致。
         */
        Box summonSectionBox = Box.createHorizontalBox();
        addSummonSectionLabel(summonSectionBox, ScriptureContentKey.SUMMON);

        Box summonContentBox = Box.createVerticalBox();
        summonSectionBox.add(summonContentBox);
        tableBox.add(summonSectionBox);

        JTextField summonLeaderTextField = addSummonInputRow(summonContentBox, ScriptureContentKey.SUMMON_LEADER);
        summonLeaderTextField.setToolTipText("经文模式输入经文编号，自定义模式输入要原样显示的文字");
        scriptureContentTextFieldMap.put(ScriptureContentKey.SUMMON_LEADER, summonLeaderTextField);

        // 回应方式只控制“回应”内容，因此放在主领输入之后、回应输入之前。
        summonContentBox.add(summonModeBox);

        JTextField summonResponseTextField = addSummonInputRow(summonContentBox, ScriptureContentKey.SUMMON_RESPONSE);
        summonResponseTextField.setToolTipText("经文模式输入经文编号，自定义模式输入要原样显示的文字");
        scriptureContentTextFieldMap.put(ScriptureContentKey.SUMMON_RESPONSE, summonResponseTextField);

        // 旧版本只有主领经文和固定回应文字，打开旧缓存时恢复原有默认回应。
        summonResponseTextField.setText("我们要赞美耶和华！");

        JTextField publicPrayTextField = addRegularTableInputRow(tableBox, ScriptureContentKey.PUBLIC_PRAY);
        scriptureContentTextFieldMap.put(ScriptureContentKey.PUBLIC_PRAY, publicPrayTextField);

        readingScriptureTextFieldList = new ArrayList<>();
        readingScriptureLabelList = new ArrayList<>();
        addReadingScriptureRow(tableBox, 0);

        JTextField confessTextField = addRegularTableInputRow(tableBox, ScriptureContentKey.CONFESS);
        scriptureContentTextFieldMap.put(ScriptureContentKey.CONFESS, confessTextField);

        JTextField forgiveSinsTextField = addRegularTableInputRow(tableBox, ScriptureContentKey.FORGIVE_SINS);
        scriptureContentTextFieldMap.put(ScriptureContentKey.FORGIVE_SINS, forgiveSinsTextField);


        if (content != null) {
            if (!isEmpty(content.getSummonLeader())) {
                summonLeaderTextField.setText(content.getSummonLeader());
            }
            if (!isEmpty(content.getSummonResponse())) {
                summonResponseTextField.setText(content.getSummonResponse());
            }
            if (!isEmpty(content.getPublicPray())) {
                publicPrayTextField.setText(content.getPublicPray());
            }
            if (!isEmpty(content.getConfess())) {
                confessTextField.setText(content.getConfess());
            }
            if (!isEmpty(content.getForgiveSins())) {
                forgiveSinsTextField.setText(content.getForgiveSins());
            }
            List<String> readingScriptureList = content.getReadingScriptureList();
            if (readingScriptureList != null && !readingScriptureList.isEmpty()) {
                readingScriptureTextFieldList.get(0).setText(readingScriptureList.get(0));
                for (int i = 1; i < readingScriptureList.size(); i++) {
                    addReadingScriptureRow(tableBox, i);
                    readingScriptureTextFieldList.get(i).setText(readingScriptureList.get(i));
                }
            } else if (!isEmpty(content.getReadingScripture())) {
                readingScriptureTextFieldList.get(0).setText(content.getReadingScripture());
            }
        }


        JPanel panel = new JPanel();
        panel.add(tableBox);
        rootBox.add(panel);
    }

    /**
     * 添加宣信面板
     *
     * @param rootBox 根容器
     */
    private void addDeclarationPanel(Box rootBox) {
        Box tableBox = Box.createVerticalBox();
        declarationTableBox = tableBox;
        addTableTitle(tableBox, InputSection.DECLARATION);

        declarationTextFieldMap = new HashMap<>();
        DeclarationEntity declaration = worshipEntity.getDeclaration();
        selectedDeclarationTheme = declaration == null ? "信条" : declaration.getTheme();
        if (!List.of("信条", "使徒信经", "迦克墩信经", "尼西亚信经", "亚他那修信经").contains(selectedDeclarationTheme)) {
            selectedDeclarationTheme = "信条";
        }

        Box themeBox = Box.createHorizontalBox();
        themeBox.add(new JLabel("宣信方式："));
        themeBox.add(Box.createHorizontalStrut(5));
        declarationThemeGroup = new ButtonGroup();
        addDeclarationThemeRadio(themeBox, "信条");
        addDeclarationThemeRadio(themeBox, "使徒信经");
        addDeclarationThemeRadio(themeBox, "迦克墩信经");
        addDeclarationThemeRadio(themeBox, "尼西亚信经");
        addDeclarationThemeRadio(themeBox, "亚他那修信经");
        tableBox.add(themeBox);

        JTextField titleTextField = addRegularTableInputRow(tableBox, DeclarationKey.TITLE);
        declarationTitleRow = titleTextField.getParent().getParent();
        // 固定输入框横向尺寸，避免主题行动画过程中 BoxLayout 临时压缩输入框。
        Dimension declarationTitleInputSize = new Dimension(
                REGULAR_TABLE_RIGHT_WIDTH - PADDING_LEFT,
                TEXT_FIELD_HEIGHT);
        titleTextField.setMinimumSize(declarationTitleInputSize);
        titleTextField.setPreferredSize(declarationTitleInputSize);
        titleTextField.setMaximumSize(declarationTitleInputSize);
        titleTextField.setToolTipText("目前仅支持‘西敏信条18:1-3、信条20:1,3、信条18-19等类似格式输入’");
        declarationTextFieldMap.put(DeclarationKey.TITLE, titleTextField);
        // 现阶段不需要讲员面板
//        JTextField speakerTextField = addRegularTableInputRow(tableBox, DeclarationKey.SPEAKER);
//        declarationTextFieldMap.put(DeclarationKey.SPEAKER, speakerTextField);

        if (declaration != null) {
            if (!isEmpty(declaration.getTitle())) {
                titleTextField.setText(declaration.getTitle());
            }
//            if (!isEmpty(declaration.getSpeaker())) {
//                speakerTextField.setText(declaration.getSpeaker());
//            }
        }
        setDeclarationTitleVisibility("信条".equals(selectedDeclarationTheme), false);

        JPanel panel = new JPanel();
        panel.add(tableBox);
        rootBox.add(panel);
    }

    /** 添加宣信方式单选项，并将当前选择同步到待保存字段。 */
    private void addDeclarationThemeRadio(Box themeBox, String theme) {
        JRadioButton radio = new JRadioButton(theme, theme.equals(selectedDeclarationTheme));
        declarationThemeGroup.add(radio);
        radio.addActionListener(event -> {
            selectedDeclarationTheme = theme;
            updateDeclarationTitleVisibility();
        });
        themeBox.add(radio);
        themeBox.add(Box.createHorizontalStrut(5));
    }

    /** 根据宣信方式显示或隐藏信条主题输入行。 */
    private void updateDeclarationTitleVisibility() {
        setDeclarationTitleVisibility("信条".equals(selectedDeclarationTheme), true);
    }

    /**
     * 设置信条主题输入行的显示状态。
     *
     * <p>用户切换宣信方式时逐步调整行高，初始化时直接设置最终状态，
     * 避免窗口首次显示时播放无意义的动画。</p>
     *
     * @param visible 是否显示主题输入行
     * @param animate 是否播放动画
     */
    private void setDeclarationTitleVisibility(boolean visible, boolean animate) {
        if (declarationTitleRow == null) {
            return;
        }

        if (declarationTitleAnimationTimer != null && declarationTitleAnimationTimer.isRunning()) {
            declarationTitleAnimationTimer.stop();
        }

        int targetHeight = visible ? TABLE_ROW_HEIGHT : 0;
        if (!animate) {
            declarationTitleRow.setVisible(visible);
            setDeclarationTitleRowHeight(targetHeight);
            declarationTitleExpanded = visible;
            return;
        }

        int currentHeight = declarationTitleRow.getHeight();
        if (currentHeight < 0 || currentHeight > TABLE_ROW_HEIGHT) {
            currentHeight = declarationTitleExpanded ? TABLE_ROW_HEIGHT : 0;
        }

        // 目标状态已经达到时不启动计时器，特别是已收起的其他信经之间切换。
        if (currentHeight == targetHeight) {
            declarationTitleRow.setVisible(visible);
            declarationTitleExpanded = visible;
            setDeclarationTitleRowHeight(targetHeight);
            return;
        }

        // 动画期间保持组件可见，收起到 0 高度后再隐藏，避免布局瞬间跳变。
        declarationTitleRow.setVisible(true);
        int direction = Integer.compare(targetHeight, currentHeight);

        final int[] animatedHeight = {currentHeight};
        declarationTitleAnimationTimer = new javax.swing.Timer(15, event -> {
            animatedHeight[0] += direction * 4;
            boolean reachedTarget = direction > 0
                    ? animatedHeight[0] >= targetHeight
                    : animatedHeight[0] <= targetHeight;
            if (reachedTarget) {
                animatedHeight[0] = targetHeight;
            }
            setDeclarationTitleRowHeight(animatedHeight[0]);
            if (reachedTarget) {
                declarationTitleAnimationTimer.stop();
                declarationTitleRow.setVisible(visible);
                declarationTitleExpanded = visible;
            }
        });
        declarationTitleAnimationTimer.start();
    }

    /** 更新主题输入行尺寸约束，使 BoxLayout 按动画高度重新布局。 */
    private void setDeclarationTitleRowHeight(int height) {
        Dimension size = new Dimension(REGULAR_TABLE_LEFT_WIDTH + REGULAR_TABLE_RIGHT_WIDTH, height);
        declarationTitleRow.setMinimumSize(size);
        declarationTitleRow.setPreferredSize(size);
        declarationTitleRow.setMaximumSize(size);
        if (declarationTableBox != null) {
            declarationTableBox.revalidate();
            declarationTableBox.repaint();
        }
    }

    /**
     * 添加证道面板
     *
     * @param rootBox 根容器
     */
    private void addPreachPanel(Box rootBox) {
        Box tableBox = Box.createVerticalBox();
        addTableTitle(tableBox, InputSection.PREACH);

        preachTextFieldMap = new HashMap<>();
        JTextField titleTextField = addRegularTableInputRow(tableBox, PreachKey.TITLE);
        preachTextFieldMap.put(PreachKey.TITLE, titleTextField);

        JTextField scriptureTextField = addRegularTableInputRow(tableBox, PreachKey.SCRIPTURE);
        preachTextFieldMap.put(PreachKey.SCRIPTURE, scriptureTextField);

        PreachEntity preach = worshipEntity.getPreach();
        if (preach != null) {
            if (!isEmpty(preach.getTitle())) {
                titleTextField.setText(preach.getTitle());
            }
            if (!isEmpty(preach.getScriptureNumber())) {
                scriptureTextField.setText(preach.getScriptureNumber());
            }
        }

        JPanel panel = new JPanel();
        panel.add(tableBox);
        rootBox.add(panel);
    }

    /**
     * 添加圣餐面板
     *
     * @param rootBox 根容器；传入 {@code null} 时仅初始化数据字段，不将面板添加到 GUI
     */
    private void addHolyCommunionPanel(Box rootBox) {
        Box tableBox = Box.createVerticalBox();
        addTableTitle(tableBox, InputSection.HOLY_COMMUNION);

        holyCommunionTextFieldMap = new HashMap<>();
        JTextField nameListTextField = addRegularTableInputRow(tableBox, HolyCommunionKey.NAME_LIST);
        nameListTextField.setToolTipText("用英文逗号分隔姓名");
        holyCommunionTextFieldMap.put(HolyCommunionKey.NAME_LIST, nameListTextField);

        HolyCommunionEntity holyCommunion = worshipEntity.getHolyCommunion();
        if (holyCommunion != null) {
            if (holyCommunion.getNameList() != null && !holyCommunion.getNameList().isEmpty()) {
                String join = String.join(",", holyCommunion.getNameList());
                nameListTextField.setText(join);
            }
        }

        if (rootBox != null) {
            JPanel panel = new JPanel();
            panel.add(tableBox);
            rootBox.add(panel);
        }
    }

    /**
     * 添加家事报告面板
     *
     * @param rootBox 根容器
     */
    private void addFamilyReportsPanel(Box rootBox) {
        Box tableBox = Box.createVerticalBox();
        addTableTitle(tableBox, InputSection.FAMILY_REPORTS);

        Box header = Box.createHorizontalBox();
        tableBox.add(header);
        addTableColumn(header, "家事报告", REGULAR_TABLE_RIGHT_WIDTH);
        addTableColumn(header, "操作", POETRY_OPERATION_COLUMN_WIDTH);

        familyReportsTextFieldList = new LinkedList<>();

        // 如果有本地缓存，则加载
        List<String> familyReportList = worshipEntity.getFamilyReports();
        if (familyReportList != null && !familyReportList.isEmpty()) {
            for (int i = 0; i < familyReportList.size(); i++) {
                addFamilyReportsTableInputRow(tableBox, i);
            }
            int i = 0;
            for (JTextField textField : familyReportsTextFieldList) {
                textField.setText(familyReportList.get(i++));
            }
        } else {
            addFamilyReportsTableInputRow(tableBox, 0);
        }

        JPanel panel = new JPanel();
        panel.setName(InputSection.FAMILY_REPORTS);
        panel.add(tableBox);
        rootBox.add(panel);
    }

    /**
     * 添加提交按钮
     *
     * @param rootBox 根盒子
     */
    private void addSubmitPanel(Box rootBox) {
        JButton submitButton = new JButton("生成PPT" +
                "");
        submitButton.setBackground(new Color(43, 102, 211));
        JPanel panel = new JPanel();
        panel.add(submitButton);
        rootBox.add(panel);

        submitButton.addActionListener((action) -> run(() -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("保存敬拜PPT文件");
            fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);
            fileChooser.setCurrentDirectory(new File(System.getProperty("user.home")));
            fileChooser.setFileFilter(new FileFilter() {
                @Override
                public boolean accept(File f) {
                    return f.isDirectory() || f.getName().toLowerCase().endsWith(".ppt") || f.getName().toLowerCase().endsWith(".pptx");
                }

                @Override
                public String getDescription() {
                    return "*.pptx（幻灯片文件）";
                }
            });
            int result = fileChooser.showSaveDialog(frame);
            logger.info("选择{}", result);
            if (result == JFileChooser.APPROVE_OPTION) {
                File outputFile = fileChooser.getSelectedFile();
                logger.info(outputFile.getAbsolutePath());
                String simpleFileName = outputFile.getName().toLowerCase();
                if (!simpleFileName.endsWith(".ppt") && !simpleFileName.endsWith(".pptx")) {
                    logger.info("自动添加文件扩展名.pptx");
                    outputFile = new File(outputFile.getParent(), outputFile.getName() + ".pptx");
                }

                boolean prepareResult = prepare();
                saveWorshipEntity(worshipEntity);

                if (prepareResult) {
                    ProgressMonitor pm = new ProgressMonitor(frame, "制作敬拜PPT", "准备制作", 0, 100);
                    File finalOutputFile = outputFile;
                    threadPool.execute(() -> {
                        WorshipPPTServiceImpl worshipPPTService = new WorshipPPTServiceImpl(worshipEntity, finalOutputFile);
                        worshipPPTService.setProgressMonitor(pm);
                        try {
                            worshipPPTService.make();
                            File pptFile = worshipPPTService.getFile();
                            String message = "<html>" +
                                    "<p>PPT制作完成！</p>" +
                                    "<p>文件位于：" +
                                    pptFile.getAbsolutePath() +
                                    "</p>" +
                                    "<p>你还需要做一些检查工作：</p>" +
                                    "<ol><li>如果不保留教会名称，请删除首页的占位符</li>" +
                                    "<li>各环节的经文是否溢出边界</li>" +
                                    "<li>圣餐诗歌需要手动调整以符合圣礼需要</li>" +
                                    "<li>还有更多需要细心检查的细节</li></ol></html>";
                            JTextPane f = createTextPane(message);
                            JOptionPane.showMessageDialog(frame, f, "提示", JOptionPane.INFORMATION_MESSAGE);
                        } catch (FileServiceException | WorshipStepException | PPTLayoutException
                                 | PoetrySourcesNotExistException | SystemException | ScriptureServiceException
                                 | ScriptureNumberException e) {
                            pm.close();
                            logger.error("制作PPT时出现错误！", e);
                            JOptionPane.showMessageDialog(
                                    frame,
                                    getExceptionReason(e),
                                    "提示",
                                    JOptionPane.ERROR_MESSAGE);
                        } catch (Exception e) {
                            pm.close();
                            logger.error("未捕获的错误！！！", e);
                            JOptionPane.showMessageDialog(
                                    frame,
                                    "制作PPT时发生异常：" + getExceptionReason(e),
                                    "提示",
                                    JOptionPane.ERROR_MESSAGE);
                        }
                    });
                }
            }
        }));
    }

///////////////////////////
// Business method
///////////////////////////

    /**
     * 提交之前检查数据
     *
     * @return 是否检查通过
     */
    private boolean prepare() {
        return prepareCover() && preparePoetry() && prepareScriptureContent() && prepareDeclaration()
                && preparePreach() && prepareFamilyReport() && prepareThanksgivingScripture()
                && prepareHolyCommunion();
    }

    private boolean prepareCover() {
        logger.debug("检查封面信息...");

        String selectedModel = null;
        Enumeration<AbstractButton> radioElements = modelRadioGroup.getElements();
        while (radioElements.hasMoreElements()) {
            AbstractButton radioElement = radioElements.nextElement();
            if (radioElement.isSelected()) {
                selectedModel = radioElement.getText();
            }
        }
        if (isEmpty(selectedModel)) {
            warn("请选择敬拜模式");
            return false;
        }
        String worshipDate = worshipDateTextField.getText();
        if (isEmpty(worshipDate)) {
            warn("请输入敬拜日期");
            return false;
        }
        String churchName = churchNameTextField.getText();
        if (isEmpty(churchName)) {
            logger.warn("未输入教会名称");
        }

        CoverEntity cover = new CoverEntity();
        cover.setModel(selectedModel);
        cover.setWorshipDate(worshipDate);
        cover.setChurchName(churchName);
        worshipEntity.setCover(cover);

        return true;
    }

    private boolean preparePoetry() {
        logger.debug("检查诗歌...");

        String selectedModel = worshipEntity.getCover().getModel();
        PoetryContentEntity poetryContentEntity = new PoetryContentEntity();
        worshipEntity.setPoetryContent(poetryContentEntity);

        int poetryMode = 0;
        Enumeration<AbstractButton> poetryRadioElements = poetryModeGroup.getElements();
        while (poetryRadioElements.hasMoreElements()) {
            AbstractButton radioElement = poetryRadioElements.nextElement();
            if (radioElement.isSelected()) {
                poetryMode = PoetryMode.MODE_2_1.equals(radioElement.getText()) ? 1 : 0;
            }
        }
        poetryContentEntity.setMode(poetryMode);

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("以下诗歌面板的输入不合法:\n");
        boolean warnTag = false;
        // 将7个诗歌面板的检查信息一并返回，防止程序中断导致缓存对象中的PrayPoetryAlbum对象成员为null
        List<JTextField[]> prayTextFieldsList = poetryListMap.get(PoetryAlbumName.PRAY_POETRY);
        String message = checkPoetryInfo(PoetryAlbumName.PRAY_POETRY, prayTextFieldsList, 0);
        if (message != null) {
            stringBuilder.append(message);
            warnTag = true;
        }
        PoetryAlbumEntity prayPoetryAlbum = readPoetryAlbum(PoetryAlbumName.PRAY_POETRY, prayTextFieldsList);
        poetryContentEntity.setPrayPoetryAlbum(prayPoetryAlbum);


        List<JTextField[]> practiseTextFieldsList = poetryListMap.get(PoetryAlbumName.PRACTISE_POETRY);
        message = checkPoetryInfo(PoetryAlbumName.PRACTISE_POETRY, practiseTextFieldsList, 0);
        if (message != null) {
            stringBuilder.append(message);
            warnTag = true;
        }
        PoetryAlbumEntity practisePoetryAlbum = readPoetryAlbum(PoetryAlbumName.PRACTISE_POETRY, practiseTextFieldsList);
        poetryContentEntity.setPractisePoetryAlbum(practisePoetryAlbum);


        List<JTextField[]> worshipTextFieldsList = poetryListMap.get(PoetryAlbumName.WORSHIP_POETRY);
        message = checkPoetryInfo(PoetryAlbumName.WORSHIP_POETRY, worshipTextFieldsList, 2);
        if (message != null) {
            stringBuilder.append(message);
            warnTag = true;
        }
        PoetryAlbumEntity worshipPoetryAlbum = readPoetryAlbum(PoetryAlbumName.WORSHIP_POETRY, worshipTextFieldsList);
        poetryContentEntity.setWorshipPoetryAlbum(worshipPoetryAlbum);

        List<JTextField[]> responseTextFieldsList = poetryListMap.get(PoetryAlbumName.RESPONSE_POETRY);
        message = checkPoetryInfo(PoetryAlbumName.RESPONSE_POETRY, responseTextFieldsList, 1);
        if (message != null) {
            stringBuilder.append(message);
            warnTag = true;
        }
        PoetryAlbumEntity responsePoetryAlbum = readPoetryAlbum(PoetryAlbumName.RESPONSE_POETRY, responseTextFieldsList);
        poetryContentEntity.setResponsePoetryAlbum(responsePoetryAlbum);


        List<JTextField[]> offertoryTextFieldsList = poetryListMap.get(PoetryAlbumName.OFFERTORY_POETRY);
        message = checkPoetryInfo(PoetryAlbumName.OFFERTORY_POETRY, offertoryTextFieldsList, 1);
        if (message != null) {
            stringBuilder.append(message);
            warnTag = true;
        }
        PoetryAlbumEntity offertoryPoetryAlbum = readPoetryAlbum(PoetryAlbumName.OFFERTORY_POETRY, offertoryTextFieldsList);
        poetryContentEntity.setOffertoryPoetryAlbum(offertoryPoetryAlbum);

        if (WorshipModel.WITHIN_HOLY_COMMUNION.equals(selectedModel) || WorshipModel.WITHIN_INITIATION.equals(selectedModel)) {
            List<JTextField[]> holyCommunionTextFieldsList = poetryListMap.get(PoetryAlbumName.HOLY_COMMUNION_POETRY);
            // procedure 会分别读取第一首和第二首圣餐诗歌，因此两行都必须填写有效信息。
            message = checkPoetryInfo(PoetryAlbumName.HOLY_COMMUNION_POETRY, holyCommunionTextFieldsList, 2);
            if (message != null) {
                stringBuilder.append(message);
                warnTag = true;
            }
            PoetryAlbumEntity holyCommunionPoetryAlbum = readPoetryAlbum(PoetryAlbumName.HOLY_COMMUNION_POETRY, holyCommunionTextFieldsList);
            poetryContentEntity.setHolyCommunionPoetryAlbum(holyCommunionPoetryAlbum);

        }

        if (WorshipModel.WITHIN_INITIATION.equals(selectedModel)) {
            List<JTextField[]> initiationTextFieldsList = poetryListMap.get(PoetryAlbumName.INITIATION_POETRY);
            message = checkPoetryInfo(PoetryAlbumName.INITIATION_POETRY, initiationTextFieldsList, 1);
            if (message != null) {
                stringBuilder.append(message);
                warnTag = true;
            }
            PoetryAlbumEntity initiationPoetryAlbum = readPoetryAlbum(PoetryAlbumName.INITIATION_POETRY, initiationTextFieldsList);
            poetryContentEntity.setInitiationPoetryAlbum(initiationPoetryAlbum);
        }
        if (warnTag) {
            // 在某个环节的面板填充有问题
            warn(stringBuilder.toString());
            return false;
        }
        // 中间环节没有发现问题
        return true;
    }

    private boolean prepareScriptureContent() {
        logger.debug("检查经文...");

        Set<String> keySet = scriptureContentTextFieldMap.keySet();
        for (String key : keySet) {
            JTextField textField = scriptureContentTextFieldMap.get(key);
            String text = textField.getText();
            if (isEmpty(text)) {
                warn("经文部分需要全部填写！");
                return false;
            }
        }

        ScriptureContentEntity scriptureContentEntity = new ScriptureContentEntity();
        // 保存前仅去除输入首尾空白；自定义内容内部的换行和空格保持不变。
        String summonLeader = scriptureContentTextFieldMap.get(ScriptureContentKey.SUMMON_LEADER).getText().trim();
        String summonResponse = scriptureContentTextFieldMap.get(ScriptureContentKey.SUMMON_RESPONSE).getText().trim();
        scriptureContentEntity.setSummonMode(selectedSummonMode);
        scriptureContentEntity.setSummonLeader(summonLeader);
        scriptureContentEntity.setSummonResponse(summonResponse);
        scriptureContentEntity.setConfess(scriptureContentTextFieldMap.get(ScriptureContentKey.CONFESS).getText().trim());
        scriptureContentEntity.setForgiveSins(scriptureContentTextFieldMap.get(ScriptureContentKey.FORGIVE_SINS).getText().trim());
        scriptureContentEntity.setPublicPray(scriptureContentTextFieldMap.get(ScriptureContentKey.PUBLIC_PRAY).getText().trim());
        List<String> readingScriptureList = new ArrayList<>();
        for (JTextField textField : readingScriptureTextFieldList) {
            readingScriptureList.add(textField.getText().trim());
        }
        scriptureContentEntity.setReadingScriptureList(readingScriptureList);
        worshipEntity.setScriptureContent(scriptureContentEntity);

        return true;
    }

    private boolean prepareDeclaration() {
        logger.debug("检查宣信...");

        JTextField declarationTitleTextField = declarationTextFieldMap.get(DeclarationKey.TITLE);
//        JTextField declarationSpeakerTextField = declarationTextFieldMap.get(DeclarationKey.SPEAKER);
        if ("信条".equals(selectedDeclarationTheme) && isEmpty(declarationTitleTextField.getText())) {
            warn("需要填写宣信主题！");
            return false;
        }
//        if (isEmpty(declarationSpeakerTextField.getText())) {
//            logger.warn("未输入讲员！");
//        }

        DeclarationEntity declaration = new DeclarationEntity();
        declaration.setTheme(selectedDeclarationTheme);
        declaration.setTitle(declarationTitleTextField.getText().trim());
//        declaration.setSpeaker(declarationSpeakerTextField.getText().trim());
        worshipEntity.setDeclaration(declaration);

        return true;
    }

    private boolean preparePreach() {
        logger.debug("检查证道...");

        JTextField preachTitleTextField = preachTextFieldMap.get(PreachKey.TITLE);
        JTextField preachScriptureTextField = preachTextFieldMap.get(PreachKey.SCRIPTURE);
        if (isEmpty(preachTitleTextField.getText())) {
            warn("请输入证道主题");
            return false;
        }
        if (isEmpty(preachScriptureTextField.getText())) {
            logger.warn("未输入证道经文！");
        }

        PreachEntity preach = new PreachEntity();
        preach.setTitle(preachTitleTextField.getText().trim());
        preach.setScriptureNumber(preachScriptureTextField.getText().trim());
        worshipEntity.setPreach(preach);

        return true;
    }

    private boolean prepareFamilyReport() {
        logger.debug("检查家事报告...");

        List<String> familyReports = new ArrayList<>();
        for (JTextField textField : familyReportsTextFieldList) {
            String item = textField.getText().trim();
            if (!isEmpty(item)) {
                familyReports.add(item);
            }
        }

        worshipEntity.setFamilyReports(familyReports);

        return true;
    }

    private boolean prepareHolyCommunion() {
        logger.debug("检查圣餐...");

        String selectedModel = worshipEntity.getCover().getModel();
        if (WorshipModel.WITHIN_HOLY_COMMUNION.equals(selectedModel) || WorshipModel.WITHIN_INITIATION.equals(selectedModel)) {
            JTextField nameListTextField = holyCommunionTextFieldMap.get(HolyCommunionKey.NAME_LIST);
            HolyCommunionEntity holyCommunionEntity = new HolyCommunionEntity();
            List<String> nameList;
            String nameListStr = nameListTextField.getText().trim();
            if (!isEmpty(nameListStr)) {
                String[] nameArray = nameListStr.split(",");
                nameList = Arrays.asList(nameArray);
            } else {
                logger.warn("未输入领餐名单，难不成都是会友？");
                nameList = new ArrayList<>();
            }
            holyCommunionEntity.setNameList(nameList);
            worshipEntity.setHolyCommunion(holyCommunionEntity);
        }

        return true;
    }

    /** 兼容旧缓存，确保未保存过该选项时仍能命中默认 procedure 分支。 */
    private boolean prepareThanksgivingScripture() {
        if (isEmpty(worshipEntity.getThanksgivingScripture())) {
            worshipEntity.setThanksgivingScripture(ThanksgivingScripture.CORINTHIANS_AND_ROMANS);
        }
        return true;
    }

    /**
     * 检查面板中的诗歌信息，如果无错误，会返回null
     * 增加歌谱源文件夹校验，如果里面没有png文件会有提示
     *
     * @param albumName
     * @param poetryTextFieldList
     * @param minCount
     * @return
     */
    private String checkPoetryInfo(String albumName, List<JTextField[]> poetryTextFieldList, int minCount) {
//        // 当前使用环境不需要检查祷告诗歌
//        if (albumName.equals(PoetryAlbumName.PRAY_POETRY)) {
//            return null;
//        }
        int count = 0;
        StringBuilder errorBuilder = new StringBuilder();
        // 遍历当前albumName面板中的诗歌列表
        for (int i = 0; i < poetryTextFieldList.size(); i++) {
            JTextField[] textFields = poetryTextFieldList.get(i);
            // 获取曲名栏目的字符串
            String name = textFields[0].getText();
            // 获取路径栏目的字符串
            String directory = textFields[1].getText();

            // 如果祷告诗歌和或者练唱诗歌没有输入，跳过检查
            if (albumName.equals(PoetryAlbumName.PRAY_POETRY) && isEmpty(name) && isEmpty(directory)){
                return null;
            }
            if (albumName.equals(PoetryAlbumName.PRACTISE_POETRY) && isEmpty(name) && isEmpty(directory)){
                return null;
            }

            // 检查输入的完整性
            if (!isEmpty(name) && isEmpty(directory) || isEmpty(name) && !isEmpty(directory)) {
                errorBuilder.append(" - " + albumName + "第" + (i + 1) + "首诗歌输入不完整\n");
            }
            File dirFile = new File(directory);
            if (dirFile.exists()) {

                try {
                    PoetryFileLocator.getPoetryFiles(dirFile);
                } catch (PoetrySourcesNotExistException e) {
                    logger.info(e.getMessage());
                    errorBuilder.append(" - " + albumName + "中第" + (i + 1) + "首诗歌未发现歌谱");
                }
            } else {
                // 目录不存在
                errorBuilder.append(" - " + albumName + "第" + (i + 1) + "行输入的路径不存在\n");
            }


            if (!isEmpty(name) && !isEmpty(directory)) {
                count++;
            }
        }
        if (!errorBuilder.isEmpty()) {
            // 有不满足的路径
            return errorBuilder.toString();
        }
        logger.debug("向{}输入了{}首诗歌", albumName, count);
        if (count < minCount) {
            return albumName + "需要至少" + minCount + "首诗歌！";
        }
        return null;
    }

    // 读取诗歌集
    private PoetryAlbumEntity readPoetryAlbum(String albumName, List<JTextField[]> textFieldsList) {
        List<PoetryEntity> poetryEntityList = new ArrayList<>();
        for (JTextField[] textFields : textFieldsList) {
            String name = textFields[0].getText().trim();
            String directoryStr = textFields[1].getText().trim();
            if (!isEmpty(name) && !isEmpty(directoryStr)) {
                poetryEntityList.add(new PoetryEntity(name, new File(directoryStr)));
            }
        }
        if (!poetryEntityList.isEmpty()) {
            PoetryAlbumEntity poetryAlbumEntity = new PoetryAlbumEntity();
            poetryAlbumEntity.setName(albumName);
            poetryAlbumEntity.setPoetryList(poetryEntityList);
            return poetryAlbumEntity;
        }
        return null;
    }

    // 根据名称获取诗歌集
    private PoetryAlbumEntity getPoetryAlbumEntity(String name) {
        PoetryContentEntity content = worshipEntity.getPoetryContent();
        if (content != null) {
            switch (name) {

                case PoetryAlbumName.PRAY_POETRY -> {
                    return content.getPrayPoetryAlbum();
                }

                case PoetryAlbumName.PRACTISE_POETRY -> {
                    return content.getPractisePoetryAlbum();
                }

                case PoetryAlbumName.WORSHIP_POETRY -> {
                    return content.getWorshipPoetryAlbum();
                }

                case PoetryAlbumName.RESPONSE_POETRY -> {
                    return content.getResponsePoetryAlbum();
                }

                case PoetryAlbumName.OFFERTORY_POETRY -> {
                    return content.getOffertoryPoetryAlbum();
                }

                case PoetryAlbumName.HOLY_COMMUNION_POETRY -> {
                    return content.getHolyCommunionPoetryAlbum();
                }

                default -> {
                    return content.getInitiationPoetryAlbum();
                }
            }
        }
        return null;
    }

    /**
     * 检查新版本
     */
    private void checkVersion() {
        threadPool.execute(() -> {
            GithubReleaseEntity githubReleaseEntity = upgradeService.checkNewRelease();
            if (githubReleaseEntity != null) {
                logger.debug("githubReleaseEntity: " + githubReleaseEntity);
                // 解析响应体
                List<String> remoteInfo = parseReleaseResponse(githubReleaseEntity);
                if (remoteInfo == null){
                    warn("远程版本信息解析错误");
                }
                String[] versionInfo = remoteInfo.get(0).split("\n");
                logger.debug("versionInfo: " + versionInfo);
                String[] bodyInfo = remoteInfo.get(1).split("\n");
                logger.debug("bodyinfo: " + bodyInfo);


                // 上半部分, 显示新版本信息;
                StringBuilder msgBuilder = new StringBuilder("<html><div style=\"padding: 0 20px; " +
                        "font-family: Consolas, PingFang SC, Microsoft YaHei\">");
                for (String s : versionInfo) {
                    msgBuilder.append("<p>").append(s).append("</p>");
                }
                msgBuilder.append("</div></html>");

                JPanel mainPanel = new JPanel(new BorderLayout());
                mainPanel.setBorder(new EmptyBorder(10, 10, 10, 30));
                JTextPane versionPanel = createTextPane(msgBuilder.toString());
                versionPanel.setBorder(new EmptyBorder(0, 0, 20, 0));
                // 监听点击前往下载
                versionPanel.addHyperlinkListener(e -> {
                    if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                        try {
                            Desktop.getDesktop().browse(e.getURL().toURI());
                        } catch (Exception ex) {
                            logger.error("打开浏览器失败", ex);
                        }
                    }
                });

                mainPanel.add(versionPanel, BorderLayout.NORTH);

                // 下半部分, 显示版本新特性
                msgBuilder.setLength(0);
                msgBuilder.append("<html><div style=\"padding: 0 20px; " +
                        "font-family: Consolas, PingFang SC, Microsoft YaHei\")");
                for (String s : bodyInfo) {
                    msgBuilder.append("<p>").append(s).append("</p>");
                }
                msgBuilder.append("</div></html>");

                JTextPane bodyPane = createTextPane(msgBuilder.toString());
                JScrollPane scrollPane = new JScrollPane(bodyPane);
                scrollPane.setPreferredSize(new Dimension(500, 300));
                mainPanel.add(scrollPane, BorderLayout.CENTER);

                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, mainPanel, "新版本提示", JOptionPane.INFORMATION_MESSAGE));
            }
        });
    }

    private List<String> parseReleaseResponse(GithubReleaseEntity githubReleaseEntity) {
        if (githubReleaseEntity == null){
            warn("远程版本信息解析错误");
        }
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        StringBuilder stringBuilder = new StringBuilder();
        // 版本信息
        String versionInfo = stringBuilder.append("发现新版本: ").append(githubReleaseEntity.tag_name()).append("\n")
                .append("-").append("发布于: ").append(simpleDateFormat.format(githubReleaseEntity.created_at())).append("\n")
                .append("-").append("下载地址: <a href='").append(githubReleaseEntity.html_url()).append("'>").append("点击前往下载Windows/macOS Intel/macOS ARM版本").append("</a>").append("\n").toString();
        // 清空
        stringBuilder.setLength(0);
        // 新版本特性
        String bodyInfo = stringBuilder.append(githubReleaseEntity.body()).toString();


        ArrayList<String> releaseInfos = new ArrayList<>(2);

        releaseInfos.add(versionInfo);
        releaseInfos.add(bodyInfo);

        return releaseInfos;
    }

///////////////////////////
// Layout method
///////////////////////////

    /**
     * 向表格添加标题
     *
     * @param tableBox 表格
     * @param title    标题
     */
    private void addTableTitle(Box tableBox, String title) {
        // 放置标题的Label，并居中显示
        JLabel label = new JLabel(title);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        // 为不同的操作系统设置不同的字体
        label.setFont(getBoldFont());

        // 创建一行，并加入到表格中
        Box rowBox = Box.createHorizontalBox();
        rowBox.add(label);
        tableBox.add(rowBox);
    }

    /**
     * 向表格添加表头
     *
     * @param tableBox 表格
     */
    private void addPoetryTableHeader(Box tableBox) {
        Box header = Box.createHorizontalBox();
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(80, 80, 80)));
        setFixedHorizontalBoxSize(header, POETRY_TABLE_WIDTH, TABLE_HEADER_HEIGHT);
        tableBox.add(header);

        addTableColumn(header, "诗歌名称", POETRY_TABLE_COLUMN_WIDTH_1);
        addTableColumn(header, "歌谱路径", POETRY_TABLE_COLUMN_WIDTH_2);
        addTableColumn(header, "操作", POETRY_TABLE_COLUMN_WIDTH_3);
    }

    /**
     * 向诗歌集表格中指定的位置添加一行
     *
     * @param tableBox    表格
     * @param albumName   列名
     * @param poetryIndex 行索引，从0开始
     */
    private void addPoetryTableRow(Box tableBox, String albumName, int poetryIndex) {
        List<JTextField[]> textFieldsList = poetryListMap.get(albumName);
        if (textFieldsList == null) {
            logger.error("无法从poetryListMap中根据{}找到元素", albumName);
            throw new SystemException("系统错误！");
        }
        if (poetryIndex > textFieldsList.size()) {
            logger.error("{}才{}行诗歌，不可在{}处添加一行诗歌", albumName, textFieldsList.size(), poetryIndex);
            throw new SystemException("系统错误！");
        }

        // 添加一行到表格中
        int rowIndex = poetryIndex + getRowIndexOffset(albumName);
        Box rowBox = Box.createHorizontalBox();
        setFixedHorizontalBoxSize(rowBox, POETRY_TABLE_WIDTH, TABLE_ROW_HEIGHT);
        try {
            tableBox.add(rowBox, rowIndex);
        } catch (Exception e) {
            logger.error("在第{}行处添加一行Box失败", rowIndex);
            throw new SystemException("系统错误！", e);
        }

        // 诗歌名称
        JTextField poetryNameTextField = addTableInputTextCell(rowBox, POETRY_TABLE_COLUMN_WIDTH_1);
//        poetryNameTextField.setToolTipText("建议带上书名号《》");

        // 歌谱图的路径
        JTextField poetryDirectoryTextField = addTableInputTextCell(rowBox, POETRY_TABLE_COLUMN_WIDTH_2);
        poetryDirectoryTextField.setToolTipText("该路径文件夹必须只包含此诗歌的歌谱图，歌谱图是用JP-WORD制作的，支持拖拽文件夹");
        poetryDirectoryTextField.setTransferHandler(new TransferHandler() {
            @Override
            public boolean importData(JComponent comp, Transferable t) {
                try {
                    File directory = getDroppedPoetryDirectory(t);
                    if (directory == null || !directory.isDirectory()) {
                        JOptionPane.showMessageDialog(frame, "请拖入一个有效的诗歌目录！", "错误提示", JOptionPane.ERROR_MESSAGE);
                        return false;
                    }

                    String filePath = directory.getCanonicalPath();
                    String formatPoetryName = parsePoetryNameFromPath(filePath);
                    if (formatPoetryName.isEmpty()) {
                        JOptionPane.showMessageDialog(
                                frame,
                                "目录名格式应为“序号-曲名-调式”，例如“001-奇异恩典-C”。",
                                "错误提示",
                                JOptionPane.ERROR_MESSAGE
                        );
                        return false;
                    }

                    logger.debug("poetryDirectory = {}, formatPoetryName = {}", filePath, formatPoetryName);
                    poetryNameTextField.setText(formatPoetryName);
                    poetryDirectoryTextField.setText(filePath);
                    return true;
                } catch (Exception e) {
                    logger.error("诗歌目录拖拽失败！", e);
                    JOptionPane.showMessageDialog(frame, "无法读取拖入的诗歌目录！", "错误提示", JOptionPane.ERROR_MESSAGE);
                    return false;
                }
            }

            @Override
            public boolean canImport(JComponent comp, DataFlavor[] flavors) {
                return Arrays.stream(flavors).anyMatch(WorshipFormServiceImpl.this::isPoetryDirectoryFlavor);
            }
        });

        JTextField[] textFieldArray = new JTextField[]{poetryNameTextField, poetryDirectoryTextField};
        textFieldsList.add(poetryIndex, textFieldArray);

        // 操作按钮
        addPoetryTableOperationCell(rowBox, textFieldsList);
    }

    /**
     * 向表头添加列
     *
     * @param headerBox  表头
     * @param columnName 列名
     * @param width      列的宽度
     */
    private void addTableColumn(Box headerBox, String columnName, int width) {
        JLabel label = new JLabel(columnName);
        label.setBorder(new EmptyBorder(0, PADDING_LEFT, 0, 0));

        JPanel column = new JPanel();
        column.setPreferredSize(new Dimension(width, TABLE_HEADER_HEIGHT));
        column.setMinimumSize(new Dimension(width, TABLE_HEADER_HEIGHT));
        column.setMaximumSize(new Dimension(width, TABLE_HEADER_HEIGHT));
        column.setLayout(new GridBagLayout());// 这种布局可以让文字垂直居中

        // 布局
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 1;

        column.add(label, gbc);
        headerBox.add(column);
    }

    /**
     * 向表格中的一行添加一个输入单元格
     *
     * @param rowBox 表格中的一行
     * @param width  宽度
     * @return 文本框
     */
    private JTextField addTableInputTextCell(Box rowBox, int width) {
        // 输入组件
        JTextField textField = new JTextField();
        textField.setPreferredSize(new Dimension(width - PADDING_LEFT, TEXT_FIELD_HEIGHT));

        leftMiddle(rowBox, textField, width);
        return textField;
    }

    /**
     * 向诗歌表格中指定行添加一个操作单元格
     *
     * @param rowBox         表格中的一行
     * @param textFieldsList 诗歌列表
     */
    private void addPoetryTableOperationCell(Box rowBox, List<JTextField[]> textFieldsList) {
        JButton[] buttons = addOperationButtons(rowBox);
        JButton insertButton = buttons[0];
        JButton deleteButton = buttons[1];
        Box tableBox = (Box) rowBox.getParent();

        insertButton.addActionListener(createPoetryInsertButtonActionListener(tableBox, rowBox));
        deleteButton.addActionListener(createPoetryDeleteButtonActionListener(tableBox, rowBox, textFieldsList));
    }

    /**
     * 添加一行输入
     *
     * @param tableBox  根容器箱子
     * @param labelName 标签文本
     * @return 文本框
     */
    private JTextField addRegularTableInputRow(Box tableBox, String labelName) {
        Box rowBox = Box.createHorizontalBox();
        tableBox.add(rowBox);

        JLabel label = addRegularTableInputLabel(rowBox, labelName);
        JTextField textField = addTableInputTextCell(rowBox, REGULAR_TABLE_RIGHT_WIDTH);
        label.setLabelFor(textField);

        return textField;
    }

    /**
     * 添加一行可动态增删的读经章节输入框。
     *
     * <p>读经章节按列表顺序保存，生成 PPT 时也按相同顺序解析和拼接。
     * 删除操作至少保留一行，避免读经面板失去可编辑入口。</p>
     *
     * @param tableBox 经文面板表格容器
     * @param index    章节列表索引
     */
    private void addReadingScriptureRow(Box tableBox, int index) {
        Box rowBox = Box.createHorizontalBox();
        int rowIndex = Math.min(index + 3, tableBox.getComponentCount());
        tableBox.add(rowBox, rowIndex);

        JLabel label = addRegularTableInputLabel(
                rowBox,
                index == 0 ? ScriptureContentKey.READING_SCRIPTURE : "");
        int operationWidth = BUTTON_WIDTH * 2 + PADDING_LEFT * 2;
        int inputWidth = REGULAR_TABLE_RIGHT_WIDTH - operationWidth;
        JTextField textField = addTableInputTextCell(rowBox, inputWidth);
        label.setLabelFor(textField);
        readingScriptureTextFieldList.add(index, textField);
        readingScriptureLabelList.add(index, label);

        JButton insertButton = createOperationButton("+");
        JButton deleteButton = createOperationButton("−");
        deleteButton.setBackground(new Color(245, 101, 81));
        insertButton.setToolTipText("在当前读经章节下面增加一行");
        deleteButton.setToolTipText("删除当前读经章节");

        Box operationBox = Box.createHorizontalBox();
        operationBox.add(insertButton);
        operationBox.add(Box.createHorizontalStrut(PADDING_LEFT));
        operationBox.add(deleteButton);
        leftMiddle(rowBox, operationBox, operationWidth);

        insertButton.addActionListener(event -> {
            int currentIndex = getIndexOfRowBox(tableBox, rowBox);
            if (currentIndex != -1) {
                int readingIndex = currentIndex - 3;
                addReadingScriptureRow(tableBox, readingIndex + 1);
                tableBox.revalidate();
                tableBox.repaint();
            }
        });
        deleteButton.addActionListener(event -> {
            if (readingScriptureTextFieldList.size() == 1) {
                JOptionPane.showMessageDialog(
                        tableBox.getRootPane(),
                        "读经至少保留一行输入框！",
                        "提示",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            int currentIndex = getIndexOfRowBox(tableBox, rowBox);
            if (currentIndex != -1) {
                int readingIndex = currentIndex - 3;
                readingScriptureTextFieldList.remove(readingIndex);
                readingScriptureLabelList.remove(readingIndex);
                tableBox.remove(rowBox);
                if (readingIndex == 0) {
                    readingScriptureLabelList.get(0).setText(ScriptureContentKey.READING_SCRIPTURE);
                }
                tableBox.revalidate();
                tableBox.repaint();
            }
        });
    }

    /**
     * 向子区域添加一行输入框。
     *
     * <p>主领和回应只增加少量左侧缩进，字号和颜色沿用原有输入标签样式，
     * 从而通过位置体现层级，而不引入新的字体规格。</p>
     *
     * @param tableBox  根容器箱子
     * @param labelName 标签文本
     * @return 文本框
     */
    private JTextField addSubsectionInputRow(Box tableBox, String labelName) {
        Box rowBox = Box.createHorizontalBox();
        tableBox.add(rowBox);

        JLabel label = addSubsectionInputLabel(rowBox, labelName);
        JTextField textField = addTableInputTextCell(rowBox, REGULAR_TABLE_RIGHT_WIDTH);
        label.setLabelFor(textField);
        return textField;
    }

    /**
     * 向宣召分组中添加一行输入框。
     *
     * <p>宣召分组本身已经占用了一个常规标签列，因此主领和回应行的
     * 输入框使用右侧剩余宽度。内部标签列略窄，可以减少标签与输入框
     * 之间的视觉距离，同时保持输入框右边界与普通经文输入框对齐。</p>
     *
     * @param contentBox 宣召分组右侧的内容容器
     * @param labelName  输入行标签
     * @return 新建的输入框
     */
    private JTextField addSummonInputRow(Box contentBox, String labelName) {
        Box rowBox = Box.createHorizontalBox();
        contentBox.add(rowBox);

        JLabel label = addSummonInputLabel(rowBox, labelName);
        int inputWidth = REGULAR_TABLE_RIGHT_WIDTH - SUMMON_LABEL_WIDTH;
        JTextField textField = addTableInputTextCell(rowBox, inputWidth);
        label.setLabelFor(textField);
        return textField;
    }

    /**
     * 添加宣召内部的次级标签。
     *
     * @param rowBox    当前输入行
     * @param labelName 标签文本
     * @return 次级标签
     */
    private JLabel addSummonInputLabel(Box rowBox, String labelName) {
        JLabel label = new JLabel(labelName);
        label.setBorder(new EmptyBorder(0, 8, 0, 0));
        leftMiddle(rowBox, label, SUMMON_LABEL_WIDTH);
        return label;
    }

    /**
     * 添加宣召分组左侧的标题。
     *
     * <p>标题沿用经文面板现有标签的字号和对齐方式，仅通过固定列宽
     * 保证它位于主领、回应两行的左侧，而不是独占一行。</p>
     *
     * @param sectionBox 宣召分组容器
     * @param title      分组标题
     */
    private void addSummonSectionLabel(Box sectionBox, String title) {
        JLabel label = new JLabel(title);
        leftMiddle(sectionBox, label, REGULAR_TABLE_LEFT_WIDTH);
    }

    /**
     * 添加子区域输入标签。
     *
     * @param rowBox    当前输入行
     * @param labelName 标签文本
     * @return 次级标题标签
     */
    private JLabel addSubsectionInputLabel(Box rowBox, String labelName) {
        JLabel label = new JLabel(labelName);
        label.setBorder(new EmptyBorder(0, 12, 0, 0));
        leftMiddle(rowBox, label, REGULAR_TABLE_LEFT_WIDTH);
        return label;
    }

    /**
     * 添加经文面板内部的子标题。
     *
     * @param tableBox 根容器箱子
     * @param title    子标题文本
     */
    private void addSubsectionTitle(Box tableBox, String title) {
        Box rowBox = Box.createHorizontalBox();
        JLabel label = new JLabel(title);
        label.setBorder(new EmptyBorder(4, 0, 4, 0));
        leftMiddle(rowBox, label, REGULAR_TABLE_LEFT_WIDTH);
        rowBox.add(label);
        tableBox.add(rowBox);
    }

    /**
     * 向常规表格中指定行添加输入标签
     *
     * @param rowBox    表格中的一行
     * @param labelName 标签文本
     * @return 标签
     */
    private JLabel addRegularTableInputLabel(Box rowBox, String labelName) {
        JLabel label = new JLabel(labelName);
        leftMiddle(rowBox, label, REGULAR_TABLE_LEFT_WIDTH);
        return label;
    }

    /**
     * 添加家事报告面板添加一行
     *
     * @param tableBox          表格箱子
     * @param familyReportIndex 添加未知，从0开始
     */
    private void addFamilyReportsTableInputRow(Box tableBox, int familyReportIndex) {
        Box rowBox = Box.createHorizontalBox();
        tableBox.add(rowBox, familyReportIndex + ROW_INDEX_OFFSET);

        JTextField textField = addTableInputTextCell(rowBox, REGULAR_TABLE_RIGHT_WIDTH);
        familyReportsTextFieldList.add(familyReportIndex, textField);

        addFamilyReportTableOperationCell(rowBox);
    }

    // 添加家事报告操作单元格
    private void addFamilyReportTableOperationCell(Box rowBox) {
        JButton[] buttons = addOperationButtons(rowBox);
        JButton insertButton = buttons[0];
        JButton deleteButton = buttons[1];
        Box tableBox = (Box) rowBox.getParent();

        insertButton.addActionListener((action) -> run(() -> {
            int currentIndex = getIndexOfRowBox(tableBox, rowBox);
            if (currentIndex != -1) {
                int nextIndex = currentIndex - ROW_INDEX_OFFSET + 1;
                addFamilyReportsTableInputRow(tableBox, nextIndex);
                tableBox.getRootPane().revalidate();
            }
        }));
        deleteButton.addActionListener((action) -> run(() -> {
            if (familyReportsTextFieldList.size() == 1) {
                JOptionPane.showMessageDialog(
                        tableBox.getRootPane(),
                        "请保留这一行！",
                        "提示",
                        JOptionPane.WARNING_MESSAGE);
            } else {
                int currentIndex = getIndexOfRowBox(tableBox, rowBox);
                if (currentIndex != -1) {
                    familyReportsTextFieldList.remove(currentIndex - ROW_INDEX_OFFSET);
                    tableBox.remove(rowBox);
                    tableBox.getRootPane().revalidate();
                }
            }
        }));
    }

    // 添加操作按钮
    private JButton[] addOperationButtons(Box rowBox) {
        // 仅保留新增和删除操作，避免操作区过于拥挤。
        JButton insertButton = createOperationButton("+");
        JButton deleteButton = createOperationButton("−");

        insertButton.setToolTipText("在这行下面插入一行");
        deleteButton.setToolTipText("删除当前行");

        deleteButton.setBackground(new Color(245, 101, 81));

        Box hBox = Box.createHorizontalBox();
        hBox.add(insertButton);
        hBox.add(Box.createHorizontalStrut(PADDING_LEFT));
        hBox.add(deleteButton);

        leftMiddle(rowBox, hBox, POETRY_OPERATION_COLUMN_WIDTH);

        return new JButton[]{insertButton, deleteButton};
    }

    /**
     * 创建行操作按钮，并统一应用紧凑的圆角方块样式。
     *
     * @param text 按钮图标文本
     * @return 已完成样式设置的按钮
     */
    private JButton createOperationButton(String text) {
        JButton button = new RoundedOperationButton(text);
        button.setPreferredSize(new Dimension(BUTTON_WIDTH, BUTTON_WIDTH));
        button.setMinimumSize(new Dimension(BUTTON_WIDTH, BUTTON_WIDTH));
        button.setMaximumSize(new Dimension(BUTTON_WIDTH, BUTTON_WIDTH));
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(88, 96, 102));
        button.setBorder(new EmptyBorder(0, 0, 0, 0));
        button.setText("");
        button.setIcon(new OperationSymbolIcon(text));
        return button;
    }

    /**
     * 操作按钮的自绘实现，避免不同 Swing 外观覆盖圆角背景。
     */
    private static class RoundedOperationButton extends JButton {
        private RoundedOperationButton(String text) {
            super(text);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color background = getBackground();
                if (getModel().isPressed()) {
                    background = background.darker();
                } else if (getModel().isRollover()) {
                    background = background.brighter();
                }
                graphics2D.setColor(background);
                graphics2D.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 7, 7);
                graphics2D.setColor(new Color(110, 118, 124));
                graphics2D.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 7, 7);
            } finally {
                graphics2D.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    /**
     * 添加感恩敬拜经文选择面板，放置在原领餐名单面板的位置。
     *
     * @param rootBox 根容器
     */
    private void addThanksgivingScripturePanel(Box rootBox) {
        Box tableBox = Box.createVerticalBox();
        addTableTitle(tableBox, "感恩敬拜经文");

        String selectedScripture = worshipEntity.getThanksgivingScripture();
        if (isEmpty(selectedScripture)) {
            selectedScripture = ThanksgivingScripture.CORINTHIANS_AND_ROMANS;
        }

        Box optionsBox = Box.createHorizontalBox();
        optionsBox.add(new JLabel("经文："));
        optionsBox.add(Box.createHorizontalStrut(PADDING_LEFT));
        ButtonGroup scriptureGroup = new ButtonGroup();
        addThanksgivingScriptureRadio(optionsBox, scriptureGroup,
                ThanksgivingScripture.CORINTHIANS_AND_ROMANS, selectedScripture);
        addThanksgivingScriptureRadio(optionsBox, scriptureGroup,
                ThanksgivingScripture.MALACHI, selectedScripture);
        tableBox.add(optionsBox);

        JPanel panel = new JPanel();
        panel.add(tableBox);
        rootBox.add(panel);
    }

    /** 添加感恩敬拜经文单选项，并同步保存当前选择。 */
    private void addThanksgivingScriptureRadio(
            Box optionsBox, ButtonGroup scriptureGroup, String scripture, String selectedScripture) {
        JRadioButton radio = new JRadioButton(scripture, scripture.equals(selectedScripture));
        scriptureGroup.add(radio);
        radio.addActionListener(event -> worshipEntity.setThanksgivingScripture(scripture));
        optionsBox.add(radio);
        optionsBox.add(Box.createHorizontalStrut(PADDING_LEFT));
    }

    /** 使用 Java2D 绘制居中的加号和减号，避免字体基线造成视觉偏移。 */
    private static class OperationSymbolIcon implements Icon {
        private static final int ICON_SIZE = 18;
        private final String symbol;

        private OperationSymbolIcon(String symbol) {
            this.symbol = symbol;
        }

        @Override
        public int getIconWidth() {
            return ICON_SIZE;
        }

        @Override
        public int getIconHeight() {
            return ICON_SIZE;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            try {
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics2D.setColor(Color.WHITE);
                graphics2D.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
                int centerX = x + ICON_SIZE / 2;
                int centerY = y + ICON_SIZE / 2;
                graphics2D.drawLine(x + 3, centerY, x + ICON_SIZE - 3, centerY);
                if ("+".equals(symbol)) {
                    graphics2D.drawLine(centerX, y + 3, centerX, y + ICON_SIZE - 3);
                }
            } finally {
                graphics2D.dispose();
            }
        }
    }

    // 水平居左，垂直居中
    private void leftMiddle(Container container, Component component, int width) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 1;

        JPanel cell = new JPanel();
        cell.setPreferredSize(new Dimension(width, TABLE_ROW_HEIGHT));
        cell.setMinimumSize(new Dimension(width, TABLE_ROW_HEIGHT));
        cell.setMaximumSize(new Dimension(width, TABLE_ROW_HEIGHT));
        cell.setLayout(new GridBagLayout());
        cell.setBorder(new EmptyBorder(0, PADDING_LEFT, 0, 0));
        cell.add(component, gbc);
        container.add(cell);
    }

    /**
     * 固定诗歌表头和数据行的横向尺寸，避免 BoxLayout 按父容器宽度重新分配列宽。
     *
     * @param box    需要固定尺寸的横向容器
     * @param width  容器宽度
     * @param height 容器高度
     */
    private void setFixedHorizontalBoxSize(Box box, int width, int height) {
        Dimension size = new Dimension(width, height);
        box.setMinimumSize(size);
        box.setPreferredSize(size);
        box.setMaximumSize(size);
    }

    /**
     * 创建诗歌集面板中插入按钮的动作监听器
     *
     * @param tableBox 表格箱子布局
     * @param rowBox   水平箱子布局
     * @return 动作监听器
     */
    private ActionListener createPoetryInsertButtonActionListener(Box tableBox, Box rowBox) {
        return (action) -> run(() -> {
            int currentIndex = getIndexOfRowBox(tableBox, rowBox);
            if (currentIndex != -1) {
                String albumName = tableBox.getParent().getName();
                int nextIndex = currentIndex - getRowIndexOffset(albumName) + 1;
                addPoetryTableRow(tableBox, albumName, nextIndex);
                tableBox.getRootPane().revalidate();
            }
        });
    }

    /**
     * 创建诗歌集面板中删除按钮的动作监听器
     *
     * @param tableBox       表格箱子布局
     * @param rowBox         水平箱子布局
     * @param textFieldsList 诗歌输入框列表
     * @return 动作监听器
     */
    private ActionListener createPoetryDeleteButtonActionListener(Box tableBox, Box rowBox, List<JTextField[]> textFieldsList) {
        return (action) -> run(() -> {
            if (textFieldsList.size() == 1) {
                JOptionPane.showMessageDialog(
                        tableBox.getRootPane(),
                        "请保留这一行！",
                        "提示",
                        JOptionPane.WARNING_MESSAGE);
            } else {
                int currentIndex = getIndexOfRowBox(tableBox, rowBox);
                if (currentIndex != -1) {
                    String albumName = tableBox.getParent().getName();
                    int index = currentIndex - getRowIndexOffset(albumName);
                    logger.debug("删除第{}行诗歌", index + 1);
                    textFieldsList.remove(index);
                    tableBox.remove(currentIndex);
                    tableBox.getRootPane().revalidate();
                }
            }
        });
    }

    /**
     * 创建诗歌集面板中清空按钮的动作监听器
     *
     * @param tableBox       表格箱子布局
     * @param rowBox         水平箱子布局
     * @param textFieldsList 诗歌输入框列表
     * @return 动作监听器
     */
    /**
     * 获取{@code rowBox}在{@code tableBox}中的位置
     *
     * @param tableBox 垂直方向的箱子布局
     * @param rowBox   水平方向的箱子布局
     * @return 索引位置，从0开始
     */
    private int getIndexOfRowBox(Box tableBox, Box rowBox) {
        Component[] rows = tableBox.getComponents();
        for (int i = 0; i < rows.length; i++) {
            if (rows[i] == rowBox) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 创建一个可以选择复制的文本域
     *
     * @param text 字符串
     * @return 文本域
     */
    private JTextPane createTextPane(String text) {
        JTextPane f = new JTextPane();
        f.setContentType("text/html");
        f.setText(text);
        f.setEditable(false);
        f.setBackground(null);
        f.setBorder(null);
        StyleSheet styleSheet = ((HTMLDocument) f.getDocument()).getStyleSheet();
        styleSheet.addRule("a { text-decoration: none; }");
        return f;
    }

///////////////////////////
// Helpful method
///////////////////////////

    /**
     * 字符串是否为空
     *
     * @param str 字符串
     * @return 布尔值
     */
    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 计算诗歌行在 tableBox 中的索引偏移量
     * 敬拜诗歌因标题和表头之间插入了模式选项框，偏移量为3；其他诗歌为2
     */
    private static int getRowIndexOffset(String albumName) {
        return PoetryAlbumName.WORSHIP_POETRY.equals(albumName) ? 3 : 2;
    }

    /**
     * 弹出警告框
     *
     * @param message 警告信息
     */
    private void warn(String message) {
        JOptionPane.showMessageDialog(frame, message, "提示", JOptionPane.WARNING_MESSAGE);
    }

    /** 提取异常链中最具体的可读消息，避免 GUI 只显示“未知异常”。 */
    private String getExceptionReason(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return throwable.getClass().getSimpleName();
    }

///////////////////////////
// System method
///////////////////////////

    /**
     * 获取窗体的图标
     *
     * @return 图标
     */
    private ImageIcon getImageIcon() {
        ClassLoader classLoader = getClass().getClassLoader();
        URL url = classLoader.getResource("icon.jpg");
        if (url == null) {
            throw new SystemException("找不到图标！");
        }
        return new ImageIcon(url);
    }

    private ImageIcon getImageIcon(String path) {
        ClassLoader classLoader = getClass().getClassLoader();
        URL url = classLoader.getResource(path);
        if (url == null) {
            throw new SystemException("找不到图标！");
        }
        return new ImageIcon(url);
    }

    /**
     * 获取粗体字体
     *
     * @return 字体
     */
    private Font getBoldFont() {
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            return new Font("微软雅黑 Light", Font.BOLD, 15);
        } else {
            return new Font("PingFang SC", Font.BOLD, 16);
        }
    }

    /**
     * 异步执行GUI事件，将事件放在EDT中执行
     *
     * @param runnable 可执行对象
     */
    private void run(Runnable runnable) {
        threadPool.execute(() -> SwingUtilities.invokeLater(runnable));
    }

    private boolean isPoetryDirectoryFlavor(DataFlavor flavor) {
        return DataFlavor.javaFileListFlavor.equals(flavor) || flavor.isFlavorTextType();
    }

    private File getDroppedPoetryDirectory(Transferable transferable) throws Exception {
        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            try {
                Object data = transferable.getTransferData(DataFlavor.javaFileListFlavor);
                if (data instanceof List<?>) {
                    List<?> files = (List<?>) data;
                    for (Object item : files) {
                        if (item instanceof File && ((File) item).isDirectory()) {
                            return (File) item;
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("无法通过文件列表读取拖拽目录，尝试文本格式", e);
            }
        }

        for (DataFlavor flavor : transferable.getTransferDataFlavors()) {
            if (!flavor.isFlavorTextType()) {
                continue;
            }
            try (Reader reader = flavor.getReaderForText(transferable);
                 BufferedReader bufferedReader = new BufferedReader(reader)) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    String candidate = line.trim();
                    if (candidate.isEmpty() || candidate.startsWith("#")) {
                        continue;
                    }
                    File directory = null;
                    if (candidate.regionMatches(true, 0, "file:", 0, 5)) {
                        URI uri = new URI(candidate);
                        if ("file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
                            directory = new File(uri.getPath());
                        }
                    } else {
                        directory = new File(candidate);
                    }
                    if (directory != null && directory.isDirectory()) {
                        return directory;
                    }
                }
            } catch (Exception e) {
                logger.warn("无法通过拖拽文本格式 {} 读取目录", flavor.getMimeType(), e);
            }
        }
        return null;
    }

    /**
     * 从目录名中提取诗歌名称，格式为 "调式《曲名》"
     */
    private String parsePoetryNameFromPath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return "";
        }

        String directoryName = new File(filePath.trim()).getName();
        int firstHyphen = directoryName.indexOf('-');
        int lastHyphen = directoryName.lastIndexOf('-');
        if (firstHyphen < 0 || lastHyphen <= firstHyphen + 1 || lastHyphen >= directoryName.length() - 1) {
            return "";
        }

        String poetryName = directoryName.substring(firstHyphen + 1, lastHyphen).trim();
        String poetryKey = directoryName.substring(lastHyphen + 1).trim();
        if (poetryName.isEmpty() || poetryKey.isEmpty()) {
            return "";
        }

        logger.info("poetryDirectoryName = {}", directoryName);
        return poetryKey + "调《" + poetryName + "》";
    }

    /**
     * 将敬拜实体序列化写入到本地磁盘默认位置
     *
     * @param worshipEntity 敬拜实体
     */
    private void saveWorshipEntity(WorshipEntity worshipEntity) {
        String appDirPath = System.getProperty("user.home") + File.separator + SystemConfig.APP_CONFIG_DIR_PATH;
        File file = new File(appDirPath, WorshipEntity.class.getSimpleName());
        if (file.exists()) {
            logger.debug("目标路径已存在缓存的的敬拜实体序列化文件");
            if (file.delete()) {
                logger.debug("已删除缓存的的敬拜实体序列化文件");
            } else {
                logger.warn("缓存的的敬拜实体序列化文件删除失败！");
                return;
            }
        }
        // 重新写入 TODO
        logger.info("将要保存的敬拜实体信息：" + worshipEntity.toString());
        try (OutputStream os = new FileOutputStream(file);
             ObjectOutputStream oos = new ObjectOutputStream(os)) {
            oos.writeObject(worshipEntity);
        } catch (Exception e) {
            logger.warn("无法序列化敬拜实体！", e);
        }
    }

    /**
     * 从本地磁盘默认位置读取敬拜实体，填充面板
     *
     * @return 敬拜实体
     */
    private WorshipEntity readWorshipEntity() {
        String appDirPath = System.getProperty("user.home") + File.separator + SystemConfig.APP_CONFIG_DIR_PATH;
        // 测试打印
        logger.debug("敬拜实体信息的路径：" + appDirPath);
        File file = new File(appDirPath, WorshipEntity.class.getSimpleName());
        if (!file.exists()) {
            return null;
        }
        try (InputStream is = new FileInputStream(file);
             ObjectInputStream ois = new ObjectInputStream(is)) {
            WorshipEntity savedWorshipEntity = (WorshipEntity) ois.readObject();
            logger.info("读取到的敬拜实体信息：" + savedWorshipEntity.toString());
            return savedWorshipEntity;
        } catch (Exception e) {
            logger.warn("无法读取敬拜实体！", e);
        }
        return null;
    }

}
