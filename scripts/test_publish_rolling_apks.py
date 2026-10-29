"""Synthetic files and external-process doubles for release safety checks."""

import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from scripts import publish_rolling_apks as publisher


SOURCE = "1" * 40
CERTIFICATE = "a" * 64
FULL = "NSE-Watcher-Full.apk"
UPDATE = "NSE-Watcher-Update.apk"


def apk_bytes(label):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w") as archive:
        archive.writestr("AndroidManifest.xml", label)
    return stream.getvalue()


class ExternalCommands:
    def __init__(self, root):
        self.root = root
        self.assets = {UPDATE: apk_bytes("previous"), "old-debug.apk": b"old"}
        self.exists = True
        self.previous_version = 200010
        self.certificate = CERTIFICATE
        self.previous_certificate = CERTIFICATE
        self.package = "ke.co.nsewatcher"
        self.version = 200011
        self.version_name = "0.1.11"
        self.sdk = 26
        self.target = 35
        self.multiple_signers = False
        self.source = SOURCE
        self.lookup_error = False
        self.upload_error = False
        self.partial_failure = None
        self.cleanup_error = False
        self.upload_count = 0
        self.mutations = []
        self.created_target = None

    def run(self, args, **kwargs):
        args = [str(item) for item in args]
        executable = Path(args[0]).name
        output = ""
        error = ""
        code = 0
        if executable.startswith("apksigner"):
            previous = Path(args[-1]).parent.name == "previous" and Path(args[-1]).read_bytes() != (self.root / "release.apk").read_bytes()
            cert = self.previous_certificate if previous else self.certificate
            output = f"Verifies\nSigner #1 certificate SHA-256 digest: {cert}\n"
            if self.multiple_signers:
                output += f"Signer #2 certificate SHA-256 digest: {'b' * 64}\n"
        elif executable.startswith("aapt"):
            previous = Path(args[-1]).parent.name == "previous" and Path(args[-1]).read_bytes() != (self.root / "release.apk").read_bytes()
            version = self.previous_version if previous else self.version
            output = (
                f"package: name='{self.package}' versionCode='{version}' "
                f"versionName='{self.version_name}'\n"
                f"sdkVersion:'{self.sdk}'\ntargetSdkVersion:'{self.target}'\n"
            )
        elif executable.startswith("zipalign"):
            pass
        elif args[1:3] == ["release", "view"]:
            if self.lookup_error:
                code, error = 1, "authentication failed"
            elif not self.exists:
                code, error = 1, "release not found"
            else:
                output = json.dumps({"assets": [{"name": name} for name in self.assets]})
        elif args[1] == "api":
            output = self.source
        elif args[1:3] == ["release", "download"]:
            target = Path(args[args.index("--dir") + 1])
            target.mkdir(parents=True, exist_ok=True)
            name = args[args.index("--pattern") + 1]
            (target / name).write_bytes(self.assets[name])
        elif args[1:3] == ["release", "create"]:
            self.mutations.append("create")
            self.exists = True
            self.created_target = args[args.index("--target") + 1]
        elif args[1:3] == ["release", "upload"]:
            if self.upload_error:
                code, error = 1, "upload failed"
            else:
                self.mutations.append("upload")
                self.upload_count += 1
                if self.partial_failure == "full":
                    self.assets.pop(FULL, None)
                    self.assets[UPDATE] = (self.root / "release.apk").read_bytes()
                    return subprocess.CompletedProcess(args, 1, "", "Full upload failed after Update completed")
                for value in args[4:args.index("--clobber")]:
                    path = Path(value)
                    if self.partial_failure == "update" and path.name == UPDATE:
                        self.assets.pop(UPDATE, None)
                        return subprocess.CompletedProcess(args, 1, "", "Update upload failed after deletion")
                    self.assets[path.name] = path.read_bytes()
        elif args[1:3] == ["release", "delete-asset"]:
            if self.cleanup_error:
                return subprocess.CompletedProcess(args, 1, "", "cleanup failed")
            self.mutations.append("delete")
            del self.assets[args[-1]]
        elif args[1:3] == ["release", "edit"]:
            self.mutations.append("edit")
        else:
            raise AssertionError(f"Unexpected external command: {args!r}")
        return subprocess.CompletedProcess(args, code, output, error)


class PublicationSafetyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.apk = self.root / "release.apk"
        self.apk.write_bytes(apk_bytes("candidate"))
        self.external = ExternalCommands(self.root)
        self.env = {
            "APK_PATH": str(self.apk),
            "ANDROID_HOME": str(self.root / "sdk"),
            "GH_TOKEN": "synthetic-test-token",
            "GH_REPO": "example/NSE-Watcher",
            "GITHUB_SHA": SOURCE,
            "GITHUB_EVENT_NAME": "push",
            "GITHUB_REF": "refs/heads/main",
            "NSEWATCHER_VERSION_CODE": "200011",
            "NSEWATCHER_VERSION_NAME": "0.1.11",
            "NSEWATCHER_RELEASE_CERT_SHA256": CERTIFICATE,
        }

    def publish(self):
        with patch.object(publisher.subprocess, "run", side_effect=self.external.run):
            return publisher.publish(self.env)

    def assert_refused_without_mutation(self):
        original = dict(self.external.assets)
        with self.assertRaises(publisher.ReleaseError):
            self.publish()
        self.assertEqual(self.external.assets, original)
        self.assertEqual(self.external.mutations, [])

    def test_success_replaces_only_two_identical_signed_assets(self):
        self.publish()
        self.assertEqual(set(self.external.assets), {FULL, UPDATE})
        self.assertEqual(self.external.assets[FULL], self.apk.read_bytes())
        self.assertEqual(self.external.assets[UPDATE], self.apk.read_bytes())
        self.assertLess(self.external.mutations.index("upload"), self.external.mutations.index("delete"))

    def test_first_release_targets_tested_commit(self):
        self.external.exists = False
        self.external.assets = {}
        self.publish()
        self.assertEqual(self.external.created_target, SOURCE)
        self.assertEqual(set(self.external.assets), {FULL, UPDATE})

    def test_complete_previous_signed_pair_advances_together(self):
        previous = self.external.assets[UPDATE]
        self.external.assets = {FULL: previous, UPDATE: previous}
        self.publish()
        self.assertEqual(self.external.assets[FULL], self.apk.read_bytes())
        self.assertEqual(self.external.assets[UPDATE], self.apk.read_bytes())

    def test_inconsistent_old_pair_is_refused(self):
        self.external.assets[FULL] = apk_bytes("different previous binary")
        self.assert_refused_without_mutation()

    def test_missing_required_input_never_mutates_release(self):
        for name in ("GH_TOKEN", "NSEWATCHER_RELEASE_CERT_SHA256", "GITHUB_SHA"):
            with self.subTest(name=name):
                value = self.env.pop(name)
                self.assert_refused_without_mutation()
                self.env[name] = value

    def test_only_main_push_can_publish(self):
        for event, ref in (("workflow_dispatch", "refs/heads/main"), ("push", "refs/heads/feature")):
            with self.subTest(event=event, ref=ref):
                self.env.update(GITHUB_EVENT_NAME=event, GITHUB_REF=ref)
                self.assert_refused_without_mutation()

    def test_candidate_certificate_must_match_permanent_pin(self):
        self.external.certificate = "b" * 64
        self.assert_refused_without_mutation()

    def test_previous_update_certificate_must_remain_compatible(self):
        self.external.previous_certificate = "b" * 64
        self.assert_refused_without_mutation()

    def test_downgrade_and_repeat_are_refused(self):
        for version in (200011, 200012):
            with self.subTest(previous=version):
                self.external.previous_version = version
                self.assert_refused_without_mutation()

    def test_package_version_name_and_sdk_mismatches_are_refused(self):
        for field, value in (("package", "other.app"), ("version", 5), ("version_name", "0.1.10"), ("sdk", 24), ("target", 34)):
            with self.subTest(field=field):
                original = getattr(self.external, field)
                setattr(self.external, field, value)
                self.assert_refused_without_mutation()
                setattr(self.external, field, original)

    def test_multiple_signers_are_refused(self):
        self.external.multiple_signers = True
        self.assert_refused_without_mutation()

    def test_corrupt_apk_is_refused_before_publication(self):
        self.apk.write_bytes(b"not an APK archive")
        self.assert_refused_without_mutation()

    def test_stale_main_commit_is_refused(self):
        self.external.source = "2" * 40
        self.assert_refused_without_mutation()

    def test_missing_partner_does_not_allow_same_version_different_bytes(self):
        self.external.assets = {FULL: apk_bytes("previous")}
        self.external.previous_version = 200011
        self.assert_refused_without_mutation()

    def test_partial_full_upload_can_be_repaired_with_identical_candidate(self):
        self.external.partial_failure = "full"
        with self.assertRaises(publisher.ReleaseError):
            self.publish()
        self.assertNotIn(FULL, self.external.assets)
        self.assertEqual(self.external.assets[UPDATE], self.apk.read_bytes())
        self.external.partial_failure = None
        self.publish()
        self.assertEqual(set(self.external.assets), {FULL, UPDATE})
        self.assertEqual(self.external.assets[FULL], self.external.assets[UPDATE])

    def test_partial_update_upload_can_be_repaired_with_identical_candidate(self):
        self.external.partial_failure = "update"
        with self.assertRaises(publisher.ReleaseError):
            self.publish()
        self.assertNotIn(UPDATE, self.external.assets)
        self.assertEqual(self.external.assets[FULL], self.apk.read_bytes())
        self.external.partial_failure = None
        self.publish()
        self.assertEqual(set(self.external.assets), {FULL, UPDATE})
        self.assertEqual(self.external.assets[FULL], self.external.assets[UPDATE])

    def test_cleanup_failure_can_be_repaired_without_reuploading_same_code(self):
        self.external.cleanup_error = True
        with self.assertRaises(publisher.ReleaseError):
            self.publish()
        uploads = self.external.upload_count
        self.external.cleanup_error = False
        self.publish()
        self.assertEqual(set(self.external.assets), {FULL, UPDATE})
        self.assertEqual(self.external.upload_count, uploads)

    def test_lookup_permission_error_does_not_create_release(self):
        self.external.lookup_error = True
        self.assert_refused_without_mutation()

    def test_upload_failure_preserves_previous_assets(self):
        self.external.upload_error = True
        self.assert_refused_without_mutation()

    def test_invalid_android_version_bounds_are_refused(self):
        for value in ("0", "-1", "2147483648", "not-a-version"):
            with self.subTest(value=value):
                self.env["NSEWATCHER_VERSION_CODE"] = value
                self.assert_refused_without_mutation()


if __name__ == "__main__":
    unittest.main()
