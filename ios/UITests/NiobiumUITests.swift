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
    @MainActor
    func testWorkshopRemixesPinnedTraitsAndOffersAllCompanions() {
        let app = XCUIApplication()
        app.launchArguments = ["--demo", "--light", "--no-splash", "--pixel-review"]
        app.launch()
        XCTAssertTrue(app.staticTexts["Local template"].waitForExistence(timeout: 15))
        app.buttons["Appearance & companion"].tap()
        XCTAssertTrue(app.buttons["character.remix"].waitForExistence(timeout: 5))
        let build = app.buttons["character.body"].label
        app.buttons["character.remix"].tap()
        XCTAssertEqual(app.buttons["character.body"].label, build)
        let hair = app.buttons["character.hair"]
        for _ in 0..<4 where !hair.isHittable { app.swipeUp() }
        let style = hair.label
        app.buttons["character.pin.hair"].tap()
        let appearance = app.buttons["Appearance & companion"]
        for _ in 0..<6 where !appearance.isHittable { app.swipeDown() }
        appearance.tap()
        appearance.tap()
        XCTAssertEqual(app.buttons["character.pin.hair"].value as? String, "Pinned")
        app.buttons["character.remix"].tap()
        XCTAssertEqual(hair.label, style)
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "Pixel appearance editor"
        screenshot.lifetime = .keepAlways
        add(screenshot)
        let mimic = app.buttons["familiar.mimic"]
        for _ in 0..<8 where !mimic.isHittable { app.swipeUp() }
        XCTAssertTrue(mimic.isHittable)
        mimic.tap()
        XCTAssertTrue(mimic.isSelected)
        let companions = XCTAttachment(screenshot: app.screenshot())
        companions.name = "Twelve independent companions"
        companions.lifetime = .keepAlways
        add(companions)
    }

    @MainActor
    func testEnginesKeepTheStandardNavigationAndHideAdvancedDsh() {
        let app = XCUIApplication()
        app.launchArguments = ["--demo", "--light", "--no-splash", "--tab", "local"]
        app.launch()
        XCTAssertTrue(app.staticTexts["A home for every Pixel."].waitForExistence(timeout: 15))
        XCTAssertTrue(app.staticTexts["On device"].exists)
        XCTAssertTrue(app.staticTexts["Hermes"].exists)
        XCTAssertTrue(app.buttons["Home"].exists)
        XCTAssertTrue(app.buttons["Pixels"].exists)
        XCTAssertFalse(app.webViews.firstMatch.exists)
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "Native Engines"
        screenshot.lifetime = .keepAlways
        add(screenshot)
        app.buttons["Home"].tap()
        XCTAssertTrue(app.staticTexts["Your Pixels"].waitForExistence(timeout: 5))
    }

}
