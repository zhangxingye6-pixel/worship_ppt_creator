<#-- scripture-format3 与 scripture-format4 的综合模板：章节标识 + 启应角色。 -->
<#list scriptureVerseList as item><#if item?has_next == false>合　：<#elseif item?item_parity == "odd">主领：<#else>会众：</#if>【${item.bookShortName}${item.chapter}:${item.verse}】${item.scripture}<#sep>${"\n"}</#list>
