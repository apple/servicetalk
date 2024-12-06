/*
 * Copyright © 2024 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.loadbalancer;

import java.util.ArrayList;
import java.util.List;

public final class ConnectionSelectorHelpers {

    private ConnectionSelectorHelpers() {
        // no instances
    }

    static List<TestLoadBalancedConnection> makeConnections(int size) {
        List<TestLoadBalancedConnection> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(TestLoadBalancedConnection.mockConnection("address-" + i));
        }
        return result;
    }
}
