#!/usr/bin/env bash

set -eu

newversion="$1"

if ( echo "$newversion" | grep -q "SNAPSHOT" ); then
    echo "Expected version to be a release version, not snapshot"
    exit 1
fi

if [ "$(echo "$newversion" | tr -Cd '.')" != ".." ]; then
    echo "Expected semver (eg 1.2.3)"
    exit 1
fi


find "$(dirname "$0")/.." -name 'component-attributes.adoc' | while read file; do

    cat "$file" | sed "s/^:git-tag: .*/:git-tag: $newversion/" > "$file.tmp"

    mv "$file.tmp" "$file"
done
