# Agência Noir — Detetive App

Aplicativo de investigação criminal com detetive IA, sistema de fichas (flashcards), ENEM, paciência, rastreio de hábitos, e muito mais.

## Stack

- **Frontend:** HTML/CSS/JS vanilla (single-page app em `app.html`)
- **Mobile:** Apache Capacitor (Android)
- **Auth & Cloud:** Supabase (Auth + PostgreSQL)
- **CRash Reporting:** Sentry
- **CI/CD:** GitHub Actions
- **Wiki offline:** 102 artigos Wikipédia PT (`enem-knowledge.js`)

## Estrutura

```
├── app.html                     # App principal (SPA ~14.5k linhas)
├── www/                         # Cópia web (mesmo conteúdo)
│   └── index.html
├── enem/                        # Knowledge base offline
│   └── enem-knowledge.js
├── android/                     # Projeto Android (Capacitor)
│   └── app/
│       ├── build.gradle
│       └── src/main/java/
├── AGENTS.md                    # Contexto completo para sessões AI
├── CONTRIBUTING.md              # Guia de contribuição
├── privacy-policy.html          # Política de privacidade (LGPD)
├── supabase-config.js           # Credenciais Supabase (gitignorado)
└── .github/workflows/build.yml  # CI/CD automático
```

## Branches

| Branch | Uso |
|--------|-----|
| `main` | Produção (código estável) |
| `develop` | Integração de funcionalidades |
| `feature/*` | Novas funcionalidades |
| `fix/*` | Correções de bugs |

## Build

```bash
cd android
gradlew assembleMarket        # APK market (assinado)
gradlew bundleMarket          # AAB para Play Store
```

## Links

- **Repositório:** [github.com/Demogracias/detetive-app](https://github.com/Demogracias/detetive-app)
- **Privacy Policy:** [demogracias.github.io/detetive-app/privacy-policy.html](https://demogracias.github.io/detetive-app/privacy-policy.html)
- **Sentry:** [sentry.io](https://sentry.io)
- **Supabase:** [supabase.com](https://supabase.com)
