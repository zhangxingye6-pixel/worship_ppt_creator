package claygminx.worshipppt.common.config;

import claygminx.worshipppt.exception.SystemException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import javax.swing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/**
 * 系统配置
 * <p>直接加载根目录下config目录中的worship-ppt.config配置文件。</p>
 */
@Slf4j
public class SystemConfig {

    /**
     * 应用配置文件夹（用于缓存敬拜实体等数据）
     */
    public final static String APP_CONFIG_DIR_PATH = ".worship-ppt";

    /**
     * 配置文件名称
     */
    public final static String CONFIG_FILE_NAME = "worship-ppt.config";

    /**
     * 核心配置
     */
    public final static String CORE_PROPERTIES = "core.properties";

    // 用户配置文件路径（运行时赋值）
    public static String USER_CONFIG_FILE_PATH = "";

    /**
     * 禁止用户自定义，只能是jar包内定义的配置参数
     */
    private final static String[] EXCLUDE_NAMESPACE = new String[]{
            "github", "gitee", "project"
    };

    /**
     * 系统属性实例对象
     */
    public final static Properties properties = new Properties();

    // 完成对properties的初始化, 初始化的结果是用户配置与核心配置会合并到properties中
    static {
        ClassLoader classLoader = SystemConfig.class.getClassLoader();

        // 1.从根目录的config/worship-ppt.config加载用户配置
        Properties userProperties = null;
        String userConfigPath = "config/" + CONFIG_FILE_NAME;
        USER_CONFIG_FILE_PATH = userConfigPath;

        File configFile = new File(userConfigPath);
        if (!configFile.exists()) {
            logger.error("配置文件{}不存在，系统退出！", configFile.getAbsolutePath());
            JOptionPane.showMessageDialog(
                    null,
                    "配置文件" + configFile.getAbsolutePath() + "不存在，系统退出！",
                    "错误提示",
                    JOptionPane.ERROR_MESSAGE
            );
            System.exit(1);
        }

        try {
            userProperties = loadUserProperties(userConfigPath);
        } catch (Exception e) {
            logger.error("用户配置加载失败！", e);
            JOptionPane.showMessageDialog(
                    null,
                    "用户配置加载失败！",
                    "错误提示",
                    JOptionPane.ERROR_MESSAGE
            );
            System.exit(1);
        }

        // 2.加载核心配置
        Properties coreProperties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(
                // 从ClassPath中获取核心配置
                Objects.requireNonNull(classLoader.getResourceAsStream(CORE_PROPERTIES)),
                StandardCharsets.UTF_8)) {
            coreProperties.load(reader);
            logger.info("核心配置加载成功");

            Set<Object> keySet = coreProperties.keySet();
            for (Object key : keySet) {
                logger.info("{}={}", key, coreProperties.get(key));
            }
        } catch (Exception e) {
            logger.error("{}加载失败！", CORE_PROPERTIES, e);
            JOptionPane.showMessageDialog(
                    null,
                    CORE_PROPERTIES + "加载失败！",
                    "错误提示",
                    JOptionPane.ERROR_MESSAGE
            );
            System.exit(1);
        }

        // 3.合并配置
        try {
            // 合并核心配置
            properties.putAll(coreProperties);
            logger.info("合并了核心配置");

            mergeUserProperties(userProperties);
        } catch (Exception e) {
            logger.error("合并配置失败！", e);
            JOptionPane.showMessageDialog(
                    null,
                    "合并配置失败！",
                    "错误提示",
                    JOptionPane.ERROR_MESSAGE
            );
            System.exit(1);
        }
    }


    private SystemConfig() {
    }


    /**
     * 三个配置配重载方法，如果取不到配置中的自定义值，就返回默认值
     *
     * @param propertiesKey
     * @param defaultConfig
     * @return
     */
    public static String getUserConfigOrDefault(String propertiesKey, String defaultConfig) {
        String string = getString(defaultConfig);
        if (string.isEmpty()) {
            logger.info("未获取到用户配置， 使用默认配置：" + defaultConfig);
            return defaultConfig;
        }
        logger.info("获取到用户配置，使用自定义配置：" + string);
        return string;
    }

    public static double getUserConfigOrDefault(String propertiesKey, double defaultConfig) {
        double doubleValue = getDouble(propertiesKey);
        if (doubleValue == -1.0) {
            logger.info("未获取到用户配置， 使用默认配置：" + defaultConfig);
            return defaultConfig;
        }
        logger.info("获取到用户配置，使用自定义配置：" + doubleValue);
        return doubleValue;
    }

    public static int getUserConfigOrDefault(String propertiesKey, int defaultConfig) {
        int intValue = getInt(propertiesKey);
        if (intValue == -1) {
            logger.info("未获取到用户配置， 使用默认配置：" + defaultConfig);
            return defaultConfig;
        }
        logger.info("获取到用户配置，使用自定义配置：" + intValue);
        return intValue;
    }

    /**
     * 获取配置中的字符串值
     *
     * @param key 键
     * @return 系统值
     */
    public static String getString(String key) {
        String strValue = properties.getProperty(key);
        if (strValue == null || strValue.isEmpty()) {
            return "";
        }
        return strValue;
    }

    /**
     * 获取配置中的int值
     *
     * @param key 键
     * @return 系统值
     */
    public static int getInt(String key) {
        String strValue = properties.getProperty(key);
        if (strValue == null || strValue.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(strValue);
        } catch (Exception e) {
            throw new SystemException("用户配置文件key = " + key + "获取int值失败！", e);
        }
    }

    /**
     * 获取配置中的double值
     *
     * @param key 键
     * @return 系统值
     */
    public static double getDouble(String key) {
        String strValue = properties.getProperty(key);
        if (strValue == null || strValue.isEmpty()) {
            return -1.0;
        }
        try {
            return Double.parseDouble(strValue);
        } catch (Exception e) {
            throw new SystemException("用户配置中key = " + key + "获取double值失败！", e);
        }
    }

    public static boolean getBoolean(String key) {
        String strValue = properties.getProperty(key);
        if (strValue == null || strValue.isEmpty()) {
            return false;
        }
        try {
            return Boolean.parseBoolean(strValue);
        } catch (Exception e) {
            throw new SystemException("用户配置中key = " + key + "获取boolean值失败！", e);
        }
    }

    /**
     * 更新用户配置的路径（已废弃，配置路径固定为config/worship-ppt.config）
     *
     * @param propFilePath 配置文件路径（已忽略）
     * @throws IOException 不再抛出异常
     */
    @Deprecated
    public static void update(String propFilePath) throws IOException {
        logger.warn("update方法已废弃，配置路径固定为config/worship-ppt.config");
    }

    /**
     * 加载用户配置
     *
     * @param propFilePath
     * @return
     * @throws IOException
     */
    private static Properties loadUserProperties(String propFilePath) throws IOException {
        Properties userProperties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(propFilePath), StandardCharsets.UTF_8)) {
            userProperties.load(reader);
            logger.info("用户配置加载成功");
            logger.debug("配置路径: " + propFilePath);

            // 获取用户配置中所有的key
            Set<Object> keySet = userProperties.keySet();
            for (Object key : keySet) {
                logger.info("{}={}", key, userProperties.get(key));
            }
        }
        return userProperties;
    }

    /**
     * 合并用户配置
     *
     * @param userProperties
     */
    private static void mergeUserProperties(Properties userProperties) {
        Set<Object> keySet = userProperties.keySet();
        for (Object keyObj : keySet) {
            String key = (String) keyObj;
            String[] split = key.split("[.]");
            String ns = split[0];
            boolean found = false;
            // 查找用户是否配置了不可配置的内容, 找到的内容不进行合并
            for (int i = 0; i < EXCLUDE_NAMESPACE.length; i++) {
                if (EXCLUDE_NAMESPACE[i].equals(ns)) {
                    found = true;
                    break;
                }
            }
            if (found) {
                logger.info("跳过{}", key);
            } else {
                properties.put(key, userProperties.get(key));
            }
        }
        logger.info("合并了用户配置");
    }
}
