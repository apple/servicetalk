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

import io.servicetalk.http.router.jersey.resources.AsynchronousResources;
import io.servicetalk.http.router.jersey.resources.SynchronousResources;

import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.core.Application;

import static java.util.Arrays.asList;

public class InputConsumingGlobalFiltersTest extends AbstractFilterInterceptorTest {

    public InputConsumingGlobalFiltersTest(final RouterApi api) {
        super(api);
    }

    public static class TestApplication extends Application {
        @Override
        public Set<Class<?>> getClasses() {
            return new HashSet<>(asList(
                    TestInputConsumingGlobalFilter.class,
                    SynchronousResources.class,
                    AsynchronousResources.class
            ));
        }
    }

    @Override
    protected Application application() {
        return new TestApplication();
    }
}
