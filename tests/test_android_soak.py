import hashlib
import importlib.util
import json
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
                                 str(SCRIPT)], text=True, capture_output=True, timeout=15)
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

    def test_progress_timeouts_are_non_authoritative(self):
        def timeout(*args, **kwargs):
            raise subprocess.TimeoutExpired('adb', 15)
        self.assertIsNone(runner.read_progress(timeout, 'idle'))
        self.assertIsNone(runner.read_progress(lambda *a, **k: 'not ready', 'idle'))
        sample = {'elapsed_ms': 1000, 'counts': {'owner_stable': True}}
        self.assertEqual(runner.read_progress(lambda *a, **k: json.dumps({'samples': [sample]}), 'idle'), sample)


if __name__ == '__main__':
    unittest.main()
