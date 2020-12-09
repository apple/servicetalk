/*
 * Copyright © 2019-2020 Apple Inc. and the ServiceTalk project authors
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

import org.apache.directory.server.dns.messages.QuestionRecord;
import org.apache.directory.server.dns.messages.RecordClass;
import org.apache.directory.server.dns.messages.RecordType;
import org.apache.directory.server.dns.messages.ResourceRecord;
import org.apache.directory.server.dns.store.DnsAttribute;
import org.apache.directory.server.dns.store.RecordStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

import static org.apache.directory.server.dns.messages.RecordType.A;
import static org.apache.directory.server.dns.messages.RecordType.AAAA;
import static org.apache.directory.server.dns.messages.RecordType.CNAME;

final class TestRecordStore implements RecordStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestRecordStore.class);
    private static final int SRV_DEFAULT_WEIGHT = 10;
    private static final int SRV_DEFAULT_PRIORITY = 10;
    private final Map<String, Map<RecordType, List<ResourceRecord>>> recordsToReturnByDomain =
            new ConcurrentHashMap<>();

    public synchronized void addSrv(final String domain, String targetDomain, final int port, final int ttl) {
        addSrv(domain, targetDomain, port, ttl, SRV_DEFAULT_WEIGHT, SRV_DEFAULT_PRIORITY);
    }

    public synchronized void addSrv(final String domain, String targetDomain, final int port, final int ttl,
                                    final int weight, final int priority) {
        Map<RecordType, List<ResourceRecord>> typeMap = getTypeMap(domain);
        List<ResourceRecord> recordList = getRecordList(typeMap, RecordType.SRV);
        recordList.add(createSrvRecord(domain, targetDomain, port, ttl, weight, priority));
    }

    public synchronized boolean removeSrv(final String domain, String targetDomain, final int port, final int ttl) {
        return removeSrv(domain, targetDomain, port, ttl, SRV_DEFAULT_WEIGHT, SRV_DEFAULT_PRIORITY);
    }

    public synchronized boolean removeSrv(final String domain, String targetDomain, final int port, final int ttl,
                                          final int weight, final int priority) {
        Map<RecordType, List<ResourceRecord>> typeMap = getTypeMap(domain);
        List<ResourceRecord> recordList = getRecordList(typeMap, RecordType.SRV);
        return removeRecords(createSrvRecord(domain, targetDomain, port, ttl, weight, priority), recordList, typeMap);
    }

    public synchronized void addIPv4Address(final String domain, final int ttl, final String... ipAddresses) {
        addAddress(domain, A, ttl, ipAddresses);
    }

    public synchronized boolean removeIPv4Address(final String domain, final int ttl, final String... ipAddresses) {
        return removeAddresses(domain, A, ttl, ipAddresses);
    }

    public synchronized boolean removeIPv4Addresses(final String domain) {
        return removeAddresses(domain, A);
    }

    public synchronized void addIPv6Address(final String domain, final int ttl, final String... ipAddresses) {
        addAddress(domain, AAAA, ttl, ipAddresses);
    }

    public synchronized boolean removeIPv6Address(final String domain, final int ttl, final String... ipAddresses) {
        return removeAddresses(domain, AAAA, ttl, ipAddresses);
    }

    public synchronized void addCNAME(final String domain, final String cname, final int ttl) {
        Map<RecordType, List<ResourceRecord>> typeMap = getTypeMap(domain);
        List<ResourceRecord> recordList = getRecordList(typeMap, RecordType.CNAME);
        recordList.add(createCnameRecord(domain, cname, ttl));
    }

    public synchronized boolean removeCNAME(final String domain, final String cname, final int ttl) {
        Map<RecordType, List<ResourceRecord>> typeMap = getTypeMap(domain);
        List<ResourceRecord> recordList = getRecordList(typeMap, RecordType.CNAME);
        return removeRecords(createCnameRecord(domain, cname, ttl), recordList, typeMap);
    }

    public synchronized boolean removeRecords(ResourceRecord... records) {
        boolean removed = false;
        for (ResourceRecord rr : records) {
            Map<RecordType, List<ResourceRecord>> typeMap = getTypeMap(rr.getDomainName());
            List<ResourceRecord> recordList = getRecordList(typeMap, rr.getRecordType());
            removed |= removeRecords(rr, recordList, typeMap);
        }
        return removed;
    }

    private Map<RecordType, List<ResourceRecord>> getTypeMap(final String domain) {
        return recordsToReturnByDomain.computeIfAbsent(domain, d -> new HashMap<>());
    }

    private List<ResourceRecord> getRecordList(Map<RecordType, List<ResourceRecord>> typeMap,
                                               final RecordType recordType) {
        return typeMap.computeIfAbsent(recordType, t -> new ArrayList<>());
    }

    private void addAddress(final String domain, final RecordType recordType, final int ttl,
                            final String... ipAddresses) {
        Map<RecordType, List<ResourceRecord>> typeMap = getTypeMap(domain);
        List<ResourceRecord> recordList = getRecordList(typeMap, recordType);
        for (String ipAddress : ipAddresses) {
            recordList.add(createAddressRecord(domain, recordType, ttl, ipAddress));
        }
    }

    private boolean removeAddresses(final String domain, final RecordType recordType) {
        Map<RecordType, List<ResourceRecord>> typeMap = getTypeMap(domain);
        boolean removed = typeMap.remove(recordType) != null;
        List<ResourceRecord> recordList = getRecordList(typeMap, recordType);
        recordList.clear();
        if (removed && typeMap.isEmpty()) {
            recordsToReturnByDomain.remove(domain, typeMap);
        }
        return removed;
    }

    private boolean removeAddresses(final String domain, final RecordType recordType, final int ttl,
                                    final String... ipAddresses) {
        boolean removed = false;
        Map<RecordType, List<ResourceRecord>> typeMap = getTypeMap(domain);
        List<ResourceRecord> recordList = getRecordList(typeMap, recordType);
        for (String ipAddress : ipAddresses) {
            removed |= removeRecords(createAddressRecord(domain, recordType, ttl, ipAddress), recordList, typeMap);
        }
        return removed;
    }

    private boolean removeRecords(ResourceRecord rr, List<ResourceRecord> recordList,
                                  Map<RecordType, List<ResourceRecord>> typeMap) {
        // We are in a synchronized block, so multilevel removal/cleanup is safe.
        final boolean removed = recordList.removeIf(listRR -> TestRecordStore.equals(rr, listRR));
        if (removed && recordList.isEmpty() &&
                typeMap.remove(rr.getRecordType()) == recordList && typeMap.isEmpty()) {
            recordsToReturnByDomain.remove(rr.getDomainName(), typeMap);
        }
        return removed;
    }

    @Nullable
    @Override
    public synchronized Set<ResourceRecord> getRecords(final QuestionRecord questionRecord) {
        final String domain = questionRecord.getDomainName();
        final Map<RecordType, List<ResourceRecord>> recordsToReturn = recordsToReturnByDomain.get(domain);
        LOGGER.debug("Getting {} records for {}", questionRecord.getRecordType(), domain);
        if (recordsToReturn != null) {
            final List<ResourceRecord> recordsForType = recordsToReturn.get(questionRecord.getRecordType());
            final List<ResourceRecord> cnameRecords = questionRecord.getRecordType() != CNAME ?
                    recordsToReturn.get(CNAME) : null;
            if (cnameRecords != null) {
                LOGGER.debug("Found CNAME records {}", cnameRecords);
                final Set<ResourceRecord> results = new HashSet<>(cnameRecords);
                if (recordsForType != null) {
                    LOGGER.debug("Found records {}", recordsForType);
                    results.addAll(recordsForType);
                }
                return results;
            }
            if (recordsForType != null) {
                LOGGER.debug("Found records {}", recordsForType);
                return new HashSet<>(recordsForType);
            }
        }
        return null;
    }

    static ResourceRecord createSrvRecord(final String domain, String targetDomain, final int port, final int ttl) {
        return createSrvRecord(domain, targetDomain, port, ttl, SRV_DEFAULT_WEIGHT, SRV_DEFAULT_PRIORITY);
    }

    static ResourceRecord createSrvRecord(final String domain, String targetDomain, final int port, final int ttl,
                                          final int weight, final int priority) {
        final Map<String, Object> attributes = new HashMap<>();
        attributes.put(DnsAttribute.SERVICE_PRIORITY, priority);
        attributes.put(DnsAttribute.SERVICE_WEIGHT, weight);
        attributes.put(DnsAttribute.SERVICE_PORT, port);
        attributes.put(DnsAttribute.DOMAIN_NAME, targetDomain);
        return new TestResourceRecord(domain, RecordType.SRV, RecordClass.IN, ttl, attributes);
    }

    static ResourceRecord createAddressRecord(final String domain, final RecordType recordType, final int ttl,
                                              final String ipAddress) {
        final Map<String, Object> attributes = new HashMap<>();
        attributes.put(DnsAttribute.IP_ADDRESS, ipAddress);
        return new TestResourceRecord(domain, recordType, RecordClass.IN, ttl, attributes);
    }

    static ResourceRecord createCnameRecord(final String domain, final String cname, final int ttl) {
        final Map<String, Object> attributes = new HashMap<>();
        attributes.put(DnsAttribute.DOMAIN_NAME, cname);
        return new TestResourceRecord(domain, RecordType.CNAME, RecordClass.IN, ttl, attributes);
    }

    static boolean equals(final ResourceRecord lhs, final ResourceRecord rhs) {
        if (lhs.getTimeToLive() == rhs.getTimeToLive() &&
                lhs.getDomainName().equals(rhs.getDomainName()) &&
                lhs.getRecordType() == rhs.getRecordType() &&
                lhs.getRecordClass() == rhs.getRecordClass()) {
            if (lhs instanceof TestResourceRecord && rhs instanceof TestResourceRecord) {
                return ((TestResourceRecord) lhs).attributes.equals(((TestResourceRecord) rhs).attributes);
            }
            return true;
        }
        return false;
    }

    // `ResourceRecordImpl`'s hashCode/equals don't include `attributes`, so it's impossible to include multiple
    // `ResourceRecordImpl`s, with different IPs, in a `Set`.
    private static final class TestResourceRecord implements ResourceRecord {
        private final String domainName;
        private final RecordType recordType;
        private final RecordClass recordClass;
        private final int timeToLive;
        private final Map<String, Object> attributes;

        TestResourceRecord(final String domainName, final RecordType recordType,
                           final RecordClass recordClass, final int timeToLive,
                           final Map<String, Object> attributes) {
            this.domainName = domainName;
            this.recordType = recordType;
            this.recordClass = recordClass;
            this.timeToLive = timeToLive;
            this.attributes = new HashMap<>();
            for (final Map.Entry<String, Object> entry : attributes.entrySet()) {
                this.attributes.put(entry.getKey().toLowerCase(), entry.getValue());
            }
        }

        @Override
        public String getDomainName() {
            return domainName;
        }

        @Override
        public RecordType getRecordType() {
            return recordType;
        }

        @Override
        public RecordClass getRecordClass() {
            return recordClass;
        }

        @Override
        public int getTimeToLive() {
            return timeToLive;
        }

        @Nullable
        @Override
        public String get(final String id) {
            final Object value = attributes.get(id.toLowerCase());
            return value == null ? null : value.toString();
        }

        @Override
        public String toString() {
            return "MyResourceRecord{" +
                    "domainName='" + domainName + '\'' +
                    ", recordType=" + recordType +
                    ", recordClass=" + recordClass +
                    ", timeToLive=" + timeToLive +
                    ", attributes=" + attributes +
                    '}';
        }
    }
}
