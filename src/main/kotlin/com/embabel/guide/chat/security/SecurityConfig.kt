package com.embabel.guide.chat.security

import com.embabel.hub.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {

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
            .cors { }  // Enable CORS with default configuration from WebConfig
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests {
                it.requestMatchers(*patterns).permitAll()
                it.requestMatchers(
                    HttpMethod.POST,
                    "/api/messages/user",
                    "/api/hub/register",
                    "/api/hub/login"
                ).permitAll()
                it.requestMatchers(
                    HttpMethod.GET,
                    "/api/auth/me"
                ).permitAll()
                it.anyRequest().authenticated()
            }
        return http.build()
    }
}
