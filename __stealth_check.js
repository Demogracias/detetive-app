
// MODO STEALTH
var stealthMode = safeJSONParse('noir_stealth_mode', false);
var stealthCode = '';
var stealthExpression = '';
var stealthResult = '';
var stealthJustEvaluated = false;

function stealthAtualizar() {
    var d = document.getElementById('stealth-display');
    var h = document.getElementById('stealth-history');
    if (d) d.textContent = stealthExpression || '0';
    if (h) h.textContent = stealthResult ? '= ' + stealthResult : '';
    var c = document.getElementById('stealth-calc');
    if (stealthMode) {
        if (c) { c.style.display = 'flex'; }
    } else {
        if (c) c.style.display = 'none';
    }
}

document.addEventListener('DOMContentLoaded', function() {
    document.querySelectorAll('.calc-btn').forEach(function(b) {
        b.addEventListener('click', function() {
            var v = this.getAttribute('data-v');
            if (!v) return;
            if (v === 'C') {
                stealthExpression = '';
                stealthResult = '';
                stealthCode = '';
                stealthJustEvaluated = false;
                stealthAtualizar();
                return;
            }
            if (v === '=') {
                try {
                    stealthCode += '=';
                    if (stealthCode === '1938=') {
                        stealthMode = false;
                        lsSet('noir_stealth_mode', 'false');
                        stealthAtualizar();
                        return;
                    }
                    stealthCode = stealthCode.slice(-20);
                    var result = Function('"use strict";return (' + stealthExpression + ')')();
                    stealthResult = String(result);
                    stealthExpression = stealthResult;
                    stealthJustEvaluated = true;
                } catch(e) {
                    stealthResult = 'ERRO';
                }
                stealthAtualizar();
                return;
            }
            if (stealthJustEvaluated && /[0-9.]/.test(v)) {
                stealthExpression = '';
                stealthJustEvaluated = false;
            }
            stealthJustEvaluated = false;
            stealthExpression += v;
            stealthCode += v;
            if (stealthCode.length > 20) stealthCode = stealthCode.slice(-20);
            stealthAtualizar();
        });
    });
    stealthAtualizar();
});

function toggleStealth() {
    stealthMode = !stealthMode;
    lsSet('noir_stealth_mode', stealthMode ? 'true' : 'false');
    stealthAtualizar();
    var dbg = document.getElementById('persistent-error');
    if (dbg) { dbg.textContent = 'Stealth: ' + (stealthMode ? '🔒 ATIVADO (digite 1938= na calculadora para sair)' : '🔓 DESATIVADO'); dbg.style.display = 'block'; setTimeout(function(){dbg.style.display='none';},5000); }
}

// DATILOGRAFIA
var dtDict = ['a','abrir','acabar','achar','acontecer','acreditar','água','ainda','algo','algum','alguém','alguns','alta','alto','amar','andar','ano','antes','antigo','ao','aos','aparecer','apenas','apoio','após','aquele','aqueles','ar','árvore','assim','até','através','atrás','bem','boca','bom','braço','caber','cada','cair','cama','caminho','campo','cara','casa','caso','causa','cedo','cento','certeza','certo','céu','chamar','chegar','cima','claro','coisa','como','comprar','compreender','comum','conhecer','conseguir','consigo','contra','contudo','conversa','copiar','coração','corpo','correr','costa','criar','cruz','cuidado','cuja','cujas','cujo','cujos','cultura','cume','cá','dado','dali','dano','dar','de','debaixo','decidir','décimo','dedo','deixar','demais','dentro','depois','desde','desenhar','despejar','destino','deus','dia','direito','dizer','do','dois','donde','dor','dormir','dos','durar','duro','dúvida','e','ela','elas','ele','eles','em','embora','encontrar','então','entre','enviar','era','erro','essa','essas','esse','esses','esta','estado','estamos','estar','estas','estava','este','esteja','estejam','estes','estou','estrada','eu','exemplo','existir','experiência','explicar','face','falar','faltar','fazer','fé','fechar','feliz','ferro','fim','final','fingir','firme','fogo','fonte','fora','força','forma','formar','frente','frase','frio','fugir','fui','função','fundamental','fundo','futuro','gabar','ganhar','gastar','gente','geral','gerar','gesto','golpe','gostar','governo','graça','grade','grande','grupo','guardar','guerra','guiar','gás','há','habitar','falar','fazer','haver','herança','história','hoje','homem','hora','horizonte','humanidade','humano','ideia','igreja','igual','imagem','imaginar','impedir','importante','indagar','índice','informação','início','instantâneo','inteiro','intenso','interior','inverno','ir','irmão','isolar','já','janeiro','jantar','jogar','jornal','jovem','julgar','junho','junto','justiça','lá','lado','lago','largo','lavar','lazer','... (line truncated to 2000 chars)
var dtTimerInterval = null;
var dtStartTime = null;
function dtInit() {
    var ta = document.getElementById('dt-text');
    if (ta) {
        var saved = localStorage.getItem('noir_dt_text');
        if (saved) { ta.value = saved; }
        var savedType = localStorage.getItem('noir_dt_type') || 'dissertativo';
        dtSetType(savedType);
        dtCount();
        var goal = localStorage.getItem('noir_dt_goal');
        var gi = document.getElementById('dt-goal-input');
        if (gi && goal) { gi.value = goal; dtSetGoal(); }
    }
    dtStartTime = Date.now();
    if (dtTimerInterval) clearInterval(dtTimerInterval);
    dtTimerInterval = setInterval(dtUpdateTimer, 1000);
    dtUpdateSaveStatus();
}
function dtUpdateTimer() {
    var el = document.getElementById('dt-timer');
    if (!el) return;
    if (!dtStartTime) { el.textContent = '00:00'; return; }
    var sec = Math.floor((Date.now() - dtStartTime) / 1000);
    var m = Math.floor(sec / 60);
    var s = sec % 60;
    el.textContent = (m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s;
}
function dtSetType(type) {
    document.querySelectorAll('#tab-datilografia .dt-btn').forEach(function(b) { b.classList.remove('active'); });
    var btn = document.getElementById('dt-btn-' + type);
    if (btn) btn.classList.add('active');
    dtShowHelp(type);
    try { localStorage.setItem('noir_dt_type', type); } catch(e) {}
}
function dtShowHelp(type) {
    var el = document.getElementById('dt-help-text'); if(!el) return;
    var tips = {
        dissertativo: '$ cat help/dissertativo — Introdução (tese) → Desenvolvimento (argumentos) → Conclusão (retomada da tese). Use 3ª pessoa, linguagem formal, conectivos lógicos.',
        carta: '$ cat help/carta — Local/data → Saudação → Corpo do texto → Despedida → Assinatura. Varie o registro conforme o destinatário.',
        cronica: '$ cat help/cronica — Texto curto, tom leve/crítico, narração em 1ª pessoa. Parta de um fato cotidiano com olhar pessoal.',
        conto: '$ cat help/conto — Situação inicial → Conflito → Clímax → Desfecho. Poucos personagens, tempo/espaço reduzidos.',
        poema: '$ cat help/poema — Versos, estrofes, rimas (opcionais), ritmo, figuras de linguagem. Explore a sonoridade e a subjetividade.',
        artigo: '$ cat help/artigo — Título → Tese → Argumentos (dados/exemplos) → Contraponto → Conclusão. Assinatura ao final.',
        narrativa: '$ cat help/narrativa — Narrador (1ª ou 3ª pessoa), personagens, enredo, tempo, espaço. Use descrições sensoriais.'
    };
    el.textContent = tips[type] || '$ cat help — Selecione um tipo textual acima.';
}
function dtCount() {
    var ta = document.getElementById('dt-text'); if(!ta) return;
    var txt = ta.value;
    var words = txt.trim() ? txt.trim().split(/\s+/).length : 0;
    var chars = txt.length;
    var paras = txt.trim() ? txt.trim().split(/\n+/).filter(function(p){return p.trim();}).length : 0;
    var wEl = document.getElementById('dt-words'); if(wEl) wEl.textContent = words;
    var cEl = document.getElementById('dt-chars'); if(cEl) cEl.textContent = chars;
    var pEl = document.getElementById('dt-paras'); if(pEl) pEl.textContent = paras;
    // Reading time (200 wpm)
    var rt = document.getElementById('dt-readtime');
    if (rt) {
        var mins = Math.ceil(words / 200);
        rt.textContent = (mins < 1 ? '<1' : mins) + ' min';
    }
    dtUpdateGoalProgress();
}
function dtAutoSave() {
    var ta = document.getElementById('dt-text'); if(!ta) return;
    try { localStorage.setItem('noir_dt_text', ta.value); } catch(e) {}
    dtUpdateSaveStatus();
}
var dtSaveTimeout = null;
function dtUpdateSaveStatus() {
    var el = document.getElementById('dt-save-status'); if(!el) return;
    el.textContent = '✓ auto';
    clearTimeout(dtSaveTimeout);
    dtSaveTimeout = setTimeout(function() { if(el) el.textContent = '💾 auto'; }, 3000);
}
function dtSetGoal() {
    var gi = document.getElementById('dt-goal-input'); if(!gi) return;
    var goal = parseInt(gi.value) || 0;
    try { localStorage.setItem('noir_dt_goal', goal.toString()); } catch(e) {}
    dtUpdateGoalProgress();
}
function dtUpdateGoalProgress() {
    var gi = document.getElementById('dt-goal-input'); if(!gi) return;
    var goal = parseInt(gi.value) || 0;
    var ta = document.getElementById('dt-text'); if(!ta) return;
    var words = ta.value.trim() ? ta.value.trim().split(/\s+/).length : 0;
    var bar = document.getElementById('dt-goal-bar');
    var text = document.getElementById('dt-goal-text');
    if (!bar || !text) return;
    if (goal > 0) {
        var pct = Math.min(100, (words / goal) * 100);
        bar.style.width = pct.toFixed(1) + '%';
        text.textContent = words + '/' + goal + ' palavras (' + pct.toFixed(0) + '%)';
        if (pct >= 100) { bar.style.background = '#00ff41'; text.style.color = '#0f0'; }
        else { bar.style.background = '#0f0'; text.style.color = '#555'; }
    } else {
        bar.style.width = '0%';
        text.textContent = words + ' palavras (sem meta)';
        text.style.color = '#444';
    }
}
function dtCheckSpell() {
    var ta = document.getElementById('dt-text'); if(!ta) return;
    var words = ta.value.split(/\s+/);
    var errors = [];
    for(var i=0;i<words.length;i++){
        var w = words[i].replace(/[^a-zA-ZáéíóúâêîôûàèìòùãõçñÁÉÍÓÚÂÊÎÔÛÀÈÌÒÙÃÕÇÑ]/g,'');
        if(w.length>2 && dtDict.indexOf(w.toLowerCase())===-1 && errors.indexOf(w)===-1) errors.push(w);
    }
    var errDiv = document.getElementById('dt-errors');
    if (!errDiv) { errDiv = document.createElement('div'); errDiv.id = 'dt-errors'; document.querySelector('.dt-wrap').appendChild(errDiv); }
    errDiv.style.cssText = 'margin-top:4px;padding:4px 8px;font-size:8px;font-family:\'Courier New\',monospace;border:1px solid #1a1a1a;background:#050505;';
    if(errors.length===0) { errDiv.style.color='#0f0'; errDiv.textContent = '$ spellcheck — Nenhum erro encontrado.'; }
    else { errDiv.style.color='#ff4444'; errDiv.innerHTML = '$ spellcheck — ' + errors.length + ' palavra(s) não encontrada(s): ' + errors.join(', '); }
    deckShowToast('🔍 Verificação: ' + errors.length + ' erro(s).', errors.length ? 'warning' : 'success');
}
function dtExport() {
    var ta = document.getElementById('dt-text'); if(!ta) return;
    if (!ta.value.trim()) { deckShowToast('⚠ Nada para exportar.', 'warning'); return; }
    var txt = ta.value;
    var typeBtn = document.querySelector('#tab-datilografia .dt-btn.active');
    var typeName = typeBtn ? typeBtn.textContent.trim().toLowerCase() : 'texto';
    var dateStr = new Date().toISOString().slice(0,10);
    var filename = 'datilografia_' + typeName + '_' + dateStr + '.txt';
    var blob = new Blob([txt], {type:'text/plain;charset=utf-8'});
    var url = URL.createObjectURL(blob);
    var a = document.createElement('a');
    a.href = url; a.download = filename;
    document.body.appendChild(a); a.click();
    document.body.removeChild(a);
    setTimeout(function(){ URL.revokeObjectURL(url); }, 1000);
    deckShowToast('⬇ Exportado: ' + filename, 'success');
}
function dtClear() {
    if (!confirm('✖ Limpar editor? Todo o texto será perdido.')) return;
    var ta = document.getElementById('dt-text'); if(!ta) return;
    ta.value = '';
    dtCount();
    dtAutoSave();
    var errDiv = document.getElementById('dt-errors');
    if (errDiv) { errDiv.style.display = 'none'; errDiv.textContent = ''; }
    deckShowToast('✖ Editor limpo.', 'info');
}
