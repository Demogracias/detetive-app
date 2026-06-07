const fs = require('fs');
const JavaScriptObfuscator = require('javascript-obfuscator');

const filePath = process.argv[2];
if (!filePath) {
    console.error('Usage: node obfuscate-html.js <path-to-html>');
    process.exit(1);
}

let html = fs.readFileSync(filePath, 'utf-8');

const obfuscatorOptions = {
    compact: true,
    controlFlowFlattening: true,
    controlFlowFlatteningThreshold: 0.5,
    deadCodeInjection: false,
    debugProtection: false,
    disableConsoleOutput: false,
    identifierNamesGenerator: 'hexadecimal',
    renameGlobals: false,
    rotateStringArray: true,
    selfDefending: true,
    stringArray: true,
    stringArrayEncoding: ['base64'],
    stringArrayThreshold: 0.7,
    target: 'browser',
    unicodeEscapeSequence: false
};

function isExternalOrJsonScript(html, idx) {
    const before = html.slice(Math.max(0, idx - 200), idx + 50);
    if (/src\s*=\s*["']/.test(before)) return true;
    if (/type\s*=\s*["']application\/json["']/.test(before)) return true;
    if (/type\s*=\s*["']text\/javascript["']/.test(before)) return false;
    return false;
}

function extractNextScript(html, startIdx) {
    const openIdx = html.indexOf('<script', startIdx);
    if (openIdx === -1) return null;
    const closeOpen = html.indexOf('>', openIdx);
    if (closeOpen === -1) return null;
    const contentStart = closeOpen + 1;
    const closeIdx = html.indexOf('</script>', contentStart);
    if (closeIdx === -1) return null;
    const content = html.slice(contentStart, closeIdx);
    return {
        fullOpenTag: html.slice(openIdx, closeOpen + 1),
        content: content,
        contentStart: contentStart,
        contentEnd: closeIdx,
        fullStart: openIdx,
        fullEnd: closeIdx + 9
    };
}

let pos = 0;
let count = 0;

while (true) {
    const script = extractNextScript(html, pos);
    if (!script) break;

    pos = script.fullEnd;
    const trimmed = script.content.trim();
    if (!trimmed) continue;
    if (script.fullOpenTag.includes('src=') || script.fullOpenTag.includes('src=')) continue;
    if (script.fullOpenTag.includes('application/json')) continue;

    try {
        const obfuscated = JavaScriptObfuscator.obfuscate(trimmed, obfuscatorOptions).getObfuscatedCode();
        const newSection = script.fullOpenTag + '\n' + obfuscated + '\n</script>';
        html = html.slice(0, script.fullStart) + newSection + html.slice(script.fullEnd);
        count++;
        const delta = script.fullEnd - script.fullStart;
        pos = script.fullStart + newSection.length;
        console.log('Obfuscated inline script #' + count + ' (' + trimmed.length + ' chars → ' + obfuscated.length + ' chars)');
    } catch (e) {
        console.error('Failed to obfuscate script block:', e.message);
        process.exit(1);
    }
}

if (count === 0) {
    console.log('No inline script blocks found to obfuscate.');
} else {
    fs.writeFileSync(filePath, html, 'utf-8');
    console.log('Obfuscation complete: ' + filePath + ' (' + count + ' scripts obfuscated)');
}
