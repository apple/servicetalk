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

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=================================================="
echo "ServiceTalk OpenTelemetry Jaeger mTLS Example"
echo "=================================================="
echo ""

# Check if certificates exist
if [ ! -d "certs" ] || [ ! -f "certs/ca-cert.pem" ]; then
    echo "📜 Generating TLS certificates..."
    ./generate-certs.sh
    echo "✓ Certificates generated successfully"
    echo ""
else
    echo "✓ Certificates already exist in certs/ directory"
    echo ""
fi

# Check if Docker/Vessel is available
if command -v vessel &> /dev/null; then
    DOCKER_CMD="vessel"
elif command -v docker &> /dev/null; then
    DOCKER_CMD="docker"
else
    echo "❌ Error: Neither 'docker' nor 'vessel' command found"
    echo "Please install Docker or Vessel to run this example"
    exit 1
fi

echo "🐳 Starting services with $DOCKER_CMD compose..."
$DOCKER_CMD compose up -d

echo ""
echo "⏳ Waiting for services to be ready..."
sleep 5

echo ""
echo "=================================================="
echo "✅ Services are running!"
echo "=================================================="
echo ""
echo "Services:"
echo "  - Jaeger UI:         http://localhost:16686"
echo "  - OTLP Collector:    https://localhost:4318 (mTLS enabled)"
echo ""
echo "To run the example:"
echo "  cd $SCRIPT_DIR"
echo "  ../../gradlew run"
echo ""
echo "To view logs:"
echo "  $DOCKER_CMD compose logs -f"
echo ""
echo "To stop services:"
echo "  $DOCKER_CMD compose down"
echo ""
echo "=================================================="
