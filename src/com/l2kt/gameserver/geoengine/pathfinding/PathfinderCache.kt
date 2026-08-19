package com.l2kt.gameserver.geoengine.pathfinding

import com.l2kt.commons.logging.CLogger
import com.l2kt.gameserver.model.location.Location
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.LongAdder

/**
 * LRU cache for pre-computed pathfinding results.
 * Eliminates redundant A* computations for frequently-traveled routes.
 *
 * Features:
 * - Thread-safe ConcurrentHashMap storage
 * - Bounded size with LRU eviction
 * - Hit/miss ratio tracking
 * - Cache key based on rounded geo coordinates (16-cell granularity)
 *
 * Ported from BrProject PathfinderCache with simplifications for L2kt.
 */
object PathfinderCache {
    private val LOGGER = CLogger(PathfinderCache::class.java.name)

    private const val DEFAULT_MAX_SIZE = 10_000
    private const val COORD_GRANULARITY = 16

    private val cache = ConcurrentHashMap<Long, List<Location>>()
    private val accessOrder = ConcurrentLinkedQueue<Long>()
    private var maxSize = DEFAULT_MAX_SIZE

    private val cacheHits = LongAdder()
    private val cacheMisses = LongAdder()

    @JvmStatic
    fun init(size: Int = DEFAULT_MAX_SIZE) {
        maxSize = size.coerceIn(100, 100_000)
        LOGGER.info("PathfinderCache initialized (max: $maxSize entries)")
    }

    @JvmStatic
    fun get(ox: Int, oy: Int, oz: Int, tx: Int, ty: Int, tz: Int): List<Location>? {
        val key = makeKey(ox, oy, oz, tx, ty, tz)
        val cached = cache[key]

        if (cached != null) {
            cacheHits.increment()
            accessOrder.remove(key)
            accessOrder.offer(key)
            return ArrayList(cached)
        }

        cacheMisses.increment()
        return null
    }

    @JvmStatic
    fun put(ox: Int, oy: Int, oz: Int, tx: Int, ty: Int, tz: Int, path: List<Location>) {
        if (path.isEmpty()) return

        val key = makeKey(ox, oy, oz, tx, ty, tz)

        while (cache.size >= maxSize && accessOrder.isNotEmpty()) {
            val evictKey = accessOrder.poll() ?: break
            cache.remove(evictKey)
        }

        cache[key] = ArrayList(path)
        accessOrder.offer(key)
    }

    @JvmStatic
    fun clear() {
        cache.clear()
        accessOrder.clear()
        LOGGER.info("PathfinderCache cleared")
    }

    @JvmStatic
    fun getStats(): Map<String, Any> {
        val hits = cacheHits.sum()
        val misses = cacheMisses.sum()
        val total = hits + misses
        val hitRatio = if (total > 0) hits.toDouble() / total * 100 else 0.0

        return mapOf(
            "size" to cache.size,
            "maxSize" to maxSize,
            "hits" to hits,
            "misses" to misses,
            "hitRatio" to "%.1f%%".format(hitRatio),
            "fillRatio" to "%.1f%%".format(cache.size.toDouble() / maxSize * 100)
        )
    }

    @JvmStatic
    fun printStats() {
        val stats = getStats()
        LOGGER.info("=== PathfinderCache Stats ===")
        stats.forEach { (k, v) -> LOGGER.info("  $k: $v") }
    }

    private fun makeKey(ox: Int, oy: Int, oz: Int, tx: Int, ty: Int, tz: Int): Long {
        val rox = ox / COORD_GRANULARITY
        val roy = oy / COORD_GRANULARITY
        val rtx = tx / COORD_GRANULARITY
        val rty = ty / COORD_GRANULARITY

        return ((rox.toLong() and 0xFFFF) shl 48) or
            ((roy.toLong() and 0xFFFF) shl 32) or
            ((rtx.toLong() and 0xFFFF) shl 16) or
            (rty.toLong() and 0xFFFF)
    }

    @JvmStatic
    fun shutdown() {
        printStats()
        clear()
    }
}
