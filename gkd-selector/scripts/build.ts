import { cp, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import { basename, dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

import { runGradle } from './gradle.ts';

type SourceMap = {
  sources?: string[];
};

const nonNullGenericDeclarations = [
  'export declare class SelectorMatch<T extends any>',
  'const constructor: abstract new <T extends any>() => SelectorMatch<T>;',
  'export declare class SelectorMatchUnit<T extends any>',
  'const constructor: abstract new <T extends any>() => SelectorMatchUnit<T>;',
  'export declare class SelectorMatchStep<T extends any>',
  'const constructor: abstract new <T extends any>() => SelectorMatchStep<T>;',
  'export declare abstract class JsNodeAdapter<T extends any>',
  'const constructor: abstract new <T extends any>() => JsNodeAdapter<T>;',
];

const typescriptDeclarationRewrites = [
  ...nonNullGenericDeclarations.map((source) => ({
    source,
    target: source.replace('extends any', 'extends {}'),
  })),
  {
    source: 'abstract getNodeKey(node: T): any;',
    target: 'abstract getNodeKey(node: T): NonNullable<unknown>;',
  },
];

const scriptDir = dirname(fileURLToPath(import.meta.url));
const packageDir = dirname(scriptDir);
const repositoryDir = dirname(packageDir);
const projectName = basename(packageDir);
const distDir = join(packageDir, 'dist');
const mainOutputDir = join(
  repositoryDir,
  `build/js/packages/${projectName}/kotlin`,
);
const gradleTasks = [
  `:${projectName}:jsProductionExecutableCompileSync`,
];

await runGradle(gradleTasks);
console.log();

if (dirname(distDir) !== packageDir) {
  throw new Error(`Refusing to replace a directory outside ${packageDir}`);
}

await rm(distDir, { recursive: true, force: true });
let rewrittenProjectSourceCount = 0;
let rewrittenBuildSourceCount = 0;
let rewrittenTypeDeclarationCount = 0;
let sourceMapCount = 0;

const outputs = [
  {
    source: mainOutputDir,
    target: distDir,
  },
];

for (const output of outputs) {
  await cp(output.source, output.target, { recursive: true });
  const outputFileNames = await readdir(output.target);
  const declarationFileNames = outputFileNames.filter((name) =>
    name.endsWith('.d.mts'),
  );
  for (const declarationFileName of declarationFileNames) {
    const declarationFile = join(output.target, declarationFileName);
    let declaration = await readFile(declarationFile, 'utf8');
    for (const rewrite of typescriptDeclarationRewrites) {
      const occurrenceCount = declaration.split(rewrite.source).length - 1;
      if (occurrenceCount !== 1) {
        throw new Error(
          `Expected one ${rewrite.source} declaration in ${declarationFileName}, found ${occurrenceCount}`,
        );
      }
      declaration = declaration.replace(rewrite.source, rewrite.target);
      rewrittenTypeDeclarationCount += 1;
    }
    await writeFile(declarationFile, declaration, 'utf8');
  }

  const mapFileNames = outputFileNames.filter((name) =>
    name.endsWith('.map'),
  );
  sourceMapCount += mapFileNames.length;

  for (const mapFileName of mapFileNames) {
    const mapFile = join(output.target, mapFileName);
    const sourceMap = JSON.parse(await readFile(mapFile, 'utf8')) as SourceMap;

    sourceMap.sources = sourceMap.sources?.map((source) => {
      const normalizedSource = source.replaceAll('\\', '/');
      const mainMarker = `/${projectName}/src/`;
      const mainIndex = normalizedSource.lastIndexOf(mainMarker);
      if (mainIndex !== -1) {
        const sourceFile = join(
          packageDir,
          'src',
          normalizedSource.slice(mainIndex + mainMarker.length),
        );
        rewrittenProjectSourceCount += 1;
        return relative(dirname(mapFile), sourceFile).replaceAll('\\', '/');
      }

      const mainBuildMarker = `/${projectName}/build/`;
      const mainBuildIndex = normalizedSource.lastIndexOf(mainBuildMarker);
      if (mainBuildIndex !== -1) {
        rewrittenBuildSourceCount += 1;
        const buildFile = join(
          packageDir,
          'build',
          normalizedSource.slice(mainBuildIndex + mainBuildMarker.length),
        );
        return relative(dirname(mapFile), buildFile).replaceAll('\\', '/');
      }

      return normalizedSource;
    });

    await writeFile(mapFile, `${JSON.stringify(sourceMap)}\n`, 'utf8');
  }
}

if (rewrittenProjectSourceCount === 0) {
  throw new Error(
    `No ${projectName} source paths found in generated source maps`,
  );
}

const expectedTypeDeclarationCount =
  typescriptDeclarationRewrites.length * outputs.length;
if (rewrittenTypeDeclarationCount !== expectedTypeDeclarationCount) {
  throw new Error(
    `Rewrote ${rewrittenTypeDeclarationCount} TypeScript declaration(s), expected ${expectedTypeDeclarationCount}`,
  );
}

console.log(`Copied Kotlin/JS output to ${distDir}`);
console.log(
  `Rewrote ${rewrittenProjectSourceCount} project source path(s) and ${rewrittenBuildSourceCount} build source path(s) in ${sourceMapCount} source map file(s)`,
);
console.log(
  `Rewrote ${rewrittenTypeDeclarationCount} TypeScript declaration(s)`,
);
