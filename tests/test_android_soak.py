import hashlib
import importlib.util
import json
import os
import shutil
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'tools/test-android-soak.py'
SPEC = importlib.util.spec_from_file_location('android_soak_runner', SCRIPT)
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)


class AndroidSoakRunnerTests(unittest.TestCase):
    def test_acceptance_rejection_remains_enabled_under_optimized_python(self):
        result = subprocess.run([sys.executable, '-O', '-c',
                                 "import runpy,sys; runpy.run_path(sys.argv[1])['require'](False, 'rejected receipt')",
                                 str(SCRIPT)], text=True, capture_output=True, timeout=15, check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('RuntimeError: rejected receipt', result.stderr)

    def test_apk_hash_streams_the_complete_file(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'app.apk'
            data = b'niobium' * 200000
            path.write_bytes(data)
            self.assertEqual(runner.sha256_file(path), hashlib.sha256(data).hexdigest())

    def test_output_policy_rejects_source_tree_artifacts_before_running(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory) / 'repo'
            root.mkdir()
            subprocess.run(['git', '-c', 'init.defaultBranch=main', 'init', '--quiet', str(root)], check=True, timeout=15)
            (root / '.gitignore').write_text('ignored/\n')
            self.assertEqual(runner.checked_output_path(root / 'ignored' / 'run', root), (root / 'ignored' / 'run').resolve())
            with self.assertRaisesRegex(RuntimeError, 'ignored output directory'):
                runner.checked_output_path(root / 'results' / 'run', root)
            self.assertEqual(runner.checked_output_path(Path(directory) / 'outside', root), (Path(directory) / 'outside').resolve())

    def test_missing_sdk_properties_leaves_configuration_unset(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.assertIsNone(runner.find_sdk(root, {}))
            (root / 'android').mkdir()
            (root / 'android/local.properties').write_text('sdk.dir=/test/sdk\n')
            self.assertEqual(runner.find_sdk(root, {}), '/test/sdk')
            self.assertEqual(runner.find_sdk(root, {'ANDROID_HOME': '/env/sdk'}), '/env/sdk')

    def test_receipt_failure_stops_app_but_accepted_run_does_not(self):
        calls = []
        def device(*args, **kwargs):
            calls.append((args, kwargs))
        with self.assertRaisesRegex(RuntimeError, 'Invalid receipt'):
            with runner.acceptance_cleanup(device):
                raise RuntimeError('Invalid receipt')
        self.assertEqual(calls, [(('shell', 'am', 'force-stop', 'io.sovware.niobium.debug'), {'check': False})])
        calls.clear()
        with runner.acceptance_cleanup(device) as accept:
            accept()
        self.assertEqual(calls, [])

    def test_cleanup_timeout_preserves_original_validation_failure(self):
        def device(*args, **kwargs):
            raise subprocess.TimeoutExpired('adb', 15)
        with self.assertRaisesRegex(RuntimeError, 'Original failure'):
            with runner.acceptance_cleanup(device):
                raise RuntimeError('Original failure')

    def test_stale_reports_cannot_pass_when_gradle_executes_no_test(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            root = base / 'repo'
            (root / 'tools').mkdir(parents=True)
            shutil.copy2(SCRIPT, root / 'tools/test-android-soak.py')
            (root / '.gitignore').write_text('android/app/build/\n')
            gradle = root / 'android/gradlew'
            gradle.parent.mkdir()
            gradle.write_text('#!/bin/sh\nexit 0\n')
            gradle.chmod(0o755)
            results = root / 'android/app/build/outputs/androidTest-results/connected/debug'
            results.mkdir(parents=True)
            method = 'idleConnectionsSurviveTwoHeartbeatIntervalsAndStillDeliver'
            (results / 'TEST-old.xml').write_text(
                '<testsuite tests="1" failures="0" errors="0" skipped="0">'
                f'<testcase name="{method}"/></testsuite>')
            stale = {'kind': 'idle', 'phase': 'scenario_passed',
                     'sample': {'elapsed_ms': 45000, 'counts': {'owner_stable': True, 'confirmed_delivery': True}}}
            (results / ('logcat-' + method + '.txt')).write_text('NiobiumSoak ' + json.dumps(stale) + '\n')
            apk = root / 'android/app/build/outputs/apk/debug/app-debug.apk'
            apk.parent.mkdir(parents=True)
            apk.write_bytes(b'stale-apk')
            sdk = base / 'sdk'
            adb = sdk / 'platform-tools/adb'
            adb.parent.mkdir(parents=True)
            adb.write_text('#!' + sys.executable + '\nimport sys\nfrom pathlib import Path\n'
                           'args=sys.argv[1:]\n'
                           'if args==["devices"]: print("List of devices attached\\nfake-serial\\tdevice")\n'
                           'elif "force-stop" in args: (Path(__file__).resolve().parents[2]/"forced-stop").touch()\n'
                           'elif "getprop" in args: print({"ro.product.cpu.abi":"arm64-v8a","ro.build.version.sdk":"36","ro.kernel.qemu":"1"}.get(args[-1],""))\n')
            adb.chmod(0o755)
            subprocess.run(['git', '-c', 'init.defaultBranch=main', 'init', '--quiet', str(root)], check=True, timeout=15)
            subprocess.run(['git', 'add', '.'], cwd=root, check=True, timeout=15)
            subprocess.run(['git', '-c', 'user.name=Fixture', '-c', 'user.email=fixture@example.invalid',
                            '-c', 'commit.gpgsign=false', 'commit', '--quiet', '-m', 'Fixture'], cwd=root, check=True, timeout=15)
            output = base / 'receipts'
            run = subprocess.run([sys.executable, str(root / 'tools/test-android-soak.py'), '--device', 'fake-serial',
                                  '--idle-seconds', '45', '--dsh-turns', '1', '--dsh-pause-seconds', '0', '--output', str(output)],
                                 env=os.environ | {'ANDROID_HOME': str(sdk)}, text=True, capture_output=True, timeout=30, check=False)
            self.assertNotEqual(run.returncode, 0)
            self.assertIn('Expected one test-device report', run.stderr)
            self.assertFalse(list(results.glob('TEST-*.xml')))
            self.assertFalse((output / 'validation.json').exists())
            self.assertTrue((base / 'forced-stop').exists())

    def test_malformed_advisory_samples_are_ignored(self):
        invalid = [None, [], {}, {'samples': None}, {'samples': {}}, {'samples': []},
                   {'samples': [None]}, {'samples': [{}]},
                   {'samples': [{'elapsed_ms': 1000}]}, {'samples': [{'counts': {}}]},
                   {'samples': [{'elapsed_ms': '1000', 'counts': {}}]},
                   {'samples': [{'elapsed_ms': True, 'counts': {}}]},
                   {'samples': [{'elapsed_ms': -1, 'counts': {}}]},
                   {'samples': [{'elapsed_ms': 1000, 'counts': []}]}]
        for receipt in invalid:
            with self.subTest(receipt=receipt):
                self.assertIsNone(runner.read_progress(lambda *a, **k: json.dumps(receipt), 'idle'))

    def test_progress_timeouts_are_non_authoritative(self):
        def timeout(*args, **kwargs):
            raise subprocess.TimeoutExpired('adb', 15)
        self.assertIsNone(runner.read_progress(timeout, 'idle'))
        self.assertIsNone(runner.read_progress(lambda *a, **k: 'not ready', 'idle'))
        sample = {'elapsed_ms': 1000, 'counts': {'owner_stable': True}}
        self.assertEqual(runner.read_progress(lambda *a, **k: json.dumps({'samples': [sample]}), 'idle'), sample)


if __name__ == '__main__':
    unittest.main()
