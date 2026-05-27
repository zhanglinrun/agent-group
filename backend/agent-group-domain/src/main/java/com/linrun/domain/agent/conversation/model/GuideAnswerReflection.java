package com.linrun.domain.agent.conversation.model;

public class GuideAnswerReflection {

    private boolean passed = true;
    private String message = "answer reflection passed";

    public static GuideAnswerReflection passed() {
        return new GuideAnswerReflection();
    }

    public static GuideAnswerReflection failed(String message) {
        GuideAnswerReflection reflection = new GuideAnswerReflection();
        reflection.setPassed(false);
        reflection.setMessage(message);
        return reflection;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message == null || message.isBlank() ? "answer reflection passed" : message;
    }
}
