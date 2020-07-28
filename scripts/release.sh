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
cat << EOF
release.sh {next_version}
EOF
}

if [ "$#" -ne "1" ]; then
    usage
    exit 1
fi

nextVersion="$1"

if ( echo "$nextVersion" | grep -qv "SNAPSHOT" ); then
    echo "Expected next version to be a SNAPSHOT version"
    usage
    exit 1
fi

version=$(cat gradle.properties | grep version= |  sed 's/-SNAPSHOT//' | sed 's/^version=//')

if ( echo "$version" | grep -q "SNAPSHOT" ); then
    echo "Expected release version to be a release version, not snapshot"
    usage
    exit 1
fi

echo "Releasing version $version"

if [ -z "${DRYRUN:-}" ]; then
    git="git"
else
    git="echo git"
fi

$git fetch -p
if $git rev-parse --quiet --verify main > /dev/null; then
    $git checkout main
else
    $git checkout --track origin/main
fi
$git pull
$git log -n1

pushd docs/generation
./gradlew --no-daemon clean validateLocalSite
popd

sed "s/^version=.*/version=$version/" gradle.properties > gradle.properties.tmp
mv gradle.properties.tmp gradle.properties

for file in docs/antora.yml */docs/antora.yml; do
    sed "s/^version:.*/version: '${version%.*}'/" "$file" > "$file.tmp"
    mv "$file.tmp" "$file"
done
for file in docs/modules/ROOT/nav.adoc */docs/modules/ROOT/nav.adoc; do
    sed "s/^:page-version: .*/:page-version: ${version%.*}/" "$file" > "$file.tmp"
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
$git push origin "$version"

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
$git push origin main
