import { spawn } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repositoryDir = dirname(dirname(scriptDir));
const isWindows = process.platform === 'win32';
const gradleWrapper = join(
  repositoryDir,
  isWindows ? 'gradlew.bat' : 'gradlew',
);

export async function runGradle(tasks: readonly string[]): Promise<void> {
  if (tasks.length === 0) throw new Error('No Gradle tasks specified');

  await new Promise<void>((resolve, reject) => {
    const command = isWindows
      ? (process.env.ComSpec ?? 'cmd.exe')
      : gradleWrapper;
    const args = isWindows
      ? ['/d', '/s', '/c', `""${gradleWrapper}" ${tasks.join(' ')}"`]
      : tasks;
    const gradle = spawn(command, args, {
      cwd: repositoryDir,
      stdio: 'inherit',
      windowsVerbatimArguments: isWindows,
    });
    gradle.on('error', reject);
    gradle.on('exit', (code) => {
      if (code === 0) resolve();
      else reject(new Error(`Gradle exited with code ${code}`));
    });
  });
}
