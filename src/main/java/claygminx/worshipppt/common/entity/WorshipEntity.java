package claygminx.worshipppt.common.entity;

import java.io.Serializable;
import java.util.List;

/**
 * 敬拜参数实体
 */
public class WorshipEntity implements Serializable {

    private static final long serialVersionUID = 8021242236962436651L;

    /**
     * 封面
     */
    private CoverEntity cover;

    /**
     * 诗歌清单
     */
    private PoetryContentEntity poetryContent;

    /**
     * 经文编号集
     */
    private ScriptureContentEntity scriptureContent;

    /**
     * 宣信
     */
    private DeclarationEntity declaration;

    /**
     * 证道
     */
    private PreachEntity preach;

    /**
     * 家事报告
     */
    /**
     * 家事报告。该字段仅用于当前生成任务，不参与敬拜配置缓存序列化。
     */
    private transient List<String> familyReports;

    /**
     * 公祷事项。该字段仅用于当前生成任务，不参与敬拜配置缓存序列化。
     */
    private transient List<String> publicPrayContents;

    /** 证道回应开关，仅用于当前生成任务，不写入敬拜配置缓存。 */
    private transient boolean preachResponseEnabled;

    /** 证道回应事项，仅用于当前生成任务，不写入敬拜配置缓存。 */
    private transient List<String> preachResponseContents;

    /** 证道内容页开关，仅用于当前生成任务，不写入敬拜配置缓存。 */
    private transient boolean preachContentsEnabled;

    /** 证道内容页正文，仅用于当前生成任务，不写入敬拜配置缓存。 */
    private transient List<String> preachContents;

    /**
     * 圣餐
     */
    private HolyCommunionEntity holyCommunion;

    /** 感恩敬拜经文选项，用于选择感恩奉献阶段的 PPT 母版。 */
    private String thanksgivingScripture;

    public CoverEntity getCover() {
        return cover;
    }

    public void setCover(CoverEntity cover) {
        this.cover = cover;
    }

    public PoetryContentEntity getPoetryContent() {
        return poetryContent;
    }

    public void setPoetryContent(PoetryContentEntity poetryContent) {
        this.poetryContent = poetryContent;
    }

    public ScriptureContentEntity getScriptureContent() {
        return scriptureContent;
    }

    public void setScriptureContent(ScriptureContentEntity scriptureContent) {
        this.scriptureContent = scriptureContent;
    }

    public DeclarationEntity getDeclaration() {
        return declaration;
    }

    public void setDeclaration(DeclarationEntity declaration) {
        this.declaration = declaration;
    }

    public PreachEntity getPreach() {
        return preach;
    }

    public void setPreach(PreachEntity preach) {
        this.preach = preach;
    }

    public List<String> getFamilyReports() {
        return familyReports;
    }

    public void setFamilyReports(List<String> familyReports) {
        this.familyReports = familyReports;
    }

    public List<String> getPublicPrayContents() {
        return publicPrayContents;
    }

    public void setPublicPrayContents(List<String> publicPrayContents) {
        this.publicPrayContents = publicPrayContents;
    }

    public boolean isPreachResponseEnabled() {
        return preachResponseEnabled;
    }

    public void setPreachResponseEnabled(boolean preachResponseEnabled) {
        this.preachResponseEnabled = preachResponseEnabled;
    }

    public List<String> getPreachResponseContents() {
        return preachResponseContents;
    }

    public void setPreachResponseContents(List<String> preachResponseContents) {
        this.preachResponseContents = preachResponseContents;
    }

    public boolean isPreachContentsEnabled() {
        return preachContentsEnabled;
    }

    public void setPreachContentsEnabled(boolean preachContentsEnabled) {
        this.preachContentsEnabled = preachContentsEnabled;
    }

    public List<String> getPreachContents() {
        return preachContents;
    }

    public void setPreachContents(List<String> preachContents) {
        this.preachContents = preachContents;
    }

    public HolyCommunionEntity getHolyCommunion() {
        return holyCommunion;
    }

    public void setHolyCommunion(HolyCommunionEntity holyCommunion) {
        this.holyCommunion = holyCommunion;
    }

    public String getThanksgivingScripture() {
        return thanksgivingScripture;
    }

    public void setThanksgivingScripture(String thanksgivingScripture) {
        this.thanksgivingScripture = thanksgivingScripture;
    }

    @Override
    public String toString() {
        return "WorshipEntity{" +
                "cover=" + cover +
                ", poetryContent=" + poetryContent +
                ", scriptureContent=" + scriptureContent +
                ", declaration=" + declaration +
                ", preach=" + preach +
                ", familyReports=" + familyReports +
                ", holyCommunion=" + holyCommunion +
                '}';
    }

}
