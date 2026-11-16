package com.assayflux.pockethost.worker

/** JNI entry point for Node.js, loaded only inside the companion application's :node process. */
object NodeBridge {
    init {
        System.loadLibrary("node")
        System.loadLibrary("nodebridge")
    }

    /** Blocks until the Node event loop exits; invoke only from a dedicated background thread. */
    @JvmStatic external fun start(projectRoot: String, entryFile: String, cacheDirectory: String, port: Int, heapLimitMb: Int): Int
}
