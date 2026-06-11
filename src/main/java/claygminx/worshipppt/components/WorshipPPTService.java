package claygminx.worshipppt.components;

import claygminx.worshipppt.exception.*;

/**
 * 敬拜PPT服务
 */
public interface WorshipPPTService {

    /**
     * 制作敬拜PPT
     */
    void make() throws FileServiceException, WorshipStepException, PPTLayoutException, PoetrySourcesNotExistException, ScriptureNumberException;

}
