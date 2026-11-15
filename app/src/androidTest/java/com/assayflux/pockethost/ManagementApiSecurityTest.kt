package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.management.ManagementApiServer
import com.assayflux.pockethost.engine.NativeEngine
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.Socket
import java.net.ServerSocket
import java.io.File

@RunWith(AndroidJUnit4::class)
class ManagementApiSecurityTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun startApi() {
        ManagementApiServer.stop()
        ManagementApiServer.start(context)
    }

    @After
    fun stopApi() {
        ManagementApiServer.stop()
    }

    @Test
    fun pairingRequiresDeviceApprovalAndSessionCanBeRevoked() {
        val challengeResponse = request("POST", "/api/v1/pair/challenge", "{}")
        assertEquals(201, challengeResponse.first)
        val challengeId = JSONObject(challengeResponse.second).getString("challengeId")
        val code = ManagementApiServer.pendingApprovalCode()
        assertNotNull(code)
        check(ManagementApiServer.approvePairing(code!!))

        val pairBody = JSONObject().put("challengeId", challengeId).put("code", code).toString()
        val pairResponse = request("POST", "/api/v1/pair/complete", pairBody)
        assertEquals(200, pairResponse.first)
        val token = JSONObject(pairResponse.second).getString("accessToken")

        assertEquals(200, request("GET", "/api/v1/status", authorization = token).first)
        assertEquals(204, request("DELETE", "/api/v1/session", authorization = token).first)
        assertEquals(401, request("GET", "/api/v1/status", authorization = token).first)
    }

    @Test
    fun rejectsUntrustedOriginsAndHostHeader() {
        val originResponse = request("OPTIONS", "/api/v1/status", origin = "https://attacker.example")
        assertEquals(403, originResponse.first)

        val hostResponse = request("GET", "/api/v1/health", host = "attacker.example")
        assertEquals(403, hostResponse.first)
    }

    @Test
    fun resourceLimitsCanChangeWhileStaticHostingRemainsOnline() {
        val root = File(context.cacheDir, "remote-config-" + System.nanoTime())
        assertEquals(true, root.mkdirs())
        File(root, "index.html").writeText("still-online")
        val port = ServerSocket(0).use { it.localPort }
        try {
            assertEquals("OK", NativeEngine.start(root.absolutePath, port, false))
            val challenge = JSONObject(request("POST", "/api/v1/pair/challenge", "{}").second)
            val challengeId = challenge.getString("challengeId")
            val code = ManagementApiServer.pendingApprovalCode()!!
            assertEquals(true, ManagementApiServer.approvePairing(code))
            val token = JSONObject(request("POST", "/api/v1/pair/complete",
                JSONObject().put("challengeId", challengeId).put("code", code).toString()).second).getString("accessToken")
            val body = JSONObject().put("pythonMaxWorkers", 6).put("nodeHeapMb", 160).toString()
            val response = request("POST", "/api/v1/resources", body, authorization = token)
            assertEquals(200, response.first)
            assertEquals("RUNNING", NativeEngine.status())
            assertEquals(true, JSONObject(response.second).getBoolean("staticHostingPreserved"))
        } finally {
            NativeEngine.stop()
            root.deleteRecursively()
        }
    }

    private fun request(
        method: String,
        path: String,
        body: String = "",
        authorization: String? = null,
        origin: String? = null,
        host: String = "127.0.0.1"
    ): Pair<Int, String> {
        Socket("127.0.0.1", 17841).use { socket ->
            socket.soTimeout = 5_000
            val bytes = body.toByteArray(Charsets.UTF_8)
            val headers = buildString {
                append("$method $path HTTP/1.1\r\n")
                append("Host: $host\r\n")
                append("Connection: close\r\n")
                if (authorization != null) append("Authorization: Bearer $authorization\r\n")
                if (origin != null) append("Origin: $origin\r\n")
                if (bytes.isNotEmpty()) {
                    append("Content-Type: application/json\r\n")
                    append("Content-Length: ${bytes.size}\r\n")
                }
                append("\r\n")
            }.toByteArray(Charsets.ISO_8859_1)
            socket.getOutputStream().apply {
                write(headers)
                write(bytes)
                flush()
            }
            val response = socket.getInputStream().bufferedReader(Charsets.UTF_8).readText()
            val status = response.lineSequence().first().split(" ").getOrNull(1)?.toInt()
                ?: error("Invalid API response: $response")
            val separator = response.indexOf("\r\n\r\n")
            val responseBody = if (separator >= 0) response.substring(separator + 4) else ""
            return status to responseBody
        }
    }
}
