package com.assayflux.pockethost.relay

/**
 * Lifecycle-only JNI bridge for the native visitor gateway.
 *
 * Credentials are supplied by the Android service from its protected store;
 * the Rust engine retains only their SHA-256 verifiers.
 */
object NativeRelay {
    init {
        System.loadLibrary("assayflux_relay_node")
    }

    @JvmStatic external fun start(configJson: String, databasePath: String): String
    @JvmStatic external fun createPairingInvite(deviceId: String): String
    @JvmStatic external fun stop(): String
    @JvmStatic external fun status(): String
}
