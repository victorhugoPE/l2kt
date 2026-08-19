package com.l2kt.test

import com.l2kt.commons.concurrent.CoroutinePool
import com.l2kt.commons.logging.CLogger
import com.l2kt.gameserver.geoengine.GeoEngine
import com.l2kt.gameserver.geoengine.pathfinding.AdvancedPathFinder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Post-implementation benchmark for Phase 1 (CoroutinePool) + Phase 2 (AdvancedPathFinder).
 * Runs the same Category B (GeoData/Pathfinding Baseline) tests plus a controlled
 * AdvancedPathFinder-only micro-benchmark to compare A* vs NodeBuffer.
 */
object PathFinderBenchmark {
    private val LOGGER = CLogger(PathFinderBenchmark::class.java.name)

    /**
     * Run benchmark of the new AdvancedPathFinder (no GeoEngine dependency needed).
     */
    fun runAdvancedPathFinderBenchmark(iterations: Int = 100): Map<String, Any> {
        val times = mutableListOf<Long>()
        var failures = 0
        var totalNodes = 0

        repeat(iterations) {
            val start = System.nanoTime()
            try {
                val path = AdvancedPathFinder.findPath(
                    1000, 1000, 0,
                    1010, 1010, 0
                )
                val elapsed = (System.nanoTime() - start) / 1_000_000
                times.add(elapsed)
                totalNodes += path.size
            } catch (e: Exception) {
                failures++
            }
        }

        return mapOf(
            "iterations" to iterations,
            "failures" to failures,
            "avgTimeMs" to if (times.isNotEmpty()) times.average() else 0.0,
            "minTimeMs" to (times.minOrNull() ?: 0L),
            "maxTimeMs" to (times.maxOrNull() ?: 0L),
            "stdDevMs" to if (times.size > 1) standardDeviation(times) else 0.0,
            "totalNodesExpanded" to totalNodes,
            "avgPathLength" to if (iterations - failures > 0) totalNodes.toDouble() / (iterations - failures) else 0.0
        )
    }

    /**
     * Benchmark CoroutinePool task submission throughput.
     */
    fun runCoroutinePoolBenchmark(taskCount: Int = 100): Map<String, Any> {
        val latch = CountDownLatch(taskCount)
        val startTime = System.nanoTime()

        repeat(taskCount) {
            CoroutinePool.execute {
                Thread.sleep((Math.random() * 5).toLong())
                latch.countDown()
            }
        }

        val completed = latch.await(30, TimeUnit.SECONDS)
        val elapsed = (System.nanoTime() - startTime) / 1_000_000

        return mapOf(
            "taskCount" to taskCount,
            "completed" to completed,
            "elapsedMs" to elapsed,
            "avgMsPerTask" to elapsed.toDouble() / taskCount,
            "tasksPerSecond" to (taskCount.toDouble() / elapsed * 1000)
        )
    }

    /**
     * Simulated pathfinding stress using GeoEngine (matches Category B baseline).
     */
    fun runPathfindingStress(count: Int = 100): Map<String, Any> {
        val times = mutableListOf<Long>()
        var failures = 0
        var emptyPaths = 0

        repeat(count) {
            val start = System.nanoTime()
            try {
                val path = GeoEngine.findPath(
                    82098, 149862, -3473,
                    83431, 147939, -3472,
                    false
                )
                val elapsed = (System.nanoTime() - start) / 1_000_000
                times.add(elapsed)
                if (path == null || path.isEmpty()) emptyPaths++
            } catch (e: Exception) {
                failures++
            }
        }

        return mapOf(
            "count" to count,
            "failures" to failures,
            "emptyPaths" to emptyPaths,
            "avgTimeMs" to if (times.isNotEmpty()) times.average() else 0.0,
            "minTimeMs" to (times.minOrNull() ?: 0L),
            "maxTimeMs" to (times.maxOrNull() ?: 0L),
            "stdDevMs" to if (times.size > 1) standardDeviation(times) else 0.0
        )
    }

    private fun standardDeviation(values: List<Long>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return Math.sqrt(variance)
    }

    /**
     * Memory snapshot.
     */
    fun memorySnapshot(): Map<String, Any> {
        val runtime = Runtime.getRuntime()
        return mapOf(
            "totalMemoryMB" to runtime.totalMemory() / 1_048_576,
            "freeMemoryMB" to runtime.freeMemory() / 1_048_576,
            "usedMemoryMB" to (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576,
            "maxMemoryMB" to runtime.maxMemory() / 1_048_576
        )
    }
}
