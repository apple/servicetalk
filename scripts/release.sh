#!/bin/bash
#
# Copyright © 2019, 2021-2022 Apple Inc. and the ServiceTalk project authors
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

DEFAULT_BRANCH="main"
JAPICMP_SKIP_VERSION="skip"
DOCS_FOLDER="docs/generation/.out/remote"
JAVADOC_FOLDER="build/javadoc"
BRANCH_NAME=$(git symbolic-ref -q HEAD)
BRANCH_NAME=${BRANCH_NAME##refs/heads/}
GIT_AUTHOR=$(git --no-pager show -s --format='%an <%ae>' HEAD)

function usage() {
  echo "Usage: $0 old_version next_version"
  echo "old_version - the previous version to run japicmp against. \"${JAPICMP_SKIP_VERSION}\" to skip japicmp"
  echo "next_version - the next version to update gradle.properties, \"-SNAPSHOT\" suffix expected"
  echo "Example to release 0.42.10: $0 0.42.9 0.42.11-SNAPSHOT"
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

if [ "$#" -ne "2" ]; then
    usage
    exit 1
fi

# Enforce JDK17 to get latest LTS javadoc format/features (search, etc.):
java_version=$(./gradlew --no-daemon -version | grep ^JVM: | awk -F\. '{gsub(/^JVM:[ \t]*/,"",$1); print $1"."$2}')
if [ "$java_version" != "17.0" ]; then
  echo "Docs can be published only using Java 17, current version: $java_version"
  exit 1
fi

oldVersion="$1"
nextVersion="$2"

if ( echo "$nextVersion" | grep -qv "SNAPSHOT" ); then
    echo "Expected next version to be a SNAPSHOT version"
    usage
    exit 1
fi

version=$(cat gradle.properties | grep version= | sed 's/^version=//')

if [ "$nextVersion" == "$version" ]; then
    echo "Next version '$nextVersion' cannot be equal to the current version in gradle.properties: $version"
    usage
    exit 1
fi

version=$(echo "$version" | sed 's/-SNAPSHOT//')

if ( echo "$version" | grep -q "SNAPSHOT" ); then
    echo "Expected release version '$version' to be a release version, not a snapshot"
    usage
    exit 1
fi

# Clean up the state at the beginning in case the previous run did not finish successfully
clean_up_gh_pages

if [ -z "${DRYRUN:-}" ]; then
    gradle_build_args="--no-build-cache --warning-mode all --refresh-dependencies clean build publishToMavenLocal"
else
    gradle_build_args="build publishToMavenLocal"
    echo "DRYRUN mode is enabled, using cached build."
fi

echo "Building local artifacts..."
./gradlew ${gradle_build_args}

if [[ "$oldVersion" == "$JAPICMP_SKIP_VERSION" ]]; then
  echo "Skipping japicmp"
else
  echo "Running japicmp of local artifacts (which will be released as $version) against old version $oldVersion..."
  ./scripts/japicmp.sh $oldVersion
fi

echo "Releasing version $version"
version_majorminor="${version%.*}"

remote=$(git remote -v | grep "apple/servicetalk.git" | head -n1)
remote_name=$(echo $remote | cut -d' ' -f1)
remote_url=$(echo $remote | cut -d' ' -f2)
echo "Working with remote $remote_name -> $remote_url"

if [ -z "${DRYRUN:-}" ]; then
    git="git"
else
    git="echo git"
    echo "DRYRUN mode is enabled, any further changes won't be committed."
fi

$git fetch -p
$git pull
$git log -n1

# No need to clean, it has been done above
echo "Generate javadoc..."
./gradlew --no-daemon javadocAll
echo "Javadoc generated, see ./$JAVADOC_FOLDER"

echo "Generate docs website..."
pushd docs/generation
./gradlew --no-daemon clean validateRemoteSite
popd
echo "Docs website generated, see ./$DOCS_FOLDER"

sed "s/^version=.*/version=$version/" gradle.properties > gradle.properties.tmp
mv gradle.properties.tmp gradle.properties

for file in docs/antora.yml */docs/antora.yml; do
    sed "s/^version:.*/version: '${version_majorminor}'/" "$file" > "$file.tmp"
    mv "$file.tmp" "$file"
done
for file in docs/modules/ROOT/nav.adoc */docs/modules/ROOT/nav.adoc; do
    sed "s/^:page-version: .*/:page-version: ${version_majorminor}/" "$file" > "$file.tmp"
    mv "$file.tmp" "$file"
done
./scripts/manage-antora-remote-versions.sh "$version"

if [ -n "${DRYRUN:-}" ]; then
    echo "DRYRUN: Pausing to allow inspection before release would be tagged."
    echo "Press enter to continue."
    read
fi

$git commit -a -m "Release $version"
$git tag "$version" -m "Release $version"

echo "Preparing repository for next development version $nextVersion"

for file in $(find . -name pom.xml); do
  # update version to the previously released version because ServiceTalk uses gradle and project caches/build
  # configuration may not be the same as for maven, but release can always be pulled from maven central.
  sed "s/<servicetalk\.version>.*<\/servicetalk\.version>/<servicetalk\.version>$version<\/servicetalk\.version>/" \
    $file > pom.xml.tmp
  mv pom.xml.tmp $file
done

sed "s/^version=.*/version=$nextVersion/" gradle.properties > gradle.properties.tmp
mv gradle.properties.tmp gradle.properties

if [[ "$BRANCH_NAME" == "$DEFAULT_BRANCH" ]]; then
  for file in docs/antora.yml */docs/antora.yml; do
    sed "s/^version:.*/version: SNAPSHOT/" "$file" > "$file.tmp"
    mv "$file.tmp" "$file"
  done
  for file in docs/modules/ROOT/nav.adoc */docs/modules/ROOT/nav.adoc; do
    sed "s/^:page-version: .*/:page-version: SNAPSHOT/" "$file" > "$file.tmp"
    mv "$file.tmp" "$file"
  done
fi

$git commit -a -m "Preparing for $nextVersion development"
$git push -u ${remote_name} "$BRANCH_NAME"
# Push tag after branch otherwise, CodeQL GH Action will fail.
$git push ${remote_name} "$version"

# Publish docs to gh-pages
if ( ! git remote get-url docs ); then
  git remote add docs git@github.com:apple/servicetalk.git
fi
git fetch docs +gh-pages:gh-pages
git worktree add gh-pages gh-pages

touch gh-pages/.nojekyll

echo "Copy javadoc to gh-pages/servicetalk/$version_majorminor"
rm -rf gh-pages/servicetalk/$version_majorminor/javadoc
\cp -r $JAVADOC_FOLDER gh-pages/servicetalk/$version_majorminor

if [[ "$BRANCH_NAME" == "$DEFAULT_BRANCH" ]]; then
  echo "Copy Antora docs to gh-pages"
  \cp -r $DOCS_FOLDER/* gh-pages

  # Avoid accumulating old javadocs for classes that have been moved, renamed or deleted.
  echo "Copy javadoc to gh-pages/servicetalk/SNAPSHOT"
  rm -rf gh-pages/servicetalk/SNAPSHOT/javadoc
  \cp -r $JAVADOC_FOLDER gh-pages/servicetalk/SNAPSHOT
else
  # Antora docs are published as a single bundle which includes all versions from site-remote.yml. We only publish docs
  # from main branch or else we may publish docs that are incomplete and missing newer versions.
  echo "Skipping Antora unless on $DEFAULT_BRANCH branch. Cherry-pick site-remote.yml changes to $DEFAULT_BRANCH."
fi

pushd gh-pages
# Do not override older javadoc with Antora placeholder
files_to_revert=$(git diff --name-only | grep 'javadoc/index.html' | grep -v SNAPSHOT)
if [[ "$BRANCH_NAME" != "$DEFAULT_BRANCH" ]]; then
  files_to_revert=$(echo ${files_to_revert} | grep -v ${version_majorminor}) || echo "empty files_to_revert"
fi
echo $files_to_revert | xargs git checkout --

$git add * .nojekyll
$git commit --author="$GIT_AUTHOR" -m "Publish docs website $version_majorminor"
$git push docs gh-pages
popd

# Clean up the state (worktree and temporary branch) after publication of the docs
clean_up_gh_pages

echo "Docs website for the release version $version_majorminor successfully published"
