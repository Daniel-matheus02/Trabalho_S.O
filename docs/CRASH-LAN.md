# Queda em multiplayer LAN — reprodução, captura e suspeitos

Documento de trabalho da adição "o jogo CRASHA em LAN" sobre a Stage 8. Tudo abaixo foi medido
nesta árvore (`src/main/java`), não inferido.

## 1. Reprodução

Harness real, sem atalhos: `GameServer` autoritativo em porta efêmera + dois `GameClient` reais
dirigindo `NetworkMatchSession` (nunca `LocalMatchSession`), `BotBrain` nos dois lados, fluxo de
poderes real (BUILD → SABOTAGE → REVEAL → próxima rodada / GAME_OVER).

```
./gradlew test --tests "com.game.networking.NetworkMatchStressTest"
```

Resultado desta árvore: **30/30 sessões completas, PASSED, nenhuma exceção registrada** no cliente
nem no servidor (`CapturingHandler` coleta todo `LogRecord` com `getThrown() != null` e o teste
falha se houver um). Uma sessão joga também a revanche; outra derruba o TCP de um jogador na
rodada 2 e reconecta com o token, continuando até o fim.

**Não consegui reproduzir a queda.** O código desta árvore já contém as proteções descritas em
(3) e nenhum caminho de exceção dispara. Não vou inventar uma pilha que não observei.

Para fechar a causa raiz da queda relatada eu preciso de um destes, do lado que caiu:

- o arquivo `logs/duelo-crash-<timestamp>.log` (é o que o item 2 passou a gravar: pilha, thread,
  fase, rodada, versão do instantâneo e as últimas 20 mensagens de protocolo), ou
- o `stderr`/console da execução que caiu, desde a primeira linha de exceção.

Sem isso, o que posso afirmar com prova é apenas o que está em (3): quais suspeitos eram reais
como forma de código e quais não têm furo.

## 2. Captura de falhas (feito)

- `com.game.diagnostics.CrashLog` grava `logs/duelo-crash-<timestamp>.log` com pilha, thread,
  fase/rodada/versão correntes e as últimas 20 mensagens de protocolo (tipo, bytes, id).
  Instalado como handler padrão de exceções não tratadas em
  `DesktopLauncher.java:13` (cliente) e `ServerLauncher.java:20` (servidor).
- `GameScreen.render()` envolve o quadro inteiro em `try/catch (RuntimeException)`
  (`GameScreen.java:401-406`): registra, mostra toast (`GameScreen.java:462-489`) e reconstrói a
  tela a partir do último instantâneo (`refresh(true)`, `presenter.recover(...)`) em vez de deixar
  o libGDX encerrar.
- Contexto e tráfego são alimentados em `CrashLog.context(...)` (`GameScreen.java:561`) e
  `CrashLog.protocol(...)` (`GameClient.java:166`, `ClientHandler.java:26`,
  `ServerHandler.java:32`, `GameSession.java:435`).

## 3. Suspeitos

### a) `GameClient.onEnvelope` só pegava `ProtocolException` — REAL, corrigido

Era real: qualquer `RuntimeException` fora de `ProtocolException` subia para
`ClientHandler.channelRead0`, caía em `exceptionCaught` (`ClientHandler.java:57-61`) →
`onConnectionLost` → fecha o canal → laço de reconexão (6 tentativas por minuto, uma sessão
recriada a cada 2 s).

Hoje: `GameClient.onEnvelope` tem `catch (ProtocolException)` **e** `catch (RuntimeException)`
(`GameClient.java:268-274`) registrando e seguindo com a conexão; `ClientHandler.channelRead0`
também protege a decodificação (`ClientHandler.java:30-38`) para que nada chegue a
`exceptionCaught` por causa de uma mensagem ruim.

### b) Serialização dos tipos novos — sem falha observada; transmissão isolada por jogador

Cobertura: `MessageCodecTest` (snapshot, `tableScore`, `PowerEvent` com `cardsBefore/After`,
`ScoreStep` via `ScoringEngine`) e, na prova de rede, `assertCodecRoundTrip` em **cada
instantâneo de cada visualizador** (`NetworkMatchStressTest.java:238-256`): `encode → decode →
equals` do `GameSnapshot` + `RoundResultPayload` + `outcome.powerEvents()` +
`playerXScore().steps()`. 30 sessões, zero divergência.

`multFactor` é `double` e sobrevive à ida e volta (`ScoreResult`/`ScoreStep` round-trip).
Listas nulas: eram o único ponto capaz de explodir de fato — `PlayerView` e `GameSnapshot`
faziam `List.copyOf(...)`, que lança `NullPointerException` em campo ausente. Agora listas nulas
viram listas vazias (`PlayerView.java:23-31`, `GameSnapshot.java:29-31`).

Radiodifusão: `GameSession.send` engole `RuntimeException` por jogador
(`GameSession.java:445-449`) e `broadcastState` envia visualizador por visualizador
(`GameSession.java:360-366`), então uma falha de serialização para um jogador não impede o outro
de receber o estado.

### c) Versão do instantâneo — sem furo encontrado

`GameEngine.version` só cresce, e toda mutação visível chama `touch()`:
`start` (`GameEngine.java:210`), `submitPlay` (`:253`, cobre mão jogada, mesa pontuada, mão de
poderes e troca de fase), `lockPowers` (`:294`, cobre `powersLocked`, poderes jogados e a
resolução), `advance` (`:311`, cobre `ready`, `GAME_OVER` e próxima rodada), `requestRematch`
(`:323`) e `redealPowersForPreview` (`:337`). `resetInternalState` não zera `version`, então a
sequência continua monotônica na revanche.

O cliente aplica `GAME_STATE_UPDATE` quando `version >= latest` (`GameClient.java:234`), então
versão repetida é idempotente e versão nova nunca é descartada.

### d) Apresentação que só funcionava com `LocalMatchSession` — risco real de ordem, coberto

`ROUND_RESULT`, `PLAYER_DAMAGE`, `TURN_CHANGED` e `GAME_STATE_UPDATE` chegam como mensagens
separadas e em qualquer ordem. A apresentação foi amarrada para tolerar isso:

- `GameScreen.handleEvents` trata `RoundResolved` sem depender do instantâneo
  (`GameScreen.java:513-524`);
- `RoundPresenter.onSnapshot` só reage a versão nova e é idempotente por rodada
  (`RoundPresenter.java:99-120`), e guarda a queima uma vez por rodada
  (`RoundPresenter.java:423-425`);
- a vida exibida (`displayedHp`) é mantida separada da real
  (`RoundPresenter.java:314`) e o dano é aplicado pela animação;
- campos nulos de instantâneo filtrado não explodem mais (item b).

Duplicatas e versões puladas: `refresh` retorna cedo quando `state.version() == lastVersion`
(`GameScreen.java`), e a apresentação é reconstruída a partir do último instantâneo em qualquer
recuperação.

### e) Servidor: validar antes de mutar e pegar `RuntimeException` — feito

`GameSession.handleAction` (`GameSession.java:206-240`) captura `GameRuleException`,
`IllegalArgumentException` e `RuntimeException` (este último responde `SERVER_ERROR` e mantém a
sessão). O engine valida antes de mutar: `submitPlay` checa fase, turno, tamanho, nulidade,
duplicatas e posse das cartas antes de tocar em `pendingCards`/`hands`
(`GameEngine.java:214-253`); `lockPowers` valida fase, trava repetida, índices fora do intervalo
e o limite `maxPowersPlayed` antes de escrever `playedPowers`/`powersLocked`
(`GameEngine.java:272-295`). `ServerHandler` também protege a decodificação
(`ServerHandler.java:40`).

## 4. Prova

`NetworkMatchStressTest`: 30 sessões semeadas, revanche, queda + reconexão no meio da rodada,
consistência cliente ↔ servidor a cada mensagem, round-trip JSON de todo instantâneo dos dois
visualizadores, e falha do teste se qualquer exceção aparecer nos logs de cliente ou servidor.

```
./gradlew test   # verde
```
