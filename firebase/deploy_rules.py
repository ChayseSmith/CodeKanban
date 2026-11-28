"""Publish the default Firestore rules with a short-lived Google access token.

Uses the Firebase Rules API, so no Firebase login token or admin private key is needed.
"""
import json
import os
import re
import urllib.error
import urllib.request
from pathlib import Path

ENDPOINT = "https://firebaserules.googleapis.com/v1/"


def normalize(text):
    return text.replace("\r\n", "\n").strip()


def deploy(project, project_number, source, request):
    if not re.fullmatch(r"[a-z][a-z0-9-]{4,28}[a-z0-9]", project):
        raise ValueError("Invalid Firebase project ID")
    prefix = f"projects/{project}"
    release_name = f"{prefix}/releases/cloud.firestore"
    current = request("GET", release_name, missing_ok=True)

    def valid_ruleset(name):
        parts = name.split("/")
        if len(parts) != 4 or parts[0] != "projects" or parts[1] not in {project, str(project_number)} or parts[2] != "rulesets" or not parts[3]:
            raise ValueError("Ruleset response belongs to an unexpected project")
        return name

    if current:
        old = request("GET", valid_ruleset(current["rulesetName"]))
        files = old.get("source", {}).get("files", [])
        if len(files) == 1 and normalize(files[0].get("content", "")) == normalize(source):
            return "Firestore rules are unchanged; no deployment needed."

    created = request("POST", f"{prefix}/rulesets", {"source": {"files": [{"name": "firestore.rules", "content": source}]}})
    name = valid_ruleset(created["name"])
    release = {"name": release_name, "rulesetName": name}
    if current:
        request("PATCH", release_name, {"release": release, "updateMask": "rulesetName"})
    else:
        request("POST", f"{prefix}/releases", release)
    return f"Deployed default Firestore rules to {project}: {name}"


def main():
    root = Path(__file__).resolve().parent.parent
    config = json.loads((root / "android/app/google-services.json").read_text(encoding="utf-8"))
    info = config["project_info"]
    project = info["project_id"]
    expected = os.environ.get("FIREBASE_PROJECT_ID", project)
    if expected != project:
        raise ValueError("Deployment project does not match the Android Firebase configuration")
    token = os.environ.get("FIREBASE_DEPLOY_ACCESS_TOKEN", "")
    if not token:
        raise ValueError("Missing short-lived Firebase deployment access token")

    def request(method, path, body=None, missing_ok=False):
        data = None if body is None else json.dumps(body).encode("utf-8")
        call = urllib.request.Request(ENDPOINT + path, data=data, method=method,
            headers={"Authorization": "Bearer " + token, "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(call, timeout=60) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if error.code == 404 and missing_ok:
                return None
            detail = error.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"Firebase Rules API {method} failed (HTTP {error.code}): {detail}") from None

    print(deploy(project, info["project_number"], (root / "firebase/firestore.rules").read_text(encoding="utf-8"), request))


if __name__ == "__main__":
    main()
