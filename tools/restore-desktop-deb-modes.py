#!/usr/bin/env python3
"""Restore jpackage resource modes inside the generated, unpublished DEB."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import uuid

directory = Path(sys.argv[1]).resolve()
prefix = sys.argv[2]
packages = [p for p in directory.glob("*.deb") if p.name.startswith(prefix)]
if len(packages) != 1:
    raise SystemExit("Expected one current desktop DEB to restore.")
package = packages[0]
output = directory / (".niobium-fixed-" + uuid.uuid4().hex + ".deb")
try:
    with tempfile.TemporaryDirectory(prefix="niobium-deb-modes-") as temporary:
        payload = Path(temporary) / "package"
        payload.mkdir(mode=0o755)
        subprocess.run(["dpkg-deb", "--raw-extract", str(package), str(payload)], check=True, timeout=60)
        manifests = list(payload.rglob("runtime-manifest.json"))
        if len(manifests) != 1:
            raise SystemExit("Expected one private runtime inside the DEB.")
        resources = manifests[0].parent.resolve()
        manifest = json.loads(manifests[0].read_text())
        # The native Secret Service bridge links to the distribution's libsecret.
        # Compose's Linux DSL does not expose jpackage's dependency flag.
        if "vault_source_sha256" in manifest:
            control = payload / "DEBIAN/control"
            lines = control.read_text().splitlines()
            dependencies = next((n for n, line in enumerate(lines) if line.startswith("Depends:")), None)
            if dependencies is None:
                lines.append("Depends: libsecret-1-0")
            elif not any(part.strip().split()[0] == "libsecret-1-0" for part in lines[dependencies].split(":", 1)[1].split(",") if part.strip()):
                lines[dependencies] += ", libsecret-1-0"
            control.write_text("\n".join(lines) + "\n")
        for relative, mode in manifest["executables"].items():
            target = (resources / relative).resolve()
            if not target.is_relative_to(resources) or not target.is_file() or not isinstance(mode, int) or mode & ~0o777:
                raise SystemExit("Invalid packaged executable entry.")
            os.chmod(target, mode)
        os.chmod(payload, 0o755)
        subprocess.run(["dpkg-deb", "--build", "--root-owner-group", "-Zgzip", "-z6", str(payload), str(output)], check=True, timeout=180)
        output.replace(package)
        print("Restored desktop executable modes in " + package.name)
finally:
    output.unlink(missing_ok=True)
