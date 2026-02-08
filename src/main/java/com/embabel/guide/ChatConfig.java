package com.embabel.guide;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.chat.Chatbot;
import com.embabel.chat.ConversationFactory;
import com.embabel.chat.ConversationFactoryProvider;
import com.embabel.chat.ConversationStoreType;
import com.embabel.chat.agent.AgentProcessChatbot;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Create a chatbot backed by all actions on the AgentPlatform
 * that respond to UserMessages. Will use utility planning.
 * Conversations are persisted to Neo4j via the STORED conversation factory.
 */
@Configuration
class ChatConfig {

    @Bean
    Chatbot chatbot(AgentPlatform agentPlatform, ConversationFactoryProvider conversationFactoryProvider) {
        ConversationFactory factory = conversationFactoryProvider.getFactory(ConversationStoreType.STORED);
        return AgentProcessChatbot.utilityFromPlatform(agentPlatform, factory);
    }
}
