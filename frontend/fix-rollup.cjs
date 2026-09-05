#!/usr/bin/env node
const fs = require('fs');
const path = require('path');

const nativeFile = path.join(__dirname, 'node_modules/rollup/dist/native.js');

if (!fs.existsSync(nativeFile)) {
  console.log('Rollup native.js not found, skipping patch');
  process.exit(0);
}

let content = fs.readFileSync(nativeFile, 'utf8');

// Fix 1: On darwin, use whichever rollup native package is actually installed
// (npm often installs only one optional). Then set packageBase from that.
// Fix path to @rollup (native.js is in rollup/dist so @rollup is ../../@rollup)
content = content.replace(
  "path.join(__dirname, '..', '@rollup')",
  "path.join(__dirname, '..', '..', '@rollup')"
);
if (!content.includes('preferredDarwinBase')) {
  content = content.replace(
    'const msvcLinkFilenameByArch = {',
    `const rollupDir = path.join(__dirname, '..', '..', '@rollup');
let preferredDarwinBase;
if (platform === 'darwin') {
  const archPreferred = process.arch === 'arm64' ? 'darwin-arm64' : 'darwin-x64';
  if (existsSync(path.join(rollupDir, 'rollup-' + archPreferred))) {
    preferredDarwinBase = archPreferred;
  } else if (existsSync(path.join(rollupDir, 'rollup-darwin-arm64'))) {
    preferredDarwinBase = 'darwin-arm64';
  } else if (existsSync(path.join(rollupDir, 'rollup-darwin-x64'))) {
    preferredDarwinBase = 'darwin-x64';
  }
}
const msvcLinkFilenameByArch = {`
  );
  content = content.replace(
    'let packageBase = getPackageBase();\n// Workaround: Force arm64 on darwin arm64 systems\nif (platform === \'darwin\' && (arch === \'arm64\' || process.arch === \'arm64\')) {\n  packageBase = \'darwin-arm64\';\n}',
    'let packageBase = (platform === \'darwin\' && preferredDarwinBase) ? preferredDarwinBase : getPackageBase();'
  );
  // In case the previous patch already ran and left the old pattern
  content = content.replace(
    /let packageBase = getPackageBase\(\);\s*\/\/ Workaround[^]*?packageBase = 'darwin-arm64';\s*\}/,
    'let packageBase = (platform === \'darwin\' && preferredDarwinBase) ? preferredDarwinBase : getPackageBase();'
  );
  if (content.includes('const packageBase = getPackageBase();')) {
    content = content.replace(
      'const packageBase = getPackageBase();',
      'let packageBase = (platform === \'darwin\' && preferredDarwinBase) ? preferredDarwinBase : getPackageBase();'
    );
  }
}

// Fix 1b: upgrade an already-patched preferredDarwinBase block to respect process.arch
content = content.replace(
  /if \(platform === 'darwin'\) \{\s*if \(existsSync\(path\.join\(rollupDir, 'rollup-darwin-arm64'\)\)\) preferredDarwinBase = 'darwin-arm64';\s*else if \(existsSync\(path\.join\(rollupDir, 'rollup-darwin-x64'\)\)\) preferredDarwinBase = 'darwin-x64';\s*\}/,
  `if (platform === 'darwin') {
  const archPreferred = process.arch === 'arm64' ? 'darwin-arm64' : 'darwin-x64';
  if (existsSync(path.join(rollupDir, 'rollup-' + archPreferred))) {
    preferredDarwinBase = archPreferred;
  } else if (existsSync(path.join(rollupDir, 'rollup-darwin-arm64'))) {
    preferredDarwinBase = 'darwin-arm64';
  } else if (existsSync(path.join(rollupDir, 'rollup-darwin-x64'))) {
    preferredDarwinBase = 'darwin-x64';
  }
}`
);

// Fix 2: Add fallback in requireWithFriendlyError
if (!content.includes('// Workaround: try other darwin native package')) {
  const requirePattern = /(const requireWithFriendlyError = id => \{[^]*?try \{[^]*?return require\(id\);[\s\S]*?\} catch \(error\) \{)/;
  const fallbackCode = `$1
    // Workaround: try other darwin native package if missing (npm optional deps bug)
    if (platform === 'darwin' && id && id.includes('@rollup/rollup-darwin-')) {
      const other = id.includes('darwin-arm64') ? id.replace('darwin-arm64', 'darwin-x64') : id.replace('darwin-x64', 'darwin-arm64');
      try { return require(other); } catch (e) { /* fall through */ }
    }
    `;
  if (requirePattern.test(content)) {
    content = content.replace(requirePattern, fallbackCode);
  }
}

// Fix 3: Update getPackageBase function
if (!content.includes("if (platform === 'darwin' && (arch === 'arm64'")) {
  const getPackageBasePattern = /(function getPackageBase\(\) \{[\s\S]*?)(const imported = bindingsByPlatformAndArch)/;
  if (getPackageBasePattern.test(content)) {
    content = content.replace(
      getPackageBasePattern,
      `$1  // Workaround: Force arm64 on darwin arm64 systems
  if (platform === 'darwin' && (arch === 'arm64' || process.arch === 'arm64')) {
    return 'darwin-arm64';
  }
  $2`
    );
  }
}

fs.writeFileSync(nativeFile, content);

// On Apple Silicon, npm often installs only one @rollup/rollup-darwin-* optional package.
// Node must match that architecture: use native arm64 Node (arch -arm64 npm run dev)
// or install the x64 package when running Node under Rosetta.
console.log('✅ Patched rollup native.js (darwin arch:', process.arch + ')');

