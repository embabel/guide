package com.embabel.guide.simple;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Export;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.guide.GuideData;

import java.util.Map;

record GuideRequest(
        String question
) {
}


record GuideResponse(
        String answer,
        String[] references
) {
}

/**
 * Single shot Q&A
 *
 * @param guideData
 */
//@Agent(description = "Embabel developer guide")
public record GuideAgent(
        GuideData guideData
) {

    @AchievesGoal(description = "Answer a question about Embabel",
            export = @Export(remote = true, startingInputTypes = {GuideRequest.class}))
    @Action
    GuideResponse answerQuestion(GuideRequest guideRequest, OperationContext context) {
        var templateModel = Map.of(
                "user", context.user(),
                "defaultPersona", guideData.config().defaultPersona()
        );
        return context.ai()
                .withLlm(guideData.config().llm())
                .withReferences(guideData.referencesForUser(null))
                .withRag(guideData.ragOptions())
                .withTemplate("guide_system")
                .createObject(GuideResponse.class, templateModel);
    }

}
