package com.embabel.guide.chat.security

import com.embabel.hub.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.util.matcher.AntPathRequestMatcher
import org.springframework.security.web.util.matcher.OrRequestMatcher

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {

    val mcpPatterns = arrayOf(
        "/sse",
        "/sse/**",
        "/mcp",
        "/mcp/**",
    )

    private val mcpMatchers = arrayOf(
        AntPathRequestMatcher("/sse"),
        AntPathRequestMatcher("/sse/**"),
        AntPathRequestMatcher("/mcp"),
        AntPathRequestMatcher("/mcp/**"),
    )

    private val mcpMatcher = OrRequestMatcher(*mcpMatchers)

    /**
     * Hard bypass for MCP endpoints.
     *
     * Some auto-configurations can contribute additional SecurityFilterChains that take precedence,
     * which can cause `/mcp` to return 403 even if we try to permit it. Ignoring bypasses the entire
     * Spring Security filter chain for these endpoints, which is what Cursor/Claude/etc. want.
     */
    @Bean
    fun webSecurityCustomizer(): WebSecurityCustomizer = WebSecurityCustomizer { web ->
        web.ignoring().requestMatchers(*mcpMatchers)
    }

    val permittedPatterns = arrayOf(
        "/ws/**",
        "/app/**",
        "/topic/**",
        "/user/**",
        "/",
        "/index.html",
        "/static/**",
    ) + mcpPatterns

    @Bean
    @Order(0)
    fun mcpFilterChain(http: HttpSecurity): SecurityFilterChain {
        // Some Cursor builds try streamable HTTP first (POST /mcp...), then fall back to SSE (/sse).
        // If any other auto-configured security chain matches /mcp first, it can result in 403s and flakey MCP.
        // This chain is scoped to MCP endpoints only and is highest precedence.
        // Use AntPathRequestMatcher so this applies even if /mcp is registered outside Spring MVC handler mappings.
        http.securityMatcher(mcpMatcher)
            .csrf { it.disable() }
            .cors { }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }

    @Bean
    @Order(1)
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf { it.disable() }
            .cors { }  // Enable CORS with default configuration from WebConfig
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .authorizeHttpRequests {
                it.requestMatchers(*permittedPatterns).permitAll()
                it.requestMatchers(*mcpMatchers).permitAll()
                it.requestMatchers(
                    HttpMethod.POST,
                    "/api/messages/user",
                    "/api/hub/register",
                    "/api/hub/login",
                    "/api/v1/data/load-references"
                ).permitAll()
                it.requestMatchers(
                    HttpMethod.GET,
                    "/api/auth/me",
                    "/api/hub/personas",
                    "/api/v1/data/stats"
                ).permitAll()
                it.anyRequest().authenticated()
            }
        return http.build()
    }
}
