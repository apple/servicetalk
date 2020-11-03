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

import io.servicetalk.http.api.HttpResponseStatus;

import org.junit.Test;

import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_0;
import static io.servicetalk.http.api.HttpResponseStatus.ACCEPTED;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.PARTIAL_CONTENT;
import static io.servicetalk.http.router.jersey.TestUtils.newLargePayload;
import static io.servicetalk.http.router.jersey.resources.SynchronousResources.PATH;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonEquals;
import static org.hamcrest.Matchers.is;

public class SynchronousResourceTest extends AbstractResourceTest {
    public SynchronousResourceTest(final boolean serverNoOffloads, final RouterApi api) {
        super(serverNoOffloads, api);
    }

    @Override
    String resourcePath() {
        return PATH;
    }

    @Test
    public void uriBuilding() {
        sendAndAssertResponse(get("/uris/relative"), OK, TEXT_PLAIN, "/async/text");
        sendAndAssertResponse(get("/uris/absolute"), OK, TEXT_PLAIN, "http://" + host() + "/sync/uris/absolute");
    }

    @Test
    public void queryParameterAreEncoded() {
        sendAndAssertResponse(get("/uris/relative?script=<foo;-/?:@=+$>"), OK, TEXT_PLAIN, "/async/text");
    }

    @Test
    public void customResponseStatus() {
        sendAndAssertNoResponse(get("/statuses/444"), HttpResponseStatus.of(444, "Three fours!"));
    }

    @Test
    public void pathParams() {
        sendAndAssertResponse(get("/matrix/ps;foo=bar1;foo=bar2/params;mp=bar3;mp=bar4"),
                OK, TEXT_PLAIN, "GOT: foo=bar1,bar2 & ps & bar3,bar4");
    }

    @Test
    public void bogusChunked() {
        sendAndAssertResponse(get("/bogus-chunked"), OK, TEXT_PLAIN, "foo");
    }

    @Test
    public void servicetalkRequestContext() {
        sendAndAssertResponse(get("/servicetalk-request"), OK, TEXT_PLAIN, "GOT: " + PATH + "/servicetalk-request");
    }

    @Test
    public void http10Support() {
        sendAndAssertResponse(get("/text").version(HTTP_1_0), HTTP_1_0, OK, TEXT_PLAIN, is("GOT: null & null"),
                __ -> 16);
    }

    @Test
    public void postTextStrInPubOut() {
        sendAndAssertResponse(post("/text-strin-pubout", "bar2", TEXT_PLAIN), OK, TEXT_PLAIN, is("GOT: bar2"),
                __ -> null);
    }

    @Test
    public void postTextPubInStrOut() {
        sendAndAssertResponse(post("/text-pubin-strout", "bar3", TEXT_PLAIN), OK, TEXT_PLAIN, "GOT: bar3");
    }

    @Test
    public void postTextPubInPubOut() {
        sendAndAssertResponse(post("/text-pubin-pubout", "bar23", TEXT_PLAIN), OK, TEXT_PLAIN, is("GOT: bar23"),
                __ -> null);
    }

    @Test
    public void getTextPubResponse() {
        sendAndAssertResponse(get("/text-pub-response?i=206"), PARTIAL_CONTENT, TEXT_PLAIN, "GOT: 206");
    }

    @Test
    public void postTextOioStreams() {
        // Small payload
        sendAndAssertResponse(post("/text-oio-streams", "bar4", TEXT_PLAIN), OK, TEXT_PLAIN, "GOT: bar4");

        // Large payload that goes above Jersey's default buffer size
        final CharSequence payload = newLargePayload();
        sendAndAssertResponse(post("/text-oio-streams", payload, TEXT_PLAIN), OK, TEXT_PLAIN,
                is("GOT: " + payload), __ -> null);
    }

    @Test
    public void postJsonOioStreams() {
        sendAndAssertResponse(post("/json-oio-streams", "{\"foo\":123}", APPLICATION_JSON), OK, APPLICATION_JSON,
                jsonEquals("{\"got\":{\"foo\":123}}"), String::length);
    }

    @Test
    public void postJsonMapInPubOut() {
        sendAndAssertResponse(post("/json-mapin-pubout", "{\"key\":\"val2\"}", APPLICATION_JSON),
                OK, APPLICATION_JSON, jsonEquals("{\"key\":\"val2\",\"foo\":\"bar3\"}"), __ -> null);
    }

    @Test
    public void postJsonPubInMapOut() {
        sendAndAssertResponse(post("/json-pubin-mapout", "{\"key\":\"val3\"}", APPLICATION_JSON),
                OK, APPLICATION_JSON, jsonEquals("{\"key\":\"val3\",\"foo\":\"bar4\"}"),
                getJsonResponseContentLengthExtractor());
    }

    @Test
    public void postJsonPubInPubOut() {
        sendAndAssertResponse(post("/json-pubin-pubout", "{\"key\":\"val4\"}", APPLICATION_JSON),
                OK, APPLICATION_JSON, jsonEquals("{\"key\":\"val4\",\"foo\":\"bar5\"}"), __ -> null);
    }

    @Test
    public void postJsonBufSingleInSingleOutResponse() {
        sendAndAssertResponse(post("/json-buf-sglin-sglout-response", "{\"key\":\"val6\"}", APPLICATION_JSON),
                ACCEPTED, APPLICATION_JSON, jsonEquals("{\"key\":\"val6\",\"foo\":\"bar6\"}"), __ -> null);
    }

    @Test
    public void postJsonBufPubInPubOut() {
        sendAndAssertResponse(post("/json-buf-pubin-pubout", "{\"key\":\"val6\"}", APPLICATION_JSON),
                OK, APPLICATION_JSON, jsonEquals("{\"KEY\":\"VAL6\"}"), __ -> null);
    }

    @Test
    public void postJsonBufPubInPubOutResponse() {
        sendAndAssertResponse(post("/json-buf-pubin-pubout-response", "{\"key\":\"val7\"}", APPLICATION_JSON),
                ACCEPTED, APPLICATION_JSON, jsonEquals("{\"KEY\":\"VAL7\"}"), __ -> null);
    }

    @Test
    public void postJsonPojoInPojoOutResponse() {
        sendAndAssertResponse(post("/json-pojoin-pojoout-response", "{\"aString\":\"val9\",\"anInt\":123}",
                APPLICATION_JSON), ACCEPTED, APPLICATION_JSON, jsonEquals("{\"aString\":\"val9x\",\"anInt\":124}"),
                getJsonResponseContentLengthExtractor());
    }

    @Test
    public void defaultSecurityContext() {
        sendAndAssertResponse(get("/security-context"), OK, APPLICATION_JSON,
                jsonEquals("{\"authenticationScheme\":null,\"secure\":false,\"userPrincipal\":null}"),
                getJsonResponseContentLengthExtractor());
    }
}
