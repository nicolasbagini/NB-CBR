#!/bin/bash
# Copia las librerías dentro de www/lib para que la app funcione sin internet
set -e
mkdir -p www/lib
cp node_modules/jszip/dist/jszip.min.js www/lib/jszip.min.js

cp node_modules/node-unrar-js/esm/js/unrar.wasm www/lib/unrar.wasm \
  || echo "AVISO: no se encontró unrar.wasm; la app usará la copia en línea"

npx esbuild node_modules/node-unrar-js/esm/index.esm.js \
  --bundle --format=esm --platform=browser \
  --alias:fs=./ci/empty.js --alias:path=./ci/empty.js --alias:module=./ci/empty.js \
  --alias:url=./ci/empty.js --alias:os=./ci/empty.js --alias:crypto=./ci/empty.js \
  --outfile=www/lib/unrar.bundle.js \
  || echo "AVISO: no se pudo empaquetar unrar; la app usará la copia en línea"

ls -la www/lib
