package claygminx.worshipppt.components.impl;

import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.Assert.*;

/** 验证公祷编号按实际字号测量，且写入 PPT 后仍保留悬挂缩进与原有文字样式。 */
public class PublicPrayContentStepTest {
    @Test
    public void alignsContinuationToMeasuredNumberPrefixAtThirtyPoints() throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            PublicPrayContentStep step = new PublicPrayContentStep(ppt, "公祷事项", null);
            XSLFTextBox box = ppt.createSlide().createTextBox();
            box.clearText();
            XSLFTextParagraph paragraph = step.addContentParagraph(box, "为教会祷告", 1, 30.0);
            double expected = step.measureTextWidth("1.", 30.0, AbstractWorshipStep.DEFAULT_FONT_FAMILY);
            assertEquals(expected, paragraph.getLeftMargin(), 0.01);
            assertEquals(-expected, paragraph.getIndent(), 0.01);
            assertEquals(TextParagraph.TextAlign.LEFT, paragraph.getTextAlign());
            assertEquals("1.为教会祷告", paragraph.getText());
            assertEquals(30.0, paragraph.getTextRuns().get(0).getFontSize(), 0.01);
            assertFalse(paragraph.getTextRuns().get(0).isBold());
            assertEquals(120.0, paragraph.getLineSpacing(), 0.01);
        }
    }

    @Test
    public void adaptsToFontSizeAndNumberWidth() throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            PublicPrayContentStep step = new PublicPrayContentStep(ppt, "公祷事项", null);
            XSLFTextBox box = ppt.createSlide().createTextBox();
            double thirty = step.addContentParagraph(box, "事项", 1, 30.0).getLeftMargin();
            double thirtySix = step.addContentParagraph(box, "事项", 1, 36.0).getLeftMargin();
            double doubleDigit = step.addContentParagraph(box, "事项", 10, 30.0).getLeftMargin();
            assertTrue(thirtySix > thirty);
            assertTrue(doubleDigit > thirty);
            assertEquals(thirty * 1.2, thirtySix, 0.02);
        }
    }

    @Test
    public void retainsIndentAndStylesAfterPowerPointSerialization() throws Exception {
        byte[] bytes;
        double expected;
        try (XMLSlideShow ppt = new XMLSlideShow(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PublicPrayContentStep step = new PublicPrayContentStep(ppt, "公祷事项", null);
            XSLFTextBox box = ppt.createSlide().createTextBox();
            box.clearText();
            expected = step.addContentParagraph(box, "这是一条需要换行的公祷事项", 3, 30.0).getLeftMargin();
            ppt.write(output);
            bytes = output.toByteArray();
        }
        try (XMLSlideShow restored = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            XSLFTextBox box = (XSLFTextBox) restored.getSlides().get(0).getShapes().get(0);
            XSLFTextParagraph paragraph = box.getTextParagraphs().stream()
                    .filter(item -> item.getText().startsWith("3.")).findFirst().orElseThrow();
            assertEquals(expected, paragraph.getLeftMargin(), 0.01);
            assertEquals(-expected, paragraph.getIndent(), 0.01);
            assertEquals(30.0, paragraph.getTextRuns().get(0).getFontSize(), 0.01);
            assertEquals(120.0, paragraph.getLineSpacing(), 0.01);
            assertFalse(paragraph.getTextRuns().get(0).isBold());
        }
    }
}
