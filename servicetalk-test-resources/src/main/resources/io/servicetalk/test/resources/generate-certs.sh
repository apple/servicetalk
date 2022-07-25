#!/bin/bash
#
# Copyright © 2018 Apple Inc. and the ServiceTalk project authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -eu

# Server
## Generate a new, self-signed root CA for the server
openssl req -new -x509 -days 30 -nodes -subj "/CN=ServiceTalkTestServerRoot" -newkey rsa:3072 -sha512 -keyout server_ca.key -out server_ca.pem

## Generate a certificate/key for the server to use for Hostname Verification via localhost
openssl req -new -keyout localhost_server_rsa.key -nodes -newkey rsa:3072 -subj "/CN=localhost" | \
    openssl x509 -req -CAkey server_ca.key -CA server_ca.pem -days 36500 -set_serial $RANDOM -sha512 -out localhost_server.pem
openssl pkcs8 -topk8 -inform PEM -outform PEM -in localhost_server_rsa.key -nocrypt -out localhost_server.key

## Import server key in to a PKCS#12 keystore
rm -f localhost_server.p12
openssl pkcs12 -info -export -out localhost_server.p12 \
               -name localhost_server -in localhost_server.pem -inkey localhost_server.key \
               -CAfile server_ca.pem -caname root -chain \
               -passout pass:changeit

## Clean up intermediate files
rm server_ca.key localhost_server_rsa.key

# Client
## Generate a new, self-signed root CA for the client
openssl req -new -x509 -days 30 -nodes -subj "/CN=ServiceTalkTestClientRoot" -newkey rsa:3072 -sha512 -keyout client_ca.key -out client_ca.pem

## Generate a certificate/key for the server to use for Hostname Verification via localhost
openssl req -new -keyout localhost_client_rsa.key -nodes -newkey rsa:3072 -subj "/CN=localhost" | \
    openssl x509 -req -CAkey client_ca.key -CA client_ca.pem -days 36500 -set_serial $RANDOM -sha512 -out localhost_client.pem
openssl pkcs8 -topk8 -inform PEM -outform PEM -in localhost_client_rsa.key -nocrypt -out localhost_client.key

## Import client key in to a PKCS#12 keystore
rm -f localhost_client.p12
openssl pkcs12 -info -export -out localhost_client.p12 \
               -name localhost_client -in localhost_client.pem -inkey localhost_client.key \
               -CAfile client_ca.pem -caname root -chain \
               -passout pass:changeit

## Clean up intermediate files
rm client_ca.key localhost_client_rsa.key

# Generate our trust store
rm -f truststore.p12
keytool -import -alias server_ca -file server_ca.pem -keystore truststore.p12 -storepass changeit -noprompt
keytool -import -alias client_ca -file client_ca.pem -keystore truststore.p12 -storepass changeit -noprompt

keytool -list -v -storepass changeit -keystore localhost_server.p12
keytool -list -v -storepass changeit -keystore localhost_client.p12
keytool -list -v -storepass changeit -keystore truststore.p12
