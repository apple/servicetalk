#!/bin/bash
#
# Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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

## This script will cycle through all the ServiceTalk repositories and run the
## same command from the repo root

## Set this in your shell profile to execute a git on all repos:
## export SERVICETALK_ROOT=<path/to/servicetalk>
## export SERVICETALK_SCRIPTS_ROOT=$SERVICETALK_ROOT/servicetalk-gradle-composite/scripts
## alias st_forall='$SERVICETALK_SCRIPTS_ROOT/forall.sh'
## alias st_gita='st_forall git'

function usage {
    cat <<EOF
Usage:
  forall [-f] [-q] [-d] [-n] [-p] [-t maxTasks] some command and arguments

-f --skip-failures
  Continue to the next repo even if a command fails. By default, execution
  stops if a command fails in one repo.
-q --quiet
  Don't print the repo names when running.
-d --divergent
  Only execute on repos who's currently checked out commit diverges from
  upstream/master.
--divergent-from commit
  Only execute on repos who's currently checked out commit diverges from
  the specified commit.
-n --non-divergent
  Only execute on repos who's currently checked out commit does not diverge
  from upstream/master.
--non-divergent-from commit
  Only execute on repos who's currently checked out commit does not diverge
  from the specified commit.
-b --branch branch
  Only execute on repos who's currently checked out branch is the specified
  branch.
-p --parallel
  Run command in all repos in parallel. A maximum of 4 tasks will be executed
  in parallel. Implies '-f'.
-t --tasks maxTasks
  Maximum number of parallel tasks. Implies '-p'.
-s --skip-cd
  Iterate over all the repos, setting ST_REPO, but do not change directory.
EOF
}

if [ "$#" -eq 0 ]; then
  usage
  exit 1
fi

divergent=
branch=
skipfailures=
quiet=
base=upstream/master
parallel=false
tasks=4
skipcd=true

while [ $# -gt 0 ]; do
  if [ "${1#-}" != "$1" ]; then
    if [ "$1" = "--divergent" -o "$1" = "-d" ]; then
        divergent=true
    elif [ "$1" = "--non-divergent" -o "$1" = "-n" ]; then
        divergent=false
    elif [ "$1" = "--divergent-from" ]; then
        divergent=true
        shift
        base="$1"
    elif [ "$1" = "--non-divergent-from" ]; then
        divergent=false
        shift
        base="$1"
    elif [ "$1" = "-b" -o "$1" = "--branch" ]; then
        shift
        branch="$1"
    elif [ "$1" = "--skip-failures" -o "$1" = "-f" ]; then
        skipfailures=true
    elif [ "$1" = "--quiet" -o "$1" = "-q" ]; then
        quiet=true
    elif [ "$1" = "--parallel" -o "$1" = "-p" ]; then
        parallel=true
    elif [ "$1" = "--tasks" -o "$1" = "-t" ]; then
        parallel=true
        shift
        tasks="$1"
    elif [ "$1" = "--skip-cd" -o "$1" = "-s" ]; then
        skipcd=false
    else
        if [ "$1" != "--help" ]; then
            echo "Unknown argument: $1"
        fi
        usage
        exit 1
    fi
  else
    break
  fi
  shift
done

source $(dirname $0)/repos.sh

BASEPATH=$(dirname $0)/..

function isDivergent {
    branch_sha="$(git rev-parse --verify HEAD)"
    merge_base="$(git merge-base "$base" HEAD)"
    if [ "$branch_sha" == "$merge_base" ]; then
        return 1
    else
        return 0
    fi
}

function shouldExecute {
    if [ -n "$divergent" ]; then
        if [ "$divergent" = "true" ]; then
            # only execute on divergent branches
            return $(isDivergent)
        elif [ "$divergent" = "false" ]; then
            # only execute on non-divergent branches
            if ( isDivergent ); then
                return 1
            else
                return 0
            fi
        fi
    fi
    if [ -n "$branch" ]; then
        current_branch=`git branch | grep "^\*" | sed "s/^\* //"`
        if [ "$current_branch" = "$branch" ]; then
            return 0
        else
            return 1
        fi
    fi

    true
}

# Extract the first argument as the command, so we can be smart with it.
CMD="$1"
shift

# If the command starts with "./" then execute it from the working directory.
if [ "${CMD#./}" != "$CMD" ]; then
    CMD="$PWD"/"${CMD#./}"
fi

for ST_REPO in $ST_REPOS; do
    REPO_PATH=$BASEPATH/$ST_REPO
    if [ "$skipcd" == "true" ]; then
        pushd $REPO_PATH > /dev/null
    fi
    if ( shouldExecute ); then
        if [ "$parallel" = "true" ]; then
            while [ "$(jobs -pr | wc -l)" -ge "$tasks" ]; do
                sleep 0.2
            done
        fi

        if [ "$quiet" != "true" ]; then
            echo -e "\033[4m\033[35m $ST_REPO \033[0m"
        fi
        export REPO

        export ST_REPO
        if [ "$parallel" = "true" ]; then
            "$CMD" "$@" &
        else
            if [ "$skipfailures" = "true" ]; then
                "$CMD" "$@" || true
            else
                "$CMD" "$@"
            fi
        fi

    else
        if [ "$quiet" != "true" ]; then
            echo -e "\033[2m $ST_REPO \033[0m"
        fi
    fi
    if [ "$skipcd" == "true" ]; then
        popd > /dev/null
    fi
done

if [ "$parallel" = "true" ]; then
    wait
fi
