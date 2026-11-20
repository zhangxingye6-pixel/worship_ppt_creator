package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.entity.GithubReleaseAssetEntity;
import claygminx.worshipppt.common.entity.GithubReleaseEntity;
import claygminx.worshipppt.util.UpdateNetworkSupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongConsumer;
import java.util.function.Consumer;

/**
 * 下载并校验正式发行包，不解压、不替换程序或用户文件。
 *
 * <p>每个实例只执行一次下载。附件必须来自指定仓库，且具备 SHA-256 摘要；
 * 通过大小和摘要校验后才将临时文件改名为 ZIP。失败或取消时删除未完成的文件。</p>
 */
public class ReleaseDownloadService {
    private static final int CONNECT_TIMEOUT_MILLIS = 15_000;
    private static final int READ_TIMEOUT_MILLIS = 30_000;
    private static final int MAX_REDIRECTS = 5;
    private static final Set<String> DOWNLOAD_HOSTS = Set.of(
            "github.com", "release-assets.githubusercontent.com",
            "objects.githubusercontent.com", "github-releases.githubusercontent.com");

    private final String owner;
    private final String repository;
    private final String proxySetting;
    private final Consumer<String> routeListener;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile HttpURLConnection activeConnection;

    public ReleaseDownloadService(String owner, String repository) {
        this(owner, repository, "", route -> { });
    }

    /**
     * 创建带显式代理与路由提示的下载任务，重定向后的连接继续使用相同配置。
     *
     * @param owner GitHub 仓库所有者
     * @param repository GitHub 仓库名称
     * @param proxySetting 代理地址、direct 或空字符串
     * @param routeListener 各请求主机所使用的连接方式回调
     */
    public ReleaseDownloadService(String owner, String repository, String proxySetting,
                                  Consumer<String> routeListener) {
        this.owner = owner;
        this.repository = repository;
        this.proxySetting = proxySetting;
        this.routeListener = routeListener;
    }

    /**
     * 精确选择正式版附件，未知架构不会猜测其他平台的安装包。
     *
     * @param release GitHub 正式发行信息
     * @param osName 当前运行系统名称
     * @param architecture 当前 Java 运行时架构
     * @return 唯一匹配的附件
     * @throws IOException 平台不支持、附件缺失或发行信息不完整
     */
    public GithubReleaseAssetEntity selectAsset(GithubReleaseEntity release,
                                               String osName, String architecture) throws IOException {
        validateRelease(release);
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        String arch = architecture == null ? "" : architecture.toLowerCase(Locale.ROOT);
        boolean intel = "amd64".equals(arch) || "x86_64".equals(arch);
        boolean arm = "aarch64".equals(arch) || "arm64".equals(arch);
        String platform;
        if (os.startsWith("windows") && intel) {
            platform = "windows";
        } else if (os.startsWith("mac") && intel) {
            platform = "macos-intel";
        } else if (os.startsWith("mac") && arm) {
            platform = "macos-arm64";
        } else {
            throw new IOException("暂不支持此系统或运行架构的应用内下载：" + osName + " / " + architecture);
        }
        String expectedName = "worship-ppt-creator-" + platform + "-" + release.tag_name() + ".zip";
        GithubReleaseAssetEntity selected = null;
        if (release.assets() != null) {
            for (GithubReleaseAssetEntity asset : release.assets()) {
                if (asset != null && expectedName.equals(asset.name()) && "uploaded".equals(asset.state())) {
                    if (selected != null) {
                        throw new IOException("发行包中存在重复的平台附件，请前往发布页检查");
                    }
                    selected = asset;
                }
            }
        }
        if (selected == null) {
            throw new IOException("该版本尚未提供当前平台的完整安装包，请稍后重试或前往发布页");
        }
        validateAsset(release, selected);
        return selected;
    }

    /**
     * 将安装包下载到独立目录，校验成功后返回最终路径。
     *
     * @param release 正式发行信息
     * @param asset 已匹配的平台附件
     * @param downloadRoot 下载根目录，不得使用程序安装目录
     * @param progress 已接收字节数的回调，由调用方切换至 UI 线程
     * @return 已校验的 ZIP 文件路径
     * @throws IOException 网络、磁盘或校验失败
     * @throws CancellationException 用户取消下载
     */
    public Path download(GithubReleaseEntity release, GithubReleaseAssetEntity asset,
                         Path downloadRoot, LongConsumer progress) throws IOException {
        if (!started.compareAndSet(false, true)) {
            throw new IOException("此下载任务已经执行，请重新创建下载任务");
        }
        validateRelease(release);
        validateAsset(release, asset);
        checkCancelled();
        Files.createDirectories(downloadRoot);
        Path directory = Files.createTempDirectory(downloadRoot, release.tag_name() + "-");
        Path partial = directory.resolve(asset.name() + ".part");
        boolean completed = false;
        try {
            MessageDigest digest = newSha256();
            HttpURLConnection connection = connect(URI.create(asset.browser_download_url()));
            long received = 0;
            try (InputStream input = connection.getInputStream();
                 OutputStream output = Files.newOutputStream(partial)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    checkCancelled();
                    received += count;
                    if (received > asset.size()) {
                        throw new IOException("安装包大小超过发行记录，已停止下载，请稍后重试");
                    }
                    output.write(buffer, 0, count);
                    digest.update(buffer, 0, count);
                    progress.accept(received);
                }
            }
            checkCancelled();
            if (received != asset.size()) {
                throw new IOException("安装包下载不完整，请检查网络后重试");
            }
            byte[] expected = HexFormat.of().parseHex(asset.digest().substring("sha256:".length()));
            if (!MessageDigest.isEqual(digest.digest(), expected)) {
                throw new IOException("安装包 SHA-256 校验失败，文件已丢弃，请重新下载");
            }
            Path destination = directory.resolve(asset.name());
            Files.move(partial, destination);
            completed = true;
            return destination;
        } catch (IOException e) {
            checkCancelled();
            throw e;
        } finally {
            HttpURLConnection connection = activeConnection;
            activeConnection = null;
            if (connection != null) {
                connection.disconnect();
            }
            if (!completed) {
                Files.deleteIfExists(partial);
                Files.deleteIfExists(directory);
            }
        }
    }

    /** 请求取消并中止网络连接，UI 等待后台任务清理结束后才允许重试。 */
    public void cancel() {
        cancelled.set(true);
        HttpURLConnection connection = activeConnection;
        if (connection != null) {
            connection.disconnect();
        }
    }

    private void checkCancelled() {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("下载已取消");
        }
    }

    private void validateRelease(GithubReleaseEntity release) throws IOException {
        if (release == null || release.draft() || release.prerelease() || release.tag_name() == null
                || !release.tag_name().matches("v[0-9]+\\.[0-9]+\\.[0-9]+")) {
            throw new IOException("发行信息无效，应用内下载仅支持正式版本");
        }
    }

    private void validateAsset(GithubReleaseEntity release, GithubReleaseAssetEntity asset) throws IOException {
        if (asset == null || asset.name() == null || !"uploaded".equals(asset.state())
                || !Set.of("worship-ppt-creator-windows-", "worship-ppt-creator-macos-intel-",
                "worship-ppt-creator-macos-arm64-").stream()
                .anyMatch(prefix -> (prefix + release.tag_name() + ".zip").equals(asset.name()))) {
            throw new IOException("安装包文件名无效");
        }
        String expectedUrl = "https://github.com/" + owner + "/" + repository
                + "/releases/download/" + release.tag_name() + "/" + asset.name();
        if (!expectedUrl.equals(asset.browser_download_url())) {
            throw new IOException("安装包地址与当前 GitHub 仓库不一致");
        }
        if (asset.size() <= 0 || asset.digest() == null
                || !asset.digest().matches("sha256:[0-9a-fA-F]{64}")) {
            throw new IOException("该安装包缺少完整性校验信息，请前往发布页手动下载");
        }
    }

    /** 仅允许 HTTPS 跳转至 GitHub 下载域名，不携带用户凭据或访问令牌。 */
    private HttpURLConnection connect(URI initialUri) throws IOException {
        URI uri = initialUri;
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            checkCancelled();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || !DOWNLOAD_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
                    || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) {
                throw new IOException("安装包下载跳转地址无效");
            }
            HttpURLConnection connection = openConnection(uri);
            activeConnection = connection;
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestProperty("User-Agent", "Worship-PPT-Updater");
            connection.setRequestProperty("Accept-Encoding", "identity");
            checkCancelled();
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                return connection;
            }
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if ((status == 301 || status == 302 || status == 303 || status == 307 || status == 308)
                    && location != null) {
                try {
                    uri = uri.resolve(location);
                } catch (IllegalArgumentException e) {
                    throw new IOException("安装包下载跳转地址无效", e);
                }
            } else {
                throw new IOException("下载安装包失败（HTTP " + status + "），请稍后重试");
            }
        }
        throw new IOException("安装包下载跳转次数过多，请前往发布页下载");
    }

    /** 将连接创建与校验分开，便于在无外网条件下验证失败、取消和重定向路径。 */
    HttpURLConnection openConnection(URI uri) throws IOException {
        return UpdateNetworkSupport.openConnection(uri, proxySetting, routeListener);
    }

    private MessageDigest newSha256() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("当前 Java 运行环境不支持 SHA-256 校验", e);
        }
    }
}
