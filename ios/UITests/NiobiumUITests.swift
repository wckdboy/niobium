import XCTest

final class NiobiumUITests: XCTestCase {
    @MainActor
    func testDashboardDemoHasRosterAndNoInventedTelemetry() {
        let app = XCUIApplication()
        app.launchArguments = ["--demo", "--light", "--no-splash"]
        app.launch()
        XCTAssertTrue(app.staticTexts["Your Pixels"].waitForExistence(timeout: 15))
        XCTAssertTrue(app.buttons["New Pixel"].exists)
        XCTAssertTrue(app.staticTexts["Orion"].exists)
        XCTAssertFalse(app.staticTexts["RTX 5070 Ti"].exists)
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "Dashboard"
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }
    @MainActor
    func testWorkshopShowsHonestTemplateAndAdultReview() {
        let app = XCUIApplication()
        app.launchArguments = ["--demo", "--light", "--no-splash", "--pixel-review"]
        app.launch()
        XCTAssertTrue(app.staticTexts["Local template"].waitForExistence(timeout: 15))
        XCTAssertTrue(app.staticTexts["Nyx"].firstMatch.exists)
        XCTAssertTrue(app.buttons["Create Nyx"].exists)
        XCTAssertTrue(app.staticTexts["Dusk · bat"].exists)
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "Pixel review"
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }
}
