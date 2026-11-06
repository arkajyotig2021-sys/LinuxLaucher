package com.mindflux.linuxlauncher.data

data class Distro(
    val id: String,
    val name: String,
    val description: String,
    val family: String,
    val desktopHint: String,
    val architecture: String = "ARM64 availability varies",
    val status: DistroStatus = DistroStatus.CATALOG_ONLY
)

enum class DistroStatus {
    CATALOG_ONLY,
    RUNTIME_AVAILABLE,
    NOT_COMPATIBLE
}

object DistroCatalog {
    val all = listOf(
        Distro("ubuntu", "Ubuntu", "A widely used Linux distribution with a large software ecosystem.", "Debian-based", "XFCE / LXQt"),
        Distro("debian", "Debian", "A stable community distribution and foundation for many others.", "Debian-based", "XFCE / LXQt"),
        Distro("arch", "Arch Linux", "A flexible rolling-release distribution for experienced users.", "Independent", "XFCE / LXQt"),
        Distro("fedora", "Fedora", "A modern community distribution with up-to-date technologies.", "RPM-based", "XFCE / LXQt"),
        Distro("alpine", "Alpine Linux", "A compact distribution designed with simplicity and security in mind.", "Independent", "XFCE (availability varies)"),
        Distro("opensuse", "openSUSE", "A versatile distribution available in multiple release models.", "RPM-based", "XFCE / LXQt"),
        Distro("void", "Void Linux", "An independent rolling-release Linux distribution.", "Independent", "XFCE / LXQt"),
        Distro("kali", "Kali Linux", "A Debian-based distribution for authorised security education and testing.", "Debian-based", "XFCE"),
        Distro("gentoo", "Gentoo", "A source-based distribution with extensive configuration options.", "Independent", "XFCE / LXQt"),
        Distro("nixos", "NixOS", "A declarative Linux distribution with reproducible system configuration.", "Independent", "XFCE / LXQt")
    )

    fun search(query: String): List<Distro> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return all
        return all.filter {
            it.name.contains(normalized, ignoreCase = true) ||
                it.description.contains(normalized, ignoreCase = true) ||
                it.family.contains(normalized, ignoreCase = true)
        }
    }
}
