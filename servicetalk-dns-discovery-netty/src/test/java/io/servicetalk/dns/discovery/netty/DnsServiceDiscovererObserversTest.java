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
package io.servicetalk.dns.discovery.netty;

import io.servicetalk.dns.discovery.netty.DnsServiceDiscovererObserver.DnsDiscoveryObserver;
import io.servicetalk.dns.discovery.netty.DnsServiceDiscovererObserver.DnsResolutionObserver;
import io.servicetalk.dns.discovery.netty.DnsServiceDiscovererObserver.ResolutionResult;

import org.junit.jupiter.api.Test;

import java.util.List;

import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.dns.discovery.netty.DnsServiceDiscovererObservers.asSafeObserver;
import static io.servicetalk.dns.discovery.netty.DnsServiceDiscovererObservers.combine;
import static io.servicetalk.dns.discovery.netty.DnsServiceDiscovererObservers.unpack;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DnsServiceDiscovererObserversTest {

    private static final String ID = "sd-id";
    private static final String NAME = "service.example.com";
    private static final String QUESTION = "question.example.com";

    // ------------------------------------------------------------------ combine

    @Test
    void combineRequiresAtLeastOneObserver() {
        assertThrows(IllegalArgumentException.class, DnsServiceDiscovererObservers::combine);
    }

    @Test
    void combineSingleReturnsSameInstance() {
        DnsServiceDiscovererObserver observer = mock(DnsServiceDiscovererObserver.class);
        assertThat(combine(observer), sameInstance(observer));
    }

    @Test
    void combineNullElementThrows() {
        DnsServiceDiscovererObserver observer = mock(DnsServiceDiscovererObserver.class);
        assertThrows(NullPointerException.class, () -> combine(observer, null));
    }

    @Test
    void combineFansOutEveryEventToAllObservers() {
        DnsServiceDiscovererObserver o1 = mock(DnsServiceDiscovererObserver.class);
        DnsServiceDiscovererObserver o2 = mock(DnsServiceDiscovererObserver.class);
        DnsDiscoveryObserver d1 = mock(DnsDiscoveryObserver.class);
        DnsDiscoveryObserver d2 = mock(DnsDiscoveryObserver.class);
        DnsResolutionObserver r1 = mock(DnsResolutionObserver.class);
        DnsResolutionObserver r2 = mock(DnsResolutionObserver.class);
        ResolutionResult result = mock(ResolutionResult.class);
        when(o1.onNewDiscovery(ID, NAME)).thenReturn(d1);
        when(o2.onNewDiscovery(ID, NAME)).thenReturn(d2);
        when(d1.onNewResolution(QUESTION)).thenReturn(r1);
        when(d2.onNewResolution(QUESTION)).thenReturn(r2);

        DnsServiceDiscovererObserver combined = combine(o1, o2);

        DnsDiscoveryObserver discovery = combined.onNewDiscovery(ID, NAME);
        verify(o1).onNewDiscovery(ID, NAME);
        verify(o2).onNewDiscovery(ID, NAME);

        DnsResolutionObserver resolution = discovery.onNewResolution(QUESTION);
        verify(d1).onNewResolution(QUESTION);
        verify(d2).onNewResolution(QUESTION);

        resolution.resolutionCompleted(result);
        verify(r1).resolutionCompleted(result);
        verify(r2).resolutionCompleted(result);

        resolution.resolutionFailed(DELIBERATE_EXCEPTION);
        verify(r1).resolutionFailed(DELIBERATE_EXCEPTION);
        verify(r2).resolutionFailed(DELIBERATE_EXCEPTION);

        discovery.discoveryCancelled();
        verify(d1).discoveryCancelled();
        verify(d2).discoveryCancelled();

        discovery.discoveryFailed(DELIBERATE_EXCEPTION);
        verify(d1).discoveryFailed(DELIBERATE_EXCEPTION);
        verify(d2).discoveryFailed(DELIBERATE_EXCEPTION);
    }

    @Test
    @SuppressWarnings("deprecation")
    void combineFansOutDeprecatedOnNewDiscovery() {
        DnsServiceDiscovererObserver o1 = mock(DnsServiceDiscovererObserver.class);
        DnsServiceDiscovererObserver o2 = mock(DnsServiceDiscovererObserver.class);
        when(o1.onNewDiscovery(NAME)).thenReturn(mock(DnsDiscoveryObserver.class));
        when(o2.onNewDiscovery(NAME)).thenReturn(mock(DnsDiscoveryObserver.class));

        combine(o1, o2).onNewDiscovery(NAME);

        verify(o1).onNewDiscovery(NAME);
        verify(o2).onNewDiscovery(NAME);
    }

    @Test
    void combineIsSafeAgainstThrowingObserver() {
        DnsServiceDiscovererObserver good = mock(DnsServiceDiscovererObserver.class);
        DnsServiceDiscovererObserver bad = mock(DnsServiceDiscovererObserver.class);
        DnsDiscoveryObserver goodDiscovery = mock(DnsDiscoveryObserver.class);
        DnsDiscoveryObserver badDiscovery = mock(DnsDiscoveryObserver.class);
        when(good.onNewDiscovery(ID, NAME)).thenReturn(goodDiscovery);
        when(bad.onNewDiscovery(ID, NAME)).thenReturn(badDiscovery);
        doThrow(DELIBERATE_EXCEPTION).when(badDiscovery).discoveryCancelled();

        // 'bad' comes first: its thrown exception must not prevent 'good' from being notified.
        DnsServiceDiscovererObserver combined = combine(bad, good);
        DnsDiscoveryObserver discovery = assertDoesNotThrow(() -> combined.onNewDiscovery(ID, NAME));

        assertDoesNotThrow(discovery::discoveryCancelled);
        verify(badDiscovery).discoveryCancelled();
        verify(goodDiscovery).discoveryCancelled();
    }

    // ------------------------------------------------------------------ asSafeObserver

    @Test
    void asSafeObserverWrapsPlainObserver() {
        DnsServiceDiscovererObserver observer = mock(DnsServiceDiscovererObserver.class);
        DnsServiceDiscovererObserver safe = asSafeObserver(observer);
        assertThat(safe, notNullValue());
        assertThat(safe, not(sameInstance(observer)));
    }

    @Test
    void asSafeObserverIsIdempotent() {
        DnsServiceDiscovererObserver safe = asSafeObserver(mock(DnsServiceDiscovererObserver.class));
        assertThat(asSafeObserver(safe), sameInstance(safe));
    }

    @Test
    void asSafeObserverDoesNotRewrapCombined() {
        DnsServiceDiscovererObserver combined = combine(mock(DnsServiceDiscovererObserver.class),
                mock(DnsServiceDiscovererObserver.class));
        assertThat(asSafeObserver(combined), sameInstance(combined));
    }

    @Test
    void asSafeObserverSwallowsExceptionFromOnNewDiscovery() {
        DnsServiceDiscovererObserver observer = mock(DnsServiceDiscovererObserver.class);
        when(observer.onNewDiscovery(anyString(), anyString())).thenThrow(DELIBERATE_EXCEPTION);

        DnsServiceDiscovererObserver safe = asSafeObserver(observer);
        DnsDiscoveryObserver discovery = assertDoesNotThrow(() -> safe.onNewDiscovery(ID, NAME));
        assertThat("Must fall back to a non-null noop observer", discovery, notNullValue());
        // The returned noop must remain safe to use.
        assertDoesNotThrow(discovery::discoveryCancelled);
        assertDoesNotThrow(() -> assertThat(discovery.onNewResolution(QUESTION), notNullValue()));
    }

    @Test
    void asSafeObserverHandlesNullReturnedObserver() {
        DnsServiceDiscovererObserver observer = mock(DnsServiceDiscovererObserver.class);
        when(observer.onNewDiscovery(anyString(), anyString())).thenReturn(null);

        DnsServiceDiscovererObserver safe = asSafeObserver(observer);
        DnsDiscoveryObserver discovery = assertDoesNotThrow(() -> safe.onNewDiscovery(ID, NAME));
        assertThat("A null observer must be replaced with a non-null noop", discovery, notNullValue());
    }

    @Test
    void asSafeObserverSwallowsExceptionFromTerminalEvents() {
        DnsServiceDiscovererObserver observer = mock(DnsServiceDiscovererObserver.class);
        DnsDiscoveryObserver discovery = mock(DnsDiscoveryObserver.class);
        DnsResolutionObserver resolution = mock(DnsResolutionObserver.class);
        when(observer.onNewDiscovery(ID, NAME)).thenReturn(discovery);
        when(discovery.onNewResolution(QUESTION)).thenReturn(resolution);
        doThrow(DELIBERATE_EXCEPTION).when(discovery).discoveryFailed(any());
        doThrow(DELIBERATE_EXCEPTION).when(resolution).resolutionCompleted(any());

        DnsServiceDiscovererObserver safe = asSafeObserver(observer);
        DnsDiscoveryObserver safeDiscovery = safe.onNewDiscovery(ID, NAME);
        assertDoesNotThrow(() -> safeDiscovery.discoveryFailed(DELIBERATE_EXCEPTION));
        verify(discovery).discoveryFailed(DELIBERATE_EXCEPTION);

        DnsResolutionObserver safeResolution = safeDiscovery.onNewResolution(QUESTION);
        assertDoesNotThrow(() -> safeResolution.resolutionCompleted(mock(ResolutionResult.class)));
        verify(resolution).resolutionCompleted(any());
    }

    @Test
    void asSafeObserverSwallowsExceptionFromResolutionFailed() {
        DnsServiceDiscovererObserver observer = mock(DnsServiceDiscovererObserver.class);
        DnsDiscoveryObserver discovery = mock(DnsDiscoveryObserver.class);
        DnsResolutionObserver resolution = mock(DnsResolutionObserver.class);
        when(observer.onNewDiscovery(ID, NAME)).thenReturn(discovery);
        when(discovery.onNewResolution(QUESTION)).thenReturn(resolution);
        doThrow(DELIBERATE_EXCEPTION).when(resolution).resolutionFailed(any());

        DnsResolutionObserver safeResolution = asSafeObserver(observer).onNewDiscovery(ID, NAME)
                .onNewResolution(QUESTION);
        assertDoesNotThrow(() -> safeResolution.resolutionFailed(DELIBERATE_EXCEPTION));
        verify(resolution).resolutionFailed(DELIBERATE_EXCEPTION);
    }

    @Test
    void asSafeObserverSwallowsExceptionFromOnNewResolution() {
        DnsServiceDiscovererObserver observer = mock(DnsServiceDiscovererObserver.class);
        DnsDiscoveryObserver discovery = mock(DnsDiscoveryObserver.class);
        when(observer.onNewDiscovery(ID, NAME)).thenReturn(discovery);
        when(discovery.onNewResolution(anyString())).thenThrow(DELIBERATE_EXCEPTION);

        DnsDiscoveryObserver safeDiscovery = asSafeObserver(observer).onNewDiscovery(ID, NAME);
        DnsResolutionObserver resolution = assertDoesNotThrow(() -> safeDiscovery.onNewResolution(QUESTION));
        assertThat("Must fall back to a non-null noop resolution observer", resolution, notNullValue());
        // The returned noop must remain safe to use.
        assertDoesNotThrow(() -> resolution.resolutionCompleted(mock(ResolutionResult.class)));
        assertDoesNotThrow(() -> resolution.resolutionFailed(DELIBERATE_EXCEPTION));
    }

    @Test
    @SuppressWarnings("deprecation")
    void asSafeObserverSwallowsExceptionFromDeprecatedOnNewDiscovery() {
        DnsServiceDiscovererObserver observer = mock(DnsServiceDiscovererObserver.class);
        when(observer.onNewDiscovery(anyString())).thenThrow(DELIBERATE_EXCEPTION);

        DnsServiceDiscovererObserver safe = asSafeObserver(observer);
        DnsDiscoveryObserver discovery = assertDoesNotThrow(() -> safe.onNewDiscovery(NAME));
        assertThat("Must fall back to a non-null noop observer", discovery, notNullValue());
        assertDoesNotThrow(discovery::discoveryCancelled);
    }

    @Test
    void asSafeObserverAttachesOriginalCauseAsSuppressed() {
        // When reporting a failure event itself throws, the original failure cause must not be lost: it is
        // attached as a suppressed exception on the throwable that gets logged.
        RuntimeException reportingFailure = new RuntimeException("observer blew up while reporting");
        Exception originalCause = new Exception("original discovery failure");
        DnsServiceDiscovererObserver observer = mock(DnsServiceDiscovererObserver.class);
        DnsDiscoveryObserver discovery = mock(DnsDiscoveryObserver.class);
        when(observer.onNewDiscovery(ID, NAME)).thenReturn(discovery);
        doThrow(reportingFailure).when(discovery).discoveryFailed(originalCause);

        DnsDiscoveryObserver safeDiscovery = asSafeObserver(observer).onNewDiscovery(ID, NAME);
        assertDoesNotThrow(() -> safeDiscovery.discoveryFailed(originalCause));
        assertThat(reportingFailure.getSuppressed(), arrayContaining(originalCause));
    }

    // ------------------------------------------------------------------ unpack

    @Test
    void unpackPlainObserverReturnsSingletonList() {
        DnsServiceDiscovererObserver observer = mock(DnsServiceDiscovererObserver.class);
        assertThat(unpack(observer), contains(observer));
    }

    @Test
    void unpackReturnsCombinedObserversInOrder() {
        DnsServiceDiscovererObserver o1 = mock(DnsServiceDiscovererObserver.class);
        DnsServiceDiscovererObserver o2 = mock(DnsServiceDiscovererObserver.class);
        DnsServiceDiscovererObserver o3 = mock(DnsServiceDiscovererObserver.class);

        assertThat(unpack(combine(o1, o2, o3)), contains(o1, o2, o3));
    }

    @Test
    void unpackUnwrapsSafeWrapper() {
        DnsServiceDiscovererObserver observer = mock(DnsServiceDiscovererObserver.class);
        assertThat(unpack(asSafeObserver(observer)), contains(observer));
    }

    @Test
    void unpackResultIsUnmodifiable() {
        List<DnsServiceDiscovererObserver> unpacked = unpack(combine(mock(DnsServiceDiscovererObserver.class),
                mock(DnsServiceDiscovererObserver.class)));
        assertThrows(UnsupportedOperationException.class,
                () -> unpacked.add(mock(DnsServiceDiscovererObserver.class)));
    }

    @Test
    @SuppressWarnings("deprecation")
    void unpackTreatsThirdPartyAggregatorAsOpaque() {
        // A third-party observer may internally aggregate observers we know how to unwrap, but since it is not
        // one of our types it must stay a single opaque leaf: unpack must not descend into it.
        DnsServiceDiscovererObserver aggregated = combine(mock(DnsServiceDiscovererObserver.class),
                mock(DnsServiceDiscovererObserver.class));
        DnsServiceDiscovererObserver thirdParty = name -> aggregated.onNewDiscovery(name);

        assertThat(unpack(thirdParty), contains(thirdParty));
    }
}
