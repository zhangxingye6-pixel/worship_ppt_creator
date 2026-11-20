package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.config.SystemConfig;
import claygminx.worshipppt.common.entity.GithubReleaseEntity;
import claygminx.worshipppt.components.UpgradeService;
import claygminx.worshipppt.exception.SystemException;
import claygminx.worshipppt.common.Dict;
import com.google.gson.Gson;
import claygminx.worshipppt.util.UpdateNetworkSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

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
        try {
            logger.info("检查升级服务");
            int connectTimeout = SystemConfig.getInt(Dict.GithubProperty.CONNECT_TIMEOUT);
            int responseTimeout = SystemConfig.getInt(Dict.GithubProperty.RESPONSE_TIMEOUT);

            String owner = SystemConfig.getString(Dict.GithubProperty.OWNER);
            String repo = SystemConfig.getString(Dict.GithubProperty.REPO);
            String url = String.format("https://api.github.com/repos/%s/%s/releases/latest", owner, repo);
            logger.debug("github request build [" + "GET " + url + "]");
            // 与安装包下载使用同一代理设置，避免版本检查与实际下载走不同网络路径。
            HttpURLConnection connection = UpdateNetworkSupport.openConnection(URI.create(url),
                    UpdateNetworkSupport.localProxySetting(
                            SystemConfig.getString(Dict.UpdateProperty.PROXY_TYPE),
                            SystemConfig.getString(Dict.UpdateProperty.PROXY_PORT)),
                    route -> logger.info("更新检查连接：{}", route));
            try {
                connection.setConnectTimeout(Math.max(1, connectTimeout) * 1000);
                connection.setReadTimeout(Math.max(1, responseTimeout) * 1000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("X-Github-Api-Version", "2022-11-28");
                connection.setRequestProperty("User-Agent", "Worship-PPT-Updater");
                int status = connection.getResponseCode();
                logger.info("版本检查请求成功");
                if (HttpURLConnection.HTTP_NOT_FOUND == status) {
                    logger.warn("{} 返回404！", url);
                } else if (HttpURLConnection.HTTP_OK == status) {
                    String responseString;
                    try (InputStream input = connection.getInputStream()) {
                        responseString = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    }
                    GithubReleaseEntity remoteReleaseEntity = new Gson().fromJson(responseString, GithubReleaseEntity.class);
                    // 与正式发布的 tag 规则一致，避免草稿和测试版本进入用户下载流程。
                    if (remoteReleaseEntity == null || remoteReleaseEntity.draft() || remoteReleaseEntity.prerelease()
                            || remoteReleaseEntity.tag_name() == null
                            || !remoteReleaseEntity.tag_name().matches("v[0-9]+\\.[0-9]+\\.[0-9]+")) {
                        return null;
                    }
                    GithubReleaseEntity thisReleaseEntity = getThisProjectReleaseEntity();
                    if (compareVersion(thisReleaseEntity, remoteReleaseEntity) < 0) {
                        return remoteReleaseEntity;
                    } else {
                        logger.info("该发行包应该是最新的，不用升级");
                    }
                } else {
                    logger.warn("{} 返回{}", url, status);
                }
            } finally {
                connection.disconnect();
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

}
