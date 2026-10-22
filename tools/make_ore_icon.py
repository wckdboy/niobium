#!/usr/bin/env python3
"""Compatibility entry point: all current Niobium marks share the lowercase n."""
from pathlib import Path
import runpy
runpy.run_path(str(Path(__file__).with_name("make_n_icon.py")), run_name="__main__")
