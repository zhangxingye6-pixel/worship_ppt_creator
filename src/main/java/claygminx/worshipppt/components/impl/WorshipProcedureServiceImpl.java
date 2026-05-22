package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.entity.CoverEntity;
import claygminx.worshipppt.common.entity.WorshipEntity;
import claygminx.worshipppt.components.*;
import claygminx.worshipppt.exception.FileServiceException;
import claygminx.worshipppt.exception.PPTLayoutException;
import claygminx.worshipppt.exception.SystemException;
import ognl.Ognl;
import ognl.OgnlException;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.dom4j.*;

import java.util.*;

/**
 * PPT制作服务的实现，通过OGNL将XML中的流程实例化
 */
public class WorshipProcedureServiceImpl implements WorshipProcedureService {

    private final static Logger logger = LoggerFactory.getLogger(WorshipProcedureService.class);

    private FileService fileService;
    private ScriptureService scriptureService;
    private ConfessionService confessionService;

    /**
     * 处理XML配置文件
     *
     * @param ppt           PPT模板对象
     * @param worshipEntity 敬拜参数实体
     * @return
     * @throws FileServiceException
     */
    @Override
    public List<WorshipStep> generate(XMLSlideShow ppt, WorshipEntity worshipEntity) throws FileServiceException, PPTLayoutException {
        String xmlString = fileService.readWorshipProcedureXml();

        Document document;
        try {
            document = DocumentHelper.parseText(xmlString);
        } catch (DocumentException e) {
            throw new FileServiceException("XML读取失败！", e);
        }

        // 获取用户选择的敬拜模式（常规、圣餐、入会）
        CoverEntity cover = worshipEntity.getCover();
        String model = cover.getModel();
        // 获取XML根元素（<worship>标签）
        Element rootElement = document.getRootElement();
        // 获取敬拜类型子元素列表(<model>标签)
        List<?> elements = rootElement.elements();
        Map<String, Object> context = new HashMap<>();  // 创建OGNL上下文对象
        List<WorshipStep> result = new ArrayList<>();
        for (Object elementObj : elements) {
            // 测试打印XML子元素<model>
            logger.debug("读取XML子元素: " + elementObj);

            Element modelElement = (Element) elementObj;
            // 获取子元素model的name属性
            String modelName = modelElement.attributeValue("name");
            if (model.equals(modelName)) {                              // 匹配敬拜模式的model结构体
                context.put("ppt", ppt);                                // ppt母版
                context.put("worshipEntity", worshipEntity);            // 面板的敬拜实体参数
                context.put("scriptureService", scriptureService);      // 经文服务
                context.put("confessionService", confessionService);    // 信条服务
                // 设置OGNL表达式的根对象(当属性没有指定的时候，默认在根对象中查找，比如name会被解析成worshipEntity.name)
                Ognl.setRoot(context, worshipEntity);
                // 获取子元素model的所有属性<worship-step>
                List<?> stepElements = modelElement.elements();

                // 校验ppt模板的完整性
                try {
                    validateTemplateCompleteness(ppt, stepElements);
                } catch (PPTLayoutException e) {
                    throw new PPTLayoutException(e.getMessage(), e);
                }

                // 按照XML定义的顺序获取
                for (Object stepElementObj : stepElements) {
                    Element stepElement = (Element) stepElementObj;

                    WorshipStep worshipStep = getWorshipStep(stepElement, context, worshipEntity);
                    if (worshipStep != null) {
                        result.add(worshipStep);
                    }
                }
                break;
            }
        }
        return result;
    }

    private static void validateTemplateCompleteness(XMLSlideShow ppt, List<?> stepElements) throws PPTLayoutException {
        // 第一母版中存在的版式名称列表
        List<String> slideLayouts = new ArrayList<>();
        for (XSLFSlideLayout slideLayout : ppt.getSlideMasters().get(0).getSlideLayouts()) {
            slideLayouts.add(slideLayout.getName());
        }
        // XML中需要的ppt版式列表
        List<String> layoutsInXML = new ArrayList<>();
        for (Object stepElementObj : stepElements) {
            Element stepElement = (Element) stepElementObj;
            layoutsInXML.add(stepElement.attributeValue("layout"));
        }
        // 在当前敬拜模式下, ppt模板文件中缺失的页面列表
        List<String> missingLayout = new ArrayList<>(layoutsInXML);
        missingLayout.removeAll(slideLayouts);

        if (!missingLayout.isEmpty()) {
            // 拼接字符串
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("以下ppt模板缺失:\n");
            // 遍历缺失的列表
            for (String s : missingLayout) {
                stringBuilder.append(" - ").append(s).append("\n");
            }
            String exceptionMeaasge = stringBuilder.toString();

            throw new PPTLayoutException(exceptionMeaasge);

        }
    }

    private WorshipStep getWorshipStep(Element stepElement, Map<String, Object> context, WorshipEntity worshipEntity) {
        String ifExpression = stepElement.attributeValue("if");
        if (ifExpression != null) {
            try {
                boolean whether = (boolean) Ognl.getValue(ifExpression, worshipEntity);
                if (!whether) {
                    logger.debug("因[{}]跳过一个阶段", ifExpression);
                    return null;
                }
            } catch (OgnlException e) {
                throw new SystemException("OGNL表达式错误！", e);
            }
        }

        String clazz = stepElement.attributeValue("class");
        String layout = stepElement.attributeValue("layout");
        String data = stepElement.attributeValue("data");
        context.put("layout", layout);
        String expression;
        if (data != null) {
            expression = String.format("new %s(#ppt, #layout, %s)", clazz, data);
        } else {
            expression = String.format("new %s(#ppt, #layout)", clazz);
        }

        // 打印完整的 OGNL 表达式
        logger.debug("生成的 OGNL 表达式: {}", expression);

        // 执行 OGNL
        try {
            return (WorshipStep) Ognl.getValue(expression, context, worshipEntity);
        } catch (OgnlException e) {
            throw new SystemException("OGNL表达式错误！", e);
        }
    }

    public void setFileService(FileService fileService) {
        this.fileService = fileService;
    }

    public void setScriptureService(ScriptureService scriptureService) {
        this.scriptureService = scriptureService;
    }

    public void setConfessionService(ConfessionService confessionService) {
        this.confessionService = confessionService;
    }
}
