package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assayflux.pockethost.diagnostics.CapacityLab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapacityLabPercentileTest {
    @Test
    fun nearestRankPercentileDoesNotUnderReportSmallSampleTail() {
        assertEquals(10L, CapacityLab.nearestRankPercentile(listOf(1L, 10L), 95))
        assertEquals(10L, CapacityLab.nearestRankPercentile(listOf(1L, 10L), 99))
        assertEquals(2L, CapacityLab.nearestRankPercentile(listOf(1L, 2L, 3L, 4L), 50))
        assertEquals(4L, CapacityLab.nearestRankPercentile(listOf(1L, 2L, 3L, 4L), 95))
        assertEquals(0L, CapacityLab.nearestRankPercentile(emptyList(), 99))
        assertThrows(IllegalArgumentException::class.java) {
            CapacityLab.nearestRankPercentile(listOf(1L), 0)
        }
    }
}
