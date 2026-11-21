package claygminx.worshipppt.util;

import org.junit.After;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/** 验证代理配置和真实的本地代理握手，不访问外网。 */
public class UpdateNetworkSupportTest {
    private static final URI GITHUB = URI.create("https://github.com/test/release");
    private final ProxySelector originalSelector = ProxySelector.getDefault();

    @After
    public void restoreProxySelector() {
        ProxySelector.setDefault(originalSelector);
    }

    @Test
    public void buildsLocalHttpAndSocksProxyFromPortOnly() throws Exception {
        assertEquals("http://127.0.0.1:7890", UpdateNetworkSupport.localProxySetting("HTTP", "7890"));
        assertEquals("socks5://127.0.0.1:7891", UpdateNetworkSupport.localProxySetting("SOCKS", "7891"));
        assertEquals("socks5://127.0.0.1:65535", UpdateNetworkSupport.localProxySetting(" socks ", "65535"));
        assertEquals("http://127.0.0.1:1", UpdateNetworkSupport.localProxySetting(null, "1"));
    }

    @Test
    public void emptyPortKeepsAutomaticProxySelection() throws Exception {
        assertEquals("", UpdateNetworkSupport.localProxySetting("HTTP", ""));
        assertEquals("", UpdateNetworkSupport.localProxySetting("SOCKS", "  "));
        assertEquals("", UpdateNetworkSupport.localProxySetting(null, null));
    }

    @Test
    public void rejectsNonPortInputAndOutOfRangePort() {
        for (String port : List.of("0", "65536", "-1", "1.5", "7890abc", "http://127.0.0.1:7890", "9999999999999")) {
            assertThrows(IOException.class, () -> UpdateNetworkSupport.localProxySetting("HTTP", port));
        }
        assertThrows(IOException.class, () -> UpdateNetworkSupport.localProxySetting("HTTPS", "7890"));
    }

    @Test
    public void acceptsExplicitHttpAndSocksWithCorrectEndpoint() throws Exception {
        for (String scheme : List.of("http", "socks", "socks5")) {
            Proxy proxy = UpdateNetworkSupport.resolveProxy(scheme + "://127.0.0.1:12345", GITHUB);
            assertEquals("http".equals(scheme) ? Proxy.Type.HTTP : Proxy.Type.SOCKS, proxy.type());
            InetSocketAddress address = (InetSocketAddress) proxy.address();
            assertEquals("127.0.0.1", address.getHostString());
            assertEquals(12345, address.getPort());
        }
    }

    @Test
    public void rejectsMalformedOrAuthenticatedProxyWithoutLeakingCredentials() {
        for (String setting : List.of("127.0.0.1:7890", "https://127.0.0.1:7890",
                "http://localhost", "http://localhost:0", "http://localhost:65536",
                "http://user:secret@localhost:7890", "http://localhost:7890/path",
                "http://localhost:7890?token=secret", "http://localhost:7890#fragment")) {
            IOException error = assertThrows(IOException.class,
                    () -> UpdateNetworkSupport.resolveProxy(setting, GITHUB));
            assertFalse(error.getMessage().contains("secret"));
        }
    }

    @Test
    public void emptySettingUsesRulesForEachTargetIncludingCdn() throws Exception {
        List<URI> selectedTargets = new ArrayList<>();
        Proxy expected = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("127.0.0.1", 12345));
        ProxySelector.setDefault(selector(expected, selectedTargets));
        URI cdn = URI.create("https://release-assets.githubusercontent.com/test");
        assertSame(expected, UpdateNetworkSupport.resolveProxy("", GITHUB));
        assertSame(expected, UpdateNetworkSupport.resolveProxy(null, cdn));
        assertEquals(List.of(GITHUB, cdn), selectedTargets);
    }

    @Test
    public void explicitProxyAndDirectDoNotConsultSystemRules() throws Exception {
        List<URI> selectedTargets = new ArrayList<>();
        ProxySelector.setDefault(selector(Proxy.NO_PROXY, selectedTargets));
        assertEquals(Proxy.Type.SOCKS, UpdateNetworkSupport.resolveProxy("socks5://localhost:12345", GITHUB).type());
        assertSame(Proxy.NO_PROXY, UpdateNetworkSupport.resolveProxy("direct", GITHUB));
        assertTrue(selectedTargets.isEmpty());
    }

    @Test
    public void missingSystemSelectorUsesDirectConnection() throws Exception {
        ProxySelector.setDefault(null);
        assertSame(Proxy.NO_PROXY, UpdateNetworkSupport.resolveProxy("", GITHUB));
    }

    @Test
    public void explicitHttpProxyReceivesRealHttpsConnectRequest() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            server.setSoTimeout(4000);
            Future<String> request = executor.submit(() -> {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(4000);
                    BufferedReader input = new BufferedReader(new InputStreamReader(
                            socket.getInputStream(), StandardCharsets.US_ASCII));
                    String firstLine = input.readLine();
                    String line;
                    while ((line = input.readLine()) != null && !line.isEmpty()) {
                        // 读取 CONNECT 请求头，再返回受控错误，避免实际访问目标站点。
                    }
                    socket.getOutputStream().write("HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n"
                            .getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    return firstLine;
                }
            });
            List<String> routes = new ArrayList<>();
            var connection = UpdateNetworkSupport.openConnection(GITHUB,
                    "http://127.0.0.1:" + server.getLocalPort(), routes::add);
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            try {
                assertThrows(IOException.class, connection::getInputStream);
                assertTrue(request.get(5, TimeUnit.SECONDS).startsWith("CONNECT github.com:443 "));
                assertTrue(routes.get(0).contains("HTTP 代理 127.0.0.1:" + server.getLocalPort()));
            } finally {
                connection.disconnect();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void explicitSocksProxyReceivesRealSocksHandshake() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            server.setSoTimeout(4000);
            Future<Integer> greeting = executor.submit(() -> {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(4000);
                    int version = socket.getInputStream().read();
                    int count = socket.getInputStream().read();
                    socket.getInputStream().readNBytes(Math.max(0, count));
                    socket.getOutputStream().write(new byte[]{5, (byte) 255});
                    socket.getOutputStream().flush();
                    return version;
                }
            });
            var connection = UpdateNetworkSupport.openConnection(URI.create("https://127.0.0.1/test"),
                    "socks5://127.0.0.1:" + server.getLocalPort(), route -> { });
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            try {
                assertThrows(IOException.class, connection::getInputStream);
                assertEquals(Integer.valueOf(5), greeting.get(5, TimeUnit.SECONDS));
            } finally {
                connection.disconnect();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private ProxySelector selector(Proxy proxy, List<URI> targets) {
        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                targets.add(uri);
                return List.of(proxy);
            }

            @Override
            public void connectFailed(URI uri, SocketAddress address, IOException error) {
                fail("Unexpected proxy connection failure");
            }
        };
    }
}
