package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.assayflux.pockethost.relay.TlsCertificateLifecycle
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TlsCertificateLifecycleTest {
    @Test
    fun loopbackListenerDoesNotRequirePublicCertificateRenewal() {
        val config = JSONObject()
            .put("bind_address", "127.0.0.1")
            .put("routes", org.json.JSONArray())
            .toString()
        assertFalse(TlsCertificateLifecycle.needsRenewal(config))
    }

    @Test
    fun publicListenerWithMissingCertificateRequiresRenewal() {
        val config = JSONObject()
            .put("bind_address", "0.0.0.0")
            .put("routes", org.json.JSONArray().put(JSONObject().put("hostname", "example.test")))
            .toString()
        assertTrue(TlsCertificateLifecycle.needsRenewal(config))
    }

    @Test
    fun malformedCertificateConfigurationFailsClosed() {
        val config = JSONObject()
            .put("bind_address", "0.0.0.0")
            .put("tls_certificate_pem", "not a certificate")
            .put("tls_private_key_pem", "not a key")
            .put("routes", org.json.JSONArray().put(JSONObject().put("hostname", "example.test")))
            .toString()
        assertTrue(TlsCertificateLifecycle.needsRenewal(config))
    }
}
