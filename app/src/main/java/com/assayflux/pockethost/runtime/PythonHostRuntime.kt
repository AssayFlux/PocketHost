package com.assayflux.pockethost.runtime

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

/**
 * Starts a user-provided WSGI application inside the app's embedded CPython 3.11 runtime.
 *
 * The runtime binds to loopback by default. LAN exposure must be an explicit user choice.
 * User project code executes with the Android app's process privileges: only run trusted code.
 */
object PythonHostRuntime {
    @Synchronized
    private fun python(context: Context): Python {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }
        return Python.getInstance()
    }

    @Synchronized
    fun start(context: Context, projectRoot: String, port: Int, allowLan: Boolean = false): String {
        require(port in 1024..65535) { "Python port must be between 1024 and 65535." }
        return python(context).getModule("pockethost_runtime")
            .callAttr("start", projectRoot, port, allowLan).toString()
    }

    @Synchronized
    fun stop(context: Context): String {
        if (!Python.isStarted()) return "STOPPED"
        return Python.getInstance().getModule("pockethost_runtime").callAttr("stop").toString()
    }

    @Synchronized
    fun status(context: Context): String {
        if (!Python.isStarted()) return "STOPPED"
        return Python.getInstance().getModule("pockethost_runtime").callAttr("status").toString()
    }
}
