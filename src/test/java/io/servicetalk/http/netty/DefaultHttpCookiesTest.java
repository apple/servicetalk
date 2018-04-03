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
package io.servicetalk.http.netty;

import io.servicetalk.http.api.HttpCookie;
import io.servicetalk.http.api.HttpCookies;
import io.servicetalk.http.api.HttpHeaders;

import io.netty.util.AsciiString;
import org.junit.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;

import static io.netty.util.AsciiString.contentEqualsIgnoreCase;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DefaultHttpCookiesTest {
    @Test
    public void decodeDuplicateNames() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("set-cookie", "qwerty=12345; Domain=somecompany.co.uk; Path=/; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("set-cookie", "qwerty=abcd; Domain=somecompany2.co.uk; Path=/2; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        HttpCookies cookies = headers.parseSetCookies();
        decodeDuplicateNames(cookies);

        // Encode now
        cookies.encodeToHttpHeaders();
        cookies = headers.parseSetCookies();
        decodeDuplicateNames(cookies);
    }

    private static void decodeDuplicateNames(HttpCookies cookies) {
        Iterator<? extends HttpCookie> cookieItr = cookies.getCookies("qwerty");
        assertTrue(cookieItr.hasNext());
        assertTrue(areCookiesEqual(new TestCookie("qwerty", "12345", "/", "somecompany.co.uk", "Wed, 30 Aug 2019 00:00:00 GMT",
                null, false, false, false), cookieItr.next()));
        assertTrue(cookieItr.hasNext());
        assertTrue(areCookiesEqual(new TestCookie("qwerty", "abcd", "/2", "somecompany2.co.uk", "Wed, 30 Aug 2019 00:00:00 GMT",
                null, false, false, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());
    }

    @Test
    public void decodeSecureCookieNames() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("set-cookie", "__Secure-ID=123; Secure; Domain=example.com");
        HttpCookies cookies = headers.parseSetCookies();
        decodeSecureCookieNames(cookies);

        // Encode now
        cookies.encodeToHttpHeaders();
        cookies = headers.parseSetCookies();
        decodeSecureCookieNames(cookies);
    }

    private static void decodeSecureCookieNames(HttpCookies cookies) {
        Iterator<? extends HttpCookie> cookieItr = cookies.getCookies("__Secure-ID");
        assertTrue(cookieItr.hasNext());
        assertTrue(areCookiesEqual(new TestCookie("__Secure-ID", "123", null, "example.com", null,
                null, false, true, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());
    }

    @Test
    public void decodeDifferentCookieNames() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("set-cookie", "foo=12345; Domain=somecompany.co.uk; Path=/; HttpOnly");
        headers.add("set-cookie", "bar=abcd; Domain=somecompany.co.uk; Path=/2; Max-Age=3000");
        HttpCookies cookies = headers.parseSetCookies();
        decodeDifferentCookieNames(cookies);

        // Encode now
        cookies.encodeToHttpHeaders();
        cookies = headers.parseSetCookies();
        decodeDifferentCookieNames(cookies);
    }

    private static void decodeDifferentCookieNames(HttpCookies cookies) {
        Iterator<? extends HttpCookie> cookieItr = cookies.getCookies("foo");
        assertTrue(cookieItr.hasNext());
        assertTrue(areCookiesEqual(new TestCookie("foo", "12345", "/", "somecompany.co.uk", null,
                null, false, false, true), cookieItr.next()));
        assertFalse(cookieItr.hasNext());
        cookieItr = cookies.getCookies("bar");
        assertTrue(cookieItr.hasNext());
        assertTrue(areCookiesEqual(new TestCookie("bar", "abcd", "/2", "somecompany.co.uk", null,
                3000L, false, false, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());
    }

    @Test
    public void removeSingleCookie() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("set-cookie", "foo=12345; Domain=somecompany.co.uk; Path=/; HttpOnly");
        headers.add("set-cookie", "bar=abcd; Domain=somecompany.co.uk; Path=/2; Max-Age=3000");
        HttpCookies cookies = headers.parseSetCookies();
        assertTrue(cookies.removeCookies("foo"));
        assertEquals(1, cookies.size());
        assertFalse(cookies.isEmpty());

        // Encode now
        cookies.encodeToHttpHeaders();
        cookies = headers.parseSetCookies();
        Iterator<? extends HttpCookie> cookieItr = cookies.getCookies("foo");
        assertFalse(cookieItr.hasNext());
        assertNull(cookies.getCookie("foo"));
        cookieItr = cookies.getCookies("bar");
        assertTrue(cookieItr.hasNext());
        assertTrue(areCookiesEqual(new TestCookie("bar", "abcd", "/2", "somecompany.co.uk", null,
                3000L, false, false, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());
        HttpCookie barCookie = cookies.getCookie("bar");
        assertNotNull(barCookie);
        assertTrue(areCookiesEqual(new TestCookie("bar", "abcd", "/2", "somecompany.co.uk", null,
                3000L, false, false, false), barCookie));
    }

    @Test
    public void removeMultipleCookiesSameName() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("set-cookie", "qwerty=12345; Domain=somecompany.co.uk; Path=/; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("set-cookie", "qwerty=abcd; Domain=somecompany2.co.uk; Path=/2; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        HttpCookies cookies = headers.parseSetCookies();
        assertTrue(cookies.removeCookies("qwerty"));
        assertEquals(0, cookies.size());
        assertTrue(cookies.isEmpty());

        // Encode now
        cookies.encodeToHttpHeaders();
        cookies = headers.parseSetCookies();
        Iterator<? extends HttpCookie> cookieItr = cookies.getCookies("qwerty");
        assertFalse(cookieItr.hasNext());
        assertNull(cookies.getCookie("qwerty"));
    }

    @Test
    public void addMultipleCookiesSameName() {
        HttpHeaders headers = new DefaultHttpHeaders();
        HttpCookies cookies = headers.parseSetCookies();
        TestCookie c1 = new TestCookie("qwerty", "12345", "/", "somecompany.co.uk", "Wed, 30 Aug 2019 00:00:00 GMT",
                null, false, false, false);
        cookies.addCookie(c1);
        TestCookie c2 = new TestCookie("qwerty", "abcd", "/2", "somecompany2.co.uk", "Wed, 30 Aug 2019 00:00:00 GMT",
                null, false, false, false);
        cookies.addCookie(c2);
        HttpCookie tmpCookie = cookies.getCookie("qwerty");
        assertNotNull(tmpCookie);
        assertTrue(areCookiesEqual(c1, tmpCookie));
        decodeDuplicateNames(cookies);

        // Encode now
        cookies.encodeToHttpHeaders();
        cookies = headers.parseSetCookies();
        decodeDuplicateNames(cookies);
    }

    @Test
    public void addMultipleCookiesDifferentName() {
        HttpHeaders headers = new DefaultHttpHeaders();
        HttpCookies cookies = headers.parseSetCookies();
        TestCookie fooCookie = new TestCookie("foo", "12345", "/", "somecompany.co.uk", null,
                null, false, false, true);
        cookies.addCookie(fooCookie);
        TestCookie barCookie = new TestCookie("bar", "abcd", "/2", "somecompany.co.uk", null,
                3000L, false, false, false);
        cookies.addCookie(barCookie);
        HttpCookie tmpCookie = cookies.getCookie("foo");
        assertNotNull(tmpCookie);
        assertTrue(areCookiesEqual(fooCookie, tmpCookie));
        tmpCookie = cookies.getCookie("bar");
        assertNotNull(tmpCookie);
        assertTrue(areCookiesEqual(barCookie, tmpCookie));

        // Encode now
        cookies.encodeToHttpHeaders();
        cookies = headers.parseSetCookies();
        decodeDifferentCookieNames(cookies);
    }

    @Test
    public void getCookieNameDomainEmptyPath() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("set-cookie", "qwerty=12345; Domain=somecompany.co.uk; Path=");
        HttpCookies cookies = headers.parseSetCookies();
        Iterator<? extends HttpCookie> cookieItr = cookies.getCookies("qwerty", "somecompany.co.uk", "");
        assertFalse(cookieItr.hasNext());

        cookieItr = cookies.getCookies("qwerty");
        assertTrue(cookieItr.hasNext());
        assertTrue(areCookiesEqual(new TestCookie("qwerty", "12345", null, "somecompany.co.uk", null,
                null, false, false, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());
    }

    @Test
    public void getAndRemoveCookiesNameDomainPath() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("set-cookie", "qwerty=12345; Domain=somecompany.co.uk; Path=/; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("set-cookie", "qwerty=abcd; Domain=somecompany2.co.uk; Path=/2; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        HttpCookies cookies = headers.parseSetCookies();
        getCookiesNameDomainPath(cookies);

        // Encode now
        cookies.encodeToHttpHeaders();
        cookies = headers.parseSetCookies();
        getCookiesNameDomainPath(cookies);

        // Removal now.
        assertTrue(cookies.removeCookies("qwerty", "somecompany2.co.uk", "/2"));
        assertEquals(1, cookies.size());
        assertFalse(cookies.isEmpty());
        Iterator<? extends HttpCookie> cookieItr = cookies.getCookies("qwerty", "somecompany2.co.uk", "/2");
        assertFalse(cookieItr.hasNext());

        // Encode again
        cookies.encodeToHttpHeaders();
        cookies = headers.parseSetCookies();
        cookieItr = cookies.getCookies("qwerty", "somecompany.co.uk", "/");
        assertTrue(cookieItr.hasNext());
        assertTrue(areCookiesEqual(new TestCookie("qwerty", "12345", "/", "somecompany.co.uk", "Wed, 30 Aug 2019 00:00:00 GMT",
                null, false, false, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());
    }

    private static void getCookiesNameDomainPath(HttpCookies cookies) {
        Iterator<? extends HttpCookie> cookieItr = cookies.getCookies("qwerty", "somecompany2.co.uk", "/2");
        assertTrue(cookieItr.hasNext());
        assertTrue(areCookiesEqual(new TestCookie("qwerty", "abcd", "/2", "somecompany2.co.uk", "Wed, 30 Aug 2019 00:00:00 GMT",
                null, false, false, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());
    }

    @Test
    public void getAndRemoveCookiesNameSubDomainPath() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("set-cookie", "qwerty=12345; Domain=foo.somecompany.co.uk; Path=/2; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("set-cookie", "qwerty=abcd; Domain=bar.somecompany.co.uk; Path=/2");
        headers.add("set-cookie", "qwerty=abxy; Domain=somecompany2.co.uk; Path=/2");
        headers.add("set-cookie", "qwerty=xyz; Domain=somecompany.co.uk; Path=/2");
        HttpCookies cookies = headers.parseSetCookies();

        Iterator<? extends HttpCookie> cookieItr = cookies.getCookies("qwerty", "somecompany.co.uk.", "/2");
        assertTrue(cookieItr.hasNext());
        assertTrue(areCookiesEqual(new TestCookie("qwerty", "12345", "/2", "foo.somecompany.co.uk", "Wed, 30 Aug 2019 00:00:00 GMT",
                null, false, false, false), cookieItr.next()));
        assertTrue(cookieItr.hasNext());
        assertTrue(areCookiesEqual(new TestCookie("qwerty", "abcd", "/2", "bar.somecompany.co.uk", null,
                null, false, false, false), cookieItr.next()));
        assertTrue(cookieItr.hasNext());
        assertTrue(areCookiesEqual(new TestCookie("qwerty", "xyz", "/2", "somecompany.co.uk", null,
                null, false, false, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());

        assertTrue(cookies.removeCookies("qwerty", "somecompany.co.uk.", "/2"));
        assertEquals(1, cookies.size());
        assertFalse(cookies.isEmpty());
        cookieItr = cookies.iterator();
        assertTrue(cookieItr.hasNext());
        assertTrue(areCookiesEqual(new TestCookie("qwerty", "abxy", "/2", "somecompany2.co.uk", null,
                null, false, false, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());
    }

    @Test
    public void getAndRemoveCookiesNameDomainSubPath() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("set-cookie", "qwerty=12345; Domain=somecompany.co.uk; Path=foo/bar; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("set-cookie", "qwerty=abcd; Domain=somecompany.co.uk; Path=/foo/bar/");
        headers.add("set-cookie", "qwerty=xyz; Domain=somecompany.co.uk; Path=/foo/barnot");
        headers.add("set-cookie", "qwerty=abxy; Domain=somecompany.co.uk; Path=/foo/bar/baz");
        HttpCookies cookies = headers.parseSetCookies();

        Iterator<? extends HttpCookie> cookieItr = cookies.getCookies("qwerty", "somecompany.co.uk", "/foo/bar");
        assertTrue(cookieItr.hasNext());
        assertTrue(areCookiesEqual(new TestCookie("qwerty", "12345", "foo/bar", "somecompany.co.uk", "Wed, 30 Aug 2019 00:00:00 GMT",
                null, false, false, false), cookieItr.next()));
        assertTrue(cookieItr.hasNext());
        assertTrue(areCookiesEqual(new TestCookie("qwerty", "abcd", "/foo/bar/", "somecompany.co.uk", null,
                null, false, false, false), cookieItr.next()));
        assertTrue(cookieItr.hasNext());
        assertTrue(areCookiesEqual(new TestCookie("qwerty", "abxy", "/foo/bar/baz", "somecompany.co.uk", null,
                null, false, false, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());

        assertTrue(cookies.removeCookies("qwerty", "somecompany.co.uk", "/foo/bar"));
        assertEquals(1, cookies.size());
        assertFalse(cookies.isEmpty());
        cookieItr = cookies.iterator();
        assertTrue(cookieItr.hasNext());
        assertTrue(areCookiesEqual(new TestCookie("qwerty", "xyz", "/foo/barnot", "somecompany.co.uk", null,
                null, false, false, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());
    }

    @Test
    public void percentEncodedValueCanBeDecoded() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("set-cookie", "qwerty=%x21%x23");
        HttpCookies cookies = headers.parseSetCookies();
        HttpCookie cookie = cookies.getCookie("qwerty");
        assertNotNull(cookie);
        assertTrue(areCookiesEqual(new TestCookie("qwerty", "%x21%x23", null, null, null,
                null, false, false, false), cookie));

        // Encode now
        cookies.encodeToHttpHeaders();
        cookies = headers.parseSetCookies();
        Iterator<? extends HttpCookie> cookieItr = cookies.getCookies("qwerty");
        assertTrue(cookieItr.hasNext());
        assertTrue(areCookiesEqual(new TestCookie("qwerty", "%x21%x23", null, null, null,
                null, false, false, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());
    }

    @Test
    public void percentEncodedNameCanBeDecoded() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("set-cookie", "%x21=foo");
        HttpCookies cookies = headers.parseSetCookies();
        HttpCookie cookie = cookies.getCookie("%x21");
        assertNotNull(cookie);
        assertTrue(areCookiesEqual(new TestCookie("%x21", "foo", null, null, null,
                null, false, false, false), cookie));

        // Encode now
        cookies.encodeToHttpHeaders();
        cookies = headers.parseSetCookies();
        Iterator<? extends HttpCookie> cookieItr = cookies.getCookies("%x21");
        assertTrue(cookieItr.hasNext());
        assertTrue(areCookiesEqual(new TestCookie("%x21", "foo", null, null, null,
                null, false, false, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());
    }

    @Test
    public void quotesInValuePreserved() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("set-cookie", "qwerty=\"12345\"; Domain=somecompany.co.uk; Path=/; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        HttpCookies cookies = headers.parseSetCookies();
        HttpCookie cookie = cookies.getCookie("qwerty");
        assertNotNull(cookie);
        assertTrue(areCookiesEqual(new TestCookie("qwerty", "12345", "/", "somecompany.co.uk", "Wed, 30 Aug 2019 00:00:00 GMT",
                null, true, false, false), cookie));

        // Encode again
        cookies.encodeToHttpHeaders();
        cookies = headers.parseSetCookies();
        Iterator<? extends HttpCookie> cookieItr = cookies.getCookies("qwerty");
        assertTrue(cookieItr.hasNext());
        assertTrue(areCookiesEqual(new TestCookie("qwerty", "12345", "/", "somecompany.co.uk", "Wed, 30 Aug 2019 00:00:00 GMT",
                null, true, false, false), cookieItr.next()));
        assertFalse(cookieItr.hasNext());

        CharSequence value = headers.get("set-cookie");
        assertTrue(AsciiString.contains(value, "qwerty=\"12345\""));
    }

    @Test
    public void getCookiesIteratorRemove() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("set-cookie", "foo=bar");
        headers.add("set-cookie", "qwerty=12345; Domain=somecompany.co.uk; Path=/; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("set-cookie", "qwerty=abcd; Domain=somecompany2.co.uk; Path=/2; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("set-cookie", "baz=xxx");
        HttpCookies cookies = headers.parseSetCookies();
        Iterator<? extends HttpCookie> cookieItr = cookies.getCookies("qwerty");
        assertTrue(cookieItr.hasNext());
        cookieItr.next();
        cookieItr.remove();
        assertTrue(cookieItr.hasNext());
        cookieItr.next();
        cookieItr.remove();
        assertFalse(cookieItr.hasNext());

        cookieItr = cookies.getCookies("qwerty");
        assertFalse(cookieItr.hasNext());

        // Encode now
        cookies.encodeToHttpHeaders();
        cookies = headers.parseSetCookies();
        cookieItr = cookies.getCookies("qwerty");
        assertFalse(cookieItr.hasNext());

        HttpCookie cookie = cookies.getCookie("foo");
        assertNotNull(cookie);
        assertTrue(areCookiesEqual(new TestCookie("foo", "bar", null, null, null,
                null, false, false, false), cookie));
        cookie = cookies.getCookie("baz");
        assertNotNull(cookie);
        assertTrue(areCookiesEqual(new TestCookie("baz", "xxx", null, null, null,
                null, false, false, false), cookie));
    }

    @Test
    public void overallIteratorRemoveFirstAndLast() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("set-cookie", "foo=bar");
        headers.add("set-cookie", "qwerty=12345; Domain=somecompany.co.uk; Path=/; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("set-cookie", "qwerty=abcd; Domain=somecompany2.co.uk; Path=/2; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("set-cookie", "baz=xxx");
        HttpCookies cookies = headers.parseSetCookies();
        // Overall iteration order isn't defined, so track which elements we don't expect to be present after removal.
        Set<String> removedNameValue = new HashSet<>();

        Iterator<? extends HttpCookie> cookieItr = cookies.iterator();
        // Remove the first and last element
        assertTrue(cookieItr.hasNext());
        HttpCookie cookie = cookieItr.next();
        removedNameValue.add(cookie.getName() + "=" + cookie.getValue());
        cookieItr.remove();

        assertTrue(cookieItr.hasNext());
        cookieItr.next();

        assertTrue(cookieItr.hasNext());
        cookieItr.next();

        assertTrue(cookieItr.hasNext());
        cookie = cookieItr.next();
        removedNameValue.add(cookie.getName() + "=" + cookie.getValue());
        cookieItr.remove();
        assertFalse(cookieItr.hasNext());

        // Encode now
        cookies.encodeToHttpHeaders();
        cookies = headers.parseSetCookies();
        cookieItr = cookies.iterator();
        assertTrue(cookieItr.hasNext());
        cookie = cookieItr.next();
        assertFalse(removedNameValue.contains(cookie.getName() + "=" + cookie.getValue()));

        assertTrue(cookieItr.hasNext());
        cookie = cookieItr.next();
        assertFalse(removedNameValue.contains(cookie.getName() + "=" + cookie.getValue()));

        assertFalse(cookieItr.hasNext());
    }

    @Test
    public void overallIteratorRemoveMiddle() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("cookie", "foo=bar");
        headers.add("cookie", "qwerty=12345; Domain=somecompany.co.uk; Path=/; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("cookie", "qwerty=abcd; Domain=somecompany2.co.uk; Path=/2; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("cookie", "baz=xxx");
        HttpCookies cookies = headers.parseCookies();
        // Overall iteration order isn't defined, so track which elements we don't expect to be present after removal.
        Set<String> removedNameValue = new HashSet<>();

        Iterator<? extends HttpCookie> cookieItr = cookies.iterator();
        // Remove the first and last element
        assertTrue(cookieItr.hasNext());
        cookieItr.next();

        assertTrue(cookieItr.hasNext());
        HttpCookie cookie = cookieItr.next();
        removedNameValue.add(cookie.getName() + "=" + cookie.getValue());
        cookieItr.remove();

        assertTrue(cookieItr.hasNext());
        cookie = cookieItr.next();
        removedNameValue.add(cookie.getName() + "=" + cookie.getValue());
        cookieItr.remove();

        assertTrue(cookieItr.hasNext());
        cookieItr.next();
        assertFalse(cookieItr.hasNext());

        // Encode now
        cookies.encodeToHttpHeaders();
        cookies = headers.parseCookies();
        cookieItr = cookies.iterator();
        assertTrue(cookieItr.hasNext());
        cookie = cookieItr.next();
        assertFalse(removedNameValue.contains(cookie.getName() + "=" + cookie.getValue()));

        assertTrue(cookieItr.hasNext());
        cookie = cookieItr.next();
        assertFalse(removedNameValue.contains(cookie.getName() + "=" + cookie.getValue()));

        assertFalse(cookieItr.hasNext());
    }

    @Test
    public void overallIteratorRemoveAll() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("set-cookie", "foo=bar");
        headers.add("set-cookie", "qwerty=12345; Domain=somecompany.co.uk; Path=/; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("set-cookie", "qwerty=abcd; Domain=somecompany2.co.uk; Path=/2; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.add("set-cookie", "baz=xxx");
        HttpCookies cookies = headers.parseSetCookies();
        Iterator<? extends HttpCookie> cookieItr = cookies.iterator();
        // Remove the first and last element
        assertTrue(cookieItr.hasNext());
        cookieItr.next();
        cookieItr.remove();

        assertTrue(cookieItr.hasNext());
        cookieItr.next();
        cookieItr.remove();

        assertTrue(cookieItr.hasNext());
        cookieItr.next();
        cookieItr.remove();

        assertTrue(cookieItr.hasNext());
        cookieItr.next();
        cookieItr.remove();
        assertFalse(cookieItr.hasNext());

        // Encode now
        cookies.encodeToHttpHeaders();
        cookies = headers.parseSetCookies();
        cookieItr = cookies.iterator();
        assertFalse(cookieItr.hasNext());
    }

    @Test(expected = IllegalArgumentException.class)
    public void noEqualsButQuotedValueThrows() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("set-cookie", "qwerty\"12345\"; Domain=somecompany.co.uk; Path=/; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.parseSetCookies();
    }

    @Test(expected = IllegalArgumentException.class)
    public void noEqualsValueWithAttributesThrows() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("set-cookie", "qwerty12345; Domain=somecompany.co.uk; Path=/; Expires=Wed, 30 Aug 2019 00:00:00 GMT");
        headers.parseSetCookies();
    }

    @Test(expected = IllegalArgumentException.class)
    public void noEqualsValueThrows() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("set-cookie", "qwerty12345");
        headers.parseSetCookies();
    }

    @Test(expected = IllegalArgumentException.class)
    public void trailingSemiColon() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("set-cookie", "qwerty=12345;");
        headers.parseSetCookies();
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidCookieName() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("set-cookie", "q@werty=12345");
        headers.parseSetCookies();
    }

    @Test
    public void invalidCookieNameNoThowIfNoValidate() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("set-cookie", "q@werty=12345");
        headers.parseSetCookies(false);
    }

    @Test(expected = IllegalStateException.class)
    public void valueIteratorThrowsIfNoNextCall() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("set-cookie", "qwerty=12345");
        HttpCookies cookies = headers.parseSetCookies();
        Iterator<HttpCookie> itr = cookies.iterator();
        assertTrue(itr.hasNext());
        itr.remove();
    }

    @Test(expected = IllegalStateException.class)
    public void entryIteratorThrowsIfDoubleRemove() {
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.add("set-cookie", "qwerty=12345");
        HttpCookies cookies = headers.parseSetCookies();
        Iterator<HttpCookie> itr = cookies.iterator();
        assertTrue(itr.hasNext());
        assertTrue(areCookiesEqual(new TestCookie("qwerty", "12345", null, null, null,
                null, false, false, false), itr.next()));
        itr.remove();
        assertTrue(cookies.isEmpty());
        assertEquals(0, cookies.size());
        itr.remove();
    }

    private static boolean areCookiesEqual(HttpCookie cookie1, HttpCookie cookie2) {
        return contentEqualsIgnoreCase(cookie1.getName(), cookie2.getName()) &&
                cookie1.getValue().equals(cookie2.getValue()) &&
                Objects.equals(cookie1.getDomain(), cookie2.getDomain()) &&
                Objects.equals(cookie1.getPath(), cookie2.getPath()) &&
                Objects.equals(cookie1.getExpires(), cookie2.getExpires()) &&
                Objects.equals(cookie1.getValue(), cookie2.getValue()) &&
                cookie1.isHttpOnly() == cookie2.isHttpOnly() &&
                cookie1.isSecure() == cookie2.isSecure() &&
                cookie1.isWrapped() == cookie2.isWrapped();
    }

    private static final class TestCookie implements HttpCookie {
        private final String name;
        private final String value;
        @Nullable
        private final String path;
        @Nullable
        private final String domain;
        @Nullable
        private final String expires;
        @Nullable
        private final Long maxAge;
        private final boolean isWrapped;
        private final boolean isSecure;
        private final boolean isHttpOnly;

        TestCookie(String name, String value, @Nullable String path,
                   @Nullable String domain, @Nullable String expires,
                   @Nullable Long maxAge, boolean isWrapped, boolean isSecure, boolean isHttpOnly) {
            this.name = name;
            this.value = value;
            this.path = path;
            this.domain = domain;
            this.expires = expires;
            this.maxAge = maxAge;
            this.isWrapped = isWrapped;
            this.isSecure = isSecure;
            this.isHttpOnly = isHttpOnly;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public boolean isWrapped() {
            return isWrapped;
        }

        @Nullable
        @Override
        public String getDomain() {
            return domain;
        }

        @Nullable
        @Override
        public String getPath() {
            return path;
        }

        @Nullable
        @Override
        public Long getMaxAge() {
            return maxAge;
        }

        @Nullable
        @Override
        public String getExpires() {
            return expires;
        }

        @Override
        public boolean isSecure() {
            return isSecure;
        }

        @Override
        public boolean isHttpOnly() {
            return isHttpOnly;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[" + name + "]";
        }
    }
}
