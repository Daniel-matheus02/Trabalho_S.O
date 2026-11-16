# Duelo de Fichas — Patch Notes

Implementação de referência: `src/main/java/com/game` (motor, rede e cliente desktop).

## v0.13 — Limpeza, Documentação e Verificação

- **[Limpeza]** `bin/` deixou de ser versionado e entrou no `.gitignore` junto de `reasonix/`;
  a pasta vazia `client/ui` foi removida.
- **[Limpeza]** Nomenclatura de "draft" eliminada: `STYLE_DRAFT` virou `STYLE_SYSTEM`/`log-system`,
  o alias `--screen=draft` foi removido, a doc de `GameScreen` foi corrigida e a imagem
  `docs/draft.png` foi apagada.
- **[Limpeza]** Código sem chamadores removido: `JokerRow`, `CardHand.setDisabled`,
  `HandCardActor.setDisabled`, `CardActor.colorFor` e imports órfãos. O javadoc obsoleto de
  "uso único" em `Sabotages` foi corrigido.
- **[Testes]** O caso de 7 cartas do `ScoringEngineTest` foi reescrito para a regra atual de 5
  cartas jogadas. A varredura de 200 partidas CPU×CPU agora também verifica o limite de
  `maxPowersPlayed` e o invariante dos `ScoreStep` em toda mão pontuada.
- **[Docs]** README sincronizado com a regra de até 2 de 6 poderes, controles de skip,
  hotkeys F5–F10, probes e arquitetura.
- **[QA]** Verificação final: `./gradlew clean test`, build do JAR, `--probe=layout`,
  `--probe=screenshots` e `--probe=frames`.

## v0.12 — Apresentação da Resolução

- **[Novo]** `ResolutionEffect`: voos dos poderes jogados da mão para os slots na ordem do
  `RoundOutcome`, escala 1,0 → 1,2 → 1,0, tooltip por ~1 s, popup de efeito e fade das cartas não
  jogadas. Um lado sem poderes mantém o rótulo "SEM PODER".
- **[Novo]** Flip scaleX 1 → 0 → 1 nas cartas de mesa alteradas por `virada_naipes`/`troca_carta`,
  usando `cardsBefore`/`cardsAfter` do `PowerEvent`.
- **[Mudança]** `PowerEvent` agora é emitido em ordem crescente de índice da mão dentro de cada
  jogador, mantendo a ordem alternada do resolvedor.
- **[Mudança]** `RoundPresenter` ganhou a etapa de resolução entre a confirmação e a contagem
  final, com skip, conclusão por novo instantâneo e replay (F10).
- **[QA]** Filmstrip `resolucao-t0..4000.png` com voos, tooltips, viras e slots finais.
- **[Testes]** Ordem dos eventos, cartas antes/depois no codec e sequência da resolução.

## v0.11 — Queima da Mão

- **[Novo]** `CardSnapshot`: cada carta remanescente é renderizada uma vez em um FrameBuffer
  RGBA8888 transparente e guardada como `TextureRegion` com Y invertido.
- **[Novo]** `DissolveShader`: ruído procedural por pixel, `u_progress`/`u_edgeWidth`, borda
  incandescente (amarelo → laranja → vermelho → cinza), compilação única e fallback de fade alfa
  se o shader falhar.
- **[Novo]** `HandBurnEffect`: spin de 360° (0,7 s, 0,05 s de stagger), queima de 0,9 s por carta
  com 0,08 s de stagger, brasas/fumaça, tremor curto e distribuição dos 6 poderes com 0,1 s de
  stagger. A mão fechada do oponente gira e queima junto e vira 6 versos.
- **[Mudança]** A troca cross-fade da Stage 2 foi substituída pela queima; as cartas jogadas já
  estão na mesa, então só as 3 remanescentes são capturadas.
- **[Mudança]** `RoundPresenter` sequencia contagem → queima → mão de poderes, com guarda de
  uma queima por rodada (mesmo após redraw/reconexão) e skip por clique/ESPAÇO.
- **[QA]** Filmstrip `queima-t0/300/700/1100/1600/2200/2800.png` executando o shader real no
  contexto GL do probe; F9 repete a queima.
- **[Testes]** Guarda de uma vez por rodada, skip, novo instantâneo no meio e movimento reduzido.

## v0.10 — Tooltips das Cartas de Poder

- **[Novo]** `CardTooltip`: um único overlay no palco (nunca um por carta) com nome e descrição
  vindos exclusivamente de `Jokers.description()`/`Sabotages.description()`, painel acima da
  carta (ou abaixo quando não couber), triângulo-ponteiro, clamp às bordas do palco e pop-in de
  0,85 → 1,0 em 0,12 s com `swingOut`.
- **[Novo]** `TooltipText` (puro): coloriza `+N fichas` em azul, `mult`/`xN` em vermelho e os
  naipes ♠♥♦♣ em suas cores; `TooltipPlacement` (puro): decide acima/abaixo, faz o clamp
  horizontal e posiciona o ponteiro.
- **[Novo]** Hover reutiliza o estado existente de `HandCardActor`; `PowerCardActor` ganhou
  escala 1,06 e inclinação de até 3° na direção do cursor. O hover/tooltip vale para a mão, os
  dois slots de poder de cada lado e os poderes jogados do oponente na revelação.
- **[QA]** Sondas `tooltip-joker`, `tooltip-sabotagem`, `tooltip-revelacao` e o filmstrip
  `tooltip-popin-t0/30/60/90/120.png`, com cursor injetado deterministicamente; F8 no DevPreview
  mostra o tooltip na primeira carta.
- **[Testes]** Colorização por spans preservando o texto e matemática de posicionamento
  (acima/abaixo, clamp e ponteiro).

## v0.9 — Contagem Animada

- **[Novo]** `ScoreStep`/`StepKind` decompõem cada pontuação em HAND_BASE, um passo por carta,
  GLOBAL, JOKER e SABOTAGE, sempre com os totais acumulados. O `ScoreResult` carrega `steps`
  (imutáveis) junto com `lines`; o último passo coincide com o placar final.
- **[Novo]** `ScoreCountTimeline` (Java puro) converte os passos em beats de 0,32 s por carta,
  0,45 s por efeito e uma pausa de 0,25 s antes do beat final. `ScoreFeel` centraliza tempos e as
  três faixas de tremor, calibradas com partidas CPU-vs-CPU.
- **[Novo]** `ScoreCountAnimator` anima os dois lados em paralelo sobre o `SharedTablePanel`:
  cartas pulam com wobble, popups sobem, fichas rolam, o placar final conta em 0,6 s, partículas
  voam para o SCORE e a tela treme. Jokers/sabotagens são localizados pelo `sourceId` nos slots.
- **[Novo]** O placar de cada lado agora é uma caixa azul de FICHAS × caixa vermelha de MULT =
  SCORE dentro do `SharedTablePanel`; o `ScorePanel` fica apenas como prévia do BUILD.
- **[Novo]** `RoundPresenter` sequencia contagem da mesa → troca para a mão de poderes → entrada
  → contagem final → dano. A vida exibida segura o valor anterior até o fim da contagem final.
  Clique/ESPAÇO acelera, um instantâneo novo conclui a contagem na hora e movimento reduzido a
  torna instantânea.
- **[QA]** Sondas `contagem` e `contagem-final` (filmstrips `contagem-t*.png` e
  `contagem-final-t*.png`) e F7 no DevPreview para repetir a contagem.
- **[Testes]** 500 mãos aleatórias verificam que os passos nunca divergem do placar; os passos
  fazem ida-e-volta pelo Jackson; timeline e `RoundPresenter` têm testes dedicados.

## v0.8 — Poderes Misturados

- **[Grande mudança]** Depois que as duas mãos são enviadas, cada jogador recebe **uma única
  lista de 6 poderes** (3 jokers + 3 sabotagens) embaralhada pelo RNG do motor. A ordem enviada é
  a ordem usada pela interface e pelos índices; os tipos nunca ficam agrupados.
- **[Mudança]** Cada jogador joga **até 2 cartas** da mão, em qualquer combinação (2 jokers,
  2 sabotagens ou 1 de cada; 0 ou 1 também é válido). As cartas jogadas aplicam-se na ordem
  crescente do índice da mão. `maxJokersPlayed` e `maxSabotagesPlayed` foram removidos.
- **[Novo]** `PowerCard(kind, id)`, `PowerEvent` no `RoundOutcome` e `powerHand`/`powerCount`/
  `playedPowers` no `PlayerView`. A ação de protocolo `LOCK_POWERS` carrega `powerIndices`.
- **[Novo]** `PowerCardActor` (PNG pixel-art em Nearest com letra-caixa ou carta vermelha
  procedural) e `PowerHand` (seleção limitada a 2 com tremor na terceira carta). O `PowerPanel`
  e seus caminhos foram removidos; o botão **CONFIRMAR PODERES (n/2)** fica na barra de ações.
- **[Novo]** Dois espaços de poder por lado na mesa de revelação, com cartas viradas para cima,
  espaços não usados apagados e **SEM PODER** quando o lado não jogou nada.
- **[Catálogo]** Jokers reduzidos aos 9 definitivos; sabotagens reduzidas às 6 definitivas
  (Bloqueio de Joker removido, pois equivalia a Névoa). `quintuplus` entra no lugar de
  `corte_real`.
- **[IA]** `BotBrain.choosePowers` enumera os subconjuntos de tamanho 0..2, simula cada um contra
  a mesa visível e maximiza a diferença de pontuação, com média de 8 amostras para a sabotagem
  aleatória.
- **[QA]** Sondas `poderes-misturados`, `poderes-selecao-2` e `revelacao-2-poderes`; F6 no
  DevPreview redistribui a mão de poderes.

## v0.7 — Mão de 5 e Poderes por Rodada

- **[Grande mudança]** Cada jogador recebe 8 cartas e envia exatamente 5. As 5 cartas jogadas
  formam a mão da rodada; não há mais cartas comunitárias (flop, turn e river).
- **[Grande mudança]** A economia e a loja foram removidas: não há moedas, compras, rerolls
  pagos nem bônus de azarão. Jokers e sabotagens são sorteados para cada rodada.
- **[Novo]** Fase de poderes: depois que os dois enviam as 5 cartas, cada jogador recebe 3
  jokers e 3 sabotagens da rodada, escolhe até 3 de cada e trava a escolha. O que não for
  jogado é descartado ao fim da rodada.
- **[Novo]** A tela de poderes mostra as mãos recebidas, o limite de jokers/sabotagens e a
  ordem de resolução da rodada.
- **[Mudança]** O modificador global continua valendo para os dois jogadores durante toda a
  partida.
- **[Mudança]** A ordem alternada de resolução das sabotagens continua valendo: R1 o Jogador 1
  resolve primeiro, R2 o Jogador 2, R3 o Jogador 1, e assim por diante.
- **[Removido]** Cartas comunitárias, moedas, loja, rerolls, bônus de azarão, o limite de 2
  sabotagens em mãos e os painéis `ShopPanel`/`SabotagePanel`.

## v0.6 — Escolha de Mão, Loja Dinâmica e Azarão

- **[Novo]** Loja de jokers com reroll pago: é possível re-sortear as 3 ofertas de joker
  gastando moedas. O preço sobe a cada atualização na mesma visita (5 → 10 → 15…). Os dois
  rerolls grátis por partida da v0.2 continuam existindo e são consumidos antes do pago.
- **[Novo]** Bônus de azarão: quem perde 2 rodadas seguidas ganha +10 moedas e continua
  ganhando enquanto a sequência de derrotas durar. Vencer zera a sequência.

### Padrões de economia adotados

As notas da versão não fixavam todos os números; a implementação usa estes valores
(centralizados em `GameRules.DEFAULT`):

| Item | Valor |
|---|---:|
| Moedas iniciais | 10 |
| Moedas por rodada (cada jogador) | 5 |
| Bônus do vencedor da rodada | 5 |
| Bônus de azarão | 10 |
| Preço de uma sabotagem | 8 |
| Rerolls grátis de joker por partida | 2 |
| Rerolls pagos na mesma visita | 5, 10, 15… |
| Sabotagens máximas em mãos | 2 |

## v0.5 — Ordem Alternada

- **[Mudança]** A ordem de resolução das sabotagens alterna por rodada: R1 o Jogador 1
  resolve primeiro, R2 o Jogador 2, R3 o Jogador 1, e assim por diante.
- **[Novo]** A tela de sabotagem mostra a ordem da rodada em destaque, e o log do protocolo
  registra `ORDEM|rodada|primeiro` antes de aplicar as cartas.

## v0.4 — Regras das Sabotagens

- **[Novo]** Limite de 2 sabotagens em mãos.
- **[Novo]** Uso único: comprou, usou, acabou. Para usar de novo, compra de novo.
- **[Novo]** Uma sabotagem por rodada. Depois do river, cada jogador usa uma (ou passa).
- **[Novo]** A loja oferece 3 sabotagens sorteadas, não o catálogo inteiro.
- **[Mudança]** Sabotagens resolvem antes da contagem de pontos. A mesa é congelada no river
  e então reescrita.
- **[Sabotagem]** Nova carta: Névoa — anula todos os jokers do oponente na rodada.

## v0.3 — A Grande Reformulação

- **[Grande mudança]** Cartas comunitárias estilo poker. Cada jogador escolhe 2 cartas; a
  mesa abre 5 cartas em flop → turn → river. A jogada é a melhor combinação de 5 entre as
  suas 2 + as 5 da mesa.
- **[Novo]** Economia de moedas: ganhas por rodada (com bônus para o vencedor), gastas na
  loja.
- **[Sabotagem]** Catálogo inicial: Virada de Naipes, Troca de River, Anti-Flush, Peso de
  Chumbo, Imposto do Par e Bloqueio de Joker.

## v0.2 — Jokers & Modificador Global

- **[Novo]** Modificador global sorteado no início da partida, valendo para os dois jogadores.
- **[Novo]** Draft de jokers: entre rodadas, os dois recebem as mesmas 3 opções e cada um
  escolhe 1 para si, acumulando o próprio estilo.
- **[Balanço]** Dois rerolls por jogador no draft.

## v0.1 — Protótipo Base

- **[Novo]** Primeira versão jogável: mão de 8 cartas, escolha de até 5 para a jogada,
  pontuação estilo Balatro (fichas × mult) e sistema de HP.
