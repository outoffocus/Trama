#!/usr/bin/env bash

set -euo pipefail

if [[ $# -eq 0 ]]; then
    echo "Usage: $0 APK [APK ...]" >&2
    exit 2
fi

sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$sdk_root" && -f local.properties ]]; then
    sdk_root=$(sed -n 's/^sdk\.dir=//p' local.properties | head -1)
fi

find_sdk_tool() {
    local tool_name="$1"
    local tool_path=""

    if command -v "$tool_name" >/dev/null 2>&1; then
        command -v "$tool_name"
        return
    fi

    if [[ -n "$sdk_root" ]]; then
        tool_path=$(find "$sdk_root" -type f -name "$tool_name" -print 2>/dev/null | sort -V | tail -1)
    fi

    if [[ -z "$tool_path" ]]; then
        echo "Unable to find $tool_name. Install Android SDK Build-Tools and NDK." >&2
        exit 2
    fi

    echo "$tool_path"
}

zipalign_bin=$(find_sdk_tool zipalign)
objdump_bin=$(find_sdk_tool llvm-objdump)
work_dir=$(mktemp -d "${TMPDIR:-/tmp}/trama-16kb.XXXXXX")
trap 'rm -rf "$work_dir"' EXIT

failed=0

for apk in "$@"; do
    if [[ ! -f "$apk" ]]; then
        echo "APK not found: $apk" >&2
        failed=1
        continue
    fi

    apk_dir="$work_dir/$(basename "$apk" .apk)"
    mkdir -p "$apk_dir"
    unzip -qq "$apk" 'lib/*/*.so' -d "$apk_dir" || true

    while IFS= read -r library; do
        if "$objdump_bin" -p "$library" | awk '
            /LOAD/ {
                for (i = 1; i <= NF; i++) {
                    if ($i == "align") {
                        split($(i + 1), value, "\\*\\*")
                        if (value[2] < 14) exit 1
                    }
                }
            }
        '; then
            echo "ALIGNED   ${library#"$apk_dir"/}"
        else
            echo "UNALIGNED ${library#"$apk_dir"/}" >&2
            failed=1
        fi
    done < <(find "$apk_dir/lib" -type f \( -path '*/arm64-v8a/*.so' -o -path '*/x86_64/*.so' \) -print 2>/dev/null | sort)

    if "$zipalign_bin" -c -P 16 4 "$apk"; then
        echo "ZIP_ALIGNED $apk"
    else
        echo "ZIP_UNALIGNED $apk" >&2
        failed=1
    fi
done

exit "$failed"
