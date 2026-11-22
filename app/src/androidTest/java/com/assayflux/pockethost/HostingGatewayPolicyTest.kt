package com.assayflux.pockethost

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.assayflux.pockethost.engine.HostingGateway
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostingGatewayPolicyTest {
    @Test
    fun publicOriginRequiresConsentAndPassedDiagnostics() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("pockethost_state", Context.MODE_PRIVATE)
        val oldConsent = preferences.getBoolean("public_origin_consent", false)
        val oldDiagnostics = preferences.getBoolean("public_origin_diagnostics_passed", false)
        try {
            preferences.edit()
                .putBoolean("public_origin_consent", false)
                .putBoolean("public_origin_diagnostics_passed", false)
                .commit()
            val noConsent = runCatching {
                HostingGateway.start(context, 18080, HostingGateway.BindMode.PUBLIC_ORIGIN)
            }.exceptionOrNull()
            assertTrue(noConsent?.message.orEmpty().contains("explicit consent"))

            preferences.edit()
                .putBoolean("public_origin_consent", true)
                .putBoolean("public_origin_diagnostics_passed", false)
                .commit()
            val noDiagnostics = runCatching {
                HostingGateway.start(context, 18080, HostingGateway.BindMode.PUBLIC_ORIGIN)
            }.exceptionOrNull()
            assertTrue(noDiagnostics?.message.orEmpty().contains("diagnostics"))
        } finally {
            preferences.edit()
                .putBoolean("public_origin_consent", oldConsent)
                .putBoolean("public_origin_diagnostics_passed", oldDiagnostics)
                .commit()
        }
    }
}
