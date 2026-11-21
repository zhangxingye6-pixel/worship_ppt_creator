package claygminx.worshipppt.components.impl;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

/** 使用临时 ZIP 验证解压边界及开发环境保护，不下载真实安装包。 */
public class UpdateInstallServiceTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void developmentClassesCannotOverwriteSourceProject() throws Exception {
        try {
            new UpdateInstallService().locateInstallation();
            fail("开发运行必须禁止自动替换");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("开发运行"));
        }
    }

    @Test
    public void preparesPackageWithoutChangingExistingInstallation() throws Exception {
        Path target = Files.createDirectory(temporary.getRoot().toPath().resolve("installed"));
        Files.writeString(target.resolve("user.txt"), "old");
        Path archive = archive(List.of("App/config/worship-ppt.config", "App/PPT模板/worship.pptx",
                "App/数据库/bible.db", "App/App.exe"));
        UpdateInstallService.PreparedInstallation prepared = new UpdateInstallService().prepare(archive,
                new UpdateInstallService.Installation(target, "config", "App.exe", false));
        Path staged = prepared.source();
        assertEquals("payload", Files.readString(staged.resolve("PPT模板/worship.pptx")));
        assertEquals("old", Files.readString(target.resolve("user.txt")));
        assertFalse(Files.exists(target.resolve("PPT模板")));
        String log = Files.readString(prepared.work().resolve("preparation.log"));
        assertTrue(log.contains("[ENV]"));
        assertTrue(log.contains("[ZIP_VALIDATED]"));
        assertTrue(log.contains("[PREPARED]"));
    }

    @Test
    public void rejectsArchiveTraversal() throws Exception {
        rejects(List.of("../escaped.txt"), "不安全路径");
    }

    @Test
    public void rejectsWindowsAbsolutePathOnEveryPlatform() throws Exception {
        rejects(List.of("C:/escaped.txt"), "不安全路径");
    }

    @Test
    public void rejectsUnixAbsolutePath() throws Exception {
        rejects(List.of("/escaped.txt"), "不安全路径");
    }

    @Test
    public void rejectsIncompletePackage() throws Exception {
        rejects(List.of("App/config/worship-ppt.config"), "缺少配置");
    }

    @Test
    public void rejectsMultipleApplicationRoots() throws Exception {
        rejects(List.of("App1/config/worship-ppt.config", "App2/config/worship-ppt.config"), "唯一");
    }

    @Test
    public void rejectsChangedLauncherName() throws Exception {
        rejects(List.of("App/config/worship-ppt.config", "App/PPT模板/worship.pptx",
                "App/数据库/bible.db", "App/Other.exe"), "启动器名称");
    }

    private void rejects(List<String> entries, String message) throws Exception {
        Path target = Files.createDirectory(temporary.getRoot().toPath().resolve("installed"));
        try {
            new UpdateInstallService().prepare(archive(entries),
                    new UpdateInstallService.Installation(target, "config", "App.exe", false));
            fail("应当拒绝不安全或不完整的安装包");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(message));
        }
        try (java.util.stream.Stream<Path> files = Files.list(target)) {
            assertEquals(0L, files.count());
        }
    }

    private Path archive(List<String> entries) throws Exception {
        Path zip = temporary.getRoot().toPath().resolve("release.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (String name : entries) {
                output.putNextEntry(new ZipEntry(name));
                output.write("payload".getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return zip;
    }
}
