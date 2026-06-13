/**
 * 使用 esbuild 将 CodeMirror 6 打包为单个 ESM 文件
 * 输出到 src/main/resources/static/js/vendor/codemirror.js
 */

import * as esbuild from 'esbuild'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const root = path.resolve(__dirname, '..')

await esbuild.build({
  entryPoints: [path.join(root, 'src/main/resources/static/js/vendor/codemirror-bundle.js')],
  bundle: true,
  format: 'esm',
  outfile: path.join(root, 'src/main/resources/static/js/vendor/codemirror.js'),
  target: 'es2020',
  sourcemap: false,
  legalComments: 'none',
  logLevel: 'info',
})

console.log('✅ CodeMirror bundle 已生成')
