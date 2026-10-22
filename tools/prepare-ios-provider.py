#!/usr/bin/env python3
"""Add Niobium's native DSH adapter to the existing iSH boot overlay."""
import os
from pathlib import Path
import plistlib
import shutil

root = Path(__file__).resolve().parents[1]
bundle = Path(os.environ['BUILT_PRODUCTS_DIR']) / os.environ['CONTENTS_FOLDER_PATH'] / 'RootfsPatch.bundle'
manifest = plistlib.loads((bundle / 'manifest.plist').read_bytes())
target = bundle / 'files/lib/niobium-agent.cjs'
target.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(root / 'runtime/niobium-agent.cjs', target)
manifest['files'] = [entry for entry in manifest['files'] if entry['dst'] != '/usr/local/lib/niobium-agent.cjs']
manifest['files'].append({'src': 'files/lib/niobium-agent.cjs', 'dst': '/usr/local/lib/niobium-agent.cjs'})
# The upstream overlay uses version 4. Niobium owns its subsequent versions;
# every app build reapplies the small adapter without replacing user data.
manifest['version'] = 100000 + int(os.environ['CURRENT_PROJECT_VERSION'])
(bundle / 'manifest.plist').write_bytes(plistlib.dumps(manifest))
