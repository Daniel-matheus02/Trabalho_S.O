package com.game.qa;

import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.game.client.network.GameClient;
import com.game.client.screens.GameScreen;
import com.game.client.screens.MultiplayerMenuScreen;
import com.game.launcher.DuelGame;
import com.game.net.protocol.SessionInfoPayload;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Prova de rede da TELA de LAN: usa a {@link MultiplayerMenuScreen} real, aperta "CRIAR SALA E
 * AGUARDAR" e faz um segundo {@link GameClient} entrar na sala, exatamente como o outro jogador da
 * LAN. O quadro é renderizado pelo próprio {@code DuelGame} ({@code game.render()}), então o
 * hand-off do menu para a mesa acontece no mesmo caminho do jogo de verdade — era nele que a queda
 * por {@code NullPointerException} em {@code MultiplayerMenuScreen.pollEvents} acontecia.
 *
 * <p>Falha (código 1) se qualquer quadro lançar exceção, se a sala não abrir, se o convidado não
 * conseguir entrar ou se a mesa não assumir a tela.</p>
 */
public final class LanHandoffProbe {

    private static final int MAX_FRAMES = 1_800;
    private static final long TIMEOUT_MS = 20_000L;

    private LanHandoffProbe() {
    }

    public static int run(DuelGame game) {
        GameClient guest = null;
        try {
            game.showMultiplayer();
            if (!(game.getScreen() instanceof MultiplayerMenuScreen menu)) {
                return fail("a tela de LAN não abriu");
            }
            setText(menu, "nameField", "Anfitriao");
            setText(menu, "hostPortField", "0");
            invoke(menu, "host");
            int port = intField(menu, "serverPort");
            if (port <= 0) {
                return fail("o servidor embutido não abriu (" + port + ")");
            }
            System.out.println("[LanHandoff] servidor do anfitrião em 127.0.0.1:" + port);

            if (!pump(game, () -> field(menu, "sessionInfo") != null, "sala criada")) {
                return 1;
            }
            SessionInfoPayload info = (SessionInfoPayload) field(menu, "sessionInfo");

            guest = new GameClient();
            guest.connect("127.0.0.1", port).get(5, TimeUnit.SECONDS);
            guest.joinSession(info.sessionCode(), "Convidado", null).get(5, TimeUnit.SECONDS);
            System.out.println("[LanHandoff] convidado entrou na sala " + info.sessionCode());

            if (!pump(game, () -> game.getScreen() instanceof GameScreen, "mesa assumiu a tela")) {
                return 1;
            }
            // A mesa precisa continuar desenhando depois do hand-off.
            for (int i = 0; i < 180; i++) {
                game.render();
            }
            if (!(game.getScreen() instanceof GameScreen)) {
                return fail("a tela mudou de novo sozinha depois do início da partida");
            }
            System.out.println("[LanHandoff] PASSOU: sala criada, convidado aceito, mesa renderizando");
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return fail("erro fatal: " + e);
        } finally {
            if (guest != null) {
                guest.close();
            }
            game.showMainMenu();
        }
    }

    /** Renderiza quadros reais até a condição valer, falhando em exceção ou em tempo esgotado. */
    private static boolean pump(DuelGame game, BooleanSupplier condition, String what) {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        for (int frame = 0; frame < MAX_FRAMES; frame++) {
            try {
                game.render();
            } catch (RuntimeException e) {
                e.printStackTrace();
                fail("exceção no quadro " + frame + " durante \"" + what + "\": " + e);
                return false;
            }
            if (condition.getAsBoolean()) {
                return true;
            }
            if (System.currentTimeMillis() > deadline) {
                break;
            }
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        fail("tempo esgotado aguardando: " + what);
        return false;
    }

    private static int fail(String message) {
        System.out.println("[LanHandoff] FALHOU: " + message);
        return 1;
    }

    private static void setText(MultiplayerMenuScreen screen, String name, String value) {
        ((TextField) field(screen, name)).setText(value);
    }

    private static int intField(MultiplayerMenuScreen screen, String name) {
        Object value = field(screen, name);
        return value instanceof Integer number ? number : -1;
    }

    private static Object field(MultiplayerMenuScreen screen, String name) {
        try {
            Field field = MultiplayerMenuScreen.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(screen);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("campo inacessível: " + name, e);
        }
    }

    private static void invoke(MultiplayerMenuScreen screen, String name) {
        try {
            Method method = MultiplayerMenuScreen.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(screen);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("método inacessível: " + name, e);
        }
    }
}
