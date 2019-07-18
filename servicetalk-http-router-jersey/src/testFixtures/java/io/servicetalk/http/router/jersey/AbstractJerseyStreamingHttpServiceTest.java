/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.router.jersey;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

@RunWith(Parameterized.class)
public abstract class AbstractJerseyStreamingHttpServiceTest
        extends AbstractNonParameterizedJerseyStreamingHttpServiceTest {

    protected AbstractJerseyStreamingHttpServiceTest(
            final AbstractJerseyStreamingHttpServiceTest.RouterApi api) {
        super(api);
    }

    /**
     * Some test classes eg. {@link ExecutionStrategyTest} come with their own parameters, JUnit only supports
     * a single level of parameters.
     */
    @SuppressWarnings("unused")
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.stream(AbstractJerseyStreamingHttpServiceTest.RouterApi.values())
                .map(api -> new Object[]{api}).collect(Collectors.toList());
    }
}
