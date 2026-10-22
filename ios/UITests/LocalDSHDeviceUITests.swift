import XCTest

/// Opt-in acceptance against the signed full DSH app on a connected device.
/// The default client-only CI target cannot boot the bundled Linux runtime.
final class LocalDSHDeviceUITests: XCTestCase {
    @MainActor
    func testLocalEngineUsesNativeNavigationAndRestoresAfterBackgrounding() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["NIOBIUM_DEVICE_DSH_TEST"] == "1",
                          "Requires the signed full DSH app and explicit device acceptance.")
        continueAfterFailure = false
        executionTimeAllowance = 420
        let app = XCUIApplication(bundleIdentifier: "ai.wckd.niobium")
        app.launchArguments = ["--dsh", "--no-splash"]
        app.launch()

        XCTAssertTrue(app.staticTexts["On-device engine"].waitForExistence(timeout: 20))
        let scout = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Scout,")).firstMatch
        let loadedOrFailed = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            scout.exists || app.buttons["Error"].exists
        }, object: app)
        XCTAssertEqual(XCTWaiter.wait(for: [loadedOrFailed], timeout: 300), .completed)
        XCTAssertTrue(scout.exists, "The real local DSH roster must load. Home: \(app.staticTexts.allElementsBoundByIndex.map(\.label).joined(separator: "; "))")
        XCTAssertTrue(app.buttons["New Pixel"].isEnabled)
        XCTAssertFalse(app.webViews.firstMatch.exists)
        capture(app, "Physical DSH Home")

        app.buttons["Engines"].tap()
        XCTAssertTrue(app.staticTexts["Ready · chats and tasks run in Niobium"].waitForExistence(timeout: 15))
        XCTAssertTrue(app.secureTextFields["DeepSeek API key"].exists)
        XCTAssertFalse(app.buttons["Advanced DSH workspace"].exists)
        XCTAssertFalse(app.webViews.firstMatch.exists)
        capture(app, "Physical native Engines")

        app.buttons["Home"].tap()
        XCTAssertTrue(scout.waitForExistence(timeout: 10))
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 8)
        app.activate()
        XCTAssertTrue(scout.waitForExistence(timeout: 30))
        let ready = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true"),
                                             object: app.buttons["New Pixel"])
        XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 30), .completed)
        app.buttons["Engines"].tap()
        XCTAssertTrue(app.staticTexts["Ready · chats and tasks run in Niobium"].waitForExistence(timeout: 30))
        app.buttons["Home"].tap()

        scout.tap()
        let composer = app.textFields["chat.composer"]
        XCTAssertTrue(composer.waitForExistence(timeout: 90))
        let attached = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"),
                                                object: app.staticTexts["Attaching to Scout…"])
        XCTAssertEqual(XCTWaiter.wait(for: [attached], timeout: 90), .completed)
        let draft = "Connection check"
        composer.tap()
        composer.typeText(draft)
        XCTAssertTrue(app.buttons["Send message"].isEnabled, "The real local session must complete its attachment handshake.")
        composer.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: draft.count))
        XCTAssertFalse(app.buttons["Send message"].isEnabled)
        XCTAssertFalse(app.webViews.firstMatch.exists)
        // Do not send a model request, capture private chat text or change keys.
    }

    @MainActor
    private func capture(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
