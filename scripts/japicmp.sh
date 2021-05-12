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

if [ $# -lt 2 ] || [ $# -gt 3 ]; then
  echo "This script compares versions for binary backward compatibility."
  echo "Usage: $0 old_version new_version [group_id]"
  exit 1
fi

MVN_REPO=$(mvn help:evaluate -Dexpression=settings.localRepository -q -DforceStdout)

JAPICMP_VERSION=0.15.3
JAR_FILE=$MVN_REPO/com/github/siom79/japicmp/japicmp/$JAPICMP_VERSION/japicmp-$JAPICMP_VERSION-jar-with-dependencies.jar
if ! test -e "$JAR_FILE"; then
    mvn -N dependency:get -DgroupId=com.github.siom79.japicmp -DartifactId=japicmp -Dversion=$JAPICMP_VERSION \
        -Dtransitive=false -Dclassifier=jar-with-dependencies 1>&2
fi

OLD_ST_VERSION=$1
NEW_ST_VERSION=$2
GROUP_ID=$3
if [ -z "$GROUP_ID" ]; then
  GROUP_ID=io.servicetalk
fi
GROUP_PATH=$(echo "$GROUP_ID" | tr '.' '/')
BASEPATH=$MVN_REPO/$GROUP_PATH/

# All servicetalk modules except:
# servicetalk-benchmarks, servicetalk-bom, servicetalk-examples, servicetalk-gradle-plugin-internal
ARTIFACTS="$(ls -d -- */ | grep 'servicetalk-' | sed 's/.$//' | \
  grep -v 'benchmark' | grep -v 'bom' | grep -v 'examples' | grep -v 'gradle-plugin-internal')"

for ARTIFACT_ID in $ARTIFACTS
do
  mvn -N dependency:get -DgroupId=$GROUP_ID -DartifactId=$ARTIFACT_ID -Dversion=$OLD_ST_VERSION \
      -Dtransitive=false >/dev/null
  mvn -N dependency:get -DgroupId=$GROUP_ID -DartifactId=$ARTIFACT_ID -Dversion=$NEW_ST_VERSION \
      -Dtransitive=false >/dev/null
  java -jar "$JAR_FILE" -b --ignore-missing-classes \
      --old $BASEPATH/$ARTIFACT_ID/$OLD_ST_VERSION/$ARTIFACT_ID-$OLD_ST_VERSION.jar \
      --new $BASEPATH/$ARTIFACT_ID/$NEW_ST_VERSION/$ARTIFACT_ID-$NEW_ST_VERSION.jar
  echo ""
done
