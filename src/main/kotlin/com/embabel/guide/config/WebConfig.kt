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
        registry.addMapping("/**")
            .allowedOrigins(
                "http://localhost:3000",      // Next.js dev server
                "http://localhost:3001",      // Alternative dev port
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
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = listOf(
            "http://localhost:3000",      // Next.js dev server
            "http://localhost:3001",      // Alternative dev port
            "http://localhost:5173",      // MCP Inspector (Vite)
            "http://localhost:6274",      // MCP Inspector (npx)
            "https://embabel.com",        // Production domain
            "https://www.embabel.com",    // Production domain with www
            "app://-"                     // Electron/Tauri app
        )
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}