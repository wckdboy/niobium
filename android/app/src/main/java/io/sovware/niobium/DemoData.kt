package io.sovware.niobium

import org.json.JSONObject

object DemoData {
    private val now = System.currentTimeMillis() / 1000

    val bots = listOf(
        BotProfile("orion", "Orion", "Research & analysis", "demo", "Demo model", 6,
            "demo-orion", "Give me the question. I will bring the receipts.", now - 30, true, true, false, "#A78BFA", false),
        BotProfile("nova", "Nova", "Creative direction", "demo", "Demo model", 7,
            "demo-nova", "A little trouble, a better idea.", now - 360, false, false, false, "#F0ABFC", false),
        BotProfile("forge", "Forge", "Automation & releases", "demo", "Demo model", 8,
            "demo-forge", "Build first. Brag after the checks pass.", now - 420, false, false, false, "#F5C97A", false),
        BotProfile("sage", "Sage", "Knowledge & memory", "demo", "Demo model", 9,
            "demo-sage", "I kept the useful bits. And the strange ones.", now - 30, true, false, false, "#5EEAD4", false),
        BotProfile("nyx", "Nyx", "Security & anomalies", "demo", "Demo model", 10,
            "demo-nyx", "Quiet night. Suspiciously quiet.", now - 540, false, false, false, "#FB7185", false),
        BotProfile("astra", "Astra", "Vision & field work", "demo", "Demo model", 11,
            "demo-astra", "Eyes open. Instruments calibrated.", now - 600, false, false, false, "#7DD3FC", false),
        BotProfile("vesper", "Vesper", "Writing & long memory", "demo", "Demo model", 12,
            "demo-vesper", "The footnotes have secrets.", now - 660, false, false, false, "#C4B5FF", false),
        BotProfile("rook", "Rook", "Research & reconnaissance", "demo", "Demo model", 13,
            "demo-rook", "One more trail worth following.", now - 720, false, false, false, "#D6A45E", false)
    )

    val groups = listOf(
        GroupRoom("demo-launch", "Launch Room", listOf(
            GroupMember("orion", "orion", "orion", "Orion"),
            GroupMember("forge", "forge", "forge", "Forge"),
            GroupMember("rook", "rook", "rook", "Rook")
        ), now - 420, 5, "Rook: I’ll check the sources and flag anything that looks off."),
        GroupRoom("demo-nightshift", "Night Shift", listOf(
            GroupMember("vesper", "vesper", "vesper", "Vesper"),
            GroupMember("rook", "rook", "rook", "Rook")
        ), now - 7200, 2, "Hermes: Overnight sweep complete. Nothing on fire.")
    )

    val routines = listOf(
        Routine("morning-brief", "Morning brief", "Every day at 08:00", "Summarise overnight signals and the top three things I need to decide.", true, "Tomorrow, 08:00", "orion"),
        Routine("release-check", "Release check", "Every Friday at 16:00", "Run the release checklist and report anything that would block shipping.", true, "Friday, 16:00", "forge")
    )

    val toolsets = listOf(
        Toolset("terminal", "Run commands, manage background processes and work on the host.", listOf("terminal", "process_manage"), true),
        Toolset("web", "Search, extract and read the live web.", listOf("web_search", "web_extract"), true),
        Toolset("browser", "Drive a real browser: navigate, click, type, inspect.", listOf("browser_navigate", "browser_click", "browser_snapshot", "browser_vision"), true),
        Toolset("file", "Read, write and patch files across a workspace.", listOf("read_file", "write_file", "patch", "search_files"), true),
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
        ActivityItem("a2", "message", "Orion replied", "Three things need you today.", now - 480, "orion"),
        ActivityItem("a3", "tool", "web_search finished", "Memory landscape · 8 sources", now - 900, "rook"),
        ActivityItem("a4", "bot", "Launch Room settled", "3 pixels · 2 rounds · 1 decision", now - 1500, "orion"),
        ActivityItem("a5", "approval", "Approval granted", "git push origin release/1.0", now - 3400, "forge")
    )

    fun messages(bot: BotProfile): List<ChatMessage> {
        val t = System.currentTimeMillis()
        return when (bot.name) {
            "orion" -> listOf(
                ChatMessage("d1", "user", "Morning. What actually needs me today?", t - 720_000),
                ChatMessage("d2", "tool", timestamp = t - 650_000, toolName = "session_search",
                    toolArgs = "{\n  \"query\": \"open threads since yesterday\"\n}",
                    toolResult = "Found 6 sessions across Orion, Forge and Rook. 3 open threads.",
                    toolStatus = ToolStatus.DONE, toolPreview = "6 sessions · 3 open threads", toolDuration = 0.4),
                ChatMessage("d3", "assistant", """
                    Three things need you today.

                    **1 · Build is green.** Forge finished the clean build and the release notes are ready for review. The only open thread is the signing profile for the TestFlight build.

                    **2 · Rook found a signal.** Two competitors shipped agent memory this week. I saved the teardown in *Research › Memory landscape* — the useful difference is their retrieval UI, not the storage layer.

                    **3 · One decision.** Orion recommends we ship Pixels before custom routines. I agree: the roster is the habit, routines are the power feature.

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
            RoomEvent(2, room.roomId, "n2", "message.member", "member", "vesper", "Vesper",
                "Overnight sweep complete. Nothing on fire. Two PRs are green and waiting for your review.", t - 7200, null, null)
        )
        return listOf(
            RoomEvent(1, room.roomId, "g1", "message.user", "user", "user", "You",
                "Final pass on the launch. Orion, make the call on scope. Forge, check the build. Rook, watch the competition.", t - 1800, null, null),
            RoomEvent(2, room.roomId, "g2", "message.member", "member", "orion", "Orion",
                "Scope call: ship Pixels first, routines second. The roster is the habit; routines are the power feature. I’ll write the decision record.", t - 1600, null, null),
            RoomEvent(3, room.roomId, "g3", "message.member", "member", "forge", "Forge",
                "Build is green. Signing profile is the only open thread. I can have TestFlight up today if we take the fresh profile.", t - 1450, null, null),
            RoomEvent(4, room.roomId, "g4", "message.member", "member", "rook", "Rook",
                "Competitor watch is clean. No launches in the last 24h. I’ll check the sources and flag anything that looks off.", t - 420, null, null),
            RoomEvent(5, room.roomId, "g5", "message.user", "user", "user", "You", "Good. Forge, ship it.", t - 360, null, null)
        )
    }

    fun toolsetsJson(): List<Toolset> = toolsets
}
