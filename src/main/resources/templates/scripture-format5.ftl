<#-- 奇数行以“主领：”开头，偶数行以“会众：”用于启应 -->
<#list scriptureVerseList as item>
<#if item?item_parity == "odd">主领：<#else>会众：</#if>${item.scripture}<#sep>${"\n"}
</#list>