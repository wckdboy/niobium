import SwiftUI
import Combine

struct AtomicMark: View {
    var size: CGFloat = 34
    var animated = false
    @State private var glow = false

    var body: some View {
        Image("PixelNiobium")
            .resizable()
            .interpolation(.none)
            .frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: size * 0.29, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: size * 0.29, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.14), lineWidth: 0.8)
            )
            .shadow(color: NB.ion.opacity(animated && glow ? 0.34 : 0.18), radius: size * 0.34, y: size * 0.12)
            .onAppear {
                guard animated else { return }
                withAnimation(.easeInOut(duration: 2.8).repeatForever(autoreverses: true)) { glow = true }
            }
    }
}

struct Wordmark: View {
    var body: some View {
        HStack(spacing: 9) {
            AtomicMark(size: 28)
            Text("niobium")
                .font(.system(size: 16, weight: .semibold))
                .tracking(0.8)
                .foregroundStyle(NB.text)
        }
    }
}

struct PixelFace: View {
    var accent: Color
    private let rows = [
        "..A..A..",
        ".AAAAAA.",
        "AAAAAAAA",
        "AADAADAA",
        "AAAAAAAA",
        "AAADDAAA",
        ".AAAAAA.",
        "..A..A.."
    ]

    var body: some View {
        Canvas { context, size in
            let cell = min(size.width, size.height) / 8
            for (row, string) in rows.enumerated() {
                for (col, char) in string.enumerated() {
                    guard char != "." else { continue }
                    let rect = CGRect(x: CGFloat(col) * cell, y: CGFloat(row) * cell, width: cell, height: cell)
                    let color: Color
                    if char == "D" {
                        color = Color(hex: 0x0B0712)
                    } else if row >= 6 {
                        color = accent.opacity(0.72)
                    } else {
                        color = accent
                    }
                    context.fill(Path(rect), with: .color(color))
                }
            }
        }
    }
}

struct PixelTile: View {
    var accent: Color
    var size: CGFloat
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.28, style: .continuous)
                .fill(
                    LinearGradient(colors: [accent.opacity(0.28), NB.ink],
                                   startPoint: .topLeading, endPoint: .bottomTrailing)
                )
            PixelFace(accent: accent)
                .padding(size * 0.10)
        }
        .frame(width: size, height: size)
        .overlay(
            RoundedRectangle(cornerRadius: size * 0.28, style: .continuous)
                .strokeBorder(
                    LinearGradient(colors: [.white.opacity(0.26), .white.opacity(0.03)],
                                   startPoint: .topLeading, endPoint: .bottomTrailing),
                    lineWidth: 1
                )
        )
    }
}

struct BotAvatar: View {
    var bot: BotProfile
    var size: CGFloat = 48
    var showStatus = true

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            PixelTile(accent: bot.accent, size: size)
            if showStatus {
                RoundedRectangle(cornerRadius: size * 0.05, style: .continuous)
                    .fill(bot.isLive ? NB.mint : NB.tertiary)
                    .frame(width: size * 0.20, height: size * 0.20)
                    .overlay(
                        RoundedRectangle(cornerRadius: size * 0.05)
                            .strokeBorder(NB.ink, lineWidth: 2)
                    )
                    .offset(x: -1, y: -1)
            }
        }
    }
}

struct GroupAvatar: View {
    var members: [GroupMember]
    var size: CGFloat = 46
    var body: some View {
        let shown = Array(members.prefix(3))
        ZStack {
            ForEach(Array(shown.enumerated()), id: \.offset) { index, member in
                PixelTile(accent: NB.color(for: member.profile), size: size * 0.58)
                    .overlay(
                        RoundedRectangle(cornerRadius: size * 0.17, style: .continuous)
                            .strokeBorder(NB.ink, lineWidth: 2)
                    )
                    .offset(x: index == 0 ? -size * 0.19 : (index == 1 ? size * 0.21 : 0),
                            y: index == 2 ? size * 0.17 : -size * 0.11)
            }
        }
        .frame(width: size, height: size)
    }
}

struct BotRow: View {
    var bot: BotProfile
    var onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 13) {
                BotAvatar(bot: bot, size: 50)
                VStack(alignment: .leading, spacing: 5) {
                    HStack(spacing: 6) {
                        Text(bot.title)
                            .font(.nbTitle(16))
                            .foregroundStyle(NB.text)
                            .lineLimit(1)
                        if bot.isDefault {
                            Circle().fill(NB.ion).frame(width: 4, height: 4)
                        }
                        Spacer(minLength: 4)
                        Text(bot.statusLine)
                            .font(.nbBody(10.5))
                            .foregroundStyle(bot.isLive ? NB.mint : NB.tertiary)
                    }
                    Text(bot.preview.isEmpty ? "Start a conversation" : bot.preview)
                        .font(.nbBody(13))
                        .foregroundStyle(NB.secondary)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                }
                Image(systemName: "chevron.right")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(NB.tertiary.opacity(0.55))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(bot.isLive ? AnyShapeStyle(
                        LinearGradient(colors: [NB.ion.opacity(0.10), NB.surface.opacity(0.55)],
                                       startPoint: .topLeading, endPoint: .bottomTrailing))
                          : AnyShapeStyle(Color.white.opacity(0.028)))
                    .overlay(
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .strokeBorder(bot.isLive ? NB.ion.opacity(0.22) : NB.line, lineWidth: 1)
                    )
            )
        }
        .buttonStyle(.plain)
    }
}

struct GroupRow: View {
    var room: GroupRoom
    var onTap: () -> Void
    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 13) {
                GroupAvatar(members: room.members, size: 44)
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(room.name).font(.nbTitle(15)).foregroundStyle(NB.text)
                        Text("· \(room.members.count) pixels")
                            .font(.nbBody(10.5))
                            .foregroundStyle(NB.tertiary)
                        Spacer()
                        if let updated = room.updatedAt {
                            Text(updated.relativeShort).font(.nbBody(10.5)).foregroundStyle(NB.tertiary)
                        }
                    }
                    Text(room.preview.isEmpty ? "Open the room" : room.preview)
                        .font(.nbBody(12.5))
                        .foregroundStyle(NB.secondary)
                        .lineLimit(1)
                }
                Image(systemName: "chevron.right")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(NB.tertiary.opacity(0.55))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 11)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Color.white.opacity(0.028)))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(NB.line, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

struct MarkdownText: View {
    var text: String
    var tint: Color = NB.text
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(Self.blocks(text).enumerated()), id: \.offset) { _, block in
                switch block {
                case .paragraph(let value):
                    Text(Self.inline(value))
                        .font(.nbBody(15.5))
                        .foregroundStyle(tint)
                        .lineSpacing(4)
                        .textSelection(.enabled)
                        .fixedSize(horizontal: false, vertical: true)
                case .bullet(let value):
                    HStack(alignment: .firstTextBaseline, spacing: 9) {
                        Circle().fill(NB.ion).frame(width: 4, height: 4).offset(y: -2)
                        Text(Self.inline(value))
                            .font(.nbBody(15))
                            .foregroundStyle(tint.opacity(0.92))
                            .lineSpacing(3)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                case .numbered(let number, let value):
                    HStack(alignment: .firstTextBaseline, spacing: 9) {
                        Text("\(number).")
                            .font(.nbMono(11, weight: .semibold))
                            .foregroundStyle(NB.ion)
                            .frame(width: 18, alignment: .trailing)
                        Text(Self.inline(value))
                            .font(.nbBody(15))
                            .foregroundStyle(tint.opacity(0.92))
                            .lineSpacing(3)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                case .code(let value):
                    Text(value)
                        .font(.nbMono(12))
                        .foregroundStyle(Color(hex: 0xC9D6F0))
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(RoundedRectangle(cornerRadius: 12).fill(.black.opacity(0.28)))
                        .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(NB.line, lineWidth: 1))
                        .textSelection(.enabled)
                }
            }
        }
    }

    enum Block {
        case paragraph(String)
        case bullet(String)
        case numbered(Int, String)
        case code(String)
    }

    static func blocks(_ text: String) -> [Block] {
        var blocks: [Block] = []
        var code: [String] = []
        var inCode = false
        for rawLine in text.components(separatedBy: .newlines) {
            let line = rawLine.trimmingCharacters(in: .whitespaces)
            if line.hasPrefix("```") {
                if inCode {
                    blocks.append(.code(code.joined(separator: "\n")))
                    code = []
                }
                inCode.toggle()
                continue
            }
            if inCode {
                code.append(rawLine)
                continue
            }
            if line.isEmpty { continue }
            if line.hasPrefix("- ") || line.hasPrefix("• ") {
                blocks.append(.bullet(String(line.dropFirst(2))))
            } else if let match = line.range(of: #"^\d+\.\s+"#, options: .regularExpression) {
                let number = Int(line[..<match.upperBound].trimmingCharacters(in: CharacterSet(charactersIn: ". "))) ?? 1
                blocks.append(.numbered(number, String(line[match.upperBound...])))
            } else {
                blocks.append(.paragraph(line))
            }
        }
        if inCode, !code.isEmpty { blocks.append(.code(code.joined(separator: "\n"))) }
        return blocks
    }

    static func inline(_ text: String) -> AttributedString {
        (try? AttributedString(markdown: text,
                               options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace))) ?? AttributedString(text)
    }
}

struct ToolCallCard: View {
    var message: ChatMessage
    @State private var expanded = false
    @State private var showResult = true

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button {
                withAnimation(.spring(response: 0.32, dampingFraction: 0.85)) { expanded.toggle() }
            } label: {
                HStack(spacing: 11) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 9, style: .continuous)
                            .fill(statusColor.opacity(0.13))
                        Image(systemName: icon)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(statusColor)
                    }
                    .frame(width: 30, height: 30)
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 6) {
                            Text(message.toolName ?? "tool")
                                .font(.nbMono(11, weight: .semibold))
                                .foregroundStyle(NB.text)
                            if message.toolStatus == .running {
                                Text("RUNNING")
                                    .font(.nbMono(8, weight: .semibold)).tracking(0.7)
                                    .foregroundStyle(NB.amber)
                            } else {
                                Text(message.toolStatus == .failed ? "FAILED" : "DONE")
                                    .font(.nbMono(8, weight: .semibold)).tracking(0.7)
                                    .foregroundStyle(message.toolStatus == .failed ? NB.rose : NB.mint)
                            }
                        }
                        Text(message.toolSummary)
                            .font(.nbBody(12))
                            .foregroundStyle(NB.secondary)
                            .lineLimit(expanded ? 4 : 1)
                    }
                    Spacer()
                    if let duration = message.toolDuration {
                        Text(String(format: "%.1fs", duration))
                            .font(.nbMono(9))
                            .foregroundStyle(NB.tertiary)
                    }
                    Image(systemName: "chevron.down")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundStyle(NB.tertiary)
                        .rotationEffect(.degrees(expanded ? 180 : 0))
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
            }
            .buttonStyle(.plain)

            if expanded {
                VStack(alignment: .leading, spacing: 10) {
                    NBDivider()
                    if let args = message.toolArgs, !args.isEmpty {
                        codeSection(title: "INPUT", value: args)
                    }
                    if let result = message.toolResult, !result.isEmpty {
                        codeSection(title: "OUTPUT", value: String(result.prefix(6000)))
                    }
                }
                .padding(.horizontal, 12)
                .padding(.bottom, 12)
            }
        }
        .background(
            RoundedRectangle(cornerRadius: 15, style: .continuous)
                .fill(NB.surface.opacity(0.78))
                .overlay(
                    RoundedRectangle(cornerRadius: 15, style: .continuous)
                        .strokeBorder(statusColor.opacity(message.toolStatus == .running ? 0.35 : 0.14), lineWidth: 1)
                )
        )
    }

    private func codeSection(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(title).font(.nbMono(8, weight: .semibold)).tracking(1.3).foregroundStyle(NB.tertiary)
            ScrollView(.horizontal, showsIndicators: false) {
                Text(value)
                    .font(.nbMono(10.5))
                    .foregroundStyle(Color(hex: 0xB9C6DE))
                    .textSelection(.enabled)
                    .padding(9)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 9).fill(.black.opacity(0.22)))
        }
    }

    private var statusColor: Color {
        switch message.toolStatus {
        case .running: return NB.amber
        case .completed: return NB.mint
        case .failed: return NB.rose
        }
    }
    private var icon: String {
        switch message.toolName {
        case "terminal": return "terminal"
        case "web_search", "web_extract": return "globe"
        case "browser_navigate", "browser_snapshot": return "safari"
        case "read_file", "write_file", "patch": return "doc.text"
        case "delegate_task": return "point.3.connected.trianglepath.dotted"
        case "image_generate": return "wand.and.stars"
        case "vision_analyze": return "eye"
        default: return "hammer"
        }
    }
}

struct ThinkingBlock: View {
    var text: String
    @State private var open = false
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Button {
                withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { open.toggle() }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "sparkle")
                        .font(.system(size: 9, weight: .semibold))
                    Text(open ? "Hide reasoning" : "Reasoning")
                        .font(.nbMono(9, weight: .medium))
                    Image(systemName: "chevron.down")
                        .font(.system(size: 7, weight: .bold))
                        .rotationEffect(.degrees(open ? 180 : 0))
                }
                .foregroundStyle(NB.violet.opacity(0.9))
            }
            .buttonStyle(.plain)
            if open {
                Text(text)
                    .font(.nbBody(12.5))
                    .foregroundStyle(NB.secondary)
                    .lineSpacing(3)
                    .padding(.leading, 10)
                    .overlay(alignment: .leading) {
                        Rectangle().fill(NB.violet.opacity(0.25)).frame(width: 1)
                    }
            }
        }
    }
}

struct TypingIndicator: View {
    @State private var phase = 0
    let timer = Timer.publish(every: 0.42, on: .main, in: .common).autoconnect()
    var body: some View {
        HStack(spacing: 4) {
            ForEach(0..<3) { index in
                Circle().fill(NB.ion.opacity(phase == index ? 1 : 0.28))
                    .frame(width: 5, height: 5)
            }
        }
        .onReceive(timer) { _ in phase = (phase + 1) % 3 }
    }
}

struct EmptyStateView: View {
    var icon: String
    var title: String
    var detail: String
    var actionTitle: String? = nil
    var action: (() -> Void)? = nil
    var body: some View {
        VStack(spacing: 14) {
            ZStack {
                Circle().fill(NB.ion.opacity(0.08)).frame(width: 68, height: 68)
                Image(systemName: icon).font(.system(size: 24, weight: .light)).foregroundStyle(NB.ion)
            }
            Text(title).font(.nbTitle(18)).foregroundStyle(NB.text)
            Text(detail)
                .font(.nbBody(13))
                .foregroundStyle(NB.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 300)
            if let actionTitle, let action {
                Button(actionTitle, action: action).buttonStyle(NBQuietButtonStyle(compact: true))
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 36)
    }
}

struct NBToast: View {
    var text: String
    var isError = false
    var body: some View {
        HStack(spacing: 9) {
            Image(systemName: isError ? "exclamationmark.triangle.fill" : "checkmark.circle.fill")
                .foregroundStyle(isError ? NB.rose : NB.mint)
                .font(.system(size: 13, weight: .semibold))
            Text(text)
                .font(.nbBody(12.5, weight: .medium))
                .foregroundStyle(NB.text)
                .lineLimit(5)
                .multilineTextAlignment(.leading)
        }
        .padding(.horizontal, 15)
        .padding(.vertical, 12)
        .background(
            Capsule(style: .continuous)
                .fill(NB.raised.opacity(0.96))
                .overlay(Capsule().strokeBorder(isError ? NB.rose.opacity(0.3) : NB.lineStrong, lineWidth: 1))
                .shadow(color: .black.opacity(0.45), radius: 22, y: 10)
        )
        .padding(.horizontal, 18)
    }
}
