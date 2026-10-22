import Foundation

enum DemoData {
    static let bots: [BotProfile] = [
        BotProfile(name: "orion", title: "Orion", role: "Research & analysis", provider: "demo", model: "Demo model", skillCount: 6,
                   canonicalSessionID: "demo-orion", canonicalResolvedID: "demo-orion", lastSessionID: "demo-orion",
                   preview: "Give me the question. I will bring the receipts.", activityAt: Date().addingTimeInterval(-30), workerActive: true,
                   isDefault: true, hidden: false, accentHex: "#A78BFA", hasAvatar: false),
        BotProfile(name: "nova", title: "Nova", role: "Creative direction", provider: "demo", model: "Demo model", skillCount: 7,
                   canonicalSessionID: "demo-nova", canonicalResolvedID: "demo-nova", lastSessionID: "demo-nova",
                   preview: "A little trouble, a better idea.", activityAt: Date().addingTimeInterval(-360), workerActive: false,
                   isDefault: false, hidden: false, accentHex: "#F0ABFC", hasAvatar: false),
        BotProfile(name: "forge", title: "Forge", role: "Automation & releases", provider: "demo", model: "Demo model", skillCount: 8,
                   canonicalSessionID: "demo-forge", canonicalResolvedID: "demo-forge", lastSessionID: "demo-forge",
                   preview: "Build first. Brag after the checks pass.", activityAt: Date().addingTimeInterval(-420), workerActive: false,
                   isDefault: false, hidden: false, accentHex: "#F5C97A", hasAvatar: false),
        BotProfile(name: "sage", title: "Sage", role: "Knowledge & memory", provider: "demo", model: "Demo model", skillCount: 9,
                   canonicalSessionID: "demo-sage", canonicalResolvedID: "demo-sage", lastSessionID: "demo-sage",
                   preview: "I kept the useful bits. And the strange ones.", activityAt: Date().addingTimeInterval(-30), workerActive: true,
                   isDefault: false, hidden: false, accentHex: "#5EEAD4", hasAvatar: false),
        BotProfile(name: "nyx", title: "Nyx", role: "Security & anomalies", provider: "demo", model: "Demo model", skillCount: 10,
                   canonicalSessionID: "demo-nyx", canonicalResolvedID: "demo-nyx", lastSessionID: "demo-nyx",
                   preview: "Quiet night. Suspiciously quiet.", activityAt: Date().addingTimeInterval(-540), workerActive: false,
                   isDefault: false, hidden: false, accentHex: "#FB7185", hasAvatar: false),
        BotProfile(name: "astra", title: "Astra", role: "Vision & field work", provider: "demo", model: "Demo model", skillCount: 11,
                   canonicalSessionID: "demo-astra", canonicalResolvedID: "demo-astra", lastSessionID: "demo-astra",
                   preview: "Eyes open. Instruments calibrated.", activityAt: Date().addingTimeInterval(-600), workerActive: false,
                   isDefault: false, hidden: false, accentHex: "#7DD3FC", hasAvatar: false),
        BotProfile(name: "vesper", title: "Vesper", role: "Writing & long memory", provider: "demo", model: "Demo model", skillCount: 12,
                   canonicalSessionID: "demo-vesper", canonicalResolvedID: "demo-vesper", lastSessionID: "demo-vesper",
                   preview: "The footnotes have secrets.", activityAt: Date().addingTimeInterval(-660), workerActive: false,
                   isDefault: false, hidden: false, accentHex: "#C4B5FF", hasAvatar: false),
        BotProfile(name: "rook", title: "Rook", role: "Research & reconnaissance", provider: "demo", model: "Demo model", skillCount: 13,
                   canonicalSessionID: "demo-rook", canonicalResolvedID: "demo-rook", lastSessionID: "demo-rook",
                   preview: "One more trail worth following.", activityAt: Date().addingTimeInterval(-720), workerActive: false,
                   isDefault: false, hidden: false, accentHex: "#D6A45E", hasAvatar: false)
    ]

    static let groups: [GroupRoom] = [
        GroupRoom(roomID: "demo-launch", name: "Launch Room",
                  members: [
                    GroupMember(memberID: "orion", profile: "orion", handle: "orion", displayName: "Orion"),
                    GroupMember(memberID: "forge", profile: "forge", handle: "forge", displayName: "Forge"),
                    GroupMember(memberID: "rook", profile: "rook", handle: "rook", displayName: "Rook")
                  ],
                  updatedAt: Date().addingTimeInterval(-420), latestSeq: 5, disbanded: false,
                  preview: "Rook: I’ll check the sources and flag anything that looks off."),
        GroupRoom(roomID: "demo-nightshift", name: "Night Shift",
                  members: [
                    GroupMember(memberID: "vesper", profile: "vesper", handle: "vesper", displayName: "Vesper"),
                    GroupMember(memberID: "rook", profile: "rook", handle: "rook", displayName: "Rook")
                  ],
                  updatedAt: Date().addingTimeInterval(-7200), latestSeq: 2, disbanded: false,
                  preview: "Hermes: Overnight sweep complete. Nothing on fire.")
    ]

    static let toolsets: [Toolset] = [
        Toolset(name: "terminal", description: "Run commands, manage background processes and work on the host.", tools: ["terminal", "process_manage"], enabled: true),
        Toolset(name: "web", description: "Search, extract and read the live web.", tools: ["web_search", "web_extract"], enabled: true),
        Toolset(name: "browser", description: "Drive a real browser: navigate, click, type, inspect.", tools: ["browser_navigate", "browser_click", "browser_snapshot", "browser_vision"], enabled: true),
        Toolset(name: "file", description: "Read, write and patch files across a workspace.", tools: ["read_file", "write_file", "patch", "search_files"], enabled: true),
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
                nextRun: "Tomorrow, 08:00", bot: "orion"),
        Routine(id: "nightly-sweep", name: "Nightly sweep", schedule: "0 2 * * *",
                prompt: "Check the deploy, open PRs and anything on fire.", enabled: true,
                nextRun: "Tomorrow, 02:00", bot: "forge")
    ]

    static let activity: [ActivityItem] = [
        ActivityItem(id: "a1", kind: .tool, title: "release_check finished",
                     detail: "All 14 checks passed · 2m 08s", date: Date().addingTimeInterval(-260),
                     botName: "forge", success: true),
        ActivityItem(id: "a2", kind: .message, title: "Orion replied",
                     detail: "Three things need you today.", date: Date().addingTimeInterval(-480),
                     botName: "orion", success: true),
        ActivityItem(id: "a3", kind: .tool, title: "web_search finished",
                     detail: "Memory landscape · 8 sources", date: Date().addingTimeInterval(-900),
                     botName: "rook", success: true),
        ActivityItem(id: "a4", kind: .bot, title: "Launch Room settled",
                     detail: "3 pixels · 2 rounds · 1 decision", date: Date().addingTimeInterval(-1500),
                     botName: "orion", success: true),
        ActivityItem(id: "a5", kind: .approval, title: "Approval granted",
                     detail: "git push origin release/1.0", date: Date().addingTimeInterval(-3400),
                     botName: "forge", success: true)
    ]

    static func messages(for bot: BotProfile) -> [ChatMessage] {
        let now = Date()
        switch bot.name {
        case "orion":
            return [
                ChatMessage(id: "d1", role: .user, text: "Morning. What actually needs me today?",
                            timestamp: now.addingTimeInterval(-720), toolStatus: .completed),
                ChatMessage(id: "d2", role: .tool, text: "", timestamp: now.addingTimeInterval(-650),
                            toolName: "session_search",
                            toolArgs: "{\n  \"query\": \"open threads since yesterday\"\n}",
                            toolResult: "Found 6 sessions across Orion, Forge and Rook. 3 open threads.",
                            toolStatus: .completed, toolPreview: "6 sessions · 3 open threads",
                            toolDuration: 0.4),
                ChatMessage(id: "d3", role: .assistant,
                            text: """
                            Three things need you today.

                            **1 · Build is green.** Forge finished the clean build and the release notes are ready for review. The only open thread is the signing profile for the TestFlight build.

                            **2 · Rook found a signal.** Two competitors shipped agent memory this week. I saved the teardown in *Research › Memory landscape* — the useful difference is their retrieval UI, not the storage layer.

                            **3 · One decision.** Orion recommends we ship Pixels before custom routines. I agree: the roster is the habit, routines are the power feature.

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
                          actorKind: "member", actorID: "vesper", actorName: "Vesper",
                          text: "Overnight sweep complete. Nothing on fire. Two PRs are green and waiting for your review.",
                          timestamp: Date().addingTimeInterval(-7200), status: nil, reason: nil)
            ]
        }
        return [
            RoomEvent(seq: 1, roomID: room.roomID, eventID: "g1", kind: "message.user",
                      actorKind: "user", actorID: "user", actorName: "You",
                      text: "Final pass on the launch. Orion, make the call on scope. Forge, check the build. Rook, watch the competition.",
                      timestamp: Date().addingTimeInterval(-1800), status: nil, reason: nil),
            RoomEvent(seq: 2, roomID: room.roomID, eventID: "g2", kind: "message.member",
                      actorKind: "member", actorID: "orion", actorName: "Orion",
                      text: "Scope call: ship Pixels first, routines second. The roster is the habit; routines are the power feature. I’ll write the decision record.",
                      timestamp: Date().addingTimeInterval(-1600), status: nil, reason: nil),
            RoomEvent(seq: 3, roomID: room.roomID, eventID: "g3", kind: "message.member",
                      actorKind: "member", actorID: "forge", actorName: "Forge",
                      text: "Build is green. Signing profile is the only open thread. I can have TestFlight up today if we take the fresh profile.",
                      timestamp: Date().addingTimeInterval(-1450), status: nil, reason: nil),
            RoomEvent(seq: 4, roomID: room.roomID, eventID: "g4", kind: "message.member",
                      actorKind: "member", actorID: "rook", actorName: "Rook",
                      text: "Competitor watch is clean. No launches in the last 24h. I’ll check the sources and flag anything that looks off.",
                      timestamp: Date().addingTimeInterval(-420), status: nil, reason: nil),
            RoomEvent(seq: 5, roomID: room.roomID, eventID: "g5", kind: "message.user",
                      actorKind: "user", actorID: "user", actorName: "You",
                      text: "Good. Forge, ship it.",
                      timestamp: Date().addingTimeInterval(-360), status: nil, reason: nil)
        ]
    }
}
