package io.sovware.niobium

import androidx.test.platform.app.InstrumentationRegistry
import io.sovware.niobium.runtime.RuntimeCommand
import io.sovware.niobium.runtime.RuntimeInstaller
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class InventoryOwnershipTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private lateinit var process: Process
    private lateinit var script: File
    private lateinit var controller: GatewayClient
    private lateinit var origin: String
    private val store=androidx.lifecycle.ViewModelStore()
    private val executor=Executors.newSingleThreadExecutor()
    private val models=mutableListOf<NiobiumViewModel>()
    @Before fun start() = runBlocking {
        context.getSharedPreferences("niobium.chat.deliveries",0).edit().clear().commit()
        val runtime=RuntimeInstaller(context).prepare()
        val name=".inventory-fixture-${java.util.UUID.randomUUID()}.mjs";script=File(runtime.home,"workspace/$name")
        script.writeText(instrumentation.context.assets.open("gateway.mjs").bufferedReader().use {it.readText()})
        process=RuntimeCommand.process(runtime,listOf("node","/root/workspace/$name")).start()
        val line=executor.submit<String> {process.inputStream.bufferedReader().readLine()}.get(45,TimeUnit.SECONDS)
        check(line.startsWith("PORT "));origin="http://127.0.0.1:${line.substringAfter("PORT ")}/proxy"
        val ready=CompletableDeferred<Unit>()
        controller=GatewayClient(object:GatewayClient.Listener {
            override fun onReady(payload:JSONObject){ready.complete(Unit)}
            override fun onNotification(type:String,sessionId:String,payload:JSONObject){}
            override fun onServerRequest(id:Any,method:String,params:JSONObject){}
            override fun onClosed(reason:String){ready.completeExceptionally(IllegalStateException(reason))}
        })
        controller.connect(origin,"fixture-token+&?");withTimeout(15000){ready.await()};Unit
    }
    @After fun stop() {
        instrumentation.runOnMainSync { models.forEach {it.disconnect()};store.clear() }
        if(::controller.isInitialized)controller.disconnect(true)
        if(::process.isInitialized) {process.outputStream.close();if(!process.waitFor(10,TimeUnit.SECONDS))process.destroyForcibly()}
        if(::script.isInitialized)script.delete()
        context.getSharedPreferences("niobium.chat.deliveries",0).edit().clear().commit();executor.shutdownNow()
    }
    private suspend fun configure(mode:String,delay:Int=0,attach:Int=0)=controller.request("fixture.android",JSONObject().put("mode",mode).put("delay",delay).put("attach_delay",attach))
    private suspend fun vm():NiobiumViewModel {
        lateinit var vm:NiobiumViewModel
        instrumentation.runOnMainSync { vm=NiobiumViewModel(context.applicationContext as android.app.Application,AgentEngine.HERMES);models.add(vm);store.put("vm-${models.size}",vm);vm.connect(origin,"fixture-token+&?","Fixture",remember=false) }
        waitFor {vm.state.connected&&vm.state.bots.isNotEmpty()}
        instrumentation.runOnMainSync {vm.openBot(vm.state.bots.single())}
        waitFor {vm.state.sessionId!=null&&!vm.state.loadingChat};return vm
    }
    private suspend fun waitFor(predicate:suspend ()->Boolean)=withTimeout(20000){while(!predicate())delay(25)}
    private fun other() = BotProfile.parse(JSONObject().put("name","other").put("canonical_session",JSONObject().put("id","other-session")))!!
    private suspend fun inventory(replies: JSONObject) { controller.request("fixture.inventory", JSONObject().put("replies", replies)) }
    private fun reply(result: JSONObject, delay: Int = 0) = JSONObject().put("result",result).put("delay",delay)
    private fun rows(key: String, name: String) = JSONObject().put(key, org.json.JSONArray().put(JSONObject().put("name",name)))
    private fun skill(name: String) = JSONObject().put("skills",JSONObject().put("general",org.json.JSONArray().put(name)))
    @Test fun inventoriesCannotFollowTheirPreviousPixelIntoAnotherChat() = runBlocking {
        configure("confirm"); val vm=vm()
        inventory(JSONObject()
            .put("tools.list",org.json.JSONArray().put(reply(rows("toolsets","old"),700)).put(reply(rows("toolsets","new"))))
            .put("skills.manage",org.json.JSONArray().put(reply(skill("old"),700)).put(reply(skill("new"))))
            .put("mcp.servers.list",org.json.JSONArray().put(reply(rows("servers","old"),700)).put(reply(rows("servers","new"))))
            .put("mcp.servers.status",org.json.JSONArray())
            .put("cron.manage",org.json.JSONArray().put(reply(rows("jobs","old"),700)).put(reply(rows("jobs","new")))))
        instrumentation.runOnMainSync {vm.refreshAllAsync();vm.loadSkills();vm.loadMCP();vm.loadRoutines(vm.state.activeBot!!)}
        waitFor {controller.request("fixture.inventory").objects("requests").size==4}
        val other=other()
        instrumentation.runOnMainSync {vm.openBot(other)};waitFor {!vm.state.loadingChat}
        instrumentation.runOnMainSync {vm.loadSkills();vm.loadMCP();vm.loadRoutines(other)}
        waitFor {vm.state.skills.values.flatten().contains("new") && vm.state.mcpServers.any {it.name=="new"} && vm.state.routines.any {it.name=="new"}}
        delay(1000)
        assertEquals(listOf("new"),vm.state.toolsets.map {it.name})
        assertEquals(listOf("new"),vm.state.skills.values.flatten())
        assertEquals(listOf("new"),vm.state.mcpServers.map {it.name})
        assertEquals(listOf("new"),vm.state.routines.map {it.name})
        assertTrue(vm.state.routines.all {it.bot=="other"})
    }
    private fun loadAll(vm: NiobiumViewModel) { vm.refreshTools(); vm.loadSkills(); vm.loadMCP(); vm.loadRoutines(vm.state.activeBot!!) }
    private fun idle(vm: NiobiumViewModel) = !vm.state.toolsLoad.loading && !vm.state.skillsLoad.loading && !vm.state.mcpLoad.loading && !vm.state.routinesLoad.loading
    @Test fun failedNewestRefreshesKeepRowsAndPartialMcpStatusStaysVisible() = runBlocking {
        configure("confirm"); val vm=vm()
        inventory(JSONObject().put("tools.list",org.json.JSONArray().put(reply(rows("toolsets","kept"))))
            .put("skills.manage",org.json.JSONArray().put(reply(skill("kept"))))
            .put("mcp.servers.list",org.json.JSONArray().put(reply(rows("servers","kept"))))
            .put("mcp.servers.status",org.json.JSONArray().put(JSONObject().put("fail",true)))
            .put("cron.manage",org.json.JSONArray().put(reply(rows("jobs","kept")))))
        instrumentation.runOnMainSync {loadAll(vm)};waitFor {idle(vm)}
        assertNotNull(vm.state.mcpLoad.warning);assertNull(vm.state.mcpLoad.error)
        assertEquals("unknown",vm.state.mcpServers.single().status)
        val replies=JSONObject()
        for ((method,value) in listOf("tools.list" to rows("toolsets","late"), "skills.manage" to skill("late"), "mcp.servers.list" to rows("servers","late"), "cron.manage" to rows("jobs","late"))) {
            replies.put(method,org.json.JSONArray().put(reply(value,700)).put(JSONObject().put("fail",true)))
        }
        replies.put("mcp.servers.status",org.json.JSONArray());inventory(replies)
        instrumentation.runOnMainSync {loadAll(vm)}
        waitFor {controller.request("fixture.inventory").objects("requests").size==4}
        instrumentation.runOnMainSync {loadAll(vm)};waitFor {idle(vm)};delay(1000)
        assertNotNull(vm.state.toolsLoad.error);assertNotNull(vm.state.skillsLoad.error)
        assertNotNull(vm.state.mcpLoad.error);assertNotNull(vm.state.routinesLoad.error)
        assertEquals(listOf("kept"),vm.state.toolsets.map {it.name});assertEquals(listOf("kept"),vm.state.skills.values.flatten())
        assertEquals(listOf("kept"),vm.state.mcpServers.map {it.name});assertEquals(listOf("kept"),vm.state.routines.map {it.name})
        instrumentation.runOnMainSync {vm.disconnect()}
        assertTrue(vm.state.toolsets.isEmpty());assertTrue(vm.state.skills.isEmpty());assertTrue(vm.state.mcpServers.isEmpty());assertTrue(vm.state.routines.isEmpty())
    }
    @Test fun repeatedMutationsSendOnceAndTheirAcknowledgementsCannotChangeAnotherPixel() = runBlocking {
        configure("confirm");val vm=vm()
        val disabled=JSONObject().put("name","shell").put("enabled",false)
        val enabled=JSONObject().put("name","other-tools").put("enabled",true)
        inventory(JSONObject()
            .put("tools.list",org.json.JSONArray().put(reply(JSONObject().put("toolsets",org.json.JSONArray().put(disabled)))).put(reply(JSONObject().put("toolsets",org.json.JSONArray().put(enabled)))))
            .put("tools.configure",org.json.JSONArray().put(reply(JSONObject(),700)))
            .put("skills.manage",org.json.JSONArray().put(reply(skill("kept"))))
            .put("skills.reload",org.json.JSONArray().put(reply(JSONObject(),700)))
            .put("cron.manage",org.json.JSONArray().put(reply(rows("jobs","existing"))).put(reply(JSONObject(),700))))
        instrumentation.runOnMainSync {vm.refreshTools();vm.loadSkills();vm.loadRoutines(vm.state.activeBot!!)}
        waitFor {idle(vm)}
        val owner=vm.chatContext;val tool=vm.state.toolsets.single();val bot=vm.state.activeBot!!;var confirmed=0
        instrumentation.runOnMainSync {
            vm.toggleToolset(tool,owner);vm.toggleToolset(tool,owner)
            vm.reloadSkills(owner);vm.reloadSkills(owner)
            vm.createRoutine("Daily","Every day","Check",bot,owner) {confirmed++}
            vm.createRoutine("Daily","Every day","Check",bot,owner) {confirmed++}
        }
        waitFor {controller.request("fixture.inventory").objects("requests").size==6}
        val other=other()
        instrumentation.runOnMainSync {vm.openBot(other)};waitFor {!vm.state.loadingChat};delay(1000)
        assertEquals(0,confirmed);assertFalse(vm.state.toolsUpdating);assertFalse(vm.state.skillsUpdating);assertFalse(vm.state.routinesUpdating)
        assertTrue(vm.state.skills.isEmpty());assertTrue(vm.state.routines.isEmpty());assertNotEquals("Skills reloaded",vm.state.toast)
        assertEquals(listOf("other-tools"),vm.state.toolsets.map {it.name})
        instrumentation.runOnMainSync {
            vm.toggleToolset(tool,owner);vm.reloadSkills(owner)
            vm.createRoutine("Stale","Every day","No",other,owner) {confirmed++}
        }
        delay(100)
        val calls=controller.request("fixture.inventory").objects("requests")
        assertEquals(1,calls.count {it.s("method")=="tools.configure"});assertEquals(1,calls.count {it.s("method")=="skills.reload"})
        val adds=calls.filter {it.s("method")=="cron.manage" && it.o("params")?.s("action")=="add"}
        assertEquals(1,adds.size);assertEquals(bot.name,adds.single().o("params")?.s("profile"))
    }

}
