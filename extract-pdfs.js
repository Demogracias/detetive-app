const fs = require('fs');
const path = require('path');
const { PDFParse } = require('pdf-parse');

const srcDir = path.join(__dirname, 'arquivo oculto');
const outFile = path.join(__dirname, 'pdf-texts.json');

const files = [
  { id: 'pdf_chamado_cthulhu', file: 'H.P. Lovecraft - O Chamado de Cthulhu_v2 (1928).pdf', name: 'O Chamado de Cthulhu (PDF)' },
  { id: 'pdf_sombra_innsmouth', file: 'A Sombra Sobre Innsmouth - H. P. Lovecraft.pdf', name: 'A Sombra Sobre Innsmouth (PDF)' },
  { id: 'pdf_montanhas_loucura', file: 'NAS MONTANHAS DA LOUCURA - H. P. Lovecraft.pdf', name: 'Nas Montanhas da Loucura (PDF)' },
  { id: 'pdf_horror_dunwich', file: 'O Horror de Dunwich - H.P. Lovecraft.pdf', name: 'O Horror de Dunwich (PDF)' },
  { id: 'pdf_rei_amarelo', file: 'o_rei_de_amarelo_-_richard_w._chambers.pdf', name: 'O Rei de Amarelo (PDF)' },
  { id: 'pdf_goetia', file: 'arte_da_goetia.pdf', name: 'Ars Goetia (PDF)' },
  { id: 'pdf_cthulhu_rpg', file: 'ilide.info-hwfwm-pen-paper-edition-i-docx-pr_c8f9a93efec13bb76679a71ca8253392.pdf', name: 'Chamado de Cthulhu RPG (PDF)' }
];

(async () => {
  const results = [];
  for (const f of files) {
    const p = path.join(srcDir, f.file);
    if (!fs.existsSync(p)) {
      console.error(`NOT FOUND: ${f.file}`);
      results.push({ id: f.id, name: f.name, content: `[Arquivo não encontrado: ${f.file}]` });
      continue;
    }
    const buf = fs.readFileSync(p);
    try {
      const fileUrl = 'file:///' + p.replace(/\\/g, '/');
      const parser = new PDFParse({ url: fileUrl });
      const result = await parser.getText();
      let text = result.text || '';
      text = text.replace(/\u2013|\u2014/g, '--').replace(/\u2018|\u2019/g, "'").replace(/\u201c|\u201d/g, '"');
      if (text.length > 500000) text = text.substring(0, 500000) + '\n\n[... TRUNCADO POR TAMANHO ...]';
      results.push({ id: f.id, name: f.name, content: text });
      console.log(`OK: ${f.file} -> ${text.length} chars`);
    } catch (e) {
      console.error(`FAIL: ${f.file} - ${e.message}`);
      results.push({ id: f.id, name: f.name, content: `[Falha ao extrair texto: ${e.message}]` });
    }
  }
  fs.writeFileSync(outFile, JSON.stringify(results, null, 2), 'utf8');
  console.log(`\nSaved to ${outFile}`);
})();
