#!/usr/bin/env bash

cd "$(dirname "$0")"
cd ..

set -eu

function usage() {
cat << EOF
manage-antora-remote-versions.sh [-a|-o] {semver}

Adds or updates tag versions in the Antora remotes.

  -a Add a version to the tags
  -u Update a patch version

semver must be the full 3 components.
EOF
}

if [ "$#" -ne "2" ]; then
    usage
    exit 1
fi

operation="$1"
newversion="$2"

if [ "$operation" != "-a" -a "$operation" != "-u" ]; then
    echo "Unknown argument: $operation"
    usage
    exit 1
fi

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

if [ "$operation" = "-a" ]; then
    cat "$file" | sed "s/^      tags: \[\(.*\)\]$/      tags: [\1, $newversion]/" > "$file.tmp"
elif [ "$operation" = "-u" ]; then
    newversionprefix="${newversion%.*}"
    cat "$file" | sed "s/^      tags: \[\(.*\)$newversionprefix\.[^,]*\(.*\)\]$/      tags: [\1$newversion\2]/" > "$file.tmp"
fi

count2="$(grep -F "$newversion" "$file.tmp" | wc -l)"

if [ "$count1" != "$count2" ]; then
    echo "The number of lines do not match:"
    echo "'tags:' lines found initially: $count1"
    echo "Lines found with new version:  $count2"
    exit 2
fi

mv "$file.tmp" "$file"

