package com.mindflux.linuxlauncher.data

data class Distro(
    val id: String,
    val name: String,
    val description: String,
    val family: String,
    val desktopHint: String,
    val architecture: String = "ARM64 availability varies",
    val status: DistroStatus = DistroStatus.CATALOG_ONLY,
    val imageRef: String = ""
)

enum class DistroStatus(val label: String, val explanation: String) {
    RUNTIME_AVAILABLE("Configured runtime profile", "The rootfs installer and apt-based runtime path are configured for this distribution. Desktop and physical-device validation are still pending."),
    EXPERIMENTAL_OCI("Experimental OCI userspace", "The OCI root filesystem may extract, but package-manager behaviour, startup services and desktop compatibility are not validated."),
    CATALOG_ONLY("Catalogue entry only", "This is listed for discovery. A generic OCI image reference does not mean this distribution is installable or fully functional here."),
    NOT_COMPATIBLE("Not compatible", "This distribution is not currently supported by this device/runtime configuration.")
}

object DistroCatalog {
    val all = listOf(
        Distro("ubuntu", "Ubuntu", "A widely used Linux distribution with a large software ecosystem.", "Debian-based", "XFCE", status = DistroStatus.RUNTIME_AVAILABLE, imageRef = "ubuntu:24.04"),
        Distro("debian", "Debian", "A stable community distribution and foundation for many others.", "Debian-based", "XFCE", status = DistroStatus.RUNTIME_AVAILABLE, imageRef = "debian:bookworm"),
        Distro("alpine", "Alpine Linux", "A compact, security-focused distribution using musl and BusyBox.", "Independent", "XFCE (manual setup)", imageRef = "alpine:3.22"),
        Distro("fedora", "Fedora", "A modern community distribution with up-to-date technologies.", "RPM-based", "XFCE / LXQt", imageRef = "fedora:latest"),
        Distro("arch", "Arch Linux", "A flexible rolling-release distribution for experienced users.", "Independent", "XFCE / LXQt", imageRef = "archlinux:latest"),
        Distro("opensuse", "openSUSE Leap", "A stable RPM-based Linux distribution.", "RPM-based", "XFCE / LXQt", imageRef = "opensuse/leap:15.6"),
        Distro("kali", "Kali Linux", "A Debian-based distribution for authorised security education and testing.", "Debian-based", "Terminal (experimental)", status = DistroStatus.EXPERIMENTAL_OCI, imageRef = "kalilinux/kali-rolling"),
        Distro("rocky", "Rocky Linux", "An enterprise-focused community Linux distribution.", "RPM-based", "XFCE / MATE", imageRef = "rockylinux:9"),
        Distro("alma", "AlmaLinux", "An enterprise-compatible community Linux distribution.", "RPM-based", "XFCE / MATE", imageRef = "almalinux:9"),
        Distro("oracle", "Oracle Linux", "An enterprise Linux distribution with OCI container images.", "RPM-based", "XFCE / MATE", imageRef = "oraclelinux:9"),
        Distro("amazon", "Amazon Linux", "A Linux distribution designed for AWS workloads.", "RPM-based", "Terminal / lightweight desktop", imageRef = "amazonlinux:2023"),
        Distro("busybox", "BusyBox Linux", "A tiny userspace useful for lightweight command-line environments; not a full desktop distro.", "Minimal userspace", "Terminal only", imageRef = "busybox:latest"),
        Distro("gentoo", "Gentoo", "A source-based distribution with extensive configuration options.", "Independent", "XFCE / LXQt", imageRef = "gentoo/stage3:latest"),
        Distro("nixos", "NixOS / Nix", "A reproducible package-management environment; full NixOS service boot is not provided by PRoot.", "Independent", "Terminal / lightweight desktop", imageRef = "nixos/nix:latest"),
        Distro("custom-linux", "Custom Linux OCI image", "Use a public Docker Hub image for another Linux distribution or a custom root filesystem.", "Custom OCI", "Depends on image", imageRef = "")
    )

    fun search(query: String): List<Distro> {
        val normalized = query.trim()
        if (normalized.isEmpty()) return all
        return all.filter {
            it.name.contains(normalized, ignoreCase = true) ||
                it.description.contains(normalized, ignoreCase = true) ||
                it.family.contains(normalized, ignoreCase = true) ||
                it.imageRef.contains(normalized, ignoreCase = true)
        }
    }
}
