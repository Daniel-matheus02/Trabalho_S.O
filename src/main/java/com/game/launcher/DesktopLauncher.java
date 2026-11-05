package com.game.launcher;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/** Ponto de entrada para desktop: inicializa a janela libGDX + LWJGL3. */
public final class DesktopLauncher {

    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Duelo de Fichas");
        config.setWindowedMode(DuelGame.VIEW_WIDTH, DuelGame.VIEW_HEIGHT);
        config.setWindowSizeLimits(1180, 700, -1, -1);
        config.useVsync(true);
        config.setForegroundFPS(60);
        config.setResizable(true);

        DuelGame.LaunchOptions options = DuelGame.LaunchOptions.parse(args);
        new Lwjgl3Application(new DuelGame(options), config);
    }

}
