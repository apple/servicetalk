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

cd "$(dirname "$0")"
cd ..

version=""
DOCS_FOLDER="docs/generation/.out/remote"
JAVADOC_FOLDER="build/javadoc"
BRANCH_NAME=$(git symbolic-ref -q HEAD)
BRANCH_NAME=${BRANCH_NAME##refs/heads/}
GIT_AUTHOR=$(git --no-pager show -s --format='%an <%ae>' HEAD)

if [ -z "${DRYRUN:-}" ]; then
    git="git"
else
    git="echo git"
fi

function usage() {
  echo "Usage: $0 [release_version]"
  echo "No arguments - update the SNAPSHOT version of docs website only"
  echo "release_version - publish docs for a new release version and update the SNAPSHOT version"
}

function clean_up_gh_pages() {
  if git worktree list | grep -q gh-pages; then
    echo "Cleanup 'gh-pages' worktree"
    git worktree remove -f gh-pages
  fi
  if git branch --list | grep -q "^\s*gh-pages$"; then
    echo "Remove 'gh-pages' branch"
    git branch -Df gh-pages
  fi
  # Just in case of the undefined initial state make sure there is no gh-pages folder
  rm -rf gh-pages
}

# Enforce JDK11 to keep javadoc format consistent for all versions:
java_version=$(./gradlew --no-daemon -version | grep ^JVM: | awk -F\. '{gsub(/^JVM:[ \t]*/,"",$1); print $1"."$2}')
if [ "$java_version" != "11.0" ]; then
  echo "Docs can be published only using Java 11, current version: $java_version"
  exit 1
fi

if [ "$#" -eq "0" ]; then
    echo "Publishing docs website for the SNAPSHOT version only"
elif [ "$#" -eq "1" ]; then
    version="$1"
    if ( echo "$version" | grep -Eqv "^\d+\.\d+$" ); then
        echo "Release version should match 'major.minor' pattern was: $1"
        exit 1
    fi
    echo "Publishing docs website for the release version $version"
else
    usage
    exit 1
fi

echo ""

# Clean up the state at the beginning in case the previous run did not finish successfully
clean_up_gh_pages

echo "Generate docs website"
pushd docs/generation
./gradlew --no-daemon clean validateRemoteSite
popd
echo "Docs website generated, see ./$DOCS_FOLDER"

echo "Generate javadoc"
./gradlew --no-daemon clean javadocAll
echo "Javadoc generated, see ./$JAVADOC_FOLDER"
``
if ( ! git remote get-url docs ); then
  git remote add docs git@github.com:apple/servicetalk.git
fi

git fetch docs +gh-pages:gh-pages
git worktree add gh-pages gh-pages

touch gh-pages/.nojekyll
\cp -r $DOCS_FOLDER/* gh-pages
echo "Copy javadoc to gh-pages/servicetalk/SNAPSHOT"
# Avoid accumulating old javadocs for classes that have been moved, renamed or deleted.
rm -rf gh-pages/servicetalk/SNAPSHOT/javadoc
\cp -r $JAVADOC_FOLDER gh-pages/servicetalk/SNAPSHOT
if [ ! -z "$version" ]; then
    echo "Copy javadoc to gh-pages/servicetalk/$version"
    rm -rf gh-pages/servicetalk/$version/javadoc
    \cp -r $JAVADOC_FOLDER gh-pages/servicetalk/$version
fi

pushd gh-pages
# Do not override older javadoc with Antora's placeholder:
files_to_revert=$(git diff --name-only | grep 'javadoc/index.html' | grep -v SNAPSHOT)
if [ ! -z "$version" ]; then
    files_to_revert=$(echo $files_to_revert | grep -v $version)
fi
echo $files_to_revert | xargs git checkout --

$git add * .nojekyll
if [ -z "$version" ]; then
    $git commit --author="$GIT_AUTHOR" -m "Update SNAPSHOT doc website"
else
    $git commit --author="$GIT_AUTHOR" -m "Publish docs website $version"
fi

$git push docs gh-pages
popd

# Clean up the state (worktree and temporary branch) after publication of the docs
clean_up_gh_pages

if [ -z "$version" ]; then
    echo "Docs website for the SNAPSHOT version successfully updated"
else
    echo "Docs website for the release version $version successfully published"
fi
