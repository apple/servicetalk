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

import io.servicetalk.http.api.StreamingHttpResponse;
import io.servicetalk.http.router.jersey.resources.AsynchronousResources;

import org.junit.Test;

import static io.servicetalk.http.api.CharSequences.newAsciiString;
import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpResponseStatus.ACCEPTED;
import static io.servicetalk.http.api.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.servicetalk.http.api.HttpResponseStatus.NO_CONTENT;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.PARTIAL_CONTENT;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonEquals;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.junit.Assert.assertThat;

public abstract class AbstractAsynchronousResourceTest extends AbstractResourceTest {
    @Override
    String resourcePath() {
        return AsynchronousResources.PATH;
    }

    @Test
    public void getCompletable() {
        StreamingHttpResponse res =
                sendAndAssertResponse(get("/completable"), NO_CONTENT, null, isEmptyString(), __ -> null);
        assertThat(res.headers().get("X-Foo-Prop"), is(newAsciiString("barProp")));

        res = sendAndAssertResponse(get("/completable?fail=true"), INTERNAL_SERVER_ERROR, null, "");
        // There is no mapper for DeliberateException hence it is propagated to the container and response filters
        // are thus bypassed.
        assertThat(res.headers().contains("X-Foo-Prop"), is(false));
    }

    @Test
    public void getStringSingle() {
        sendAndAssertResponse(get("/single-string"), OK, TEXT_PLAIN, "DONE");

        sendAndAssertNoResponse(get("/single-string?fail=true"), INTERNAL_SERVER_ERROR);
    }

    @Test
    public void headStringSingle() {
        sendAndAssertResponse(head("/single-string"), OK, TEXT_PLAIN, isEmptyString(), 4);

        sendAndAssertNoResponse(head("/single-string?fail=true"), INTERNAL_SERVER_ERROR);
    }

    @Test
    public void headCompletable() {
        StreamingHttpResponse res =
                sendAndAssertResponse(head("/completable"), NO_CONTENT, null, isEmptyString(), __ -> null);
        assertThat(res.headers().get("X-Foo-Prop"), is(newAsciiString("barProp")));

        res = sendAndAssertResponse(head("/completable?fail=true"), INTERNAL_SERVER_ERROR, null, "");
        // There is no mapper for DeliberateException hence it is propagated to the container and response filters
        // are thus bypassed.
        assertThat(res.headers().contains("X-Foo-Prop"), is(false));
    }

    @Test
    public void postJsonBufSingleInSingleOut() {
        sendAndAssertResponse(post("/json-buf-sglin-sglout", "{\"key\":\"val5\"}", APPLICATION_JSON),
                OK, APPLICATION_JSON, jsonEquals("{\"key\":\"val5\",\"foo\":\"bar6\"}"), String::length);

        sendAndAssertResponse(post("/json-buf-sglin-sglout?fail=true", "{\"key\":\"val5\"}", APPLICATION_JSON),
                INTERNAL_SERVER_ERROR, null, "");
    }

    @Test
    public void getResponseSingle() {
        sendAndAssertResponse(get("/single-response"), ACCEPTED, TEXT_PLAIN, "DONE");

        sendAndAssertNoResponse(get("/single-response?fail=true"), INTERNAL_SERVER_ERROR);
    }

    @Test
    public void headResponseSingle() {
        sendAndAssertResponse(head("/single-response"), ACCEPTED, TEXT_PLAIN, isEmptyString(), 4);

        sendAndAssertNoResponse(head("/single-response?fail=true"), INTERNAL_SERVER_ERROR);
    }

    @Test
    public void getResponseSinglePublisherEntity() {
        sendAndAssertResponse(get("/single-response-pub-entity?i=206"), PARTIAL_CONTENT, TEXT_PLAIN, "GOT: 206");
    }

    @Test
    public void headResponseSinglePublisherEntity() {
        sendAndAssertResponse(head("/single-response-pub-entity?i=206"), PARTIAL_CONTENT, TEXT_PLAIN,
                isEmptyString(), 8);
    }

    @Test
    public void getMapSingle() {
        sendAndAssertResponse(get("/single-map"), OK, APPLICATION_JSON,
                jsonEquals("{\"foo\":\"bar4\"}"), getJsonResponseContentLengthExtractor());

        sendAndAssertNoResponse(get("/single-map?fail=true"), INTERNAL_SERVER_ERROR);
    }

    @Test
    public void headMapSingle() {
        sendAndAssertResponse(head("/single-map"), OK, APPLICATION_JSON, isEmptyString(),
                getJsonResponseContentLengthExtractor().andThen(i -> i != null ? 14 : null));

        sendAndAssertNoResponse(head("/single-map?fail=true"), INTERNAL_SERVER_ERROR);
    }

    @Test
    public void getPojoSingle() {
        sendAndAssertResponse(get("/single-pojo"), OK, APPLICATION_JSON,
                jsonEquals("{\"aString\":\"boo\",\"anInt\":456}"), getJsonResponseContentLengthExtractor());

        sendAndAssertNoResponse(get("/single-pojo?fail=true"), INTERNAL_SERVER_ERROR);
    }

    @Test
    public void headPojoSingle() {
        sendAndAssertResponse(head("/single-pojo"), OK, APPLICATION_JSON, isEmptyString(),
                getJsonResponseContentLengthExtractor().andThen(i -> i != null ? 29 : null));

        sendAndAssertNoResponse(head("/single-pojo?fail=true"), INTERNAL_SERVER_ERROR);
    }

    @Test
    public void postJsonPojoInPojoOutSingle() {
        sendAndAssertResponse(post("/json-pojoin-pojoout-single", "{\"aString\":\"val6\",\"anInt\":123}",
                APPLICATION_JSON), OK, APPLICATION_JSON,
                jsonEquals("{\"aString\":\"val6x\",\"anInt\":124}"), getJsonResponseContentLengthExtractor());

        sendAndAssertNoResponse(post("/json-pojoin-pojoout-single?fail=true", "{\"aString\":\"val8\",\"anInt\":123}",
                APPLICATION_JSON), INTERNAL_SERVER_ERROR);
    }

    @Test
    public void postJsonPojoInPojoOutResponseSingle() {
        sendAndAssertResponse(post("/json-pojoin-pojoout-response-single", "{\"aString\":\"val7\",\"anInt\":123}",
                APPLICATION_JSON), ACCEPTED, APPLICATION_JSON, jsonEquals("{\"aString\":\"val7x\",\"anInt\":124}"),
                getJsonResponseContentLengthExtractor());

        sendAndAssertNoResponse(post("/json-pojoin-pojoout-response-single?fail=true",
                "{\"aString\":\"val7\",\"anInt\":123}", APPLICATION_JSON), INTERNAL_SERVER_ERROR);
    }
}
