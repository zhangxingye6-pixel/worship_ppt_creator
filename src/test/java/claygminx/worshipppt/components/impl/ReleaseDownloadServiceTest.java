package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.entity.GithubReleaseAssetEntity;
import claygminx.worshipppt.common.entity.GithubReleaseEntity;
import com.google.gson.Gson;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.HexFormat;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CancellationException;

import static org.junit.Assert.*;

/** 使用内存连接测试下载边界，不访问 GitHub，不下载真实安装包。 */
public class ReleaseDownloadServiceTest {
    private static final byte[] PAYLOAD = "test-release-payload".getBytes(StandardCharsets.UTF_8);
    private static final String OWNER = "test-owner";
    private static final String REPOSITORY = "test-repository";
    private static final String TAG = "v1.9.0";

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void selectsEachPublishedPlatformAndArchitectureAlias() throws Exception {
        for (String[] platform : List.of(new String[]{"Windows 11", "amd64", "windows"},
                new String[]{"Windows 10", "x86_64", "windows"},
                new String[]{"Mac OS X", "x86_64", "macos-intel"},
                new String[]{"Mac OS X", "amd64", "macos-intel"},
                new String[]{"Mac OS X", "aarch64", "macos-arm64"},
                new String[]{"Mac OS X", "arm64", "macos-arm64"})) {
            GithubReleaseAssetEntity asset = asset(platform[2]);
            assertSame(asset, service().selectAsset(release(asset), platform[0], platform[1]));
        }
    }

    @Test
    public void rejectsUnsupportedPlatformRatherThanGuessing() throws Exception {
        GithubReleaseEntity release = release(asset("windows"));
        assertThrows(IOException.class, () -> service().selectAsset(release, "Linux", "amd64"));
        assertThrows(IOException.class, () -> service().selectAsset(release, "Windows 11", "aarch64"));
        assertThrows(IOException.class, () -> service().selectAsset(release, "Mac OS X", "unknown"));
    }

    @Test
    public void rejectsMissingDuplicateAndUnpublishedAssets() throws Exception {
        GithubReleaseAssetEntity asset = asset("windows");
        assertThrows(IOException.class, () -> service().selectAsset(release(asset), "Mac OS X", "arm64"));
        assertThrows(IOException.class, () -> service().selectAsset(
                release(asset).assets(List.of(asset, asset)), "Windows 11", "amd64"));
        asset.state("starter");
        assertThrows(IOException.class, () -> service().selectAsset(release(asset), "Windows 11", "amd64"));
    }

    @Test
    public void rejectsDraftPrereleaseAndUnsafeVersion() throws Exception {
        GithubReleaseEntity release = release(asset("windows"));
        assertThrows(IOException.class, () -> service().selectAsset(release.draft(true), "Windows 11", "amd64"));
        release.draft(false).prerelease(true);
        assertThrows(IOException.class, () -> service().selectAsset(release, "Windows 11", "amd64"));
        release.prerelease(false).tag_name("v1.9.0-rc.1");
        assertThrows(IOException.class, () -> service().selectAsset(release, "Windows 11", "amd64"));
        release.tag_name("../escape");
        assertThrows(IOException.class, () -> service().selectAsset(release, "Windows 11", "amd64"));
    }

    @Test
    public void requiresDigestAndTrustedRepositoryUrlBeforeConnecting() throws Exception {
        GithubReleaseAssetEntity asset = asset("windows");
        String digest = asset.digest();
        asset.digest(null);
        assertThrows(IOException.class, () -> service().selectAsset(release(asset), "Windows 11", "amd64"));
        asset.digest(digest).browser_download_url("https://example.com/package.zip");
        assertThrows(IOException.class, () -> service().selectAsset(release(asset), "Windows 11", "amd64"));
        asset.browser_download_url("https://github.com/another/repository/releases/download/" + TAG + "/" + asset.name());
        assertThrows(IOException.class, () -> service().selectAsset(release(asset), "Windows 11", "amd64"));
    }

    @Test
    public void savesOnlyVerifiedPayloadAndReportsProgress() throws Exception {
        GithubReleaseAssetEntity asset = asset("windows");
        Path root = temporary.newFolder().toPath();
        long[] received = {0};
        FakeConnection connection = response(200, PAYLOAD, null);
        Path file = service(connection).download(release(asset), asset, root, value -> received[0] = value);
        assertArrayEquals(PAYLOAD, Files.readAllBytes(file));
        assertEquals(asset.name(), file.getFileName().toString());
        assertEquals(PAYLOAD.length, received[0]);
        assertEquals(15_000, connection.getConnectTimeout());
        assertEquals(30_000, connection.getReadTimeout());
        assertFalse(connection.getInstanceFollowRedirects());
        assertTrue(connection.disconnected);
        try (var files = Files.list(file.getParent())) {
            assertEquals(1, files.count());
        }
    }

    @Test
    public void digestMismatchDiscardsFile() throws Exception {
        GithubReleaseAssetEntity asset = asset("windows").digest("sha256:" + "0".repeat(64));
        assertDownloadFailure(service(response(200, PAYLOAD, null)), asset, IOException.class);
    }

    @Test
    public void incompleteAndOversizedPayloadsAreDiscarded() throws Exception {
        assertDownloadFailure(service(response(200, new byte[1], null)), asset("windows"), IOException.class);
        assertDownloadFailure(service(response(200, new byte[100], null)), asset("windows"), IOException.class);
    }

    @Test
    public void serverFailureAndTimeoutLeaveNoPartialFiles() throws Exception {
        assertDownloadFailure(service(response(403, new byte[0], null)), asset("windows"), IOException.class);
        FakeConnection timeout = new FakeConnection(200, new ByteArrayInputStream(PAYLOAD), null) {
            @Override
            public InputStream getInputStream() throws IOException {
                throw new SocketTimeoutException("Read timed out");
            }
        };
        assertDownloadFailure(service(timeout), asset("windows"), SocketTimeoutException.class);
    }

    @Test
    public void cancellationDuringTransferDiscardsFile() throws Exception {
        GithubReleaseAssetEntity asset = asset("windows");
        FakeService service = service(response(200, PAYLOAD, null));
        Path root = temporary.newFolder().toPath();
        assertThrows(CancellationException.class,
                () -> service.download(release(asset), asset, root, received -> service.cancel()));
        assertEmpty(root);
    }

    @Test
    public void cancelledTaskDoesNotConnectOrCreateDirectory() throws Exception {
        FakeService service = service();
        service.cancel();
        assertDownloadFailure(service, asset("windows"), CancellationException.class);
    }

    @Test
    public void followsTrustedHttpsRedirect() throws Exception {
        GithubReleaseAssetEntity asset = asset("windows");
        Path root = temporary.newFolder().toPath();
        Path file = service(response(302, new byte[0], "https://release-assets.githubusercontent.com/file"),
                response(200, PAYLOAD, null)).download(release(asset), asset, root, received -> { });
        assertArrayEquals(PAYLOAD, Files.readAllBytes(file));
    }

    @Test
    public void rejectsUntrustedAndInsecureRedirects() throws Exception {
        for (String location : List.of("http://github.com/file", "https://example.com/file",
                "https://github.com.evil.example/file", "https://user@github.com/file",
                "https://github.com:8443/file")) {
            assertDownloadFailure(service(response(302, new byte[0], location)), asset("windows"), IOException.class);
        }
    }

    @Test
    public void preventsInfiniteRedirects() throws Exception {
        FakeConnection[] responses = new FakeConnection[6];
        for (int i = 0; i < responses.length; i++) {
            responses[i] = response(302, new byte[0], "/same-place");
        }
        assertDownloadFailure(service(responses), asset("windows"), IOException.class);
    }

    @Test
    public void rejectsSecondUseOfDownloadTask() throws Exception {
        GithubReleaseAssetEntity asset = asset("windows");
        FakeService service = service(response(200, PAYLOAD, null));
        Path root = temporary.newFolder().toPath();
        service.download(release(asset), asset, root, received -> { });
        assertThrows(IOException.class, () -> service.download(release(asset), asset, root, received -> { }));
    }

    @Test
    public void githubJsonIncludesAssetsAndDigest() {
        GithubReleaseEntity parsed = new Gson().fromJson("{\"tag_name\":\"v1.9.0\",\"draft\":false,"
                + "\"prerelease\":false,\"assets\":[{\"name\":\"package.zip\",\"state\":\"uploaded\","
                + "\"size\":42,\"digest\":\"sha256:abc\",\"browser_download_url\":\"https://github.com/file\"}]}",
                GithubReleaseEntity.class);
        assertEquals(42, parsed.assets().get(0).size());
        assertEquals("sha256:abc", parsed.assets().get(0).digest());
        assertFalse(parsed.prerelease());
    }

    private void assertDownloadFailure(FakeService service, GithubReleaseAssetEntity asset,
                                       Class<? extends Throwable> type) throws Exception {
        Path root = temporary.newFolder().toPath();
        assertThrows(type, () -> service.download(release(asset), asset, root, received -> { }));
        assertEmpty(root);
    }

    private void assertEmpty(Path root) throws IOException {
        try (var files = Files.list(root)) {
            assertEquals(0, files.count());
        }
    }

    private GithubReleaseEntity release(GithubReleaseAssetEntity asset) {
        return new GithubReleaseEntity().tag_name(TAG).assets(List.of(asset));
    }

    private GithubReleaseAssetEntity asset(String platform) throws Exception {
        String name = "worship-ppt-creator-" + platform + "-" + TAG + ".zip";
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(PAYLOAD));
        return new GithubReleaseAssetEntity().name(name).state("uploaded").size(PAYLOAD.length)
                .digest("sha256:" + digest).browser_download_url("https://github.com/" + OWNER + "/" + REPOSITORY
                        + "/releases/download/" + TAG + "/" + name);
    }

    private FakeConnection response(int code, byte[] body, String location) throws Exception {
        return new FakeConnection(code, new ByteArrayInputStream(body), location);
    }

    private FakeService service(FakeConnection... connections) {
        return new FakeService(connections);
    }

    private static class FakeService extends ReleaseDownloadService {
        private final Queue<FakeConnection> connections;

        FakeService(FakeConnection... connections) {
            super(OWNER, REPOSITORY);
            this.connections = new ArrayDeque<>(List.of(connections));
        }

        @Override
        HttpURLConnection openConnection(URI uri) {
            assertFalse("Unexpected network connection", connections.isEmpty());
            return connections.remove();
        }
    }

    private static class FakeConnection extends HttpURLConnection {
        private final int code;
        private final InputStream input;
        private final String location;
        private boolean disconnected;

        FakeConnection(int code, InputStream input, String location) throws Exception {
            super(URI.create("https://github.com/test").toURL());
            this.code = code;
            this.input = input;
            this.location = location;
        }

        @Override public int getResponseCode() { return code; }
        @Override public String getHeaderField(String name) { return "Location".equals(name) ? location : null; }
        @Override public InputStream getInputStream() throws IOException { return input; }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
    }
}
