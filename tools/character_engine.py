#!/usr/bin/env python3
"""Compatibility entry point for Niobium's supported 48px character catalog.

The obsolete 32px writer was removed because it could overwrite both native
atlases with an incompatible schema. Keep older commands safe.
"""
from character_engine48 import main

if __name__ == "__main__":
    main()
