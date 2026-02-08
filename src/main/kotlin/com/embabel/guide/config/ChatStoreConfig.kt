package com.embabel.guide.config

import com.embabel.chat.store.model.StoredUser
import com.embabel.chat.store.repository.ChatSessionRepository
import com.embabel.chat.store.repository.ChatSessionRepositoryImpl
import com.embabel.guide.domain.GuideUserData
import org.drivine.manager.GraphObjectManager
import org.drivine.manager.PersistenceManager
import org.drivine.manager.PersistenceManagerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

/**
 * Configuration for embabel-chat-store integration.
 * Registers GuideUserData as the StoredUser implementation for polymorphic deserialization,
 * and wires up the ChatSessionRepository bean.
 */
@Configuration
class ChatStoreConfig {

    @Bean
    @Primary
    fun persistenceManager(factory: PersistenceManagerFactory): PersistenceManager {
        val pm = factory.get("neo")
        // Register GuideUserData as implementation of StoredUser interface.
        // Composite label key is sorted alphabetically with pipe separator.
        pm.registerSubtype(
            StoredUser::class.java,
            "GuideUser|User",
            GuideUserData::class.java
        )
        return pm
    }

    @Bean
    fun chatSessionRepository(
        @Qualifier("neoGraphObjectManager") graphObjectManager: GraphObjectManager
    ): ChatSessionRepository {
        return ChatSessionRepositoryImpl(graphObjectManager)
    }
}
