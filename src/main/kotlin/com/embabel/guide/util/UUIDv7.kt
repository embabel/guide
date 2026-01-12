package com.embabel.guide.util

import com.fasterxml.uuid.Generators
import java.util.UUID

/**
 * Utility for generating UUIDv7 (time-ordered) identifiers.
 *
 * UUIDv7 embeds a Unix timestamp in the first 48 bits, making them:
 * - Naturally sortable by creation time
 * - Efficient for database indexing
 * - Globally unique without coordination
 */
object UUIDv7 {

    private val generator = Generators.timeBasedEpochGenerator()

    /**
     * Generate a new UUIDv7.
     */
    fun generate(): UUID = generator.generate()

    /**
     * Generate a new UUIDv7 as a string.
     */
    fun generateString(): String = generate().toString()
}