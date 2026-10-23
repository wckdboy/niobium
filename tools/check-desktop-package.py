#!/usr/bin/env python3
"""Verify actual packaged resources and run the installed native launcher."""
import hashlib
import json
import os
import plistlib
import re
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(sys.argv[1]).resolve()
if "--installers" in sys.argv:
    packages = list(root.glob("dmg/*.dmg")) + list(root.glob("deb/*.deb"))
    if not packages:
        raise SystemExit("No native desktop installer was built.")
    for package in packages:
        subprocess.run([sys.executable, __file__, str(package)], check=True)
    raise SystemExit(0)
if root.is_file():
    with tempfile.TemporaryDirectory(prefix="niobium-installer-check-") as directory:
        mounted = False
        try:
            if root.suffix == ".deb":
                subprocess.run(["dpkg-deb", "--extract", str(root), directory], check=True, timeout=60)
            elif root.suffix == ".dmg":
                subprocess.run(["hdiutil", "attach", "-readonly", "-nobrowse", "-mountpoint", directory, str(root)], check=True, capture_output=True, timeout=60)
                mounted = True
            else:
                raise SystemExit("Unsupported desktop installer.")
            subprocess.run([sys.executable, __file__, directory], check=True)
        finally:
            if mounted:
                subprocess.run(["hdiutil", "detach", "-force", directory], check=True, capture_output=True, timeout=30)
    raise SystemExit(0)
launchers = [p for p in root.rglob("*") if p.name in ("Niobium", "niobium") and p.parent.name in ("bin", "MacOS") and p.is_file()]
if len(launchers) != 1:
    raise SystemExit("Expected exactly one packaged Niobium launcher.")
manifests = list(root.rglob("runtime-manifest.json"))
if len(manifests) != 1:
    raise SystemExit("Expected one private DSH runtime manifest.")
manifest = json.loads(manifests[0].read_text())
resources = manifests[0].parent
if manifest["platform"].startswith("darwin-"):
    info = plistlib.loads((launchers[0].parents[1] / "Info.plist").read_bytes())
    commands = subprocess.run(["otool", "-l", str(resources / "node")], capture_output=True, text=True, check=True, timeout=15).stdout
    minimum = re.search(r"^\s+minos (\d+(?:\.\d+)+)", commands, re.MULTILINE)
    def version(value):
        parts = tuple(map(int, value.split(".")))
        return parts + (0,) * (3 - len(parts))
    if minimum is None or version(info["LSMinimumSystemVersion"]) < version(minimum[1]):
        raise SystemExit("The macOS app minimum is older than its bundled Node runtime.")
def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
for name, expected in manifest["files"].items():
    if digest(resources / name) != expected:
        raise SystemExit("Packaged runtime checksum mismatch: " + name)
source = Path(__file__).resolve().parents[1]
for relative in ["desktop/runtime/desktop-host.cjs", "runtime/niobium-agent.cjs", "runtime/niobium-messaging.cjs", "android/runtime/niobium-local.cjs"]:
    origin = source / relative
    if digest(resources / origin.name) != digest(origin):
        raise SystemExit("Packaged runtime is stale: " + origin.name)
if digest(resources / "package-lock.json") != digest(source / "desktop/runtime/package-lock.json"):
    raise SystemExit("Packaged DSH dependencies are stale.")
if digest(resources / "node") != manifest["node_sha256"] or digest(resources / "package-lock.json") != manifest["lock_sha256"]:
    raise SystemExit("Packaged Node/DSH lock integrity check failed.")
for relative, mode in manifest["executables"].items():
    executable = resources / relative
    if executable.stat().st_mode & 0o777 != mode or not os.access(executable, os.X_OK):
        raise SystemExit("Packaged executable permissions were lost: " + relative)
env = dict(os.environ, PATH="/usr/bin:/bin")
result = subprocess.run([str(launchers[0]), "--check-runtime"], cwd="/tmp", env=env,
                        capture_output=True, text=True, timeout=90)
if result.returncode != 0 or "NIOBIUM_RUNTIME_OK" not in result.stdout:
    raise SystemExit("Installed runtime check failed:\n" + result.stdout[-2000:] + result.stderr[-2000:])
print(json.dumps({"platform": manifest["platform"], "node": manifest["node"], "dsh": manifest["dsh"],
                  "installed_launcher": str(launchers[0].relative_to(root)), "runtime_integrity": True,
                  "native_provider_check": True, "global_java_node_required": False}, indent=2))
