Duelo de Fichas 1.0.0 - compilação autônoma
========================================

Os dois arquivos são instaladores completos: eles embutem o Java 21 e o jogo,
então nada precisa ser instalado antes e não é necessária conexão com a internet.

Windows
-------
  Clique duas vezes em DueloDeFichas.exe.
  A primeira execução descompacta o jogo em %LOCALAPPDATA%\DueloDeFichas
  e cria um atalho no Menu Iniciar. As execuções seguintes são imediatas.

  Servidor dedicado de LAN (opcional):
    DueloDeFichas.exe --server --port=7777

Linux (x86-64)
--------------
  chmod +x DueloDeFichas-linux.run
  ./DueloDeFichas-linux.run

  A primeira execução descompacta o jogo em ~/.local/share/duelo-de-fichas/app
  e registra uma entrada na área de trabalho. As execuções seguintes são imediatas.

  Servidor dedicado de LAN (opcional):
    ./DueloDeFichas-linux.run --server --port=7777

O jogo precisa de OpenGL 2.0+ (qualquer área de trabalho normal com aceleração de GPU).
O multiplayer em LAN embutido no jogo funciona sem esse servidor dedicado.
