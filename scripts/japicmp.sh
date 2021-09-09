#!/bin/bash
#
# Copyright © 2021 Apple Inc. and the ServiceTalk project authors
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

SCRIPT=$(basename "${BASH_SOURCE:-stidn}")

if [ $# -lt 1 ] || [ $# -gt 3 ]; then
  echo "# Usage"
  echo "#    ${SCRIPT} <old_version> (<new_version> (<group_id>))"
  echo "# Description"
  echo "# This script compares versions for binary backward compatibility."
  echo "# It must be run from a directory containing a clone of ServiceTalk"
  echo "# if optional <new_version> unspecified or string 'local' then compare to local build"
  echo "# if optional <group_id> unspecified then local dir gradle 'group' property will be used"
  echo "# Comparisons against local build assume that './gradlew build' has been run."
  exit 1
fi

MVN_REPO="$(mvn help:evaluate -Dexpression=settings.localRepository -q -DforceStdout)"

JAPICMP_VERSION="0.15.3"
JAR_DIR="${MVN_REPO}/com/github/siom79/japicmp/japicmp/${JAPICMP_VERSION}"
JAR_FILE="${JAR_DIR}/japicmp-${JAPICMP_VERSION}-jar-with-dependencies.jar"
if [ ! -f "${JAR_FILE}" ]; then
  mvn -N dependency:get \
    -DgroupId=com.github.siom79.japicmp -DartifactId=japicmp -Dversion="${JAPICMP_VERSION}" \
    -Dtransitive=false -Dclassifier=jar-with-dependencies 2>&1 || exit 1
fi

OLD_ST_VERSION="${1:-}"
LOCAL="${2:-local}"
NEW_ST_VERSION="${2:-$(./gradlew properties | grep '^version: ' | cut -f 2 -d ' ')}"
GROUP_ID="${3:-$(./gradlew properties | grep '^group: ' | cut -f 2 -d ' ')}"
GROUP_PATH=$(echo "${GROUP_ID}" | tr '.' '/')
BASEPATH="${MVN_REPO}/${GROUP_PATH}/"

if [ -z "${OLD_ST_VERSION}" ]; then
  echo "# Error: Old version not specified."
  exit 1
fi

# All servicetalk modules except:
# servicetalk-benchmarks, servicetalk-bom, servicetalk-examples, servicetalk-gradle-plugin-internal
ARTIFACTS="$(find servicetalk-* -type d -maxdepth 0 |
  grep -v -- '-\(benchmarks\|bom\|examples\|gradle-plugin-internal\)$')"

for ARTIFACT_ID in ${ARTIFACTS}; do
  OLD_JAR="${BASEPATH}/${ARTIFACT_ID}/${OLD_ST_VERSION}/${ARTIFACT_ID}-${OLD_ST_VERSION}.jar"

  FOUND_OLD=$( (mvn -N -U dependency:get \
    -DgroupId="${GROUP_ID}" -DartifactId="${ARTIFACT_ID}" \
    -Dversion="${OLD_ST_VERSION}" -Dtransitive=false 1>&2 >/dev/null && echo true) ||
    echo false)

  if [ "${FOUND_OLD}" = "false" ] || [ ! -f "${OLD_JAR}" ]; then
    echo "# Skipping ${ARTIFACT_ID} : old artifact (${OLD_ST_VERSION}) not found"
    echo ""
    continue
  fi

  if [ "${LOCAL}" = "local" ]; then
    NEW_JAR="${ARTIFACT_ID}/build/libs/${ARTIFACT_ID}-${NEW_ST_VERSION}.jar"
  else
    mvn -N -U dependency:get -DgroupId="${GROUP_ID}" -DartifactId="${ARTIFACT_ID}" \
      -Dversion="${NEW_ST_VERSION}" -Dtransitive=false >/dev/null
    NEW_JAR="${BASEPATH}/${ARTIFACT_ID}/${NEW_ST_VERSION}/${ARTIFACT_ID}-${NEW_ST_VERSION}.jar"
  fi

  if [ ! -f "${NEW_JAR}" ]; then
    echo "# Skipping ${ARTIFACT_ID} : new artifact (${NEW_ST_VERSION}) not found"
    echo ""
    continue
  fi

  java -jar "$JAR_FILE" --no-error-on-exclusion-incompatibility --report-only-filename \
    -a protected -b --ignore-missing-classes --include-synthetic \
    --old "${OLD_JAR}" --new "${NEW_JAR}" | grep -v -- '--ignore-missing-classes'
  echo ""
done
