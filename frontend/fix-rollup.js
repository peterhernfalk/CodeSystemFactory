#!/usr/bin/env node
const fs = require('fs');
const path = require('path');

const nativeFile = path.join(__dirname, 'node_modules/rollup/dist/native.js');

if (!fs.existsSync(nativeFile)) {
  console.log('Rollup native.js not found, skipping patch');
  process.exit(0);
}

let content = fs.readFileSync(nativeFile, 'utf8');

// Fix 1: Change const to let and add arm64 override
if (content.includes('const packageBase = getPackageBase();')) {
  content = content.replace(
    'const packageBase = getPackageBase();',
    `let packageBase = getPackageBase();
// Workaround: Force arm64 on darwin arm64 systems
if (platform === 'darwin' && (arch === 'arm64' || process.arch === 'arm64')) {
  packageBase = 'darwin-arm64';
}`
  );
}

// Fix 2: Add fallback in requireWithFriendlyError
if (!content.includes('// Workaround for npm optional dependencies bug: try arm64')) {
  const requirePattern = /(const requireWithFriendlyError = id => \{[^]*?try \{[^]*?return require\(id\);[\s\S]*?\} catch \(error\) \{)/;
  const fallbackCode = `$1
    // Workaround for npm optional dependencies bug: try arm64 if x64 fails on arm64 systems
    if (id && id.includes('darwin-x64') && (arch === 'arm64' || process.arch === 'arm64')) {
      try {
        const arm64Id = id.replace('darwin-x64', 'darwin-arm64');
        return require(arm64Id);
      } catch (e) {
        // Fall through to original error handling
      }
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
console.log('✅ Successfully patched rollup native.js for arm64');

