# Guia de Contribuição

## Fluxo de Trabalho (Git Flow Leve)

```
main ──── merge ──────────────────►
  │                ▲
  │                │ PR
  └──► develop ────┘
         │            ▲
         │  feature/* │ PR
         └────────────┘
         │            ▲
         │  fix/*     │ PR
         └────────────┘
```

### Branches

| Branch | Origem | Destino do PR | Descrição |
|--------|--------|---------------|-----------|
| `main` | — | — | Produção. Apenas merge via PR de `develop` |
| `develop` | `main` | `main` | Integração. Base para features/fixes |
| `feature/<nome>` | `develop` | `develop` | Nova funcionalidade |
| `fix/<nome>` | `develop` | `develop` | Correção de bug |

### Commits Atômicos

Cada commit deve conter **uma única mudança lógica**. Critérios:

- **Pequeno:** não mistura formatação com lógica
- **Granular:** se der `git revert <hash>`, o commit desfaz uma coisa só
- **Mensagem descritiva:** `tipo(escopo): resumo` (ex: `feat(auth): add Google OAuth login`)

### Prefixos de Commit

```
feat:     Nova funcionalidade
fix:      Correção de bug
refactor: Refatoração (sem mudar comportamento)
docs:     Documentação
style:    Formatação, espaços, lint (sem mudar lógica)
chore:    Build, CI, dependências
test:     Testes
perf:     Performance
```

### Pull Requests

- Título descritivo seguindo `tipo(escopo): resumo`
- Descrição explica **o que** e **por que**
- Mantenha PRs pequenos (máximo ~300 linhas)
- Um PR = uma funcionalidade/correção

### Antes de Abrir PR

1. `git fetch && git checkout develop && git pull`
2. `git checkout -b feature/meu-feature`
3. Faça commits atômicos
4. Rode lint/testes se disponível
5. Push e abra PR para `develop`

### Code Review

- Revise mudanças com `git diff`
- Verifique se não há segredos (tokens, senhas) no código
- Teste localmente antes de aprovar
