import { spawn } from 'node:child_process';
import {
  access,
  cp,
  mkdir,
  mkdtemp,
  readFile,
  rename,
  rm,
  stat,
  writeFile,
} from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

type PackageManifest = {
  name?: string;
  version?: string;
  main?: string;
  types?: string;
  publishConfig?: {
    registry?: string;
  };
};

const scriptDir = dirname(fileURLToPath(import.meta.url));
const packageDir = dirname(scriptDir);
const manifestFile = join(packageDir, 'package.json');
const distDir = join(packageDir, 'dist');
const buildDir = join(packageDir, 'build');

const readManifest = async (file: string): Promise<PackageManifest> =>
  JSON.parse(await readFile(file, 'utf8')) as PackageManifest;

const requireString = (
  value: string | undefined,
  field: string,
  file: string,
): string => {
  if (value === undefined || value.length === 0) {
    throw new Error(`Missing ${field} in ${file}`);
  }
  return value;
};

const pathExists = async (path: string): Promise<boolean> => {
  try {
    await stat(path);
    return true;
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === 'ENOENT') return false;
    throw error;
  }
};

const run = async (
  command: string,
  args: readonly string[],
  cwd: string,
): Promise<void> => {
  await new Promise<void>((resolve, reject) => {
    const child = spawn(command, args, { cwd, stdio: 'inherit' });
    child.on('error', reject);
    child.on('exit', (code, signal) => {
      if (code === 0) {
        resolve();
      } else {
        reject(
          new Error(
            signal === null
              ? `${command} exited with code ${code}`
              : `${command} exited with signal ${signal}`,
          ),
        );
      }
    });
  });
};

const localManifest = await readManifest(manifestFile);
const packageName = requireString(localManifest.name, 'name', manifestFile);
const packageVersion = requireString(
  localManifest.version,
  'version',
  manifestFile,
);
const packageRegistry = requireString(
  localManifest.publishConfig?.registry,
  'publishConfig.registry',
  manifestFile,
);
const packageSpecifier = `${packageName}@${packageVersion}`;
const temporaryDir = await mkdtemp(join(tmpdir(), 'gkd-selector-dist-'));

try {
  await writeFile(
    join(temporaryDir, 'package.json'),
    `${JSON.stringify(
      {
        private: true,
        dependencies: { [packageName]: packageVersion },
      },
      null,
      2,
    )}\n`,
    'utf8',
  );

  try {
    await run(
      'pnpm',
      ['install', '--ignore-scripts', `--registry=${packageRegistry}`],
      temporaryDir,
    );
  } catch (error) {
    throw new Error(
      `Unable to download ${packageSpecifier}. Publish it first or run pnpm --dir gkd-selector build instead.`,
      { cause: error },
    );
  }

  const installedPackageDir = join(
    temporaryDir,
    'node_modules',
    ...packageName.split('/'),
  );
  const installedManifestFile = join(installedPackageDir, 'package.json');
  const installedManifest = await readManifest(installedManifestFile);

  if (
    installedManifest.name !== packageName ||
    installedManifest.version !== packageVersion
  ) {
    throw new Error(
      `Downloaded ${installedManifest.name}@${installedManifest.version}, expected ${packageSpecifier}`,
    );
  }

  const installedDistDir = join(installedPackageDir, 'dist');
  for (const field of ['main', 'types'] as const) {
    const relativeFile = requireString(
      installedManifest[field],
      field,
      installedManifestFile,
    );
    if (!relativeFile.startsWith('./dist/')) {
      throw new Error(
        `Downloaded ${packageSpecifier} has ${field} outside dist: ${relativeFile}`,
      );
    }
    await access(join(installedPackageDir, relativeFile));
  }

  await mkdir(buildDir, { recursive: true });
  const stagingDir = await mkdtemp(join(buildDir, 'fetch-dist-'));
  const stagedDistDir = join(stagingDir, 'dist');
  const previousDistDir = join(stagingDir, 'previous-dist');
  let canRemoveStagingDir = true;

  try {
    await cp(installedDistDir, stagedDistDir, { recursive: true });

    const hadPreviousDist = await pathExists(distDir);
    if (hadPreviousDist) await rename(distDir, previousDistDir);

    try {
      await rename(stagedDistDir, distDir);
    } catch (error) {
      if (hadPreviousDist) {
        try {
          await rename(previousDistDir, distDir);
        } catch (rollbackError) {
          canRemoveStagingDir = false;
          throw new AggregateError(
            [error, rollbackError],
            `Unable to replace ${distDir}; the previous dist remains at ${previousDistDir}`,
          );
        }
      }
      throw error;
    }
  } finally {
    if (canRemoveStagingDir) {
      await rm(stagingDir, { recursive: true, force: true });
    }
  }

  console.log(`Fetched ${packageSpecifier} dist to ${distDir}`);
} finally {
  await rm(temporaryDir, { recursive: true, force: true });
}
