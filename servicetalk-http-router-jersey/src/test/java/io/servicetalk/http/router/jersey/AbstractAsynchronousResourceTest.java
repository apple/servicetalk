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

import io.servicetalk.http.api.HttpPayloadChunk;
import io.servicetalk.http.api.HttpResponse;

import org.junit.Ignore;
import org.junit.Test;

import static io.servicetalk.http.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpResponseStatuses.ACCEPTED;
import static io.servicetalk.http.api.HttpResponseStatuses.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatuses.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatuses.OK;
import static io.servicetalk.http.api.HttpResponseStatuses.PARTIAL_CONTENT;
import static io.servicetalk.http.router.jersey.resources.AsynchronousResources.PATH;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonEquals;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public abstract class AbstractAsynchronousResourceTest extends AbstractResourceTest {
    @Override
    String getResourcePath() {
        return PATH;
    }

    @Ignore("Remove this after https://github.com/eclipse-ee4j/jersey/issues/3672 is solved")
    @Override
    public void getJson() {
        // NOOP
    }

    @Ignore("Remove this after https://github.com/eclipse-ee4j/jersey/issues/3672 is solved")
    @Override
    public void putJsonResponse() {
        // NOOP
    }

    @Test
    public void getCompletable() {
        HttpResponse<HttpPayloadChunk> res =
                sendAndAssertResponse(get("/completable"), NO_CONTENT, null, is(""), $ -> null);
        assertThat(res.getHeaders().get("X-Foo-Prop"), is(newAsciiString("barProp")));

        res = sendAndAssertResponse(get("/completable?fail=true"), INTERNAL_SERVER_ERROR, null, "");
        // There is no mapper for DeliberateException hence it is propagated to the container and response filters
        // are thus bypassed.
        assertThat(res.getHeaders().contains("X-Foo-Prop"), is(false));
    }

    @Test
    public void postJsonBufSingleInSingleOut() {
        sendAndAssertResponse(post("/json-buf-sglin-sglout", "{\"key\":\"val5\"}", APPLICATION_JSON),
                OK, APPLICATION_JSON, jsonEquals("{\"key\":\"val5\",\"foo\":\"bar6\"}"), $ -> null);

        sendAndAssertResponse(post("/json-buf-sglin-sglout?fail=true", "{\"key\":\"val5\"}", APPLICATION_JSON),
                INTERNAL_SERVER_ERROR, null, "");
    }

    @Test
    public void getResponseSingle() {
        sendAndAssertResponse(get("/single-response"), ACCEPTED, TEXT_PLAIN, "DONE");

        sendAndAssertNoResponse(get("/single-response?fail=true"), INTERNAL_SERVER_ERROR);
    }

    @Test
    public void getResponseSinglePublisherEntity() {
        sendAndAssertResponse(get("/single-response-pub-entity?i=206"), PARTIAL_CONTENT, TEXT_PLAIN, "GOT: 206");
    }

    @Test
    public void getMapSingle() {
        sendAndAssertResponse(get("/single-map"), OK, APPLICATION_JSON,
                jsonEquals("{\"foo\":\"bar4\"}"), String::length);

        sendAndAssertNoResponse(get("/single-map?fail=true"), INTERNAL_SERVER_ERROR);
    }
}
