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
#---------------------------------------------------------------------------
#
# Copyright (c) 2017-2018 Apple Inc. and the SwiftNIO project authors
# Licensed under Apache License v2.0
#
# See https://github.com/apple/swift-nio/blob/a6239a9005343842033ef63108f4d7a030de531e/LICENSE.txt for license information
# See https://github.com/apple/swift-nio/blob/a6239a9005343842033ef63108f4d7a030de531e/CONTRIBUTORS.txt for the list of SwiftNIO project authors
#
# SPDX-License-Identifier: Apache-2.0
#
#---------------------------------------------------------------------------
#
# Modified from https://github.com/apple/swift-nio/blob/a6239a9005343842033ef63108f4d7a030de531e/scripts/generate_contributors_list.sh
#

set -eu
here="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
contributors=$( cd "$here"/.. && git shortlog -es | cut -f2 | sed 's/^/- /' )

cat > "$here/../CONTRIBUTORS.txt" <<- EOF
	For the purpose of tracking copyright, this is the list of individuals and
	organizations who have contributed source code to ServiceTalk.

	For employees of an organization/company where the copyright of work done
	by employees of that company is held by the company itself, only the company
	needs to be listed here.

	## COPYRIGHT HOLDERS

	- Apple Inc. (all contributors with '@apple.com')

	### Contributors

	$contributors

	**Updating this list**

	Please do not edit this file manually. It is generated using \`./scripts/generate_contributors_list.sh\`. If a name is misspelled or appearing multiple times: add an entry in \`./.mailmap\`
EOF