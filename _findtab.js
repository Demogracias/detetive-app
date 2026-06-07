const fs = require('fs');
const h = fs.readFileSync('app.html', 'utf8');
const i = h.indexOf('id="tab-jogatina"');
const start = h.lastIndexOf('<div', i);
const end = h.indexOf('-->', i);
let endDiv = h.indexOf('</div>', i);
endDiv = h.indexOf('</div>', endDiv + 6);
console.log('=== JOGATINA TAB ===');
console.log(h.substring(start, endDiv + 6));
