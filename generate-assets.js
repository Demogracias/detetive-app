const fs = require('fs');
const path = require('path');
const dir = 'C:\\Users\\Joaov\\Desktop\\ver final\\classicas';
const files = fs.readdirSync(dir).filter(f => f.endsWith('.txt')).sort();
let output = 'window.ASSETS_DATA = window.ASSETS_DATA || {};\n';
output += 'Object.assign(window.ASSETS_DATA, {\n';
files.forEach((file, i) => {
  const id = 'classic' + (i+1) + '.mp3';
  const content = fs.readFileSync(path.join(dir, file), 'utf8').trim();
  const comma = (i < files.length - 1) ? ',' : '';
  output += '  "' + id + '": `' + content + '`' + comma + '\n';
});
output += '});\n';
fs.writeFileSync('C:\\Users\\Joaov\\Desktop\\ver final\\assets-classicas.js', output, 'utf8');
var stats = fs.statSync('C:\\Users\\Joaov\\Desktop\\ver final\\assets-classicas.js');
console.log('OK: ' + files.length + ' entries, ' + (stats.size/1024/1024).toFixed(1) + 'MB');
