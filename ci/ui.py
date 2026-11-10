"""Tiny uiautomator helper for the emulator smoke test.

python3 ci/ui.py tap "<text>"          tap the first node whose text or description matches
python3 ci/ui.py switch "<row title>"  tap the switch on the same row as a title
python3 ci/ui.py all_creatures OUTDIR  pick every creature and environment pill, screenshot each
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def sh(*a):
    return subprocess.run(["adb", "shell", *a], capture_output=True, text=True).stdout


def dump():
    sh("uiautomator", "dump", "/sdcard/ui.xml")
    xml = subprocess.run(["adb", "exec-out", "cat", "/sdcard/ui.xml"], capture_output=True, text=True).stdout
    try:
        return ET.fromstring(xml)
    except ET.ParseError:
        return None


def bounds(n):
    m = re.findall(r"\d+", n.get("bounds", ""))
    if len(m) != 4:
        return None
    x1, y1, x2, y2 = map(int, m)
    return x1, y1, x2, y2


def label(n):
    return (n.get("text") or n.get("content-desc") or "").strip()


def find(root, text):
    for n in root.iter("node"):
        if label(n) == text:
            return n
    for n in root.iter("node"):
        if label(n).startswith(text):
            return n
    return None


def tap_node(n):
    b = bounds(n)
    if not b:
        return False
    sh("input", "tap", str((b[0] + b[2]) // 2), str((b[1] + b[3]) // 2))
    return True


def tap(text):
    root = dump()
    if root is None:
        print("no ui dump"); return False
    n = find(root, text)
    if n is None:
        print("not found:", text); return False
    return tap_node(n)


def switch(title):
    root = dump()
    if root is None:
        return False
    t = find(root, title)
    if t is None:
        print("not found:", title); return False
    tb = bounds(t)
    best, bd = None, 1e9
    for n in root.iter("node"):
        if n.get("checkable") == "true" or "Switch" in (n.get("class") or ""):
            b = bounds(n)
            if b:
                d = abs((b[1] + b[3]) / 2 - (tb[1] + tb[3]) / 2)
                if d < bd:
                    best, bd = n, d
    if best is not None and bd < 200:
        return tap_node(best)
    return tap_node(t)


def all_creatures(out):
    names = ["Mochi", "Kalboob", "Kitty", "Kiwi", "Just the sky"]
    i = 0
    for name in names:
        if not tap(name):
            continue
        time.sleep(3)
        root = dump()
        envs = []
        if root is not None:
            for n in root.iter("node"):
                if (n.get("content-desc") or "").startswith("Environment: "):
                    envs.append(n.get("content-desc"))
        for e in envs or [None]:
            if e:
                tap(e)
                time.sleep(3)
            subprocess.run(f"adb exec-out screencap -p > {out}/1{i:02d}_{name.replace(' ', '_')}.png", shell=True)
            i += 1


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "tap":
        tap(sys.argv[2])
    elif cmd == "switch":
        switch(sys.argv[2])
    elif cmd == "all_creatures":
        all_creatures(sys.argv[2])
