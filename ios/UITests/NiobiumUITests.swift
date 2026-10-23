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
        XCTAssertEqual(app.textFields["Pixel name"].value as? String, "Nyx")
        XCTAssertTrue(app.buttons["Create Nyx"].exists)
        XCTAssertTrue(app.staticTexts["Dusk · bat"].exists)
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "Pixel review"
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }
    @MainActor
    func testWorkshopPreservesThePortraitThroughRemixUndoAndTabChanges() {
        let app = XCUIApplication()
        app.launchArguments = ["--demo", "--light", "--no-splash", "--pixel-review"]
        app.launch()
        XCTAssertTrue(app.buttons["look.nyx"].waitForExistence(timeout: 15))
        let nyx = app.buttons["look.nyx"]
        nyx.tap()
        XCTAssertTrue(nyx.isSelected)
        let color = app.buttons["look.color.outfit"]
        let original = color.value as? String
        let remix = app.buttons["look.remix"]
        for _ in 0..<3 where !remix.isHittable { app.swipeUp() }
        remix.tap()
        XCTAssertTrue(nyx.isSelected)
        XCTAssertNotEqual(color.value as? String, original)
        app.buttons["look.undo"].tap()
        XCTAssertEqual(color.value as? String, original)
        app.buttons["builder.tab.creature"].tap()
        let mimic = app.buttons["familiar.mimic"]
        for _ in 0..<5 where !mimic.isHittable { app.swipeUp() }
        XCTAssertTrue(mimic.isHittable)
        mimic.tap()
        XCTAssertTrue(mimic.isSelected)
        let creatures = XCTAttachment(screenshot: app.screenshot())
        creatures.name = "Twelve independent creatures"; creatures.lifetime = .keepAlways; add(creatures)
        let look = app.buttons["builder.tab.look"]
        for _ in 0..<5 where !look.isHittable { app.swipeDown() }
        look.tap()
        XCTAssertTrue(nyx.isSelected)
        XCTAssertEqual(color.value as? String, original)
        XCTAssertTrue(app.buttons["Create Nyx"].isHittable)
    }

    @MainActor
    func testWardrobeFiltersNewLooksAndAIStaysOptional() {
        let app = XCUIApplication()
        app.launchArguments = ["--demo", "--light", "--no-splash", "--pixel-review"]
        app.launch()
        let browse = app.buttons["look.browse"]
        XCTAssertTrue(browse.waitForExistence(timeout: 15))
        for _ in 0..<4 where !browse.isHittable { app.swipeUp() }
        browse.tap()
        app.buttons["wardrobe.family.dark"].tap()
        let lilith = app.buttons["look.lilith"]
        XCTAssertTrue(lilith.waitForExistence(timeout: 3)); lilith.tap()
        XCTAssertTrue(app.buttons["Create Nyx"].isHittable)
        let ai = app.buttons["artist.open.pixel"]
        for _ in 0..<4 where !ai.isHittable { app.swipeUp() }
        ai.tap()
        XCTAssertTrue(app.staticTexts["AI atelier"].waitForExistence(timeout: 3))
        XCTAssertFalse(app.buttons["artist.generate"].isEnabled)
        app.buttons["Done"].tap()
        XCTAssertTrue(app.buttons["Create Nyx"].isHittable)
    }

    @MainActor
    func testPreviewAppearsWithoutWaitingForAnAIRequest() {
        let app = XCUIApplication()
        app.launchArguments = ["--demo", "--light", "--no-splash"]
        app.launch()
        XCTAssertTrue(app.buttons["New Pixel"].waitForExistence(timeout: 15))
        app.buttons["New Pixel"].tap()
        app.buttons["Security"].tap()
        app.buttons["Preview Pixel"].tap()
        XCTAssertTrue(app.staticTexts["Local template"].waitForExistence(timeout: 2))
        XCTAssertTrue(app.buttons["Create Nyx"].isHittable)
        XCTAssertFalse(app.staticTexts["AI persona"].exists)
    }

    @MainActor
    func testEnginesKeepTheStandardNavigationAndHideAdvancedDsh() {
        let app = XCUIApplication()
        app.launchArguments = ["--demo", "--light", "--no-splash", "--tab", "local"]
        app.launch()
        XCTAssertTrue(app.staticTexts["On device"].waitForExistence(timeout: 15))
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
