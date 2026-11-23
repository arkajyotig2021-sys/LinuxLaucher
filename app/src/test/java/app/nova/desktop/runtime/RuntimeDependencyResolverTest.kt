package app.nova.desktop.runtime

import org.junit.Assert.*
import org.junit.Test

class RuntimeDependencyResolverTest {
    private val host = setOf("packages.example.test")
    private fun item(id: String, deps: List<String> = emptyList()) = RuntimePackage(
        id, "1.0", "a".repeat(64), "https://packages.example.test/$id", host, deps
    )

    @Test fun dependenciesAreOrderedBeforeRequestedPackage() {
        val resolver = RuntimeDependencyResolver(mapOf("engine" to item("engine"), "guest" to item("guest", listOf("engine"))))
        assertEquals(listOf("engine", "guest"), resolver.resolve("guest", "arm64-v8a").map { it.id })
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingDependencyIsRejected() {
        RuntimeDependencyResolver(mapOf("guest" to item("guest", listOf("missing")))).resolve("guest", "arm64-v8a")
    }

    @Test(expected = IllegalArgumentException::class)
    fun dependencyCycleIsRejected() {
        RuntimeDependencyResolver(mapOf("a" to item("a", listOf("b")), "b" to item("b", listOf("a")))).resolve("a", "arm64-v8a")
    }

    @Test(expected = IllegalArgumentException::class)
    fun untrustedHostIsRejected() {
        RuntimeDependencyResolver(mapOf("guest" to RuntimePackage("guest", "1", "a".repeat(64), "https://evil.example/file", host))).resolve("guest", "arm64-v8a")
    }
}
