package com.l2kt.commons.concurrent

import com.l2kt.commons.logging.CLogger
import java.util.concurrent.ScheduledFuture

/**
 * DEPRECATED: Legacy ThreadPool wrapper for backward compatibility.
 *
 * All calls are delegated to the new CoroutinePool.
 * Use CoroutinePool directly for new code.
 *
 * This wrapper ensures 100% backward compatibility with existing code
 * that still references ThreadPool.* methods.
 */
@Deprecated("Use CoroutinePool instead", ReplaceWith("CoroutinePool"))
object ThreadPool {
    private val LOGGER = CLogger(ThreadPool::class.java.name)

    /**
     * Initialize thread pooling system.
     * Delegates to CoroutinePool.init()
     */
    fun init() {
        LOGGER.warn("ThreadPool.init() called. Delegating to CoroutinePool.init()")
        CoroutinePool.init()
    }

    /**
     * Schedule a one-shot action.
     * Delegates to CoroutinePool.schedule()
     */
    fun schedule(r: Runnable, delay: Long): ScheduledFuture<*>? {
        return CoroutinePool.schedule(r, delay)
    }

    /**
     * Schedule a periodic action.
     * Delegates to CoroutinePool.scheduleAtFixedRate()
     */
    fun scheduleAtFixedRate(r: Runnable, delay: Long, period: Long): ScheduledFuture<*>? {
        return CoroutinePool.scheduleAtFixedRate(r, delay, period)
    }

    /**
     * Execute a task immediately.
     * Delegates to CoroutinePool.execute()
     */
    fun execute(r: Runnable) {
        CoroutinePool.execute(r)
    }

    /**
     * Get thread pool stats.
     * Delegates to CoroutinePool.printMetrics()
     */
    fun getStats() {
        LOGGER.info("ThreadPool stats (delegated to CoroutinePool):")
        CoroutinePool.printMetrics()
    }

    /**
     * Shutdown thread pooling system.
     * Delegates to CoroutinePool.shutdown()
     */
    fun shutdown() {
        LOGGER.warn("ThreadPool.shutdown() called. Delegating to CoroutinePool.shutdown()")
        CoroutinePool.shutdown()
    }
}