package com.mindflux.linuxlauncher.runtime

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RuntimeCommandBuilderTest {
    private val temp = TemporaryFolder().apply { create() }

    @Test fun mapsSupportedAbisInDevicePriorityOrder() {
        assertEquals("arm64-v8a", RuntimeCommandBuilder.supportedAndroidAbi(listOf("arm64-v8a", "armeabi-v7a")))
        assertEquals("x86_64", RuntimeCommandBuilder.supportedAndroidAbi(listOf("mips", "x86_64")))
        assertNull(RuntimeCommandBuilder.supportedAndroidAbi(listOf("mips")))
    }

    @Test fun buildsRootfsPathInsidePrivateContainerDirectory() {
        val result = RuntimeCommandBuilder.rootfsDirectory(temp.root, "ubuntu")
        assertTrue(result.path.startsWith(File(temp.root, "linux-containers").canonicalPath))
        assertTrue(result.path.endsWith("ubuntu/rootfs"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTraversalInContainerId() {
        RuntimeCommandBuilder.rootfsDirectory(temp.root, "../outside")
    }

    @Test fun commandIncludesRootlessMountsAndLoginShell() {
        val binary = File(temp.root, "proot").apply { writeText("test"); setExecutable(true) }
        val rootfs = File(temp.root, "rootfs").apply { mkdirs(); File(this, "bin").mkdirs() }
        val command = RuntimeCommandBuilder.buildInteractiveShell(binary, rootfs)
        assertEquals(binary.absolutePath, command.first())
        assertTrue(command.contains("-0"))
        assertTrue(command.containsAll(listOf("/dev", "/proc", "/sys")))
        assertTrue(command.contains("/bin/sh"))
        assertEquals("-l", command.last())
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesToLaunchWithoutBundledEngine() {
        val rootfs = File(temp.root, "rootfs").apply { mkdirs(); File(this, "bin").mkdirs() }
        RuntimeCommandBuilder.buildInteractiveShell(File(temp.root, "missing-proot"), rootfs)
    }
}
