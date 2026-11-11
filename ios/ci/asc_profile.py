#!/usr/bin/env python3
"""Makes sure an App Store provisioning profile exists for the app and the distribution
certificate in the CI keychain, installs it, and prints its UUID.

Uses the App Store Connect API. Needs these environment variables:
  ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY_PATH (path to the .p8 file), BUNDLE_ID, CERT_PEM_PATH
  (PEM certificates exported from the signing keychain; the one Apple knows about is used).
Writes profile_uuid=<uuid> to $GITHUB_OUTPUT when it is set.
"""
import base64
import json
import os
import plistlib
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, utils

API = "https://api.appstoreconnect.apple.com/v1"
PROFILE_NAME = "MacGruber GitHub CI App Store"


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def make_token() -> str:
    with open(os.environ["ASC_KEY_PATH"], "rb") as f:
        key = serialization.load_pem_private_key(f.read(), password=None)
    header = {"alg": "ES256", "kid": os.environ["ASC_KEY_ID"], "typ": "JWT"}
    now = int(time.time())
    payload = {"iss": os.environ["ASC_ISSUER_ID"], "iat": now, "exp": now + 1000, "aud": "appstoreconnect-v1"}
    signing_input = (b64url(json.dumps(header).encode()) + "." + b64url(json.dumps(payload).encode())).encode()
    der = key.sign(signing_input, ec.ECDSA(hashes.SHA256()))
    r, s = utils.decode_dss_signature(der)
    return signing_input.decode() + "." + b64url(r.to_bytes(32, "big") + s.to_bytes(32, "big"))


TOKEN = None


def api(method: str, path: str, body=None, query=None):
    url = API + path
    if query:
        url += "?" + urllib.parse.urlencode(query)
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", "Bearer " + TOKEN)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            raw = resp.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        detail = e.read().decode(errors="replace")
        sys.exit(f"App Store Connect API {method} {path} failed with HTTP {e.code}:\n{detail}\n"
                 "Check that ASC_KEY_ID, ASC_ISSUER_ID and ASC_KEY_P8 are right and the key has the Admin role.")


def load_local_certs():
    with open(os.environ["CERT_PEM_PATH"], "rb") as f:
        pem = f.read()
    blocks = re.findall(rb"-----BEGIN CERTIFICATE-----.+?-----END CERTIFICATE-----", pem, re.S)
    return [x509.load_pem_x509_certificate(b) for b in blocks]


def find_certificate():
    local = load_local_certs()
    local_der = {c.public_bytes(serialization.Encoding.DER) for c in local}
    for cert_type in ("DISTRIBUTION", "IOS_DISTRIBUTION"):
        result = api("GET", "/certificates", query={"filter[certificateType]": cert_type, "limit": "200"})
        for item in result["data"]:
            content = item["attributes"].get("certificateContent")
            if content and base64.b64decode(content) in local_der:
                print(f"Using certificate {item['attributes'].get('name')} "
                      f"(expires {item['attributes'].get('expirationDate')})")
                return item["id"]
    names = ", ".join(c.subject.rfc4514_string() for c in local)
    sys.exit("The certificate in DIST_CERT_P12 isn't an active distribution certificate on this Apple team.\n"
             f"Certificates found in the .p12: {names}\n"
             "Make an Apple Distribution certificate on the same team as APPLE_TEAM_ID and export it again.")


def find_bundle_id(identifier: str):
    result = api("GET", "/bundleIds", query={"filter[identifier]": identifier, "limit": "200"})
    for item in result["data"]:
        # The filter also matches longer ids that start with this one.
        if item["attributes"]["identifier"] == identifier:
            return item["id"]
    print(f"Registering bundle ID {identifier}")
    created = api("POST", "/bundleIds", body={"data": {"type": "bundleIds", "attributes": {
        "identifier": identifier, "name": "MacGruber Soundboard", "platform": "IOS"}}})
    return created["data"]["id"]


def find_or_create_profile(bundle_id_id: str, cert_id: str):
    result = api("GET", "/profiles", query={"filter[name]": PROFILE_NAME, "limit": "200"})
    for item in result["data"]:
        attrs = item["attributes"]
        if attrs.get("name") != PROFILE_NAME:
            continue
        usable = attrs.get("profileState") == "ACTIVE" and attrs.get("profileType") == "IOS_APP_STORE"
        if usable:
            certs = api("GET", f"/profiles/{item['id']}/certificates", query={"limit": "200"})
            bundle = api("GET", f"/profiles/{item['id']}/bundleId")
            usable = (any(c["id"] == cert_id for c in certs["data"])
                      and bundle["data"]["id"] == bundle_id_id)
        if usable:
            print(f"Reusing profile {PROFILE_NAME}")
            return attrs["profileContent"]
        print(f"Deleting outdated profile {item['id']}")
        api("DELETE", f"/profiles/{item['id']}")
    print(f"Creating profile {PROFILE_NAME}")
    created = api("POST", "/profiles", body={"data": {
        "type": "profiles",
        "attributes": {"name": PROFILE_NAME, "profileType": "IOS_APP_STORE"},
        "relationships": {
            "bundleId": {"data": {"type": "bundleIds", "id": bundle_id_id}},
            "certificates": {"data": [{"type": "certificates", "id": cert_id}]},
        },
    }})
    return created["data"]["attributes"]["profileContent"]


def install_profile(content_b64: str) -> str:
    data = base64.b64decode(content_b64)
    decoded = subprocess.run(["security", "cms", "-D"], input=data, capture_output=True, check=True).stdout
    uuid = plistlib.loads(decoded)["UUID"]
    home = os.path.expanduser("~")
    for folder in ("Library/MobileDevice/Provisioning Profiles",
                   "Library/Developer/Xcode/UserData/Provisioning Profiles"):
        path = os.path.join(home, folder)
        os.makedirs(path, exist_ok=True)
        with open(os.path.join(path, uuid + ".mobileprovision"), "wb") as f:
            f.write(data)
    return uuid


def main():
    global TOKEN
    TOKEN = make_token()
    cert_id = find_certificate()
    bundle_id_id = find_bundle_id(os.environ["BUNDLE_ID"])
    uuid = install_profile(find_or_create_profile(bundle_id_id, cert_id))
    print(f"Installed profile {uuid}")
    out = os.environ.get("GITHUB_OUTPUT")
    if out:
        with open(out, "a") as f:
            f.write(f"profile_uuid={uuid}\n")


if __name__ == "__main__":
    main()
