const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

const [serial, command = 'status'] = process.argv.slice(2);
if (!serial || !['status', 'connect', 'enable'].includes(command)) {
  console.error('Usage: node scripts/watch-calls.cjs WATCH_SERIAL [status|connect|enable]');
  process.exit(2);
}
const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
if (!sdk) throw new Error('Set ANDROID_HOME to the installed Android SDK directory.');

const android = path.join(sdk, 'platforms', 'android-36', 'android.jar');
const d8 = path.join(sdk, 'build-tools', '36.0.0', 'lib', 'd8.jar');
for (const file of [android, d8]) {
  if (!fs.existsSync(file)) throw new Error('Missing SDK file: ' + file);
}

const run = (program, args) => execFileSync(program, args, {
  encoding: 'utf8', stdio: ['ignore', 'pipe', 'inherit'], timeout: 30000, windowsHide: true
});
const adb = (...args) => run('adb', ['-s', serial, ...args]);
if (adb('get-state').trim() !== 'device') throw new Error('The watch must be connected in ADB.');
if (!/^feature:android\.hardware\.type\.watch\s*$/m.test(adb('shell', 'pm', 'list', 'features'))) {
  throw new Error('The selected ADB device is not a watch.');
}

const tempRoot = fs.realpathSync(os.tmpdir());
const local = fs.realpathSync(fs.mkdtempSync(path.join(tempRoot, 'wristlink-hfp-')));
const remote = '/data/local/tmp/' + path.basename(local) + '.jar';
let copied = false;
try {
  const classes = path.join(local, 'classes');
  fs.mkdirSync(classes);
  run('javac', ['--release', '17', '-cp', android, '-d', classes, path.join(__dirname, 'HfpRestore.java')]);
  const files = fs.readdirSync(classes).filter(file => file.endsWith('.class'));
  run('java', ['-cp', d8, 'com.android.tools.r8.D8', '--lib', android, '--min-api', '36',
    '--output', local, ...files.map(file => path.join(classes, file))]);
  const jar = path.join(local, 'helper.jar');
  run('jar', ['--create', '--file', jar, '-C', local, 'classes.dex']);
  adb('push', jar, remote);
  copied = true;
  process.stdout.write(adb('shell', 'CLASSPATH=' + remote, 'app_process', '/system/bin', 'HfpRestore', command));
} catch (error) {
  if (error.stdout) process.stdout.write(error.stdout);
  if (error.status !== 3) console.error('Hands-Free helper did not complete:', error.message);
  process.exitCode = error.status || 1;
} finally {
  if (copied) {
    try { adb('shell', 'rm', '-f', remote); }
    catch { console.error('ADB disconnected. Remove the temporary helper after reconnecting: ' + remote); }
  }
  if (path.dirname(local) === tempRoot && path.basename(local).startsWith('wristlink-hfp-')) {
    fs.rmSync(local, { recursive: true, force: true });
  }
}
