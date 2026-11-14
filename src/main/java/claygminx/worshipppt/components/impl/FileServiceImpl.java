package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.config.SystemConfig;
import claygminx.worshipppt.common.entity.CoverEntity;
import claygminx.worshipppt.components.FileService;
import claygminx.worshipppt.exception.FileServiceException;
import claygminx.worshipppt.exception.SystemException;
import claygminx.worshipppt.common.Dict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Scanner;

public class FileServiceImpl implements FileService {

    private final static Logger logger = LoggerFactory.getLogger(FileService.class);

    private final static String EXTENSION = ".pptx";

    private static FileService fileService;

    private FileServiceImpl() {
    }

    public static FileService getInstance() {
        if (fileService == null) {
            fileService = new FileServiceImpl();
        }
        return fileService;
    }

    @Override
    public File copyTemplate(CoverEntity coverEntity) throws FileServiceException {
        logger.info("开始根据模板创建PPT文件");

        String templateFilePath = SystemConfig.getString(Dict.PPTProperty.MASTER_WORSHIP_FILE_PATH);
        File targetFile = new File("敬拜-" + coverEntity.getWorshipDate() + EXTENSION);

        try (InputStream inputStream = new FileInputStream(templateFilePath)) {
            logger.debug("开始复制...");
            Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("PPT文件创建完成，位于：" + targetFile.getAbsolutePath());
        } catch (IOException | NullPointerException e) {
            throw new FileServiceException("PPT文件创建失败！", e);
        }

        return targetFile;
    }

    public void copyTemplate(File targetFile) throws FileServiceException {
        logger.info("开始根据模板创建PPT文件");

        String templateFilePath = SystemConfig.getString(Dict.PPTProperty.MASTER_WORSHIP_FILE_PATH);
        try (InputStream inputStream = new FileInputStream(templateFilePath)) {
            logger.debug("开始复制...");
            Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("PPT文件创建完成，位于：" + targetFile.getAbsolutePath());
        } catch (IOException | NullPointerException e) {
            throw new FileServiceException("PPT文件创建失败！", e);
        }
    }

    @Override
    public String readWorshipProcedureXml() throws FileServiceException {
        // 获取xml配置的绝对路径
        String xmlPath = SystemConfig.getString(Dict.PPTProperty.GENERAL_PROCEDURE_XML_PATH);
        // xml配置路径检查
        if (xmlPath == null || "".equals(xmlPath)) {
            throw new SystemException(Dict.PPTProperty.GENERAL_PROCEDURE_XML_PATH + "不可为空！");
        }

        try (InputStream inputStream = new FileInputStream(xmlPath);
             Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8.name())) {
            return internalReadWorshipProcedureXml(scanner);
        } catch (IOException | NullPointerException e) {
            throw new FileServiceException("读取" + xmlPath + "失败！", e);
        }
    }

    /**
     * 内部读取XML配置
     * @param scanner
     * @return String格式的配置信息
     */
    private String internalReadWorshipProcedureXml(Scanner scanner) {
        logger.debug("开始逐行读取XML配置");
        StringBuilder stringBuilder = new StringBuilder();
        while (scanner.hasNextLine()) {
            String str = scanner.nextLine();
            // 测试打印读取的配置
            logger.debug("读取到XML配置: " + str);
            stringBuilder.append(str);
        }
        logger.info("XML配置读取完成");
        return stringBuilder.toString();
    }
}
