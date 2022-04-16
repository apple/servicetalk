#!/bin/bash
#
# Copyright © 2019, 2021 Apple Inc. and the ServiceTalk project authors
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
  echo "Usage: $0 old_version next_version [branch_name]"
  echo "old_version - the previous version to run japicmp against. 'skip' to skip japicmp"
  echo "next_version - the next version to update gradle.properties, expected -SNAPSHOT suffix"
  echo "branch_name - the branch name to release from (default is 'main')"
  echo "Example to release 0.42.10: $0 0.42.9 0.42.11-SNAPSHOT"
}

if [ "$#" -ne "2" ]; then
    usage
    exit 1
fi

oldVersion="$1"
nextVersion="$2"

if [ "$#" -gt "2" ]; then
  branchName="$3"
else
  branchName="main"
fi

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

if [ -z "${DRYRUN:-}" ]; then
    gradle_build_args="--no-build-cache --warning-mode all --refresh-dependencies clean build publishToMavenLocal"
else
    gradle_build_args="build publishToMavenLocal"
    echo "DRYRUN mode is enabled, using cached build."
fi

echo "Building local artifacts..."
./gradlew ${gradle_build_args}

if [[ "$oldVersion" == "skip" ]]; then
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
if $git rev-parse --quiet --verify ${branchName} > /dev/null; then
    $git checkout ${branchName}
else
    $git checkout --track ${remote_name}/${branchName}
fi
$git pull
$git log -n1

pushd docs/generation
./gradlew --no-daemon clean validateRemoteSite
popd

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

for file in docs/antora.yml */docs/antora.yml; do
    sed "s/^version:.*/version: SNAPSHOT/" "$file" > "$file.tmp"
    mv "$file.tmp" "$file"
done
for file in docs/modules/ROOT/nav.adoc */docs/modules/ROOT/nav.adoc; do
    sed "s/^:page-version: .*/:page-version: SNAPSHOT/" "$file" > "$file.tmp"
    mv "$file.tmp" "$file"
done

$git commit -a -m "Preparing for $nextVersion development"
$git push -u ${remote_name} "$branchName"
# Push tag after branch otherwise, CodeQL GH Action will fail.
$git push ${remote_name} "$version"

./scripts/publish-docs.sh "$version_majorminor"
