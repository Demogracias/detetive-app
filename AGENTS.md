# AGENTS.md — Contexto para Sessões AI

## Projeto: Agência Noir (Detetive App)

App Android completo (~14700 linhas HTML/CSS/JS em arquivo único) para investigação particular, com sincronização na nuvem, modais profissionais, e build Capacitor.

## Stack
- **Frontend**: HTML + CSS + JS puro em `app.html` (single-file, ~14747 linhas)
- **Android**: Capacitor 5.7.0, Gradle 8.13, JDK 17, minSdk 26, targetSdk 36
- **Backend**: Supabase (PostgreSQL + Auth + REST)
- **Auth**: Email/senha + Google OAuth via Supabase Auth
- **Build**: Gradle 8.13 (downgraded de 8.14.3 por erro de extração JNI nativo)
- **Git**: branch `master`, remote `origin` → `github.com/Demogracias/detetive-app`

## Credenciais (fora do git)
- `supabase-config.js` — `window.__SUPABASE_URL` + `window.__SUPABASE_ANON_KEY` (gitignorado)
- `android/keystore.properties` — senhas do keystore (gitignorado)
- `android/noir-release.jks` — keystore PKCS12 (gitignorado)

## Comandos de Build
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
cd android
./gradlew.bat assembleRelease   # APK release (~213 MB)
./gradlew.bat assembleMarket    # APK market com sufixo -market
```

## APKs gerados
- `android/app/build/outputs/apk/release/app-release.apk`
- `android/app/build/outputs/apk/market/app-market.apk`

## Estrutura
- `app.html` — app completo (HTML + CSS + JS embutido)
- `supabase-config.js` — credenciais Supabase (NÃO COMMITAR)
- `android/` — projeto Android (Capacitor)
- `android/noir-release.jks` — keystore (NÃO COMMITAR)
- `android/keystore.properties` — senhas keystore (NÃO COMMITAR)
- `android/app/src/main/AndroidManifest.xml` — 11 permissões mínimas
- `android/app/src/main/java/com/detetive/app/plugins/` — 4 plugins nativos (NoirPlugin, SchedulingPlugin, AlarmReceiver, BootReceiver)
- `play_store_listing.md` — descrição para Play Store
- `AI_CONTEXT.md` — contexto completo de sessões anteriores

## Supabase
- Project ID: `janzxabmoetvfrtwkxdy`
- URL: `https://janzxabmoetvfrtwkxdy.supabase.co`
- Anon key em `supabase-config.js`
- Service role: `sb_secret_Har2VNBqXvB-8teXCP12Xw_PTYHwjZ9` (NÃO usar no frontend)
- DB password: `X7pNzGjYR6yZUR5J`
- Schema executado: `supabase_schema.sql` (tabelas `user_data`, `purchases` + RLS)

## Circuit Breaker Patterns Implementados
- **Cloud Sync**: `_cloudSyncFailCount` + `_cloudSyncCooldown` — backoff exponencial após 3 falhas consecutivas (cooldown até 10 min)
- **Novel Fetch Queue**: retry automático (2 tentativas por capítulo) antes de abortar
- **Scorched Earth**: rollback completo se qualquer cifragem falhar (backup localStorage antes da operação)

## GitHub
- Token: `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` (usuário Demogracias)
- Repo: `https://github.com/Demogracias/detetive-app`
- CI/CD: GitHub Actions configurado em `.github/workflows/build.yml`

## Convenções de Código
- Sem comentários em código (a menos que explicitamente solicitado)
- Minify + obfuscação ativados no build release (`minifyEnabled true`)
- `window.alert/confirm/prompt` sobrescritos por modais não-bloqueantes (`deckShowAlert/deckShowConfirm/deckShowPrompt`)
- Backdoor TURING protegido por `window.__DEV__` (padrão `false`; `true` apenas em dev)
- Variáveis globais: `chatHistory`, `subjects`, `appointments`, `studyLogs`, `custodyLog` etc.