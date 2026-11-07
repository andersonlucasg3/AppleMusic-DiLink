#!/usr/bin/env python3
"""Phase B analysis: compare the library classes bundled by the Apple Music engine with the
ones bundled by the tv-app / car-app builds.

Both APKs are minified, so exact library versions are not stored anywhere in the dex; what
matters for the single-APK idea is the *overlap*: the app dex would be appended after the
engine dex (classes9+), so the engine's copy of a library class always wins at runtime.  A
class present in both APKs but with a different method set is a version skew: calls emitted
by the app against the newer library may not resolve against the engine's older copy.

Usage:
    python dex_dupes.py --engine engine.apk --app tv-app.apk [--app car-app.apk]
"""

import argparse
import hashlib
import json
import re
import struct
import sys
import zipfile

U16 = lambda b, o: struct.unpack_from("<H", b, o)[0]
U32 = lambda b, o: struct.unpack_from("<I", b, o)[0]


def uleb128(buf, pos):
    result = 0
    shift = 0
    while True:
        b = buf[pos]
        pos += 1
        result |= (b & 0x7F) << shift
        if not b & 0x80:
            return result, pos
        shift += 7


class Dex:
    def __init__(self, data):
        self.data = data
        self.string_off = [U32(data, U32(data, 0x3C) + 4 * i) for i in range(U32(data, 0x38))]
        self.type_ids = [U32(data, U32(data, 0x44) + 4 * i) for i in range(U32(data, 0x40))]
        self.proto_off = U32(data, 0x4C)
        self.proto_count = U32(data, 0x48)
        self.method_off = U32(data, 0x5C)
        self.class_count = U32(data, 0x60)
        self.class_off = U32(data, 0x64)
        self._strings = {}

    def string(self, idx):
        s = self._strings.get(idx)
        if s is None:
            off = self.string_off[idx]
            n, p = uleb128(self.data, off)
            end = self.data.index(b"\x00", p)
            raw = self.data[p:end]
            try:
                s = raw.decode("utf-8")
            except UnicodeDecodeError:
                s = raw.decode("utf-8", "replace")
            s = s.replace("\xc0\x80", "\x00")
            self._strings[idx] = s
        return s

    def type_name(self, idx):
        return self.string(self.type_ids[idx])

    def proto_signature(self, idx):
        off = self.proto_off + 12 * idx
        ret = self.type_name(U32(self.data, off + 4))
        params_off = U32(self.data, off + 8)
        params = []
        if params_off:
            size = U32(self.data, params_off)
            for i in range(size):
                params.append(self.type_name(U16(self.data, params_off + 4 + 2 * i)))
        return "(" + "".join(params) + ")" + ret

    def classes(self):
        """Yield (descriptor, set(method signatures))."""
        for i in range(self.class_count):
            base = self.class_off + 32 * i
            desc = self.type_name(U32(self.data, base))
            class_data_off = U32(self.data, base + 24)
            methods = set()
            if class_data_off:
                p = class_data_off
                sf, p = uleb128(self.data, p)
                inf, p = uleb128(self.data, p)
                dm, p = uleb128(self.data, p)
                vm, p = uleb128(self.data, p)
                idx = 0
                for _ in range(sf + inf):
                    _, p = uleb128(self.data, p)
                    _, p = uleb128(self.data, p)
                for count in (dm, vm):
                    idx = 0
                    for _ in range(count):
                        diff, p = uleb128(self.data, p)
                        idx += diff
                        _, p = uleb128(self.data, p)
                        _, p = uleb128(self.data, p)
                        moff = self.method_off + 8 * idx
                        name = self.string(U32(self.data, moff + 4))
                        proto = self.proto_signature(U16(self.data, moff + 2))
                        methods.add(name + proto)
            yield desc, methods


def hash_methods(methods):
    h = hashlib.md5()
    for m in sorted(methods):
        h.update(m.encode("utf-8", "replace"))
        h.update(b"\x00")
    return h.hexdigest(), len(methods)


PREFIXES = [
    "Landroidx/",
    "Lcoil3/",
    "Lkotlin/",
    "Lkotlinx/",
    "Lcoil/",
    "Lokhttp3/",
    "Lokio/",
    "Lcom/google/",
    "Lorg/jetbrains/",
]


def prefix_of(desc):
    for p in PREFIXES:
        if desc.startswith(p):
            return p
    return None


def load_apk(path, keep_sets):
    """descriptor -> (hash, method_count, methods|None)"""
    out = {}
    with zipfile.ZipFile(path) as z:
        for name in z.namelist():
            if not re.match(r"^classes\d*\.dex$", name):
                continue
            dex = Dex(z.read(name))
            for desc, methods in dex.classes():
                h, n = hash_methods(methods)
                keep = keep_sets and prefix_of(desc) is not None
                out[desc] = (h, n, methods if keep else None)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--engine", required=True)
    ap.add_argument("--app", action="append", required=True)
    ap.add_argument("--json", default=None)
    args = ap.parse_args()

    print("engine: %s" % args.engine)
    engine = load_apk(args.engine, True)
    print("  classes: %d" % len(engine))

    result = {"engine": args.engine, "apps": {}}
    for app_path in args.app:
        print("\napp: %s" % app_path)
        app = load_apk(app_path, True)
        shared = [d for d in app if d in engine]
        differing = [d for d in shared if app[d][0] != engine[d][0]]
        print("  classes: %d  shared with engine: %d  different method sets: %d"
              % (len(app), len(shared), len(differing)))

        per_prefix = {}
        for p in PREFIXES:
            e = {d: v for d, v in engine.items() if d.startswith(p)}
            a = {d: v for d, v in app.items() if d.startswith(p)}
            sh = set(e) & set(a)
            diff = [d for d in sh if e[d][0] != a[d][0]]
            per_prefix[p] = {"engine": len(e), "app": len(a), "shared": len(sh),
                             "different": len(diff)}
            print("  %-16s engine=%-6d app=%-6d shared=%-6d different=%-5d"
                  % (p, len(e), len(a), len(sh), len(diff)))

        examples = []
        missing_total = 0
        for d in differing:
            eg = engine.get(d)
            ag = app.get(d)
            extra = []
            if eg[2] is not None and ag[2] is not None:
                extra = sorted(ag[2] - eg[2])
                missing_total += len(extra)
            examples.append({"class": d, "engine_methods": eg[1], "app_methods": ag[1],
                             "app_only_methods": extra})
        examples.sort(key=lambda ex: -len(ex["app_only_methods"]))
        print("  app-only methods on shared classes: %d" % missing_total)
        for ex in examples[:12]:
            print("   %-70s engine=%-4d app=%-4d  app-only: %s"
                  % (ex["class"], ex["engine_methods"], ex["app_methods"],
                     ", ".join(m[:60] for m in ex["app_only_methods"][:3])))

        result["apps"][app_path] = {"classes": len(app), "shared": len(shared),
                                    "different": len(differing),
                                    "prefixes": per_prefix, "examples": examples}

    if args.json:
        open(args.json, "w", encoding="utf-8").write(json.dumps(result, indent=2, ensure_ascii=False))
        print("\nwrote %s" % args.json)
    return 0


if __name__ == "__main__":
    sys.exit(main())
