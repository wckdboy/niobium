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

class ChatDeliveryTest {
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
        val name=".delivery-fixture-${java.util.UUID.randomUUID()}.mjs";script=File(runtime.home,"workspace/$name")
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
    @Test fun doubleTapsSendOnceAndDraftAcknowledgementWaitsForAcceptance()=runBlocking {
        configure("delay",1500);val vm=vm();var confirmed=0
        instrumentation.runOnMainSync {vm.send("One message",onAccepted={confirmed++});vm.send("One message",onAccepted={confirmed++})}
        assertTrue(vm.state.sendingChat);assertEquals(0,confirmed)
        waitFor {!vm.state.sendingChat};assertEquals(1,confirmed)
        assertFalse(vm.state.deliveryUnconfirmed)
        val calls=controller.request("fixture.android").objects("requests");assertEquals(1,calls.count {it.s("method")=="prompt.submit"})
    }
    @Test fun aRejectedSendKeepsItsDraftAndRequiresReview()=runBlocking {
        configure("reject");val vm=vm();var confirmed=0
        val key=vm.chatDraftScope
        instrumentation.runOnMainSync {vm.retainDraft(key,"Keep this draft");vm.send("Keep this draft",onAccepted={confirmed++})}
        waitFor {!vm.state.sendingChat}
        assertEquals(0,confirmed);assertEquals("Keep this draft",vm.draftText(key))
        assertTrue(vm.state.deliveryUnconfirmed);assertFalse(vm.state.canSubmitChat)
        instrumentation.runOnMainSync {vm.refreshChat()};waitFor {!vm.state.loadingChat}
        assertEquals("fixture-session",vm.state.sessionId);assertEquals("Keep this draft",vm.draftText(key))
        assertTrue(vm.state.deliveryUnconfirmed)
    }
    @Test fun acceptingAnOlderSendPreservesANewerDraftInTheSameConversation()=runBlocking {
        configure("delay",1000);val vm=vm();val key=vm.chatDraftScope
        instrumentation.runOnMainSync {
            vm.retainDraft(key,"First draft");vm.send("First draft")
            vm.retainDraft(key,"Next thought")
        }
        waitFor {!vm.state.sendingChat}
        assertFalse(vm.state.deliveryUnconfirmed);assertEquals("Next thought",vm.draftText(key))
        instrumentation.runOnMainSync {vm.retainDraft(key,"Confirmed draft");vm.send("Confirmed draft")}
        waitFor {!vm.state.sendingChat};assertEquals("",vm.draftText(key))
    }
    @Test fun lostReceiptsStayLockedAcrossViewModelRestartAndNeverReplay()=runBlocking {
        configure("drop");val first=vm();var confirmed=0
        instrumentation.runOnMainSync {first.send("Possibly accepted",onAccepted={confirmed++})}
        waitFor {first.state.deliveryUnconfirmed&&!first.state.sendingChat};assertEquals(0,confirmed)
        configure("confirm");val restored=vm()
        assertNotEquals(first.chatContext,restored.chatContext)
        assertTrue(restored.state.deliveryUnconfirmed);assertFalse(restored.state.canSubmitChat)
        assertTrue(restored.state.messages.any {it.text=="Possibly accepted"})
        instrumentation.runOnMainSync {restored.send("Do not replay")}
        delay(100);assertEquals(1,controller.request("fixture.android").objects("requests").count {it.s("method")=="prompt.submit"})
        instrumentation.runOnMainSync {restored.acknowledgeUnconfirmedDelivery()};waitFor {restored.state.canSubmitChat}
        assertEquals(1,controller.request("fixture.android").objects("requests").count {it.s("method")=="prompt.submit"})
    }
    @Test fun leavingAChatDuringImageUploadNeverPostsItsPromptIntoTheNewChat()=runBlocking {
        configure("confirm",attach=1500);val vm=vm();var confirmed=0
        instrumentation.runOnMainSync {vm.send("Old photo task","AAAA","image/png",onAccepted={confirmed++})}
        waitFor {controller.request("fixture.android").objects("requests").isNotEmpty()}
        val other=BotProfile.parse(JSONObject().put("name","other").put("canonical_session",JSONObject().put("id","other-session")))!!
        instrumentation.runOnMainSync {vm.openBot(other)}
        waitFor {vm.state.activeBot?.name=="other"&&!vm.state.loadingChat};delay(1800)
        assertEquals(0,confirmed);assertEquals(0,controller.request("fixture.android").objects("requests").count {it.s("method")=="prompt.submit"})
    }
}
