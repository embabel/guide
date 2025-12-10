package com.embabel.guide.domain.drivine;

import org.drivine.annotation.NodeFragment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Simple data representation of GuideUser properties for Drivine composition.
 * Maps directly to node properties without OGM relationships.
 */
@NodeFragment(labels = {"GuideUser"})
public class GuideUserData implements HasGuideUserData {

    private String id;

    @Nullable
    private String persona;

    @Nullable
    private String customPrompt;

    // No-arg constructor for Jackson
    public GuideUserData() {
    }

    public GuideUserData(String id, @Nullable String persona, @Nullable String customPrompt) {
        this.id = id;
        this.persona = persona;
        this.customPrompt = customPrompt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Nullable
    public String getPersona() {
        return persona;
    }

    public void setPersona(@Nullable String persona) {
        this.persona = persona;
    }

    @Nullable
    public String getCustomPrompt() {
        return customPrompt;
    }

    public void setCustomPrompt(@Nullable String customPrompt) {
        this.customPrompt = customPrompt;
    }

    @Override
    @NotNull
    public GuideUserData guideUserData() {
        return this;
    }

    @Override
    public String toString() {
        return "GuideUserData{" +
                "id='" + id + '\'' +
                ", persona='" + persona + '\'' +
                ", customPrompt='" + customPrompt + '\'' +
                '}';
    }
}
