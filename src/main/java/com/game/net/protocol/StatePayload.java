package com.game.net.protocol;

import com.game.core.state.GameSnapshot;

/** Snapshot autoritativo completo, filtrado para o jogador destinatário. */
public record StatePayload(GameSnapshot snapshot) {
}
