#!/bin/bash
#
# Copyright © 2026 Apple Inc. and the ServiceTalk project authors
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

set -e

CERTS_DIR="./certs"

echo "Generating mTLS certificates for OTLP collector..."
echo ""

# Create certs directory
mkdir -p "$CERTS_DIR"
cd "$CERTS_DIR"

# Generate CA private key and certificate
echo "1. Generating CA certificate..."
openssl genrsa -out ca-key.pem 4096
openssl req -new -x509 -days 365 -key ca-key.pem -out ca-cert.pem \
  -subj "/C=US/ST=California/L=Cupertino/O=ServiceTalk/CN=ServiceTalk CA"

# Generate server private key and certificate signing request
echo "2. Generating server certificate..."
openssl genrsa -out server-key.pem 4096
openssl req -new -key server-key.pem -out server-csr.pem \
  -subj "/C=US/ST=California/L=Cupertino/O=ServiceTalk/CN=localhost"

# Create server certificate extensions file
cat > server-ext.cnf <<EOF
subjectAltName = DNS:localhost,DNS:otel-collector,IP:127.0.0.1
extendedKeyUsage = serverAuth
EOF

# Sign server certificate with CA
openssl x509 -req -days 365 -in server-csr.pem -CA ca-cert.pem -CAkey ca-key.pem \
  -CAcreateserial -out server-cert.pem -extfile server-ext.cnf

# Generate client private key and certificate signing request
echo "3. Generating client certificate..."
openssl genrsa -out client-key.pem 4096
openssl req -new -key client-key.pem -out client-csr.pem \
  -subj "/C=US/ST=California/L=Cupertino/O=ServiceTalk/CN=ServiceTalk Client"

# Create client certificate extensions file
cat > client-ext.cnf <<EOF
extendedKeyUsage = clientAuth
EOF

# Sign client certificate with CA
openssl x509 -req -days 365 -in client-csr.pem -CA ca-cert.pem -CAkey ca-key.pem \
  -CAcreateserial -out client-cert.pem -extfile client-ext.cnf

# Clean up temporary files
rm -f server-csr.pem client-csr.pem server-ext.cnf client-ext.cnf ca-cert.srl

# Set appropriate permissions
chmod 644 *.pem
chmod 600 *-key.pem

echo ""
echo "✓ Certificates generated successfully in $CERTS_DIR/"
echo ""
echo "Generated files:"
echo "  - ca-cert.pem       (CA certificate - trust store)"
echo "  - ca-key.pem        (CA private key)"
echo "  - server-cert.pem   (Server certificate)"
echo "  - server-key.pem    (Server private key)"
echo "  - client-cert.pem   (Client certificate)"
echo "  - client-key.pem    (Client private key)"
echo ""
echo "Note: Keep *-key.pem files secure!"
echo ""
