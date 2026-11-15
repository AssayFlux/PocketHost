package com.assayflux.pockethost.runtime

import android.content.Context

/** Explicit owner acknowledgement for optional runtimes. This is NOT an OS sandbox. */
object RuntimeTrustGate {
    private const val PREFS = "pockethost_state"
    private const val KEY = "trusted_runtime_code_ack"

    fun isAcknowledged(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun requireAcknowledged(context: Context) {
        check(isAcknowledged(context)) {
            "Optional runtimes are disabled until you acknowledge that only trusted application code may execute. This acknowledgement is not a sandbox."
        }
    }
}
