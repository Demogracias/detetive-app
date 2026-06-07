const fs = require('fs');
const h = fs.readFileSync('app.html', 'utf8');
let idx = -1;
let count = 0;
while ((idx = h.indexOf('prompt(', idx + 1)) !== -1) {
    count++;
    const line = h.substring(0, idx).split('\n').length;
    const ctx = h.substring(Math.max(0, idx - 40), idx + 60);
    console.log('#' + count + ' line ' + line + ':', ctx.replace(/\n/g, ' '));
}
