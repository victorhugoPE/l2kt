package com.l2kt.gameserver.geoengine.pathfinding

import com.l2kt.Config
import com.l2kt.gameserver.geoengine.GeoEngine
import com.l2kt.gameserver.geoengine.geodata.GeoStructure
import com.l2kt.gameserver.model.location.Location
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Modernized A* PathFinder using PriorityQueue instead of legacy linked-list NodeBuffer.
 *
 * Improvements over legacy NodeBuffer:
 * - PriorityQueue<PathNode> for O(log n) extraction vs O(n) linked-list traversal
 * - Int-based costs (50% memory vs Double)
 * - HashSet for closed set (O(1) lookup vs array bounds check)
 * - Stateless design (no pre-allocated buffers to lock)
 * - Diagonal movement with proper corner validation
 * - Configurable heuristic weight
 */
object AdvancedPathFinder {

    /**
     * Find path using A* with PriorityQueue.
     *
     * @param gox origin geo X
     * @param goy origin geo Y
     * @param goz origin geo Z
     * @param gtx target geo X
     * @param gty target geo Y
     * @param gtz target geo Z
     * @return list of world locations forming the path, empty if not found
     */
    fun findPath(gox: Int, goy: Int, goz: Short, gtx: Int, gty: Int, gtz: Short): List<Location> {
        val opened = PriorityQueue<PathNode>()
        val closed = HashSet<PathNode>()

        val startNswe = GeoEngine.getNsweNearest(gox, goy, goz.toInt())
        val startNode = PathNode(gox, goy, goz.toInt(), startNswe)
        startNode.setCost(null, 0, getCostH(gox, goy, goz.toInt(), gtx, gty, gtz.toInt()))
        opened.add(startNode)

        var count = 0
        while (opened.isNotEmpty() && count < Config.MAX_ITERATIONS) {
            val current = opened.poll()

            // Reached target?
            if (current.geoX == gtx && current.geoY == gty &&
                abs(current.z - gtz) < GeoStructure.CELL_HEIGHT * 2
            ) {
                return constructPath(current)
            }

            closed.add(current)
            expand(current, gtx, gty, gtz.toInt(), opened, closed)
            count++
        }

        return emptyList()
    }

    /**
     * Expand current node by adding valid neighbors to the open set.
     */
    private fun expand(
        current: PathNode,
        gtx: Int, gty: Int, gtz: Int,
        opened: PriorityQueue<PathNode>,
        closed: HashSet<PathNode>
    ) {
        val nswe = current.nswe
        if (nswe.toInt() == 0) return

        val x = current.geoX
        val y = current.geoY
        val z = current.z + GeoStructure.CELL_IGNORE_HEIGHT

        // Cardinal directions
        val nsweN = addDirectionalNode(current, x, y, z, nswe, 0, -1,
            GeoStructure.CELL_FLAG_N, Config.BASE_WEIGHT, gtx, gty, gtz, opened, closed)
        val nsweS = addDirectionalNode(current, x, y, z, nswe, 0, 1,
            GeoStructure.CELL_FLAG_S, Config.BASE_WEIGHT, gtx, gty, gtz, opened, closed)
        val nsweW = addDirectionalNode(current, x, y, z, nswe, -1, 0,
            GeoStructure.CELL_FLAG_W, Config.BASE_WEIGHT, gtx, gty, gtz, opened, closed)
        val nsweE = addDirectionalNode(current, x, y, z, nswe, 1, 0,
            GeoStructure.CELL_FLAG_E, Config.BASE_WEIGHT, gtx, gty, gtz, opened, closed)

        // Diagonal directions (requires both adjacent cardinal dirs to be passable)
        addCornerNode(current, x, y, z, nswe, -1, -1,
            GeoStructure.CELL_FLAG_W, GeoStructure.CELL_FLAG_N, nsweW, nsweN,
            Config.DIAGONAL_WEIGHT, gtx, gty, gtz, opened, closed)
        addCornerNode(current, x, y, z, nswe, 1, -1,
            GeoStructure.CELL_FLAG_E, GeoStructure.CELL_FLAG_N, nsweE, nsweN,
            Config.DIAGONAL_WEIGHT, gtx, gty, gtz, opened, closed)
        addCornerNode(current, x, y, z, nswe, -1, 1,
            GeoStructure.CELL_FLAG_W, GeoStructure.CELL_FLAG_S, nsweW, nsweS,
            Config.DIAGONAL_WEIGHT, gtx, gty, gtz, opened, closed)
        addCornerNode(current, x, y, z, nswe, 1, 1,
            GeoStructure.CELL_FLAG_E, GeoStructure.CELL_FLAG_S, nsweE, nsweS,
            Config.DIAGONAL_WEIGHT, gtx, gty, gtz, opened, closed)
    }

    private fun addDirectionalNode(
        parent: PathNode, x: Int, y: Int, z: Int,
        nswe: Byte, dx: Int, dy: Int, directionFlag: Byte, weight: Int,
        gtx: Int, gty: Int, gtz: Int,
        opened: PriorityQueue<PathNode>, closed: HashSet<PathNode>
    ): Byte {
        if ((nswe.toInt() and directionFlag.toInt()) != 0) {
            return addNode(parent, x + dx, y + dy, z, weight, gtx, gty, gtz, opened, closed)
        }
        return (0).toByte()
    }

    private fun addCornerNode(
        parent: PathNode, x: Int, y: Int, z: Int, nswe: Byte,
        dx: Int, dy: Int,
        dirFlagX: Byte, dirFlagY: Byte,
        nsweX: Byte, nsweY: Byte, weight: Int,
        gtx: Int, gty: Int, gtz: Int,
        opened: PriorityQueue<PathNode>, closed: HashSet<PathNode>
    ) {
        if ((nsweX.toInt() and dirFlagY.toInt()) != 0 && (nsweY.toInt() and dirFlagX.toInt()) != 0) {
            addNode(parent, x + dx, y + dy, z, weight, gtx, gty, gtz, opened, closed)
        }
    }

    private fun addNode(
        parent: PathNode, gx: Int, gy: Int, checkZ: Int, weight: Int,
        gtx: Int, gty: Int, gtz: Int,
        opened: PriorityQueue<PathNode>, closed: HashSet<PathNode>
    ): Byte {
        if (gx < 0 || gx >= GeoStructure.GEO_CELLS_X || gy < 0 || gy >= GeoStructure.GEO_CELLS_Y) {
            return (0).toByte()
        }

        val block = GeoEngine.getBlock(gx, gy)
        val index = block.getIndexBelow(gx, gy, checkZ)
        if (index < 0) return (0).toByte()

        val newZ = block.getHeight(index).toInt()
        val nswe = block.getNswe(index)

        val node = PathNode(gx, gy, newZ, nswe)
        if (closed.contains(node)) return nswe

        // Calculate weight: penalize partially-blocked cells
        val finalWeight = if (nswe == 0xFF.toByte()) {
            weight
        } else {
            weight * Config.OBSTACLE_MULTIPLIER
        }

        val hCost = getCostH(gx, gy, newZ, gtx, gty, gtz)
        val newCostG = parent.costG + finalWeight

        // Check if node is already in the open set with a better cost
        val existingNode = opened.find { it == node }
        if (existingNode != null) {
            if (newCostG < existingNode.costG) {
                opened.remove(existingNode)
                existingNode.setCost(parent, finalWeight, hCost)
                opened.add(existingNode)
            }
        } else {
            node.setCost(parent, finalWeight, hCost)
            opened.add(node)
        }

        return nswe
    }

    /**
     * Construct path from target node back to start by following parent pointers.
     * Returns world coordinates.
     */
    private fun constructPath(target: PathNode): List<Location> {
        val path = LinkedList<Location>()
        var dx = 0
        var dy = 0

        var node: PathNode? = target
        var parent = node?.parent

        while (parent != null) {
            val nx = parent.geoX - node!!.geoX
            val ny = parent.geoY - node.geoY

            if (dx != nx || dy != ny) {
                val worldX = GeoEngine.getWorldX(node.geoX)
                val worldY = GeoEngine.getWorldY(node.geoY)
                path.addFirst(Location(worldX, worldY, node.z))
                dx = nx
                dy = ny
            }

            node = parent
            parent = node.parent
        }

        return path
    }

    /**
     * Heuristic cost: Euclidean distance with configurable weight.
     */
    private fun getCostH(gx: Int, gy: Int, gz: Int, gtx: Int, gty: Int, gtz: Int): Int {
        val dx = abs(gx - gtx)
        val dy = abs(gy - gty)
        val dz = abs(gz - gtz) / GeoStructure.CELL_HEIGHT

        return (sqrt((dx * dx + dy * dy + dz * dz).toDouble()) * Config.HEURISTIC_WEIGHT).toInt()
    }
}
