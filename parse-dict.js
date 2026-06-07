const fs = require('fs');
const path = require('path');

const RAW_DIR = 'C:/Users/Joaov/Desktop/ver final/dicionario-aberto-master/raw';
const OUTPUT = 'C:/Users/Joaov/Desktop/ver final/vocab-data.js';
const TARGET = 6000;

const MODERN = new Set('ABCDEFGHIJKLMNOPQRSTUVWXYZÁÀÂÃÉÊÍÓÔÕÚÇabcdefghijklmnopqrstuvwxyzáàâãéêíóôõúç');

function isModernWord(text) {
    for (let i = 0; i < text.length; i++) {
        if (!MODERN.has(text[i])) return false;
    }
    return true;
}

function normalizeText(text) {
    if (!text) return '';
    text = text.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&')
               .replace(/&mdash;/g, '—').replace(/&ndash;/g, '–')
               .replace(/&hellip;/g, '…').replace(/&nbsp;/g, ' ');
    text = text.replace(/<[^>]+>/g, '');
    text = text.replace(/\s+/g, ' ').trim();
    return text;
}

function shouldKeepEntry(word, def) {
    if (!word || !def) return false;
    if (word.length < 3 || word.length > 28) return false;
    if (def.length < 20 || def.length > 250) return false;
    // Skip affixes and particles (start with ... or are very short patterns)
    if (word.startsWith('...') || word.startsWith('..')) return false;
    if (word.includes(' ') || word.includes('-')) return false;
    // Skip if definition contains "designativo" (affix indicator)
    if (def.includes('designativo') || def.includes('Designativo')) return false;
    if (!isModernWord(word)) return false;
    // Skip if word doesn't start with uppercase letter
    if (!/^[A-ZÁÀÂÃÉÊÍÓÔÕÚÇ]/.test(word)) return false;
    // Skip very common grammar words
    const skip = ['DE', 'DA', 'DO', 'EM', 'COM', 'PARA', 'POR', 'SE', 'OS', 'AS', 'NA', 'NO', 'UM', 'UMA'];
    if (skip.includes(word)) return false;
    return true;
}

const letters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('');
let allEntries = [];
let total = 0;

// First pass: collect all valid entries
letters.forEach(letter => {
    const filePath = path.join(RAW_DIR, letter + '.xml');
    if (!fs.existsSync(filePath)) return;
    
    const xml = fs.readFileSync(filePath, 'utf8');
    const entries = xml.split('<entry');
    
    for (let i = 1; i < entries.length; i++) {
        const entry = entries[i];
        const orthMatch = entry.match(/<orth>([^<]+)<\/orth>/);
        const defMatch = entry.match(/<def>([\s\S]*?)<\/def>/);
        
        if (orthMatch && defMatch) {
            total++;
            const word = normalizeText(orthMatch[1]);
            let def = normalizeText(defMatch[1]);
            
            // First sentence only
            const pi = def.indexOf('.');
            if (pi > 0) def = def.substring(0, pi + 1);
            
            if (shouldKeepEntry(word, def)) {
                allEntries.push({ 
                    w: word.toUpperCase(), 
                    d: def.charAt(0).toUpperCase() + def.slice(1),
                    letter: letter
                });
            }
        }
    }
});

console.log(`Total raw: ${total}, Valid after filtering: ${allEntries.length}`);

// Remove duplicates
const seen = new Set();
const unique = allEntries.filter(e => {
    if (seen.has(e.w)) return false;
    seen.add(e.w);
    return true;
});
console.log(`After dedup: ${unique.length}`);

// Distribute selection across letters for variety
const byLetter = {};
unique.forEach(e => {
    if (!byLetter[e.letter]) byLetter[e.letter] = [];
    byLetter[e.letter].push(e);
});

const perLetter = {};
Object.keys(byLetter).forEach(l => {
    perLetter[l] = Math.max(1, Math.round(TARGET * (byLetter[l].length / unique.length)));
});

// Pick entries from each letter
let selected = [];
Object.keys(byLetter).forEach(l => {
    const pool = byLetter[l].sort(() => Math.random() - 0.5);
    const count = Math.min(perLetter[l], pool.length);
    selected = selected.concat(pool.slice(0, count));
});

// If we don't have enough, fill more
if (selected.length < TARGET) {
    const remaining = unique.filter(e => !selected.find(s => s.w === e.w));
    remaining.sort(() => Math.random() - 0.5);
    selected = selected.concat(remaining.slice(0, TARGET - selected.length));
}

// Sort alphabetically
selected.sort((a, b) => a.w.localeCompare(b.w));

console.log(`Final selection: ${selected.length}`);

// Generate JS file
let jsContent = 'window.VOCAB_DATA = [\n';
selected.forEach((entry, idx) => {
    const d = entry.d.replace(/"/g, '\\"');
    jsContent += `  {w:"${entry.w}",d:"${d}"}`;
    if (idx < selected.length - 1) jsContent += ',';
    jsContent += '\n';
});
jsContent += '];\n';

fs.writeFileSync(OUTPUT, jsContent, 'utf8');
console.log(`Written to ${OUTPUT}`);

// Show samples from different letters
const samples = {};
selected.forEach(e => {
    if (!samples[e.w[0]]) samples[e.w[0]] = e;
});
Object.keys(samples).sort().slice(0, 15).forEach(l => {
    const e = samples[l];
    console.log(`  ${e.w}: ${e.d.substring(0, 50)}...`);
});
