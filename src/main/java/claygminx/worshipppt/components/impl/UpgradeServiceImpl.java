package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.config.SystemConfig;
import claygminx.worshipppt.common.entity.GithubReleaseEntity;
import claygminx.worshipppt.components.UpgradeService;
import claygminx.worshipppt.exception.SystemException;
import claygminx.worshipppt.common.Dict;
import com.google.gson.Gson;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class UpgradeServiceImpl implements UpgradeService {

    private final static Logger logger = LoggerFactory.getLogger(UpgradeService.class);

    private static UpgradeService upgradeService;

    /**
     * 获取实例对象
     * @return 升级服务实例对象
     */
    public static UpgradeService getInstance() {
        if (upgradeService == null) {
            upgradeService = new UpgradeServiceImpl();
        }
        return upgradeService;
    }

    @Override
    public GithubReleaseEntity checkNewRelease() {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            logger.info("检查升级服务");
            // 原gitee配置
//             int connectTimeout = SystemConfig.getInt(Dict.GiteeProperty.CONNECT_TIMEOUT);
//             int connectRequestTimeout = SystemConfig.getInt(Dict.GiteeProperty.CONNECT_REQUEST_TIMEOUT);
//             int responseTimeout = SystemConfig.getInt(Dict.GiteeProperty.RESPONSE_TIMEOUT);

            int connectTimeout = SystemConfig.getInt(Dict.GithubProperty.CONNECT_TIMEOUT);
            int connectRequestTimeout = SystemConfig.getInt(Dict.GithubProperty.CONNECT_REQUEST_TIMEOUT);
            int responseTimeout = SystemConfig.getInt(Dict.GithubProperty.RESPONSE_TIMEOUT);

//             String url = SystemConfig.getString(Dict.GiteeProperty.URL);
            String owner = SystemConfig.getString(Dict.GithubProperty.OWNER);
            String repo = SystemConfig.getString(Dict.GithubProperty.REPO);
            String url = String.format("https://api.github.com/repos/%s/%s/releases/latest", owner, repo);
            logger.debug("github request build [" + "GET " + url + "]");
            HttpGet httpGet = new HttpGet(url);
//            httpGet.addHeader("Content-Type", "application/json;charset=UTF-8");
            // Github Rest Api "Get the latest release"请求
            httpGet.addHeader("Accept", "application/vnd.github+json");
            httpGet.addHeader("X-Github-Api-Version", "2022-11-28");
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(connectTimeout, TimeUnit.SECONDS)
                    .setConnectionRequestTimeout(connectRequestTimeout, TimeUnit.SECONDS)
                    .setResponseTimeout(responseTimeout, TimeUnit.SECONDS)
                    .build();
            httpGet.setConfig(requestConfig);
            CloseableHttpResponse response = client.execute(httpGet);
            logger.info("版本检查请求成功");

            if (HttpStatus.SC_NOT_FOUND == response.getCode()) {
                logger.warn("{} 返回404！", url);
            } else if (HttpStatus.SC_OK == response.getCode()) {
                logger.info("{} 返回200！", url);
//                StringBuilder responseBuilder = new StringBuilder();

//                try (Scanner scanner = new Scanner(response.getEntity().getContent(), StandardCharsets.UTF_8.name())) {
//                    while (scanner.hasNextLine()) {
//                        responseBuilder.append(scanner.nextLine());
//                    }
//                }
//                String responseString = responseBuilder.toString();
                String responseString = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                // 转码
                responseString = new String(responseString.getBytes(), System.getProperty("file.encoding"));
                logger.info(responseString);

                GithubReleaseEntity remoteReleaseEntity = new Gson().fromJson(responseString, GithubReleaseEntity.class);
                logger.info("response [" + remoteReleaseEntity + "]");
                GithubReleaseEntity thisReleaseEntity = getThisProjectReleaseEntity();
                if (compareVersion(thisReleaseEntity, remoteReleaseEntity) < 0) {
                    // 小于远程发行包版本，所以应提示要升级
//                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    String downloadUrl = getDownloadUrlFromGitHubBody(remoteReleaseEntity);
                    if (downloadUrl != null) {
                        return remoteReleaseEntity;
                    }
                    logger.warn("远程发行包缺失下载地址！");
                } else {
                    logger.info("该发行包应该是最新的，不用升级");
                }
            } else {
                logger.warn("{} 返回{}", url, response.getCode());
            }
        } catch (Exception e) {
            logger.error("升级服务出现异常！", e);
        }
        return null;
    }

    protected GithubReleaseEntity getThisProjectReleaseEntity() {
        String projectVersion = SystemConfig.getString(Dict.ProjectProperty.VERSION);
        String sProjectTime = SystemConfig.getString(Dict.ProjectProperty.TIME);
        String projectTimeFormat = SystemConfig.getString(Dict.ProjectProperty.TIME_FORMAT);
        try {
            Date oProjectTime = new SimpleDateFormat(projectTimeFormat).parse(sProjectTime);
            return new GithubReleaseEntity()
                    .created_at(oProjectTime)
                    .name(projectVersion)
                    .tag_name(projectVersion);
        } catch (ParseException e) {
            String message = String.format("格式化发行包的构建时间时出错，构建时间是%s，时间格式是%s", sProjectTime, projectTimeFormat);
            throw new SystemException(message, e);
        }
    }

    protected int compareVersion(GithubReleaseEntity thisEntity, GithubReleaseEntity otherEntity) {
        String thisTagName = thisEntity.tag_name();
        String otherTagName = otherEntity.tag_name();
        int[] thisVersion = parseVersion(thisTagName);
        int[] otherVersion = parseVersion(otherTagName);
        logger.info("当前版本号：{}，远程版本号：{}", thisTagName, otherTagName);

        for (int i = 0; i < thisVersion.length; i++) {
            int r = thisVersion[i] - otherVersion[i];
            if (r != 0) {
                return r;
            }
        }

        return 0;
    }

    private int[] parseVersion(String version) {
        int[] result = new int[3];
        if ("v".equalsIgnoreCase(version.substring(0, 1))) {
            version = version.substring(1);
        }
        int suffixIndex = version.indexOf("-");
        if (suffixIndex >= 0) {
            version = version.substring(0, suffixIndex);
        }
        String[] versionPartArray = version.split("[.]");
        for (int i = 0; i < versionPartArray.length; i++) {
            int n = Integer.parseInt(versionPartArray[i]);
            result[i] = n;
        }
        return result;
    }

    /**
     * 从GitHub消息返回体中获取下载地址
     * @param body 消息体
     * @return 下载地址
     */
    protected String getDownloadUrlFromGitHubBody(GithubReleaseEntity body) {
        if (body == null) {
            return "";
        }
        return body.html_url();
    }
}
