#!/usr/bin/env bash

set -eu

newref="$1"

find "$(dirname "$0")/.." -name 'component-attributes.adoc' | while read file; do

    cat "$file" | sed "s/^:git-ref: .*/:git-ref: $newref/" > "$file.tmp"

    mv "$file.tmp" "$file"
done
