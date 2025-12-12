package com.embabel.guide.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        // MCP endpoints - allow all origins for CLI tools like Claude Code
        registry.addMapping("/sse/**")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
        registry.addMapping("/mcp/**")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)

        // Other endpoints - specific origins
        registry.addMapping("/**")
            .allowedOrigins(
                "http://localhost:3000",      // Next.js dev server
                "http://localhost:3001",      // Alternative dev port
                "http://localhost:8042",      // Docker frontend
                "http://localhost:5173",      // MCP Inspector (Vite)
                "http://localhost:6274",      // MCP Inspector (npx)
                "https://embabel.com",        // Production domain
                "https://www.embabel.com",    // Production domain with www
                "app://-"                     // Electron/Tauri app
            )
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val source = UrlBasedCorsConfigurationSource()

        // MCP endpoints - allow all origins for CLI tools like Claude Code
        val mcpConfig = CorsConfiguration()
        mcpConfig.allowedOriginPatterns = listOf("*")
        mcpConfig.allowedMethods = listOf("GET", "POST", "OPTIONS")
        mcpConfig.allowedHeaders = listOf("*")
        mcpConfig.allowCredentials = true
        source.registerCorsConfiguration("/sse/**", mcpConfig)
        source.registerCorsConfiguration("/mcp/**", mcpConfig)

        // Other endpoints - specific origins
        val defaultConfig = CorsConfiguration()
        defaultConfig.allowedOrigins = listOf(
            "http://localhost:3000",      // Next.js dev server
            "http://localhost:3001",      // Alternative dev port
            "http://localhost:8042",      // Docker frontend
            "http://localhost:5173",      // MCP Inspector (Vite)
            "http://localhost:6274",      // MCP Inspector (npx)
            "https://embabel.com",        // Production domain
            "https://www.embabel.com",    // Production domain with www
            "app://-"                     // Electron/Tauri app
        )
        defaultConfig.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        defaultConfig.allowedHeaders = listOf("*")
        defaultConfig.allowCredentials = true
        source.registerCorsConfiguration("/**", defaultConfig)

        return source
    }
}