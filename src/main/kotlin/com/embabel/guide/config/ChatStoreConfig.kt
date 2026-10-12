package com.embabel.guide.config

import com.embabel.agent.spi.support.springai.SpringAiLlmService
import com.embabel.chat.store.adapter.LlmTitleGenerator
import com.embabel.chat.store.adapter.TitleGenerator
import com.embabel.chat.store.model.StoredUser
import com.embabel.chat.store.repository.ChatSessionRepository
import com.embabel.chat.store.repository.ChatSessionRepositoryImpl
import com.embabel.guide.domain.GuideUserCache
import com.embabel.guide.domain.GuideUserData
import com.embabel.guide.domain.GuideUserService
import com.embabel.hub.integrations.UserKeyStore
import com.embabel.hub.integrations.UserModelFactory
import org.drivine.manager.StatelessGraphObjectManager
import org.drivine.manager.PersistenceManager
import org.drivine.manager.PersistenceManagerFactory
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationEventPublisher
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
    fun titleGenerator(
        userKeyStore: UserKeyStore,
        userModelFactory: UserModelFactory,
        guideUserCache: GuideUserCache,
        guideUserService: GuideUserService,
    ): TitleGenerator =
        LlmTitleGenerator { prompt, userId ->
            val logger = org.slf4j.LoggerFactory.getLogger("TitleGenerator")
            logger.info("[TITLE] generate called: userId={}", userId)
            // userId is the internal GuideUser ID; key store is keyed by web user ID
            val webUserId = userId?.let { id ->
                guideUserCache.getByInternalId(id)?.webUser?.id
                    ?: guideUserService.findWebUserById(id).orElse(null)?.webUser?.id
            }
            logger.info("[TITLE] resolved webUserId={}", webUserId)
            val activeKey = webUserId?.let { userKeyStore.getActiveKey(it) }
            if (activeKey == null) {
                logger.warn("[TITLE] No active key for webUserId={}, returning fallback", webUserId)
                return@LlmTitleGenerator TitleGenerator.DEFAULT_FALLBACK
            }
            val (provider, apiKey) = activeKey
            logger.info("[TITLE] Calling {} / {} for title", provider, provider.summarizerModel)
            val result = userModelFactory.getLlmService(provider, provider.summarizerModel, apiKey) as SpringAiLlmService
            val title = result.chatModel.call(Prompt(UserMessage(prompt))).result?.output?.text
            logger.info("[TITLE] LLM returned: '{}'", title?.take(80))
            title ?: TitleGenerator.DEFAULT_FALLBACK
        }

    @Bean
    @Primary
    fun persistenceManager(factory: PersistenceManagerFactory): PersistenceManager {
        val pm = factory.get("neo")
        // Register GuideUserData as implementation of StoredUser interface.
        // Composite label key is sorted alphabetically with pipe separator.
        pm.registerSubtype(
            StoredUser::class.java,
            listOf("GuideUser", "User"),
            GuideUserData::class.java
        )
        return pm
    }

    @Bean
    fun chatSessionRepository(
        @Qualifier("neoGraphObjectManager") graphObjectManager: StatelessGraphObjectManager,
        persistenceManager: PersistenceManager,
        eventPublisher: ApplicationEventPublisher,
    ): ChatSessionRepository {
        // chat-store takes a PersistenceManager as of 0.5.0; the event publisher moved after it
        // and is now optional.
        return ChatSessionRepositoryImpl(graphObjectManager, persistenceManager, eventPublisher)
    }
}
