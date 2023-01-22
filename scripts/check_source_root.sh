#!/bin/bash

set -e

TEMPLATE="""// Configure {source-root} values based on how this document is rendered: on GitHub or not
ifdef::env-github[]
:source-root:
endif::[]
ifndef::env-github[]
ifndef::source-root[:source-root: https://github.com/apple/servicetalk/blob/{page-origin-refname}]
endif::[]
"""

FIX=false
EXIT_SUCCESS=true

function usage {
    echo "Usage $(basename $0) -f [optional filename]"
    echo "      -f causes the files to be fixed"

    exit 1
}

# evaluate whether the passed file properly defines and uses the source-root variable
function eval_file() {
    local f=$1

    local has_def=$(grep ':source\-root:' $f)

    # We assume that we never wat a bare {source-root} and it will always have a '/something'.
    # Otherwise, we can't distinguish it from the header.
    local has_ref=$(grep '{source\-root}/' $f)

    if [ -z "$has_def" ] && [ -n "$has_ref" ]; then
        # def is defined but ref is not

        if $FIX
        then
            echo "INFO: adding definition for source-root to $f."
            local contents=$(cat $f)
            echo "$TEMPLATE" > $f
            echo "$contents" >> $f
        else
            echo "ERROR: reference to 'source-root' found but no definition: $f"
            EXIT_SUCCESS=false
        fi

    elif [ -n "$has_def" ] && [ -z "$has_ref" ]; then
        echo "WARNING: definition of 'source-root' found but no references: $f"
    fi
    }

function process_all() {
    for DOCFILE in $(find . -type f -iname "*.adoc")
    do
        eval_file $DOCFILE
    done

    if ! $EXIT_SUCCESS
    then
        echo "Command found errors. Exiting."
        exit 1
    fi

}

optstring=":f"

while getopts ${optstring} arg; do
    case "${arg}" in
        f) FIX=true ;;
        ?) 
           echo "Invalid options: ${OPTARG}."
           usage
           ;;
    esac
done

process_all

