package com.game.client.network;

import com.game.core.rules.RoundOutcome;
import com.game.core.state.GameSnapshot;
import com.game.net.protocol.DamagePayload;
import com.game.net.protocol.GameOverPayload;
import com.game.net.protocol.PlayerStatusPayload;
import com.game.net.protocol.RoundResultPayload;
import com.game.net.protocol.SessionInfoPayload;
import com.game.net.protocol.TurnChangedPayload;

/**
 * Tudo o que é interessante e chega da rede, enfileirado para a thread de renderização.
 * A interface esvazia esta fila a cada quadro; nenhuma chamada GL acontece nas threads do Netty.
 */
public sealed interface ClientEvent {

    record Connected() implements ClientEvent {
    }

    record ConnectionLost(String reason) implements ClientEvent {
    }

    record Reconnecting(int attempt, int maxAttempts) implements ClientEvent {
    }

    record Reconnected() implements ClientEvent {
    }

    record ReconnectFailed(String reason) implements ClientEvent {
    }

    record SessionInfoReceived(SessionInfoPayload info, boolean created) implements ClientEvent {
    }

    record GameStarted(GameSnapshot snapshot) implements ClientEvent {
    }

    record StateUpdated(GameSnapshot snapshot) implements ClientEvent {
    }

    record TurnChanged(TurnChangedPayload payload) implements ClientEvent {
    }

    record RoundResolved(RoundResultPayload payload) implements ClientEvent {
    }

    record Damage(DamagePayload payload) implements ClientEvent {
    }

    record GameOver(GameOverPayload payload) implements ClientEvent {
    }

    record PlayerStatus(PlayerStatusPayload payload) implements ClientEvent {
    }

    record ErrorReceived(String code, String message) implements ClientEvent {
    }

    record RematchWaiting() implements ClientEvent {
    }
}
