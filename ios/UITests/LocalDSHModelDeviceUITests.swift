import XCTest

/// A real DSH turn through a controlled model endpoint in a separate app sandbox.
final class LocalDSHModelDeviceUITests: XCTestCase {
    @MainActor
    func testStreamSurvivesBackgroundAndHistorySurvivesColdLaunch() throws {
        try XCTSkipUnless(ProcessInfo.processInfo.environment["NIOBIUM_DEVICE_MODEL_TEST"] == "1",
                          "Requires the isolated debug acceptance app.")
        continueAfterFailure = false
        executionTimeAllowance = 600
        let fixture = try XCTUnwrap(NBLocalModelFixture())
        let prefix = "LOCAL DSH \(fixture.marker)"
        defer { fixture.stop() }
        let app = XCUIApplication(bundleIdentifier: "ai.wckd.niobium.acceptance")
        app.launchArguments = ["--dsh", "--no-splash"]
        app.launchEnvironment["NIOBIUM_DSH_FIXTURE_URL"] = fixture.baseURL
        app.launch()

        let scout = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "Scout,")).firstMatch
        XCTAssertTrue(scout.waitForExistence(timeout: 300))
        scout.tap()
        let composer = app.textFields["chat.composer"]
        XCTAssertTrue(composer.waitForExistence(timeout: 90))
        let attached = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"),
                                                object: app.staticTexts["Attaching to Scout…"])
        XCTAssertEqual(XCTWaiter.wait(for: [attached], timeout: 90), .completed)
        XCTAssertTrue(composer.isHittable, "The native composer must be ready for input after attachment.")
        composer.tap()
        composer.typeText("Say hello for the local acceptance check.")
        let send = app.buttons["Send message"]
        let ready = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true"), object: send)
        XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 90), .completed)
        send.tap()
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", prefix)).firstMatch.waitForExistence(timeout: 60))
        let reply = app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", prefix + " DEVICE ROUND TRIP")).firstMatch
        XCTAssertTrue(app.buttons["Stop response"].exists, "Background acceptance must begin during an unfinished turn.")
        XCTAssertFalse(reply.exists)
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 8)
        fixture.releaseReply()
        app.activate()
        XCTAssertTrue(reply.waitForExistence(timeout: 90))
        XCTAssertGreaterThan(fixture.requestCount, 0)
        XCTAssertTrue(app.buttons["Send message"].waitForExistence(timeout: 30), "The streamed turn must finish.")

        app.terminate()
        app.launch()
        XCTAssertTrue(scout.waitForExistence(timeout: 300))
        scout.tap()
        XCTAssertTrue(reply.waitForExistence(timeout: 90), "Real DSH history must survive a full app/guest restart.")
        XCTAssertFalse(app.webViews.firstMatch.exists)
    }
}
