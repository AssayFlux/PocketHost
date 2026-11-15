package com.assayflux.pockethost.engine

/** JNI carries lifecycle commands only; HTTP requests remain inside Rust. */
object NativeEngine {
    init { System.loadLibrary("pockethost") }
    @JvmStatic external fun start(documentRoot: String, port: Int, allowLan: Boolean): String
    @JvmStatic external fun startMultiSite(documentRoot: String, hostnameRootsJson: String, port: Int, allowLan: Boolean): String
    @JvmStatic external fun stop(): String
    @JvmStatic external fun status(): String
    @JvmStatic external fun requestCount(): Long
    @JvmStatic external fun bytesServed(): Long
    @JvmStatic external fun activeConnections(): Long
    @JvmStatic external fun queuedConnections(): Long
    @JvmStatic external fun setCapacityLimit(limit: Int): String
    @JvmStatic external fun rejectedConnections(): Long
}
