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


# 1. Split APK support.
# VelvetBox stores an app's split APKs (screen density, language, features) in
# <engine app dir>/<package>/splits/<splitName>.apk. Tell Android about them in
# the ApplicationInfo, so the sandboxed app loads their resources/code the normal way.
patch(
    "Bcore/src/main/java/top/niunaijun/blackbox/core/system/pm/PackageManagerCompat.java",
    """        ai.publicSourceDir = sourceDir;
        ai.sourceDir = sourceDir;
""",
    """        ai.publicSourceDir = sourceDir;
        ai.sourceDir = sourceDir;
        // VelvetBox: split APK support
        try {
            java.io.File splitDir = new java.io.File(BEnvironment.getAppDir(ai.packageName), "splits");
            java.io.File[] splitFiles = splitDir.listFiles();
            if (splitFiles != null) {
                java.util.List<java.io.File> apks = new java.util.ArrayList<>();
                for (java.io.File f : splitFiles) {
                    if (f.isFile() && f.getName().endsWith(".apk")) apks.add(f);
                }
                if (!apks.isEmpty()) {
                    java.util.Collections.sort(apks, (a, b) -> a.getName().compareTo(b.getName()));
                    String[] names = new String[apks.size()];
                    String[] paths = new String[apks.size()];
                    for (int i = 0; i < apks.size(); i++) {
                        String n = apks.get(i).getName();
                        names[i] = n.substring(0, n.length() - 4);
                        paths[i] = apks.get(i).getAbsolutePath();
                    }
                    ai.splitNames = names;
                    ai.splitSourceDirs = paths;
                    ai.splitPublicSourceDirs = paths;
                }
            }
        } catch (Throwable ignored) {
        }
""",
    "split APK support",
)

# 2. Turn off code shrinking for the engine (it breaks its reflection tricks).
patch(
    "Bcore/build.gradle",
    "minifyEnabled true",
    "minifyEnabled false",
    "no engine shrinking",
)
