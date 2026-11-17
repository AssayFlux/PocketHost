package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assayflux.pockethost.management.RelayAgent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RelayAgentHttpFramingTest {

    @Test
    fun sseLineReaderBoundsAllocationAndAcceptsNormalLineEndings() {
        val reader = java.io.StringReader("abc\\r\\nxy\\nlast")
        assertEquals("abc", RelayAgent.readBoundedSseLine(reader, 3))
        assertEquals("xy", RelayAgent.readBoundedSseLine(reader, 3))
        assertEquals("last", RelayAgent.readBoundedSseLine(reader, 4))
        assertEquals(null, RelayAgent.readBoundedSseLine(reader, 4))
    }

    @Test
    fun sseLineReaderRejectsOversizedLineBeforeReturningIt() {
        val reader = java.io.StringReader("abcd\\n")
        assertTrue(runCatching { RelayAgent.readBoundedSseLine(reader, 3) }.isFailure)
        assertTrue(runCatching { RelayAgent.readBoundedSseLine(java.io.StringReader("x"), 0) }.isFailure)
    }

    @Test
    fun acceptsOnlyUnambiguousBoundedResponseFraming() {
        val chunked = RelayAgent.validateResponseFraming("chunked", null)
        assertTrue(chunked.chunked)
        assertEquals(null, chunked.contentLength)

        val fixed = RelayAgent.validateResponseFraming(null, "8192")
        assertFalse(fixed.chunked)
        assertEquals(8192L, fixed.contentLength)

        val closeDelimited = RelayAgent.validateResponseFraming(null, null)
        assertFalse(closeDelimited.chunked)
        assertEquals(null, closeDelimited.contentLength)
    }

    @Test
    fun rejectsAmbiguousUnsupportedAndUnboundedResponseFraming() {
        assertTrue(runCatching { RelayAgent.validateResponseFraming("chunked", "12") }.isFailure)
        assertTrue(runCatching { RelayAgent.validateResponseFraming("gzip, chunked", null) }.isFailure)
        assertTrue(runCatching { RelayAgent.validateResponseFraming("identity", null) }.isFailure)
        assertTrue(runCatching { RelayAgent.validateResponseFraming(null, "not-a-number") }.isFailure)
        assertTrue(runCatching { RelayAgent.validateResponseFraming(null, "-1") }.isFailure)
        assertTrue(runCatching { RelayAgent.validateResponseFraming(null, "8388609") }.isFailure)
    }
}
