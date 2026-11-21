package claygminx.worshipppt.common.config;

import claygminx.worshipppt.common.Dict;
import claygminx.worshipppt.util.UpdateNetworkSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 仅负责更新代理的两个配置项，保留用户文件中的注释、顺序和其他设置。 */
public final class UpdateProxyConfig {
    private static final Object WRITE_LOCK = new Object();
    private final Path file;
    private final Properties runtimeProperties;

    public UpdateProxyConfig(Path file, Properties runtimeProperties) {
        this.file = file.toAbsolutePath();
        this.runtimeProperties = runtimeProperties;
    }

    /** 经校验并规范化的本机代理选择。 */
    public record Settings(String type, String port) {
        public String proxySetting() throws IOException {
            return UpdateNetworkSupport.localProxySetting(type, port);
        }
    }

    /**
     * 用户填写端口时使用本次选择，否则从磁盘读取已保存的类型和端口。
     *
     * @param type GUI 选中的 HTTP 或 SOCKS
     * @param port GUI 输入端口，可为空
     * @return 本次下载实际使用的设置
     * @throws IOException 输入无效或读取配置失败
     */
    public Settings resolve(String type, String port) throws IOException {
        if (port != null && !port.isBlank()) {
            return normalize(type, port);
        }
        synchronized (WRITE_LOCK) {
            Properties saved = new Properties();
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                saved.load(reader);
            }
            Settings settings = normalize(saved.getProperty(Dict.UpdateProperty.PROXY_TYPE),
                    saved.getProperty(Dict.UpdateProperty.PROXY_PORT));
            refreshRuntime(settings);
            return settings;
        }
    }

    /**
     * 原子保存用户的有效选择，并同步当前进程的配置缓存。
     *
     * <p>只有用户明确填写端口时才调用；下载是否成功不影响已保存的偏好。
     * 先写入同目录临时文件，成功后替换原文件，避免写入中断损坏配置。</p>
     *
     * @param settings 用户填写并已校验的类型和端口
     * @throws IOException 配置不可写或保存期间被其他程序修改
     */
    public void save(Settings settings) throws IOException {
        Settings normalized = normalize(settings.type(), settings.port());
        if (normalized.port().isEmpty()) {
            return;
        }
        synchronized (WRITE_LOCK) {
            String original = Files.readString(file, StandardCharsets.UTF_8);
            String updated = replaceProperty(original, Dict.UpdateProperty.PROXY_TYPE, normalized.type());
            updated = replaceProperty(updated, Dict.UpdateProperty.PROXY_PORT, normalized.port());
            if (!updated.equals(original)) {
                Path temporary = Files.createTempFile(file.getParent(), ".update-proxy-", ".tmp");
                try {
                    Files.writeString(temporary, updated, StandardCharsets.UTF_8);
                    if (!Files.readString(file, StandardCharsets.UTF_8).equals(original)) {
                        throw new IOException("配置文件已被其他程序修改，请重新尝试保存代理设置");
                    }
                    try {
                        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } catch (AtomicMoveNotSupportedException e) {
                        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
            refreshRuntime(normalized);
        }
    }

    private Settings normalize(String type, String port) throws IOException {
        String protocol = type == null || type.isBlank() ? "HTTP" : type.trim().toUpperCase(Locale.ROOT);
        String value = port == null ? "" : port.trim();
        UpdateNetworkSupport.localProxySetting(protocol, value);
        return new Settings(protocol, value.isEmpty() ? "" : Integer.toString(Integer.parseInt(value)));
    }

    private void refreshRuntime(Settings settings) {
        runtimeProperties.putAll(Map.of(Dict.UpdateProperty.PROXY_TYPE, settings.type(),
                Dict.UpdateProperty.PROXY_PORT, settings.port()));
    }

    /** 使用标准 Properties 读取语义，写入时只替换两个单行配置，保留其余文件内容。 */
    private String replaceProperty(String text, String key, String value) throws IOException {
        Pattern entry = Pattern.compile("(?m)^[\\t ]*" + Pattern.quote(key) + "[\\t ]*[:=][^\\r\\n]*");
        Matcher matcher = entry.matcher(text);
        if (matcher.find()) {
            do {
                // 本功能写入的字段都是单行值，不覆盖人工编写的续行配置。
                String line = matcher.group();
                int backslashes = 0;
                for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
                    backslashes++;
                }
                if (backslashes % 2 != 0) {
                    throw new IOException("代理配置请使用单行格式后重试：" + key);
                }
            } while (matcher.find());
            return entry.matcher(text).replaceAll(Matcher.quoteReplacement(key + "=" + value));
        }
        String newline = text.contains("\r\n") ? "\r\n" : "\n";
        return text + (text.isEmpty() || text.endsWith("\n") || text.endsWith("\r") ? "" : newline)
                + key + "=" + value + newline;
    }
}
