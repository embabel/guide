package com.embabel.guide;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Export;
import com.embabel.agent.api.common.OperationContext;

import java.util.Collections;

record GuideRequest(
        String question
) {
}


record GuideResponse(
        String answer,
        String[] references
) {
}

@Agent(description = "Embabel developer guide")
public record GuideAgent(
        GuideData guideData
) {

    @AchievesGoal(description = "Answer a question about Embabel",
            export = @Export(remote = true, startingInputTypes = {GuideRequest.class}))
    @Action
    GuideResponse answerQuestion(GuideRequest guideRequest, OperationContext operationContext) {
        return operationContext.ai()
                .withLlm(guideData.guideConfig().llm())
                .withReferences(guideData.references())
                .withRag(guideData.ragOptions())
                .withTemplate("guide_system")
                .createObject(GuideResponse.class, guideData.templateModel(Collections.singletonMap(
                        "user", operationContext.getProcessContext().getProcessOptions().getIdentities().getForUser()
                )));
    }

}
