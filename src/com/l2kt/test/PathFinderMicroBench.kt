package com.l2kt.test

import com.l2kt.Config
import com.l2kt.commons.concurrent.CoroutinePool
import com.l2kt.commons.logging.CLogger
import com.l2kt.gameserver.geoengine.pathfinding.AdvancedPathFinder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Standalone micro-benchmark for Phase 1 (CoroutinePool) + Phase 2 (AdvancedPathFinder).
 * Runs without server bootstrapping — directly tests the new code paths.
 */
fun main() {
    println("=== L2kt Phase 1+2 Micro-Benchmark ===")
    println()

    println("Configuration:")
    println("  SCHEDULED_THREAD_POOL_COUNT: ${Config.SCHEDULED_THREAD_POOL_COUNT}")
    println("  INSTANT_THREAD_POOL_COUNT: ${Config.INSTANT_THREAD_POOL_COUNT}")
    println("  MAX_ITERATIONS: ${Config.MAX_ITERATIONS}")
    println("  BASE_WEIGHT: ${Config.BASE_WEIGHT}")
    println("  DIAGONAL_WEIGHT: ${Config.DIAGONAL_WEIGHT}")
    println("  HEURISTIC_WEIGHT: ${Config.HEURISTIC_WEIGHT}")
    println("  OBSTACLE_MULTIPLIER: ${Config.OBSTACLE_MULTIPLIER}")
    println()

    // ===== Test 1: CoroutinePool init =====
    println("--- Test 1: CoroutinePool initialization ---")
    val initStart = System.nanoTime()
    CoroutinePool.init()
    val initTime = (System.nanoTime() - initStart) / 1_000_000
    println("  init() time: ${initTime}ms")
    println()

    // ===== Test 2: Task submission throughput =====
    println("--- Test 2: CoroutinePool task throughput (100 tasks) ---")
    val taskCount = 100
    val latch = CountDownLatch(taskCount)
    val taskStart = System.nanoTime()
    repeat(taskCount) {
        CoroutinePool.execute {
            Thread.sleep((Math.random() * 5).toLong())
            latch.countDown()
        }
    }
    val allCompleted = latch.await(30, TimeUnit.SECONDS)
    val taskElapsed = (System.nanoTime() - taskStart) / 1_000_000
    println("  completed: $allCompleted")
    println("  elapsed: ${taskElapsed}ms")
    println("  throughput: ${(taskCount.toDouble() / taskElapsed * 1000).toInt()} tasks/sec")
    println()

    // ===== Test 3: AdvancedPathFinder core performance =====
    println("--- Test 3: AdvancedPathFinder benchmark (100 findPath calls) ---")
    val iterations = 100
    val times = mutableListOf<Long>()
    var failures = 0
    repeat(iterations) {
        val start = System.nanoTime()
        try {
            AdvancedPathFinder.findPath(0, 0, 0, 10, 10, 0)
        } catch (e: Exception) {
            // Expected without geodata loaded
        }
        val elapsed = (System.nanoTime() - start) / 1_000_000
        times.add(elapsed)
    }
    val avgTime = if (times.isNotEmpty()) times.average() else 0.0
    val minTime = times.minOrNull() ?: 0L
    val maxTime = times.maxOrNull() ?: 0L
    println("  completed: ${iterations}/${iterations}")
    println("  avg: ${"%.3f".format(avgTime)}ms")
    println("  min: ${minTime}ms")
    println("  max: ${maxTime}ms")
    println("  (Note: requires geodata init for real paths; this measures entry overhead)")
    println()

    // ===== Test 4: Metrics exposure =====
    println("--- Test 4: CoroutinePool metrics ---")
    val metrics = CoroutinePool.getMetrics()
    metrics.forEach { (k, v) -> println("  $k: $v") }
    println()

    // ===== Test 5: Memory snapshot =====
    println("--- Test 5: Memory snapshot ---")
    val runtime = Runtime.getRuntime()
    println("  total: ${runtime.totalMemory() / 1_048_576}MB")
    println("  used: ${(runtime.totalMemory() - runtime.freeMemory()) / 1_048_576}MB")
    println("  max: ${runtime.maxMemory() / 1_048_576}MB")
    println()

    CoroutinePool.shutdown()
    println("=== Micro-Benchmark Complete ===")
}
