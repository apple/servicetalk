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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static io.servicetalk.data.jackson.jersey.resources.SingleJsonResources.PATH;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpResponseStatus.BAD_REQUEST;

class SingleJsonResourcesTest extends AbstractStreamingJsonResourcesTest {

    @Override
    protected String testUri(final String path) {
        return PATH + path;
    }

    @ParameterizedTest
    @EnumSource(RouterApi.class)
    void postTooManyJsonMaps(RouterApi api) throws Exception {
        setUp(api);
        sendAndAssertStatusOnly(post("/map", "{\"foo1\":\"bar1\"}{\"foo2\":\"bar2\"}", APPLICATION_JSON), BAD_REQUEST);
    }
}
