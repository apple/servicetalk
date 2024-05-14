#!/bin/bash

FIRST_GRADLE_TARGETS=""
SECOND_GRADLE_TARGETS=""

# Execute the printJavaTargetCompatibility task to get the java target compatibility for each subproject
# and extract the projects that require jdk9+.
while read -r line
do
  javaTarget=$(echo "$line" | sed -e 's/^version: \(.*\) name:.*/\1/g')
  if [ "$javaTarget" = "1.9" ] || [ "$javaTarget" = "1.10" ] || [ "$javaTarget" -gt "8" ] 2> /dev/null
  then
    currDir=$(echo "$line" | sed -e 's/^version:.* name: \(.*\)$/\1/g')
    FIRST_GRADLE_TARGETS="$FIRST_GRADLE_TARGETS :$currDir:clean :$currDir:check"
    SECOND_GRADLE_TARGETS="$SECOND_GRADLE_TARGETS :$currDir:publish"
  fi
done < <(./gradlew printJavaTargetCompatibility)

echo "FIRST_GRADLE_TARGETS=$FIRST_GRADLE_TARGETS"
echo ""
echo "SECOND_GRADLE_TARGETS=$SECOND_GRADLE_TARGETS"
echo ""
