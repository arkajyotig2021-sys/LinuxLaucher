package app.nova.desktop.runtime

import android.content.Context
import android.net.Uri

enum class RuntimeKind { WINDOWS, LINUX }
enum class RuntimeState { NOT_INSTALLED, READY, STARTING, RUNNING, FAILED, UNSUPPORTED }
data class RuntimeStatus(val kind: RuntimeKind, val state: RuntimeState, val detail: String)

/** Native runtime plug-in contract. A backend must be bundled and ABI-validated before it can run guests. */
interface GuestRuntime {
    val kind: RuntimeKind
    fun status(context: Context): RuntimeStatus
    fun install(context: Context): RuntimeStatus
    fun launch(context: Context, document: Uri): RuntimeStatus
}

class UnavailableGuestRuntime(override val kind: RuntimeKind) : GuestRuntime {
    override fun status(context: Context) = RuntimeStatus(kind, RuntimeState.NOT_INSTALLED,
        if (kind == RuntimeKind.WINDOWS)
            "Wine/Box64 backend is not bundled; Windows executables cannot run in this build."
        else "Linux userspace backend is not bundled; Linux desktop binaries cannot run in this build.")
    override fun install(context: Context) = status(context)
    override fun launch(context: Context, document: Uri) = status(context)
}
