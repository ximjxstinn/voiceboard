#!/usr/bin/env python3
"""
Lädt die fertige Jar als neue Version auf Modrinth hoch.
- Läuft nur, wenn das Secret MODRINTH_TOKEN gesetzt ist.
- Überspringt, wenn es die Versionsnummer auf Modrinth schon gibt.
- Minecraft-Versionen und Abhängigkeiten werden vorher bei Modrinth nachgeschlagen.
"""
import glob, json, os, sys, urllib.request, urllib.error, uuid

API = "https://api.modrinth.com/v2"
PROJECT = "VmzRw23O"
GAME_VERSIONS = ["26.1", "26.1.1", "26.1.2", "26.2", "26.3"]
DEPENDENCIES = [("fabric-api", "required"), ("simple-voice-chat", "required"), ("modmenu", "optional")]
UA = "ximjxstinn/voiceboard (GitHub Actions)"

token = os.environ.get("MODRINTH_TOKEN", "").strip()
if not token:
    print("::notice::Modrinth übersprungen – Secret MODRINTH_TOKEN ist nicht gesetzt")
    sys.exit(0)

def call(path, data=None, headers=None, method=None, hint=None):
    h = {"User-Agent": UA, "Authorization": token}
    h.update(headers or {})
    req = urllib.request.Request(API + path, data=data, headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", "replace")
        print(f"::error::Modrinth {method or 'GET'} {path} -> HTTP {e.code}: {body[:500]}")
        if hint and e.code in (401, 403, 404):
            print(f"::error::{hint}")
        sys.exit(1)

version = [l.split("=", 1)[1].strip() for l in open("gradle.properties", encoding="utf-8") if l.startswith("mod_version=")][0]
jars = [j for j in glob.glob("out/*.jar")]
if not jars:
    print("::error::Keine Jar in out/ gefunden"); sys.exit(1)
jar = jars[0]

existing = call(f"/project/{PROJECT}/version", hint=(
    "Projekt nicht sichtbar. Ist das Projekt noch nicht öffentlich, braucht der Modrinth-Schlüssel zusätzlich "
    "die Rechte 'Read projects' und 'Read versions' (neben 'Create versions'). Sonst Projekt-ID prüfen."))
if any(v.get("version_number") == version for v in existing):
    print(f"::notice::Modrinth: Version {version} gibt es schon – nichts zu tun")
    sys.exit(0)

known = {t["version"] for t in call("/tag/game_version")}
game_versions = [v for v in GAME_VERSIONS if v in known]
missing = [v for v in GAME_VERSIONS if v not in known]
if missing:
    print(f"::warning::Diese Minecraft-Versionen kennt Modrinth nicht und werden ausgelassen: {', '.join(missing)}")
if not game_versions:
    print("::error::Keine gültigen Minecraft-Versionen"); sys.exit(1)

deps = []
for slug, kind in DEPENDENCIES:
    deps.append({"project_id": call(f"/project/{slug}")["id"], "dependency_type": kind})

notes = f"release-notes/v{version}.md"
changelog = open(notes, encoding="utf-8").read() if os.path.exists(notes) else f"VoiceBoard {version}"

data = {
    "name": f"VoiceBoard {version}",
    "version_number": version,
    "changelog": changelog,
    "dependencies": deps,
    "game_versions": game_versions,
    "version_type": "release",
    "loaders": ["fabric"],
    "featured": True,
    "status": "listed",
    "project_id": PROJECT,
    "file_parts": ["file"],
    "primary_file": "file",
}

boundary = uuid.uuid4().hex
body = bytearray()
body += f"--{boundary}\r\nContent-Disposition: form-data; name=\"data\"\r\nContent-Type: application/json\r\n\r\n".encode()
body += json.dumps(data).encode() + b"\r\n"
body += f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{os.path.basename(jar)}\"\r\nContent-Type: application/java-archive\r\n\r\n".encode()
body += open(jar, "rb").read() + b"\r\n"
body += f"--{boundary}--\r\n".encode()

result = call("/version", data=bytes(body), headers={"Content-Type": f"multipart/form-data; boundary={boundary}"}, method="POST",
              hint="Hochladen abgelehnt. Der Modrinth-Schlüssel braucht das Recht 'Create versions'.")
print(f"::notice::Modrinth: VoiceBoard {version} hochgeladen ({os.path.basename(jar)}) für {', '.join(game_versions)}")
print(f"::notice::Modrinth-Version-ID: {result.get('id')}")
