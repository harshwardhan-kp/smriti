#!/usr/bin/env bash
# sherpa-onnx is NOT published on Maven Central by k2-fsa; they ship prebuilt AARs on GitHub
# releases instead. The AAR is 47 MB, so it is gitignored rather than committed — same policy
# as the models. Run this once after cloning, before the first build.
set -euo pipefail
VERSION="${1:-1.13.7}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/libs/sherpa-onnx-${VERSION}.aar"
mkdir -p "$ROOT/app/libs"
if [[ -f "$DEST" ]]; then echo "already present: $DEST"; exit 0; fi
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${VERSION}/sherpa-onnx-${VERSION}.aar"
echo "fetching $URL"
curl -fsSL -o "$DEST" "$URL"
echo "ok: $DEST ($(du -h "$DEST" | cut -f1))"
