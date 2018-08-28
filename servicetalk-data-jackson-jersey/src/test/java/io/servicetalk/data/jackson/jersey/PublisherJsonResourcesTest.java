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

import io.servicetalk.http.router.jersey.TestUtils.ContentReadException;

import static io.servicetalk.data.jackson.jersey.resources.PublisherJsonResources.PATH;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;

public class PublisherJsonResourcesTest extends AbstractStreamingJsonResourcesTest {
    @Override
    protected String testUri(final String path) {
        return PATH + path;
    }

    // When a source publisher is map to a response publisher, errors that happen when the source is read
    // occur after the execution has left the Jersey router. This leads to 200 OK responses with a broken content
    // stream, as visible in the following tests overrides.

    @Override
    public void postJsonMapFailure() {
        expected.expect(ContentReadException.class);
        sendAndAssertResponse(post("/map?fail=true", "{\"foo\":\"bar\"}", APPLICATION_JSON), OK, APPLICATION_JSON, "");
    }

    @Override
    public void postBrokenJsonMap() {
        expected.expect(ContentReadException.class);
        sendAndAssertResponse(post("/map", "{key:789}", APPLICATION_JSON), OK, APPLICATION_JSON, "");
    }

    @Override
    public void postJsonPojoFailure() {
        expected.expect(ContentReadException.class);
        sendAndAssertResponse(post("/pojo?fail=true", "{\"aString\":\"foo\",\"anInt\":123}", APPLICATION_JSON), OK,
                APPLICATION_JSON, "");
    }

    @Override
    public void postBrokenJsonPojo() {
        expected.expect(ContentReadException.class);
        sendAndAssertResponse(post("/pojo", "{key:789}", APPLICATION_JSON), OK, APPLICATION_JSON, "");
    }

    @Override
    public void postInvalidJsonPojo() {
        expected.expect(ContentReadException.class);
        sendAndAssertResponse(post("/pojo", "{\"foo\":\"bar\"}", APPLICATION_JSON), OK, APPLICATION_JSON, "");
    }
}
