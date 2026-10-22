#!/usr/bin/env python3
"""Prepare the pinned Android runtime; download data, never execute vendor scripts."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
LOCK = ROOT / "config/android-runtime.json"

def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()

def fetch(url, sha, path):
    if path.is_file() and digest(path) == sha:
        return path
    path.parent.mkdir(parents=True, exist_ok=True)
    pending = path.with_suffix(path.suffix + ".pending")
    try:
        with urllib.request.urlopen(url, timeout=60) as response, pending.open("wb") as target:
            shutil.copyfileobj(response, target)
        if digest(pending) != sha:
            raise ValueError("Runtime checksum mismatch: " + path.name)
        pending.replace(path)
    finally:
        pending.unlink(missing_ok=True)
    return path

def deb_payload(path):
    raw = path.read_bytes()
    if raw[:8] != b"!<arch>\n":
        raise ValueError("Invalid package container")
    at = 8
    while at + 60 <= len(raw):
        header = raw[at:at + 60]
        size = int(header[48:58]); name = header[:16].decode().strip().rstrip("/")
        body = raw[at + 60:at + 60 + size]; at += 60 + size + size % 2
        if name.startswith("data.tar"):
            return tarfile.open(fileobj=io.BytesIO(body))
    raise ValueError("Package has no data archive")

def elf_16k(path):
    raw = path.read_bytes()
    if raw[:5] != b"\x7fELF\x02" or struct.unpack_from("<H", raw, 18)[0] != 183:
        raise ValueError("Expected a 64-bit ARM executable: " + path.name)
    offset = struct.unpack_from("<Q", raw, 32)[0]
    size, count = struct.unpack_from("<HH", raw, 54)
    loads = [struct.unpack_from("<IIQQQQQQ", raw, offset + i * size) for i in range(count)]
    if not any(p[0] == 1 for p in loads) or any(p[7] < 16384 for p in loads if p[0] == 1):
        raise ValueError("Native runtime does not support 16 KB load alignment: " + path.name)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=ROOT / "android/app/build/generated/runtime")
    parser.add_argument("--sources", action="store_true")
    args = parser.parse_args()
    config = json.loads(LOCK.read_text())
    cache = ROOT / "android/build/runtime-downloads"; cache.mkdir(parents=True, exist_ok=True)
    out = args.output
    native = out / "jniLibs/arm64-v8a"; native.mkdir(parents=True, exist_ok=True)
    assets = out / "assets"; assets.mkdir(parents=True, exist_ok=True)
    mapping = {
        "proot": {"usr/bin/proot": "libniobium_proot.so", "usr/libexec/proot/loader": "libniobium_proot_loader.so"},
        "libtalloc": {"usr/lib/libtalloc.so.2.5.0": "libniobium_talloc.so"},
        "libandroid-shmem": {"usr/lib/libandroid-shmem.so": "libniobium_android_shmem.so"},
    }
    prefix = "data/data/com.termux/files/"
    for package in config["packages"]:
        path = fetch(package["url"], package["sha256"], cache / (package["name"] + ".deb"))
        with deb_payload(path) as archive:
            members = {item.name.removeprefix("./"): item for item in archive}
            for source, destination in mapping[package["name"]].items():
                item = members[prefix + source]
                if not item.isfile(): raise ValueError("Unexpected native library type")
                target = native / destination
                with archive.extractfile(item) as stream, target.open("wb") as sink:
                    shutil.copyfileobj(stream, sink)
                elf_16k(target)
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    properties = ROOT / "android/local.properties"
    if not sdk and properties.exists():
        sdk = next(line.split("=", 1)[1] for line in properties.read_text().splitlines() if line.startswith("sdk.dir="))
    if not sdk: raise ValueError("ANDROID_HOME is required to compile the guest supervisor")
    ndk = Path(sdk) / "ndk" / config["ndk_version"]
    host = next((ndk / "toolchains/llvm/prebuilt").iterdir())
    compiler = host / "bin/aarch64-linux-android28-clang"
    supervisor = native / "libniobium_supervisor.so"
    subprocess.run([str(compiler), "-std=c11", "-O2", "-fPIE", "-pie",
                    "-Wl,-z,max-page-size=16384", "-Wl,-z,common-page-size=16384",
                    "-Wl,-z,relro,-z,now", str(ROOT / "android/runtime/supervisor.c"),
                    "-o", str(supervisor)], check=True)
    elf_16k(supervisor)
    for path in native.iterdir(): path.chmod(0o755)
    local = ROOT / "ios/Resources/root.tar.gz"
    source = local if local.is_file() and digest(local) == config["rootfs"]["sha256"] else fetch(
        config["rootfs"]["url"], config["rootfs"]["sha256"], cache / "root.tar.gz")
    target = assets / "niobium-runtime.image"
    if not target.exists() or digest(target) != config["rootfs"]["sha256"]:
        shutil.copy2(source, target)
    if digest(target) != config["rootfs"]["sha256"]: raise ValueError("Guest checksum mismatch")
    metadata = dict(config)
    metadata["native_sha256"] = {p.name: digest(p) for p in sorted(native.iterdir())}
    (assets / "niobium-runtime.json").write_text(json.dumps(metadata, indent=2) + "\n")
    for name in ["niobium-local.cjs"]:
        path = ROOT / "android/runtime" / name
        if path.exists(): shutil.copy2(path, assets / name)
    if args.sources:
        sources = ROOT / "dist/android-runtime-sources"; sources.mkdir(parents=True, exist_ok=True)
        shutil.copy2(LOCK, sources / LOCK.name)
        shutil.copy2(ROOT / "android/runtime/supervisor.c", sources / "supervisor.c")
        for item in config["sources"]:
            suffix = ".zip" if item["url"].endswith(".zip") else ".tar.gz"
            fetch(item["url"], item["sha256"], sources / (item["name"] + suffix))
        # The binary packages are built with these exact recipes and patches.
        # Include every file, rather than providing only an upstream source URL.
        for package in config["packages"]:
            relative = "packages/" + package["name"]
            recipe_dir = sources / "termux-recipes" / package["name"]
            recipe_dir.mkdir(parents=True, exist_ok=True)
            api = "https://api.github.com/repos/termux/termux-packages/contents/" + relative + "?ref=" + config["source_recipes_commit"]
            with urllib.request.urlopen(api, timeout=60) as response:
                entries = json.load(response)
            for entry in entries:
                if entry["type"] != "file":
                    raise ValueError("Unbundled source recipe subdirectory: " + entry["path"])
                destination = recipe_dir / entry["name"]
                with urllib.request.urlopen(entry["download_url"], timeout=60) as response, destination.open("wb") as sink:
                    shutil.copyfileobj(response, sink)
        shutil.copy2(ROOT / "NOTICE", sources / "NOTICE")
        shutil.copy2(ROOT / "tools/prepare-android-runtime.py", sources / "prepare-android-runtime.py")
        (sources / "SHA256SUMS").write_text("".join(
            digest(path) + "  " + str(path.relative_to(sources)) + "\\n"
            for path in sorted(sources.rglob("*")) if path.is_file() and path.name != "SHA256SUMS"))
        print("Pinned corresponding-source archives and build recipes: " + str(sources))
    print("Prepared ARM64 runtime, 16 KB native ELF alignment and verified Alpine/DSH image.")

if __name__ == "__main__":
    main()
