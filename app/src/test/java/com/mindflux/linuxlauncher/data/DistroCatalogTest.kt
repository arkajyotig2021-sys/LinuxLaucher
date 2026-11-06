package com.mindflux.linuxlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DistroCatalogTest {
    @Test
    fun emptyQueryReturnsEntirePreloadedCatalogue() {
        assertEquals(DistroCatalog.all.size, DistroCatalog.search("").size)
    }

    @Test
    fun searchIsCaseInsensitive() {
        assertEquals("Ubuntu", DistroCatalog.search("UBUNTU").single().name)
    }

    @Test
    fun unknownQueryReturnsNoResults() {
        assertTrue(DistroCatalog.search("not-a-real-distro").isEmpty())
    }

    @Test
    fun catalogueContainsBroadLinuxFamiliesAndCustomImageEntry() {
        assertTrue(DistroCatalog.all.size >= 14)
        assertTrue(DistroCatalog.all.any { it.name == "Alpine Linux" })
        assertTrue(DistroCatalog.all.any { it.name == "Fedora" })
        assertTrue(DistroCatalog.all.any { it.name == "Arch Linux" })
        assertTrue(DistroCatalog.all.any { it.name == "Kali Linux" })
        assertTrue(DistroCatalog.all.any { it.id == "custom-linux" && it.imageRef.isBlank() })
    }

    @Test
    fun imageReferencesAreSearchable() {
        assertEquals("Ubuntu", DistroCatalog.search("ubuntu:24.04").single().name)
    }
}
