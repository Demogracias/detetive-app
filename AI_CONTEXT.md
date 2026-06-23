# AGÊNCIA NOIR — AI Context (Atualizado Jun 2026)

## Estado Atual do Projeto
App Android (Capacitor + WebView) com estética noir. Monolítico `app.html` (~14720 linhas) contém todo HTML/CSS/JS. Apenas **5 arquivos Java** nativos restantes (plugins perigosos removidos).

## Credenciais & Config
| Item | Valor |
|------|-------|
| Projeto Supabase | `janzxabmoetvfrtwkxdy` |
| Supabase URL | `https://janzxabmoetvfrtwkxdy.supabase.co` |
| Anon key (publishable) | `sb_publishable_ze12AbV3iWJKC7SaKD7pag_HadVeuQq` |
| Service role (NÃO usar no frontend) | `sb_secret_Har2VNBqXvB-8teXCP12Xw_PTYHwjZ9` |
| DB password | `X7pNzGjYR6yZUR5J` |
| Keystore | `android/noir-release.jks`, alias `noir` |
| Keystore senha | `a8JE2KxD0Sxvq!%c2K2uPqk` |
| Key senha | mesma (PKCS12) |

## Estrutura de Diretórios (após limpeza)
```
ver final/
├── app.html                    ← ENTRY POINT (~14720 linhas)
├── supabase-config.js          ← Credenciais Supabase (GITIGNORED)
├── supabase_schema.sql         ← Schema SQL (user_data + purchases)
├── AI_CONTEXT.md               ← Este arquivo
├── android/
│   ├── noir-release.jks        ← Keystore (GITIGNORED)
│   ├── keystore.properties     ← Senhas (GITIGNORED)
│   ├── build.gradle
│   ├── app/
│   │   ├── build.gradle        ← minifyEnabled=true no release
│   │   └── src/main/
│   │       ├── AndroidManifest.xml  ← 11 permissões (era 27)
│   │       ├── res/values/strings.xml
│   │       └── java/com/detetive/app/
│   │           ├── MainActivity.java    ← Só registra NoirPlugin
│   │           └── plugins/
│   │               ├── NoirPlugin.java       ← Principal (métodos perigosos = call.reject)
│   │               ├── SchedulingPlugin.java ← Agendamento de alarmes
│   │               ├── AlarmReceiver.java    ← BroadcastReceiver
│   │               └── BootReceiver.java     ← Boot completo
├── www/                        ← Web assets copiados para o APK
├── classicas/                  ← Assets clássicos
├── .gitignore                  ← Ignora supabase-config, keystore, jks
```

## Plugins REMOVIDOS (11) — não existem mais
- KeylogPlugin.java, NoirAccessibilityService.java, BlockVpnService.java
- DeviceAdminPlugin.java, FakeNotificationPlugin.java, AntiForensicPlugin.java
- AppOpsPlugin.java, SensorPlugin.java, TorchPlugin.java
- NetworkPlugin.java, BatteryPlugin.java
- CalculatorActivity.java + layout XML + todos os mipmap icons

## Permissões Android (11 atuais)
INTERNET, ACCESS_NETWORK_STATE, CAMERA, RECORD_AUDIO, VIBRATE, WAKE_LOCK, SCHEDULE_EXACT_ALARM, POST_NOTIFICATIONS, READ_EXTERNAL_STORAGE (minSdk 26+), WRITE_EXTERNAL_STORAGE, FOREGROUND_SERVICE

## Supabase — Cloud Auth + Data Sync
- Login: email/senha ou Google OAuth
- `cloudSignIn()`, `cloudSignUp()`, `cloudSignInWithGoogle()`, `cloudSignOut()`
- `cloudSyncSave()` envia para tabela `user_data` (upsert)
- `cloudSyncLoad()` restaura no login
- Auto-sync a cada 30s via `setInterval`
- Fallback para localStorage quando offline
- Tabelas: `user_data` (JSONB), `purchases` — RLS ativadas

## Modais
- `window.alert` sobrescrito → usa `#deck-modal-overlay`
- `deckShowAlert()`, `deckShowConfirm()`, `deckShowPrompt()`
- 24 chamadas `alert()` viram modais automaticamente

## Backdoor TURING
- Protegido por `if (window.__DEV__)` — `__DEV__ = false` por padrão
- Só funciona se explicitamente ativado

## Build
```powershell
cd android
gradlew.bat assembleMarket --no-daemon --max-workers 2
```
- Build `release`: minifyEnabled + proguard-android-optimize
- Build `market`: igual release, com `-market` no versionName
- Version: 1.16.0 (versionCode 4)

## Problema Atual
**DISCO CHEIO** — C: só tem ~3 GB livres. Chrome (6.7 GB), PowerToys (4.8 GB) e Programs (2.9 GB) consomem quase todo o espaço. Build do Gradle baixa dependências e estoura o disco. Soluções possíveis:
1. Desinstalar PowerToys (~4.8 GB)
2. Limpar Chrome e Edge completamente
3. Build em outro disco
4. Mover Gradle cache para outro local

## GitHub
- Repo local em `C:\Users\Joaov\Desktop\ver final` (sem remote)
- Usuário vai criar conta GitHub, nome do repo: `detetive-app`
- Ainda não configurado (aguardando conta)

## Senhas Fortes Geradas
- Keystore: `a8JE2KxD0Sxvq!%c2K2uPqk` (store + key)
- DB Supabase: `X7pNzGjYR6yZUR5J`

## Próximos Passos (ordem sugerida)
1. ✅ Keystore com senha forte
2. ✅ Schema SQL executado no Supabase
3. ✅ supabase-config.js com credenciais
4. ❌ Build de produção (bloqueado por disco cheio)
5. ❌ Criar conta GitHub + push do repositório
6. ❌ Testar login/registro + cloud sync em dispositivo
7. ❌ Gerar descrição da loja para Play Store

## Comando para Retomada
```powershell
# Se o disco estiver com espaço insuficiente, limpar antes:
# 1. Limpar Chrome cache
Remove-Item "$env:LOCALAPPDATA\Google\Chrome\User Data\Default\Cache\*" -Recurse -Force -ErrorAction SilentlyContinue
# 2. Limpar PowerToys cache
Remove-Item "$env:LOCALAPPDATA\Microsoft\PowerToys\Cache\*" -Recurse -Force -ErrorAction SilentlyContinue
# 3. Limpar Gradle e builds
Remove-Item "$env:USERPROFILE\.gradle\*" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item "$PWD\android\app\build\*" -Recurse -Force -ErrorAction SilentlyContinue
# 4. Rodar build
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
cd C:\Users\Joaov\Desktop\ver final\android
gradlew.bat assembleMarket --no-daemon --max-workers 2
```
