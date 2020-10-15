/*
 * Copyright © 2020 Apple Inc. and the ServiceTalk project authors
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
package io.servicetalk.grpc.protoc;

import org.junit.Test;

import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class StringUtilsTest {
    @Test
    public void emptyOptions() {
        Map<String, String> options = StringUtils.parseOptions("");
        assertThat(options.isEmpty(), is(true));
    }

    @Test
    public void singleEntryNoValue() {
        Map<String, String> options = StringUtils.parseOptions("foo");
        assertThat(options.size(), is(1));
        assertContainsNullValue(options, "foo");
    }

    @Test
    public void singleEntryValue() {
        Map<String, String> options = StringUtils.parseOptions("foo=bar");
        assertThat(options.size(), is(1));
        assertThat(options.get("foo"), is("bar"));
    }

    @Test
    public void twoEntriesNoValues() {
        Map<String, String> options = StringUtils.parseOptions("foo1,foo2");
        assertThat(options.size(), is(2));
        assertContainsNullValue(options, "foo1");
        assertContainsNullValue(options, "foo2");
    }

    @Test
    public void twoEntriesFirstValue() {
        Map<String, String> options = StringUtils.parseOptions("foo1=bar1,foo2");
        assertThat(options.size(), is(2));
        assertThat(options.get("foo1"), is("bar1"));
        assertContainsNullValue(options, "foo2");
    }

    @Test
    public void twoEntriesSecondValue() {
        Map<String, String> options = StringUtils.parseOptions("foo1,foo2=bar2");
        assertThat(options.size(), is(2));
        assertContainsNullValue(options, "foo1");
        assertThat(options.get("foo2"), is("bar2"));
    }

    @Test
    public void twoEntriesBothValues() {
        Map<String, String> options = StringUtils.parseOptions("foo1=bar1,foo2=bar2");
        assertThat(options.size(), is(2));
        assertThat(options.get("foo1"), is("bar1"));
        assertThat(options.get("foo2"), is("bar2"));
    }

    @Test
    public void threeEntriesNoValues() {
        Map<String, String> options = StringUtils.parseOptions("foo1,foo2,foo3");
        assertThat(options.size(), is(3));
        assertContainsNullValue(options, "foo1");
        assertContainsNullValue(options, "foo2");
        assertContainsNullValue(options, "foo3");
    }

    @Test
    public void threeEntriesFirstValue() {
        Map<String, String> options = StringUtils.parseOptions("foo1=bar1,foo2,foo3");
        assertThat(options.size(), is(3));
        assertThat(options.get("foo1"), is("bar1"));
        assertContainsNullValue(options, "foo2");
        assertContainsNullValue(options, "foo3");
    }

    @Test
    public void threeEntriesSecondValue() {
        Map<String, String> options = StringUtils.parseOptions("foo1,foo2=bar2,foo3");
        assertThat(options.size(), is(3));
        assertContainsNullValue(options, "foo1");
        assertThat(options.get("foo2"), is("bar2"));
        assertContainsNullValue(options, "foo3");
    }

    @Test
    public void threeEntriesThirdValue() {
        Map<String, String> options = StringUtils.parseOptions("foo1,foo2,foo3=bar3");
        assertThat(options.size(), is(3));
        assertContainsNullValue(options, "foo1");
        assertContainsNullValue(options, "foo2");
        assertThat(options.get("foo3"), is("bar3"));
    }

    @Test
    public void threeEntriesFirstSecondValue() {
        Map<String, String> options = StringUtils.parseOptions("foo1=bar1,foo2=bar2,foo3");
        assertThat(options.size(), is(3));
        assertThat(options.get("foo1"), is("bar1"));
        assertThat(options.get("foo2"), is("bar2"));
        assertContainsNullValue(options, "foo3");
    }

    @Test
    public void threeEntriesFirstThirdValue() {
        Map<String, String> options = StringUtils.parseOptions("foo1=bar1,foo2,foo3=bar3");
        assertThat(options.size(), is(3));
        assertThat(options.get("foo1"), is("bar1"));
        assertContainsNullValue(options, "foo2");
        assertThat(options.get("foo3"), is("bar3"));
    }

    @Test
    public void threeEntriesSecondThirdValue() {
        Map<String, String> options = StringUtils.parseOptions("foo1,foo2=bar2,foo3=bar3");
        assertThat(options.size(), is(3));
        assertContainsNullValue(options, "foo1");
        assertThat(options.get("foo2"), is("bar2"));
        assertThat(options.get("foo3"), is("bar3"));
    }

    @Test
    public void threeEntriesValues() {
        Map<String, String> options = StringUtils.parseOptions("foo1=bar1,foo2=bar2,foo3=bar3");
        assertThat(options.size(), is(3));
        assertThat(options.get("foo1"), is("bar1"));
        assertThat(options.get("foo2"), is("bar2"));
        assertThat(options.get("foo3"), is("bar3"));
    }

    private static void assertContainsNullValue(Map<String, String> options, String key) {
        assertThat(options.containsKey(key), is(true));
        assertThat(options.get(key), is(nullValue()));
    }
}
