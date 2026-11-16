package com.game.launcher;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/** Ponto de entrada para desktop: inicializa a janela libGDX + LWJGL3. */
public final class DesktopLauncher {

    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        com.game.diagnostics.CrashLog.install();
        DuelGame.LaunchOptions options = DuelGame.LaunchOptions.parse(args);

        // A prova de rede não precisa de janela: roda antes de qualquer inicialização do GL.
        if ("netplay".equals(options.probe())) {
            System.exit(com.game.qa.NetplayProbe.run(args));
            return;
        }

        // Lê a área útil real do monitor e a escala do sistema: a janela nunca abre maior que a
        // tela e nunca fica menor que o layout responsivo suporta.
        WindowSizing.Fit fit = WindowSizing.compute();
        int width = options.width() > 0
                ? Math.min(options.width(), fit.usable().width) : fit.width();
        int height = options.height() > 0
                ? Math.min(options.height(), fit.usable().height) : fit.height();
        int x = fit.usable().x + Math.max(0, (fit.usable().width - width) / 2);
        int y = fit.usable().y + Math.max(0, (fit.usable().height - height) / 2);
        System.out.println("[Display] " + fit.describe()
                + " requested=" + width + "x" + height + " at " + x + "," + y);

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Duelo de Fichas");
        config.setWindowedMode(width, height);
        config.setWindowSizeLimits(Math.min(fit.minWidth(), width), Math.min(fit.minHeight(), height),
                -1, -1);
        config.setWindowPosition(x, y);
        config.useVsync(true);
        config.setForegroundFPS(60);
        config.setResizable(true);

        new Lwjgl3Application(new DuelGame(options), config);
    }

}
