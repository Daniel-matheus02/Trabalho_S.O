package com.game.core.rules;

import com.game.core.model.Card;
import com.game.core.model.PowerKind;

import java.util.List;

/**
 * Um poder aplicado durante a resolução, na ordem em que foi resolvido.
 *
 * <p>Para jokers o alvo é o próprio jogador e as listas de cartas são {@code null}; para
 * sabotagens o alvo é o oponente. As listas só são preenchidas quando a sabotagem reescreveu as
 * cartas da mesa ({@code cardsBefore} e {@code cardsAfter}); caso contrário são {@code null}.</p>
 */
public record PowerEvent(
        int player,
        PowerKind kind,
        String id,
        int targetPlayer,
        List<Card> cardsBefore,
        List<Card> cardsAfter
) {
    public PowerEvent {
        cardsBefore = cardsBefore == null ? null : List.copyOf(cardsBefore);
        cardsAfter = cardsAfter == null ? null : List.copyOf(cardsAfter);
    }
}
