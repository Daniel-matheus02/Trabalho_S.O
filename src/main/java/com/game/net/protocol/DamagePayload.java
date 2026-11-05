package com.game.net.protocol;

/** Evento de dano para animações de acerto. */
public record DamagePayload(int targetPlayer, int damage, int hpRemaining) {
}
