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

set -eu

function usage() {
    cat <<EOF
Usage:
  ./scripts/update-copyright.sh git-ref [year]

  git-ref
    A SHA, branch, tag, etc that is used as the base to diff the workspace
    against. This is used to find the list of files to check the copyright dates
    of, as well as to show a diff of what's changed, to make decisions on
    whether or not to update the copyright year.

  year  [default: current year]
    Specify the year to add to the copyright years.

  Description

  Using the git-ref specified, this tool will find files modified between that
  ref and the current workspace. For each file, a munged diff will be shown, and
  the user prompted to enter 'y' or 'n' for updating the copyright.

  The diff munging removes any modified imports, and shows only the modified
  lines themselves, excluding context. Additionally, only the first N lines of
  the diff are shown, where N is approximately the number of rows the terminal
  is able to display. The intent is to display only the information necessary
  for the user to decide whether a change is sufficiently non-trivial as to
  require a copyright date update.

EOF
}

if [ $# -lt 1 ]; then
    usage
    exit 1
fi

thisyear="$(date +%Y)"
base="$1"
year="${2:-$thisyear}"

if ( ! echo "$year" | grep -q "^20[1-9][0-9]$" ); then
    echo "'$year' doesn't look like a valid year, 2010-2099."
    usage
    exit 1
fi

if ( ! git show "$base" > /dev/null 2>&1 ); then
    echo "Invalid or unable to find git reference '$base'"
    usage
    exit 1
fi

function finish {
    rm -f update-copyright-modified-files
    rm -f update-copyright-temp
}
trap finish EXIT

lastyear="$((year-1))"
lines="$(tput lines)"
git diff --name-only "$base" > update-copyright-modified-files
exec 4<> update-copyright-modified-files
while read file <&4; do
    if [ ! -e "$file" ]; then
        continue
    fi
    if ( ! grep -q "Copyright © .* Apple Inc.*" "$file" ); then
        echo "No copyright found:"
        echo "$file"
        echo "Press enter to continue."
        read discard
        continue
    fi
    copyright="$(head -n3 "$file" | grep "Copyright" |\
        sed 's/.*Copyright © \(.*\) Apple Inc.*/\1/')"
    if ( echo "$copyright" | grep -q "$year" ); then
        continue
    fi

    clear
    git diff "$base" -- "$file" |\
        grep '^[-+]' |\
        grep -v '^[+-]import' |\
        head -n "$((lines-1))"

    if [ "$copyright" = "$lastyear" ]; then
        newcopyright="$copyright-$year"
    elif [ "${copyright%-$lastyear}" != "$copyright" ]; then
        # This means it ends in a hyphen followed by last year
        # eg. 2016-2018
        newcopyright="${copyright%-$lastyear}-$year"
    else
        newcopyright="$copyright, $year"
    fi

    echo -n "Update copyright ($copyright) to $newcopyright? [y/n]"
    update=
    while [ "$update" != 'y' -a "$update" != 'n' ]; do
        read -s -n1 update
    done
    echo "$update"

    if [ "$update" = 'y' ]; then
        head -n3 "$file" | sed "s/$copyright/$newcopyright/" > update-copyright-temp
        tail -n+4 "$file" >> update-copyright-temp
        mv update-copyright-temp "$file"
        git add "$file"
    fi
done
