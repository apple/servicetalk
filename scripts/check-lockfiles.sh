#!/bin/bash
#
# Copyright © 2024 Apple Inc. and the ServiceTalk project authors
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

./gradlew resolveAndLockAll --write-locks
echo ""

status=$(git status --porcelain | grep 'gradle.lockfile' || true)
if [ -n "$status" ]; then
  echo "You MUST regenerate lockfiles using: ./gradlew resolveAndLockAll --write-locks"
  echo "The following files are not updated:"
  echo "$status"
  exit 1
else
  echo "All Gradle lockfiles are up to date"
fi
