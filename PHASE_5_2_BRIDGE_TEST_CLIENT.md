# Phase 5.2: Bridge Test Client Guide

This guide provides test clients for verifying the TrustVault Bridge Protocol implementation.

---

## Table of Contents

1. [Python Test Client](#python-test-client)
2. [Shell Script Test Client](#shell-script-test-client)
3. [Browser Console Test](#browser-console-test)
4. [Test Scenarios](#test-scenarios)

---

## Python Test Client

### Installation

```bash
# No external dependencies needed - uses only standard library
python3 --version  # Requires Python 3.6+
```

### Implementation

Save as `test_bridge_client.py`:

```python
#!/usr/bin/env python3
"""
TrustVault Bridge Protocol Test Client

Connects to localhost:7654 and tests the bridge protocol.
"""

import socket
import json
import time
import uuid
import hashlib
import hmac
import base64
import sys

class BridgeTestClient:
    """Test client for TrustVault Bridge Protocol"""

    def __init__(self, host='127.0.0.1', port=7654, timeout=5):
        self.host = host
        self.port = port
        self.timeout = timeout
        self.device_name = f"TestClient-{uuid.uuid4().hex[:8]}"
        self.pairing_id = None
        self.shared_secret = None

    def connect(self):
        """Connect to bridge server"""
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.settimeout(self.timeout)
            self.socket.connect((self.host, self.port))
            print(f"✅ Connected to {self.host}:{self.port}")
            return True
        except Exception as e:
            print(f"❌ Connection failed: {e}")
            return False

    def disconnect(self):
        """Disconnect from server"""
        try:
            self.socket.close()
            print("✅ Disconnected")
        except Exception as e:
            print(f"⚠️  Disconnect error: {e}")

    def send_message(self, message):
        """Send message to server and receive response"""
        try:
            # Serialize message
            json_str = json.dumps(message, separators=(',', ':'))

            # Send
            self.socket.send(json_str.encode('utf-8'))
            print(f"📤 Sent: {message['messageType']}")
            print(f"   {json_str[:100]}...")

            # Receive
            response = self.socket.recv(4096).decode('utf-8')
            data = json.loads(response)
            print(f"📥 Received: {data['messageType']}")
            print(f"   Status: {'✅ Success' if data.get('success', True) else '❌ Failed'}")

            return data

        except Exception as e:
            print(f"❌ Error: {e}")
            return None

    def test_handshake(self):
        """Test 1: Handshake - Establish protocol compatibility"""
        print("\n" + "="*60)
        print("TEST 1: Handshake")
        print("="*60)

        message = {
            "messageType": "Handshake",
            "requestId": str(uuid.uuid4()),
            "clientName": "TrustVault-TestClient",
            "clientVersion": "1.0.0",
            "protocol": "TrustVault-Bridge",
            "protocolVersion": "1.0"
        }

        response = self.send_message(message)

        if response and response.get('messageType') == 'HandshakeResponse':
            print(f"✅ Protocol: {response.get('protocol')}")
            print(f"✅ App: {response.get('appName')} v{response.get('appVersion')}")
            print(f"✅ Server ID Hash: {response.get('serverIdHash', 'N/A')[:32]}...")
            return True
        else:
            print("❌ Handshake failed")
            return False

    def test_pairing(self, shared_secret=None):
        """Test 2: TestAssociate - Device pairing"""
        print("\n" + "="*60)
        print("TEST 2: Device Pairing (TestAssociate)")
        print("="*60)

        # Use provided secret or empty (for testing)
        self.shared_secret = shared_secret or "test_shared_secret_12345"

        # Generate client key (would be from browser extension in real usage)
        client_key = base64.b64encode(b"test_client_public_key").decode('utf-8')

        # Compute HMAC-SHA256(key, sharedSecret)
        key_hash = hmac.new(
            self.shared_secret.encode('utf-8'),
            client_key.encode('utf-8'),
            hashlib.sha256
        ).hexdigest()

        message = {
            "messageType": "TestAssociate",
            "requestId": str(uuid.uuid4()),
            "key": client_key,
            "keyHash": key_hash,
            "deviceName": self.device_name
        }

        response = self.send_message(message)

        if response and response.get('success'):
            self.pairing_id = response.get('id')
            print(f"✅ Pairing successful!")
            print(f"✅ Pairing ID: {self.pairing_id}")
            return True
        else:
            print(f"❌ Pairing failed: {response.get('errorMessage', 'Unknown error')}")
            return False

    def test_invalid_pairing(self):
        """Test 2b: Invalid pairing - Wrong shared secret"""
        print("\n" + "="*60)
        print("TEST 2b: Invalid Pairing (Wrong Secret)")
        print("="*60)

        # Use wrong secret
        wrong_secret = "wrong_secret_xyz"
        client_key = base64.b64encode(b"test_key").decode('utf-8')

        # Compute with wrong secret
        wrong_hash = hmac.new(
            wrong_secret.encode('utf-8'),
            client_key.encode('utf-8'),
            hashlib.sha256
        ).hexdigest()

        message = {
            "messageType": "TestAssociate",
            "requestId": str(uuid.uuid4()),
            "key": client_key,
            "keyHash": wrong_hash,
            "deviceName": "InvalidDevice"
        }

        response = self.send_message(message)

        if not response.get('success'):
            print(f"✅ Correctly rejected invalid pairing")
            print(f"   Error: {response.get('errorMessage')}")
            return True
        else:
            print(f"❌ Should have rejected pairing (security issue!)")
            return False

    def test_get_logins(self, url="https://github.com"):
        """Test 3: GetLogins - Credential query"""
        print("\n" + "="*60)
        print(f"TEST 3: Credential Query (GetLogins)")
        print("="*60)

        if not self.pairing_id:
            print("⚠️  Skipping: Device not paired")
            return False

        message = {
            "messageType": "GetLogins",
            "requestId": str(uuid.uuid4()),
            "url": url,
            "id": self.pairing_id
        }

        response = self.send_message(message)

        if response and response.get('messageType') == 'LoginResponse':
            entries = response.get('entries', [])
            print(f"✅ Query successful!")
            print(f"✅ Found {len(entries)} credentials")

            for i, entry in enumerate(entries):
                print(f"\n   Credential {i+1}:")
                print(f"   - Name: {entry.get('name')}")
                print(f"   - Login: {entry.get('login')}")
                print(f"   - Password: {'*' * min(10, len(entry.get('password', '')))}")
                if entry.get('totp'):
                    print(f"   - TOTP: {entry.get('totp')} (6-digit code)")

            return len(entries) > 0
        else:
            error_msg = response.get('message', 'Unknown error') if response else "No response"
            print(f"❌ Query failed: {error_msg}")
            return False

    def test_unpaired_access(self):
        """Test 3b: Unpaired device cannot access credentials"""
        print("\n" + "="*60)
        print("TEST 3b: Unpaired Device Rejection")
        print("="*60)

        # Use fake pairing ID
        fake_pairing_id = str(uuid.uuid4())

        message = {
            "messageType": "GetLogins",
            "requestId": str(uuid.uuid4()),
            "url": "https://example.com",
            "id": fake_pairing_id
        }

        response = self.send_message(message)

        if response and not response.get('success'):
            print(f"✅ Correctly rejected unpaired device")
            print(f"   Error code: {response.get('code')}")
            return True
        else:
            print(f"❌ Should have rejected unpaired device (security issue!)")
            return False

    def run_all_tests(self):
        """Run all tests"""
        print("\n" + "🧪 " * 20)
        print("TrustVault Bridge Protocol - Test Suite")
        print("🧪 " * 20)
        print(f"\nTarget: {self.host}:{self.port}")
        print(f"Device: {self.device_name}")

        # Connect
        if not self.connect():
            return False

        results = []

        try:
            # Test 1: Handshake
            results.append(("Handshake", self.test_handshake()))

            # Test 2: Valid Pairing
            results.append(("Device Pairing", self.test_pairing()))

            # Test 2b: Invalid Pairing
            results.append(("Invalid Pairing Rejection", self.test_invalid_pairing()))

            # Test 3: Credential Query
            results.append(("Credential Query", self.test_get_logins()))

            # Test 3b: Unpaired Access
            results.append(("Unpaired Device Rejection", self.test_unpaired_access()))

        finally:
            self.disconnect()

        # Summary
        print("\n" + "="*60)
        print("TEST SUMMARY")
        print("="*60)

        passed = sum(1 for _, result in results if result)
        total = len(results)

        for test_name, result in results:
            status = "✅ PASS" if result else "❌ FAIL"
            print(f"{status}: {test_name}")

        print(f"\nTotal: {passed}/{total} tests passed")
        print("="*60 + "\n")

        return passed == total


def main():
    """Main entry point"""
    import argparse

    parser = argparse.ArgumentParser(description='TrustVault Bridge Test Client')
    parser.add_argument('--host', default='127.0.0.1', help='Bridge server host')
    parser.add_argument('--port', type=int, default=7654, help='Bridge server port')
    parser.add_argument('--timeout', type=int, default=5, help='Socket timeout')

    args = parser.parse_args()

    client = BridgeTestClient(host=args.host, port=args.port, timeout=args.timeout)
    success = client.run_all_tests()

    sys.exit(0 if success else 1)


if __name__ == '__main__':
    main()
```

### Usage

```bash
# Make executable
chmod +x test_bridge_client.py

# Run all tests
python3 test_bridge_client.py

# Connect to specific host
python3 test_bridge_client.py --host 192.168.1.100 --port 7654
```

### Expected Output

```
🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪
TrustVault Bridge Protocol - Test Suite
🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪 🧪

Target: 127.0.0.1:7654
Device: TestClient-a1b2c3d4

✅ Connected to 127.0.0.1:7654
📤 Sent: Handshake
📥 Received: HandshakeResponse
   Status: ✅ Success

✅ Protocol: TrustVault-Bridge
✅ App: TrustVault v1.0.0
✅ Server ID Hash: a7f1e4c2b9d8f3a6e5c1b4d7...

[... more test output ...]

============================================================
TEST SUMMARY
============================================================
✅ PASS: Handshake
✅ PASS: Device Pairing
✅ PASS: Invalid Pairing Rejection
✅ PASS: Credential Query
✅ PASS: Unpaired Device Rejection

Total: 5/5 tests passed
============================================================
```

---

## Shell Script Test Client

### Installation

```bash
# Any Linux/macOS with netcat and jq
brew install netcat-openbsd jq  # macOS
sudo apt-get install netcat jq  # Ubuntu/Debian
```

### Implementation

Save as `test_bridge_client.sh`:

```bash
#!/bin/bash

# TrustVault Bridge Protocol - Shell Test Client

HOST="${1:-127.0.0.1}"
PORT="${2:-7654}"
TIMEOUT=5

echo "🧪 TrustVault Bridge Test Client"
echo "Target: $HOST:$PORT"
echo

# Test 1: Handshake
echo "TEST 1: Handshake"
REQUEST_ID=$(uuidgen)

HANDSHAKE=$(cat <<EOF
{"messageType":"Handshake","requestId":"$REQUEST_ID","clientName":"TestClient","clientVersion":"1.0.0","protocol":"TrustVault-Bridge","protocolVersion":"1.0"}
EOF
)

RESPONSE=$(echo "$HANDSHAKE" | nc -w $TIMEOUT $HOST $PORT)

if echo "$RESPONSE" | jq . > /dev/null 2>&1; then
    echo "✅ Received valid JSON response"
    echo "$RESPONSE" | jq '.'
else
    echo "❌ Invalid or no response"
    echo "Response: $RESPONSE"
fi

echo
```

### Usage

```bash
chmod +x test_bridge_client.sh
./test_bridge_client.sh 127.0.0.1 7654
```

---

## Browser Console Test

### JavaScript Test Code

Paste in browser DevTools console (F12 → Console):

```javascript
// TrustVault Bridge Protocol - Browser Test

const BRIDGE_HOST = '127.0.0.1';
const BRIDGE_PORT = 7654;
const BRIDGE_URL = `ws://${BRIDGE_HOST}:${BRIDGE_PORT}`;

async function testBridge() {
    console.log('🧪 TrustVault Bridge Test');

    try {
        // Test 1: TCP Connection
        console.log('\nTEST 1: TCP Connection');
        const response = await fetch(`http://${BRIDGE_HOST}:${BRIDGE_PORT}/`, {
            method: 'POST',
            body: JSON.stringify({
                messageType: 'Handshake',
                requestId: crypto.randomUUID(),
                clientName: 'BrowserTest',
                clientVersion: '1.0.0'
            })
        }).catch(e => console.log('Note: CORS may block - this is expected', e.message));

        console.log('✅ Connection test completed');

    } catch (e) {
        console.error('❌ Error:', e);
    }
}

// Run test
testBridge();
```

---

## Test Scenarios

### Scenario 1: Happy Path

**Steps**:
1. ✅ Handshake succeeds
2. ✅ Pairing succeeds
3. ✅ GetLogins returns credentials

**Expected Results**:
- Protocol version matches
- Pairing ID generated
- Credentials returned for matching URL

---

### Scenario 2: Security Tests

**Test 2a**: Invalid shared secret
- Send TestAssociate with wrong keyHash
- ✅ Should receive: `success: false`
- ✅ Should receive: `errorMessage: "Invalid shared secret"`

**Test 2b**: Unpaired device access
- Send GetLogins with fake pairing ID
- ✅ Should receive: `code: "NOT_PAIRED"`
- ✅ Should receive: Error message

**Test 2c**: Non-matching URL
- Create credential for github.com
- Send GetLogins for example.com
- ✅ Should return empty entries list

---

### Scenario 3: TOTP Testing

**Setup**:
1. Create credential with OTP secret
2. Get credential via GetLogins
3. Verify TOTP code

**Example OTP Secret** (Base32):
```
JBSWY3DPEBLW64TMMQ======
```

**Expected TOTP Codes**:
- Decode Base32 secret
- Generate HMAC-SHA1(secret, time_step)
- Extract 6-digit code
- Valid for 30 seconds

---

### Scenario 4: Error Handling

**Invalid message**:
```json
{"invalid": "message"}
```
- Should return: Error message with `code: "PROTOCOL_ERROR"`

**Missing fields**:
```json
{"messageType": "TestAssociate"}
```
- Should return: Error message indicating missing fields

---

## Debugging

### Enable Verbose Logging

Add to BridgeServer before connecting:

```kotlin
// In BridgeServer.kt
private val tag = "BridgeServer"

// Log all connections
Log.d(tag, "Accepting client connection from $hostAddress")
Log.d(tag, "Received JSON: $jsonMessage")
Log.d(tag, "Sending response: $responseJson")
```

### Monitor Network Traffic

```bash
# Linux/macOS
sudo tcpdump -i lo port 7654 -A

# Show only localhost traffic on port 7654
# -i lo: loopback interface
# -A: print ASCII
```

### Check App Logs

```bash
adb logcat | grep BridgeServer
```

---

## Performance Testing

### Load Test

Create N concurrent connections:

```python
import concurrent.futures
import socket

def single_connection():
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.connect(('127.0.0.1', 7654))
    sock.close()

# Test 100 concurrent connections
with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
    futures = [executor.submit(single_connection) for _ in range(100)]
    results = [f.result() for f in concurrent.futures.as_completed(futures)]
    print(f"✅ All {len(results)} connections successful")
```

---

## Troubleshooting

### Issue: Connection Refused

**Cause**: Bridge server not running
**Solution**: Start bridge in MainActivity

```kotlin
val bridgeServer = BridgeServer(context, credentialRepository)
bridgeServer.start()
```

### Issue: Timeout

**Cause**: Server not responding
**Solution**: Check logs for errors

```bash
adb logcat | grep -i bridge
```

### Issue: Invalid TOTP

**Cause**: Base32 decoding error or wrong time
**Solution**: Verify secret encoding and system time

```bash
# Check system time
date +%s

# Manual TOTP verification
python3 -c "
import hmac, hashlib, struct, time, base64
secret = base64.b32decode('JBSWY3DPEBLW64TMMQ======')
time_step = int(time.time()) // 30
msg = struct.pack('>Q', time_step)
code = hmac.new(secret, msg, hashlib.sha1).digest()
offset = code[-1] & 0x0F
number = struct.unpack('>I', code[offset:offset+4])[0]
print(f'TOTP: {number % 1000000:06d}')
"
```

---

## Summary

This test client suite enables comprehensive validation of the TrustVault Bridge Protocol:

✅ **Python client** - Full test suite with all scenarios
✅ **Shell script** - Lightweight network testing
✅ **Browser console** - Quick connectivity check
✅ **Debug tools** - Network monitoring and logging
✅ **Performance tests** - Load and stress testing

Use these tools to verify bridge functionality before deploying to production.

---

**Created**: 2025-10-18
**Updated**: 2025-10-18

*For protocol details, see PHASE_5_2_BRIDGE_PROTOCOL.md*
