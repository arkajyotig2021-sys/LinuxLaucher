package com.mindflux.linuxlauncher.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection

class DownloadResumePolicyTest {
    @Test
    fun serverReturningFullBodyRestartsInsteadOfAppending() {
        assertFalse(DownloadResumePolicy.shouldAppend(4096L, HttpURLConnection.HTTP_OK, null))
    }

    @Test
    fun matchingPartialContentRangeAppendsAtExactOffset() {
        assertTrue(DownloadResumePolicy.shouldAppend(4096L, HttpURLConnection.HTTP_PARTIAL, "bytes 4096-8191/8192"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun mismatchedPartialContentRangeIsRejected() {
        DownloadResumePolicy.shouldAppend(4096L, HttpURLConnection.HTTP_PARTIAL, "bytes 0-4095/8192")
    }

    @Test(expected = IllegalStateException::class)
    fun missingContentRangeIsRejected() {
        DownloadResumePolicy.shouldAppend(4096L, HttpURLConnection.HTTP_PARTIAL, null)
    }
}
