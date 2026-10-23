#!/usr/bin/env python3
"""Real native API acceptance using an isolated keychain/private D-Bus service."""
from pathlib import Path
import hashlib
import importlib.util
import json
import os
import platform
import shlex
import subprocess
import sys
import tempfile
import time

ROOT = Path(__file__).resolve().parents[1]
MAC = platform.system() == "Darwin"
MASTER = b"fixture-niobium-vault-master-only"
FIRST = b"fixture-first-account-private-only"
SECOND = b"fixture-second-account-private-only"


def main():
    with tempfile.TemporaryDirectory(prefix="niobium-vault-test-") as directory:
        temporary = Path(directory)
        if not MAC and "--inside-test-bus" not in sys.argv:
            data = temporary / "data"; runtime = temporary / "run"
            data.mkdir(mode=0o700); runtime.mkdir(mode=0o700)
            env = dict(os.environ, XDG_DATA_HOME=str(data), XDG_RUNTIME_DIR=str(runtime))
            env.pop("GNOME_KEYRING_CONTROL", None)
            subprocess.run(["dbus-run-session", "--", sys.executable, __file__, "--inside-test-bus"], env=env, check=True, timeout=120)
            return
        native = temporary / "niobium-vault"
        spec = importlib.util.spec_from_file_location("vault_build", ROOT / "tools/build-desktop-vault.py")
        builder = importlib.util.module_from_spec(spec); spec.loader.exec_module(builder); builder.build(native)
        flags = ["cc", str(ROOT / "tests/fixtures/vault-native-test.c"), "-O2", "-Wall", "-Wextra", "-Werror", "-Wno-deprecated-declarations"]
        if MAC: flags += ["-mmacosx-version-min=13.5", "-framework", "Security", "-framework", "CoreFoundation"]
        else: flags += shlex.split(subprocess.check_output(["pkg-config", "--cflags", "--libs", "libsecret-1"], text=True))
        control = temporary / "vault-test-control"
        subprocess.run(flags + ["-o", str(control)], check=True)
        chain = temporary / "isolated.keychain"
        checks = []
        def call(op, kind=None, identity=None, secret=None, expected=0):
            args = [str(control), op, str(chain)] if MAC else [str(native), op]
            if kind is not None: args += [kind, identity]
            if op == "put": args += ["Niobium isolated fixture"]
            r = subprocess.run(args, input=secret, capture_output=True, timeout=20)
            assert r.returncode == expected, f"Native vault {op} returned {r.returncode}, expected {expected}"
            assert not r.stderr, "Native helper exposed unexpected diagnostics"
            checks.append(op)
            return r.stdout
        daemon = None; before = None
        try:
            if MAC:
                before = subprocess.check_output(["/usr/bin/security", "list-keychains", "-d", "user"])
                call("create", secret=MASTER)
            else:
                control_directory = temporary / "control"; control_directory.mkdir(mode=0o700)
                daemon = subprocess.Popen(["gnome-keyring-daemon", "--foreground", "--components=secrets", "--control-directory=" + str(control_directory), "--unlock"], stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                daemon.stdin.write(MASTER); daemon.stdin.close()
                for _ in range(100):
                    r = subprocess.run([str(native), "status"], capture_output=True, timeout=2)
                    if r.returncode == 0: break
                    time.sleep(0.1)
                else: raise AssertionError("Isolated Secret Service did not initialize")
            call("status")
            one = hashlib.sha256(b"one").hexdigest(); two = hashlib.sha256(b"two").hexdigest()
            call("get", "gateway", one, expected=10)
            call("put", "gateway", one, FIRST)
            assert call("get", "gateway", one) == FIRST
            call("put", "gateway", two, SECOND)
            assert call("get", "gateway", two) == SECOND
            call("put", "images", one, SECOND)
            assert call("get", "images", one) == SECOND
            assert call("get", "gateway", one) == FIRST
            if MAC: call("lock")
            else: subprocess.run([str(control), "lock", "isolated"], check=True, capture_output=True, timeout=10)
            call("status", expected=11)
            call("get", "gateway", one, expected=11)
            call("put", "gateway", one, SECOND, expected=11)
            call("delete", "gateway", one, expected=11)
            if MAC: call("unlock", secret=MASTER)
            else:
                subprocess.run(["gnome-keyring-daemon", "--control-directory=" + str(control_directory), "--unlock"], input=MASTER, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=True, timeout=10)
            assert call("get", "gateway", one) == FIRST
            call("put", "gateway", one, SECOND)
            assert call("get", "gateway", one) == SECOND
            call("delete", "gateway", one)
            call("delete", "gateway", one)
            call("get", "gateway", one, expected=10)
            assert call("get", "gateway", two) == SECOND
            assert call("get", "images", one) == SECOND
            call("delete", "gateway", two); call("delete", "images", one)
            invalid = subprocess.run([str(native), "get", "gateway", "invalid-id"], capture_output=True)
            assert invalid.returncode == 14 and not invalid.stdout and not invalid.stderr
        finally:
            if MAC:
                call("destroy")
                after = subprocess.check_output(["/usr/bin/security", "list-keychains", "-d", "user"])
                assert before == after, "Native test changed the user's keychain search list"
            elif daemon:
                daemon.terminate()
                try: daemon.wait(timeout=5)
                except subprocess.TimeoutExpired: daemon.kill(); daemon.wait(timeout=5)
        evidence = {"platform": "macos-keychain" if MAC else "linux-secret-service", "native_operations": len(checks),
                    "isolated_vault": True, "restart_persistence": True, "account_and_kind_isolation": True,
                    "locked_reads_writes_deletes_rejected": True, "idempotent_delete": True, "invalid_requests_rejected": True}
        output = ROOT / "desktop/build/evidence/native-vault.json"; output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(evidence, indent=2) + "\n")
        print(json.dumps(evidence))


if __name__ == "__main__":
    main()
