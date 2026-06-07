const fs = require('fs');
const path = require('path');
const https = require('https');

const PUBLIC_DIR = path.join(__dirname, 'enem-api-main', 'enem-api-main', 'public');
const EXTRACTOR_DIR = path.join(__dirname, 'enem-extractor-master', 'provas');
const ENEM_DIR = path.join(__dirname, 'enem');
const OUT_FILE = path.join(ENEM_DIR, 'enem-data.js');

const DISCIPLINA_LOOKUP = {
    'ciencias-humanas': 'Ciências Humanas',
    'ciencias-natureza': 'Ciências da Natureza',
    'linguagens': 'Linguagens',
    'matematica': 'Matemática'
};

function ensureDir(dir) {
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
}

function downloadFile(url, dest) {
    return new Promise((resolve, reject) => {
        if (fs.existsSync(dest)) { resolve(false); return; }
        ensureDir(path.dirname(dest));
        const file = fs.createWriteStream(dest);
        https.get(url, res => {
            if (res.statusCode === 200) { res.pipe(file); file.on('finish', () => { file.close(); resolve(true); }); }
            else { file.close(); fs.unlinkSync(dest); reject(new Error(`${res.statusCode} ${url}`)); }
        }).on('error', err => { file.close(); if (fs.existsSync(dest)) fs.unlinkSync(dest); reject(err); });
    });
}

function contentBlocksToText(blocks) {
    return blocks.map(b => {
        if (b.type === 'image') return `![Imagem](${b.content})`;
        return b.content || '';
    }).join('').replace(/\s+/g, ' ').trim();
}

function extractTextBlocks(blocks) {
    return blocks.map(b => b.content || '').join('').trim();
}

async function processExtractorData(output) {
    console.log('\n=== Processando dados do enem-extractor ===');
    if (!fs.existsSync(EXTRACTOR_DIR)) { console.log('  Pasta provas/ não encontrada, pulando'); return; }

    const years = fs.readdirSync(EXTRACTOR_DIR).filter(d => /^\d{4}$/.test(d));
    for (const year of years) {
        const days = fs.readdirSync(path.join(EXTRACTOR_DIR, year)).filter(d => /^\d$/.test(d));
        for (const day of days) {
            const outputFile = path.join(EXTRACTOR_DIR, year, day, 'output.json');
            if (!fs.existsSync(outputFile)) continue;
            console.log(`  ${year} - Dia ${day}...`);

            const raw = JSON.parse(fs.readFileSync(outputFile, 'utf8'));
            const questions = raw.data || [];

            if (!output.questions[year]) {
                output.questions[year] = [];
                output.exams.push({
                    year: parseInt(year),
                    title: `ENEM ${year} (Dia ${day})`,
                    disciplines: [{ label: 'Não classificado', value: 'unknown' }],
                    languages: []
                });
            }

            for (const q of questions) {
                const imgBlocks = (q.content || []).filter(b => b.type === 'image');
                let context = contentBlocksToText(q.content);

                const alternatives = [];
                let correctLetter = null;

                for (let i = 0; i < 5; i++) {
                    const alt = q.alternatives && q.alternatives[i];
                    if (!alt) continue;
                    const letter = alt.alternative || String.fromCharCode(65 + i);
                    const text = extractTextBlocks(alt.content || []);
                    alternatives.push({ letter, text, isCorrect: !!alt.correct });
                    if (alt.correct) correctLetter = letter;
                }

                // Copy images from extractor
                const files = [];
                for (const img of imgBlocks) {
                    const imgUrl = img.content;
                    const match = imgUrl.match(/\/provas\/(.+)$/);
                    if (match) {
                        const localPath = path.join('provas', match[1]);
                        const fullPath = path.join(ENEM_DIR, localPath);
                        files.push(path.join('enem', localPath).replace(/\\/g, '/'));

                        // Try to copy from local extractor folder
                        const srcPath = path.join(EXTRACTOR_DIR, match[1]);
                        if (fs.existsSync(srcPath)) {
                            ensureDir(path.dirname(fullPath));
                            fs.copyFileSync(srcPath, fullPath);
                        } else {
                            // Download from GitHub
                            try {
                                await downloadFile(imgUrl, fullPath);
                            } catch (e) {
                                // Image not available, skip
                            }
                        }
                    }
                }

                output.questions[year].push({
                    index: q.number,
                    title: `Questão ${q.number} - ENEM ${year} (Dia ${day})`,
                    discipline: 'unknown',
                    language: null,
                    context: context,
                    alternativesIntroduction: null,
                    alternatives: alternatives,
                    correctAlternative: correctLetter,
                    files: files,
                    source: 'extractor'
                });
            }
        }
    }
}

async function main() {
    console.log('=== Gerando dados do ENEM ===');
    ensureDir(ENEM_DIR);

    const output = { exams: [], questions: {} };

    // --- Part 1: enem-api-main data (2009-2023) ---
    if (fs.existsSync(path.join(PUBLIC_DIR, 'exams.json'))) {
        console.log('Processando enem-api-main (2009-2023)...');
        const examsJson = JSON.parse(fs.readFileSync(path.join(PUBLIC_DIR, 'exams.json'), 'utf8'));
        const exams = examsJson.value || examsJson;

        let totalApi = 0;
        for (const exam of exams) {
            const yearStr = String(exam.year);
            const detailsPath = path.join(PUBLIC_DIR, yearStr, 'details.json');
            if (!fs.existsSync(detailsPath)) { console.log(`  ${exam.title}: details.json não encontrado`); continue; }

            const details = JSON.parse(fs.readFileSync(detailsPath, 'utf8'));
            output.exams.push({
                year: exam.year,
                title: details.title,
                disciplines: details.disciplines,
                languages: details.languages,
                source: 'enem-api'
            });

            output.questions[yearStr] = [];
            const questionList = details.questions || [];

            for (const qInfo of questionList) {
                const qIndex = qInfo.index;
                const qDirBase = path.join(PUBLIC_DIR, yearStr, 'questions');
                let qDir = path.join(qDirBase, String(qIndex));
                let detailsFile = path.join(qDir, 'details.json');

                if (!fs.existsSync(detailsFile) && qInfo.language) {
                    qDir = path.join(qDirBase, `${qIndex}-${qInfo.language}`);
                    detailsFile = path.join(qDir, 'details.json');
                }

                if (!fs.existsSync(detailsFile)) { continue; }

                const qData = JSON.parse(fs.readFileSync(detailsFile, 'utf8'));

                let files = qData.files || [];
                if (files.length === 0 && qData.context) {
                    const regex = /!\[\]\(https:\/\/enem\.dev\/([^)]+)\)/g;
                    let m; while ((m = regex.exec(qData.context)) !== null) files.push(`https://enem.dev/${m[1]}`);
                }

                let context = qData.context || '';
                for (const url of files) {
                    const filename = url.replace('https://enem.dev/', '');
                    const localPath = path.join('enem', filename);
                    const fullLocalPath = path.join(ENEM_DIR, filename);
                    const sourcePath = path.join(PUBLIC_DIR, filename);
                    if (fs.existsSync(sourcePath)) {
                        ensureDir(path.dirname(fullLocalPath));
                        fs.copyFileSync(sourcePath, fullLocalPath);
                    }
                    context = context.split(url).join(localPath);
                }

                output.questions[yearStr].push({
                    index: qData.index,
                    title: qData.title,
                    discipline: qData.discipline,
                    language: qData.language,
                    context: context,
                    alternativesIntroduction: qData.alternativesIntroduction || null,
                    alternatives: qData.alternatives.map(a => ({ letter: a.letter, text: a.text, isCorrect: a.isCorrect })),
                    correctAlternative: qData.correctAlternative,
                    files: files.map(url => path.join('enem', url.replace('https://enem.dev/', '')).replace(/\\/g, '/')),
                    source: 'enem-api'
                });
                totalApi++;
            }
            console.log(`  ${exam.title}: ${output.questions[yearStr].length} questões`);
        }
        console.log(`Total enem-api-main: ${totalApi} questões`);
    }

    // --- Part 2: enem-extractor data ---
    await processExtractorData(output);

    // --- Write output ---
    let totalQuestions = 0;
    for (const y in output.questions) totalQuestions += output.questions[y].length;

    let jsContent = '// Dados consolidados do ENEM\n';
    jsContent += '// Gerado por generate-enem-data.js\n';
    jsContent += 'var ENEM_DATA = ' + JSON.stringify(output) + ';\n';

    fs.writeFileSync(OUT_FILE, jsContent, 'utf8');
    const fileSize = (fs.statSync(OUT_FILE).size / (1024 * 1024)).toFixed(2);
    console.log(`\n✓ Arquivo gerado: ${OUT_FILE} (${fileSize} MB)`);
    console.log(`✓ Total de questões: ${totalQuestions}`);

    // Count images
    let imgCount = 0;
    function countImages(dir) {
        if (!fs.existsSync(dir)) return;
        fs.readdirSync(dir).forEach(f => {
            const fp = path.join(dir, f);
            if (fs.statSync(fp).isDirectory()) countImages(fp);
            else if (/\.(jpg|png)$/i.test(f)) imgCount++;
        });
    }
    ['2009','2010','2011','2012','2013','2014','2015','2016','2017','2018','2019','2020','2021','2022','2023','2024','provas'].forEach(d => countImages(path.join(ENEM_DIR, d)));
    console.log(`✓ Imagens: ${imgCount}`);
}

main().catch(console.error);
