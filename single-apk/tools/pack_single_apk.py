#!/usr/bin/env python3
"""Pack the patched manifest + an extra dex into the engine APK, byte-preserving the rest.

Only two things change versus the engine APK:
  * AndroidManifest.xml is replaced by the patched binary manifest;
  * a new classesN.dex (N = next free index) is appended after the last classes*.dex.

Every other entry keeps its original order, compression method and metadata.  Alignment
and signing are done afterwards by zipalign/apksigner (see build-single-apk.ps1).

Usage:
    python pack_single_apk.py --engine engine.apk --dex classes.dex \
        --manifest patched-manifest.bin --out packed-unsigned.apk
"""

import argparse
import re
import sys
import zipfile


def next_dex_name(names):
    highest = 0
    for name in names:
        m = re.match(r"^classes(\d*)\.dex$", name)
        if not m:
            continue
        suffix = m.group(1)
        highest = max(highest, 1 if suffix == "" else int(suffix))
    return "classes%d.dex" % (highest + 1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--engine", required=True)
    ap.add_argument("--dex", required=True)
    ap.add_argument("--manifest", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    dex_bytes = open(args.dex, "rb").read()
    manifest_bytes = open(args.manifest, "rb").read()

    src = zipfile.ZipFile(args.engine)
    infos = src.infolist()
    names = [i.filename for i in infos]
    dex_name = next_dex_name(names)
    last_dex = max(i for i, n in enumerate(names) if re.match(r"^classes(\d*)\.dex$", n))

    replaced_manifest = False
    inserted_dex = False
    with zipfile.ZipFile(args.out, "w", allowZip64=True) as dst:
        for idx, info in enumerate(infos):
            data = src.read(info.filename) if info.filename != "AndroidManifest.xml" else manifest_bytes
            if info.filename == "AndroidManifest.xml":
                replaced_manifest = True
            out_info = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            out_info.compress_type = info.compress_type
            out_info.external_attr = info.external_attr
            out_info.create_system = info.create_system
            dst.writestr(out_info, data)
            if idx == last_dex:
                dex_info = zipfile.ZipInfo(dex_name, date_time=info.date_time)
                dex_info.compress_type = zipfile.ZIP_DEFLATED
                dst.writestr(dex_info, dex_bytes)
                inserted_dex = True
        if not replaced_manifest or not inserted_dex:
            raise SystemExit("FAILED: manifest=%s dex=%s" % (replaced_manifest, inserted_dex))

    print("packed %s: manifest=%d B, %s=%d B" % (args.out, len(manifest_bytes), dex_name, len(dex_bytes)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
