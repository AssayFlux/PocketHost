package com.assayflux.pockethost

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.engine.NativeEngine
import com.assayflux.pockethost.runtime.NodeHostRuntime
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class NodeRuntimeSmokeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val project get() = File(context.filesDir, "node-project-instrumentation")

    @After
    fun cleanup() {
        runCatching { NativeEngine.stop() }
        runCatching { NodeHostRuntime.stop(context) }
        project.deleteRecursively()
    }

    @Test
    fun embeddedNodeStartsAndIsRoutedThroughRustLoopbackProxy() {
        instrumentation.runOnMainSync {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        check(project.mkdirs() || project.isDirectory)
        File(project, "package.json").writeText(
            """{"name":"pockethost-node-smoke","version":"1.0.0","main":"server.js"}"""
        )
        File(project, "server.js").writeText(
            "const http=require('node:http');\n" +
                "http.createServer((req,res)=>res.end('node-smoke:'+process.version))" +
                ".listen(Number(process.env.PORT),'0.0.0.0');\n"
        )

        assertEquals("STARTING", NodeHostRuntime.start(context, project.absolutePath, 3000))
        val deadline = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < deadline && NodeHostRuntime.status(context) != "RUNNING") {
            Thread.sleep(500)
        }
        assertEquals("RUNNING", NodeHostRuntime.status(context))

        val siteRoot = File(context.filesDir, "node-proxy-smoke").apply { mkdirs() }
        val routes = JSONObject().put("node-smoke.local",
            JSONObject().put("runtime", "node").put("port", 3000)).toString()
        assertEquals("OK", NativeEngine.startMultiSite(siteRoot.absolutePath, routes, 18080, false))

        val connection = URL("http://127.0.0.1:18080/").openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Host", "node-smoke.local")
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        assertTrue("Unexpected Node response: $body", body.contains("node-smoke:v24."))

        assertEquals("OK", NativeEngine.stop())
        NodeHostRuntime.stop(context)
    }
}
