package io.sovware.niobium

/** Fixture launch arguments are available only in the debug APK. Release startup reads no extras. */
internal data class DebugLaunchOptions private constructor(
    val demo: Boolean = false,
    val bot: String? = null,
    val tab: String? = null,
    val room: String? = null,
    val connection: Connection? = null,
    val tools: Boolean = false,
    val demoAction: Boolean = false
) {
    override fun toString() = "DebugLaunchOptions(demo=$demo, explicitConnection=${connection != null})"
    companion object {
        fun read(debugBuild: Boolean, text: (String) -> String?, flag: (String) -> Boolean): DebugLaunchOptions {
            if (!debugBuild) return DebugLaunchOptions()
            val demo = flag("demo") || flag("NIOBIUM_DEMO")
            val gateway = text("gateway")
            val token = text("token")
            val connection = if (!demo && !gateway.isNullOrBlank() && !token.isNullOrBlank()) Connection("Live test",gateway,token) else null
            return DebugLaunchOptions(demo,text("bot"),text("tab"),text("room"),connection,flag("tools"),demo && flag("demoTool"))
        }
    }
}
