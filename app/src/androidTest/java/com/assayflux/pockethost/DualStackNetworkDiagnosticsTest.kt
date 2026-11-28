package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assayflux.pockethost.dns.DetectedPublicAddresses
import com.assayflux.pockethost.dns.NetworkDiagnostics
import com.assayflux.pockethost.dns.PublicAddressDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress

@RunWith(AndroidJUnit4::class)
class DualStackNetworkDiagnosticsTest {
    @Test
    fun ipv4RejectsPrivateLoopbackAndCarrierGradeNat() {
        listOf("127.0.0.1", "10.0.0.1", "172.16.0.1", "192.168.1.1", "100.64.0.1", "169.254.1.1", "192.0.0.1", "192.88.99.1")
            .forEach { value ->
                assertFalse("$value must not be published", PublicAddressDetector.isGloballyRoutable(InetAddress.getByName(value), false))
            }
        assertTrue(PublicAddressDetector.isGloballyRoutable(InetAddress.getByName("1.1.1.1"), false))
    }

    @Test
    fun ipv6RejectsUlaLinkLocalAndDocumentationAddresses() {
        listOf("::1", "fe80::1", "fc00::1", "2001:db8::1", "2001::1", "2002::1", "3fff::1").forEach { value ->
            assertFalse("$value must not be published", PublicAddressDetector.isGloballyRoutable(InetAddress.getByName(value), true))
        }
        assertTrue(PublicAddressDetector.isGloballyRoutable(InetAddress.getByName("2606:4700:4700::1111"), true))
    }

    @Test
    fun cgnatRangeIsRecognizedWithoutOverclaimingOtherPrivateNat() {
        assertTrue(NetworkDiagnostics.isCarrierGradeNat("100.64.0.1"))
        assertTrue(NetworkDiagnostics.isCarrierGradeNat("100.127.255.254"))
        assertFalse(NetworkDiagnostics.isCarrierGradeNat("192.168.1.1"))
    }

    @Test
    fun missingAddressFamilyIsReportedIndependently() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val results = NetworkDiagnostics.assess(context, DetectedPublicAddresses(null, null))
        assertEquals(2, results.size)
        assertEquals("IPv4", results[0].family)
        assertEquals("IPv6", results[1].family)
        assertEquals("NOT VERIFIED", results[0].inboundReachability)
        assertEquals("NOT VERIFIED", results[1].inboundReachability)
    }
}
