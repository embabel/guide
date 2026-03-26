package com.embabel.hub

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * IP-based rate limiting filter with two tiers:
 * - Tight limit on specific paths (e.g. unauthenticated key validation)
 * - Global limit on all other requests
 *
 * Buckets are created lazily per IP and kept in memory.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RateLimitFilter : OncePerRequestFilter() {

    private val globalBuckets = ConcurrentHashMap<String, Bucket>()
    private val tightBuckets = ConcurrentHashMap<String, Bucket>()

    private val tightPaths = setOf(
        "/api/hub/integrations/keys/validate",
    )

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val ip = request.remoteAddr

        if (request.requestURI in tightPaths) {
            val bucket = tightBuckets.computeIfAbsent(ip) { newTightBucket() }
            if (!bucket.tryConsume(1)) {
                response.status = HttpStatus.TOO_MANY_REQUESTS.value()
                response.writer.write("""{"error":"Too many requests"}""")
                return
            }
        }

        val globalBucket = globalBuckets.computeIfAbsent(ip) { newGlobalBucket() }
        if (!globalBucket.tryConsume(1)) {
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.writer.write("""{"error":"Too many requests"}""")
            return
        }

        filterChain.doFilter(request, response)
    }

    companion object {
        private fun newGlobalBucket(): Bucket = Bucket.builder()
            .addLimit(Bandwidth.simple(300, Duration.ofMinutes(1)))
            .build()

        private fun newTightBucket(): Bucket = Bucket.builder()
            .addLimit(Bandwidth.simple(10, Duration.ofMinutes(1)))
            .build()
    }
}