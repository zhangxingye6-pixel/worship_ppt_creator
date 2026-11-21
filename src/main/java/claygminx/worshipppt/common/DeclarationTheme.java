package claygminx.worshipppt.common;

import java.util.HashMap;

/**
 * 宣信主题，用与切换在DeclarationContentStep中excute方法的模式，预留了扩展
 */
public class DeclarationTheme {

    public static final HashMap<String, String> DECLARATION_METHOD_MAP;

    static {
        DECLARATION_METHOD_MAP = new HashMap<String, String>();
        // 全称的映射，更优雅一点
        // GUI 的“信条”选项对应西敏信条，标题沿用原有全称。
        DECLARATION_METHOD_MAP.put("信条", "威斯敏斯特公认信条");
        DECLARATION_METHOD_MAP.put("西敏信条", "威斯敏斯特公认信条");
    }


}
