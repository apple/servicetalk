/*
 * Copyright © 2019 Apple Inc. and the ServiceTalk project authors
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

import io.netty.util.internal.PlatformDependent;
import org.apache.directory.server.dns.messages.QuestionRecord;
import org.apache.directory.server.dns.messages.RecordClass;
import org.apache.directory.server.dns.messages.RecordType;
import org.apache.directory.server.dns.messages.ResourceRecord;
import org.apache.directory.server.dns.store.DnsAttribute;
import org.apache.directory.server.dns.store.RecordStore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

import static io.servicetalk.dns.discovery.netty.DnsTestUtils.nextIp;
import static io.servicetalk.dns.discovery.netty.DnsTestUtils.nextIp6;

final class TestRecordStore implements RecordStore {
    private final Set<String> domains;
    private final int minRecords;
    private final int maxRecords;

    TestRecordStore(Set<String> domains) {
        this(domains, 1, 10);
    }

    TestRecordStore(Set<String> domains, int minRecords, int maxRecords) {
        this.domains = domains;
        this.minRecords = minRecords;
        this.maxRecords = maxRecords;
    }

    @Nullable
    @Override
    public Set<ResourceRecord> getRecords(QuestionRecord questionRecord) {
        String name = questionRecord.getDomainName();
        if (domains.contains(name)) {
            Set<ResourceRecord> records = new HashSet<>();
            do {
                final Map<String, Object> attributes = new HashMap<>();
                switch (questionRecord.getRecordType()) {
                    case A:
                        attributes.put(DnsAttribute.IP_ADDRESS, nextIp());
                        break;
                    case AAAA:
                        attributes.put(DnsAttribute.IP_ADDRESS, nextIp6());
                        break;
                    default:
                        break;
                }
                records.add(new TestResourceRecord(
                        name, questionRecord.getRecordType(), RecordClass.IN, 1, attributes));
            }
            while ((PlatformDependent.threadLocalRandom().nextBoolean() || records.size() < minRecords)
                    && records.size() < maxRecords);
            return records;
        }
        return null;
    }

    // `ResourceRecordImpl`'s hashCode/equals don't include `attributes`, so it's impossible to include multiple
    // `ResourceRecordImpl`s, with different IPs, in a `Set`.
    private static class TestResourceRecord implements ResourceRecord {
        private String domainName;
        private RecordType recordType;
        private RecordClass recordClass;
        private int timeToLive;
        private Map<String, Object> attributes;

        TestResourceRecord(final String domainName, final RecordType recordType,
                           final RecordClass recordClass, final int timeToLive,
                           final Map<String, Object> attributes) {
            this.domainName = domainName;
            this.recordType = recordType;
            this.recordClass = recordClass;
            this.timeToLive = timeToLive;
            this.attributes = new HashMap<>();
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
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
        public String get(String id) {
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
