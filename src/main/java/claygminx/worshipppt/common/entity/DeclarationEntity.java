package claygminx.worshipppt.common.entity;

import java.io.Serializable;

/**
 * 宣信实体
 */
public class DeclarationEntity implements Serializable {

    private static final long serialVersionUID = 5066922226672640394L;

    /**
     * 宣信主题
     */
    private String title = "";

    /** 宣信方式：信条、使徒信经、迦克墩信经或尼西亚信经。 */
    private String theme = "信条";

    /**
     * 讲员
     */
    private String speaker = "";

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTheme() {
        return theme == null || theme.isBlank() ? "信条" : theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public String getSpeaker() {
        return speaker;
    }

    public void setSpeaker(String speaker) {
        this.speaker = speaker;
    }

    @Override
    public String toString() {
        return "DeclarationEntity{" +
                "theme='" + theme + '\'' +
                "title='" + title + '\'' +
                ", speaker='" + speaker + '\'' +
                '}';
    }
}
