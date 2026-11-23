package app.nova.desktop.runtime

data class RuntimePackage(
    val id: String,
    val version: String,
    val sha256: String,
    val source: String,
    val trustedHosts: Set<String>,
    val dependencies: List<String> = emptyList(),
    val architectures: Set<String> = setOf("arm64-v8a")
)

class RuntimeDependencyResolver(private val packages: Map<String, RuntimePackage>) {
    /** Returns dependencies before dependants; rejects missing, cyclic, unpinned or untrusted entries. */
    fun resolve(requested: String, abi: String): List<RuntimePackage> {
        val ordered = mutableListOf<RuntimePackage>()
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()

        fun visit(id: String) {
            if (id in visited) return
            require(id !in visiting) { "Runtime dependency cycle detected at $id." }
            val item = packages[id] ?: error("Missing runtime dependency: $id")
            require(item.architectures.contains(abi)) { "$id does not support $abi." }
            require(item.sha256.matches(Regex("^[a-fA-F0-9]{64}$"))) { "$id has no pinned SHA-256." }
            require(item.source.startsWith("https://")) { "$id must use HTTPS." }
            val host = java.net.URI(item.source).host ?: error("$id has an invalid source URL.")
            require(item.trustedHosts.any { it.equals(host, ignoreCase = true) }) { "$id source host is not trusted." }
            visiting += id
            item.dependencies.forEach(::visit)
            visiting -= id
            visited += id
            ordered += item
        }

        visit(requested)
        return ordered
    }
}
