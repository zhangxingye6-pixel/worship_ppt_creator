package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.entity.PreachContentPageEntity;
import claygminx.worshipppt.common.entity.PreachEntity;
import org.apache.poi.xslf.usermodel.SlideLayout;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.junit.Test;
import org.openxmlformats.schemas.drawingml.x2006.main.CTRegularTextRun;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.Assert.*;

/** 验证证道小标题、正文样式及多页生成，避免新增输入项后重现首张空白页问题。 */
public class PreachContentStepTest {
    @Test
    public void rendersBoldRedSubtitleWithSeparateLineSpacingBeforeUnboldBody() throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            XSLFTextBox box = ppt.createSlide().createTextBox();
            PreachContentStep step = step(ppt);
            step.renderPageContent(box, new PreachContentPageEntity("信心的根基", "第一段正文\n第二段正文"));
            List<XSLFTextParagraph> paragraphs = box.getTextParagraphs();
            assertEquals(3, paragraphs.size());
            assertEquals("信心的根基", paragraphs.get(0).getText());
            XSLFTextRun subtitle = paragraphs.get(0).getTextRuns().get(0);
            assertEquals(30.0, subtitle.getFontSize(), 0.01);
            assertTrue(subtitle.isBold());
            assertArrayEquals(new byte[]{(byte) 245, 101, 81},
                    ((CTRegularTextRun) subtitle.getXmlObject()).getRPr().getSolidFill().getSrgbClr().getVal());
            assertEquals(85.0, paragraphs.get(0).getLineSpacing(), 0.01);
            assertEquals(110.0, paragraphs.get(1).getLineSpacing(), 0.01);
            assertEquals(110.0, paragraphs.get(2).getLineSpacing(), 0.01);
            assertEquals("第一段正文", paragraphs.get(1).getText());
            assertEquals("第二段正文", paragraphs.get(2).getText());
            assertEquals(step.getTitleFontSize(), paragraphs.get(1).getTextRuns().get(0).getFontSize(), 0.01);
            assertFalse(paragraphs.get(1).getTextRuns().get(0).isBold());
        }
    }

    @Test
    public void skipsEmptySubtitleWithoutInsertingBlankParagraph() throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            XSLFTextBox box = ppt.createSlide().createTextBox();
            step(ppt).renderPageContent(box, new PreachContentPageEntity("", "正文"));
            assertEquals(1, box.getTextParagraphs().size());
            assertEquals("正文", box.getTextParagraphs().get(0).getText());
        }
    }

    @Test
    public void reusesDefaultPageAndKeepsSubtitleBodyPairsInOrder() throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            XSLFSlideLayout layout = ppt.getSlideMasters().get(0).getLayout(SlideLayout.TITLE_AND_CONTENT);
            PreachEntity preach = new PreachEntity();
            preach.setTitle("证道主题");
            new PreachStep(ppt, layout.getName(), preach).execute();
            PreachContentStep step = new PreachContentStep(ppt, layout.getName(), preach, List.of(
                    new PreachContentPageEntity("第一页小标题", "第一页正文"),
                    new PreachContentPageEntity("第二页小标题", "第二页正文")));
            step.execute();
            assertEquals(2, ppt.getSlides().size());
            for (int index = 0; index < 2; index++) {
                assertEquals("《证道主题》", ppt.getSlides().get(index).getPlaceholder(0).getText());
                XSLFTextShape body = ppt.getSlides().get(index).getPlaceholder(1);
                assertEquals(index == 0 ? "第一页小标题" : "第二页小标题", body.getTextParagraphs().get(0).getText());
                assertEquals(index == 0 ? "第一页正文" : "第二页正文", body.getTextParagraphs().get(1).getText());
            }
        }
    }

    @Test
    public void retainsSubtitleStyleAfterPowerPointSerialization() throws Exception {
        byte[] bytes;
        try (XMLSlideShow ppt = new XMLSlideShow(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XSLFTextBox box = ppt.createSlide().createTextBox();
            step(ppt).renderPageContent(box, new PreachContentPageEntity("小标题", "正文"));
            ppt.write(output);
            bytes = output.toByteArray();
        }
        try (XMLSlideShow restored = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            XSLFTextShape box = (XSLFTextShape) restored.getSlides().get(0).getShapes().get(0);
            XSLFTextRun subtitle = box.getTextParagraphs().get(0).getTextRuns().get(0);
            assertTrue(subtitle.isBold());
            assertEquals(30.0, subtitle.getFontSize(), 0.01);
            assertEquals(85.0, box.getTextParagraphs().get(0).getLineSpacing(), 0.01);
            assertEquals(110.0, box.getTextParagraphs().get(1).getLineSpacing(), 0.01);
        }
    }

    private PreachContentStep step(XMLSlideShow ppt) {
        return new PreachContentStep(ppt, "证道", new PreachEntity(), List.of());
    }
}
