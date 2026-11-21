package claygminx.worshipppt.common.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@ToString
public class GithubReleaseEntity {

    private Long id;
    // 版本号
    private String tag_name;
    // 有信息的版本号
    private String name;
    // 版本描述
    private String body;
    // 发行时间
    private Date created_at;
    // 下载地址
    private String html_url;
    /** 仅正式发行版参与升级，附件由当前操作系统和运行架构精确匹配。 */
    private boolean draft;
    private boolean prerelease;
    private List<GithubReleaseAssetEntity> assets;

}
