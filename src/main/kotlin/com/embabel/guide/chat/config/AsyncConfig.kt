package com.embabel.guide.chat.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

/**
 * Configuration for async task execution.
 * Provides the applicationTaskExecutor bean required by Spring Security.
 */
@Configuration
class AsyncConfig {

    @Bean(name = ["applicationTaskExecutor"])
    fun applicationTaskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 2
        executor.maxPoolSize = 5
        executor.queueCapacity = 100
        executor.setThreadNamePrefix("guide-async-")
        executor.initialize()
        return executor
    }
}