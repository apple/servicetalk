/*
 * Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.http.utils;

import io.servicetalk.transport.api.HostAndPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.annotation.Nullable;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UriReferenceTest {

    private static final UriReference BASE = UriReference.parse("http://a/b/c/d;p?q");

    // Expected values are from RFC 3986, sections 5.4.1 and 5.4.2, with the fragment removed.
    @ParameterizedTest(name = "{displayName} [{index}] \"{0}\" -> \"{1}\"")
    @CsvSource({
            // 5.4.1. Normal Examples
            "g:h,           g:h",
            "g,             http://a/b/c/g",
            "./g,           http://a/b/c/g",
            "g/,            http://a/b/c/g/",
            "/g,            http://a/g",
            "//g,           http://g",
            "?y,            http://a/b/c/d;p?y",
            "g?y,           http://a/b/c/g?y",
            "#s,            http://a/b/c/d;p?q#s",
            "g#s,           http://a/b/c/g#s",
            "g?y#s,         http://a/b/c/g?y#s",
            ";x,            http://a/b/c/;x",
            "g;x,           http://a/b/c/g;x",
            "g;x?y#s,       http://a/b/c/g;x?y#s",
            "'',            http://a/b/c/d;p?q",
            ".,             http://a/b/c/",
            "./,            http://a/b/c/",
            "..,            http://a/b/",
            "../,           http://a/b/",
            "../g,          http://a/b/g",
            "../..,         http://a/",
            "../../,        http://a/",
            "../../g,       http://a/g",
            // 5.4.2. Abnormal Examples
            "../../../g,    http://a/g",
            "../../../../g, http://a/g",
            "/./g,          http://a/g",
            "/../g,         http://a/g",
            "g.,            http://a/b/c/g.",
            ".g,            http://a/b/c/.g",
            "g..,           http://a/b/c/g..",
            "..g,           http://a/b/c/..g",
            "./../g,        http://a/b/g",
            "./g/.,         http://a/b/c/g/",
            "g/./h,         http://a/b/c/g/h",
            "g/../h,        http://a/b/c/h",
            "g;x=1/./y,     http://a/b/c/g;x=1/y",
            "g;x=1/../y,    http://a/b/c/y",
            "g?y/./x,       http://a/b/c/g?y/./x",
            "g?y/../x,      http://a/b/c/g?y/../x",
            "g#s/./x,       http://a/b/c/g#s/./x",
            "g#s/../x,      http://a/b/c/g#s/../x",
            "http:g,        http:g",
    })
    void resolveRfc3986Examples(String reference, String expected) {
        assertThat(BASE.resolve(reference).toString(), is(expected.replaceFirst("#.*", "")));
    }

    @ParameterizedTest(name = "{displayName} [{index}] \"{0}\" -> \"{1}\"")
    @CsvSource({
            "http://x/a/../b, http://x/b",
            "http://x/./a/b,  http://x/a/b",
            "//x/a/./b,       http://x/a/b",
            "//x/a/../../b,   http://x/b",
    })
    void resolveRemovesDotSegmentsFromAbsoluteAndNetworkPathReferences(String reference, String expected) {
        assertThat(BASE.resolve(reference).toString(), is(expected));
    }

    @Test
    void resolveKeepsPercentEncodedDotSegments() {
        assertThat(BASE.resolve("/a/%2e%2e/b").toString(), is("http://a/a/%2e%2e/b"));
    }

    @Test
    void resolveRelativePathAgainstEmptyBasePathWithAuthority() {
        assertThat(UriReference.parse("http://a").resolve("g").toString(), is("http://a/g"));
    }

    @ParameterizedTest(name = "{displayName} [{index}] \"{0}\"")
    @ValueSource(strings = {"://a/b", ":g", ":"})
    void parseRelativePathWithColonInFirstSegmentThrows(String reference) {
        assertThrows(IllegalArgumentException.class, () -> UriReference.parse(reference));
    }

    @ParameterizedTest(
            name = "{displayName} [{index}] \"{0}\" -> scheme={1} authority={2} path={3} query={4}")
    @CsvSource({
            "'', , , '', ",
            "a:, a, , '', ",
            "//, , '', '', ",
            "?#, , , '', ''",
    })
    void parseEmptyComponents(String reference, @Nullable String expectedScheme, @Nullable String expectedAuthority,
                              String expectedPath, @Nullable String expectedQuery) {
        UriReference uriReference = UriReference.parse(reference);
        assertThat(uriReference.scheme, is(expectedScheme));
        assertThat(uriReference.authority, is(expectedAuthority));
        assertThat(uriReference.path, is(expectedPath));
        assertThat(uriReference.query, is(expectedQuery));
    }

    @Test
    void parseFragmentWithLineTerminator() {
        assertThat(UriReference.parse("/x#\u0085").toString(), is("/x"));
    }

    @Test
    void parseRelativePathWithColonAfterFirstSegment() {
        assertThat(UriReference.parse("./a:b").path, is("./a:b"));
    }

    @ParameterizedTest(name = "{displayName} [{index}] \"{0}\" -> host={1} port={2}")
    @CsvSource({
            "//a,              a,         -1",
            "//a:8080,         a,         8080",
            "//a:,             a,         -1",
            "//u:p@a,          a,         -1",
            "//u:p@a:8080,     a,         8080",
            "//[::1],          [::1],     -1",
            "//[::1]:8080,     [::1],     8080",
            "//u@[::1]:8080,   [::1],     8080",
    })
    void hostAndPort(String reference, String expectedHost, int expectedPort) {
        HostAndPort hostAndPort = UriReference.parse(reference).hostAndPort();
        assertThat(hostAndPort, is(HostAndPort.of(expectedHost, expectedPort)));
    }

    @Test
    void hostAndPortWithoutAuthorityIsNull() {
        assertThat(UriReference.parse("/a").hostAndPort(), is(nullValue()));
    }
}
