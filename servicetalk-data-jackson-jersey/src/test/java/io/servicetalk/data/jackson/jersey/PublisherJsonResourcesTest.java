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

import io.servicetalk.http.api.HttpResponseStatus;
import io.servicetalk.http.router.jersey.TestUtils.ContentReadException;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.function.Executable;

import javax.annotation.Nullable;

import static io.servicetalk.data.jackson.jersey.resources.PublisherJsonResources.PATH;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.router.jersey.AbstractJerseyStreamingHttpServiceTest.RouterApi.ASYNC_AGGREGATED;
import static io.servicetalk.http.router.jersey.AbstractJerseyStreamingHttpServiceTest.RouterApi.ASYNC_STREAMING;
import static io.servicetalk.http.router.jersey.AbstractJerseyStreamingHttpServiceTest.RouterApi.BLOCKING_AGGREGATED;
import static io.servicetalk.http.router.jersey.AbstractJerseyStreamingHttpServiceTest.RouterApi.BLOCKING_STREAMING;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class PublisherJsonResourcesTest extends AbstractStreamingJsonResourcesTest {

    @Override
    protected String testUri(final String path) {
        return PATH + path;
    }

    // When a source publisher is map to a response publisher, errors that happen when the source is read
    // occur after the execution has left the Jersey router. This leads to 200 OK responses with a broken content
    // stream, as visible in the following tests overrides with the async router API or leads to a 500 ISE response
    // for aggregated router API.

    final HttpResponseStatus statusForFailedSerialization() {
        return api == ASYNC_STREAMING || api == BLOCKING_STREAMING ? OK : INTERNAL_SERVER_ERROR;
    }

    @Nullable
    final CharSequence contentTypeForFailedSerialization() {
        return api == ASYNC_STREAMING || api == BLOCKING_STREAMING ? APPLICATION_JSON : null;
    }

    private void skipWhenAggregatingDueToOffloadingIssueInCombinationWithJacksonAndAggregated() {
        assumeTrue(api != ASYNC_AGGREGATED && api != BLOCKING_AGGREGATED);
    }

    private void expectReadFailureWhenNotAggregating(Executable executable) {
        if (api == ASYNC_AGGREGATED || api == BLOCKING_AGGREGATED) {
            assertDoesNotThrow(executable);
        } else {
            assertThrows(ContentReadException.class, executable);
        }
    }

    @Override
    void postJsonMapFailure(RouterApi api) throws Exception {
        setUp(api);
        skipWhenAggregatingDueToOffloadingIssueInCombinationWithJacksonAndAggregated();
        expectReadFailureWhenNotAggregating(() ->
                sendAndAssertResponse(post("/map?fail=true", "{\"foo\":\"bar\"}", APPLICATION_JSON),
                        statusForFailedSerialization(), contentTypeForFailedSerialization(), ""));
    }

    @Override
    void postBrokenJsonMap(RouterApi api) throws Exception {
        setUp(api);
        expectReadFailureWhenNotAggregating(() ->
                sendAndAssertResponse(post("/map", "{key:789}", APPLICATION_JSON), statusForFailedSerialization(),
                contentTypeForFailedSerialization(), ""));
    }

    @Override
    void postJsonPojoFailure(RouterApi api) throws Exception {
        setUp(api);
        skipWhenAggregatingDueToOffloadingIssueInCombinationWithJacksonAndAggregated();
        expectReadFailureWhenNotAggregating(() ->
                sendAndAssertResponse(post("/pojo?fail=true", "{\"aString\":\"foo\",\"anInt\":123}", APPLICATION_JSON),
                        statusForFailedSerialization(), contentTypeForFailedSerialization(), ""));
    }

    @Override
    void postBrokenJsonPojo(RouterApi api) throws Exception {
        setUp(api);
        expectReadFailureWhenNotAggregating(() ->
                sendAndAssertResponse(post("/pojo", "{key:789}", APPLICATION_JSON),
                        statusForFailedSerialization(), contentTypeForFailedSerialization(), ""));
    }

    @Override
    void postInvalidJsonPojo(RouterApi api) throws Exception {
        setUp(api);
        expectReadFailureWhenNotAggregating(() ->
                sendAndAssertResponse(post("/pojo", "{\"foo\":\"bar\"}", APPLICATION_JSON),
                        statusForFailedSerialization(), contentTypeForFailedSerialization(), ""));
    }

    @Disabled("Remove this after read cancel stops closing channel")
    @Override
    void postBrokenJsonPojoResponse(RouterApi api) {
        // NOOP
    }

    @Disabled("Remove this after read cancel stops closing channel")
    @Override
    void postBrokenJsonMapResponse(RouterApi api) {
        // NOOP
    }

    @Disabled("Remove this after read cancel stops closing channel")
    @Override
    void postInvalidJsonPojoResponse(RouterApi api) {
        // NOOP
    }

    @Disabled("Remove this after read cancel stops closing channel")
    @Override
    void postJsonMapResponseFailure(RouterApi api) {
        // NOOP
    }

    @Disabled("Remove this after read cancel stops closing channel")
    @Override
    void postJsonPojoResponseFailure(RouterApi api) {
        // NOOP
    }
}
