#!/usr/bin/env python3
"""
VelvetBox patches for the NewBlackbox sandbox engine, applied by the GitHub
build after the engine is downloaded. Each patch fails loudly if the engine
code it expects has changed.

Usage: python3 engine-patches/apply_patches.py engine/NewBlackbox
"""
import sys
from pathlib import Path

root = Path(sys.argv[1])


def patch(rel_path, old, new, name):
    p = root / rel_path
    s = p.read_text(encoding="utf-8")
    if s.count(old) != 1:
        sys.exit(f"Patch '{name}' failed: expected code not found exactly once in {rel_path}")
    p.write_text(s.replace(old, new), encoding="utf-8")
    print(f"Applied patch: {name}")


# 1. Show sandboxed apps in Recent apps with their real name,
#    without the engine's "[B0]" (sandbox user number) tag in front.
patch(
    "Bcore/src/main/java/top/niunaijun/blackbox/utils/compat/TaskDescriptionCompat.java",
    """        return String.format(Locale.CHINA, "[B%d]%s", userId, label);""",
    """        return label == null ? "" : label.toString();""",
    "no [B0] tag in Recent apps",
)

# 2. Turn off code shrinking for the engine (it breaks its reflection tricks).
patch(
    "Bcore/build.gradle",
    "minifyEnabled true",
    "minifyEnabled false",
    "no engine shrinking",
)
