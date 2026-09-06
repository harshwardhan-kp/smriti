#!/usr/bin/env bash
# Copies the six Qualcomm runtime libraries out of a local QAIRT 2.50 extraction into jniLibs.
#
# They are deliberately not in git: redistribution is governed by the SDK's LICENSE.pdf and
# QNN_NOTICE.txt, and this repo has a public remote. Every developer stages their own copy.
#
# Usage: scripts/stage-genie-libs.sh [path-to-qairt-2.50.0.260828]
set -euo pipefail

QAIRT="${1:-vendor/qairt/tree/qairt-2.50.0.260828}"
DEST="app/src/offline/jniLibs/arm64-v8a"

[ -d "$QAIRT" ] || { echo "No QAIRT extraction at $QAIRT" >&2; exit 1; }
mkdir -p "$DEST"

# Five aarch64 host-side libraries. libQnnHtpPrepare.so is not among them: the bundle ships
# precompiled context binaries, so no graph is ever compiled at runtime.
for lib in libGenie libQnnHtp libQnnSystem libQnnHtpNetRunExtensions libQnnHtpV81Stub; do
  cp "$QAIRT/lib/aarch64-android/$lib.so" "$DEST/"
done

# The DSP-side skel, which is a Hexagon ELF and not interchangeable with the aarch64 files of
# the same name that also live under hexagon-v81/unsigned.
cp "$QAIRT/lib/hexagon-v81/unsigned/libQnnHtpV81Skel.so" "$DEST/"

# Genie's headers, on the same footing as the libraries: same SDK, same licence, same reason
# for staying out of a repo with a public remote.
mkdir -p app/src/main/cpp/include
cp -R "$QAIRT/include/Genie" app/src/main/cpp/include/

# Guard the one mistake this layout invites: taking a DSP-side build for a host-side one.
for so in "$DEST"/*.so; do
  arch=$(file -b "$so")
  case "$(basename "$so")" in
    libQnnHtpV81Skel.so)
      [[ "$arch" == *"DSP6"* ]] || { echo "FAIL $so should be a Hexagon DSP6 ELF, got: $arch" >&2; exit 1; } ;;
    *)
      [[ "$arch" == *"aarch64"* ]] || { echo "FAIL $so should be aarch64, got: $arch" >&2; exit 1; } ;;
  esac
done

echo "Staged $(ls "$DEST" | wc -l | tr -d ' ') libraries into $DEST ($(du -sh "$DEST" | cut -f1))"
