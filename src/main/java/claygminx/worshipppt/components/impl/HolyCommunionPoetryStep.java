package claygminx.worshipppt.components.impl;

import claygminx.worshipppt.common.config.SystemConfig;
import claygminx.worshipppt.common.entity.PoetryEntity;
import claygminx.worshipppt.exception.PPTLayoutException;
import claygminx.worshipppt.exception.PoetrySourcesNotExistException;
import claygminx.worshipppt.exception.WorshipStepException;
import claygminx.worshipppt.common.Dict;
import org.apache.poi.xslf.usermodel.XMLSlideShow;

import java.util.Collections;
import java.util.List;

/**
 * 圣餐诗歌阶段
 */
public class HolyCommunionPoetryStep extends RegularPoetryStep {

//    private final static Logger logger = LoggerFactory.getLogger(HolyCommunionPoetryStep.class);

    private final List<PoetryEntity> poetryList;

    public HolyCommunionPoetryStep(XMLSlideShow ppt, String layout, List<PoetryEntity> poetryList) {
        super(ppt, layout, poetryList);
        this.poetryList = poetryList;
    }

    @Override
    public void execute() throws WorshipStepException, PPTLayoutException, PoetrySourcesNotExistException {
        try {
            for (PoetryEntity poetry : poetryList) {
                new HolyCommunionPoetryTitleStep(getPpt(), "圣餐-诗歌标题", Collections.singletonList(poetry)).execute();
                renderPoetry(poetry);
            }
        } catch (PoetrySourcesNotExistException e) {
            throw new PoetrySourcesNotExistException("圣餐诗歌：" + e.getMessage());
        }
    }

    @Override
    public double getTop() {
        return SystemConfig.getDouble(Dict.PPTProperty.HOLY_COMMUNION_POETRY_TOP);
    }

    @Override
    public List<PoetryEntity> getPoetryList() {
        return poetryList;
    }
}
