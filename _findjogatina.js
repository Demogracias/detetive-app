const fs = require('fs');
const h = fs.readFileSync('app.html', 'utf8');
const i = h.indexOf('id="tab-jogatina"');
// Find opening div
const open = h.lastIndexOf('<div', i);
// Find the jogatina section end - look for the next tab div or modal
const close = h.indexOf('<!-- MODAIS -->', i);
console.log('=== JOGATINA TAB CONTENT ===');
console.log(h.substring(open, close));
console.log('=== END ===');
