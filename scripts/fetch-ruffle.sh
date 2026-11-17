#!/usr/bin/env bash
# Downloads the Ruffle web player (MIT / Apache-2.0) into app/assets/www.
set -euo pipefail
VERSION="${RUFFLE_VERSION:-0.7.1}"
HERE="$(cd "$(dirname "$0")/.." && pwd)"; DEST="$HERE/app/assets/www"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
(cd "$T" && npm pack "@ruffle-rs/ruffle@$VERSION" >/dev/null && tar xzf ruffle-rs-ruffle-*.tgz)
cp "$T"/package/ruffle.js "$T"/package/core.ruffle.*.js "$T"/package/*.wasm "$DEST/"
cp "$T/package/LICENSE_MIT" "$T/package/LICENSE_APACHE" "$DEST/"
echo "Ruffle $VERSION copied to $DEST"
