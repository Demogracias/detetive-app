const fs = require('fs');
const h = fs.readFileSync('app.html', 'utf8');
const i = h.indexOf('id="app-main"');
const ln = h.substring(0, i).split('\n').length;
console.log('Line:', ln);
console.log(h.substring(Math.max(0,i-200), i+200));
