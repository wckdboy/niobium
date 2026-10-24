package io.sovware.niobium

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.sovware.niobium.runtime.RuntimeInstaller
import io.sovware.niobium.runtime.RuntimeCommand
import io.sovware.niobium.runtime.LocalRuntimeProxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

@RunWith(AndroidJUnit4::class)
class LocalRuntimeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private class GuestOutput(process: Process) {
        private val tail = StringBuilder()
        init {
            Thread {
                runCatching { process.inputStream.use { input ->
                    val buffer = ByteArray(4096)
                    while (true) {
                        val size = input.read(buffer)
                        if (size < 0) break
                        synchronized(tail) {
                            tail.append(String(buffer, 0, size, Charsets.UTF_8))
                            if (tail.length > 16384) tail.delete(0, tail.length - 16384)
                        }
                    }
                } }
            }.apply { isDaemon = true; start() }
        }
        fun snapshot(): String = synchronized(tail) { tail.toString() }
    }

    @Test fun nativeProviderSetupActivatesModelRoutesAndKeepsKeysOutOfSettings() = kotlinx.coroutines.runBlocking {
        val runtime = RuntimeInstaller(InstrumentationRegistry.getInstrumentation().targetContext).prepare()
        val name = ".provider-test-" + java.util.UUID.randomUUID()
        val home = java.io.File(runtime.home, name).apply { mkdirs() }
        val guestHome = "/root/$name"
        val socket = java.io.File(runtime.root, "tmp/nb-providers.sock").apply { delete() }
        val proxy = LocalRuntimeProxy(socket.path)
        val process = RuntimeCommand.process(runtime, listOf("/usr/local/bin/dsh-serve"), mapOf(
            "DSH_PORT" to proxy.port.toString(), "NIOBIUM_SOCKET" to "/tmp/nb-providers.sock", "NIOBIUM_AGENT_TOKEN" to proxy.key,
            "DSH_HOME" to guestHome, "NIOBIUM_DSH_HOME" to guestHome,
            "NODE_OPTIONS" to "--require /usr/local/lib/niobium-local.cjs --require /usr/local/lib/niobium-agent.cjs")).start()
        Thread { process.inputStream.use { it.copyTo(java.io.OutputStream.nullOutputStream()) } }.apply { isDaemon = true; start() }
        val ready = kotlinx.coroutines.CompletableDeferred<Unit>()
        val client = GatewayClient(object : GatewayClient.Listener {
            override fun onReady(payload: org.json.JSONObject) { ready.complete(Unit) }
            override fun onNotification(type: String, sessionId: String, payload: org.json.JSONObject) {}
            override fun onServerRequest(id: Any, method: String, params: org.json.JSONObject) {}
            override fun onClosed(reason: String) { ready.completeExceptionally(IllegalStateException(reason)) }
        })
        try {
            kotlinx.coroutines.withTimeout(90_000) { while (process.isAlive && !LocalRuntimeProxy.ready(socket.path)) kotlinx.coroutines.delay(200) }
            assertTrue("DSH did not start", process.isAlive)
            client.connect(proxy.origin + "/niobium", proxy.key, proxy.key)
            kotlinx.coroutines.withTimeout(20_000) { ready.await() }
            val before = client.request("local.providers").getJSONObject("default").getString("provider")
            for ((ref, id) in listOf("DEEPSEEK_API_KEY" to "deepseek-official", "OPENAI_API_KEY" to "openai", "ANTHROPIC_API_KEY" to "anthropic")) {
                val key = "fixture-$id-private-only"
                val result = client.request("local.credential", org.json.JSONObject().put("ref", ref).put("value", key), 30_000)
                val provider = result.objects("providers").first { it.getString("id") == id }
                assertTrue(provider.getBoolean("active")); assertTrue(provider.getBoolean("key_configured"))
                assertTrue(provider.getJSONArray("models").length() > 0); assertFalse(result.toString().contains(key))
                assertEquals(before, result.getJSONObject("default").getString("provider"))
                assertFalse(java.io.File(home, "settings.yaml").readText().contains(key))
            }
            assertEquals(384, android.system.Os.stat(java.io.File(home, ".credentials.yaml").path).st_mode and 511)
        } finally {
            client.disconnect(true); proxy.close(); process.outputStream.close()
            if (!process.waitFor(15, TimeUnit.SECONDS)) { process.destroyForcibly(); process.waitFor(3, TimeUnit.SECONDS) }
            socket.delete()
            java.nio.file.Files.walk(home.toPath()).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { java.nio.file.Files.deleteIfExists(it) } }
        }
    }

    @Test fun dshWebIsPrivateAndAuthenticated() {
        val runtime = RuntimeInstaller(InstrumentationRegistry.getInstrumentation().targetContext).prepare()
        val socket = java.io.File(runtime.root, "tmp/nb-test.sock")
        socket.delete()
        val process = RuntimeCommand.process(runtime, listOf("/usr/local/bin/dsh-serve"), mapOf(
            "DSH_PORT" to "3080", "NIOBIUM_SOCKET" to "/tmp/nb-test.sock",
            "NODE_OPTIONS" to "--require /usr/local/lib/niobium-local.cjs")).start()
        val output = GuestOutput(process)
        var proxy: LocalRuntimeProxy? = null
        try {
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
            while (process.isAlive && System.nanoTime() < until && !LocalRuntimeProxy.ready(socket.path)) Thread.sleep(200)
            if (!process.isAlive) fail("DSH exited: " + output.snapshot())
            assertTrue("DSH web did not become ready: " + LocalRuntimeProxy.probe(socket.path) + "\n" + output.snapshot(), LocalRuntimeProxy.ready(socket.path))
            proxy = LocalRuntimeProxy(socket.path)
            val client = okhttp3.OkHttpClient()
            fun get(cookie: Boolean, origin: String? = null): okhttp3.Response {
                val request = okhttp3.Request.Builder().url(proxy!!.origin)
                if (cookie) request.header("Cookie", "niobium_session=" + proxy!!.key)
                if (origin != null) request.header("Origin", origin)
                return client.newCall(request.build()).execute()
            }
            get(false).use { assertEquals(403, it.code) }
            get(true, "https://evil.example").use { assertEquals(403, it.code) }
            get(true).use {
                assertEquals(200, it.code)
                assertTrue(it.header("Content-Security-Policy").orEmpty().contains("connect-src 'self'"))
                assertTrue(it.body.string().contains("<html", true))
            }
            val slow = (0 until 12).map { java.net.Socket("127.0.0.1", proxy.port) }
            val dripping = java.util.concurrent.atomic.AtomicBoolean(true)
            val dripper = Thread {
                while (dripping.get()) {
                    slow.forEach { runCatching { it.getOutputStream().write('G'.code); it.getOutputStream().flush() } }
                    Thread.sleep(200)
                }
            }.apply { start() }
            try {
                // Traffic continues frequently enough to defeat a per-read timeout.
                Thread.sleep(2600)
                get(true).use { assertEquals("Slow unauthenticated clients held every slot", 200, it.code) }
            } finally {
                dripping.set(false); slow.forEach { it.close() }; dripper.join(1000)
            }
            val connected = java.util.concurrent.CountDownLatch(1)
            var websocketFailure: Throwable? = null
            val websocket = client.newWebSocket(okhttp3.Request.Builder().url(proxy.origin + "/api/events.mux")
                .header("Cookie", "niobium_session=" + proxy.key).header("Origin", proxy.origin).build(),
                object : okhttp3.WebSocketListener() {
                    override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) { connected.countDown() }
                    override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                        websocketFailure = t; connected.countDown()
                    }
                })
            assertTrue("WebSocket upgrade timed out", connected.await(10, TimeUnit.SECONDS))
            assertNull("WebSocket failed: " + websocketFailure, websocketFailure)
            websocket.cancel()
            fun rpc(method: String, payload: org.json.JSONObject): org.json.JSONObject {
                val body = org.json.JSONObject().put("type", "client-request").put("rpcId", java.util.UUID.randomUUID().toString())
                    .put("method", method).put("payload", payload)
                val request = okhttp3.Request.Builder().url(proxy.origin + "/api/" + method)
                    .header("Cookie", "niobium_session=" + proxy.key)
                    .post(body.toString().toRequestBody("application/json".toMediaType())).build()
                return client.newCall(request).execute().use {
                    assertEquals(200, it.code)
                    val result = org.json.JSONObject(it.body.string()).getJSONObject("result")
                    assertTrue(result.toString(), result.getBoolean("ok")); result.getJSONObject("value")
                }
            }
            val session = rpc("session.create", org.json.JSONObject().put("cwd", "/root/workspace")).getString("sessionId")
            val skills = rpc("skill.list", org.json.JSONObject().put("sessionId", session)).getJSONArray("skills")
            assertTrue("The bundled bot-build skill was not discovered", (0 until skills.length()).any {
                skills.getJSONObject(it).getString("name") == "bot-build"
            })
            // The guest itself must not expose the unauthenticated stock TCP listener.
            assertTrue(runCatching { java.net.Socket("127.0.0.1", 3080).use { } }.isFailure)
        } finally {
            proxy?.close(); process.outputStream.close()
            assertTrue("Stop did not drain the guest tree", process.waitFor(15, TimeUnit.SECONDS))
            socket.delete()
        }
    }

    @Test fun reinstallPreservesPixelWorkspace() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val installer = RuntimeInstaller(context)
        val before = installer.prepare()
        val file = java.io.File(before.home, "workspace/.niobium-persistence-test")
        val skill = java.io.File(before.home, "workspace/.agents/skills/pixel-builder/SKILL.md")
        val originalSkill = skill.readText()
        skill.appendText("\n# User-customized instruction: preserve me.\n")
        file.writeText("keep this local Pixel")
        try {
            java.io.File(before.root, ".niobium-image").writeText("previous-image")
            val after = installer.prepare()
            assertEquals("keep this local Pixel", java.io.File(after.home, "workspace/.niobium-persistence-test").readText())
            assertTrue(java.io.File(after.home, "workspace/.agents/skills/pixel-builder/SKILL.md").isFile)
            assertEquals(originalSkill + "\n# User-customized instruction: preserve me.\n", skill.readText())
        } finally { file.delete(); skill.writeText(originalSkill) }
    }


    @Test fun foregroundServiceRunsBehindNativeAppAndAdvancedWebview() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val preferences = context.getSharedPreferences("niobium", android.content.Context.MODE_PRIVATE)
        val previous = preferences.getString("agent-provider", null)
        preferences.edit().putString("agent-provider", "dsh").commit()
        val activity = instrumentation.startActivitySync(android.content.Intent(context, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("tab", "local"))
        fun walk(node: android.view.accessibility.AccessibilityNodeInfo?): List<android.view.accessibility.AccessibilityNodeInfo> {
            if (node == null) return emptyList()
            return listOf(node) + (0 until node.childCount).flatMap { walk(node.getChild(it)) }
        }
        fun findWeb(view: android.view.View): android.webkit.WebView? {
            if (view is android.webkit.WebView) return view
            if (view is android.view.ViewGroup) for (index in 0 until view.childCount) findWeb(view.getChildAt(index))?.let { return it }
            return null
        }
        try {
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
            while (System.nanoTime() < until && io.sovware.niobium.runtime.LocalRuntime.state.value.phase !in listOf(
                io.sovware.niobium.runtime.RuntimePhase.RUNNING, io.sovware.niobium.runtime.RuntimePhase.ERROR)) Thread.sleep(200)
            val running = io.sovware.niobium.runtime.LocalRuntime.state.value
            assertEquals(running.message, io.sovware.niobium.runtime.RuntimePhase.RUNNING, running.phase)
            val origin = running.origin!!
            instrumentation.runOnMainSync { assertNull("DSH replaced the native app with a WebView", findWeb(activity.window.decorView)) }
            compose.onNodeWithTag("nav.home").performClick().assertIsSelected()
            val dashboardDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (System.nanoTime() < dashboardDeadline && walk(instrumentation.uiAutomation.rootInActiveWindow).none { it.text?.toString() == "Your Pixels" }) Thread.sleep(100)
            assertTrue("Native dashboard was not shown; screen=" + walk(instrumentation.uiAutomation.rootInActiveWindow).mapNotNull { it.text }.joinToString(" | "), walk(instrumentation.uiAutomation.rootInActiveWindow).any { it.text?.toString() == "Your Pixels" })
            compose.onNodeWithTag("nav.local").performClick().assertIsSelected()
            // Connection completion inserts the provider card above this control.
            // Wait for that layout before finding/clicking its accessibility node.
            val readyDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (System.nanoTime() < readyDeadline && walk(instrumentation.uiAutomation.rootInActiveWindow).none {
                it.text?.toString() == "Ready · chats and tasks run in Niobium"
            }) Thread.sleep(100)
            assertTrue("Local provider did not become ready", walk(instrumentation.uiAutomation.rootInActiveWindow).any {
                it.text?.toString() == "Ready · chats and tasks run in Niobium"
            })
            compose.onNodeWithText("Advanced controls").performScrollTo().performClick()
            compose.onNodeWithText("Advanced DSH workspace").performScrollTo().performClick()
            compose.onNodeWithText("Advanced DSH").assertIsDisplayed()
            var cssHeight = 0.0
            var webState = "No WebView"
            val renderDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (cssHeight < 300 && System.nanoTime() < renderDeadline) {
                compose.waitForIdle()
                val result = java.util.concurrent.CountDownLatch(1)
                instrumentation.runOnMainSync {
                    val web = findWeb(activity.window.decorView)
                    if (web == null) result.countDown()
                    else {
                        webState = "${web.width}×${web.height}; ${web.url}"
                        web.evaluateJavascript("(()=>{let e=document.createElement('div');e.style.height='100vh';document.body.append(e);let h=e.getBoundingClientRect().height;e.remove();return h})()") {
                            cssHeight = it.toDoubleOrNull() ?: 0.0; result.countDown()
                        }
                    }
                }
                result.await(2, TimeUnit.SECONDS)
                if (cssHeight < 300) Thread.sleep(200)
            }
            assertTrue("Advanced WebView CSS viewport collapsed: $cssHeight; $webState", cssHeight > 300)
            io.sovware.niobium.runtime.LocalRuntime.stop(context)
            val stopped = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (System.nanoTime() < stopped && io.sovware.niobium.runtime.LocalRuntime.state.value.phase != io.sovware.niobium.runtime.RuntimePhase.OFF) Thread.sleep(100)
            assertEquals(io.sovware.niobium.runtime.RuntimePhase.OFF, io.sovware.niobium.runtime.LocalRuntime.state.value.phase)
            assertTrue("Stopped local port is still reachable", runCatching { java.net.Socket("127.0.0.1", android.net.Uri.parse(origin).port).use { } }.isFailure)
            io.sovware.niobium.runtime.LocalRuntime.start(context)
            val restarted = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
            while (System.nanoTime() < restarted && io.sovware.niobium.runtime.LocalRuntime.state.value.phase !in listOf(
                io.sovware.niobium.runtime.RuntimePhase.RUNNING, io.sovware.niobium.runtime.RuntimePhase.ERROR)) Thread.sleep(100)
            assertEquals(io.sovware.niobium.runtime.RuntimePhase.RUNNING, io.sovware.niobium.runtime.LocalRuntime.state.value.phase)
        } finally {
            io.sovware.niobium.runtime.LocalRuntime.stop(context)
            instrumentation.runOnMainSync { activity.finish() }
            preferences.edit().putString("agent-provider", previous).commit()
        }
    }
    @Test fun guestShellNodeAndDshRunInAppSandbox() {
        val runtime = RuntimeInstaller(InstrumentationRegistry.getInstrumentation().targetContext).prepare()
        val process = RuntimeCommand.process(runtime, listOf("/bin/sh", "-c",
            "printf 'NIOBIUM_SHELL_OK\\n'; node --version; dsh --version")).start()
        val executor = Executors.newSingleThreadExecutor()
        try {
            val output = executor.submit<String> { process.inputStream.bufferedReader().readText() }
            val done = process.waitFor(60, TimeUnit.SECONDS)
            if (!done) process.outputStream.close()
            assertTrue("Guest startup timed out", done)
            val text = output.get(10, TimeUnit.SECONDS)
            assertEquals("Guest failed:\n" + text, 0, process.exitValue())
            assertTrue(text, text.contains("NIOBIUM_SHELL_OK"))
            assertTrue(text, text.contains("v22.") || text.contains("v24."))
            assertTrue(text, text.contains("0.1.0"))
        } finally {
            process.outputStream.close()
            process.destroy()
            executor.shutdownNow()
        }
    }
    @Test fun explicitGatewayLaunchOverridesSavedLocalWithoutLosingThePixel() = kotlinx.coroutines.runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val runtime = RuntimeInstaller(context).prepare()
        val name = ".launch-fixture-" + java.util.UUID.randomUUID() + ".mjs"
        val script = java.io.File(runtime.home,"workspace/$name")
        script.writeText(instrumentation.context.assets.open("gateway.mjs").bufferedReader().use { it.readText() })
        val process = RuntimeCommand.process(runtime,listOf("node","/root/workspace/$name")).start()
        val reader = process.inputStream.bufferedReader()
        val executor = Executors.newSingleThreadExecutor()
        val preferences = context.getSharedPreferences("niobium",android.content.Context.MODE_PRIVATE)
        val previousProvider = preferences.getString("agent-provider",null)
        val hadEnabled = preferences.contains("dsh-enabled")
        val wasEnabled = preferences.getBoolean("dsh-enabled",false)
        val secure = SecurePrefs(context)
        val previousConnection = secure.loadConnection()
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val line = executor.submit<String> { reader.readLine() }.get(45,TimeUnit.SECONDS)
            assertTrue(line,line.startsWith("PORT "))
            val origin = "http://127.0.0.1:${line.substringAfter("PORT ").toInt()}/proxy"
            var hub: NiobiumEngineHub? = null
            instrumentation.runOnMainSync {
                preferences.edit().putString("agent-provider","dsh").putBoolean("dsh-enabled",false).commit()
                hub = NiobiumEngineHub(context.applicationContext as android.app.Application)
                store.put("explicit-launch",hub!!)
                hub!!.bootstrap(bot = "remote-fixture",initialConnection = Connection("Launch fixture",origin,"fixture-token+&?"))
                assertSame(hub!!.hermes,hub!!.active)
            }
            val deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(45)
            while (System.nanoTime()<deadline && (hub!!.hermes.state.loadingChat || hub!!.hermes.state.sessionId==null) && hub!!.hermes.state.error==null) kotlinx.coroutines.delay(100)
            assertEquals(hub!!.hermes.state.error,"remote-fixture",hub!!.hermes.state.activeBot?.name)
            assertEquals("fixture-session",hub!!.hermes.state.sessionId)
            assertEquals(AgentEngine.HERMES,hub!!.active.state.engine)
            assertNull(hub!!.local.state.activeBot)
            assertFalse(hub!!.local.state.connected)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            if (previousConnection==null) secure.clear() else secure.saveConnection(previousConnection)
            preferences.edit().putString("agent-provider",previousProvider).apply {
                if (hadEnabled) putBoolean("dsh-enabled",wasEnabled) else remove("dsh-enabled")
            }.commit()
            process.outputStream.close();process.waitFor(20,TimeUnit.SECONDS)
            if (process.isAlive) process.destroyForcibly()
            reader.close();executor.shutdownNow();script.delete()
        }
    }

    @Test fun nativeDshProviderCreatesPixelsAndStreamsThroughRealDsh() = kotlinx.coroutines.runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val runtime = RuntimeInstaller(context).prepare()
        val socket = java.io.File(runtime.root, "tmp/nb-native-test.sock").apply { delete() }
        val proxy = LocalRuntimeProxy(socket.path)
        val mock = java.net.ServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1"))
        val received = java.util.concurrent.CountDownLatch(1)
        val profile = "native-" + java.util.UUID.randomUUID().toString().take(6)
        val note = "Native private note " + profile
        val sawMessaging = java.util.concurrent.atomic.AtomicBoolean(false)
        val responder = Thread {
            runCatching {
                while (!mock.isClosed) mock.accept().use { peer ->
                    val input = peer.getInputStream()
                    val headers = java.io.ByteArrayOutputStream()
                    while (headers.size() < 32768) {
                        val value = input.read(); if (value < 0) break
                        headers.write(value)
                        if (headers.size() >= 4 && headers.toString("UTF-8").endsWith("\r\n\r\n")) break
                    }
                    val header = headers.toString("UTF-8")
                    val first = header.lineSequence().first()
                    val size = header.lineSequence().firstOrNull { it.startsWith("Content-Length:", true) }
                        ?.substringAfter(":")?.trim()?.toInt() ?: 0
                    val body = input.readNBytes(size).toString(Charsets.UTF_8)
                    val payload = runCatching { org.json.JSONObject(body) }.getOrNull()
                    val toolsInRequest = payload?.optJSONArray("tools")
                    if (toolsInRequest != null && (0 until toolsInRequest.length()).any { toolsInRequest.optJSONObject(it)?.optJSONObject("function")?.optString("name") == "message_agent" }) sawMessaging.set(true)
                    val messages = payload?.optJSONArray("messages")
                    val hasToolReply = messages != null && (0 until messages.length()).any { messages.optJSONObject(it)?.optString("role") == "tool" }
                    val wantsDm = body.contains("Send a private note to Scout") && !hasToolReply
                    val wantsQuestion = body.contains("Niobium delivery [") && body.contains(note) && !hasToolReply
                    val reply = if (first.contains("/models")) """{"object":"list","data":[{"id":"deepseek-chat","object":"model"}]}"""
                    else {
                        received.countDown()
                        val delta = if (wantsDm || wantsQuestion) org.json.JSONObject().put("tool_calls", org.json.JSONArray().put(org.json.JSONObject()
                            .put("index",0).put("id","niobium-dm-call").put("type","function").put("function",org.json.JSONObject().put("name",if (wantsQuestion) "ask_user_question" else "message_agent")
                                .put("arguments",if (wantsQuestion) org.json.JSONObject().put("questions",org.json.JSONArray().put(org.json.JSONObject()
                                    .put("id","room-choice").put("question","Which accent should I use?")
                                    .put("options",org.json.JSONArray().put(org.json.JSONObject().put("label","Violet")).put(org.json.JSONObject().put("label","Mint"))))).toString()
                                    else org.json.JSONObject().put("recipient","default").put("text",note).toString()))))
                            else org.json.JSONObject().put("content", "NATIVE-DSH-REPLY")
                        val chunk = org.json.JSONObject().put("id", "niobium-fixture").put("object", "chat.completion.chunk")
                            .put("created", 1700000000).put("model", "deepseek-chat")
                            .put("choices", org.json.JSONArray().put(org.json.JSONObject().put("index", 0)
                                .put("delta", delta).put("finish_reason", if (wantsDm || wantsQuestion) "tool_calls" else "stop")))
                        "data: " + chunk.toString() + "\n\ndata: [DONE]\n\n"
                    }
                    val bytes = reply.toByteArray()
                    val type = if (first.contains("/models")) "application/json" else "text/event-stream"
                    peer.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: " + type +
                        "\r\nContent-Length: " + bytes.size + "\r\nConnection: close\r\n\r\n").toByteArray())
                    peer.getOutputStream().write(bytes); peer.getOutputStream().flush()
                }
            }
        }.apply { isDaemon = true; start() }
        val process = RuntimeCommand.process(runtime, listOf("/usr/local/bin/dsh-serve"), mapOf(
            "DSH_PORT" to proxy.port.toString(), "NIOBIUM_SOCKET" to "/tmp/nb-native-test.sock",
            "NIOBIUM_AGENT_TOKEN" to proxy.key, "DEEPSEEK_API_KEY" to "test-fixture",
            "DEEPSEEK_BASE_URL" to "http://127.0.0.1:" + mock.localPort,
            "NODE_OPTIONS" to "--require /usr/local/lib/niobium-local.cjs --require /usr/local/lib/niobium-agent.cjs")).start()
        Thread { process.inputStream.use { it.copyTo(java.io.OutputStream.nullOutputStream()) } }.apply { isDaemon = true; start() }

        var vm: NiobiumViewModel? = null
        val vmStore = androidx.lifecycle.ViewModelStore()
        val preferences = context.getSharedPreferences("niobium", android.content.Context.MODE_PRIVATE)
        val previousProvider = preferences.getString("agent-provider", null)
        val hadEnabled = preferences.contains("dsh-enabled")
        val wasEnabled = preferences.getBoolean("dsh-enabled",false)
        try {
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
            while (System.nanoTime() < until && process.isAlive && !LocalRuntimeProxy.ready(socket.path)) Thread.sleep(200)
            assertTrue("DSH was not ready", process.isAlive && LocalRuntimeProxy.ready(socket.path))
            GatewayClient.probe(proxy.origin + "/niobium", proxy.key, proxy.key)
            instrumentation.runOnMainSync {
                preferences.edit().putString("agent-provider","dsh").putBoolean("dsh-enabled",false).commit()
                val hub = NiobiumEngineHub(context.applicationContext as android.app.Application)
                vmStore.put("native-provider",hub)
                hub.bootstrap(bot = "default")
                vm = hub.local
                vm!!.connect(proxy.origin + "/niobium", proxy.key, "On device · DSH", false, true, proxy.key)
            }
            suspend fun waitFor(label: String, predicate: () -> Boolean) {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45)
                while (System.nanoTime() < deadline && !predicate() && vm?.state?.error == null) kotlinx.coroutines.delay(100)
                assertTrue(label + ": " + vm?.state?.error + "; turn=" + vm?.state?.turnActive + "; modelRequests=" + (1 - received.count), predicate())
            }
            waitFor("Native roster connected") { vm!!.state.connected && vm!!.state.bots.isNotEmpty() }
            waitFor("The launch target opens in the selected DSH workspace") { vm!!.state.activeBot?.name == "default" }
            instrumentation.runOnMainSync { vm!!.closeBot() }
            assertFalse(vm!!.state.needsConnection)
            assertEquals(AgentEngine.DSH, vm!!.state.engine)
            val tools = setOf("web", "file", "terminal", "todo", "skills", "delegation", "messaging")
            val original = PixelBuilder.fallback("Research")
            val fallback = original.copy(name = profile, toolsets = tools.toList(), permissions = original.permissions.copy(shell = true))
            val drafted = PixelDraft(fallback, false, tools, emptySet(), emptyList())
            instrumentation.runOnMainSync {
                vm!!.buildPixel(context, drafted, {}, { error("Native creation failed: " + it) })
            }
            waitFor("Native Pixel created and chat attached") { vm!!.state.activeBot?.name == profile && vm!!.state.canSubmitChat }
            assertTrue(java.io.File(runtime.home, ".dsh/.agent-presets/" + profile + "/agent.cordis.yml").isFile)
            instrumentation.runOnMainSync { vm!!.send("Say hello") }
            waitFor("Real DSH response rendered in native state") { vm!!.state.messages.any { it.role == "assistant" && it.text.contains("NATIVE-DSH-REPLY") } && !vm!!.state.turnActive }
            assertEquals(0L, received.count)
            val bot = vm!!.state.activeBot!!
            instrumentation.runOnMainSync { vm!!.closeBot(); vm!!.openBot(bot); vm!!.openBot(bot) }
            waitFor("Native chat history restored") { !vm!!.state.loadingChat && vm!!.state.messages.any { it.text.contains("NATIVE-DSH-REPLY") } }
            val soakTurns=soakArgument("niobium.dsh_turns",0,0,60)
            val pauseSeconds=soakArgument("niobium.dsh_pause_seconds",0,0,300)
            require(pauseSeconds==0L || soakTurns>0) { "Set niobium.dsh_turns when requesting a pause" }
            val trace=if (soakTurns>0) SoakTrace(context,"dsh") else null
            val ownedChat=vm!!.state.sessionId
            trace?.record("running",org.json.JSONObject().put("requested_turns",soakTurns).put("pause_seconds",pauseSeconds))
            repeat(soakTurns.toInt()) { index ->
                val before=vm!!.state.messages.count { it.role=="assistant" && it.text.contains("NATIVE-DSH-REPLY") }
                instrumentation.runOnMainSync {vm!!.send("Lifecycle check " + (index+1))}
                waitFor("Soak response " + (index+1)) { !vm!!.state.sendingChat && !vm!!.state.turnActive &&
                    vm!!.state.messages.count {it.role=="assistant" && it.text.contains("NATIVE-DSH-REPLY")} > before }
                instrumentation.runOnMainSync {vm!!.closeBot();vm!!.openBot(bot)}
                waitFor("Soak history " + (index+1)) { !vm!!.state.loadingChat && vm!!.state.sessionId==ownedChat &&
                    vm!!.state.messages.count {it.role=="assistant" && it.text.contains("NATIVE-DSH-REPLY")} > before }
                kotlinx.coroutines.delay(pauseSeconds*1000)
                assertTrue(vm!!.state.connected);assertEquals(ownedChat,vm!!.state.sessionId)
                trace?.record("running",org.json.JSONObject().put("completed_turns",index+1).put("owner_stable",true))
            }
            assertTrue("The actual DSH model request omitted the reviewed messaging tool", sawMessaging.get())
            instrumentation.runOnMainSync { vm!!.send("Send a private note to Scout") }
            val conversations = java.io.File(runtime.home,".dsh/niobium-conversations.json")
            waitFor("Real message_agent delivered an attributed DM") {
                runCatching {
                    val rooms = org.json.JSONObject(conversations.readText()).getJSONObject("rooms")
                    rooms.keys().asSequence().map { rooms.getJSONObject(it) }.any { room ->
                        val events = room.getJSONArray("events")
                        val rows = (0 until events.length()).map { events.getJSONObject(it) }
                        rows.any { it.optJSONObject("payload")?.optString("text") == note }
                    }
                }.getOrDefault(false)
            }
            instrumentation.runOnMainSync { vm!!.closeBot(); vm!!.refreshAllAsync() }
            waitFor("DM is visible in native rooms") { vm!!.state.groups.any { it.members.any { m -> m.profile == profile } } }
            val dm = vm!!.state.groups.first { it.members.any { m -> m.profile == profile } }
            instrumentation.runOnMainSync { vm!!.openGroup(dm) }
            waitFor("Native room transcript contains the actual DM") { vm!!.state.roomEvents.any { it.text == note } }
            waitFor("Android admits the room-owned question") { vm!!.state.clarify?.sessionId != null }
            assertTrue(vm!!.state.clarify!!.sessionId in org.json.JSONObject(conversations.readText()).getJSONObject("rooms").getJSONObject(dm.roomId)
                .getJSONObject("sessions").let { map -> map.keys().asSequence().map { map.getString(it) }.toSet() })
            instrumentation.runOnMainSync { vm!!.resolveClarify(mapOf("room-choice" to "Violet")) }
            waitFor("The actual DSH room continues after its native answer") { vm!!.state.roomEvents.any { it.actorId == "default" && it.text == "NATIVE-DSH-REPLY" } }
            val roomState = org.json.JSONObject(conversations.readText()).getJSONObject("rooms").getJSONObject(dm.roomId)
            val sessionMap = roomState.getJSONObject("sessions")
            val ownedSessions = sessionMap.keys().asSequence().map { sessionMap.getString(it) }.toSet()
            assertTrue(ownedSessions.isNotEmpty())
            val activity = instrumentation.startActivitySync(android.content.Intent(context, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("demo", true)) as MainActivity
            try {
                instrumentation.runOnMainSync { vm!!.closeGroup();activity.setContent { NiobiumTheme { NiobiumApp(vm!!,"pixels") } } }
                compose.onNodeWithTag("pixels.rooms").performClick()
                compose.onNodeWithText("Rooms").assertIsDisplayed()
                compose.onNodeWithText(dm.name).performClick()
                compose.waitUntil(5000) { compose.onAllNodesWithText(note).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("room.actions").performClick()
                compose.onNodeWithText("Clear room").performClick()
                compose.onNodeWithTag("room.clear.confirm").assertIsDisplayed()
                assertTrue(org.json.JSONObject(conversations.readText()).getJSONObject("rooms").has(dm.roomId))
                compose.onNodeWithTag("room.clear.cancel").performClick()
                compose.onNodeWithTag("room.clear.confirm").assertDoesNotExist()
                assertTrue(org.json.JSONObject(conversations.readText()).getJSONObject("rooms").has(dm.roomId))
                compose.onNodeWithTag("room.actions").performClick()
                compose.onNodeWithText("Clear room").performClick()
                compose.onNodeWithTag("room.clear.confirm").performClick()
            } finally { instrumentation.runOnMainSync { activity.finish() } }
            waitFor("Room is cleared from native navigation") { vm!!.state.groups.none { it.roomId == dm.roomId } }
            waitFor("Archived room sessions never become Scout's DM") {
                vm!!.state.bots.firstOrNull { it.name == "default" }?.canonicalSessionId !in ownedSessions
            }
            trace?.record("scenario_passed",org.json.JSONObject().put("completed_turns",soakTurns).put("owner_stable",true).put("rooms_verified",true))

        } finally {
            instrumentation.runOnMainSync { vm?.disconnect(); vmStore.clear() }
            preferences.edit().putString("agent-provider", previousProvider).apply {
                if (hadEnabled) putBoolean("dsh-enabled",wasEnabled) else remove("dsh-enabled")
            }.commit()
            proxy.close(); process.outputStream.close()
            if (!process.waitFor(20, TimeUnit.SECONDS)) { process.destroyForcibly(); check(process.waitFor(10,TimeUnit.SECONDS)) { "Test runtime did not stop" } }
            mock.close(); responder.join(1000); socket.delete()
            val preset=java.io.File(runtime.home, ".dsh/.agent-presets/" + profile).toPath()
            if (java.nio.file.Files.exists(preset,java.nio.file.LinkOption.NOFOLLOW_LINKS)) java.nio.file.Files.walk(preset).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach {java.nio.file.Files.deleteIfExists(it)}
            }
        }
    }

}
