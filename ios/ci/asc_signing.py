#!/usr/bin/env python3
"""Sets up App Store signing on a CI Mac using only an App Store Connect API key (Admin role).

  prepare   Loads the team's distribution certificate from ios/ci/dist-cert.enc (decrypted with a key
            derived from the API key), or creates a new one through the API and saves it there
            encrypted. Registers the bundle ID if needed, creates or reuses an App Store
            provisioning profile, installs it, and writes the certificate as a .p12 for the keychain.
  revoke ID Revokes a certificate (used only if a newly created one couldn't be saved).

Environment: ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY_PATH (.p8 file), BUNDLE_ID, OUT_DIR, GITHUB_OUTPUT.

The encrypted file is safe to keep in a public repo: it can only be opened with the API key, and anyone
holding the API key could create a distribution certificate anyway.
"""
import base64
import hashlib
import json
import os
import plistlib
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, rsa, utils
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.serialization import pkcs12
from cryptography.x509.oid import NameOID

API = "https://api.appstoreconnect.apple.com/v1"
PROFILE_NAME = "MacGruber GitHub CI App Store"
CERT_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "dist-cert.enc")

with open(os.environ["ASC_KEY_PATH"], "rb") as _f:
    API_KEY = serialization.load_pem_private_key(_f.read(), password=None)


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def make_token() -> str:
    header = {"alg": "ES256", "kid": os.environ["ASC_KEY_ID"], "typ": "JWT"}
    now = int(time.time())
    payload = {"iss": os.environ["ASC_ISSUER_ID"], "iat": now, "exp": now + 1000, "aud": "appstoreconnect-v1"}
    signing_input = (b64url(json.dumps(header).encode()) + "." + b64url(json.dumps(payload).encode())).encode()
    r, s = utils.decode_dss_signature(API_KEY.sign(signing_input, ec.ECDSA(hashes.SHA256())))
    return signing_input.decode() + "." + b64url(r.to_bytes(32, "big") + s.to_bytes(32, "big"))


def api(method, path, body=None, query=None, allow=()):
    url = API + path + ("?" + urllib.parse.urlencode(query) if query else "")
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", "Bearer " + make_token())
    if data is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            raw = resp.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        if e.code in allow:
            return None
        detail = e.read().decode(errors="replace")
        hint = ""
        if e.code in (401, 403):
            hint = ("\nCheck ASC_KEY_ID, ASC_ISSUER_ID and ASC_KEY_P8, and that the key's role is Admin.")
        elif e.code == 409 and path == "/certificates":
            hint = ("\nApple's limit on distribution certificates is reached. Revoke an unused one at "
                    "https://developer.apple.com/account/resources/certificates/list and run again.")
        sys.exit(f"App Store Connect API {method} {path} failed with HTTP {e.code}:\n{detail}{hint}")


# ---------------------------------------------------------------- stored certificate

def file_key() -> bytes:
    der = API_KEY.private_bytes(serialization.Encoding.DER, serialization.PrivateFormat.PKCS8,
                                serialization.NoEncryption())
    return hashlib.sha256(b"macgruber-dist-cert-v1" + der).digest()


def load_stored():
    if not os.path.exists(CERT_FILE):
        return None
    try:
        blob = base64.b64decode(open(CERT_FILE, "rb").read())
        plain = AESGCM(file_key()).decrypt(blob[:12], blob[12:], None)
    except Exception:
        print("Saved certificate can't be opened with this API key (the key was probably replaced).")
        return None
    return json.loads(plain)


def save_stored(record):
    nonce = secrets.token_bytes(12)
    blob = nonce + AESGCM(file_key()).encrypt(nonce, json.dumps(record).encode(), None)
    with open(CERT_FILE, "wb") as f:
        f.write(base64.b64encode(blob) + b"\n")


def certificate_is_active(cert_id: str) -> bool:
    result = api("GET", f"/certificates/{cert_id}", allow=(404,))
    if not result:
        return False
    expires = result["data"]["attributes"].get("expirationDate", "")
    # Renew a month before it expires.
    return not expires or expires[:10] > time.strftime("%Y-%m-%d", time.gmtime(time.time() + 30 * 86400))


def create_certificate():
    print("Creating a new Apple Distribution certificate")
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    csr = (x509.CertificateSigningRequestBuilder()
           .subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "MacGruber GitHub CI")]))
           .sign(key, hashes.SHA256()))
    created = api("POST", "/certificates", body={"data": {"type": "certificates", "attributes": {
        "certificateType": "DISTRIBUTION",
        "csrContent": csr.public_bytes(serialization.Encoding.PEM).decode(),
    }}})
    return {
        "cert_id": created["data"]["id"],
        "cert_der": created["data"]["attributes"]["certificateContent"],
        "key_pem": key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                     serialization.NoEncryption()).decode(),
    }


# ---------------------------------------------------------------- bundle ID and profile

def find_bundle_id(identifier: str) -> str:
    result = api("GET", "/bundleIds", query={"filter[identifier]": identifier, "limit": "200"})
    for item in result["data"]:
        if item["attributes"]["identifier"] == identifier:  # the filter also matches longer ids
            return item["id"]
    print(f"Registering bundle ID {identifier}")
    created = api("POST", "/bundleIds", body={"data": {"type": "bundleIds", "attributes": {
        "identifier": identifier, "name": "MacGruber Soundboard", "platform": "IOS"}}})
    return created["data"]["id"]


def find_or_create_profile(bundle_id_id: str, cert_id: str) -> str:
    result = api("GET", "/profiles", query={"filter[name]": PROFILE_NAME, "limit": "200"})
    for item in result["data"]:
        attrs = item["attributes"]
        if attrs.get("name") != PROFILE_NAME:
            continue
        usable = attrs.get("profileState") == "ACTIVE" and attrs.get("profileType") == "IOS_APP_STORE"
        if usable:
            certs = api("GET", f"/profiles/{item['id']}/certificates", query={"limit": "200"})
            bundle = api("GET", f"/profiles/{item['id']}/bundleId")
            usable = any(c["id"] == cert_id for c in certs["data"]) and bundle["data"]["id"] == bundle_id_id
        if usable:
            print(f"Reusing profile {PROFILE_NAME}")
            return attrs["profileContent"]
        print(f"Deleting outdated profile {item['id']}")
        api("DELETE", f"/profiles/{item['id']}", allow=(404,))
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
    for folder in ("Library/MobileDevice/Provisioning Profiles",
                   "Library/Developer/Xcode/UserData/Provisioning Profiles"):
        path = os.path.join(os.path.expanduser("~"), folder)
        os.makedirs(path, exist_ok=True)
        with open(os.path.join(path, uuid + ".mobileprovision"), "wb") as f:
            f.write(data)
    return uuid


# ---------------------------------------------------------------- main

def output(name, value):
    out = os.environ.get("GITHUB_OUTPUT")
    if out:
        with open(out, "a") as f:
            f.write(f"{name}={value}\n")


def prepare():
    record = load_stored()
    if record and not certificate_is_active(record["cert_id"]):
        print("Saved certificate was revoked or is about to expire.")
        record = None
    created = record is None
    if created:
        record = create_certificate()
        save_stored(record)
    else:
        print("Using the saved distribution certificate")

    cert = x509.load_der_x509_certificate(base64.b64decode(record["cert_der"]))
    key = serialization.load_pem_private_key(record["key_pem"].encode(), password=None)
    team = cert.subject.get_attributes_for_oid(NameOID.ORGANIZATIONAL_UNIT_NAME)[0].value
    print(f"Certificate: {cert.subject.rfc4514_string()}, expires {cert.not_valid_after_utc:%Y-%m-%d}")

    # Legacy PKCS#12 encryption, which macOS `security import` reads.
    password = secrets.token_hex(16)
    encryption = (serialization.PrivateFormat.PKCS12.encryption_builder()
                  .kdf_rounds(50000)
                  .key_cert_algorithm(pkcs12.PBES.PBESv1SHA1And3KeyTripleDESCBC)
                  .hmac_hash(hashes.SHA1())
                  .build(password.encode()))
    p12 = pkcs12.serialize_key_and_certificates(b"MacGruber CI", key, cert, None, encryption)
    p12_path = os.path.join(os.environ["OUT_DIR"], "dist.p12")
    with open(p12_path, "wb") as f:
        f.write(p12)

    bundle_id_id = find_bundle_id(os.environ["BUNDLE_ID"])
    uuid = install_profile(find_or_create_profile(bundle_id_id, record["cert_id"]))
    print(f"Installed profile {uuid} for team {team}")

    print(f"::add-mask::{password}")
    output("p12_path", p12_path)
    output("p12_password", password)
    output("team_id", team)
    output("profile_uuid", uuid)
    output("cert_id", record["cert_id"])
    output("created", "true" if created else "false")


if __name__ == "__main__":
    if sys.argv[1:2] == ["prepare"]:
        prepare()
    elif sys.argv[1:2] == ["revoke"] and len(sys.argv) == 3:
        api("DELETE", f"/certificates/{sys.argv[2]}", allow=(404,))
        print(f"Revoked certificate {sys.argv[2]}")
    else:
        sys.exit("usage: asc_signing.py prepare | revoke CERT_ID")
