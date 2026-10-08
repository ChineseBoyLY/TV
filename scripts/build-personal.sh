#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${JAVA_HOME:-}" ]]; then
    for candidate in "$HOME"/.gradle/jdks/*/bin/java; do
        if [[ -x "$candidate" ]]; then
            export JAVA_HOME="${candidate%/bin/java}"
            break
        fi
    done
fi
for candidate in "$HOME"/.local/share/uv/python/cpython-3.10*-linux-x86_64-gnu/bin; do
    if [[ -x "$candidate/python3.10" ]]; then
        export PATH="$candidate:$PATH"
        break
    fi
done
if ! compgen -G 'app/libs/lib-common-*.aar' >/dev/null; then
    echo 'Missing Media3 AARs in app/libs; see PERSONAL.md.' >&2
    exit 1
fi
exec ./gradlew :app:assembleMobileDebug --console=plain "$@"
