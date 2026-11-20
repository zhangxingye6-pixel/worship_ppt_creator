package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.Dict;
import claygminx.worshipppt.common.config.SystemConfig;
import claygminx.worshipppt.common.config.UpdateProxyConfig;
import claygminx.worshipppt.common.entity.GithubReleaseAssetEntity;
import claygminx.worshipppt.common.entity.GithubReleaseEntity;
import claygminx.worshipppt.util.UpdateNetworkSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.net.ssl.SSLException;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 应用内更新下载窗口。
 *
 * <p>组件与状态只在 Swing 事件线程更新，网络及磁盘操作交给后台任务。
 * 此阶段仅下载完整安装包，安装目录、配置、母版和数据库均不做替换。</p>
 */
public class UpgradeDialog extends JDialog {
    private static final Logger logger = LoggerFactory.getLogger(UpgradeDialog.class);
    private static final String NETWORK_NOTICE = "下载需要访问 GitHub。\n"
            + "如果当前网络无法稳定连接 GitHub，请先连接可用的网络代理。\n"
            + "在下载窗口选择 HTTP 或 SOCKS，仅需填写本机代理端口。\n"
            + "填写的设置会自动保存；端口留空时读取已保存设置。\n\n是否开始下载？";

    private final GithubReleaseEntity release;
    private final String owner = SystemConfig.getString(Dict.GithubProperty.OWNER);
    private final String repository = SystemConfig.getString(Dict.GithubProperty.REPO);
    private final JButton downloadButton = new JButton("下载更新");
    private final JButton closeButton = new JButton("稍后");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JTextArea status = createReadOnlyArea("");
    private final JComboBox<String> proxyType = new JComboBox<>(new String[]{"HTTP", "SOCKS"});
    private final JTextField proxyPort = new JTextField(
            SystemConfig.getString(Dict.UpdateProperty.PROXY_PORT), 6);
    private GithubReleaseAssetEntity asset;
    private volatile ReleaseDownloadService downloadService;
    private final AtomicBoolean cancellationRequested = new AtomicBoolean();
    private Path downloadedFile;
    private boolean downloading;
    private boolean closeAfterCancellation;

    /**
     * 创建非模态更新提示，不影响用户继续制作 PPT。
     *
     * @param parent 主程序窗口
     * @param release 检测到的新正式版本
     */
    public UpgradeDialog(Frame parent, GithubReleaseEntity release) {
        super(parent, "新版本提示", false);
        this.release = release;
        String configuredType = SystemConfig.getString(Dict.UpdateProperty.PROXY_TYPE).trim().toUpperCase(Locale.ROOT);
        proxyType.setSelectedItem("SOCKS".equals(configuredType) ? "SOCKS" : "HTTP");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel heading = new JPanel(new BorderLayout(0, 6));
        JTextArea version = createReadOnlyArea("发现新版本：" + release.tag_name()
                + (release.created_at() == null ? "" : "\n发布时间："
                + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(release.created_at())));
        heading.add(version, BorderLayout.NORTH);
        JTextArea notes = createReadOnlyArea(release.body() == null || release.body().isBlank()
                ? "此版本未提供更新说明。" : release.body());
        notes.setOpaque(true);
        notes.setCaretPosition(0);
        JScrollPane scrollPane = new JScrollPane(notes);
        scrollPane.setPreferredSize(new Dimension(540, 230));
        heading.add(scrollPane, BorderLayout.CENTER);
        content.add(heading, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 10));
        status.setRows(4);
        JScrollPane statusScroll = new JScrollPane(status);
        statusScroll.setBorder(BorderFactory.createEmptyBorder());
        statusScroll.setPreferredSize(new Dimension(540, 84));
        JPanel downloadInfo = new JPanel(new BorderLayout(0, 8));
        JPanel proxyRow = new JPanel(new BorderLayout(8, 0));
        JLabel proxyLabel = new JLabel("下载代理");
        proxyLabel.setLabelFor(proxyType);
        proxyType.setToolTipText("选择本机代理软件提供的协议类型");
        proxyPort.putClientProperty("JTextField.placeholderText", "端口号");
        proxyPort.setToolTipText("仅输入本机代理端口（1 至 65535）；自动保存，留空使用已保存设置");
        JPanel proxyInputs = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        proxyInputs.add(proxyType);
        JPanel portRow = new JPanel(new BorderLayout(8, 0));
        portRow.setBorder(new EmptyBorder(0, 8, 0, 0));
        JLabel portLabel = new JLabel("端口");
        portLabel.setLabelFor(proxyPort);
        portRow.add(portLabel, BorderLayout.WEST);
        portRow.add(proxyPort, BorderLayout.CENTER);
        proxyInputs.add(portRow);
        proxyRow.add(proxyLabel, BorderLayout.WEST);
        proxyRow.add(proxyInputs, BorderLayout.CENTER);
        downloadInfo.add(proxyRow, BorderLayout.NORTH);
        downloadInfo.add(statusScroll, BorderLayout.CENTER);
        bottom.add(downloadInfo, BorderLayout.NORTH);
        progressBar.setStringPainted(true);
        progressBar.setString("尚未下载");
        bottom.add(progressBar, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton releaseButton = new JButton("前往发布页");
        releaseButton.addActionListener(event -> openReleasePage());
        downloadButton.addActionListener(event -> {
            if (downloadedFile != null) {
                openDirectory();
            } else {
                confirmAndDownload();
            }
        });
        closeButton.addActionListener(event -> {
            if (downloading) {
                cancelDownload(false);
            } else {
                dispose();
            }
        });
        actions.add(releaseButton);
        actions.add(closeButton);
        actions.add(downloadButton);
        bottom.add(actions, BorderLayout.SOUTH);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (downloading) {
                    cancelDownload(true);
                } else {
                    dispose();
                }
            }
        });
        selectAsset();
        pack();
        setMinimumSize(new Dimension(520, 400));
        setLocationRelativeTo(parent);
    }

    /** 先验证平台附件和摘要，避免用户开始下载后才发现无法使用。 */
    private void selectAsset() {
        try {
            asset = new ReleaseDownloadService(owner, repository).selectAsset(
                    release, System.getProperty("os.name"), System.getProperty("os.arch"));
            status.setText("安装包：" + asset.name() + "\n大小：" + formatBytes(asset.size()));
        } catch (IOException e) {
            status.setText(e.getMessage());
            downloadButton.setEnabled(false);
        }
    }

    /** 每次开始或重试之前提示网络要求，由用户确认后才连接下载服务器。 */
    private void confirmAndDownload() {
        if (downloading || asset == null) {
            return;
        }
        String enteredType = (String) proxyType.getSelectedItem();
        String enteredPort = proxyPort.getText().trim();
        try {
            UpdateNetworkSupport.localProxySetting(enteredType, enteredPort);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "代理配置有误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int choice = JOptionPane.showOptionDialog(this, NETWORK_NOTICE, "下载前的网络提示",
                JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
                new String[]{"开始下载", "暂不下载"}, "开始下载");
        if (choice != 0) {
            return;
        }
        downloading = true;
        cancellationRequested.set(false);
        downloadService = null;
        proxyType.setEnabled(false);
        proxyPort.setEnabled(false);
        downloadButton.setEnabled(false);
        closeButton.setText("取消下载");
        closeButton.setEnabled(true);
        progressBar.setValue(0);
        progressBar.setIndeterminate(true);
        progressBar.setString("正在连接 GitHub");
        status.setText("正在下载安装包，请保持网络连接。");
        new SwingWorker<Path, Long>() {
            @Override
            protected Path doInBackground() throws IOException {
                UpdateProxyConfig proxyConfig = new UpdateProxyConfig(
                        Path.of(SystemConfig.USER_CONFIG_FILE_PATH), SystemConfig.properties);
                UpdateProxyConfig.Settings settings = proxyConfig.resolve(enteredType, enteredPort);
                if (cancellationRequested.get()) {
                    throw new CancellationException("下载已取消");
                }
                if (!enteredPort.isBlank()) {
                    try {
                        proxyConfig.save(settings);
                    } catch (IOException e) {
                        throw new IOException("无法保存代理设置，请检查配置文件的写入权限后重试", e);
                    }
                }
                SwingUtilities.invokeLater(() -> {
                    proxyType.setSelectedItem(settings.type());
                    proxyPort.setText(settings.port());
                });
                ReleaseDownloadService operation = new ReleaseDownloadService(owner, repository,
                        settings.proxySetting(), route -> {
                            logger.info("下载请求路由：{}", route);
                            SwingUtilities.invokeLater(() -> {
                                if (downloading && closeButton.isEnabled()) {
                                    status.setText("连接方式：" + route);
                                }
                            });
                        });
                downloadService = operation;
                if (cancellationRequested.get()) {
                    operation.cancel();
                }
                Path root = Path.of(System.getProperty("user.home"), SystemConfig.APP_CONFIG_DIR_PATH, "updates");
                return operation.download(release, asset, root, received -> publish(received));
            }

            @Override
            protected void process(List<Long> chunks) {
                if (!downloading || closeAfterCancellation || !closeButton.isEnabled()) {
                    return;
                }
                long received = chunks.get(chunks.size() - 1);
                progressBar.setIndeterminate(false);
                progressBar.setValue((int) (100.0 * received / asset.size()));
                progressBar.setString(formatBytes(received) + " / " + formatBytes(asset.size()));
                if (received == asset.size()) {
                    progressBar.setString("正在校验安装包");
                }
            }

            @Override
            protected void done() {
                downloading = false;
                proxyType.setEnabled(true);
                proxyPort.setEnabled(true);
                progressBar.setIndeterminate(false);
                closeButton.setEnabled(true);
                closeButton.setText("关闭");
                downloadButton.setEnabled(true);
                try {
                    downloadedFile = get();
                    progressBar.setValue(100);
                    progressBar.setString("下载完成，SHA-256 校验通过");
                    downloadButton.setText("打开下载目录");
                    status.setText("已保存：" + downloadedFile
                            + "\n请退出程序后解压安装；替换前请备份个人配置和 PPT 母版。");
                    if (!closeAfterCancellation) {
                        openDirectory();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showDownloadFailure(e);
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof CancellationException) {
                        progressBar.setValue(0);
                        progressBar.setString("下载已取消");
                        status.setText("未完成的文件已清理，可以重新下载。");
                        downloadButton.setText("重新下载");
                    } else {
                        showDownloadFailure(e.getCause());
                    }
                }
                if (closeAfterCancellation) {
                    dispose();
                }
            }
        }.execute();
    }

    private void showDownloadFailure(Throwable error) {
        logger.error("下载安装包失败", error);
        progressBar.setValue(0);
        progressBar.setString("下载失败");
        String reason;
        if (error instanceof SocketTimeoutException) {
            reason = "连接或读取 GitHub 超时";
        } else if (error instanceof UnknownHostException) {
            reason = "无法解析下载服务器域名，请检查 DNS 或网络连接";
        } else if (error instanceof SSLException) {
            reason = "无法建立安全连接，请检查系统时间、证书或代理设置";
        } else if (error instanceof ConnectException) {
            reason = "无法连接下载服务器";
        } else {
            reason = error.getMessage();
        }
        status.setText("下载失败：" + (reason == null || reason.isBlank()
                ? error.getClass().getSimpleName() : reason)
                + "\n请检查网络、代理是否对本程序生效，以及本地存储空间，然后重试；也可前往发布页下载。");
        downloadButton.setText("重新下载");
    }

    private void cancelDownload(boolean closeWhenStopped) {
        cancellationRequested.set(true);
        closeAfterCancellation = closeWhenStopped;
        closeButton.setEnabled(false);
        progressBar.setString("正在取消并清理文件");
        // 不调用 SwingWorker.cancel：等待连接及临时文件清理结束后再允许重试。
        ReleaseDownloadService operation = downloadService;
        if (operation == null) {
            return;
        }
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                operation.cancel();
                return null;
            }
        }.execute();
    }

    private void openDirectory() {
        performDesktopAction(() -> {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                throw new IOException("当前系统不支持自动打开目录");
            }
            Desktop.getDesktop().open(downloadedFile.getParent().toFile());
        }, "无法打开下载目录，请根据窗口中的保存路径查找安装包。");
    }

    private void openReleasePage() {
        performDesktopAction(() -> {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new IOException("当前系统不支持自动打开浏览器");
            }
            String tag = release.tag_name();
            String suffix = tag != null && tag.matches("v[0-9]+\\.[0-9]+\\.[0-9]+") ? "/tag/" + tag : "";
            Desktop.getDesktop().browse(URI.create("https://github.com/" + owner + "/" + repository + "/releases" + suffix));
        }, "无法打开浏览器，请手动访问项目的 GitHub Releases 页面。");
    }

    /** 桌面打开操作也放在后台，避免系统文件管理器或浏览器阻塞 UI。 */
    private void performDesktopAction(DesktopAction action, String failureMessage) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws IOException {
                action.run();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    logger.warn(failureMessage, e.getCause());
                    JOptionPane.showMessageDialog(UpgradeDialog.this, failureMessage, "提示", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private static JTextArea createReadOnlyArea(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        // 说明区域仅展示信息，避免获得输入焦点或显示文本输入光标。
        area.setFocusable(false);
        area.setCursor(Cursor.getDefaultCursor());
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFont(UIManager.getFont("Label.font"));
        return area;
    }

    private static String formatBytes(long bytes) {
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    @FunctionalInterface
    private interface DesktopAction {
        void run() throws IOException;
    }
}
