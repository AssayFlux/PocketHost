package com.assayflux.pockethost

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.assayflux.pockethost.management.ManagementApiServer
import com.assayflux.pockethost.engine.NativeEngine
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.Socket
import java.net.ServerSocket
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
    fun sensitiveOwnerMutationsRequireAuthenticationWithinFiveMinutes() {
        assertFalse(ManagementApiServer.ownerReauthenticationRequired("OWNER", 1_000L, 1_300L))
        assertTrue(ownerReauthenticationRequired("OWNER", 1_000L, 1_301L))
        assertTrue(ownerReauthenticationRequired("OWNER", 1_301L, 1_300L))
        assertTrue(ownerReauthenticationRequired("OWNER", 0L, 1_300L))
        assertFalse(ownerReauthenticationRequired("OPERATOR", 0L, 1_300L))
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
    fun corsPreflightAllowsSiteAccessPolicyPut() {
        val response = rawRequest("OPTIONS", "/api/v1/sites/example/access", origin = "http://localhost:3000")
        assertTrue(response.startsWith("HTTP/1.1 204 No Content"))
        assertTrue(response.contains("Access-Control-Allow-Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS"))
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
    fun ownerCanConfigureSiteScopedPrivateAndRestrictedVisitorCredentials() {
        val challenge = JSONObject(request("POST", "/api/v1/pair/challenge", "{}").second)
        val code = ManagementApiServer.pendingApprovalCode()!!
        assertEquals(true, ManagementApiServer.approvePairing(code))
        val ownerToken = JSONObject(request("POST", "/api/v1/pair/complete",
            JSONObject().put("challengeId", challenge.getString("challengeId")).put("code", code).toString()).second)
            .getString("accessToken")

        val siteId = "visitor_" + System.nanoTime().toString().takeLast(10)
        val root = File(context.filesDir, siteId)
        assertEquals(true, root.mkdirs())
        File(root, "index.html").writeText("private-site")
        val hostname = "${siteId.replace("_", "-")}.example.test"
        try {
            val create = request("POST", "/api/v1/sites",
                JSONObject().put("id", siteId).put("name", "Visitor policy test")
                    .put("runtime", "static").put("root", root.absolutePath)
                    .put("hostnames", org.json.JSONArray().put(hostname)).put("enabled", true).toString(),
                authorization = ownerToken)
            assertEquals(201, create.first)

            assertEquals(200, request("PUT", "/api/v1/sites/$siteId/access",
                JSONObject().put("mode", "restricted").toString(), authorization = ownerToken).first)
            val invite = request("POST", "/api/v1/sites/$siteId/visitors",
                JSONObject().put("username", "alice").toString(), authorization = ownerToken)
            assertEquals(201, invite.first)
            val visitorToken = JSONObject(invite.second).getString("token")
            assertEquals(true, visitorToken.length >= 32)

            val siteList = JSONObject(request("GET", "/api/v1/sites", authorization = ownerToken).second)
                .getJSONArray("items")
            val site = (0 until siteList.length()).map { siteList.getJSONObject(it) }
                .first { it.getString("id") == siteId }
            assertEquals("restricted", site.getString("accessMode"))
            assertEquals("alice", site.getJSONArray("visitors").getString(0))

            val stored = com.assayflux.pockethost.sites.SiteRegistry.list(context).first { it.id == siteId }
            assertEquals(false, stored.visitorCredentials.values.contains(visitorToken))
            assertEquals(64, stored.visitorCredentials.getValue("alice").length)

            assertEquals(204, request("DELETE", "/api/v1/sites/$siteId/visitors/alice",
                authorization = ownerToken).first)
            assertEquals(0, com.assayflux.pockethost.sites.SiteRegistry.list(context)
                .first { it.id == siteId }.visitorCredentials.size)

            assertEquals(200, request("PUT", "/api/v1/sites/$siteId/access",
                JSONObject().put("mode", "public").toString(), authorization = ownerToken).first)
            val publicSite = com.assayflux.pockethost.sites.SiteRegistry.list(context).first { it.id == siteId }
            assertEquals("public", publicSite.accessMode)
            assertEquals(0, publicSite.visitorCredentials.size)
        } finally {
            runCatching { com.assayflux.pockethost.sites.SiteRegistry.remove(context, siteId) }
            root.deleteRecursively()
        }
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
    fun passwordChangeRotatesSessionsAndInvalidatesOldPassword() {
        val challenge = JSONObject(request("POST", "/api/v1/pair/challenge", "{}").second)
        val code = ManagementApiServer.pendingApprovalCode()!!
        assertEquals(true, ManagementApiServer.approvePairing(code))
        val ownerToken = JSONObject(request("POST", "/api/v1/pair/complete",
            JSONObject().put("challengeId", challenge.getString("challengeId")).put("code", code).toString()).second)
            .getString("accessToken")
        if (!com.assayflux.pockethost.management.HostAccountStore.hasOwner(context)) {
            assertEquals(201, request("POST", "/api/v1/auth/accounts",
                JSONObject().put("username", "hostowner").put("password", "PocketHost-Owner-Password-42").put("role", "OWNER").toString(),
                authorization = ownerToken).first)
        }
        val username = "passchange_" + System.nanoTime().toString().takeLast(8)
        val oldPassword = "PocketHost-Old-Password-42"
        val newPassword = "PocketHost-New-Password-84"
        assertEquals(201, request("POST", "/api/v1/auth/accounts",
            JSONObject().put("username", username).put("password", oldPassword).put("role", "OPERATOR").toString(),
            authorization = ownerToken).first)
        val firstSession = JSONObject(request("POST", "/api/v1/auth/login",
            JSONObject().put("username", username).put("password", oldPassword).toString()).second)
        val secondSession = JSONObject(request("POST", "/api/v1/auth/login",
            JSONObject().put("username", username).put("password", oldPassword).toString()).second)
        val firstToken = firstSession.getString("accessToken")
        val secondToken = secondSession.getString("accessToken")
        val changed = request("POST", "/api/v1/auth/password",
            JSONObject().put("currentPassword", oldPassword).put("newPassword", newPassword).toString(),
            authorization = firstToken)
        assertEquals(200, changed.first)
        val replacement = JSONObject(changed.second).getString("accessToken")
        assertEquals(401, request("GET", "/api/v1/status", authorization = firstToken).first)
        assertEquals(401, request("GET", "/api/v1/status", authorization = secondToken).first)
        assertEquals(200, request("GET", "/api/v1/status", authorization = replacement).first)
        assertEquals(401, request("POST", "/api/v1/auth/login",
            JSONObject().put("username", username).put("password", oldPassword).toString()).first)
        assertEquals(200, request("POST", "/api/v1/auth/login",
            JSONObject().put("username", username).put("password", newPassword).toString()).first)
        assertEquals(204, request("DELETE", "/api/v1/auth/accounts/" + username, authorization = ownerToken).first)
    }

    @Test
    fun ownerRecoveryRequiresFreshDeviceApprovedPairing() {
        fun approvedPairingToken(): String {
            val challenge = JSONObject(request("POST", "/api/v1/pair/challenge", "{}").second)
            val code = ManagementApiServer.pendingApprovalCode()!!
            assertEquals(true, ManagementApiServer.approvePairing(code))
            return JSONObject(request("POST", "/api/v1/pair/complete",
                JSONObject().put("challengeId", challenge.getString("challengeId")).put("code", code).toString()).second)
                .getString("accessToken")
        }

        val pairingToken = approvedPairingToken()
        if (!com.assayflux.pockethost.management.HostAccountStore.hasOwner(context)) {
            assertEquals(201, request("POST", "/api/v1/auth/accounts",
                JSONObject().put("username", "hostowner").put("password", "PocketHost-Owner-Password-42").put("role", "OWNER").toString(),
                authorization = pairingToken).first)
        }
        val ownerUsername = com.assayflux.pockethost.management.HostAccountStore.list(context)
            .first { it.role == "OWNER" }.username
        val recoveredPassword = "PocketHost-Recovered-Owner-84"
        val recovery = request("POST", "/api/v1/auth/recover-owner",
            JSONObject().put("newPassword", recoveredPassword).toString(), authorization = pairingToken)
        assertEquals(200, recovery.first)
        val recoveredToken = JSONObject(recovery.second).getString("accessToken")
        assertEquals(401, request("GET", "/api/v1/status", authorization = pairingToken).first)
        assertEquals(200, request("GET", "/api/v1/status", authorization = recoveredToken).first)

        val login = JSONObject(request("POST", "/api/v1/auth/login",
            JSONObject().put("username", ownerUsername).put("password", recoveredPassword).toString()).second)
        val normalOwnerToken = login.getString("accessToken")
        assertEquals(403, request("POST", "/api/v1/auth/recover-owner",
            JSONObject().put("newPassword", "PocketHost-Another-Password-99").toString(),
            authorization = normalOwnerToken).first)

        // Restore the emulator's conventional test credential so other instrumentation
        // cases remain independent regardless of test execution order. Pairing is rate-limited
        // to one challenge every three seconds.
        Thread.sleep(3_100)
        val resetPairing = approvedPairingToken()
        assertEquals(200, request("POST", "/api/v1/auth/recover-owner",
            JSONObject().put("newPassword", "PocketHost-Owner-Password-42").toString(),
            authorization = resetPairing).first)
    }

    @Test
    fun healthEndpointRemainsResponsiveDuringConcurrentPasswordVerification() {
        val username = "parallel_" + System.nanoTime().toString().takeLast(8)
        val password = "PocketHost-Parallel-Password-42"
        com.assayflux.pockethost.management.HostAccountStore.create(
            context, username, password.toCharArray(), "OPERATOR"
        )
        // One deliberately expensive PBKDF2 verification is enough to prove that
        // health traffic is independent. Running two KDFs simultaneously on the
        // API-26 two-core emulator starves the guest CPU and tests scheduler contention
        // rather than the listener's request isolation.
        val executor = Executors.newSingleThreadExecutor()
        val ready = CountDownLatch(1)
        try {
            val loginTask = executor.submit<Pair<Int, String>> {
                ready.countDown()
                request("POST", "/api/v1/auth/login",
                    JSONObject().put("username", username).put("password", password).toString())
            }
            assertEquals(true, ready.await(5, TimeUnit.SECONDS))

            // The management listener must continue serving lightweight health checks
            // while an expensive password verification runs on a different worker thread.
            val startedAt = System.nanoTime()
            val health = request("GET", "/api/v1/health")
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
            assertEquals(200, health.first)
            assertEquals(true, elapsedMs < 5_000)

            assertEquals(200, loginTask.get(120, TimeUnit.SECONDS).first)
        } finally {
            executor.shutdownNow()
            com.assayflux.pockethost.management.HostAccountStore.revokeOperator(context, username)
        }
    }

    @Test
    fun loginRejectsInvalidCredentials() {
        val response = request("POST", "/api/v1/auth/login",
            JSONObject().put("username", "missing-account").put("password", "not-a-valid-password").toString())
        assertEquals(401, response.first)
    }

    private fun rawRequest(method: String, path: String, origin: String): String {
        Socket("127.0.0.1", 17841).use { socket ->
            socket.soTimeout = 10_000
            val headers = "$method $path HTTP/1.1\r\nHost: 127.0.0.1\r\nOrigin: $origin\r\nConnection: close\r\n\r\n"
            socket.getOutputStream().write(headers.toByteArray(Charsets.ISO_8859_1))
            socket.getOutputStream().flush()
            return socket.getInputStream().bufferedReader(Charsets.UTF_8).readText()
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
            socket.soTimeout = 120_000
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
