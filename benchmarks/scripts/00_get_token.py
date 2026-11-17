"""
Benchmark Helper: Get JWT Token
Authenticates and returns token for use by other scripts.
"""
import requests, json, sys

AUTH_URL = "http://localhost:8080/api/v1/auth/login"
CREDENTIALS = {"email": "gokusengar666@gmail.com", "password": "password123"}

def get_token():
    try:
        resp = requests.post(AUTH_URL, json=CREDENTIALS, timeout=10)
        if resp.status_code == 200:
            data = resp.json()
            token = data.get("token") or data.get("accessToken") or data.get("access_token")
            if token:
                print(f"[OK] JWT token acquired (first 30 chars): {token[:30]}...")
                return token
            else:
                print(f"[ERROR] No token field found. Response: {data}")
                return None
        else:
            print(f"[ERROR] Login failed: {resp.status_code} - {resp.text}")
            return None
    except Exception as e:
        print(f"[ERROR] Auth request failed: {e}")
        return None

if __name__ == "__main__":
    token = get_token()
    if token:
        # Save token to file for other scripts to use
        with open("benchmarks/results/token.txt", "w") as f:
            f.write(token)
        print("[OK] Token saved to benchmarks/results/token.txt")
    else:
        sys.exit(1)

