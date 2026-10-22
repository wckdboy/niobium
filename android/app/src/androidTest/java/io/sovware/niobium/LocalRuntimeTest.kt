package io.sovware.niobium

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.sovware.niobium.runtime.RuntimeInstaller
import io.sovware.niobium.runtime.RuntimeCommand
import io.sovware.niobium.runtime.LocalRuntimeProxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

@RunWith(AndroidJUnit4::class)
class LocalRuntimeTest {
    @Test fun dshWebIsPrivateAndAuthenticated() {
        val runtime = RuntimeInstaller(InstrumentationRegistry.getInstrumentation().targetContext).prepare()
        val socket = java.io.File(runtime.root, "tmp/nb-test.sock")
        socket.delete()
        val process = RuntimeCommand.process(runtime, listOf("/usr/local/bin/dsh-serve"), mapOf(
            "DSH_PORT" to "3080", "NIOBIUM_SOCKET" to "/tmp/nb-test.sock",
            "NODE_OPTIONS" to "--require /usr/local/lib/niobium-local.cjs")).start()
        val executor = Executors.newSingleThreadExecutor()
        val output = executor.submit<String> { process.inputStream.bufferedReader().readText() }
        var proxy: LocalRuntimeProxy? = null
        try {
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
            while (process.isAlive && System.nanoTime() < until && !LocalRuntimeProxy.ready(socket.path)) Thread.sleep(200)
            if (!process.isAlive) fail("DSH exited: " + output.get(10, TimeUnit.SECONDS))
            assertTrue("DSH web did not become ready: " + LocalRuntimeProxy.probe(socket.path), LocalRuntimeProxy.ready(socket.path))
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
            socket.delete(); executor.shutdownNow()
        }
    }

    @Test fun reinstallPreservesPixelWorkspace() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val installer = RuntimeInstaller(context)
        val before = installer.prepare()
        val file = java.io.File(before.home, "workspace/.niobium-persistence-test")
        file.writeText("keep this local Pixel")
        try {
            java.io.File(before.root, ".niobium-image").writeText("previous-image")
            val after = installer.prepare()
            assertEquals("keep this local Pixel", java.io.File(after.home, "workspace/.niobium-persistence-test").readText())
            assertTrue(java.io.File(after.home, "workspace/.agents/skills/pixel-builder/SKILL.md").isFile)
        } finally { file.delete() }
    }

    @Test fun foregroundServiceStartsAndStopsFromLocalScreen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(android.content.Intent(context, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("tab", "local"))
        try {
            instrumentation.waitForIdleSync()
            io.sovware.niobium.runtime.LocalRuntime.start(context)
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
            while (System.nanoTime() < until && io.sovware.niobium.runtime.LocalRuntime.state.value.phase !in listOf(
                io.sovware.niobium.runtime.RuntimePhase.RUNNING, io.sovware.niobium.runtime.RuntimePhase.ERROR)) Thread.sleep(200)
            val running = io.sovware.niobium.runtime.LocalRuntime.state.value
            assertEquals(running.message, io.sovware.niobium.runtime.RuntimePhase.RUNNING, running.phase)
            val origin = running.origin!!
            val openDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            var opened = false
            fun walk(node: android.view.accessibility.AccessibilityNodeInfo?): List<android.view.accessibility.AccessibilityNodeInfo> {
                if (node == null) return emptyList()
                return listOf(node) + (0 until node.childCount).flatMap { walk(node.getChild(it)) }
            }
            while (!opened && System.nanoTime() < openDeadline) {
                val nodes = walk(instrumentation.uiAutomation.rootInActiveWindow).filter { it.text?.contains("Open Pixel workspace") == true }
                for (node in nodes) {
                    var current: android.view.accessibility.AccessibilityNodeInfo? = node
                    repeat(5) {
                        if (current?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK) == true) opened = true
                        if (!opened) current = current?.parent
                    }
                }
                if (!opened) Thread.sleep(200)
            }
            assertTrue("Local workspace button was not accessible: " + walk(instrumentation.uiAutomation.rootInActiveWindow).mapNotNull { it.text }.joinToString(" | "), opened)
            fun findWeb(view: android.view.View): android.webkit.WebView? {
                if (view is android.webkit.WebView) return view
                if (view is android.view.ViewGroup) for (index in 0 until view.childCount) findWeb(view.getChildAt(index))?.let { return it }
                return null
            }
            var cssHeight = 0.0
            val renderDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (cssHeight < 300 && System.nanoTime() < renderDeadline) {
                val result = java.util.concurrent.CountDownLatch(1)
                instrumentation.runOnMainSync {
                    val web = findWeb(activity.window.decorView)
                    if (web == null) result.countDown()
                    else web.evaluateJavascript("(()=>{let e=document.createElement('div');e.style.height='100vh';document.body.append(e);let h=e.getBoundingClientRect().height;e.remove();return h})()") {
                        cssHeight = it.toDoubleOrNull() ?: 0.0; result.countDown()
                    }
                }
                result.await(2, TimeUnit.SECONDS)
                if (cssHeight < 300) Thread.sleep(200)
            }
            assertTrue("WebView CSS viewport collapsed to " + cssHeight, cssHeight > 300)
            io.sovware.niobium.runtime.LocalRuntime.stop(context)
            val stopped = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (System.nanoTime() < stopped && io.sovware.niobium.runtime.LocalRuntime.state.value.phase != io.sovware.niobium.runtime.RuntimePhase.OFF) Thread.sleep(100)
            assertEquals(io.sovware.niobium.runtime.RuntimePhase.OFF, io.sovware.niobium.runtime.LocalRuntime.state.value.phase)
            assertTrue("Stopped local port is still reachable", runCatching {
                java.net.Socket("127.0.0.1", android.net.Uri.parse(origin).port).use { }
            }.isFailure)
        } finally {
            io.sovware.niobium.runtime.LocalRuntime.stop(context)
            instrumentation.runOnMainSync { activity.finish() }
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
}
