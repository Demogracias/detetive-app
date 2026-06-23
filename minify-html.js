const fs = require('fs');
const path = require('path');
const os = require('os');
const { execSync } = require('child_process');

const filePath = process.argv[2] || 'app.html';
let html = fs.readFileSync(filePath, 'utf8');
const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), 'mh-'));

// Minify inline script blocks (non-src)
html = html.replace(/(<script\b(?!\s*src\b)[^>]*>)([\s\S]*?)(<\/script>)/gi, (match, open, code, close) => {
    if (!code.trim()) return match;
    const tmpFile = path.join(tmpDir, 'block_' + Date.now() + '_' + Math.random().toString(36).slice(2) + '.js');
    fs.writeFileSync(tmpFile, code, 'utf8');
    try {
        execSync('npx --yes terser "' + tmpFile + '" --compress --mangle --output "' + tmpFile + '"', { stdio: 'pipe', timeout: 60000, shell: true });
        const minified = fs.readFileSync(tmpFile, 'utf8').trim();
        return open + '\n' + minified + '\n' + close;
    } catch (e) {
        return match;
    } finally {
        try { fs.unlinkSync(tmpFile); } catch(e) {}
    }
});

// Minify style blocks
html = html.replace(/<style>([\s\S]*?)<\/style>/gi, (match, css) => {
    if (!css.trim()) return match;
    let minified = css
        .replace(/\/\*[\s\S]*?\*\//g, '')
        .replace(/[\r\n]+/g, ' ')
        .replace(/\s{2,}/g, ' ')
        .replace(/\s*([{}:;,])\s*/g, '$1')
        .replace(/;\s*}/g, '}')
        .trim();
    return '<style>' + minified + '</style>';
});

// Clean up
try { fs.rmSync(tmpDir, { recursive: true, force: true }); } catch(e) {}

fs.writeFileSync(filePath, html, 'utf8');
console.log('Minification complete: ' + filePath);
