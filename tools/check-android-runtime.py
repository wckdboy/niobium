#!/usr/bin/env python3
"""Verify the runtime as actually packaged by Gradle, including ELF alignment."""
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import zipfile

spec = importlib.util.spec_from_file_location("prepare_runtime", Path(__file__).with_name("prepare-android-runtime.py"))
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)

with zipfile.ZipFile(sys.argv[1]) as archive, tempfile.TemporaryDirectory() as temporary:
    manifest = json.loads(archive.read("assets/niobium-runtime.json"))
    for name, expected in manifest["native_sha256"].items():
        raw = archive.read("lib/arm64-v8a/" + name)
        if hashlib.sha256(raw).hexdigest() != expected:
            raise ValueError("Packaged native checksum mismatch: " + name)
        path = Path(temporary) / name
        path.write_bytes(raw)
        prepare.elf_16k(path)
    image = archive.read("assets/niobium-runtime.image")
    if hashlib.sha256(image).hexdigest() != manifest["rootfs"]["sha256"]:
        raise ValueError("Packaged Linux image checksum mismatch")
    archive.getinfo("assets/niobium-local.cjs")
    provider = archive.read("assets/niobium-agent.cjs")
    expected_provider = hashlib.sha256((prepare.ROOT / "runtime/niobium-agent.cjs").read_bytes()).hexdigest()
    if hashlib.sha256(provider).hexdigest() != expected_provider or manifest["provider_sha256"] != expected_provider:
        raise ValueError("Packaged native provider is stale or has a checksum mismatch")
    archive.getinfo("assets/bot_build.md")
    print("PASS: packaged image, all native hashes, ARM64 ABI and 16 KB ELF alignment")
