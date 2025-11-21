package com.embabel.agent.rag.neo.drivine

/**
 * Query result abstraction that is not coupled to OGM.
 * Provides access to result rows as a list of maps.
 */
class QueryResult(
    private val rows: List<Map<String, Any>>
) : Iterable<Map<String, Any>> by rows {

    /**
     * Get all result rows.
     */
    fun items(): List<Map<String, Any>> = rows

    /**
     * Get a single row from the result set, or null if empty.
     */
    fun singleOrNull(): Map<String, Any>? = rows.singleOrNull()
}
