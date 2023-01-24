#!/bin/bash
#
# Copyright © 2023 Apple Inc. and the ServiceTalk project authors
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

TEMPLATE="""// Configure {source-root} values based on how this document is rendered: on GitHub or not
ifdef::env-github[]
:source-root:
endif::[]
ifndef::env-github[]
ifndef::source-root[:source-root: https://github.com/apple/servicetalk/blob/{page-origin-refname}]
endif::[]
"""

REPAIR=false
EXIT_SUCCESS=true

function usage {
    echo "Usage $(basename $0) -r [optional filename]"
    echo "      -r causes the files to be fixed"
}

function exit_abnormal {
    usage
    exit 1
}

# evaluate whether the passed file properly defines and uses the source-root variable
function eval_file() {
    local file="$1"

    if ! [ -f "$file" ]; then
        echo "File $file doesn't exist. Exiting."
        exit 1
    fi

    local has_def=$(grep ':source\-root:' $file)

    # We assume that we never wat a bare {source-root} and it will always have a '/something'.
    # Otherwise, we can't distinguish it from the header.
    local has_ref=$(grep -n '{source\-root}/' $file)

    if [ -z "$has_def" ] && [ -n "$has_ref" ]; then
        # source-root is not defined but there is a reference to it.
        if $REPAIR
        then
            echo "INFO: adding definition for source-root to $file."
            local contents=$(cat $file)
            echo "$TEMPLATE" > $file
            echo "$contents" >> $file
        else
            echo "ERROR: reference to 'source-root' found but no definition. $file: $has_ref"
            EXIT_SUCCESS=false
        fi

    elif [ -n "$has_def" ] && [ -z "$has_ref" ]; then
        echo "WARNING: definition of 'source-root' found but no references: $file"
    fi
    }

function process_all() {
    for DOCFILE in $(find . -type f -iname "*.adoc")
    do
        eval_file $DOCFILE
    done
}

while getopts ":rh" arg; do
    case "${arg}" in
        r) REPAIR=true ;;
        h)
           usage
           exit 1
           ;;
        ?)
           echo "Invalid options: ${OPTARG}."
           exit_abnormal
           ;;
    esac
done

shift "$((OPTIND-1))"

if [ -z "${1-}" ]; then
    process_all
else
    eval_file $1
fi

if ! $EXIT_SUCCESS
then
    echo "Found missing source-root definitions."
    echo "Try running with the -r flag to automatically fix missing source-root definitions."
    echo "Exiting."
    exit 1
fi
