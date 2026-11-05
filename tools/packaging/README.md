# Empacotamento: executáveis autônomos

`build_packages.py` gera dois executáveis em arquivo único que instalam e executam o jogo
com **nenhum Java, Gradle ou conexão com a internet** na máquina do jogador. Cada um
embute um runtime Temurin Java 21 e os fat jars do cliente e do servidor.

| Arquivo | Plataforma | O que faz |
|---|---|---|
| `dist/DueloDeFichas.exe` | Windows 10/11 x64 | Descompacta uma vez em `%LOCALAPPDATA%\DueloDeFichas`, cria um atalho no Menu Iniciar e abre o jogo |
| `dist/DueloDeFichas-linux.run` | Linux x86-64 | Descompacta uma vez em `${XDG_DATA_HOME:-~/.local/share}/duelo-de-fichas`, registra uma entrada na área de trabalho e abre o jogo |

Os dois aceitam `--server [--port=7777]` (servidor dedicado de LAN) e `--install-only`
(instalar sem abrir o jogo).

## Gerando os executáveis

```bash
python tools/packaging/build_packages.py
```

O script:

1. baixa o Temurin JDK 21 e o JRE 21 (armazenados em `~/.cache/duelo-de-fichas-build`,
   alterável com `DUELO_BUILD_CACHE` ou `--cache-dir`);
2. gera `clientJar` e `serverJar` com o wrapper do Gradle do projeto;
3. desenha o ícone do aplicativo (`icon.png` / `icon.ico`) em Python puro;
4. monta o zip do payload do Windows e o tar.gz do payload do Linux (o arquivo do Linux
   preserva as permissões de execução do JRE);
5. compila `windows/Launcher.cs` com o `csc.exe` que acompanha o .NET Framework e embute
   o payload como recurso;
6. grava o inicializador autoextraível do Linux (cabeçalho `linux/launcher.sh.in` +
   payload anexado) e os arquivos `dist/SHA256SUMS.txt` e `dist/README.txt`.

Opções úteis: `--skip-build` (reutiliza `build/libs`), `--only windows|linux`,
`--work-dir DIR` (mantém os arquivos intermediários).

O executável do Windows precisa ser gerado no Windows; o do Linux pode ser montado em
qualquer sistema operacional.

## Como funciona a primeira execução

- **Windows** — `Launcher.cs` extrai o `payload.zip` embutido para uma pasta de preparação
  ao lado da instalação final e depois a troca de forma atômica. Uma janela de progresso é
  exibida. Um marcador `payload.version` evita extrações repetidas; uma nova compilação
  (hash diferente do jogo/jars/runtime) dispara uma atualização limpa.
- **Linux** — o arquivo `.run` é um script POSIX seguido por um payload tar.gz anexado
  após o byte `PAYLOAD_OFFSET`. Ele extrai para uma pasta de preparação, troca a
  instalação, grava `app/launch.sh` e registra
  `~/.local/share/applications/duelo-de-fichas.desktop`.

Os dois instaladores são por usuário: não exigem administrador/root e não modificam nada
fora do perfil do usuário.

## Observações

- O jogo precisa de OpenGL 2.0+; o JRE embutido não inclui as bibliotecas gráficas do
  sistema (X11/Wayland no Linux), que toda área de trabalho já possui.
- `dist/` é saída gerada e é ignorada pelo git.
