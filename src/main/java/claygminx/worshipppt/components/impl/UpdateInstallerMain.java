package claygminx.worshipppt.components.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.time.OffsetDateTime;
import java.nio.charset.StandardCharsets;

/**
 * 独立更新进程入口，只依赖 JDK，运行文件与运行时均位于安装目录之外。
 *
 * <p>先等待原进程退出，再备份并替换发布包中的文件。任何替换或启动失败均尝试回滚；
 * 日志和安装状态保留在本次更新目录中，旧文件保留在安装目录旁，便于失败后手动恢复。</p>
 */
public final class UpdateInstallerMain {
    private UpdateInstallerMain() {
    }

    /**
     * 等待宿主退出后安装，并将结果写入更新目录。
     *
     * @param args 安装计划文件的绝对路径
     * @throws Exception 安装或回滚失败，异常同时记录到外部进程日志
     */
    public static void main(String[] args) throws Exception {
        // Windows 与 macOS 的默认字符集可能不同，诊断文件统一为 UTF-8。
        System.setOut(new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new java.io.PrintStream(System.err, true, StandardCharsets.UTF_8));
        log("BOOT", "pid=" + ProcessHandle.current().pid() + ", os=" + System.getProperty("os.name")
                + ", osVersion=" + System.getProperty("os.version") + ", arch=" + System.getProperty("os.arch")
                + ", javaVersion=" + System.getProperty("java.version") + ", javaHome=" + System.getProperty("java.home"));
        Path planFile = Path.of(args[0]);
        Path work = planFile.getParent();
        Properties plan = new Properties();
        try (InputStream input = Files.newInputStream(planFile)) {
            plan.load(input);
        }
        Path target = Path.of(plan.getProperty("target"));
        log("PLAN", "plan=" + planFile + ", source=" + plan.getProperty("source") + ", target=" + target
                + ", config=" + plan.getProperty("config") + ", launcher=" + plan.getProperty("launcher")
                + ", backup=" + plan.getProperty("backup"));
        Path lockPath = target.resolveSibling("." + target.getFileName() + "-update.lock");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock == null) {
                throw new IOException("已有更新任务正在处理此安装目录");
            }
            log("LOCK_ACQUIRED", "lock=" + lockPath);
            Files.writeString(work.resolve("installer.ready"), "ready");
            long pid = Long.parseLong(plan.getProperty("parentPid"));
            ProcessHandle parent = ProcessHandle.of(pid).orElse(null);
            log("WAIT_OLD_EXIT", "parentPid=" + pid + ", alive=" + (parent != null && parent.isAlive())
                    + ", timeoutSeconds=120");
            if (parent != null) {
                parent.onExit().get(120, TimeUnit.SECONDS);
            }
            log("OLD_EXITED", "parentPid=" + pid);
            Path source = Path.of(plan.getProperty("source"));
            Path backup = Path.of(plan.getProperty("backup"));
            install(source, target, backup, plan.getProperty("config"), plan.getProperty("launcher"), work);
            Files.writeString(work.resolve("install-success.txt"), "安装完成，旧文件保留在：" + backup + "\n");
            log("SUCCESS", "files installed; launcher invocation completed; backup=" + backup
                    + "; application health has not been verified");
        } catch (Exception failure) {
            log("FAILED", failure.toString());
            failure.printStackTrace(System.err);
            Files.writeString(work.resolve("install-failed.txt"), failure.toString()
                    + "\n详见 installer.log；旧文件备份位置：" + plan.getProperty("backup") + "\n");
            throw failure;
        }
    }

    /**
     * 按发布包的顶层项目替换文件，保留配置，失败时逆序恢复已操作的项目。
     *
     * @param source 已校验并解压的新版本根目录
     * @param target 当前安装根目录
     * @param backup 空的备份目录，成功后也不删除
     * @param configRelative 相对于安装根目录的用户配置目录
     * @param launcherRelative 启动器的相对路径；测试传空字符串以禁止启动
     * @throws IOException 文件操作或启动失败
     */
    static void install(Path source, Path target, Path backup,
                        String configRelative, String launcherRelative) throws IOException {
        install(source, target, backup, configRelative, launcherRelative, backup.getParent());
    }

    /** 带独立诊断目录的安装事务，重启输出不能写入即将被替换的程序目录。 */
    private static void install(Path source, Path target, Path backup,
                                String configRelative, String launcherRelative, Path diagnostics) throws IOException {
        long started = System.nanoTime();
        source = source.toRealPath();
        target = target.toRealPath();
        log("PRECHECK", "source=" + source + ", target=" + target + ", backup=" + backup);
        if (source.startsWith(target) || target.startsWith(source)
                || backup.toAbsolutePath().normalize().startsWith(target)) {
            throw new IOException("更新暂存目录必须位于安装目录之外");
        }
        Files.createDirectory(backup);
        Path config = target.resolve(configRelative).normalize();
        if (!config.startsWith(target) || !Files.isDirectory(config, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("用户配置目录不合法，已停止安装");
        }
        Path preserved = backup.resolve("preserved-config");
        log("PRESERVE_CONFIG_BEGIN", "from=" + config + ", to=" + preserved);
        copyTree(config, preserved);
        log("PRESERVE_CONFIG_DONE", "config=" + config + "; original file contents and comments retained");
        Path migrated = backup.resolve("migrated-config");
        Path migrationReport = diagnostics.resolve("config-migration.txt");
        log("CONFIG_MIGRATION_BEGIN", "oldConfig=" + preserved + ", defaults=" + source.resolve(configRelative)
                + ", destination=" + migrated);
        UpdateConfigMigration.prepare(preserved, source.resolve(configRelative), migrated, migrationReport);
        log("CONFIG_MIGRATION_DONE", "report=" + migrationReport
                + "; commonKeys=retainUserValue, addedKeys=newDefault, removedKeys=archive, procedureXml=replace");
        List<String> changed = new ArrayList<>();
        List<String> saved = new ArrayList<>();
        try {
            try (Stream<Path> entries = Files.list(source)) {
                for (Path entry : entries.sorted().toList()) {
                    String name = entry.getFileName().toString();
                    Path effectiveSource = entry;
                    // Windows 直接安装迁移目录；macOS 在 Contents 替换后再安装同一份迁移结果。
                    if (name.equals(configRelative)) {
                        effectiveSource = migrated;
                    }
                    Path destination = target.resolve(name);
                    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                        log("BACKUP_BEGIN", "from=" + destination + ", to=" + backup.resolve(name));
                        Files.move(destination, backup.resolve(name));
                        saved.add(name);
                        log("BACKUP_DONE", "name=" + name);
                    }
                    changed.add(name);
                    log("COPY_NEW_BEGIN", "from=" + effectiveSource + ", to=" + destination);
                    copyTree(effectiveSource, destination);
                    log("COPY_NEW_DONE", "name=" + name);
                }
            }
            // macOS 的配置位于 Contents 内，统一换成已验证的配置迁移结果。
            if (Path.of(configRelative).getParent() != null) {
                log("RESTORE_CONFIG_BEGIN", "from=" + migrated + ", to=" + config);
                deleteTree(config);
                copyTree(migrated, config);
                log("RESTORE_CONFIG_DONE", "config=" + config);
            }
            log("FILES_INSTALLED", "replacedEntries=" + changed + ", elapsedMillis="
                    + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            if (!launcherRelative.isEmpty()) {
                startApplication(target, launcherRelative, diagnostics.resolve("restart-new.log"));
            }
        } catch (Exception failure) {
            log("ROLLBACK_BEGIN", "reason=" + failure + ", changedEntries=" + changed);
            failure.printStackTrace(System.err);
            IOException result = new IOException("安装失败，正在恢复旧版本：" + failure.getMessage(), failure);
            for (int index = changed.size() - 1; index >= 0; index--) {
                String name = changed.get(index);
                try {
                    log("ROLLBACK_ENTRY_BEGIN", "name=" + name + ", hadBackup=" + saved.contains(name));
                    deleteTree(target.resolve(name));
                    if (saved.contains(name)) {
                        Files.move(backup.resolve(name), target.resolve(name));
                    }
                    log("ROLLBACK_ENTRY_DONE", "name=" + name);
                } catch (IOException restoreFailure) {
                    log("ROLLBACK_ENTRY_FAILED", "name=" + name + ", reason=" + restoreFailure);
                    restoreFailure.printStackTrace(System.err);
                    result.addSuppressed(restoreFailure);
                }
            }
            log(result.getSuppressed().length == 0 ? "ROLLBACK_DONE" : "ROLLBACK_INCOMPLETE",
                    "restoreFailures=" + result.getSuppressed().length + ", backup=" + backup);
            if (result.getSuppressed().length == 0 && !launcherRelative.isEmpty()) {
                try {
                    log("RESTART_OLD", "attempting to restart restored version");
                    startApplication(target, launcherRelative, diagnostics.resolve("restart-old.log"));
                } catch (IOException restartFailure) {
                    log("RESTART_OLD_FAILED", restartFailure.toString());
                    result.addSuppressed(restartFailure);
                }
            }
            throw result;
        }
    }

    /** 直接调用原生启动器，路径作为独立参数传递，避免空格或特殊字符被 shell 解释。 */
    private static void startApplication(Path target, String launcherRelative, Path output) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(target.resolve(launcherRelative).toString());
        Path workingDirectory = target.getFileName().toString().endsWith(".app")
                ? target.resolve("Contents/Resources") : target;
        Path executable = target.resolve(launcherRelative);
        log("RESTART_BEGIN", "launcher=" + executable + ", exists=" + Files.exists(executable)
                + ", executable=" + Files.isExecutable(executable) + ", cwd=" + workingDirectory
                + ", stdoutAndStderr=" + output);
        Process process = builder.directory(workingDirectory.toFile()).redirectErrorStream(true)
                .redirectOutput(output.toFile()).start();
        log("RESTART_PID", "pid=" + process.pid());
        try {
            if (process.waitFor(1, TimeUnit.SECONDS)) {
                log("RESTART_EARLY_EXIT", "pid=" + process.pid() + ", exitCode=" + process.exitValue());
                if (process.exitValue() != 0) {
                    throw new IOException("启动器退出，错误码：" + process.exitValue() + "，输出日志：" + output);
                }
            } else {
                log("RESTART_RUNNING", "pid=" + process.pid() + "; native process running after 1 second");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("等待程序重启时被中断", e);
        }
    }

    /** 复制文件树并保留权限和符号链接，不跟随链接递归到用户目录以外。 */
    static void copyTree(Path source, Path destination) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path copy = destination.resolve(source.relativize(path));
                try {
                    if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                        Files.createDirectories(copy);
                    } else {
                        Files.createDirectories(copy.getParent());
                        Files.copy(path, copy, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                } catch (IOException failure) {
                    throw new IOException("复制失败：" + path + " -> " + copy, failure);
                }
            }
        }
    }

    /** 仅删除事务中明确记录的目标，不跟随符号链接。 */
    static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    /** 使用 JDK 标准输出，确保独立更新器无需依赖应用的日志库即可记录安装过程。 */
    private static void log(String phase, String detail) {
        System.out.println(OffsetDateTime.now() + " [" + phase + "] " + detail);
    }
}
