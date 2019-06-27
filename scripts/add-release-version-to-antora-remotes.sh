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

file=docs/generation/site-remote.yml

count1="$(grep "tags:" "$file" | wc -l)"

cat "$file" | sed "s/^      tags: \[\(.*\)\]$/      tags: [\1, $newversion]/" > "$file.tmp"

count2="$(grep -F "$newversion" "$file.tmp" | wc -l)"

if [ "$count1" != "$count2" ]; then
    echo "The number of lines do not match:"
    echo "'tags:' lines found initially: $count1"
    echo "Lines found with new version:  $count2"
    exit 2
fi

mv "$file.tmp" "$file"

