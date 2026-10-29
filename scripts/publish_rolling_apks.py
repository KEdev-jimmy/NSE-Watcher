"""Publish one verified release APK under two stable download names.

Runs in the main-push publish job after validation. Never handles passwords or
decodes a keystore; signing happens before this script is invoked.
"""

from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from typing import Mapping
import zipfile


FULL = "NSE-Watcher-Full.apk"
UPDATE = "NSE-Watcher-Update.apk"
TAG = "ci-apk"
PACKAGE = "ke.co.nsewatcher"


class ReleaseError(RuntimeError):
    """A release prerequisite or compatibility check failed."""


class CommandError(ReleaseError):
    def __init__(self, stderr: str):
        super().__init__("An Android/GitHub command failed; publication stopped.")
        self.stderr = stderr


def command(arguments: list[str]) -> str:
    try:
        result = subprocess.run(
            arguments, capture_output=True, text=True, timeout=180, check=False
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise ReleaseError("An Android/GitHub command could not complete.") from error
    if result.returncode:
        raise CommandError(result.stderr)
    return result.stdout.strip()


@dataclass(frozen=True)
class Identity:
    version_code: int
    version_name: str
    certificate: str


@dataclass(frozen=True)
class Configuration:
    apk: Path
    sdk_tools: Path
    repo: str
    source: str
    version_code: int
    version_name: str
    certificate: str

    @classmethod
    def read(cls, environment: Mapping[str, str]):
        required = (
            "APK_PATH", "ANDROID_HOME", "GH_TOKEN", "GH_REPO", "GITHUB_SHA",
            "NSEWATCHER_VERSION_CODE", "NSEWATCHER_VERSION_NAME",
            "NSEWATCHER_RELEASE_CERT_SHA256",
        )
        for name in required:
            if not environment.get(name, "").strip():
                raise ReleaseError(f"Missing required input: {name}")
        if environment.get("GITHUB_EVENT_NAME") != "push" or environment.get("GITHUB_REF") != "refs/heads/main":
            raise ReleaseError("Only a main-branch push may publish rolling APKs.")
        raw_version = environment["NSEWATCHER_VERSION_CODE"]
        if not re.fullmatch(r"[1-9][0-9]*", raw_version) or int(raw_version) > 2100000000:
            raise ReleaseError("versionCode must be a positive supported Android integer.")
        name = environment["NSEWATCHER_VERSION_NAME"]
        if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(?:[-.][0-9A-Za-z.-]+)?", name):
            raise ReleaseError("versionName must be a semantic-style version.")
        source = environment["GITHUB_SHA"]
        certificate = environment["NSEWATCHER_RELEASE_CERT_SHA256"].lower()
        repo = environment["GH_REPO"]
        if not re.fullmatch(r"[0-9a-f]{40}", source):
            raise ReleaseError("Source commit is not a full Git SHA.")
        if not re.fullmatch(r"[0-9a-f]{64}", certificate):
            raise ReleaseError("A permanent public SHA-256 certificate pin is required.")
        if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo):
            raise ReleaseError("GitHub repository identity is invalid.")
        apk = Path(environment["APK_PATH"])
        if not apk.is_file() or apk.stat().st_size == 0:
            raise ReleaseError("The signed APK is missing or empty.")
        return cls(apk, Path(environment["ANDROID_HOME"]) / "build-tools" / "35.0.0",
                   repo, source, int(raw_version), name, certificate)


def verify_apk(path: Path, configuration: Configuration) -> Identity:
    try:
        with zipfile.ZipFile(path) as archive:
            if "AndroidManifest.xml" not in archive.namelist() or archive.testzip() is not None:
                raise ReleaseError("APK ZIP integrity check failed.")
    except (OSError, zipfile.BadZipFile) as error:
        raise ReleaseError("APK is not a complete readable ZIP archive.") from error
    tools = configuration.sdk_tools
    command([str(tools / "zipalign"), "-c", "4", str(path)])
    signature = command([str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(path)])
    certificates = re.findall(r"^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$", signature, re.MULTILINE)
    if len(certificates) != 1:
        raise ReleaseError("APK must have exactly one verifiable signing certificate.")
    certificate = certificates[0].lower()
    if certificate != configuration.certificate:
        raise ReleaseError("APK signing certificate differs from the permanent identity.")
    metadata = command([str(tools / "aapt"), "dump", "badging", str(path)])
    packages = re.findall(r"^package: (.+)$", metadata, re.MULTILINE)
    if len(packages) != 1:
        raise ReleaseError("APK package metadata is unavailable.")
    fields = dict(re.findall(r"([A-Za-z]+)='([^']*)'", packages[0]))
    if fields.get("name") != PACKAGE:
        raise ReleaseError("APK application ID differs from NSE-Watcher.")
    raw_version = fields.get("versionCode", "")
    if not re.fullmatch(r"[1-9][0-9]*", raw_version) or int(raw_version) > 2100000000:
        raise ReleaseError("APK versionCode is unavailable or invalid.")
    if "sdkVersion:'26'" not in metadata.splitlines() or "targetSdkVersion:'35'" not in metadata.splitlines():
        raise ReleaseError("APK Android SDK compatibility metadata changed.")
    return Identity(int(raw_version), fields.get("versionName", ""), certificate)


def release_assets(configuration: Configuration, *, allow_absent: bool = False):
    try:
        result = command(["gh", "release", "view", TAG, "--repo", configuration.repo, "--json", "assets"])
    except CommandError as error:
        if allow_absent and error.stderr.strip().lower() == "release not found":
            return None
        raise
    try:
        assets = json.loads(result)["assets"]
        if not isinstance(assets, list) or any(not isinstance(asset.get("name"), str) for asset in assets):
            raise ValueError("Malformed release assets")
        return assets
    except (ValueError, KeyError, TypeError, AttributeError) as error:
        raise ReleaseError("GitHub release metadata could not be verified.") from error


def apk_digest(path: Path) -> bytes:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.digest()


def publish(environment: Mapping[str, str]) -> Identity:
    config = Configuration.read(environment)
    candidate = verify_apk(config.apk, config)
    if candidate.version_code != config.version_code or candidate.version_name != config.version_name:
        raise ReleaseError("APK version differs from the requested release.")
    assets = release_assets(config, allow_absent=True)
    notes = (
        f"Release-signed NSE Watcher from source commit {config.source} "
        f"(version {config.version_name}, Android versionCode {candidate.version_code}). "
        "Full and Update APKs contain identical verified bytes. Updates require a compatible "
        "installed signing identity. Prior debug installs may require a separately approved, "
        "backed-up migration. CI does not prove physical-phone update/data retention. "
        "The source archive follows the original ci-apk tag; use the stated APK source commit."
    )
    with tempfile.TemporaryDirectory(prefix="nse-release-") as temporary:
        root = Path(temporary)
        upload_names = [FULL, UPDATE]
        if assets is not None:
            names = [asset["name"] for asset in assets]
            previous_dir = root / "previous"
            previous_dir.mkdir()
            candidate_digest = apk_digest(config.apk)
            older_assets = []
            for name in (FULL, UPDATE):
                if name not in names:
                    continue
                command(["gh", "release", "download", TAG, "--repo", config.repo,
                         "--pattern", name, "--dir", str(previous_dir)])
                previous_path = previous_dir / name
                previous = verify_apk(previous_path, config)
                previous_digest = apk_digest(previous_path)
                if previous.certificate != candidate.certificate:
                    raise ReleaseError("Signing certificate changed; refusing an incompatible update.")
                if candidate.version_code < previous.version_code:
                    raise ReleaseError("Rolling APK versionCode must increase; downgrade refused.")
                if candidate.version_code == previous.version_code:
                    if previous_digest != candidate_digest:
                        raise ReleaseError("Same-version replacement has different bytes; publication refused.")
                    # Resume only byte-identical uploads; never delete a good current asset.
                    upload_names.remove(name)
                else:
                    older_assets.append((previous.version_code, previous_digest))
            if len(older_assets) == 2 and older_assets[0] != older_assets[1]:
                raise ReleaseError("Previous signed pair has inconsistent versions or bytes; investigate.")
        latest_main = command(["gh", "api", f"repos/{config.repo}/commits/main", "--jq", ".sha"])
        if latest_main != config.source:
            raise ReleaseError("A newer main commit exists; refusing stale rolling publication.")
        if assets is None:
            command(["gh", "release", "create", TAG, "--repo", config.repo, "--target", config.source,
                     "--title", "NSE Watcher Rolling APK", "--notes", notes, "--prerelease"])
        full, update = root / FULL, root / UPDATE
        shutil.copyfile(config.apk, full)
        shutil.copyfile(config.apk, update)
        if upload_names:
            command(["gh", "release", "upload", TAG,
                     *(str(root / name) for name in upload_names),
                     "--clobber", "--repo", config.repo])
        uploaded = release_assets(config)
        if not {FULL, UPDATE}.issubset({asset["name"] for asset in uploaded}):
            raise ReleaseError("Both signed assets were not uploaded; refusing cleanup.")
        for asset in uploaded:
            if asset["name"] not in {FULL, UPDATE}:
                command(["gh", "release", "delete-asset", TAG, "--repo", config.repo, "--yes", "--", asset["name"]])
        final_names = sorted(asset["name"] for asset in release_assets(config))
        if final_names != [FULL, UPDATE]:
            raise ReleaseError("Rolling release does not contain exactly Full and Update APKs.")
        command(["gh", "release", "edit", TAG, "--repo", config.repo,
                 "--title", "NSE Watcher Rolling APK", "--notes", notes])
    return candidate


if __name__ == "__main__":
    try:
        identity = publish(os.environ)
        print(f"Published exactly two signed APK assets, versionCode {identity.version_code}.")
        print(f"Public certificate SHA-256: {identity.certificate}")
    except ReleaseError as error:
        print(f"Release blocked: {error}", file=sys.stderr)
        sys.exit(1)
