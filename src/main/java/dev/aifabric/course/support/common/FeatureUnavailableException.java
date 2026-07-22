package dev.aifabric.course.support.common;

public class FeatureUnavailableException extends RuntimeException {

    private final String capability;

    public FeatureUnavailableException(String capability) {
        super("The " + capability + " capability is not implemented at this course checkpoint.");
        this.capability = capability;
    }

    public String getCapability() {
        return capability;
    }
}
