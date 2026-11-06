package com.mindflux.linuxlauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DistroCatalogTest {
    @Test
    fun emptyQueryReturnsEntireCatalogue() {
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
}
