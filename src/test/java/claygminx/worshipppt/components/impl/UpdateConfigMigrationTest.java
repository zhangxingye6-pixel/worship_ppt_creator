package claygminx.worshipppt.components.impl;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.Assert.*;

/** 配置迁移必须保留语义、更新新版键集合，且不能改动备份或原始默认文件。 */
public class UpdateConfigMigrationTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void retainsUserValuesAddsDefaultsAndArchivesRemovedKeys() throws Exception {
        Path output = migrate("common=42\nremoved=old-secret\n", "# New documentation\ncommon=30\nadded=new-default\n");
        Properties values = load(output);
        assertEquals("42", values.getProperty("common"));
        assertEquals("new-default", values.getProperty("added"));
        assertFalse(values.containsKey("removed"));
        String report = Files.readString(temporary.getRoot().toPath().resolve("config-migration.txt"));
        assertTrue(report.contains("[ADDED] added"));
        assertTrue(report.contains("[REMOVED] removed"));
        assertFalse(report.contains("old-secret"));
        assertTrue(Files.readString(output).startsWith("# New documentation\n"));
        assertTrue(Files.readString(temporary.getRoot().toPath().resolve("old/worship-ppt.config")).contains("old-secret"));
    }

    @Test
    public void preservesIntentionallyEmptyUserValue() throws Exception {
        Properties values = load(migrate("proxy.port=\n", "proxy.port=7890\n"));
        assertEquals("", values.getProperty("proxy.port"));
    }

    @Test
    public void usesLatestDuplicateUserValue() throws Exception {
        Properties values = load(migrate("key=first\nkey=last\n", "key=new\nkey=newest\n"));
        assertEquals("last", values.getProperty("key"));
    }

    @Test
    public void handlesChineseEscapedKeysBackslashesAndMultilineValues() throws Exception {
        String oldText = "标题=中文\nkey\\ with\\ spaces=left\\nright\npath=C:\\\\data\\\\ppt\n"
                + "multi=first\\\n  second\n";
        Properties oldValues = parse(oldText);
        Properties values = load(migrate(oldText,
                "标题=默认\nkey\\ with\\ spaces=new\npath=new\nmulti=new\\\n  default\n"));
        assertEquals(oldValues, values);
    }

    @Test
    public void preservesLeadingAndTrailingSpacesInUserValue() throws Exception {
        Properties values = load(migrate("key=\\ value  \n", "key=default\n"));
        assertEquals(" value  ", values.getProperty("key"));
    }

    @Test
    public void preservesNewTemplateCrLfCommentsAndSectionOrder() throws Exception {
        Path output = migrate("a=user\nb=other\n", "# First\r\na=default\r\n\r\n# Second\r\nb=default\r\n");
        String text = Files.readString(output);
        assertEquals("# First\r\na=user\r\n\r\n# Second\r\nb=other\r\n", text);
    }

    @Test
    public void copiesNewWorkflowAndRetainsCompleteOldWorkflowInBackup() throws Exception {
        Path root = temporary.getRoot().toPath();
        Path old = Files.createDirectory(root.resolve("old"));
        Path defaults = Files.createDirectory(root.resolve("defaults"));
        Files.writeString(old.resolve("worship-ppt.config"), "font=30\n");
        Files.writeString(defaults.resolve("worship-ppt.config"), "font=32\n");
        Files.writeString(old.resolve("worship-procedure.xml"), "old-workflow");
        Files.writeString(defaults.resolve("worship-procedure.xml"), "new-workflow");
        Files.writeString(defaults.resolve("new-resource.txt"), "new-resource");
        UpdateConfigMigration.prepare(old, defaults, root.resolve("merged"), root.resolve("config-migration.txt"));
        assertEquals("new-workflow", Files.readString(root.resolve("merged/worship-procedure.xml")));
        assertEquals("old-workflow", Files.readString(old.resolve("worship-procedure.xml")));
        assertEquals("new-resource", Files.readString(root.resolve("merged/new-resource.txt")));
    }

    @Test
    public void acceptsUtf8BomWithoutAddingItToFirstKey() throws Exception {
        Properties values = load(migrate("\uFEFFkey=user\n", "\uFEFFkey=default\n"));
        assertEquals("user", values.getProperty("key"));
        assertEquals(1, values.size());
    }

    @Test
    public void rejectsBrokenUnicodeBeforeCreatingMergedDirectory() throws Exception {
        try {
            migrate("key=\\uZZZZ\n", "key=default\n");
            fail("应当拒绝无效的 Unicode 转义");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Unicode"));
        }
        assertFalse(Files.exists(temporary.getRoot().toPath().resolve("merged")));
    }

    private Path migrate(String oldText, String newText) throws Exception {
        Path root = temporary.getRoot().toPath();
        Path old = Files.createDirectory(root.resolve("old"));
        Path defaults = Files.createDirectory(root.resolve("defaults"));
        Files.writeString(old.resolve("worship-ppt.config"), oldText, StandardCharsets.UTF_8);
        Files.writeString(defaults.resolve("worship-ppt.config"), newText, StandardCharsets.UTF_8);
        Path merged = root.resolve("merged");
        UpdateConfigMigration.prepare(old, defaults, merged, root.resolve("config-migration.txt"));
        assertEquals(oldText, Files.readString(old.resolve("worship-ppt.config")));
        assertEquals(newText, Files.readString(defaults.resolve("worship-ppt.config")));
        return merged.resolve("worship-ppt.config");
    }

    private Properties load(Path file) throws Exception {
        return parse(Files.readString(file));
    }

    private Properties parse(String text) throws Exception {
        Properties values = new Properties();
        values.load(new StringReader(text));
        return values;
    }
}
