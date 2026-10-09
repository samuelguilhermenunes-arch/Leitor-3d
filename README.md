# ModScout

App Android que observa os arquivos de um jogo (para criar mods) e verifica se um mod está completo.

## Como gerar o APK (instalável)

### Opção A: Android Studio (PC)
1. Abra esta pasta no Android Studio e espere o Gradle sincronizar.
2. Menu Build > Build Bundle(s) / APK(s) > Build APK(s).
3. O arquivo fica em `app/build/outputs/apk/debug/app-debug.apk`.

### Opção B: GitHub (sem instalar nada)
1. Crie uma conta e um repositório novo em github.com.
2. Envie todo o conteúdo desta pasta (incluindo a pasta oculta `.github`).
3. Aba **Actions** > **Build APK** > **Run workflow**.
4. Quando terminar, baixe `ModScout-APK` em Artifacts e extraia o `app-debug.apk`.

## Instalar no celular
Copie o `app-debug.apk`, abra pelo gerenciador de arquivos e permita "instalar de fontes desconhecidas" quando o Android pedir.

## Usar com o MT Manager
Na aba Observar, toque em "Escolher pasta (MT Manager)", selecione MT Manager no menu do seletor e entre em Android > data > pasta do jogo.
