import Foundation

enum DemoData {
    static let bots: [BotProfile] = [
        BotProfile(name: "athena", title: "Athena", role: "Research, strategy and long-horizon thinking.",
                   provider: "nous", model: "Hermes 4 405B", skillCount: 18,
                   canonicalSessionID: "demo-athena", canonicalResolvedID: "demo-athena",
                   lastSessionID: "demo-athena", preview: "Three things need you today.",
                   activityAt: Date().addingTimeInterval(-38), workerActive: true,
                   isDefault: false, hidden: false, accentHex: "#A78BFA", hasAvatar: false),
        BotProfile(name: "forge", title: "Forge", role: "Builds, codes and ships the work.",
                   provider: "openrouter", model: "Claude Sonnet 4.6", skillCount: 24,
                   canonicalSessionID: "demo-forge", canonicalResolvedID: "demo-forge",
                   lastSessionID: "demo-forge", preview: "Clean build is green. Signing profile is the only open thread.",
                   activityAt: Date().addingTimeInterval(-140), workerActive: true,
                   isDefault: false, hidden: false, accentHex: "#F5C97A", hasAvatar: false),
        BotProfile(name: "scout", title: "Scout", role: "Browses the web, watches signals and reports back.",
                   provider: "nous", model: "Hermes 4 70B", skillCount: 14,
                   canonicalSessionID: "demo-scout", canonicalResolvedID: "demo-scout",
                   lastSessionID: "demo-scout", preview: "Two competitors shipped agent memory this week.",
                   activityAt: Date().addingTimeInterval(-760), workerActive: false,
                   isDefault: false, hidden: false, accentHex: "#5EEAD4", hasAvatar: false),
        BotProfile(name: "hermes", title: "Hermes", role: "Your primary agent. Everything, connected.",
                   provider: "nous", model: "Hermes 4 405B", skillCount: 42,
                   canonicalSessionID: "demo-hermes", canonicalResolvedID: "demo-hermes",
                   lastSessionID: "demo-hermes", preview: "The vault is unlocked and the workspace is ready.",
                   activityAt: Date().addingTimeInterval(-3600), workerActive: false,
                   isDefault: true, hidden: false, accentHex: "#C4B5FD", hasAvatar: false)
    ]

    static let groups: [GroupRoom] = [
        GroupRoom(roomID: "demo-launch", name: "Launch Room",
                  members: [
                    GroupMember(memberID: "athena", profile: "athena", handle: "athena", displayName: "Athena"),
                    GroupMember(memberID: "forge", profile: "forge", handle: "forge", displayName: "Forge"),
                    GroupMember(memberID: "scout", profile: "scout", handle: "scout", displayName: "Scout")
                  ],
                  updatedAt: Date().addingTimeInterval(-420), latestSeq: 5, disbanded: false,
                  preview: "Scout: I’ll check the sources and flag anything that looks off."),
        GroupRoom(roomID: "demo-nightshift", name: "Night Shift",
                  members: [
                    GroupMember(memberID: "hermes", profile: "hermes", handle: "hermes", displayName: "Hermes"),
                    GroupMember(memberID: "scout", profile: "scout", handle: "scout", displayName: "Scout")
                  ],
                  updatedAt: Date().addingTimeInterval(-7200), latestSeq: 2, disbanded: false,
                  preview: "Hermes: Overnight sweep complete. Nothing on fire.")
    ]

    static let toolsets: [Toolset] = [
        Toolset(name: "terminal", description: "Run commands, manage background processes and work on the host.", tools: ["terminal", "process_manage"], enabled: true),
        Toolset(name: "web", description: "Search, extract and read the live web.", tools: ["web_search", "web_extract"], enabled: true),
        Toolset(name: "browser", description: "Drive a real browser: navigate, click, type, inspect.", tools: ["browser_navigate", "browser_click", "browser_snapshot", "browser_vision"], enabled: true),
        Toolset(name: "coding", description: "Read, write and patch files across a workspace.", tools: ["read_file", "write_file", "patch", "search_files"], enabled: true),
        Toolset(name: "delegation", description: "Spawn isolated subagents for parallel work.", tools: ["delegate_task"], enabled: true),
        Toolset(name: "memory", description: "Persistent memory across sessions and pixels.", tools: ["memory", "session_search"], enabled: true),
        Toolset(name: "skills", description: "Create, inspect and improve reusable procedures.", tools: ["skill_manage", "skill_view", "skills_list"], enabled: true),
        Toolset(name: "cronjob", description: "Schedule recurring work and routines.", tools: ["cronjob_manage"], enabled: true),
        Toolset(name: "vision", description: "Understand images and screenshots.", tools: ["vision_analyze"], enabled: true),
        Toolset(name: "image_gen", description: "Generate images from a prompt.", tools: ["image_generate"], enabled: false),
        Toolset(name: "code_execution", description: "Run Python that calls tools programmatically.", tools: ["execute_code"], enabled: true),
        Toolset(name: "bot_room", description: "Verified Group Chat turn capabilities.", tools: [], enabled: false),
        Toolset(name: "connections", description: "Remote connectors and account authorization.", tools: ["manage_connections"], enabled: true),
        Toolset(name: "todo", description: "Plan and track work in the session.", tools: ["todo_list"], enabled: true)
    ]

    static let routines: [Routine] = [
        Routine(id: "morning-brief", name: "Morning brief", schedule: "0 8 * * *",
                prompt: "Summarize overnight messages, builds and calendar.", enabled: true,
                nextRun: "Tomorrow, 08:00", bot: "athena"),
        Routine(id: "nightly-sweep", name: "Nightly sweep", schedule: "0 2 * * *",
                prompt: "Check the deploy, open PRs and anything on fire.", enabled: true,
                nextRun: "Tomorrow, 02:00", bot: "forge")
    ]

    static let activity: [ActivityItem] = [
        ActivityItem(id: "a1", kind: .tool, title: "release_check finished",
                     detail: "All 14 checks passed · 2m 08s", date: Date().addingTimeInterval(-260),
                     botName: "forge", success: true),
        ActivityItem(id: "a2", kind: .message, title: "Athena replied",
                     detail: "Three things need you today.", date: Date().addingTimeInterval(-480),
                     botName: "athena", success: true),
        ActivityItem(id: "a3", kind: .tool, title: "web_search finished",
                     detail: "Memory landscape · 8 sources", date: Date().addingTimeInterval(-900),
                     botName: "scout", success: true),
        ActivityItem(id: "a4", kind: .bot, title: "Launch Room settled",
                     detail: "3 pixels · 2 rounds · 1 decision", date: Date().addingTimeInterval(-1500),
                     botName: "athena", success: true),
        ActivityItem(id: "a5", kind: .approval, title: "Approval granted",
                     detail: "git push origin release/1.0", date: Date().addingTimeInterval(-3400),
                     botName: "forge", success: true)
    ]

    static func messages(for bot: BotProfile) -> [ChatMessage] {
        let now = Date()
        switch bot.name {
        case "athena":
            return [
                ChatMessage(id: "d1", role: .user, text: "Morning. What actually needs me today?",
                            timestamp: now.addingTimeInterval(-720), toolStatus: .completed),
                ChatMessage(id: "d2", role: .tool, text: "", timestamp: now.addingTimeInterval(-650),
                            toolName: "session_search",
                            toolArgs: "{\n  \"query\": \"open threads since yesterday\"\n}",
                            toolResult: "Found 6 sessions across Athena, Forge and Scout. 3 open threads.",
                            toolStatus: .completed, toolPreview: "6 sessions · 3 open threads",
                            toolDuration: 0.4),
                ChatMessage(id: "d3", role: .assistant,
                            text: """
                            Three things need you today.

                            **1 · Build is green.** Forge finished the clean build and the release notes are ready for review. The only open thread is the signing profile for the TestFlight build.

                            **2 · Scout found a signal.** Two competitors shipped agent memory this week. I saved the teardown in *Research › Memory landscape* — the useful difference is their retrieval UI, not the storage layer.

                            **3 · One decision.** Athena recommends we ship Pixels before custom routines. I agree: the roster is the habit, routines are the power feature.

                            Say the word and I’ll open the release checklist.
                            """,
                            timestamp: now.addingTimeInterval(-480),
                            reasoning: "Prioritising by consequence and time sensitivity. Signing is the only true blocker.",
                            toolStatus: .completed),
                ChatMessage(id: "d4", role: .user, text: "Open the release checklist. Keep it tight.",
                            timestamp: now.addingTimeInterval(-300), toolStatus: .completed)
            ]
        case "forge":
            return [
                ChatMessage(id: "f1", role: .user, text: "Clean build, then tell me what’s blocking TestFlight.",
                            timestamp: now.addingTimeInterval(-900), toolStatus: .completed),
                ChatMessage(id: "f2", role: .tool, text: "", timestamp: now.addingTimeInterval(-840),
                            toolName: "terminal",
                            toolArgs: "{\n  \"command\": \"./gradlew clean assembleRelease\"\n}",
                            toolResult: "BUILD SUCCESSFUL in 2m 08s\\n42 actionable tasks: 42 executed",
                            toolStatus: .completed, toolPreview: "BUILD SUCCESSFUL · 2m 08s",
                            toolDuration: 128.2),
                ChatMessage(id: "f3", role: .assistant,
                            text: """
                            Build is green. **One blocker:** the iOS signing profile isn’t registered for the new bundle ID, so the archive validates but can’t be exported.

                            Two ways forward:
                            - Add `io.sovware.niobium` to the existing distribution profile.
                            - Or create a fresh profile with the two devices we actually test on.

                            I’d take the second — cleaner and easier to rotate later. Want me to open the checklist?
                            """,
                            timestamp: now.addingTimeInterval(-620), toolStatus: .completed),
                ChatMessage(id: "f4", role: .tool, text: "", timestamp: now.addingTimeInterval(-560),
                            toolName: "write_file",
                            toolArgs: "{\n  \"path\": \"dist/release-checklist.md\"\n}",
                            toolResult: "Wrote 1.4 KB → dist/release-checklist.md",
                            toolStatus: .completed, toolPreview: "Wrote release-checklist.md",
                            toolDuration: 0.1)
            ]
        default:
            return [
                ChatMessage(id: "s1", role: .user, text: "Anything I should know from the overnight sweep?",
                            timestamp: now.addingTimeInterval(-1200), toolStatus: .completed),
                ChatMessage(id: "s2", role: .assistant,
                            text: """
                            Quiet night, one real signal.

                            Two competitors shipped agent memory this week. Both store more than us, but neither gives the user a clean way to see *why* something was remembered. That’s the opening.

                            I saved the teardown and a two-minute summary in **Research › Memory landscape**. Nothing else needs you.
                            """,
                            timestamp: now.addingTimeInterval(-1080),
                            toolStatus: .completed)
            ]
        }
    }

    static func events(for room: GroupRoom) -> [RoomEvent] {
        if room.roomID == "demo-nightshift" {
            return [
                RoomEvent(seq: 1, roomID: room.roomID, eventID: "n1", kind: "message.user",
                          actorKind: "user", actorID: "user", actorName: "You",
                          text: "Overnight sweep please. Only wake me for a real fire.",
                          timestamp: Date().addingTimeInterval(-8000), status: nil, reason: nil),
                RoomEvent(seq: 2, roomID: room.roomID, eventID: "n2", kind: "message.member",
                          actorKind: "member", actorID: "hermes", actorName: "Hermes",
                          text: "Overnight sweep complete. Nothing on fire. Two PRs are green and waiting for your review.",
                          timestamp: Date().addingTimeInterval(-7200), status: nil, reason: nil)
            ]
        }
        return [
            RoomEvent(seq: 1, roomID: room.roomID, eventID: "g1", kind: "message.user",
                      actorKind: "user", actorID: "user", actorName: "You",
                      text: "Final pass on the launch. Athena, make the call on scope. Forge, check the build. Scout, watch the competition.",
                      timestamp: Date().addingTimeInterval(-1800), status: nil, reason: nil),
            RoomEvent(seq: 2, roomID: room.roomID, eventID: "g2", kind: "message.member",
                      actorKind: "member", actorID: "athena", actorName: "Athena",
                      text: "Scope call: ship Pixels first, routines second. The roster is the habit; routines are the power feature. I’ll write the decision record.",
                      timestamp: Date().addingTimeInterval(-1600), status: nil, reason: nil),
            RoomEvent(seq: 3, roomID: room.roomID, eventID: "g3", kind: "message.member",
                      actorKind: "member", actorID: "forge", actorName: "Forge",
                      text: "Build is green. Signing profile is the only open thread. I can have TestFlight up today if we take the fresh profile.",
                      timestamp: Date().addingTimeInterval(-1450), status: nil, reason: nil),
            RoomEvent(seq: 4, roomID: room.roomID, eventID: "g4", kind: "message.member",
                      actorKind: "member", actorID: "scout", actorName: "Scout",
                      text: "Competitor watch is clean. No launches in the last 24h. I’ll check the sources and flag anything that looks off.",
                      timestamp: Date().addingTimeInterval(-420), status: nil, reason: nil),
            RoomEvent(seq: 5, roomID: room.roomID, eventID: "g5", kind: "message.user",
                      actorKind: "user", actorID: "user", actorName: "You",
                      text: "Good. Forge, ship it.",
                      timestamp: Date().addingTimeInterval(-360), status: nil, reason: nil)
        ]
    }
}
