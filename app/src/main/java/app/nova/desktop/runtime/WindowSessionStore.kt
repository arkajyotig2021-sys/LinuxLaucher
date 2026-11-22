package app.nova.desktop.runtime

import java.util.UUID

enum class WindowKind { NOVA_BUILT_IN, ANDROID_EXTERNAL, WINDOWS_GUEST, LINUX_GUEST }
data class WindowSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val kind: WindowKind,
    val packageName: String? = null,
    val focused: Boolean = true
)

/** In-shell task/session model; it does not claim to re-parent Android Activities into NOVA. */
class WindowSessionStore {
    private val sessions = linkedMapOf<String, WindowSession>()

    fun open(title: String, kind: WindowKind, packageName: String? = null): WindowSession {
        sessions.replaceAll { _, value -> value.copy(focused = false) }
        val session = WindowSession(title = title, kind = kind, packageName = packageName)
        sessions[session.id] = session
        return session
    }

    fun focus(id: String): Boolean {
        if (id !in sessions) return false
        sessions.replaceAll { key, value -> value.copy(focused = key == id) }
        return true
    }

    fun close(id: String): Boolean = sessions.remove(id) != null
    fun all(): List<WindowSession> = sessions.values.toList()
}
