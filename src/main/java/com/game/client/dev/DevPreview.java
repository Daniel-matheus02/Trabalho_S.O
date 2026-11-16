package com.game.client.dev;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;

/**
 * Atalhos de desenvolvimento, ativos apenas com {@code -Dduelo.dev=true}.
 *
 * <p>F5 repete na mesa a animação de entrada e de virada das cartas sobre o último instantâneo,
 * sem jogar uma partida inteira.</p>
 */
public final class DevPreview {

    private static final boolean ENABLED = Boolean.getBoolean("duelo.dev");

    private DevPreview() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    /** Verdadeiro no quadro em que F5 é pressionado (somente no modo de desenvolvimento). */
    public static boolean replayRequested() {
        return ENABLED && Gdx.input.isKeyJustPressed(Input.Keys.F5);
    }

    /** Verdadeiro no quadro em que F6 é pressionado: redistribui a mão de poderes. */
    public static boolean redealRequested() {
        return ENABLED && Gdx.input.isKeyJustPressed(Input.Keys.F6);
    }

    /** Verdadeiro no quadro em que F7 é pressionado: repete a contagem no último instantâneo. */
    public static boolean countReplayRequested() {
        return ENABLED && Gdx.input.isKeyJustPressed(Input.Keys.F7);
    }

    /** Verdadeiro no quadro em que F8 é pressionado: mostra o tooltip na primeira carta. */
    public static boolean tooltipRequested() {
        return ENABLED && Gdx.input.isKeyJustPressed(Input.Keys.F8);
    }

    /** Verdadeiro no quadro em que F9 é pressionado: repete a queima da mão. */
    public static boolean burnReplayRequested() {
        return ENABLED && Gdx.input.isKeyJustPressed(Input.Keys.F9);
    }

    /** Verdadeiro no quadro em que F10 é pressionado: repete a resolução inteira. */
    public static boolean resolutionReplayRequested() {
        return ENABLED && Gdx.input.isKeyJustPressed(Input.Keys.F10);
    }
}
