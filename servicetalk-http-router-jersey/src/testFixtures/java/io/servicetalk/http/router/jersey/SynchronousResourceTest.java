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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.servicetalk.http.api.HttpHeaderValues.APPLICATION_JSON;
import static io.servicetalk.http.api.HttpHeaderValues.TEXT_PLAIN;
import static io.servicetalk.http.api.HttpProtocolVersion.HTTP_1_0;
import static io.servicetalk.http.api.HttpResponseStatus.ACCEPTED;
import static io.servicetalk.http.api.HttpResponseStatus.BAD_REQUEST;
import static io.servicetalk.http.api.HttpResponseStatus.OK;
import static io.servicetalk.http.api.HttpResponseStatus.PARTIAL_CONTENT;
import static io.servicetalk.http.router.jersey.TestUtils.newLargePayload;
import static io.servicetalk.http.router.jersey.resources.SynchronousResources.PATH;
import static net.javacrumbs.jsonunit.JsonMatchers.jsonEquals;
import static org.hamcrest.Matchers.is;

class SynchronousResourceTest extends AbstractResourceTest {

    @Override
    String resourcePath() {
        return PATH;
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("io.servicetalk.http.router.jersey.AbstractResourceTest#data")
    void uriBuilding(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        sendAndAssertResponse(get("/uris/relative"), OK, TEXT_PLAIN, "/async/text");
        sendAndAssertResponse(get("/uris/absolute"), OK, TEXT_PLAIN, "http://" + host() + "/sync/uris/absolute");
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("io.servicetalk.http.router.jersey.AbstractResourceTest#data")
    void queryParameterAreEncoded(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        sendAndAssertResponse(get("/uris/relative?script=<foo;-/?:@=+$>"), BAD_REQUEST, TEXT_PLAIN,
            "Illegal character in query at index 49: http://" + host() + "/sync/uris/relative?script=<foo;-/?:@=+$>");
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("io.servicetalk.http.router.jersey.AbstractResourceTest#data")
    void customResponseStatus(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        sendAndAssertNoResponse(get("/statuses/444"), HttpResponseStatus.of(444, "Three fours!"));
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("io.servicetalk.http.router.jersey.AbstractResourceTest#data")
    void pathParams(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        sendAndAssertResponse(get("/matrix/ps;foo=bar1;foo=bar2/params;mp=bar3;mp=bar4"),
                OK, TEXT_PLAIN, "GOT: foo=bar1,bar2 & ps & bar3,bar4");
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("io.servicetalk.http.router.jersey.AbstractResourceTest#data")
    void bogusChunked(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        sendAndAssertResponse(get("/bogus-chunked"), OK, TEXT_PLAIN, "foo");
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("io.servicetalk.http.router.jersey.AbstractResourceTest#data")
    void servicetalkRequestContext(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        sendAndAssertResponse(get("/servicetalk-request"), OK, TEXT_PLAIN, "GOT: " + PATH + "/servicetalk-request");
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("io.servicetalk.http.router.jersey.AbstractResourceTest#data")
    void http10Support(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        sendAndAssertResponse(get("/text").version(HTTP_1_0), HTTP_1_0, OK, TEXT_PLAIN, is("GOT: null & null"),
                __ -> 16);
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("io.servicetalk.http.router.jersey.AbstractResourceTest#data")
    void postTextStrInPubOut(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        sendAndAssertResponse(post("/text-strin-pubout", "bar2", TEXT_PLAIN), OK, TEXT_PLAIN, is("GOT: bar2"),
                __ -> null);
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("io.servicetalk.http.router.jersey.AbstractResourceTest#data")
    void postTextPubInStrOut(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        sendAndAssertResponse(post("/text-pubin-strout", "bar3", TEXT_PLAIN), OK, TEXT_PLAIN, "GOT: bar3");
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("io.servicetalk.http.router.jersey.AbstractResourceTest#data")
    void postTextPubInPubOut(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        sendAndAssertResponse(post("/text-pubin-pubout", "bar23", TEXT_PLAIN), OK, TEXT_PLAIN, is("GOT: bar23"),
                __ -> null);
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("io.servicetalk.http.router.jersey.AbstractResourceTest#data")
    void getTextPubResponse(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        sendAndAssertResponse(get("/text-pub-response?i=206"), PARTIAL_CONTENT, TEXT_PLAIN, "GOT: 206");
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("io.servicetalk.http.router.jersey.AbstractResourceTest#data")
    void postTextOioStreams(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        // Small payload
        sendAndAssertResponse(post("/text-oio-streams", "bar4", TEXT_PLAIN), OK, TEXT_PLAIN, "GOT: bar4");

        // Large payload that goes above Jersey's default buffer size
        final CharSequence payload = newLargePayload();
        sendAndAssertResponse(post("/text-oio-streams", payload, TEXT_PLAIN), OK, TEXT_PLAIN,
                is("GOT: " + payload), __ -> null);
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("io.servicetalk.http.router.jersey.AbstractResourceTest#data")
    void postJsonOioStreams(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        sendAndAssertResponse(post("/json-oio-streams", "{\"foo\":123}", APPLICATION_JSON), OK, APPLICATION_JSON,
                jsonEquals("{\"got\":{\"foo\":123}}"), String::length);
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("io.servicetalk.http.router.jersey.AbstractResourceTest#data")
    void postJsonMapInPubOut(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        sendAndAssertResponse(post("/json-mapin-pubout", "{\"key\":\"val2\"}", APPLICATION_JSON),
                OK, APPLICATION_JSON, jsonEquals("{\"key\":\"val2\",\"foo\":\"bar3\"}"), __ -> null);
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("io.servicetalk.http.router.jersey.AbstractResourceTest#data")
    void postJsonPubInMapOut(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        sendAndAssertResponse(post("/json-pubin-mapout", "{\"key\":\"val3\"}", APPLICATION_JSON),
                OK, APPLICATION_JSON, jsonEquals("{\"key\":\"val3\",\"foo\":\"bar4\"}"),
                getJsonResponseContentLengthExtractor());
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("io.servicetalk.http.router.jersey.AbstractResourceTest#data")
    void postJsonPubInPubOut(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        sendAndAssertResponse(post("/json-pubin-pubout", "{\"key\":\"val4\"}", APPLICATION_JSON),
                OK, APPLICATION_JSON, jsonEquals("{\"key\":\"val4\",\"foo\":\"bar5\"}"), __ -> null);
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("io.servicetalk.http.router.jersey.AbstractResourceTest#data")
    void postJsonBufSingleInSingleOutResponse(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        sendAndAssertResponse(post("/json-buf-sglin-sglout-response", "{\"key\":\"val6\"}", APPLICATION_JSON),
                ACCEPTED, APPLICATION_JSON, jsonEquals("{\"key\":\"val6\",\"foo\":\"bar6\"}"), __ -> null);
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("io.servicetalk.http.router.jersey.AbstractResourceTest#data")
    void postJsonBufPubInPubOut(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        sendAndAssertResponse(post("/json-buf-pubin-pubout", "{\"key\":\"val6\"}", APPLICATION_JSON),
                OK, APPLICATION_JSON, jsonEquals("{\"KEY\":\"VAL6\"}"), __ -> null);
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("io.servicetalk.http.router.jersey.AbstractResourceTest#data")
    void postJsonBufPubInPubOutResponse(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        sendAndAssertResponse(post("/json-buf-pubin-pubout-response", "{\"key\":\"val7\"}", APPLICATION_JSON),
                ACCEPTED, APPLICATION_JSON, jsonEquals("{\"KEY\":\"VAL7\"}"), __ -> null);
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("io.servicetalk.http.router.jersey.AbstractResourceTest#data")
    void postJsonPojoInPojoOutResponse(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        sendAndAssertResponse(post("/json-pojoin-pojoout-response", "{\"aString\":\"val9\",\"anInt\":123}",
                APPLICATION_JSON), ACCEPTED, APPLICATION_JSON, jsonEquals("{\"aString\":\"val9x\",\"anInt\":124}"),
                getJsonResponseContentLengthExtractor());
    }

    @ParameterizedTest(name = "{1} server-no-offloads = {0}")
    @MethodSource("io.servicetalk.http.router.jersey.AbstractResourceTest#data")
    void defaultSecurityContext(final boolean serverNoOffloads, final RouterApi api) {
        setUp(serverNoOffloads, api);
        sendAndAssertResponse(get("/security-context"), OK, APPLICATION_JSON,
                jsonEquals("{\"authenticationScheme\":null,\"secure\":false,\"userPrincipal\":null}"),
                getJsonResponseContentLengthExtractor());
    }
}
