package com.l2kt.gameserver.geoengine.pathfinding

import com.l2kt.gameserver.geoengine.geodata.GeoStructure

/**
 * Modernized pathfinding node using Int-based costs (50% less memory than Double).
 * Immutable coordinates, mutable costs for A* traversal.
 * Implements Comparable for PriorityQueue ordering.
 */
class PathNode(
    val geoX: Int,
    val geoY: Int,
    val z: Int,
    val nswe: Byte
) : Comparable<PathNode> {

    var costG: Int = 0
    var costH: Int = 0
    var costF: Int = 0
    var parent: PathNode? = null

    fun setCost(parentNode: PathNode?, weight: Int, hCost: Int) {
        costG = weight
        if (parentNode != null) {
            costG += parentNode.costG
        }
        costH = hCost
        costF = costG + costH
        parent = parentNode
    }

    fun clean() {
        costG = 0
        costH = 0
        costF = 0
        parent = null
    }

    override fun compareTo(other: PathNode): Int = this.costF - other.costF

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PathNode) return false
        return geoX == other.geoX && geoY == other.geoY && z == other.z
    }

    override fun hashCode(): Int {
        var result = geoX
        result = 31 * result + geoY
        result = 31 * result + z
        return result
    }
}
