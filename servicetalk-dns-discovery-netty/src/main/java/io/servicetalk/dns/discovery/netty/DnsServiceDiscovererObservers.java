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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * A factory to create different {@link DnsServiceDiscovererObserver}s.
 */
public final class DnsServiceDiscovererObservers {

    private DnsServiceDiscovererObservers() {
        // No instances.
    }

    /**
     * Converts passed {@link DnsServiceDiscovererObserver} to a safe version that catches and logs all exceptions, but
     * does not rethrow them.
     *
     * @param observer {@link DnsServiceDiscovererObserver} to convert
     * @return a safe version of the passed {@link DnsServiceDiscovererObserver} that catches and logs all exceptions,
     * but does not rethrow them.
     */
    public static DnsServiceDiscovererObserver asSafeObserver(final DnsServiceDiscovererObserver observer) {
        if (observer instanceof CatchAllDnsServiceDiscovererObserver) {
            return observer;
        }
        if (observer instanceof BiDnsServiceDiscovererObserver) {
            // BiDnsServiceDiscovererObserver is always safe
            return observer;
        }
        return new CatchAllDnsServiceDiscovererObserver(observer);
    }

    /**
     * Combines multiple {@link DnsServiceDiscovererObserver}s into a single {@link DnsServiceDiscovererObserver}.
     * <p>
     * When more than one observer is provided, the returned observer fans out every event to all combined observers
     * and is exception-safe: each combined observer is wrapped with
     * {@link #asSafeObserver(DnsServiceDiscovererObserver)}, so an exception thrown by one observer is caught and
     * logged rather than rethrown, and never prevents the remaining observers from being notified. When a single
     * observer is provided it is returned as-is, without this safety wrapping.
     *
     * @param other {@link DnsServiceDiscovererObserver}s to combine
     * @return a {@link DnsServiceDiscovererObserver} that delegates all invocations to the provided
     * {@link DnsServiceDiscovererObserver}s
     * @see #asSafeObserver(DnsServiceDiscovererObserver)
     * @see #unpack(DnsServiceDiscovererObserver)
     */
    public static DnsServiceDiscovererObserver combine(final DnsServiceDiscovererObserver... other) {
        switch (other.length) {
            case 0:
                throw new IllegalArgumentException("At least one DnsServiceDiscovererObserver is required to combine");
            case 1:
                return requireNonNull(other[0]);
            default:
                BiDnsServiceDiscovererObserver bi = new BiDnsServiceDiscovererObserver(other[0], other[1]);
                for (int i = 2; i < other.length; i++) {
                    bi = new BiDnsServiceDiscovererObserver(bi, other[i]);
                }
                return bi;
        }
    }

    /**
     * Unpacks a {@link DnsServiceDiscovererObserver} into the list of observers it aggregates.
     * <p>
     * If the provided {@code observer} was created using the {@link #combine(DnsServiceDiscovererObserver...) combine}
     * method, this method returns the individual observers that were combined. Otherwise, it returns a singleton list
     * containing the provided observer.
     * <p>
     * Only the observers produced by this class are unwrapped: the aggregating observer created by
     * {@link #combine(DnsServiceDiscovererObserver...) combine} and the safe wrapper created by
     * {@link #asSafeObserver(DnsServiceDiscovererObserver) asSafeObserver}. Any other
     * {@link DnsServiceDiscovererObserver} &mdash; including a third-party implementation that internally wraps or
     * aggregates other observers &mdash; is treated as opaque: wherever it occurs in the combined structure it appears
     * as a single element of the returned list, and its contents are never unwrapped.
     *
     * @param observer {@link DnsServiceDiscovererObserver} to unpack
     * @return a {@link List} of {@link DnsServiceDiscovererObserver}s
     * @see #combine(DnsServiceDiscovererObserver...)
     */
    public static List<DnsServiceDiscovererObserver> unpack(final DnsServiceDiscovererObserver observer) {
        if (observer instanceof BiDnsServiceDiscovererObserver ||
                observer instanceof CatchAllDnsServiceDiscovererObserver) {
            final List<DnsServiceDiscovererObserver> result = new ArrayList<>();
            unpack(observer, result);
            return Collections.unmodifiableList(result);
        }
        return Collections.singletonList(observer);
    }

    private static void unpack(final DnsServiceDiscovererObserver observer,
                               final List<DnsServiceDiscovererObserver> result) {
        if (observer instanceof BiDnsServiceDiscovererObserver) {
            final BiDnsServiceDiscovererObserver bi = (BiDnsServiceDiscovererObserver) observer;
            unpack(bi.first(), result);
            unpack(bi.second(), result);
        } else if (observer instanceof CatchAllDnsServiceDiscovererObserver) {
            // combine(...) wraps each combined observer with asSafeObserver(...), so unwrap to expose the original
            // leaf observer that was provided by the user.
            unpack(((CatchAllDnsServiceDiscovererObserver) observer).observer(), result);
        } else {
            result.add(observer);
        }
    }
}
