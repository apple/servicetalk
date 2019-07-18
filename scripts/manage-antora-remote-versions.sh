#!/usr/bin/env bash

cd "$(dirname "$0")"
cd ..

set -eu

function usage() {
cat << EOF
manage-antora-remote-versions.sh {semver}

Adds or updates tag versions in the Antora remotes.

semver must be the full 3 components.
EOF
}

if [ "$#" -ne "1" ]; then
    usage
    exit 1
fi

newversion="$1"

if ( echo "$newversion" | grep -q "SNAPSHOT" ); then
    echo "Expected version to be a release version, not snapshot"
    usage
    exit 1
fi

if [ "$(echo "$newversion" | tr -Cd '.')" != ".." ]; then
    echo "Expected semver (eg 1.2.3)"
    usage
    exit 1
fi

file=docs/generation/site-remote.yml

count1="$(grep "tags:" "$file" | wc -l)"

majorminor="${newversion%.*}"

rm -f "$file.tmp"
cat "$file" | while IFS='' read line; do
    if ( echo "$line" | grep -q "^      tags: \[.*\]" ); then
        if (echo "$line" | grep -q "[[ ]${majorminor}.\d" ); then
            # If major.minor was already in this line, update the patch version
            echo "$line" |
              sed "s/^      tags: \[\(.*[[ ]\)$majorminor\.[^,]*\(.*\)\]$/      tags: [\1$newversion\2]/" \
              >> "$file.tmp"
        else
            # If major.minor was not already in this line, add it
            echo "$line" | sed "s/^      tags: \[\(.*\)\]$/      tags: [\1, $newversion]/" >> "$file.tmp"
        fi
    else
        echo "$line" >> "$file.tmp"
    fi
done

count2="$(grep -F "$newversion" "$file.tmp" | wc -l)"

if [ "$count1" != "$count2" ]; then
    echo "The number of lines do not match:"
    echo "'tags:' lines found initially: $count1"
    echo "Lines found with new version:  $count2"
    exit 2
fi

mv "$file.tmp" "$file"

