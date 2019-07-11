#!/usr/bin/env bash

cd "$(dirname "$0")"
cd ..

set -eu

function usage() {
cat << EOF
release.sh {next_version}

TODO
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

if [ "$(echo "$nextVersion" | tr -Cd '.')" != ".." ]; then
    echo "Expected next version to be semver (eg 1.2.3)"
    usage
    exit 1
fi

version=$(cat gradle.properties | grep version= |  sed 's/-SNAPSHOT//' | sed 's/^version=//')

if ( echo "$version" | grep -q "SNAPSHOT" ); then
    echo "Expected release version to be a release version, not snapshot"
    usage
    exit 1
fi

if [ "$(echo "$version" | tr -Cd '.')" != ".." ]; then
    echo "Expected release version to be semver (eg 1.2.3)"
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
$git branch -D master
$git checkout --track origin/master
$git pull
$git log -n1

pushd docs/generation
rm -rf .cache .out
./gradlew --no-daemon validateLocalSite
popd

for file in gradle.properties */gradle.properties; do
    sed "s/^version=.*/version=$version/" "$file" > "$file.tmp"
    mv "$file.tmp" "$file"
done
for file in docs/antora.yml */docs/antora.yml; do
    sed "s/^version:.*/version: ${version%.*}/" "$file" > "$file.tmp"
    mv "$file.tmp" "$file"
done
./scripts/update-git-ref-in-antora-components-attributes.sh "$version"
./scripts/manage-antora-remote-versions.sh "$version"

if [ -n "${DRYRUN:-}" ]; then
    echo "DRYRUN: Pausing to allow inspection before release would be tagged."
    echo "Press enter to continue."
    read
fi

$git commit -a -m "Release $version"
$git push origin master

$git tag "$version" -m "Release $version"
$git push origin "$version"

echo "Preparing repository for next development version $nextVersion"

for file in gradle.properties */gradle.properties; do
    sed "s/^version=.*/version=$nextVersion/" "$file" > "$file.tmp"
    mv "$file.tmp" "$file"
done
for file in docs/antora.yml */docs/antora.yml; do
    sed "s/^version:.*/version: ${nextVersion%.*}-SNAPSHOT/" "$file" > "$file.tmp"
    mv "$file.tmp" "$file"
done
./scripts/update-git-ref-in-antora-components-attributes.sh "master"

$git commit -a -m "Preparing for $nextVersion development"
$git push origin master

