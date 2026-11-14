package com.game.qa;

import com.game.client.controller.LocalMatchSession;
import com.game.client.screens.BaseScreen;
import com.game.client.screens.GameScreen;
import com.game.client.screens.SettingsScreen;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import com.game.launcher.DuelGame;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Percorre a árvore de atores em vários tamanhos lógicos e falha se algo visível ultrapassar as
 * bordas horizontais da janela. Cobre a tela de seleção de cartas, a loja e as configurações.
 */
public final class LayoutProbe {

    private static final int[][] SIZES = {
            {1200, 650}, {1280, 720}, {1366, 768}, {1440, 900},
            {1600, 900}, {1920, 1080}, {2560, 1440}, {3840, 2160},
            // Áreas lógicas típicas de telas com escala de sistema.
            {1093, 614},  // 1366x768 @ 125%
            {1536, 864},  // 1920x1080 @ 125%
            {1280, 720},  // 1920x1080 @ 150%
            {1707, 960}   // 2560x1440 @ 150%
    };

    private LayoutProbe() {
    }

    public static void run(DuelGame game) {
        Map<String, BaseScreen> screens = new LinkedHashMap<>();
        screens.put("selecao-cartas", new GameScreen(game, new LocalMatchSession("QA"), null));
        screens.put("loja", new GameScreen(game, shopSession(), null));
        screens.put("configuracoes", new SettingsScreen(game));

        int failures = 0;
        int checks = 0;
        for (Map.Entry<String, BaseScreen> entry : screens.entrySet()) {
            BaseScreen screen = entry.getValue();
            for (int[] size : SIZES) {
                checks++;
                List<String> overflow = screen.probeOverflow(size[0], size[1]);
                if (overflow.isEmpty()) {
                    System.out.printf("[LayoutProbe] %-16s %4dx%-4d PASS%n",
                            entry.getKey(), size[0], size[1]);
                } else {
                    failures++;
                    System.out.printf("[LayoutProbe] %-16s %4dx%-4d FAIL %s%n",
                            entry.getKey(), size[0], size[1], String.join(" | ", overflow));
                }
            }
        }
        System.out.println("[LayoutProbe] checks=" + checks + " failures=" + failures);
    }

    private static LocalMatchSession shopSession() {
        LocalMatchSession session = new LocalMatchSession("QA");
        GameSnapshot snapshot = session.snapshot();
        session.submitPlay(snapshot.player(1).hand().subList(0, 2));
        tickUntil(session, GamePhase.SABOTAGE, 30);
        if (session.snapshot().phase() == GamePhase.SABOTAGE) {
            session.passSabotage();
        }
        tickUntil(session, GamePhase.REVEAL, 30);
        session.advance();
        tickUntil(session, GamePhase.SHOP, 30);
        return session;
    }

    private static void tickUntil(LocalMatchSession session, GamePhase phase, int maxTicks) {
        for (int i = 0; i < maxTicks && session.snapshot().phase() != phase; i++) {
            session.tick(1.2f);
        }
    }
}
