#!/bin/bash
#
# Copyright © 2019 Apple Inc. and the ServiceTalk project authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

cd "$(dirname "$0")"
cd ..

set -eu

function usage() {
  echo "Usage: $0 version"
  echo "version - Adds or updates tag versions in the Antora remotes. Expected semver 3 components."
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
    if ( echo "$line" | grep -q "^      tags: \[.*\]$" ); then
        if ( echo "$line" | grep -q "[[ ]${majorminor}.\d" ); then
            # If major.minor was already in this line, update the patch version
            echo "$line" |
              sed "s/^      tags: \[\(.*[[ ]\)$majorminor\.[^,]*\(.*\)\]$/      tags: [\1$newversion\2]/" \
              >> "$file.tmp"
        elif ( echo "$line" | grep -q "^      tags: \[[ ]*\]$" ); then
            # If tags is empty
            echo "$line" | sed "s/^      tags: \[[ ]*\]$/      tags: [$newversion]/" >> "$file.tmp"
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
