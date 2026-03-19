import os
import requests
import socket
import uuid

# Consul configuration (can be overridden via env)
CONSUL_HOST = os.getenv("CONSUL_HOST", "localhost")
CONSUL_PORT = int(os.getenv("CONSUL_PORT", "8500"))
AI_SERVICE_NAME = os.getenv("AI_SERVICE_NAME", "ai-service")
AI_SERVICE_ID = os.getenv("AI_SERVICE_ID", f"{AI_SERVICE_NAME}-{uuid.uuid4().hex[:8]}")
AI_SERVICE_ADDRESS = os.getenv("AI_SERVICE_ADDRESS", None)
if not AI_SERVICE_ADDRESS:
    try:
        AI_SERVICE_ADDRESS = socket.gethostbyname(socket.gethostname())
    except Exception:
        AI_SERVICE_ADDRESS = "127.0.0.1"
AI_SERVICE_PORT = int(os.getenv("AI_SERVICE_PORT", os.getenv("PORT", "8080")))

CONSUL_REG_URL = f"http://{CONSUL_HOST}:{CONSUL_PORT}/v1/agent/service/register"
CONSUL_DEREG_URL = f"http://{CONSUL_HOST}:{CONSUL_PORT}/v1/agent/service/deregister"


def register_with_consul():
    payload = {
        "Name": AI_SERVICE_NAME,
        "ID": AI_SERVICE_ID,
        "Address": AI_SERVICE_ADDRESS,
        "Port": AI_SERVICE_PORT,
        "Tags": ["ai", "omninote"],
        "Check": {
            "HTTP": f"http://{AI_SERVICE_ADDRESS}:{AI_SERVICE_PORT}/health",
            "Interval": "10s",
            "DeregisterCriticalServiceAfter": "1m"
        }
    }
    try:
        resp = requests.put(CONSUL_REG_URL, json=payload, timeout=5)
        resp.raise_for_status()
        print(f"Registered service {AI_SERVICE_ID} with Consul at {CONSUL_HOST}:{CONSUL_PORT}")
        return True
    except Exception as e:
        print(f"Failed to register with Consul: {e}")
        return False


def deregister_from_consul():
    try:
        url = f"{CONSUL_DEREG_URL}/{AI_SERVICE_ID}"
        resp = requests.put(url, timeout=5)
        if resp.status_code in (200, 204):
            print(f"Deregistered service {AI_SERVICE_ID} from Consul")
            return True
        # fallback: try direct deregister endpoint
        resp2 = requests.put(url, timeout=5)
        resp2.raise_for_status()
        print(f"Deregistered service {AI_SERVICE_ID} from Consul (fallback)")
        return True
    except Exception as e:
        print(f"Failed to deregister from Consul: {e}")
        return False
