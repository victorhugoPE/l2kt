package com.l2kt.gameserver.geoengine.pathfinding

import com.l2kt.gameserver.geoengine.GeoEngine
import com.l2kt.gameserver.model.location.Location
import kotlin.math.*

/**
 * Path post-processing: smoothing via line-of-sight simplification
 * and Catmull-Rom spline interpolation for natural movement.
 *
 * Ported from BrProject PathFinder.smoothPath() + applyCurveInterpolation().
 */
object PathSmoother {

    private const val INTERPOLATION_STEP_DISTANCE = 64
    private const val MAX_HEIGHT_DIFF = 150
    private const val MAX_INTERPOLATION_STEPS = 10

    /**
     * Simplify a raw A* path by removing intermediate nodes
     * that have direct line-of-sight between them.
     */
    fun smooth(path: List<Location>): List<Location> {
        if (path.size < 3) return path

        val smoothed = mutableListOf<Location>()
        smoothed.add(path[0])

        var currentIndex = 0
        while (currentIndex < path.size - 1) {
            var farthestIndex = currentIndex + 1

            for (i in currentIndex + 2 until path.size) {
                val current = path[currentIndex]
                val target = path[i]
                if (abs(target.z - current.z) > MAX_HEIGHT_DIFF) break

                if (canMoveDirectly(current.x, current.y, current.z, target.x, target.y, target.z)) {
                    farthestIndex = i
                } else {
                    break
                }
            }

            smoothed.add(path[farthestIndex])
            currentIndex = farthestIndex
        }

        if (smoothed.last() != path.last()) {
            smoothed.add(path.last())
        }

        return smoothed
    }

    /**
     * Apply Catmull-Rom spline interpolation for smoother curves.
     */
    fun interpolate(path: List<Location>): List<Location> {
        if (path.size < 3) return path

        val interpolated = mutableListOf<Location>()
        interpolated.add(path[0])

        for (i in 0 until path.size - 1) {
            val p0 = if (i > 0) path[i - 1] else path[i]
            val p1 = path[i]
            val p2 = path[i + 1]
            val p3 = if (i + 2 < path.size) path[i + 2] else path[i + 1]

            val distance = distance3D(p1, p2)
            val steps = (distance / INTERPOLATION_STEP_DISTANCE).toInt()
                .coerceIn(2, MAX_INTERPOLATION_STEPS)

            for (j in 1 until steps) {
                val t = j.toDouble() / steps
                val point = catmullRom(p0, p1, p2, p3, t)
                if (canMoveDirectly(p1.x, p1.y, p1.z, point.x, point.y, point.z)) {
                    interpolated.add(point)
                }
            }

            interpolated.add(p2)
        }

        return interpolated
    }

    /**
     * Full pipeline: smooth then interpolate.
     */
    fun process(path: List<Location>): List<Location> {
        if (path.size < 2) return path
        val smoothed = smooth(path)
        return if (smoothed.size >= 3) interpolate(smoothed) else smoothed
    }

    private fun catmullRom(p0: Location, p1: Location, p2: Location, p3: Location, t: Double): Location {
        val t2 = t * t
        val t3 = t2 * t

        val x = 0.5 * ((2 * p1.x) + (-p0.x + p2.x) * t +
            (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 +
            (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3)

        val y = 0.5 * ((2 * p1.y) + (-p0.y + p2.y) * t +
            (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 +
            (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3)

        val z = p1.z + ((p2.z - p1.z) * t)

        return Location(x.toInt(), y.toInt(), z.toInt())
    }

    private fun canMoveDirectly(ox: Int, oy: Int, oz: Int, tx: Int, ty: Int, tz: Int): Boolean {
        return try {
            val gox = GeoEngine.getGeoX(ox)
            val goy = GeoEngine.getGeoY(oy)
            val gtx = GeoEngine.getGeoX(tx)
            val gty = GeoEngine.getGeoY(ty)
            if (!GeoEngine.hasGeoPos(gox, goy) || !GeoEngine.hasGeoPos(gtx, gty)) return true
            GeoEngine.canMoveToTarget(ox, oy, oz, tx, ty, tz)
        } catch (e: Exception) {
            true
        }
    }

    private fun distance3D(a: Location, b: Location): Double {
        val dx = (a.x - b.x).toDouble()
        val dy = (a.y - b.y).toDouble()
        val dz = (a.z - b.z).toDouble()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
