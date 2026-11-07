#!/usr/bin/env python3
"""Pack the engine APK with the probe dexes *before* the engine dexes.

Dex order inside an APK decides which copy of a duplicated class the class loader
resolves: the first dex that contains the class wins.  Phase B of the single-APK spike
needs the app (probe) copy of AndroidX/Compose to win, so the layout is inverted:

    classes.dex .. classesK.dex          <- probe dexes (K = number of probe dex files)
    classes(K+1).dex .. classes(K+N).dex  <- engine classes.dex .. classesN.dex, renumbered

The probe dex entries are written at the position of the engine's first dex entry, followed
immediately by the renumbered engine dexes, so both the archive entry order and the numeric
names say "probe first".  Every other entry keeps its original order, compression method and
metadata (AndroidManifest.xml is replaced by the patched binary manifest).

Usage:
    python pack_inverted_apk.py --engine engine.apk --probe-dex-dir probe-dex/ \
        --manifest patched-manifest.bin --out packed-unsigned.apk
"""

import argparse
import os
import re
import sys
import zipfile

DEX_RE = re.compile(r"^classes(\d*)\.dex$")


def dex_index(name):
    m = DEX_RE.match(name)
    suffix = m.group(1)
    return 1 if suffix == "" else int(suffix)


def dex_name(index):
    return "classes.dex" if index == 1 else "classes%d.dex" % index


def read_probe_dexes(directory):
    names = [n for n in os.listdir(directory) if DEX_RE.match(n)]
    names.sort(key=dex_index)
    if not names:
        raise SystemExit("no classes*.dex in %s" % directory)
    return [(n, open(os.path.join(directory, n), "rb").read()) for n in names]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--engine", required=True)
    ap.add_argument("--probe-dex-dir", required=True)
    ap.add_argument("--manifest", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--order", choices=("probe-first", "engine-first"), default="probe-first",
                    help="probe-first (spike goal) or engine-first (Phase A / control)")
    args = ap.parse_args()

    probe = read_probe_dexes(args.probe_dex_dir)
    manifest_bytes = open(args.manifest, "rb").read()

    src = zipfile.ZipFile(args.engine)
    infos = src.infolist()
    engine_dexes = sorted([i for i in infos if DEX_RE.match(i.filename)],
                          key=lambda i: dex_index(i.filename))
    if not engine_dexes:
        raise SystemExit("no engine dex entries in %s" % args.engine)
    first_dex = min(i for i, info in enumerate(infos) if DEX_RE.match(info.filename))

    written_dex = []
    replaced_manifest = False
    with zipfile.ZipFile(args.out, "w", allowZip64=True) as dst:
        for idx, info in enumerate(infos):
            name = info.filename
            if idx == first_dex:
                if args.order == "probe-first":
                    for k, (pname, pbytes) in enumerate(probe, start=1):
                        out_name = dex_name(k)
                        zi = zipfile.ZipInfo(out_name, date_time=info.date_time)
                        zi.compress_type = zipfile.ZIP_DEFLATED
                        dst.writestr(zi, pbytes)
                        written_dex.append((out_name, len(pbytes), pname))
                    for k, einfo in enumerate(engine_dexes, start=len(probe) + 1):
                        out_name = dex_name(k)
                        zi = zipfile.ZipInfo(out_name, date_time=einfo.date_time)
                        zi.compress_type = einfo.compress_type
                        zi.external_attr = einfo.external_attr
                        zi.create_system = einfo.create_system
                        dst.writestr(zi, src.read(einfo.filename))
                        written_dex.append((out_name, einfo.file_size, einfo.filename))
                else:  # engine-first: control for the diagnostic (Phase A order)
                    for k, einfo in enumerate(engine_dexes, start=1):
                        out_name = dex_name(k)
                        zi = zipfile.ZipInfo(out_name, date_time=einfo.date_time)
                        zi.compress_type = einfo.compress_type
                        zi.external_attr = einfo.external_attr
                        zi.create_system = einfo.create_system
                        dst.writestr(zi, src.read(einfo.filename))
                        written_dex.append((out_name, einfo.file_size, einfo.filename))
                    for k, (pname, pbytes) in enumerate(probe, start=len(engine_dexes) + 1):
                        out_name = dex_name(k)
                        zi = zipfile.ZipInfo(out_name, date_time=info.date_time)
                        zi.compress_type = zipfile.ZIP_DEFLATED
                        dst.writestr(zi, pbytes)
                        written_dex.append((out_name, len(pbytes), pname))
                continue
            if DEX_RE.match(name):
                continue  # engine dexes were re-emitted above, in order
            data = manifest_bytes if name == "AndroidManifest.xml" else src.read(name)
            if name == "AndroidManifest.xml":
                replaced_manifest = True
            out_info = zipfile.ZipInfo(name, date_time=info.date_time)
            out_info.compress_type = info.compress_type
            out_info.external_attr = info.external_attr
            out_info.create_system = info.create_system
            dst.writestr(out_info, data)

    if not replaced_manifest:
        raise SystemExit("AndroidManifest.xml not found in %s" % args.engine)

    print("probe dexes : %d" % len(probe))
    print("engine dexes: %d" % len(engine_dexes))
    for out_name, size, source in written_dex:
        print("  %-14s <- %-14s %9d B" % (out_name, source, size))
    print("packed %s (manifest %d B, order=%s)" % (
        args.out, len(manifest_bytes), args.order))
    return 0


if __name__ == "__main__":
    sys.exit(main())
