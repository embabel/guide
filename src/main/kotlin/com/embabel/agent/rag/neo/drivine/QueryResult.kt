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

    inline fun <reified T : Number> numberOrZero(key: String): T {
        val v = items().firstOrNull()?.get(key) as? Number ?: return zeroOf()
        return when (T::class) {
            Int::class    -> v.toInt() as T
            Long::class   -> v.toLong() as T
            Float::class  -> v.toFloat() as T
            Double::class -> v.toDouble() as T
            Short::class  -> v.toShort() as T
            Byte::class   -> v.toByte() as T
            else -> error("Unsupported number type: ${T::class}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Number> zeroOf(): T =
        when (T::class) {
            Int::class    -> 0 as T
            Long::class   -> 0L as T
            Float::class  -> 0f as T
            Double::class -> 0.0 as T
            Short::class  -> 0.toShort() as T
            Byte::class   -> 0.toByte() as T
            else -> error("Unsupported number type: ${T::class}")
        }
}
