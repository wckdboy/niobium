#!/usr/bin/env python3
"""Run isolated Android transport and real local DSH acceptance with controlled providers."""
from pathlib import Path
import argparse, datetime, hashlib, json, os, shutil, signal, subprocess, time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--device', default=os.environ.get('ANDROID_SERIAL', 'emulator-5554'))
    parser.add_argument('--idle-seconds', type=int, default=1200)
    parser.add_argument('--dsh-turns', type=int, default=12)
    parser.add_argument('--dsh-pause-seconds', type=int, default=45)
    parser.add_argument('--production-ref', help='Require unchanged app/core/runtime sources relative to this commit')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    for name, value, low, high in [('idle seconds', args.idle_seconds, 45, 3600), ('DSH turns', args.dsh_turns, 1, 60), ('DSH pause', args.dsh_pause_seconds, 0, 300)]:
        if not low <= value <= high: parser.error(f'{name} must be between {low} and {high}')
    def git(*parts): return subprocess.check_output(['git', *parts], cwd=ROOT, text=True).strip()
    if git('status', '--porcelain'): parser.error('Commit source changes before collecting provenance')
    source = git('rev-parse', 'HEAD')
    if args.production_ref:
        subprocess.run(['git', 'diff', '--exit-code', args.production_ref, 'HEAD', '--', 'android/app/src/main', 'android/app/build.gradle.kts', 'android/build.gradle.kts', 'core', 'runtime', 'android/runtime', 'config', 'tools/prepare-android-runtime.py'], cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not sdk:
        sdk = next((s.split('=', 1)[1] for s in (ROOT/'android/local.properties').read_text().splitlines() if s.startswith('sdk.dir=')), None)
    if not sdk: parser.error('Set ANDROID_HOME to the Android SDK')
    adb = str(Path(sdk)/'platform-tools/adb')
    def device(*parts, check=True):
        return subprocess.run([adb, '-s', args.device, *parts], text=True, capture_output=True, check=check, timeout=15).stdout.strip()
    devices = subprocess.check_output([adb, 'devices'], text=True).splitlines()[1:]
    ready = [r.split()[0] for r in devices if len(r.split()) == 2 and r.split()[1] == 'device']
    if ready != [args.device]: parser.error('Connect only the requested test device so Gradle cannot select another device')
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    stage = args.output or ROOT/'dist/evidence'/('android-soak-'+stamp)
    stage.mkdir(parents=True, exist_ok=False)
    report = {'schema_version': 1, 'source_commit': source, 'production_reference': args.production_ref,
              'device': args.device, 'abi': device('shell', 'getprop', 'ro.product.cpu.abi'),
              'android_api': device('shell', 'getprop', 'ro.build.version.sdk'),
              'emulator': device('shell', 'getprop', 'ro.kernel.qemu') == '1', 'real_provider_account': False,
              'started_at': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'scenarios': []}
    scenarios = [('idle', 'ChatDeliveryTest', 'idleConnectionsSurviveTwoHeartbeatIntervalsAndStillDeliver',
                  {'niobium.idle_seconds': args.idle_seconds}, args.idle_seconds+600),
                 ('dsh', 'LocalRuntimeTest', 'nativeDshProviderCreatesPixelsAndStreamsThroughRealDsh',
                  {'niobium.dsh_turns': args.dsh_turns, 'niobium.dsh_pause_seconds': args.dsh_pause_seconds}, args.dsh_turns*(args.dsh_pause_seconds+100)+600)]
    for kind, suite, method, params, timeout in scenarios:
        command = ['./gradlew', ':app:connectedDebugAndroidTest', '--console=plain',
                   '-Pandroid.injected.device.serial='+args.device,
                   '-Pandroid.testInstrumentationRunnerArguments.class=io.sovware.niobium.'+suite+'#'+method]
        command += ['-Pandroid.testInstrumentationRunnerArguments.'+k+'='+str(v) for k,v in params.items()]
        print(f'Starting {kind}: {params}', flush=True)
        with (stage/(kind+'.log')).open('w') as output:
            process = subprocess.Popen(command, cwd=ROOT/'android', env=os.environ|{'ANDROID_HOME':sdk,'ANDROID_SERIAL':args.device}, stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
            deadline = time.monotonic()+timeout; next_progress = 0; completed = False
            try:
                while process.poll() is None:
                    if time.monotonic() > deadline: raise TimeoutError(kind+' scenario exceeded its deadline')
                    if time.monotonic() >= next_progress:
                        result = device('exec-out', 'run-as', 'io.sovware.niobium.debug', 'cat', 'cache/niobium-soak-'+kind+'.json', check=False)
                        try:
                            receipt = json.loads(result); sample = receipt['samples'][-1]
                            print(f"{kind}: {sample['elapsed_ms']//1000}s, {sample['counts']}", flush=True)
                        except (ValueError, KeyError, IndexError): pass
                        next_progress = time.monotonic()+30
                    time.sleep(2)
                if process.returncode: raise RuntimeError(f'{kind} failed; inspect {stage/(kind+".log")}')
                completed = True
            finally:
                if process.poll() is None:
                    os.killpg(process.pid, signal.SIGTERM)
                    try: process.wait(10)
                    except subprocess.TimeoutExpired: os.killpg(process.pid, signal.SIGKILL); process.wait()
                if not completed: device('shell', 'am', 'force-stop', 'io.sovware.niobium.debug', check=False)
        results = ROOT/'android/app/build/outputs/androidTest-results/connected/debug'
        xmls = list(results.glob('TEST-*.xml'))
        assert len(xmls) == 1, 'Expected one test-device report'
        root = ET.parse(xmls[0]).getroot()
        assert int(root.attrib['tests']) == 1 and all(int(root.attrib.get(k,0)) == 0 for k in ['failures','errors','skipped'])
        assert [c.attrib['name'] for c in root.iter('testcase')] == [method]
        shutil.copy2(xmls[0], stage/(kind+'-native.xml'))
        samples = []
        for log in results.rglob('logcat-*'+method+'.txt'):
            for line in log.read_text(errors='replace').splitlines():
                if 'NiobiumSoak' in line and '{' in line:
                    row = json.loads(line[line.index('{'):])
                    if row.get('kind') == kind: samples.append(row)
        assert samples and samples[-1]['phase'] == 'scenario_passed', 'Missing final structured acceptance receipt'
        counts = samples[-1]['sample']['counts']
        if kind == 'idle':
            assert counts['confirmed_delivery'] and counts['owner_stable'] and samples[-1]['sample']['elapsed_ms'] >= args.idle_seconds*1000
        else:
            assert counts['completed_turns'] == args.dsh_turns and counts['rooms_verified'] and counts['owner_stable']
            assert samples[-1]['sample']['elapsed_ms'] >= args.dsh_turns*args.dsh_pause_seconds*1000
        (stage/(kind+'-samples.json')).write_text(json.dumps(samples,indent=2)+'\n')
        apk = ROOT/'android/app/build/outputs/apk/debug/app-debug.apk'
        report['scenarios'].append({'kind':kind,'status':'pass','parameters':params,'samples':samples,
                                    'debug_apk_sha256':hashlib.file_digest(apk.open('rb'),'sha256').hexdigest()})
        (stage/'validation.json').write_text(json.dumps(report,indent=2)+'\n')
    assert git('rev-parse', 'HEAD') == source and not git('status','--porcelain'), 'Source changed during acceptance'
    report.update(status='pass',completed_at=datetime.datetime.now(datetime.timezone.utc).isoformat(),memory_scope='App and instrumentation only; guest DSH/Node excluded',limitations=['Controlled-provider instrumentation acceptance; does not establish battery, Doze or real-account behavior.'])
    (stage/'validation.json').write_text(json.dumps(report,indent=2)+'\n')
    print('PASS: controlled Android soak receipts saved to '+str(stage), flush=True)

if __name__ == '__main__': main()
