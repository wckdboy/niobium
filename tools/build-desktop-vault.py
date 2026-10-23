#!/usr/bin/env python3
"""Compile the small native OS vault helper for the current desktop platform."""
from pathlib import Path
import platform
import shlex
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def build(output: Path):
    output.parent.mkdir(parents=True, exist_ok=True)
    command = ["cc", "-std=c11", "-O2", "-Wall", "-Wextra", "-Werror", "-Wno-deprecated-declarations"]
    if platform.system() == "Darwin":
        command += ["-mmacosx-version-min=13.5", "-framework", "Security", "-framework", "CoreFoundation"]
    elif platform.system() == "Linux":
        command += shlex.split(subprocess.check_output(["pkg-config", "--cflags", "--libs", "libsecret-1"], text=True))
    else:
        raise SystemExit("The native vault supports Linux and macOS.")
    # Keep libraries after the translation unit for Linux's --as-needed linker.
    subprocess.run(command[:1] + [str(ROOT / "desktop/native/vault.c")] + command[1:] + ["-o", str(output)], check=True)
    output.chmod(0o755)


if __name__ == "__main__":
    build(Path(sys.argv[1]))
