package claygminx.worshipppt.components.impl;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.OutputStream;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** 验证更新事务保留用户配置、替换模板并保留可回滚的旧文件。 */
public class UpdateInstallerMainTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void preservesConfigAndReplacesTemplateAndDatabase() throws Exception {
        Path root = temporary.getRoot().toPath();
        Path target = root.resolve("installed");
        Path source = root.resolve("staged");
        Path backup = root.resolve("backup");
        createInstallTree(target, "old");
        createInstallTree(source, "new");

        UpdateInstallerMain.install(source, target, backup, "config", "");

        assertEquals("sample=old-config\n", Files.readString(target.resolve("config/worship-ppt.config")));
        assertEquals("new-template", Files.readString(target.resolve("PPT模板/worship.pptx")));
        assertEquals("new-database", Files.readString(target.resolve("数据库/test.db")));
        assertEquals("old-template", Files.readString(backup.resolve("PPT模板/worship.pptx")));
        assertTrue(Files.exists(backup.resolve("preserved-config/worship-ppt.config")));
    }

    @Test
    public void rejectsBackupInsideInstallationBeforeChangingFiles() throws Exception {
        Path root = temporary.getRoot().toPath();
        Path target = root.resolve("installed");
        Path source = root.resolve("staged");
        createInstallTree(target, "old");
        createInstallTree(source, "new");

        try {
            UpdateInstallerMain.install(source, target, target.resolve("backup"), "config", "");
            fail("应当拒绝安装目录内的备份路径");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("安装目录之外"));
        }

        assertEquals("old-template", Files.readString(target.resolve("PPT模板/worship.pptx")));
        assertFalse(Files.exists(target.resolve("backup")));
    }

    @Test
    public void preservesMacConfigInsideReplacedContents() throws Exception {
        Path root = temporary.getRoot().toPath();
        Path target = root.resolve("Old.app");
        Path source = root.resolve("New.app");
        createInstallTree(target.resolve("Contents/Resources"), "old");
        createInstallTree(source.resolve("Contents/Resources"), "new");
        Files.writeString(target.resolve("Contents/Resources/config/worship-procedure.xml"), "old-procedure");
        Files.writeString(source.resolve("Contents/Resources/config/worship-procedure.xml"), "new-procedure");
        UpdateInstallerMain.install(source, target, root.resolve("backup"), "Contents/Resources/config", "");
        assertEquals("sample=old-config\n", Files.readString(target.resolve("Contents/Resources/config/worship-ppt.config")));
        assertEquals("new-procedure", Files.readString(target.resolve("Contents/Resources/config/worship-procedure.xml")));
        assertEquals("new-template", Files.readString(target.resolve("Contents/Resources/PPT模板/worship.pptx")));
    }

    @Test
    public void rollsBackEveryReplacedFileWhenLauncherCannotStart() throws Exception {
        Path root = temporary.getRoot().toPath();
        Path target = root.resolve("installed");
        Path source = root.resolve("staged");
        createInstallTree(target, "old");
        createInstallTree(source, "new");
        Files.writeString(source.resolve("new-file.txt"), "new");
        Files.writeString(target.resolve("config/worship-procedure.xml"), "old-procedure");
        Files.writeString(source.resolve("config/worship-procedure.xml"), "new-procedure");
        try {
            UpdateInstallerMain.install(source, target, root.resolve("backup"), "config", "missing-launcher.exe");
            fail("不存在的启动器必须导致回滚");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("安装失败"));
        }
        assertEquals("old-template", Files.readString(target.resolve("PPT模板/worship.pptx")));
        assertEquals("old-database", Files.readString(target.resolve("数据库/test.db")));
        assertEquals("sample=old-config\n", Files.readString(target.resolve("config/worship-ppt.config")));
        assertEquals("old-procedure", Files.readString(target.resolve("config/worship-procedure.xml")));
        assertFalse(Files.exists(target.resolve("new-file.txt")));
    }

    @Test
    public void leavesUnrelatedUserFilesUntouched() throws Exception {
        Path root = temporary.getRoot().toPath();
        Path target = root.resolve("installed");
        Path source = root.resolve("staged");
        createInstallTree(target, "old");
        createInstallTree(source, "new");
        Files.writeString(target.resolve("my-presentation.pptx"), "user-output");
        UpdateInstallerMain.install(source, target, root.resolve("backup"), "config", "");
        assertEquals("user-output", Files.readString(target.resolve("my-presentation.pptx")));
    }

    @Test
    public void helperRunsWithOnlyItsOwnClassAndJdk() throws Exception {
        Path root = temporary.getRoot().toPath();
        Path target = root.resolve("installed");
        Path source = root.resolve("staged");
        Path work = Files.createDirectory(root.resolve("work"));
        createInstallTree(target, "old");
        createInstallTree(source, "new");
        Properties plan = new Properties();
        plan.setProperty("parentPid", Long.toString(Long.MAX_VALUE));
        plan.setProperty("target", target.toString());
        plan.setProperty("source", source.toString());
        plan.setProperty("config", "config");
        plan.setProperty("launcher", "");
        plan.setProperty("backup", root.resolve("backup").toString());
        Path planFile = work.resolve("install.properties");
        try (OutputStream output = Files.newOutputStream(planFile)) {
            plan.store(output, "test");
        }
        Path helper = root.resolve("helper");
        UpdateInstallService.stageHelperClasses(helper);
        Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        Process child = new ProcessBuilder(java.toString(), "-cp", helper.toString(),
                UpdateInstallerMain.class.getName(), planFile.toString()).redirectErrorStream(true)
                .redirectOutput(work.resolve("test.log").toFile()).start();
        try {
            assertTrue("独立更新器未能退出", child.waitFor(15, TimeUnit.SECONDS));
            assertEquals(Files.readString(work.resolve("test.log")), 0, child.exitValue());
            assertTrue(Files.exists(work.resolve("installer.ready")));
            assertTrue(Files.exists(work.resolve("install-success.txt")));
            String log = Files.readString(work.resolve("test.log"));
            assertTrue(log.contains("[BOOT]"));
            assertTrue(log.contains("[WAIT_OLD_EXIT]"));
            assertTrue(log.contains("[PRESERVE_CONFIG_DONE]"));
            assertTrue(log.contains("[CONFIG_MIGRATION_DONE]"));
            assertTrue(log.contains("[COPY_NEW_DONE]"));
            assertTrue(log.contains("[SUCCESS]"));
            assertEquals("new-template", Files.readString(target.resolve("PPT模板/worship.pptx")));
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly().waitFor();
            }
        }
    }

    private static void createInstallTree(Path root, String version) throws Exception {
        Files.createDirectories(root.resolve("config"));
        Files.createDirectories(root.resolve("PPT模板"));
        Files.createDirectories(root.resolve("数据库"));
        Files.writeString(root.resolve("config/worship-ppt.config"), "sample=" + version + "-config\n");
        Files.writeString(root.resolve("PPT模板/worship.pptx"), version + "-template");
        Files.writeString(root.resolve("数据库/test.db"), version + "-database");
    }
}
