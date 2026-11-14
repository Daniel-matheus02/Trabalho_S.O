# Duelo de Fichas — Patch Notes

Implementação de referência: `src/main/java/com/game` (motor, rede e cliente desktop).

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
