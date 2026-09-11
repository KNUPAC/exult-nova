#!/usr/bin/env bash
set -euo pipefail
nova_repo=$(cd "$(dirname "$0")/.." && pwd)
nova_work="$nova_repo/build-nova"
cd "$nova_repo"
echo '5e5113e31dd8010b8dfd00b6b08f76681dc1e88254d357c92f15c202f7ed7e1f  exult-1.12.1.tar.gz' | sha256sum --check
# Refuse reuse of an old cross-compilation tree.
mkdir "$nova_work"
mkdir "$nova_work/source" "$nova_work/host"
tar -xzf exult-1.12.1.tar.gz -C "$nova_work/source" --strip-components=1
cd "$nova_work/source"
patch --batch --forward --dry-run -p1 < "$nova_repo/nova.patch"
patch --batch --forward -p1 < "$nova_repo/nova.patch"
cp -R "$nova_repo/overlay/." .
autoreconf -v -i
cd "$nova_work/host"
../source/configure --enable-data --enable-android-apk=debug \
    --with-android-arch=arm64-v8a --with-android-sdk=35 --with-android-build-tools=35.0.0 \
    --disable-exult --disable-tools --disable-timidity-midi --disable-alsa \
    --disable-fluidsynth --disable-mt32emu --disable-all-hq-scalers \
    --disable-nxbr --disable-zip-support --disable-sdl-parachute
make -C files -j2
make -C data -j2
make -C android
mkdir -p "$nova_repo/artifacts"
cp android/app/build/outputs/apk/debug/app-debug.apk "$nova_repo/artifacts/Exult-Nova-arm64-debug.apk"
python3 "$nova_repo/scripts/check-apk.py" "$nova_repo/artifacts/Exult-Nova-arm64-debug.apk"
# Include the exact patched GPL engine source alongside the test APK.
tar -czf "$nova_repo/artifacts/Exult-Nova-source.tar.gz" -C "$nova_work" source
