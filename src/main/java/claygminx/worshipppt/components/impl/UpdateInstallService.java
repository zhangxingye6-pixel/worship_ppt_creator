package claygminx.worshipppt.components.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;

/**
 * 为已完成摘要校验的发行包准备安装事务。
 *
 * <p>只接受从正式安装 JAR 启动的应用，拒绝修改 IDEA 的源码目录。
 * 更新进程使用解压包中的 Java，避免占用即将替换的旧运行时。</p>
 */
public class UpdateInstallService {
    private static final Logger logger = LoggerFactory.getLogger(UpdateInstallService.class);
    private static final long MAX_EXPANDED_BYTES = 2L * 1024 * 1024 * 1024;

    /** 当前安装位置，不依赖用户选择的下载目录。 */
    public record Installation(Path root, String config, String launcher, boolean mac) {
    }

    /** 每次尝试使用独立目录，日志、握手标记和安装计划不会与上一次混用。 */
    record PreparedInstallation(Path source, Path work) {
    }

    /**
     * 从当前类所在 JAR 定位程序根目录和启动器。
     *
     * @return 当前正式安装的位置
     * @throws IOException 开发模式、未知包结构或安装目录不可写
     */
    public Installation locateInstallation() throws IOException {
        try {
            Path jar = Path.of(UpdateInstallService.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .toRealPath();
            logger.info("[UPDATE][LOCATE] os={}, arch={}, javaVersion={}, javaHome={}, codeSource={}",
                    System.getProperty("os.name"), System.getProperty("os.arch"),
                    System.getProperty("java.version"), System.getProperty("java.home"), jar);
            if (!Files.isRegularFile(jar) || !jar.getFileName().toString().endsWith(".jar")) {
                throw new IOException("当前为开发运行，不能自动安装。安装包已保留，可手动使用。");
            }
            boolean mac = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
            for (Path root = jar.getParent(); root != null; root = root.getParent()) {
                if (mac && root.getFileName() != null && root.getFileName().toString().endsWith(".app")) {
                    String config = "Contents/Resources/config";
                    requireResources(root.resolve("Contents/Resources"));
                    return new Installation(root, config, macLauncher(root), true);
                }
                if (!mac && Files.isDirectory(root.resolve("config"))
                        && Files.isDirectory(root.resolve("PPT模板"))) {
                    requireResources(root);
                    return new Installation(root, "config", windowsLauncher(root), false);
                }
            }
            throw new IOException("无法识别当前安装目录，安装包已保留，请手动安装");
        } catch (java.net.URISyntaxException e) {
            throw new IOException("无法定位当前程序", e);
        }
    }

    /**
     * 校验压缩包路径、解压到独立目录，并检查新版资源和运行时。
     *
     * @param archive 下载并通过 SHA-256 校验的 ZIP
     * @param installation 当前安装信息
     * @return 解压后的新版本根目录
     * @throws IOException ZIP 不安全、结构不兼容、权限不足或解压失败
     */
    PreparedInstallation prepare(Path archive, Installation installation) throws IOException {
        Path work = Files.createTempDirectory(archive.getParent(), "install-");
        trace(work, "ENV", "os=" + System.getProperty("os.name") + ", arch=" + System.getProperty("os.arch")
                + ", javaVersion=" + System.getProperty("java.version") + ", javaHome=" + System.getProperty("java.home"));
        trace(work, "PLAN", "archive=" + archive + ", bytes=" + Files.size(archive)
                + ", target=" + installation.root() + ", config=" + installation.config()
                + ", launcher=" + installation.launcher() + ", mac=" + installation.mac());
        try {
            Path source = prepareFiles(archive, installation, work);
            trace(work, "PREPARED", "source=" + source + ", logDirectory=" + work);
            return new PreparedInstallation(source, work);
        } catch (IOException | RuntimeException failure) {
            trace(work, "PREPARE_FAILED", failure.toString());
            writeFailure(work, failure);
            throw new IOException(failure.getMessage() + "\n更新日志：" + work, failure);
        }
    }

    private Path prepareFiles(Path archive, Installation installation, Path work) throws IOException {
        Path target = installation.root().toRealPath();
        trace(work, "PERMISSIONS", "target=" + target + ", writable=" + Files.isWritable(target)
                + ", parent=" + target.getParent() + ", parentWritable=" + Files.isWritable(target.getParent()));
        trace(work, "DISK", "targetUsableBytes=" + Files.getFileStore(target).getUsableSpace()
                + ", stagingUsableBytes=" + Files.getFileStore(work).getUsableSpace());
        if (!Files.isWritable(target) || !Files.isWritable(target.getParent())) {
            throw new IOException("安装目录不可写，请将应用移动到有写权限的位置后重试");
        }
        Path stage = Files.createDirectory(work.resolve("package"));
        trace(work, "EXTRACT_BEGIN", "destination=" + stage);
        if (stage.toRealPath().startsWith(target)) {
            throw new IOException("下载目录位于安装目录中，不能安全替换，请手动安装");
        }
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            long total = 0;
            List<? extends ZipEntry> entries = zip.stream().toList();
            if (entries.size() > 50_000) {
                throw new IOException("安装包文件数量超过限制");
            }
            for (ZipEntry entry : entries) {
                String name = entry.getName().replace('\\', '/');
                Path destination = stage.resolve(name).normalize();
                if (name.startsWith("/") || name.contains(":") || !destination.startsWith(stage)
                        || List.of(name.split("/")).contains("..")) {
                    throw new IOException("安装包包含不安全路径：" + name);
                }
                if (entry.getSize() < 0 || entry.getSize() > MAX_EXPANDED_BYTES - total) {
                    throw new IOException("安装包解压大小超过限制");
                }
                total += entry.getSize();
                if (!installation.mac()) {
                    if (entry.isDirectory()) {
                        Files.createDirectories(destination);
                    } else {
                        Files.createDirectories(destination.getParent());
                        try (InputStream input = zip.getInputStream(entry);
                             OutputStream output = Files.newOutputStream(destination)) {
                            byte[] buffer = new byte[64 * 1024];
                            long written = 0;
                            int count;
                            while ((count = input.read(buffer)) != -1) {
                                written += count;
                                if (written > entry.getSize()) {
                                    throw new IOException("安装包文件实际大小与声明不符");
                                }
                                output.write(buffer, 0, count);
                            }
                        }
                    }
                }
            }
            trace(work, "ZIP_VALIDATED", "entries=" + entries.size() + ", expandedBytes=" + total);
        }
        if (installation.mac()) {
            // ditto 保留 macOS 启动器、JRE 的执行权限及运行时内部符号链接。
            runExtraction(archive, stage, work);
        }
        trace(work, "EXTRACT_DONE", "destination=" + stage);
        List<Path> roots;
        try (Stream<Path> paths = Files.walk(stage, 4)) {
            roots = paths.filter(path -> installation.mac()
                    ? path.getFileName().toString().endsWith(".app")
                    : Files.isRegularFile(path.resolve("config/worship-ppt.config"))).toList();
        }
        if (roots.size() != 1) {
            trace(work, "LAYOUT_REJECTED", "candidateRoots=" + roots);
            throw new IOException("安装包中没有唯一的应用目录，已停止安装");
        }
        Path source = roots.get(0);
        trace(work, "LAYOUT", "source=" + source + ", nativeLauncher=" + source.resolve(installation.launcher())
                + ", launcherExecutable=" + Files.isExecutable(source.resolve(installation.launcher())));
        requireResources(installation.mac() ? source.resolve("Contents/Resources") : source);
        if (!Files.isRegularFile(source.resolve(installation.launcher()))
                || (installation.mac() && !Files.isExecutable(source.resolve(installation.launcher())))) {
            throw new IOException("新版启动器名称与当前安装不一致，请手动安装");
        }
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path) && !path.toRealPath().startsWith(source.toRealPath())) {
                    throw new IOException("安装包符号链接指向应用目录之外");
                }
            }
        }
        trace(work, "SYMLINKS_VALIDATED", "all links remain inside " + source);
        return source;
    }

    /**
     * 启动外部更新器；只有确认更新器已启动后，调用方才可以退出旧程序。
     *
     * @param archive 已校验的发行包
     * @return 更新日志所在目录
     * @throws IOException 准备失败或外部运行时无法启动
     */
    public Path start(Path archive) throws IOException {
        Installation installation = locateInstallation();
        PreparedInstallation prepared = prepare(archive, installation);
        Path source = prepared.source();
        Path work = prepared.work();
        try {
            return launchInstaller(installation, source, work);
        } catch (IOException | RuntimeException failure) {
            trace(work, "HELPER_START_FAILED", failure.toString());
            writeFailure(work, failure);
            throw new IOException(failure.getMessage() + "\n更新日志：" + work, failure);
        }
    }

    private Path launchInstaller(Installation installation, Path source, Path work) throws IOException {
        Path java;
        try (Stream<Path> paths = Files.walk(source, 12)) {
            List<Path> executables = paths.filter(path -> Files.isRegularFile(path)
                    && path.getParent().getFileName().toString().equals("bin")
                    && path.getFileName().toString().equals(installation.mac() ? "java" : "javaw.exe")).toList();
            if (executables.size() != 1) {
                trace(work, "RUNTIME_REJECTED", "javaCandidates=" + executables);
                throw new IOException("安装包缺少唯一的 Java 运行时，不能自动安装");
            }
            java = executables.get(0);
        }
        trace(work, "RUNTIME", "java=" + java + ", executable=" + Files.isExecutable(java));
        if (installation.mac()) {
            // 仅采集诊断，不删除 quarantine、不更改签名或绕过系统安全检查。
            diagnose(work, "macos-architecture.log", "/usr/bin/uname", "-m");
            diagnose(work, "macos-binaries.log", "/usr/bin/file", java.toString(),
                    source.resolve(installation.launcher()).toString());
            diagnose(work, "macos-current-attributes.log", "/usr/bin/xattr", "-l", installation.root().toString());
            diagnose(work, "macos-new-attributes.log", "/usr/bin/xattr", "-l", source.toString());
        }
        Path helpers = work.resolve("helper");
        stageHelperClasses(helpers);
        Properties plan = new Properties();
        plan.setProperty("parentPid", Long.toString(ProcessHandle.current().pid()));
        plan.setProperty("source", source.toString());
        plan.setProperty("target", installation.root().toString());
        plan.setProperty("config", installation.config());
        plan.setProperty("launcher", installation.launcher());
        Path backup = installation.root().resolveSibling("." + installation.root().getFileName()
                + "-backup-" + UUID.randomUUID());
        plan.setProperty("backup", backup.toString());
        trace(work, "BACKUP_PLAN", "backup=" + backup
                + ", configPolicy=migrateKeys, procedurePolicy=replace, templatePolicy=replace");
        Path planFile = work.resolve("install.properties");
        try (OutputStream output = Files.newOutputStream(planFile)) {
            plan.store(output, "Worship PPT update transaction");
        }
        trace(work, "HELPER_START", "java=" + java + ", classPath=" + helpers + ", plan=" + planFile);
        Process installer = new ProcessBuilder(java.toString(), "-Dfile.encoding=UTF-8", "-cp", helpers.toString(),
                UpdateInstallerMain.class.getName(), planFile.toString()).directory(work.toFile())
                .redirectErrorStream(true).redirectOutput(work.resolve("installer.log").toFile()).start();
        trace(work, "HELPER_PID", "pid=" + installer.pid() + ", parentPid=" + ProcessHandle.current().pid());
        try {
            for (int attempt = 0; attempt < 100; attempt++) {
                if (Files.exists(work.resolve("installer.ready")) && installer.isAlive()) {
                    trace(work, "HELPER_READY", "pid=" + installer.pid() + "; original application may now exit");
                    return work;
                }
                if (!installer.isAlive()) {
                    trace(work, "HELPER_EXITED", "exitCode=" + installer.exitValue());
                    break;
                }
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            installer.destroy();
            throw new IOException("等待更新器启动时被中断", e);
        }
        installer.destroy();
        throw new IOException("更新器未能启动，当前程序未被替换。请查看：" + work.resolve("installer.log"));
    }

    private static void requireResources(Path root) throws IOException {
        if (!Files.isRegularFile(root.resolve("config/worship-ppt.config"))
                || !Files.isRegularFile(root.resolve("PPT模板/worship.pptx"))
                || !Files.isDirectory(root.resolve("数据库"))) {
            throw new IOException("安装包或当前程序缺少配置、PPT 模板或数据库");
        }
    }

    /** 独立更新器只携带这两个仅依赖 JDK 的类，不依赖旧安装目录中的 JAR。 */
    static void stageHelperClasses(Path helpers) throws IOException {
        for (Class<?> component : List.of(UpdateInstallerMain.class, UpdateConfigMigration.class)) {
            String resource = component.getName().replace('.', '/') + ".class";
            Path destination = helpers.resolve(resource);
            Files.createDirectories(destination.getParent());
            try (InputStream input = component.getClassLoader().getResourceAsStream(resource)) {
                if (input == null) {
                    throw new IOException("更新器组件缺失：" + component.getSimpleName());
                }
                Files.copy(input, destination);
            }
        }
    }

    private static String windowsLauncher(Path root) throws IOException {
        try (Stream<Path> paths = Files.list(root)) {
            List<Path> executables = paths.filter(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe")).toList();
            if (executables.size() != 1) {
                throw new IOException("无法识别唯一的 Windows 启动器，请手动安装");
            }
            return executables.get(0).getFileName().toString();
        }
    }

    /** 从标准 plist 读取真正的 macOS 入口，禁用外部 DTD 和实体加载。 */
    private static String macLauncher(Path root) throws IOException {
        try (InputStream input = Files.newInputStream(root.resolve("Contents/Info.plist"))) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            org.w3c.dom.Document document = factory.newDocumentBuilder().parse(input);
            org.w3c.dom.NodeList keys = document.getElementsByTagName("key");
            for (int index = 0; index < keys.getLength(); index++) {
                Node key = keys.item(index);
                if (!"CFBundleExecutable".equals(key.getTextContent())) {
                    continue;
                }
                Node value = key.getNextSibling();
                while (value != null && !(value instanceof Element)) {
                    value = value.getNextSibling();
                }
                if (value != null && "string".equals(value.getNodeName())) {
                    String name = value.getTextContent();
                    if (name.matches("[^/\\\\]+") && !name.equals(".") && !name.equals("..")) {
                        return "Contents/MacOS/" + name;
                    }
                }
            }
            throw new IOException("macOS 应用缺少有效启动器名称");
        } catch (javax.xml.parsers.ParserConfigurationException | org.xml.sax.SAXException e) {
            throw new IOException("无法读取 macOS 应用启动信息", e);
        }
    }

    private static void runExtraction(Path archive, Path stage, Path work) throws IOException {
        trace(work, "DITTO_START", "/usr/bin/ditto -x -k; archive=" + archive + ", destination=" + stage
                + ", output=" + work.resolve("extraction.log"));
        Process extraction = new ProcessBuilder("/usr/bin/ditto", "-x", "-k", archive.toString(), stage.toString())
                .redirectErrorStream(true).redirectOutput(work.resolve("extraction.log").toFile()).start();
        try {
            if (!extraction.waitFor(120, TimeUnit.SECONDS)) {
                extraction.destroyForcibly();
                trace(work, "DITTO_TIMEOUT", "pid=" + extraction.pid() + ", timeoutSeconds=120");
                throw new IOException("解压安装包超时");
            }
            trace(work, "DITTO_EXIT", "pid=" + extraction.pid() + ", exitCode=" + extraction.exitValue());
            if (extraction.exitValue() != 0) {
                throw new IOException("macOS 安装包解压失败，请查看解压日志");
            }
        } catch (InterruptedException e) {
            extraction.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("解压被中断", e);
        }
    }

    /** 准备阶段日志同时进入应用日志和本次更新目录，不输出配置正文或代理凭据。 */
    private static void trace(Path work, String phase, String detail) {
        logger.info("[UPDATE][{}] {}", phase, detail);
        try {
            Files.writeString(work.resolve("preparation.log"), OffsetDateTime.now() + " [" + phase + "] "
                    + detail + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException failure) {
            logger.warn("无法写入更新准备日志：{}", work, failure);
        }
    }

    /** 诊断命令失败只记录，不影响安装流程，也不修改 macOS 安全属性。 */
    private static void diagnose(Path work, String file, String... command) {
        Process process = null;
        try {
            trace(work, "MAC_DIAGNOSTIC_BEGIN", "command=" + List.of(command) + ", output=" + file);
            process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(work.resolve(file).toFile()).start();
            if (process.waitFor(5, TimeUnit.SECONDS)) {
                trace(work, "MAC_DIAGNOSTIC_DONE", "output=" + file + ", exitCode=" + process.exitValue());
            } else {
                process.destroyForcibly();
                trace(work, "MAC_DIAGNOSTIC_TIMEOUT", "output=" + file);
            }
        } catch (IOException failure) {
            trace(work, "MAC_DIAGNOSTIC_UNAVAILABLE", "output=" + file + ", reason=" + failure);
        } catch (InterruptedException failure) {
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            trace(work, "MAC_DIAGNOSTIC_INTERRUPTED", "output=" + file);
        }
    }

    /** 将完整异常链和 suppressed 回滚异常保留在本次目录，不能只记录异常消息。 */
    private static void writeFailure(Path work, Throwable failure) {
        logger.error("更新准备失败，诊断目录：{}", work, failure);
        try (java.io.PrintWriter output = new java.io.PrintWriter(
                Files.newBufferedWriter(work.resolve("preparation-error.log"), StandardCharsets.UTF_8))) {
            failure.printStackTrace(output);
        } catch (IOException logFailure) {
            logger.warn("无法写入更新异常堆栈：{}", work, logFailure);
        }
    }
}
