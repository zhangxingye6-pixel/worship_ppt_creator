package claygminx.worshipppt.common.entity;

/**
 * 单页证道内容，仅用于当前制作任务，不写入敬拜配置缓存。
 *
 * <p>小标题与正文作为同一页传递，避免增删页面后出现标题、正文错位。</p>
 */
public final class PreachContentPageEntity {
    private final String subtitle;
    private final String content;

    /**
     * 创建一页证道内容。
     *
     * @param subtitle 单行小标题，可留空
     * @param content 正文，保留输入中的段落换行
     */
    public PreachContentPageEntity(String subtitle, String content) {
        this.subtitle = subtitle == null ? "" : subtitle.trim();
        this.content = content == null ? "" : content.trim();
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getContent() {
        return content;
    }

    /** 小标题与正文均为空时，保留原流程中的默认证道空白页。 */
    public boolean isEmpty() {
        return subtitle.isEmpty() && content.isEmpty();
    }
}
