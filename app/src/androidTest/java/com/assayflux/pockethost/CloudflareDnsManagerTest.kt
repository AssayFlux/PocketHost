package com.assayflux.pockethost

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assayflux.pockethost.dns.CloudflareApiTokenStore
import com.assayflux.pockethost.dns.CloudflareDnsApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloudflareDnsManagerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun dnsApiTokenIsEncryptedAndIndependentFromTunnelToken() {
        CloudflareApiTokenStore.clear(context)
        val token = "dns-api-token-" + "a".repeat(48)
        CloudflareApiTokenStore.save(context, token)
        assertEquals(token, CloudflareApiTokenStore.read(context))
        assertTrue(CloudflareApiTokenStore.hasToken(context))
        val raw = context.getSharedPreferences("pockethost_secure", Context.MODE_PRIVATE)
            .getString("cloudflare_dns_api_token", "")
        assertFalse("Raw DNS API token must not be stored in preferences", raw.orEmpty().contains(token))
        assertFalse("DNS API token must not overwrite the named-tunnel token",
            context.getSharedPreferences("pockethost_secure", Context.MODE_PRIVATE)
                .getString("cloudflare_tunnel_token", "").orEmpty().contains(token))
        CloudflareApiTokenStore.clear(context)
        assertFalse(CloudflareApiTokenStore.hasToken(context))
        assertEquals(null, CloudflareApiTokenStore.read(context))
    }

    @Test
    fun recordValidationAcceptsSupportedRecordTypesAndZoneNames() {
        assertEquals("assayflux.org", CloudflareDnsApi.normalizeDomain(" AssayFlux.org. "))
        assertTrue(CloudflareDnsApi.supportedRecordTypes().containsAll(listOf("A", "AAAA")))
        CloudflareDnsApi.validate("A", "@", "192.0.2.10", "assayflux.org", "10")
        CloudflareDnsApi.validate("AAAA", "www.assayflux.org", "2001:db8::10", "assayflux.org", "10")
        CloudflareDnsApi.validate("CNAME", "www.assayflux.org", "assayflux.org", "assayflux.org", "10")
        CloudflareDnsApi.validate("TXT", "_pockethost.assayflux.org", "ownership-proof-value", "assayflux.org", "10")
        CloudflareDnsApi.validate("MX", "@", "mail.assayflux.org", "assayflux.org", "20")
    }

    @Test
    fun recordValidationRejectsInvalidAddressesAndForeignNames() {
        assertInvalid { CloudflareDnsApi.validate("A", "@", "999.2.3.4", "assayflux.org", "10") }
        assertInvalid { CloudflareDnsApi.validate("A", "@", "192.168.001.1", "assayflux.org", "10") }
        assertInvalid { CloudflareDnsApi.validate("AAAA", "@", "not-an-ipv6-address", "assayflux.org", "10") }
        assertInvalid { CloudflareDnsApi.validate("AAAA", "@", "fe80::1%wlan0", "assayflux.org", "10") }
        assertInvalid { CloudflareDnsApi.validate("CNAME", "www.other.test", "assayflux.org", "assayflux.org", "10") }
        assertInvalid { CloudflareDnsApi.validate("MX", "@", "mail.assayflux.org", "assayflux.org", "70000") }
    }

    @Test
    fun onlyWebAddressableRecordTypesCanBeProxied() {
        assertTrue(CloudflareDnsApi.proxyEligible("A"))
        assertTrue(CloudflareDnsApi.proxyEligible("AAAA"))
        assertTrue(CloudflareDnsApi.proxyEligible("CNAME"))
        assertFalse(CloudflareDnsApi.proxyEligible("TXT"))
        assertFalse(CloudflareDnsApi.proxyEligible("MX"))
    }

    private fun assertInvalid(block: () -> Unit) {
        val failure = runCatching(block).exceptionOrNull()
        assertTrue("Expected validation to reject the supplied record", failure is IllegalArgumentException)
    }
}
