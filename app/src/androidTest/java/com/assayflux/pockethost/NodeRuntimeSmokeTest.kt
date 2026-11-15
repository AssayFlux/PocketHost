package com.assayflux.pockethost

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.engine.NativeEngine
import com.assayflux.pockethost.runtime.NodeHostRuntime
import com.assayflux.pockethost.runtime.NodeRuntimeStateStore
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.io.File

@RunWith(AndroidJUnit4::class)
class NodeRuntimeSmokeTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val project get() = File(context.filesDir, "node-project-instrumentation")

    @After
    fun cleanup() {
        runCatching { NativeEngine.stop() }
        runCatching { NodeHostRuntime.stop(context) }
        context.getSharedPreferences("pockethost_state", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("trusted_runtime_code_ack", false).commit()
        project.deleteRecursively()
    }

    @Test
    fun stateStoreUpdateSurvivesInterruptedWaitForCrossProcessLock() {
        val lockFile = File(context.noBackupFilesDir, "node-runtime-state.json.lock")
        lockFile.parentFile?.mkdirs()
        val failure = AtomicReference<Throwable?>()
        val started = CountDownLatch(1)

        RandomAccessFile(lockFile, "rw").use { owner ->
            owner.channel.lock().use {
                val writer = Thread {
                    started.countDown()
                    try {
                        NodeRuntimeStateStore.update(
                            context,
                            metrics = JSONObject().put("requests", 1)
                        )
                    } catch (error: Throwable) {
                        failure.set(error)
                    }
                }
                writer.start()
                assertTrue("State writer did not start", started.await(2, TimeUnit.SECONDS))
                Thread.sleep(100)
                writer.interrupt()
                writer.join(2_000)
                assertFalse("Interrupted state writer remained blocked", writer.isAlive)
                assertNull("State writer leaked an exception", failure.get())
            }
        }
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
                "http.createServer((req,res)=>{const body='node-smoke:'+process.version;" +
                "res.writeHead(200,{'Content-Length':Buffer.byteLength(body)});res.end(body);})" +
                ".listen(Number(process.env.PORT),'0.0.0.0');\n"
        )

        context.getSharedPreferences("pockethost_state", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("trusted_runtime_code_ack", true).commit()
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

        val body = java.net.Socket("127.0.0.1", 18080).use { socket ->
            socket.soTimeout = 10_000
            socket.getOutputStream().write(
                "GET / HTTP/1.1\r\nHost: node-smoke.local\r\nConnection: close\r\n\r\n"
                    .toByteArray(Charsets.ISO_8859_1)
            )
            socket.getOutputStream().flush()
            socket.getInputStream().bufferedReader().readText()
        }
        assertTrue("Unexpected Node response: $body", body.contains("node-smoke:v24."))

        assertEquals("OK", NativeEngine.stop())
        NodeHostRuntime.stop(context)
    }
}
