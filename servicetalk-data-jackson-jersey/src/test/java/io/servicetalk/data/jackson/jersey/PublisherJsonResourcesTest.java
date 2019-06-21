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

import org.junit.Ignore;

import javax.annotation.Nullable;

import static io.servicetalk.data.jackson.jersey.resources.PublisherJsonResources.PATH;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.router.jersey.AbstractNonParameterizedJerseyStreamingHttpServiceTest.RouterApi.ASYNC_AGGREGATED;
import static io.servicetalk.http.router.jersey.AbstractNonParameterizedJerseyStreamingHttpServiceTest.RouterApi.BLOCKING_AGGREGATED;
import static org.junit.Assume.assumeTrue;

public class PublisherJsonResourcesTest extends AbstractStreamingJsonResourcesTest {
    public PublisherJsonResourcesTest(final RouterApi api) {
        super(api);
    }

    @Override
    protected String testUri(final String path) {
        return PATH + path;
    }

    // When a source publisher is map to a response publisher, errors that happen when the source is read
    // occur after the execution has left the Jersey router. This leads to 200 OK responses with a broken content
    // stream, as visible in the following tests overrides with the async router API or leads to a 500 ISE response
    // for aggregated router api.

    final HttpResponseStatus statusForFailedSerialization() {
        switch (api) {
            case ASYNC_STREAMING:
            case BLOCKING_STREAMING:
                return OK;
            case BLOCKING_AGGREGATED:
            case ASYNC_AGGREGATED:
                return INTERNAL_SERVER_ERROR;
            default:
                throw new UnsupportedOperationException("Unexpected router API: " + api.name());
        }
    }

    @Nullable
    final CharSequence contentTypeForFailedSerialization() {
        switch (api) {
            case ASYNC_STREAMING:
            case BLOCKING_STREAMING:
                return APPLICATION_JSON;
            case BLOCKING_AGGREGATED:
            case ASYNC_AGGREGATED:
                return null;
            default:
                throw new UnsupportedOperationException("Unexpected router API: " + api.name());
        }
    }

    private void skipWhenAggregatingDueToOffloadingIssueInCombinationWithJacksonAndAggregated() {
        assumeTrue(api != ASYNC_AGGREGATED && api != BLOCKING_AGGREGATED);
    }

    private void expectReadFailureWhenNotAggregating() {
        if (api == ASYNC_AGGREGATED || api == BLOCKING_AGGREGATED) {
            return;
        }
        expected.expect(ContentReadException.class);
    }

    @Override
    public void postJsonMapFailure() {
        skipWhenAggregatingDueToOffloadingIssueInCombinationWithJacksonAndAggregated();
        expectReadFailureWhenNotAggregating();
        sendAndAssertResponse(post("/map?fail=true", "{\"foo\":\"bar\"}", APPLICATION_JSON),
                statusForFailedSerialization(), contentTypeForFailedSerialization(), "");
    }

    @Override
    public void postBrokenJsonMap() {
        expectReadFailureWhenNotAggregating();
        sendAndAssertResponse(post("/map", "{key:789}", APPLICATION_JSON), statusForFailedSerialization(),
                contentTypeForFailedSerialization(), "");
    }

    @Override
    public void postJsonPojoFailure() {
        skipWhenAggregatingDueToOffloadingIssueInCombinationWithJacksonAndAggregated();
        expectReadFailureWhenNotAggregating();
        sendAndAssertResponse(post("/pojo?fail=true", "{\"aString\":\"foo\",\"anInt\":123}", APPLICATION_JSON),
                statusForFailedSerialization(), contentTypeForFailedSerialization(), "");
    }

    @Override
    public void postBrokenJsonPojo() {
        expectReadFailureWhenNotAggregating();
        sendAndAssertResponse(post("/pojo", "{key:789}", APPLICATION_JSON),
                statusForFailedSerialization(), contentTypeForFailedSerialization(), "");
    }

    @Override
    public void postInvalidJsonPojo() {
        expectReadFailureWhenNotAggregating();
        sendAndAssertResponse(post("/pojo", "{\"foo\":\"bar\"}", APPLICATION_JSON),
                statusForFailedSerialization(), contentTypeForFailedSerialization(), "");
    }

    @Ignore("Remove this after read cancel stops closing channel")
    @Override
    public void postBrokenJsonPojoResponse() {
        // NOOP
    }

    @Ignore("Remove this after read cancel stops closing channel")
    @Override
    public void postBrokenJsonMapResponse() {
        // NOOP
    }

    @Ignore("Remove this after read cancel stops closing channel")
    @Override
    public void postInvalidJsonPojoResponse() {
        // NOOP
    }

    @Ignore("Remove this after read cancel stops closing channel")
    @Override
    public void postJsonMapResponseFailure() {
        // NOOP
    }

    @Ignore("Remove this after read cancel stops closing channel")
    @Override
    public void postJsonPojoResponseFailure() {
        // NOOP
    }
}
