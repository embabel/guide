package com.embabel.guide.domain

import org.drivine.manager.StatelessGraphObjectManager
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class FeedbackRepository(
    @param:Qualifier("neoGraphObjectManager") private val graphObjectManager: StatelessGraphObjectManager
) {

    @Transactional
    fun save(feedbackView: FeedbackView): FeedbackView =
        graphObjectManager.save(feedbackView)
}
