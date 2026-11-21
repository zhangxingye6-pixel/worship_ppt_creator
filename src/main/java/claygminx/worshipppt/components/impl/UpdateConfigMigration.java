package claygminx.worshipppt.components.impl;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;

/**
 * 根据新版配置模板迁移用户配置，仅依赖 JDK，可供独立更新进程使用。
 *
 * <p>同名配置保留用户值（包括空值），新增项使用新版默认值，已移除项不再写入生效配置。
 * 使用新版注释与顺序；Properties 负责转义、重复键及续行的解析，不自行猜测配置语法。</p>
 */
final class UpdateConfigMigration {
    private static final String CONFIG_NAME = "worship-ppt.config";

    private UpdateConfigMigration() {
    }

    /**
     * 在备份目录内生成待安装的配置目录，成功前不会触碰当前安装目录。
     *
     * @param preserved 旧配置的完整快照
     * @param defaults 新版发行包的 config 目录
     * @param migrated 待生成的合并目录，必须不存在
     * @param report 只包含键名、操作和备份位置的迁移报告
     * @throws IOException 配置无效或合并失败，此时必须中止安装
     */
    static void prepare(Path preserved, Path defaults, Path migrated, Path report) throws IOException {
        String oldText = read(preserved.resolve(CONFIG_NAME));
        String template = read(defaults.resolve(CONFIG_NAME));
        Properties oldValues = parse(oldText);
        Properties newValues = parse(template);
        String merged = merge(template, oldValues, newValues);
        // 先验证合并结果，避免空值、转义或续行在生成时发生语义变化。
        Properties expected = new Properties();
        for (String key : newValues.stringPropertyNames()) {
            expected.setProperty(key, oldValues.getProperty(key, newValues.getProperty(key)));
        }
        if (!expected.equals(parse(merged))) {
            throw new IOException("配置迁移后的内容校验失败，已停止安装");
        }
        List<String> changes = new ArrayList<>();
        changes.add("配置迁移规则：保留同名项的用户值，新增项使用新版默认值，移除已废弃项。");
        changes.add("原始配置完整备份：" + preserved);
        changes.add("流程 XML 及其他随包配置文件使用新版覆盖。报告不记录配置值。");
        for (String key : new TreeSet<>(newValues.stringPropertyNames())) {
            changes.add((oldValues.containsKey(key) ? "[RETAINED] " : "[ADDED] ") + key);
        }
        for (String key : new TreeSet<>(oldValues.stringPropertyNames())) {
            if (!newValues.containsKey(key)) {
                changes.add("[REMOVED] " + key);
            }
        }
        UpdateInstallerMain.copyTree(preserved, migrated);
        try (java.util.stream.Stream<Path> paths = Files.walk(defaults)) {
            for (Path file : paths.toList()) {
                Path relative = defaults.relativize(file);
                Path destination = migrated.resolve(relative);
                if (relative.toString().equals(CONFIG_NAME)) {
                    continue;
                }
                requireSafeParents(migrated, destination);
                if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
                    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
                            && !Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS)) {
                        UpdateInstallerMain.deleteTree(destination);
                    }
                    Files.createDirectories(destination);
                } else {
                    UpdateInstallerMain.deleteTree(destination);
                    UpdateInstallerMain.copyTree(file, destination);
                    changes.add("[REPLACED_FILE] " + relative);
                }
            }
        }
        // 旧配置可能是符号链接，先移除快照中的链接，不能通过它改写目录以外的原文件。
        Files.deleteIfExists(migrated.resolve(CONFIG_NAME));
        Files.writeString(migrated.resolve(CONFIG_NAME), merged, StandardCharsets.UTF_8);
        Files.write(report, changes, StandardCharsets.UTF_8);
    }

    /** 按新版模板逐个逻辑配置项输出，共有项通过标准 Properties 编码保留旧值。 */
    private static String merge(String template, Properties oldValues, Properties newValues) throws IOException {
        String newline = template.contains("\r\n") ? "\r\n" : "\n";
        String[] lines = template.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        List<String> output = new ArrayList<>();
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            String stripped = line.stripLeading();
            if (stripped.isEmpty() || stripped.startsWith("#") || stripped.startsWith("!")) {
                output.add(line);
                continue;
            }
            StringBuilder logical = new StringBuilder(line);
            while (continues(line) && index + 1 < lines.length) {
                line = lines[++index];
                logical.append('\n').append(line);
            }
            Properties entry = parse(logical + "\n");
            if (entry.size() != 1) {
                throw new IOException("新版配置包含无法识别的逻辑配置项");
            }
            String key = entry.stringPropertyNames().iterator().next();
            if (oldValues.containsKey(key) && !oldValues.getProperty(key).equals(newValues.getProperty(key))) {
                Properties replacement = new Properties();
                replacement.setProperty(key, oldValues.getProperty(key));
                StringWriter writer = new StringWriter();
                replacement.store(writer, null);
                String encoded = writer.toString();
                // store 的首行仅为时间戳；保留后面的标准转义配置，时间戳不写入用户文件。
                output.add(encoded.substring(encoded.indexOf('\n') + 1).replaceFirst("\\r?\\n$", ""));
            } else {
                output.add(logical.toString().replace("\n", newline));
            }
        }
        return String.join(newline, output);
    }

    private static boolean continues(String line) {
        int backslashes = 0;
        for (int index = line.length() - 1; index >= 0 && line.charAt(index) == '\\'; index--) {
            backslashes++;
        }
        return backslashes % 2 != 0;
    }

    private static String read(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private static Properties parse(String text) throws IOException {
        Properties values = new Properties();
        try {
            values.load(new StringReader(text));
        } catch (IllegalArgumentException failure) {
            throw new IOException("配置中存在无效的 Unicode 转义，请根据备份检查配置后重试", failure);
        }
        return values;
    }

    /** 不通过旧配置中的目录链接写入用户目录之外的文件。 */
    private static void requireSafeParents(Path root, Path destination) throws IOException {
        for (Path parent = destination.getParent(); parent != null && parent.startsWith(root); parent = parent.getParent()) {
            if (Files.isSymbolicLink(parent)) {
                throw new IOException("配置目录包含符号链接，需要手动迁移：" + parent);
            }
        }
    }
}
