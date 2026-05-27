package com.linrun.trigger.support.tool;

public record GuideToolEvidenceCheck(boolean passed, String message) {

    public static GuideToolEvidenceCheck ok() {
        return new GuideToolEvidenceCheck(true, "tool evidence passed");
    }

    public static GuideToolEvidenceCheck failed(String message) {
        return new GuideToolEvidenceCheck(false, message);
    }
}
