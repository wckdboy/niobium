#!/usr/bin/env python3
"""Build the current host's private DSH bundle; never uses a global npm install."""
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]


def main():
    config = json.loads((ROOT / "config/desktop-runtime.json").read_text())
    system = {"Darwin": "darwin", "Linux": "linux"}.get(platform.system())
    arch = {"arm64": "arm64", "aarch64": "arm64", "x86_64": "x64", "AMD64": "x64"}.get(platform.machine())
    target = f"{system}-{arch}"
    if target not in config["archives"]:
        raise SystemExit(f"Unsupported desktop host: {target}")
    cache = ROOT / "desktop/build/runtime-cache"
    cache.mkdir(parents=True, exist_ok=True)
    stem = f"node-v{config['node_version']}-{target}"
    archive = cache / f"{stem}.tar.xz"
    expected = config["archives"][target]
    if not archive.exists() or hashlib.sha256(archive.read_bytes()).hexdigest() != expected:
        stage = archive.with_suffix(".download")
        with urllib.request.urlopen(f"https://nodejs.org/dist/v{config['node_version']}/{archive.name}", timeout=60) as source, stage.open("wb") as out:
            shutil.copyfileobj(source, out)
        if hashlib.sha256(stage.read_bytes()).hexdigest() != expected:
            stage.unlink()
            raise SystemExit("Official Node archive failed its pinned SHA-256 check.")
        stage.replace(archive)
    extracted = cache / stem
    if not (extracted / "bin/node").is_file():
        with tarfile.open(archive) as source:
            source.extractall(cache, filter="data")
    runtime = ROOT / "desktop/runtime"
    lock = json.loads((runtime / "package-lock.json").read_text())
    family = {key: value["version"] for key, value in lock["packages"].items()
              if key.startswith("node_modules/@deepseek-ai/dsh") and key.count("node_modules/") == 1}
    if not family or any(version != config["dsh_version"] for version in family.values()):
        raise SystemExit("The DSH lock includes an unreviewed component version.")
    env = dict(os.environ, PATH=str(extracted / "bin") + os.pathsep + os.environ.get("PATH", ""))
    subprocess.run([str(extracted / "bin/node"), str(extracted / "lib/node_modules/npm/bin/npm-cli.js"),
                    "ci", "--omit=dev", "--ignore-scripts", "--no-audit", "--no-fund"], cwd=runtime, env=env, check=True)
    # This pinned upstream script only restores spawn-helper's executable bit.
    subprocess.run([str(extracted / "bin/node"), "node_modules/@deepseek-ai/dsh-subprocess-local/scripts/ensure-spawn-helper.mjs"], cwd=runtime, env=env, check=True)
    bundle = runtime / "bundle" / target.replace("darwin", "macos")
    stage = bundle.with_name(bundle.name + ".staging")
    shutil.rmtree(stage, ignore_errors=True)
    stage.mkdir(parents=True)
    try:
        shutil.copy2(extracted / "bin/node", stage / "node")
        shutil.copy2(extracted / "LICENSE", stage / "NODE-LICENSE")
        shutil.copytree(runtime / "node_modules", stage / "node_modules", symlinks=True)
        for origin in [runtime / "desktop-host.cjs", ROOT / "runtime/niobium-agent.cjs",
                       ROOT / "runtime/niobium-messaging.cjs", ROOT / "android/runtime/niobium-local.cjs"]:
            shutil.copy2(origin, stage / origin.name)
        for name in ["LICENSE", "NOTICE"]:
            shutil.copy2(ROOT / name, stage / name)
        for name in ["package.json", "package-lock.json"]:
            shutil.copy2(runtime / name, stage / name)
        subprocess.run([sys.executable, str(ROOT / "tools/build-desktop-vault.py"), str(stage / "niobium-vault")], check=True)
        subprocess.run([str(stage / "node"), "-e", "require('./node_modules/ws'); require('koffi'); const p=require('node-pty').spawn('/bin/sh',['-c','printf niobium-native-ok'],{cols:80,rows:24}); let out=''; p.onData(s=>out+=s); p.onExit(e=>{if(e.exitCode || !out.includes('niobium-native-ok')) process.exitCode=1}); const t=setTimeout(()=>{p.kill();process.exit(1)},10000); t.unref();"], cwd=stage, env=env, check=True, timeout=20)
        manifest = {"platform": target, "node": config["node_version"], "dsh": config["dsh_version"],
                    "node_archive_sha256": expected,
                    "files": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in stage.glob("*.cjs")},
                    "lock_sha256": hashlib.sha256((stage / "package-lock.json").read_bytes()).hexdigest()}
        manifest["node_sha256"] = hashlib.sha256((stage / "node").read_bytes()).hexdigest()
        manifest["files"]["niobium-vault"] = hashlib.sha256((stage / "niobium-vault").read_bytes()).hexdigest()
        manifest["vault_source_sha256"] = hashlib.sha256((ROOT / "desktop/native/vault.c").read_bytes()).hexdigest()
        manifest["executables"] = {str(p.relative_to(stage)): p.stat().st_mode & 0o777
                                   for p in stage.rglob("*") if p.is_file() and p.stat().st_mode & 0o111}
        (stage / "runtime-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
        shutil.rmtree(bundle, ignore_errors=True)
        stage.replace(bundle)
    finally:
        shutil.rmtree(stage, ignore_errors=True)
    print(f"Desktop DSH bundle ready: {bundle.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
