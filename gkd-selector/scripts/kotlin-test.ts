import { runGradle } from './gradle.ts';

await runGradle([
  ':gkd-selector:jvmTest',
  ':gkd-selector:jsNodeTest',
]);
