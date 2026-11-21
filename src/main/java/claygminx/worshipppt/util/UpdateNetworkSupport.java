package claygminx.worshipppt.util;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** 更新检查与附件下载共用的代理选择规则，不修改其他功能的网络连接。 */
public final class UpdateNetworkSupport {
    private UpdateNetworkSupport() {
    }

    /**
     * 将 GUI 或外部配置中的代理类型、端口转换为本机代理地址。
     *
     * @param type HTTP 或 SOCKS，未配置时默认 HTTP
     * @param port 代理端口；留空时使用系统/Java 代理规则
     * @return 主机固定为 127.0.0.1 的代理地址，未指定端口时返回空字符串
     * @throws IOException 类型不支持或端口不是 1 至 65535 的整数
     */
    public static String localProxySetting(String type, String port) throws IOException {
        String protocol = type == null || type.isBlank() ? "HTTP" : type.trim().toUpperCase(Locale.ROOT);
        if (!"HTTP".equals(protocol) && !"SOCKS".equals(protocol)) {
            throw new IOException("代理类型只能选择 HTTP 或 SOCKS");
        }
        String value = port == null ? "" : port.trim();
        if (value.isEmpty()) {
            return "";
        }
        if (!value.matches("[0-9]{1,5}")) {
            throw new IOException("代理端口只需输入 1 至 65535 的整数，无需填写地址或协议");
        }
        int number = Integer.parseInt(value);
        if (number < 1 || number > 65535) {
            throw new IOException("代理端口必须在 1 至 65535 之间");
        }
        return ("HTTP".equals(protocol) ? "http" : "socks5") + "://127.0.0.1:" + number;
    }

    /**
     * 选择本次更新连接的代理。
     *
     * <p>显式代理优先；留空时查询 Java 的代理选择器；direct 表示主动直连。
     * 显式代理连接失败时不会悄悄回退到直连。代理不接受用户名或密码。</p>
     *
     * @param setting HTTP/SOCKS 代理地址、direct 或空字符串
     * @param target 实际请求地址，每次重定向后重新查询系统规则
     * @return 本次连接使用的代理，直连时返回 {@link Proxy#NO_PROXY}
     * @throws IOException 代理配置无效或无法读取系统代理规则
     */
    public static Proxy resolveProxy(String setting, URI target) throws IOException {
        String value = setting == null ? "" : setting.trim();
        if ("direct".equalsIgnoreCase(value)) {
            return Proxy.NO_PROXY;
        }
        if (value.isEmpty()) {
            try {
                ProxySelector selector = ProxySelector.getDefault();
                List<Proxy> proxies = selector == null ? null : selector.select(target);
                return proxies == null || proxies.isEmpty() || proxies.get(0) == null
                        ? Proxy.NO_PROXY : proxies.get(0);
            } catch (RuntimeException e) {
                throw new IOException("无法读取系统代理，请填写代理软件提供的 HTTP 或 SOCKS 地址", e);
            }
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            Proxy.Type type;
            if ("http".equals(scheme)) {
                type = Proxy.Type.HTTP;
            } else if ("socks".equals(scheme) || "socks5".equals(scheme)) {
                type = Proxy.Type.SOCKS;
            } else {
                throw new IllegalArgumentException("Unsupported proxy protocol");
            }
            if (uri.getHost() == null || uri.getPort() < 1 || uri.getPort() > 65535
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
                throw new IllegalArgumentException("Invalid proxy address");
            }
            return new Proxy(type, InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
        } catch (IllegalArgumentException e) {
            throw new IOException("代理地址无效：请使用 http://主机:端口 或 socks5://主机:端口，不支持账号密码", e);
        }
    }

    /**
     * 显式传入已选择的代理，确保 HTTPS 请求及 GitHub CDN 跳转均采用同一规则。
     *
     * @param target 请求地址
     * @param setting 本次更新的代理配置
     * @param route 接收连接方式说明的回调，不包含任何账号或密码
     * @return 尚未建立连接的 HTTP 连接对象
     * @throws IOException 地址或代理配置无效
     */
    public static HttpURLConnection openConnection(URI target, String setting,
                                                  Consumer<String> route) throws IOException {
        Proxy proxy = resolveProxy(setting, target);
        String description;
        if (proxy.type() == Proxy.Type.DIRECT) {
            description = "Java 直连（TUN/系统网络仍可能接管）";
        } else if (proxy.address() instanceof InetSocketAddress address) {
            description = proxy.type() + " 代理 " + address.getHostString() + ":" + address.getPort();
        } else {
            throw new IOException("系统返回了无法识别的代理地址，请填写显式代理");
        }
        route.accept(target.getHost() + "：" + description);
        return (HttpURLConnection) target.toURL().openConnection(proxy);
    }
}
