package claygminx.worshipppt.util;

import claygminx.worshipppt.common.Dict;
import claygminx.worshipppt.common.config.UpdateProxyConfig;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.Assert.*;

/** 验证 GUI 代理偏好的保存、读取及对其他用户配置的保护。 */
public class UpdateProxyConfigTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void savesInitialChoiceAndRefreshesRunningConfiguration() throws Exception {
        String original = "# 用户配置\r\nppt.general.titleSize=28.0\r\n"
                + "update.proxy.type=HTTP\r\nupdate.proxy.port=\r\n# 保留注释\r\n";
        Path file = configFile(original);
        Properties runtime = new Properties();
        runtime.setProperty("ppt.general.titleSize", "28.0");
        UpdateProxyConfig config = new UpdateProxyConfig(file, runtime);
        UpdateProxyConfig.Settings entered = config.resolve("SOCKS", "7891");
        config.save(entered);
        assertEquals(original.replace("update.proxy.type=HTTP", "update.proxy.type=SOCKS")
                .replace("update.proxy.port=", "update.proxy.port=7891"), Files.readString(file));
        assertEquals("SOCKS", runtime.getProperty(Dict.UpdateProperty.PROXY_TYPE));
        assertEquals("7891", runtime.getProperty(Dict.UpdateProperty.PROXY_PORT));
        assertEquals("28.0", runtime.getProperty("ppt.general.titleSize"));
    }

    @Test
    public void blankInputUsesSavedTypeAndPortAfterRestart() throws Exception {
        Path file = configFile("update.proxy.type=SOCKS\nupdate.proxy.port=7891\n");
        UpdateProxyConfig config = new UpdateProxyConfig(file, new Properties());
        UpdateProxyConfig.Settings saved = config.resolve("HTTP", "");
        assertEquals("SOCKS", saved.type());
        assertEquals("7891", saved.port());
        assertEquals("socks5://127.0.0.1:7891", saved.proxySetting());
    }

    @Test
    public void laterExplicitChoiceReplacesSavedSettings() throws Exception {
        Path file = configFile("update.proxy.type=SOCKS\nupdate.proxy.port=7891\n");
        UpdateProxyConfig config = new UpdateProxyConfig(file, new Properties());
        config.save(config.resolve("HTTP", "7890"));
        UpdateProxyConfig.Settings reloaded = config.resolve("SOCKS", "  ");
        assertEquals("HTTP", reloaded.type());
        assertEquals("7890", reloaded.port());
    }

    @Test
    public void appendsMissingKeysWithoutReformattingOtherValues() throws Exception {
        Path file = configFile("# 模板路径\nppt.master.worshipFilePath=PPT模板/worship.pptx");
        UpdateProxyConfig config = new UpdateProxyConfig(file, new Properties());
        config.save(config.resolve("HTTP", "7890"));
        assertEquals("# 模板路径\nppt.master.worshipFilePath=PPT模板/worship.pptx\n"
                + "update.proxy.type=HTTP\nupdate.proxy.port=7890\n", Files.readString(file));
        try (var files = Files.list(file.getParent())) {
            assertEquals(1, files.count());
        }
    }

    @Test
    public void invalidInputAndBlankSaveCannotOverwriteExistingSettings() throws Exception {
        String original = "update.proxy.type=SOCKS\nupdate.proxy.port=7891\n";
        Path file = configFile(original);
        UpdateProxyConfig config = new UpdateProxyConfig(file, new Properties());
        assertThrows(IOException.class, () -> config.resolve("HTTP", "65536"));
        config.save(new UpdateProxyConfig.Settings("HTTP", ""));
        assertEquals(original, Files.readString(file));
    }

    @Test
    public void emptySavedPortUsesAutomaticProxyRules() throws Exception {
        Path file = configFile("# 还没有配置本机代理\n");
        UpdateProxyConfig config = new UpdateProxyConfig(file, new Properties());
        assertEquals("", config.resolve("SOCKS", "").proxySetting());
    }

    @Test
    public void missingConfigAndMultilineProxyFailWithoutPartialWrite() throws Exception {
        Path missing = temporary.getRoot().toPath().resolve("missing.config");
        UpdateProxyConfig config = new UpdateProxyConfig(missing, new Properties());
        assertThrows(IOException.class, () -> config.save(new UpdateProxyConfig.Settings("HTTP", "7890")));
        assertFalse(Files.exists(missing));
        String original = "update.proxy.type=HTTP\nupdate.proxy.port=78\\\n90\n";
        Path file = configFile(original);
        UpdateProxyConfig multiline = new UpdateProxyConfig(file, new Properties());
        assertThrows(IOException.class, () -> multiline.save(new UpdateProxyConfig.Settings("SOCKS", "7891")));
        assertEquals(original, Files.readString(file));
    }

    private Path configFile(String content) throws IOException {
        Path file = temporary.newFolder().toPath().resolve("worship-ppt.config");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}
