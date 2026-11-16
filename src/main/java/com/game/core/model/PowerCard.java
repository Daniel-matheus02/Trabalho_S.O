package com.game.core.model;

/**
 * Uma carta de poder recebida na rodada. O {@code id} aponta para o catálogo de
 * {@link com.game.core.scoring.Jokers} ou de {@link com.game.core.sabotage.Sabotages}.
 */
public record PowerCard(PowerKind kind, String id) {
}
