#!/usr/bin/env python3
"""Run opt-in native DSH acceptance against a full iOS app."""
import argparse
import copy
from datetime import datetime, timezone
from pathlib import Path
import plistlib
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def run(*args):
    subprocess.run([str(arg) for arg in args], cwd=ROOT, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", required=True, help="Connected iPhone/iPad or simulator UDID")
    parser.add_argument("--simulator", action="store_true", help="Use an iOS simulator and a matching full simulator app")
    parser.add_argument("--app", type=Path, help="Development-signed full Niobium.app")
    parser.add_argument("--model-app", type=Path, help="Isolated full Debug app, bundle ai.wckd.niobium.acceptance")
    parser.add_argument("--only-model", action="store_true", help="Run only the controlled model/history test")
    parser.add_argument("--derived-data", type=Path, default=ROOT / "dist/iphone-acceptance")
    parser.add_argument("--result-bundle", type=Path)
    args = parser.parse_args()
    if not args.app and not args.only_model:
        parser.error("--app is required for the standard device smoke test")
    if args.only_model and not args.model_app:
        parser.error("--only-model requires --model-app")
    subjects = []
    if not args.only_model:
        subjects.append((args.app.resolve(), "ai.wckd.niobium", "LocalDSHDeviceUITests", "NIOBIUM_DEVICE_DSH_TEST"))
    if args.model_app:
        subjects.append((args.model_app.resolve(), "ai.wckd.niobium.acceptance", "LocalDSHModelDeviceUITests", "NIOBIUM_DEVICE_MODEL_TEST"))
    for app, bundle, _, _ in subjects:
        info = plistlib.loads((app / "Info.plist").read_bytes())
        if info.get("CFBundleIdentifier") != bundle or not list(app.rglob("niobium-agent.cjs")):
            parser.error("Test app has the wrong bundle identity or lacks the native DSH adapter")
        if not args.simulator:
            run("codesign", "--verify", "--deep", "--strict", app)
    run("xcodegen", "generate", "--spec", "ios/project.yml", "--project", "ios")
    derived = args.derived_data.resolve()
    destination = "platform=" + ("iOS Simulator" if args.simulator else "iOS") + ",id=" + args.device
    run("xcodebuild", "-project", "ios/Niobium.xcodeproj", "-scheme", "Niobium",
        "-configuration", "Debug", "-destination", destination,
        "-derivedDataPath", derived, "-only-testing:NiobiumUITests/LocalDSHDeviceUITests",
        "-allowProvisioningUpdates", "build-for-testing")

    # Apple's xctestrun format explicitly supports editing UITargetAppPath.
    # Build the UI runner from the lightweight project, then target the real
    # signed full app. Never install the client-only build as the test subject.
    products = derived / "Build/Products"
    platform = "iphonesimulator" if args.simulator else "iphoneos"
    original = max(products.glob("Niobium_" + platform + "*.xctestrun"), key=lambda p: p.stat().st_mtime)
    manifest = plistlib.loads(original.read_bytes())

    def configure(target, app, bundle, test_class, flag):
        target["UITargetAppPath"] = str(app)
        target["UITargetAppBundleIdentifier"] = bundle
        target["DependentProductPaths"] = [str(app)] + [
            p for p in target.get("DependentProductPaths", []) if "NiobiumUITests-Runner.app" in p
        ]
        target.setdefault("EnvironmentVariables", {})[flag] = "1"
        target["OnlyTestIdentifiers"] = [test_class]
        return target

    for index, (app, bundle, test_class, flag) in enumerate(subjects):
        selected = copy.deepcopy(manifest)
        if "TestConfigurations" in selected:
            for config in selected["TestConfigurations"]:
                config["TestTargets"] = [configure(t, app, bundle, test_class, flag) for t in config["TestTargets"]
                                         if t.get("BlueprintName") == "NiobiumUITests"]
            targets = [t for c in selected["TestConfigurations"] for t in c["TestTargets"]]
        else:
            target = configure(selected["NiobiumUITests"], app, bundle, test_class, flag)
            selected = {"NiobiumUITests": target, "__xctestrun_metadata__": selected["__xctestrun_metadata__"]}
            targets = [target]
        if not targets:
            raise ValueError("No native UI test target found")
        prepared = products / (test_class + ".xctestrun")
        prepared.write_bytes(plistlib.dumps(selected))
        result = args.result_bundle or ROOT / "dist" / ("NiobiumDevice-" + datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S") + ".xcresult")
        if index:
            result = result.with_name(result.stem + "-model.xcresult")
        run("xcodebuild", "test-without-building", "-xctestrun", prepared,
            "-destination", destination, "-parallel-testing-enabled", "NO",
            "-test-timeouts-enabled", "YES", "-maximum-test-execution-time-allowance", "720",
            "-resultBundlePath", result.resolve())
        print("DSH acceptance results:", result.resolve())


if __name__ == "__main__":
    main()
