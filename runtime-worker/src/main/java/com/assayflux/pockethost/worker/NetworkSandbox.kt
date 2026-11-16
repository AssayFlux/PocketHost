package com.assayflux.pockethost.worker

/** Native, irreversible network syscall policy for isolated execution cells. */
object NetworkSandbox {
    init { System.loadLibrary("pockethost_sandbox") }

    /**
     * Denies socket creation/connect/bind and permits accept only on listenerFd.
     * Must be called before loading or executing project code. A non-zero result
     * is fatal: callers must not continue with project startup.
     */
    /** Close inherited socket FDs before project code can use generic read/write syscalls. */
    @JvmStatic external fun closeInheritedSocketsExcept(listenerFd: Int): Int

    @JvmStatic external fun installNetworkPolicy(listenerFd: Int): Int
    @JvmStatic external fun duplicateToStandardInput(socketFd: Int): Int
    @JvmStatic external fun getResourceLimits(): LongArray
    @JvmStatic external fun getPeerUid(socketFd: Int): Int
}
