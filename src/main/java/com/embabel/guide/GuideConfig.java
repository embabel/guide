package com.embabel.guide;

import com.embabel.common.ai.model.LlmOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "guide")
public record GuideConfig(
        @DefaultValue("4") int topK,
        @DefaultValue("0.7") double similarityThreshold,
        LlmOptions llm
) {
}