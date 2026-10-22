import SwiftUI

/// Every new Pixel goes through the same design, review and provisioning flow.
struct NewBotView: View {
    @EnvironmentObject private var store: AppStore
    var body: some View {
        AIBuildView { bot in store.openBot(bot) }.environmentObject(store)
    }
}
