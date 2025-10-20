package com.embabel.guide.chat.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
class SecurityConfig {

    val patterns = arrayOf(
        "/ws/**",
        "/app/**",
        "/topic/**",
        "/user/**",
        "/",
        "/index.html",
        "/static/**"
    )

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf { it.disable() }
            .authorizeHttpRequests {
                it.requestMatchers(*patterns).permitAll()
                it.requestMatchers(
                    HttpMethod.POST,
                    "/api/messages/user",
                    "/api/hub/register"
                ).permitAll()
                it.anyRequest().authenticated()
            }
        return http.build()
    }
}
