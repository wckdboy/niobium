#!/usr/bin/env python3
"""Run the opt-in native DSH smoke test against a signed full iOS app."""
import argparse
from datetime import datetime, timezone
from pathlib import Path
import plistlib
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def run(*args):
    subprocess.run([str(arg) for arg in args], cwd=ROOT, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", required=True, help="Connected iPhone/iPad UDID")
    parser.add_argument("--app", required=True, type=Path, help="Development-signed full Niobium.app")
    parser.add_argument("--derived-data", type=Path, default=ROOT / "dist/iphone-acceptance")
    parser.add_argument("--result-bundle", type=Path)
    args = parser.parse_args()
    app = args.app.resolve()
    info = plistlib.loads((app / "Info.plist").read_bytes())
    if info.get("CFBundleIdentifier") != "ai.wckd.niobium" or not list(app.rglob("niobium-agent.cjs")):
        parser.error("--app must be the signed full Niobium build with the native DSH adapter")
    run("codesign", "--verify", "--deep", "--strict", app)
    run("xcodegen", "generate", "--spec", "ios/project.yml", "--project", "ios")
    derived = args.derived_data.resolve()
    run("xcodebuild", "-project", "ios/Niobium.xcodeproj", "-scheme", "Niobium",
        "-configuration", "Debug", "-destination", "platform=iOS,id=" + args.device,
        "-derivedDataPath", derived, "-only-testing:NiobiumUITests/LocalDSHDeviceUITests",
        "-allowProvisioningUpdates", "build-for-testing")

    # Apple's xctestrun format explicitly supports editing UITargetAppPath.
    # Build the UI runner from the lightweight project, then target the real
    # signed full app. Never install the client-only build as the test subject.
    products = derived / "Build/Products"
    original = max(products.glob("Niobium_iphoneos*.xctestrun"), key=lambda p: p.stat().st_mtime)
    manifest = plistlib.loads(original.read_bytes())

    def configure(target):
        target["UITargetAppPath"] = str(app)
        target["DependentProductPaths"] = [str(app)] + [
            p for p in target.get("DependentProductPaths", []) if "NiobiumUITests-Runner.app" in p
        ]
        target.setdefault("EnvironmentVariables", {})["NIOBIUM_DEVICE_DSH_TEST"] = "1"
        target["OnlyTestIdentifiers"] = ["LocalDSHDeviceUITests"]
        return target

    if "TestConfigurations" in manifest:
        for config in manifest["TestConfigurations"]:
            config["TestTargets"] = [configure(t) for t in config["TestTargets"]
                                     if t.get("BlueprintName") == "NiobiumUITests"]
        targets = [t for c in manifest["TestConfigurations"] for t in c["TestTargets"]]
    else:
        target = configure(manifest["NiobiumUITests"])
        manifest = {"NiobiumUITests": target, "__xctestrun_metadata__": manifest["__xctestrun_metadata__"]}
        targets = [target]
    if not targets:
        raise ValueError("No native UI test target found")
    prepared = products / "NiobiumPhysicalDSH.xctestrun"
    prepared.write_bytes(plistlib.dumps(manifest))
    result = args.result_bundle or ROOT / "dist" / ("NiobiumDevice-" + datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S") + ".xcresult")
    run("xcodebuild", "test-without-building", "-xctestrun", prepared,
        "-destination", "platform=iOS,id=" + args.device, "-parallel-testing-enabled", "NO",
        "-test-timeouts-enabled", "YES", "-maximum-test-execution-time-allowance", "420",
        "-resultBundlePath", result.resolve())
    print("Physical DSH acceptance results:", result.resolve())


if __name__ == "__main__":
    main()
