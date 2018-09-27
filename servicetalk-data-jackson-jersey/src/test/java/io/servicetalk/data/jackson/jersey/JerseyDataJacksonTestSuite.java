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
package io.servicetalk.data.jackson.jersey;

import io.servicetalk.http.router.jersey.BaseJerseyRouterTestSuite;

import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import static org.hamcrest.Matchers.is;
import static org.junit.Assume.assumeThat;

@RunWith(Suite.class)
@SuiteClasses({
        SingleJsonResourcesTest.class,
        PublisherJsonResourcesTest.class,
        JsonMicroformatResourcesTest.class,
        CustomJacksonSerializationProviderTest.class,
        FilterStreamingJsonTest.class,
        BaseJerseyRouterTestSuite.class
})
public class JerseyDataJacksonTestSuite {
    @BeforeClass
    public static void wap() {
        boolean jerseyJacksonOnClasspath = false;
        try {
            Class.forName("org.glassfish.jersey.jackson.JacksonFeature");
            jerseyJacksonOnClasspath = true;
        } catch (Throwable t) {
            // NOOP
        }
        assumeThat("Test suite disabled because Jersey's Jackson serializer is on the classpath",
                jerseyJacksonOnClasspath, is(false));
    }
}
