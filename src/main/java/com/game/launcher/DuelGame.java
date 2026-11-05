package com.game.launcher;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import com.game.assets.GameAssets;
import com.game.client.GameSettings;
import com.game.client.controller.LocalMatchSession;
import com.game.client.controller.MatchSession;
import com.game.client.screens.GameOverScreen;
import com.game.client.screens.GameScreen;
import com.game.client.screens.MainMenuScreen;
import com.game.client.screens.MultiplayerMenuScreen;
import com.game.client.screens.SettingsScreen;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import com.game.server.network.GameServer;

/**
 * Ponto de entrada da aplicação que implementa {@link ApplicationListener} do libGDX: é dono dos
 * assets, das configurações, da navegação entre telas e (no modo de captura de tela) da captura
 * de quadros para verificação.
 */
public final class DuelGame extends Game {

    public static final int VIEW_WIDTH = 1280;
    public static final int VIEW_HEIGHT = 720;

    private final LaunchOptions options;
    private GameAssets assets;
    private GameSettings settings;
    private int frames;
    private boolean captured;

    public DuelGame(LaunchOptions options) {
        this.options = options == null ? LaunchOptions.none() : options;
    }

    @Override
    public void create() {
        assets = new GameAssets();
        settings = new GameSettings();
        if (options.hasScreenRequest()) {
            openRequestedScreen(options.screen());
        } else {
            showMainMenu();
        }
    }

    private void openRequestedScreen(String screen) {
        switch (screen == null ? "menu" : screen) {
            case "game", "reveal", "draft", "gameover" -> openDemo(screen);
            case "multiplayer" -> showMultiplayer();
            case "settings" -> showSettings();
            default -> showMainMenu();
        }
    }

    public void showMainMenu() {
        switchTo(new MainMenuScreen(this));
    }

    public void showMultiplayer() {
        switchTo(new MultiplayerMenuScreen(this));
    }

    public void showSettings() {
        switchTo(new SettingsScreen(this));
    }

    public void showSinglePlayer() {
        LocalMatchSession session = new LocalMatchSession(settings.playerName());
        switchTo(new GameScreen(this, session, null));
    }

    public void showNetworkGame(MatchSession session, Runnable cleanup) {
        switchTo(new GameScreen(this, session, cleanup));
    }

    public void showGameScreen(MatchSession session, Runnable cleanup) {
        switchTo(new GameScreen(this, session, cleanup));
    }

    public void showGameOver(MatchSession session, Runnable cleanup) {
        switchTo(new GameOverScreen(this, session, cleanup));
    }

    private void switchTo(Screen next) {
        Screen previous = getScreen();
        setScreen(next);
        if (previous != null) {
            previous.dispose();
        }
    }

    /** Usado por --screen=game|reveal|draft|gameover para renderizar estados predefinidos para QA. */
    private void openDemo(String screen) {
        LocalMatchSession session = screen.equals("gameover")
                ? new LocalMatchSession(settings.playerName(),
                        new com.game.core.rules.GameRules(120, 8, 5, 3, 2), new java.util.Random(7))
                : new LocalMatchSession(settings.playerName());
        GameSnapshot snapshot = session.snapshot();
        switch (screen) {
            case "game" -> {
                GameScreen gameScreen = new GameScreen(this, session, null);
                switchTo(gameScreen);
                gameScreen.demoSelect(3);
                return;
            }
            case "reveal" -> {
                session.submitPlay(snapshot.player(1).hand().subList(0, 3));
                tickUntil(session, GamePhase.REVEAL, 30);
            }
            case "draft" -> {
                session.submitPlay(snapshot.player(1).hand().subList(0, 3));
                tickUntil(session, GamePhase.REVEAL, 30);
                session.advance();
                tickUntil(session, GamePhase.DRAFT, 30);
            }
            case "gameover" -> {
                for (int i = 0; i < 500; i++) {
                    GameSnapshot state = session.snapshot();
                    if (state.phase() == GamePhase.GAME_OVER) {
                        break;
                    }
                    switch (state.phase()) {
                        case BUILD -> {
                            if (state.currentPlayer() == 1) {
                                session.submitPlay(state.player(1).hand().subList(0, 5));
                            } else {
                                session.tick(1.2f);
                            }
                        }
                        case REVEAL -> {
                            session.advance();
                            session.tick(1.2f);
                        }
                        case DRAFT -> {
                            if (state.currentPlayer() == 1) {
                                session.pickJoker(0);
                            } else {
                                session.tick(1.2f);
                            }
                        }
                        default -> {
                        }
                    }
                }
            }
            default -> {
            }
        }
        if (screen.equals("gameover")) {
            switchTo(new GameOverScreen(this, session, null));
        } else {
            switchTo(new GameScreen(this, session, null));
        }
    }

    private static void tickUntil(LocalMatchSession session, GamePhase phase, int maxTicks) {
        for (int i = 0; i < maxTicks && session.snapshot().phase() != phase; i++) {
            session.tick(1.2f);
        }
    }

    @Override
    public void render() {
        super.render();
        frames++;
        if (options.screenshotPath() != null && !captured && frames >= 30) {
            captureScreenshot(options.screenshotPath());
        }
    }

    private void captureScreenshot(String path) {
        captured = true;
        Pixmap pixmap = ScreenUtils.getFrameBufferPixmap(0, 0,
                Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Pixmap flipped = new Pixmap(pixmap.getWidth(), pixmap.getHeight(), pixmap.getFormat());
        flipped.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < pixmap.getHeight(); y++) {
            for (int x = 0; x < pixmap.getWidth(); x++) {
                flipped.drawPixel(x, pixmap.getHeight() - 1 - y, pixmap.getPixel(x, y));
            }
        }
        PixmapIO.writePNG(Gdx.files.absolute(path), flipped);
        pixmap.dispose();
        flipped.dispose();
        Gdx.app.log("Screenshot", "Salvo em " + path);
        Gdx.app.exit();
    }

    @Override
    public void dispose() {
        Screen current = getScreen();
        if (current != null) {
            current.hide();
            current.dispose();
            setScreen(null);
        }
        if (assets != null) {
            assets.dispose();
        }
    }

    public GameAssets assets() {
        return assets;
    }

    public GameSettings settings() {
        return settings;
    }

    /** Opções de inicialização: verificação por captura de tela e seleção da tela padrão. */
    public record LaunchOptions(String screenshotPath, String screen) {

        public static LaunchOptions none() {
            return new LaunchOptions(null, null);
        }

        public static LaunchOptions parse(String[] args) {
            String screenshot = null;
            String screen = null;
            if (args != null) {
                for (String arg : args) {
                    if (arg.startsWith("--screenshot=")) {
                        screenshot = arg.substring("--screenshot=".length());
                    } else if (arg.startsWith("--screen=")) {
                        screen = arg.substring("--screen=".length());
                    }
                }
            }
            return new LaunchOptions(screenshot, screen);
        }

        public boolean hasScreenRequest() {
            return screen != null && !screen.isBlank();
        }
    }
}
