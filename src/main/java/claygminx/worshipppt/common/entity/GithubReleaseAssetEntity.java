package claygminx.worshipppt.common.entity;

import lombok.Getter;
import lombok.Setter;

/** GitHub 发行附件信息，用于平台匹配和下载完整性校验。 */
@Getter
@Setter
public class GithubReleaseAssetEntity {
    private String name;
    private String state;
    private String browser_download_url;
    private String digest;
    private long size;
}
