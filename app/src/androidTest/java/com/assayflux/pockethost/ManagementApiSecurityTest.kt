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
    fun pairingRateLimitWindowResetsAfterListenerRestart() {
        assertEquals(201, request("POST", "/api/v1/pair/challenge", "{}").first)
        ManagementApiServer.stop()
        ManagementApiServer.start(context)
        val second = request("POST", "/api/v1/pair/challenge", "{}")
        assertEquals(201, second.first)
        assertNotNull(JSONObject(second.second).optString("challengeId").takeIf { it.isNotBlank() })
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

    @Test
    fun operatorAccountLoginAndOwnerOnlyWritesAreEnforced() {
        val challenge = JSONObject(request("POST", "/api/v1/pair/challenge", "{}").second)
        val code = ManagementApiServer.pendingApprovalCode()!!
        assertEquals(true, ManagementApiServer.approvePairing(code))
        val bootstrap = JSONObject(request("POST", "/api/v1/pair/complete",
            JSONObject().put("challengeId", challenge.getString("challengeId")).put("code", code).toString()).second)
        val ownerToken = bootstrap.getString("accessToken")
        if (!com.assayflux.pockethost.management.HostAccountStore.hasOwner(context)) {
            val ownerSetup = request("POST", "/api/v1/auth/accounts",
                JSONObject().put("username", "hostowner").put("password", "PocketHost-Owner-Password-42").put("role", "OWNER").toString(),
                authorization = ownerToken)
            assertEquals(201, ownerSetup.first)
        }
        val username = "operator_" + System.nanoTime().toString().takeLast(8)
        val password = "PocketHost-Test-Password-42"
        val created = request("POST", "/api/v1/auth/accounts",
            JSONObject().put("username", username).put("password", password).put("role", "OPERATOR").toString(),
            authorization = ownerToken)
        assertEquals(201, created.first)
        val login = request("POST", "/api/v1/auth/login",
            JSONObject().put("username", username).put("password", password).toString())
        assertEquals(200, login.first)
        val operatorToken = JSONObject(login.second).getString("accessToken")
        assertEquals("OPERATOR", JSONObject(request("GET", "/api/v1/auth/me", authorization = operatorToken).second).getString("role"))
        assertEquals(200, request("GET", "/api/v1/status", authorization = operatorToken).first)
        assertEquals(403, request("POST", "/api/v1/resources",
            JSONObject().put("pythonMaxWorkers", 4).put("nodeHeapMb", 128).toString(), authorization = operatorToken).first)
        assertEquals(204, request("DELETE", "/api/v1/auth/accounts/" + username, authorization = ownerToken).first)
        assertEquals(401, request("GET", "/api/v1/status", authorization = operatorToken).first)
        assertEquals(401, request("POST", "/api/v1/auth/login",
            JSONObject().put("username", username).put("password", password).toString()).first)
    }

    @Test
    fun accountStoreClearsCallerPasswordAndVerifierBuffers() {
        val username = "secure_" + System.nanoTime().toString().takeLast(8)
        val password = "PocketHost-Secure-Test-42".toCharArray()
        val created = com.assayflux.pockethost.management.HostAccountStore.create(
            context, username, password, "OPERATOR"
        )
        assertEquals(true, password.all { it == '\u0000' })
        assertEquals("OPERATOR", created.role)

        val loginPassword = "PocketHost-Secure-Test-42".toCharArray()
        val account = com.assayflux.pockethost.management.HostAccountStore.verify(context, username, loginPassword)
        assertNotNull(account)
        assertEquals(true, loginPassword.all { it == '\u0000' })

        val missingPassword = "PocketHost-Secure-Test-42".toCharArray()
        assertEquals(null, com.assayflux.pockethost.management.HostAccountStore.verify(
            context, "not_a_real_user", missingPassword
        ))
        assertEquals(true, missingPassword.all { it == '\u0000' })
        assertEquals(true, com.assayflux.pockethost.management.HostAccountStore.revokeOperator(context, username))
    }

    @Test
    fun loginRejectsInvalidCredentials() {
        val response = request("POST", "/api/v1/auth/login",
            JSONObject().put("username", "missing-account").put("password", "not-a-valid-password").toString())
        assertEquals(401, response.first)
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
