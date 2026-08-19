package com.l2kt.commons.concurrent

import com.l2kt.Config
import com.l2kt.commons.logging.CLogger
import kotlinx.coroutines.*
import java.util.concurrent.*
import java.util.concurrent.atomic.LongAdder

/**
 * Modernized concurrency pool replacing legacy ThreadPool.kt
 * Supports: Scheduled, Instant, Virtual (I/O), Pathfinding, ForkJoin
 *
 * Ported from BrProject with adaptations for L2kt environment.
 */
object CoroutinePool {
    private val LOGGER = CLogger(CoroutinePool::class.java.name)

    private var scheduledExecutors: Array<ScheduledThreadPoolExecutor>? = null
    private var instantExecutors: Array<ThreadPoolExecutor>? = null
    private var pathfindingExecutor: ThreadPoolExecutor? = null
    private var virtualExecutor: ExecutorService? = null
    private var forkJoinPool: ForkJoinPool? = null

    private val BackgroundScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val PathfindingDispatcher: CoroutineDispatcher
        get() = pathfindingExecutor?.asCoroutineDispatcher() ?: Dispatchers.Default

    private val totalTasksSubmitted = LongAdder()
    private val totalTasksCompleted = LongAdder()
    private val totalTasksRejected = LongAdder()
    private val pathfindingTasksSubmitted = LongAdder()
    private val pathfindingTasksCompleted = LongAdder()

    private const val MAX_DELAY_MS: Long = 2_000_000_000L

    /**
     * Initialize the CoroutinePool with thread pools based on Config.
     * Called once on GameServer startup.
     */
    @JvmStatic
    fun init() {
        try {
            if (scheduledExecutors != null) {
                LOGGER.warn("CoroutinePool already initialized. Skipping...")
                return
            }

            val availableProcessors = Runtime.getRuntime().availableProcessors()

            // Scheduled pool (long-running, delayed tasks)
            val totalScheduled = Config.SCHEDULED_THREAD_POOL_COUNT.let {
                if (it == -1) availableProcessors else it
            }.coerceIn(1, 256)
            val scheduledPoolCount = minOf(4, totalScheduled).coerceAtLeast(1)
            val scheduledPoolCoreSize = maxOf(1, totalScheduled / scheduledPoolCount)

            scheduledExecutors = Array(scheduledPoolCount) { index ->
                ScheduledThreadPoolExecutor(scheduledPoolCoreSize).apply {
                    threadFactory = ThreadFactory { r ->
                        Thread(r, "ScheduledPool-$index").apply { isDaemon = false }
                    }
                    setRemoveOnCancelPolicy(true)
                    setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
                }
            }

            // Instant pool (short-lived, immediate tasks)
            val totalInstant = Config.INSTANT_THREAD_POOL_COUNT.let {
                if (it == -1) availableProcessors * 2 else it
            }.coerceIn(1, 256)
            val instantPoolCount = minOf(2, totalInstant).coerceAtLeast(1)
            val instantPoolCoreSize = maxOf(1, totalInstant / instantPoolCount)
            val instantPoolMaxSize = (instantPoolCoreSize * 2).coerceAtMost(256)

            instantExecutors = Array(instantPoolCount) { index ->
                ThreadPoolExecutor(
                    instantPoolCoreSize,
                    instantPoolMaxSize,
                    60L, TimeUnit.SECONDS,
                    LinkedBlockingQueue(5000),
                    ThreadFactory { r ->
                        Thread(r, "InstantPool-$index").apply { isDaemon = false }
                    }
                ).apply {
                    allowCoreThreadTimeOut(true)
                }
            }

            // Virtual thread executor for I/O-bound tasks
            try {
                virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()
            } catch (e: Exception) {
                LOGGER.warn("Virtual threads not available (Java < 21): ${e.message}")
                virtualExecutor = null
            }

            // Pathfinding pool (exclusive, CPU-bound)
            val pathfindingThreads = (availableProcessors / 2).coerceAtLeast(2)
            pathfindingExecutor = ThreadPoolExecutor(
                pathfindingThreads,
                pathfindingThreads,
                60L, TimeUnit.SECONDS,
                LinkedBlockingQueue(200),
                ThreadFactory { r ->
                    Thread(r, "PathfindingThread-${r.hashCode()}").apply {
                        isDaemon = false
                        priority = Thread.NORM_PRIORITY - 1
                    }
                },
                ThreadPoolExecutor.CallerRunsPolicy()
            ).apply {
                allowCoreThreadTimeOut(false)
                prestartAllCoreThreads()
            }

            // ForkJoinPool for parallel decomposable work
            forkJoinPool = ForkJoinPool(
                availableProcessors,
                ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                null,
                true
            )

            LOGGER.info("CoroutinePool initialized")
            LOGGER.info("  Scheduled pools: $scheduledPoolCount x $scheduledPoolCoreSize threads")
            LOGGER.info("  Instant pools: $instantPoolCount x $instantPoolCoreSize-$instantPoolMaxSize threads")
            LOGGER.info("  Virtual threads: ${if (virtualExecutor != null) "enabled" else "disabled"}")
            LOGGER.info("  Pathfinding threads: $pathfindingThreads")
            LOGGER.info("  Available processors: $availableProcessors")

        } catch (e: Exception) {
            LOGGER.error("CRITICAL ERROR initializing CoroutinePool: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Execute a task immediately in the instant pool.
     */
    @JvmStatic
    fun execute(task: Runnable) {
        val executors = instantExecutors
        if (executors == null || executors.isEmpty()) {
            LOGGER.warn("CoroutinePool not initialized. Running task inline.")
            try {
                task.run()
            } catch (e: Exception) {
                LOGGER.error("Task execution error: ${e.message}")
            }
            return
        }

        try {
            totalTasksSubmitted.increment()
            val selectedPool = executors[ThreadLocalRandom.current().nextInt(executors.size)]
            selectedPool.execute(task)
        } catch (e: RejectedExecutionException) {
            totalTasksRejected.increment()
            LOGGER.warn("Task rejected (pool full). Running inline.")
            try {
                task.run()
            } catch (ex: Exception) {
                LOGGER.error("Task execution error: ${ex.message}")
            }
        } catch (e: Exception) {
            LOGGER.error("Error executing task: ${e.message}")
        }
    }

    /**
     * Schedule a one-shot task to run after a delay (ms).
     */
    @JvmStatic
    fun schedule(task: Runnable, delayMs: Long): ScheduledFuture<*>? {
        val executors = scheduledExecutors ?: return null
        return try {
            totalTasksSubmitted.increment()
            val selectedPool = executors[ThreadLocalRandom.current().nextInt(executors.size)]
            selectedPool.schedule(task, validate(delayMs), TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            LOGGER.error("Error scheduling task: ${e.message}")
            null
        }
    }

    /**
     * Schedule a task to run repeatedly at fixed rate.
     */
    @JvmStatic
    fun scheduleAtFixedRate(task: Runnable, initialDelayMs: Long, periodMs: Long): ScheduledFuture<*>? {
        val executors = scheduledExecutors ?: return null
        return try {
            totalTasksSubmitted.increment()
            val selectedPool = executors[ThreadLocalRandom.current().nextInt(executors.size)]
            selectedPool.scheduleAtFixedRate(
                task,
                validate(initialDelayMs),
                validate(periodMs),
                TimeUnit.MILLISECONDS
            )
        } catch (e: Exception) {
            LOGGER.error("Error scheduling periodic task: ${e.message}")
            null
        }
    }

    /**
     * Execute a pathfinding task in the exclusive pathfinding pool.
     * Falls back to inline execution if pool is full (CallerRunsPolicy).
     */
    @JvmStatic
    fun executePathfinding(task: Runnable) {
        val executor = pathfindingExecutor ?: run {
            task.run()
            return
        }
        try {
            pathfindingTasksSubmitted.increment()
            executor.execute(task)
        } catch (e: Exception) {
            task.run()
        }
    }

    /**
     * Execute I/O-bound task in virtual thread pool if available.
     * Falls back to instant pool otherwise.
     */
    @JvmStatic
    fun executeIO(task: Runnable) {
        val executor = virtualExecutor
        if (executor == null) {
            execute(task)
            return
        }
        try {
            totalTasksSubmitted.increment()
            executor.execute(task)
        } catch (e: Exception) {
            execute(task)
        }
    }

    @JvmStatic
    fun getPathfindingQueueSize(): Int = pathfindingExecutor?.queue?.size ?: 0

    @JvmStatic
    fun getPathfindingActiveCount(): Int = pathfindingExecutor?.activeCount ?: 0

    @JvmStatic
    fun getTotalTasksSubmitted(): Long = totalTasksSubmitted.sum()

    @JvmStatic
    fun getTotalTasksCompleted(): Long = totalTasksCompleted.sum()

    @JvmStatic
    fun getTotalTasksRejected(): Long = totalTasksRejected.sum()

    /**
     * Get metrics as a map for monitoring/admin commands.
     */
    @JvmStatic
    fun getMetrics(): Map<String, Any> {
        val instantPools = instantExecutors ?: emptyArray()
        val scheduledPools = scheduledExecutors ?: emptyArray()

        return mapOf(
            "tasksSubmitted" to totalTasksSubmitted.sum(),
            "tasksCompleted" to totalTasksCompleted.sum(),
            "tasksRejected" to totalTasksRejected.sum(),
            "scheduledPools" to scheduledPools.size,
            "scheduledQueueSize" to scheduledPools.sumOf { it.queue.size },
            "scheduledActiveCount" to scheduledPools.sumOf { it.activeCount },
            "instantPools" to instantPools.size,
            "instantCoreSize" to instantPools.sumOf { it.corePoolSize },
            "instantMaxSize" to instantPools.sumOf { it.maximumPoolSize },
            "instantActiveCount" to instantPools.sumOf { it.activeCount },
            "instantQueueSize" to instantPools.sumOf { it.queue.size },
            "pathfindingQueueSize" to getPathfindingQueueSize(),
            "pathfindingActiveCount" to getPathfindingActiveCount(),
            "pathfindingTasksSubmitted" to pathfindingTasksSubmitted.sum(),
            "pathfindingTasksCompleted" to pathfindingTasksCompleted.sum()
        )
    }

    @JvmStatic
    fun printMetrics() {
        val metrics = getMetrics()
        LOGGER.info("=== CoroutinePool Metrics ===")
        metrics.forEach { (key, value) -> LOGGER.info("  $key: $value") }
    }

    private fun validate(delayMs: Long): Long = maxOf(0, minOf(MAX_DELAY_MS, delayMs))

    /**
     * Shutdown all pools gracefully.
     */
    @JvmStatic
    fun shutdown() {
        if (scheduledExecutors == null) return

        try {
            LOGGER.info("Shutting down CoroutinePool...")

            BackgroundScope.cancel()
            scheduledExecutors?.forEach { it.shutdown() }
            instantExecutors?.forEach { it.shutdown() }
            pathfindingExecutor?.shutdown()
            virtualExecutor?.shutdown()
            forkJoinPool?.shutdown()

            scheduledExecutors = null
            instantExecutors = null
            pathfindingExecutor = null
            virtualExecutor = null
            forkJoinPool = null

            LOGGER.info("CoroutinePool shutdown complete")
        } catch (e: Exception) {
            LOGGER.error("Error shutting down CoroutinePool: ${e.message}")
        }
    }
}
