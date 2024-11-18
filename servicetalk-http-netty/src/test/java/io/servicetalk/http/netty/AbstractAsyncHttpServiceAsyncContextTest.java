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
package io.servicetalk.http.netty;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

abstract class AbstractAsyncHttpServiceAsyncContextTest extends AbstractHttpServiceAsyncContextTest {

    @Test
    void newRequestsGetFreshContextImmediate() throws Exception {
        newRequestsGetFreshContext(true);
    }

    private static List<Arguments> params() {
        List<Arguments> params = new ArrayList<>();
        for (boolean useImmediate : Arrays.asList(false, true)) {
            for (InitContextKeyPlace place : InitContextKeyPlace.values()) {
                for (boolean asyncService : Arrays.asList(false, true)) {
                    if (!useImmediate && !asyncService) {
                        continue;
                    }
                    params.add(Arguments.of(useImmediate, place, asyncService));
                }
            }
        }
        return params;
    }

    @ParameterizedTest(name = "{displayName} [{index}]: useImmediate={0} initContextKeyPlace={1} asyncService={2}")
    @MethodSource("params")
    void contextPreservedOverFilterBoundariesAsync(boolean useImmediate, InitContextKeyPlace place,
                                                   boolean asyncService) throws Exception {
        contextPreservedOverFilterBoundaries(useImmediate, place, asyncService);
    }

    @ParameterizedTest(name = "{displayName} [{index}]: connectionAcceptorType={0}")
    @EnumSource(ConnectionAcceptorType.class)
    void connectionAcceptorContextDoesNotLeakImmediate(ConnectionAcceptorType type) throws Exception {
        connectionAcceptorContextDoesNotLeak(type, true);
    }
}
