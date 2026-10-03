package be.heyman.android.jemmapassdemo.downloads

import java.util.PriorityQueue

data class PatchDescriptor(
    val fromVersion: String,
    val toVersion: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
)

object PatchChainResolver {

    private data class Cost(val hops: Int, val totalBytes: Long) : Comparable<Cost> {
        override fun compareTo(other: Cost): Int {
            val cmpHops = hops.compareTo(other.hops)
            if (cmpHops != 0) return cmpHops
            return totalBytes.compareTo(other.totalBytes)
        }
    }

    private data class SearchNode(
        val version: String,
        val cost: Cost,
        val path: List<PatchDescriptor>,
    )

    fun resolveChain(
        currentVersion: String,
        targetVersion: String,
        availablePatches: List<PatchDescriptor>,
    ): List<PatchDescriptor>? {
        if (currentVersion.isBlank() || targetVersion.isBlank()) {
            return null
        }
        if (currentVersion == targetVersion) {
            return emptyList()
        }

        // Deduplicate multiple patches between the same (from, to) versions by keeping the lightest
        val validPatches = availablePatches
            .filter { it.sizeBytes >= 0L && it.fromVersion.isNotBlank() && it.toVersion.isNotBlank() }
            .groupBy { it.fromVersion to it.toVersion }
            .values
            .mapNotNull { group -> group.minByOrNull { it.sizeBytes } }

        val graph = validPatches.groupBy { it.fromVersion }
        if (!graph.containsKey(currentVersion)) {
            return null
        }

        val bestCosts = mutableMapOf<String, Cost>()
        val queue = PriorityQueue<SearchNode>(compareBy { it.cost })

        val initialCost = Cost(0, 0L)
        bestCosts[currentVersion] = initialCost
        queue.add(SearchNode(currentVersion, initialCost, emptyList()))

        while (queue.isNotEmpty()) {
            val current = queue.poll()

            if (current.version == targetVersion) {
                return current.path
            }

            val recordedBest = bestCosts[current.version]
            if (recordedBest != null && current.cost > recordedBest) {
                continue
            }

            val outgoing = graph[current.version] ?: continue
            for (patch in outgoing) {
                val nextCost = Cost(current.cost.hops + 1, current.cost.totalBytes + patch.sizeBytes)
                val dest = patch.toVersion
                val currentBestToDest = bestCosts[dest]

                if (currentBestToDest == null || nextCost < currentBestToDest) {
                    bestCosts[dest] = nextCost
                    queue.add(SearchNode(dest, nextCost, current.path + patch))
                }
            }
        }

        return null
    }
}
