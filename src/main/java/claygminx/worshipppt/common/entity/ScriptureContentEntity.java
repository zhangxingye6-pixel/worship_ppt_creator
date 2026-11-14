package claygminx.worshipppt.common.entity;

import claygminx.worshipppt.common.Dict;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 经文清单实体
 */
public class ScriptureContentEntity implements Serializable {

    private static final long serialVersionUID = 6658036227033027631L;

    /**
     * 宣召
     */
    private String summon;

    /**
     * 宣召主领内容。旧版本的 summon 字段作为兼容字段保留。
     */
    private String summonLeader;

    /**
     * 宣召回应内容。
     */
    private String summonResponse;

    /**
     * 宣召回应方式：经文或自定义。
     */
    private String summonMode;

    /**
     * 公祷
     */
    private String publicPray;

    /**
     * 认罪
     */
    private String confess;

    /**
     * 赦罪
     */
    private String forgiveSins;

    /**
     * 读经
     */
    private String readingScripture;

    /**
     * 读经章节列表。旧版本的 readingScripture 字段作为兼容字段保留。
     */
    private List<String> readingScriptureList = new ArrayList<>();

    /**
     * 证道经文
     */
    private String preachScripture;


    public String getSummon() {
        return summon;
    }

    public void setSummon(String summon) {
        this.summon = summon;
    }

    public String getSummonLeader() {
        return summonLeader == null || summonLeader.isEmpty() ? summon : summonLeader;
    }

    /**
     * 设置宣召主领内容，同时写入旧版字段，保证旧缓存仍可被读取。
     *
     * @param summonLeader 主领内容
     */
    public void setSummonLeader(String summonLeader) {
        this.summonLeader = summonLeader;
        this.summon = summonLeader;
    }

    /**
     * 获取宣召回应内容。
     *
     * @return 回应内容
     */
    public String getSummonResponse() {
        return summonResponse;
    }

    /**
     * 设置宣召回应内容。
     *
     * @param summonResponse 回应内容
     */
    public void setSummonResponse(String summonResponse) {
        this.summonResponse = summonResponse;
    }

    /**
     * 获取宣召回应方式。旧版本没有该字段，默认按经文模式处理。
     *
     * @return 经文或自定义
     */
    public String getSummonMode() {
        return summonMode == null || summonMode.isEmpty() ? Dict.SummonMode.SCRIPTURE : summonMode;
    }

    /**
     * 设置宣召回应方式。
     *
     * @param summonMode 经文或自定义
     */
    public void setSummonMode(String summonMode) {
        this.summonMode = summonMode;
    }

    public String getPublicPray() {
        return publicPray;
    }

    public void setPublicPray(String publicPray) {
        this.publicPray = publicPray;
    }

    public String getConfess() {
        return confess;
    }

    public void setConfess(String confess) {
        this.confess = confess;
    }

    public String getForgiveSins() {
        return forgiveSins;
    }

    public void setForgiveSins(String forgiveSins) {
        this.forgiveSins = forgiveSins;
    }

    public String getReadingScripture() {
        return readingScripture;
    }

    public void setReadingScripture(String readingScripture) {
        this.readingScripture = readingScripture;
    }

    /**
     * 获取读经章节列表。
     *
     * @return 按界面顺序排列的读经章节
     */
    public List<String> getReadingScriptureList() {
        return readingScriptureList;
    }

    /**
     * 设置读经章节列表，并同步保留第一行到旧版字段。
     *
     * @param readingScriptureList 读经章节列表
     */
    public void setReadingScriptureList(List<String> readingScriptureList) {
        this.readingScriptureList = readingScriptureList == null
                ? new ArrayList<>()
                : new ArrayList<>(readingScriptureList);
        this.readingScripture = this.readingScriptureList.isEmpty()
                ? null
                : this.readingScriptureList.get(0);
    }

    @Override
    public String toString() {
        return "ScriptureContentEntity{" +
                "summon='" + summon + '\'' +
                ", summonLeader='" + summonLeader + '\'' +
                ", summonResponse='" + summonResponse + '\'' +
                ", summonMode='" + summonMode + '\'' +
                ", publicPray='" + publicPray + '\'' +
                ", confess='" + confess + '\'' +
                ", forgiveSins='" + forgiveSins + '\'' +
                ", readingScripture='" + readingScripture + '\'' +
                ", readingScriptureList=" + readingScriptureList +
                '}';
    }

}
