package io.sovware.niobium

import org.json.JSONObject

object DemoData {
    private val now = System.currentTimeMillis() / 1000

    val bots = listOf(
        BotProfile("athena", "Athena", "Research, strategy and long-horizon thinking.", "nous", "Hermes 4 405B", 18,
            "demo-athena", "Three things need you today.", now - 38, true, false, false, "#A78BFA", false),
        BotProfile("forge", "Forge", "Builds, codes and ships the work.", "openrouter", "Claude Sonnet 4.6", 24,
            "demo-forge", "Clean build is green. Signing profile is the only open thread.", now - 140, true, false, false, "#F5C97A", false),
        BotProfile("scout", "Scout", "Browses the web, watches signals and reports back.", "nous", "Hermes 4 70B", 14,
            "demo-scout", "Two competitors shipped agent memory this week.", now - 760, false, false, false, "#5EEAD4", false),
        BotProfile("hermes", "Hermes", "Your primary agent. Everything, connected.", "nous", "Hermes 4 405B", 42,
            "demo-hermes", "The vault is unlocked and the workspace is ready.", now - 3600, false, true, false, "#C4B5FD", false)
    )

    val groups = listOf(
        GroupRoom("demo-launch", "Launch Room", listOf(
            GroupMember("athena", "athena", "athena", "Athena"),
            GroupMember("forge", "forge", "forge", "Forge"),
            GroupMember("scout", "scout", "scout", "Scout")
        ), now - 420, 5, "Scout: I’ll check the sources and flag anything that looks off."),
        GroupRoom("demo-nightshift", "Night Shift", listOf(
            GroupMember("hermes", "hermes", "hermes", "Hermes"),
            GroupMember("scout", "scout", "scout", "Scout")
        ), now - 7200, 2, "Hermes: Overnight sweep complete. Nothing on fire.")
    )

    val toolsets = listOf(
        Toolset("terminal", "Run commands, manage background processes and work on the host.", listOf("terminal", "process_manage"), true),
        Toolset("web", "Search, extract and read the live web.", listOf("web_search", "web_extract"), true),
        Toolset("browser", "Drive a real browser: navigate, click, type, inspect.", listOf("browser_navigate", "browser_click", "browser_snapshot", "browser_vision"), true),
        Toolset("coding", "Read, write and patch files across a workspace.", listOf("read_file", "write_file", "patch", "search_files"), true),
        Toolset("delegation", "Spawn isolated subagents for parallel work.", listOf("delegate_task"), true),
        Toolset("memory", "Persistent memory across sessions and pixels.", listOf("memory", "session_search"), true),
        Toolset("skills", "Create, inspect and improve reusable procedures.", listOf("skill_manage", "skill_view", "skills_list"), true),
        Toolset("cronjob", "Schedule recurring work and routines.", listOf("cronjob_manage"), true),
        Toolset("vision", "Understand images and screenshots.", listOf("vision_analyze"), true),
        Toolset("image_gen", "Generate images from a prompt.", listOf("image_generate"), false),
        Toolset("code_execution", "Run Python that calls tools programmatically.", listOf("execute_code"), true),
        Toolset("bot_room", "Verified Group Chat turn capabilities.", emptyList(), false),
        Toolset("connections", "Remote connectors and account authorization.", listOf("manage_connections"), true),
        Toolset("todo", "Plan and track work in the session.", listOf("todo_list"), true)
    )

    val activity = listOf(
        ActivityItem("a1", "tool", "release_check finished", "All 14 checks passed · 2m 08s", now - 260, "forge"),
        ActivityItem("a2", "message", "Athena replied", "Three things need you today.", now - 480, "athena"),
        ActivityItem("a3", "tool", "web_search finished", "Memory landscape · 8 sources", now - 900, "scout"),
        ActivityItem("a4", "bot", "Launch Room settled", "3 pixels · 2 rounds · 1 decision", now - 1500, "athena"),
        ActivityItem("a5", "approval", "Approval granted", "git push origin release/1.0", now - 3400, "forge")
    )

    fun messages(bot: BotProfile): List<ChatMessage> {
        val t = System.currentTimeMillis()
        return when (bot.name) {
            "athena" -> listOf(
                ChatMessage("d1", "user", "Morning. What actually needs me today?", t - 720_000),
                ChatMessage("d2", "tool", timestamp = t - 650_000, toolName = "session_search",
                    toolArgs = "{\n  \"query\": \"open threads since yesterday\"\n}",
                    toolResult = "Found 6 sessions across Athena, Forge and Scout. 3 open threads.",
                    toolStatus = ToolStatus.DONE, toolPreview = "6 sessions · 3 open threads", toolDuration = 0.4),
                ChatMessage("d3", "assistant", """
                    Three things need you today.

                    **1 · Build is green.** Forge finished the clean build and the release notes are ready for review. The only open thread is the signing profile for the TestFlight build.

                    **2 · Scout found a signal.** Two competitors shipped agent memory this week. I saved the teardown in *Research › Memory landscape* — the useful difference is their retrieval UI, not the storage layer.

                    **3 · One decision.** Athena recommends we ship Pixels before custom routines. I agree: the roster is the habit, routines are the power feature.

                    Say the word and I’ll open the release checklist.
                """.trimIndent(), t - 480_000,
                    reasoning = "Prioritising by consequence and time sensitivity. Signing is the only true blocker."),
                ChatMessage("d4", "user", "Open the release checklist. Keep it tight.", t - 300_000)
            )
            "forge" -> listOf(
                ChatMessage("f1", "user", "Clean build, then tell me what’s blocking TestFlight.", t - 900_000),
                ChatMessage("f2", "tool", timestamp = t - 840_000, toolName = "terminal",
                    toolArgs = "{\n  \"command\": \"./gradlew clean assembleRelease\"\n}",
                    toolResult = "BUILD SUCCESSFUL in 2m 08s\n42 actionable tasks",
                    toolStatus = ToolStatus.DONE, toolPreview = "BUILD SUCCESSFUL · 2m 08s", toolDuration = 128.2),
                ChatMessage("f3", "assistant", """
                    Build is green. **One blocker:** the iOS signing profile isn’t registered for the new bundle ID, so the archive validates but can’t be exported.

                    Two ways forward:
                    - Add `io.sovware.niobium` to the existing distribution profile.
                    - Or create a fresh profile with the two devices we actually test on.

                    I’d take the second — cleaner and easier to rotate later. Want me to open the checklist?
                """.trimIndent(), t - 620_000)
            )
            else -> listOf(
                ChatMessage("s1", "user", "Anything I should know from the overnight sweep?", t - 1_200_000),
                ChatMessage("s2", "assistant", """
                    Quiet night, one real signal.

                    Two competitors shipped agent memory this week. Both store more than us, but neither gives the user a clean way to see *why* something was remembered. That’s the opening.

                    I saved the teardown in **Research › Memory landscape**. Nothing else needs you.
                """.trimIndent(), t - 1_080_000)
            )
        }
    }

    fun events(room: GroupRoom): List<RoomEvent> {
        val t = System.currentTimeMillis() / 1000
        if (room.roomId == "demo-nightshift") return listOf(
            RoomEvent(1, room.roomId, "n1", "message.user", "user", "user", "You",
                "Overnight sweep please. Only wake me for a real fire.", t - 8000, null, null),
            RoomEvent(2, room.roomId, "n2", "message.member", "member", "hermes", "Hermes",
                "Overnight sweep complete. Nothing on fire. Two PRs are green and waiting for your review.", t - 7200, null, null)
        )
        return listOf(
            RoomEvent(1, room.roomId, "g1", "message.user", "user", "user", "You",
                "Final pass on the launch. Athena, make the call on scope. Forge, check the build. Scout, watch the competition.", t - 1800, null, null),
            RoomEvent(2, room.roomId, "g2", "message.member", "member", "athena", "Athena",
                "Scope call: ship Pixels first, routines second. The roster is the habit; routines are the power feature. I’ll write the decision record.", t - 1600, null, null),
            RoomEvent(3, room.roomId, "g3", "message.member", "member", "forge", "Forge",
                "Build is green. Signing profile is the only open thread. I can have TestFlight up today if we take the fresh profile.", t - 1450, null, null),
            RoomEvent(4, room.roomId, "g4", "message.member", "member", "scout", "Scout",
                "Competitor watch is clean. No launches in the last 24h. I’ll check the sources and flag anything that looks off.", t - 420, null, null),
            RoomEvent(5, room.roomId, "g5", "message.user", "user", "user", "You", "Good. Forge, ship it.", t - 360, null, null)
        )
    }

    fun toolsetsJson(): List<Toolset> = toolsets
}
