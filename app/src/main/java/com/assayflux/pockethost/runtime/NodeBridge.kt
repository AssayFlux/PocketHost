package com.assayflux.pockethost.runtime

/** JNI entry point for the single Node.js instance isolated in the :node process. */
object NodeBridge {
    init {
        System.loadLibrary("node")
        System.loadLibrary("nodebridge")
    }

    /** Blocks until the Node event loop exits; call only from a dedicated background thread. */
    @JvmStatic external fun start(projectRoot: String, entryFile: String, cacheDirectory: String, port: Int, heapLimitMb: Int): Int
}
