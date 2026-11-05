package com.game.client.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.game.client.controller.NetworkMatchSession;
import com.game.client.network.ClientEvent;
import com.game.client.network.GameClient;
import com.game.launcher.DuelGame;
import com.game.net.protocol.SessionInfoPayload;
import com.game.rendering.Palette;
import com.game.rendering.Ui;
import com.game.server.ServerConfig;
import com.game.server.network.GameServer;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Abre uma partida na LAN como anfitrião ou entrando nela, com um servidor autoritativo embutido no lado do anfitrião. */
public final class MultiplayerMenuScreen extends BaseScreen {

    private final Table body = new Table();
    private final Label statusLabel = new Label("", skin, "small");
    private final TextField nameField;
    private final TextField hostPortField;
    private final TextField joinIpField;
    private final TextField joinPortField;
    private final TextField codeField;
    private final Map<Integer, String> playerNames = new LinkedHashMap<>();

    private GameClient client;
    private GameServer hostServer;
    private SessionInfoPayload sessionInfo;
    private String mode;
    private String pendingCode;
    private String serverHost;
    private int serverPort;
    private boolean connecting;
    private boolean waitingRoomShown;
    private boolean createdByMe;
    private boolean handedOff;

    public MultiplayerMenuScreen(DuelGame game) {
        super(game);
        nameField = new TextField(game.settings().playerName(), skin);
        nameField.setMessageText("SEU NOME");
        nameField.setMaxLength(20);
        hostPortField = new TextField("7777", skin);
        joinIpField = new TextField("127.0.0.1", skin);
        joinPortField = new TextField("7777", skin);
        codeField = new TextField("", skin);
        codeField.setMessageText("C\u00d3DIGO DA SALA");
        build();
        showForms();
    }

    private void build() {
        Table root = new Table();
        root.setFillParent(true);
        root.pad(22).top();
        stage.addActor(root);

        Table header = new Table();
        header.add(Ui.button(skin, "\u2190 VOLTAR", "ghost", () -> game.showMainMenu()))
                .width(140).height(38);
        header.add(Ui.label(skin, "MULTIPLAYER NA LAN", "h1")).expandX().center();
        header.add().width(140);
        root.add(header).growX().padBottom(14).row();

        Table nameRow = new Table();
        nameRow.add(Ui.label(skin, "SEU NOME", "gold-small")).padRight(10);
        nameRow.add(nameField).width(300).height(42);
        root.add(nameRow).padBottom(14).row();

        root.add(body).grow().row();
        root.add(statusLabel).growX().height(26).padTop(8);
    }

    private void showForms() {
        waitingRoomShown = false;
        body.clearChildren();
        Table columns = new Table();
        columns.add(hostPanel()).width(550).padRight(24);
        columns.add(joinPanel()).width(550);
        body.add(columns).center().expand();
    }

    private Table hostPanel() {
        Table panel = Ui.panel(assets, "panelDark");
        panel.pad(24).top().left();
        panel.add(Ui.label(skin, "CRIAR SALA", "h2")).left().row();
        Label description = Ui.wrapLabel(skin,
                "ABRE UM SERVIDOR AUTORITATIVO NESTA M\u00c1QUINA. COMPARTILHE O C\u00d3DIGO E O IP "
                        + "DA LAN COM O OUTRO JOGADOR.", "body-dim");
        panel.add(description).width(500).left().padTop(4).row();

        panel.add(Ui.label(skin, "PORTA TCP", "gold-small")).left().padTop(18).row();
        panel.add(hostPortField).width(220).height(44).left().row();

        Label hint = Ui.wrapLabel(skin,
                "VOC\u00ca TAMB\u00c9M PODE JOGAR NESTA M\u00c1QUINA: O CLIENTE CONECTA EM 127.0.0.1.",
                "small");
        panel.add(hint).width(500).left().padTop(12).row();
        panel.add(Ui.button(skin, "CRIAR SALA E AGUARDAR", "gold", this::host))
                .width(360).height(50).padTop(20).left().row();
        return panel;
    }

    private Table joinPanel() {
        Table panel = Ui.panel(assets, "panelDark");
        panel.pad(24).top().left();
        panel.add(Ui.label(skin, "ENTRAR EM SALA", "h2")).left().row();
        Label description = Ui.wrapLabel(skin,
                "INFORME O IP DO HOST, A PORTA E O C\u00d3DIGO DE 6 CARACTERES DA SALA.",
                "body-dim");
        panel.add(description).width(500).left().padTop(4).row();

        Table fields = new Table();
        fields.left();
        fields.add(Ui.label(skin, "IP DO SERVIDOR", "gold-small")).left().padRight(10);
        fields.add(joinIpField).width(260).height(42).row();
        fields.add(Ui.label(skin, "PORTA", "gold-small")).left().padRight(10).padTop(8);
        fields.add(joinPortField).width(140).height(42).padTop(8).left().row();
        fields.add(Ui.label(skin, "C\u00d3DIGO DA SALA", "gold-small")).left().padRight(10).padTop(8);
        fields.add(codeField).width(260).height(42).padTop(8).row();
        panel.add(fields).left().padTop(16).row();

        panel.add(Ui.button(skin, "ENTRAR NA SALA", "green", this::join))
                .width(360).height(50).padTop(20).left().row();
        return panel;
    }

    // ------------------------------------------------------------------ anfitriar / entrar

    private void host() {
        if (connecting) {
            return;
        }
        int port = parsePort(hostPortField.getText(), 7777);
        setStatus("ABRINDO SERVIDOR NA PORTA " + port + "...", Palette.INK_DIM);
        try {
            hostServer = startHostServer(port);
        } catch (Exception e) {
            setStatus("N\u00c3O FOI POSS\u00cdVEL ABRIR O SERVIDOR: " + e.getMessage(), Palette.BAD);
            hostServer = null;
            return;
        }
        serverPort = hostServer.boundPort();
        serverHost = "127.0.0.1";
        mode = "HOST";
        connecting = true;
        client = new GameClient();
        client.connect("127.0.0.1", serverPort);
        setStatus("SERVIDOR ATIVO NA PORTA " + serverPort + ". CONECTANDO...", Palette.INK_DIM);
    }

    private GameServer startHostServer(int preferredPort) throws Exception {
        ServerConfig base = ServerConfig.load(null).withBindAddress("0.0.0.0");
        Exception last = null;
        for (int offset = 0; offset < 6; offset++) {
            GameServer server = new GameServer(base.withPort(preferredPort + offset));
            try {
                server.start();
                return server;
            } catch (Exception e) {
                last = e;
                server.close();
            }
        }
        throw last == null ? new IllegalStateException("Nenhuma porta livre") : last;
    }

    private void join() {
        if (connecting) {
            return;
        }
        String ip = joinIpField.getText().trim();
        String code = codeField.getText().trim().toUpperCase(java.util.Locale.ROOT);
        int port = parsePort(joinPortField.getText(), 7777);
        if (ip.isEmpty() || code.isEmpty()) {
            setStatus("INFORME O IP E O C\u00d3DIGO DA SALA.", Palette.BAD);
            return;
        }
        mode = "JOIN";
        pendingCode = code;
        serverHost = ip;
        serverPort = port;
        connecting = true;
        client = new GameClient();
        setStatus("CONECTANDO A " + ip + ":" + port + "...", Palette.INK_DIM);
        client.connect(ip, port);
    }

    // ------------------------------------------------------------------ eventos

    @Override
    public void render(float delta) {
        if (!handedOff) {
            pollEvents();
        }
        super.render(delta);
    }

    private void pollEvents() {
        if (client == null) {
            return;
        }
        ClientEvent event;
        while ((event = client.pollEvent()) != null) {
            switch (event) {
                case ClientEvent.Connected connected -> {
                    if ("HOST".equals(mode)) {
                        client.createSession(playerName());
                        setStatus("SALA CRIADA. CONECTANDO COMO HOST...", Palette.INK_DIM);
                    } else if ("JOIN".equals(mode)) {
                        client.joinSession(pendingCode, playerName(), null);
                        setStatus("ENTRANDO NA SALA " + pendingCode + "...", Palette.INK_DIM);
                    }
                }
                case ClientEvent.SessionInfoReceived received -> {
                    sessionInfo = received.info();
                    createdByMe = received.created();
                    connecting = false;
                    playerNames.put(received.info().playerId(), received.info().playerName());
                    showWaitingRoom();
                }
                case ClientEvent.PlayerStatus status -> {
                    playerNames.put(status.payload().playerId(), status.payload().playerName());
                    if (waitingRoomShown) {
                        showWaitingRoom();
                    }
                }
                case ClientEvent.GameStarted started -> openGame();
                case ClientEvent.ErrorReceived error -> {
                    setStatus(error.code() + ": " + error.message(), Palette.BAD);
                    connecting = false;
                    closeConnection();
                    showForms();
                }
                case ClientEvent.ConnectionLost lost -> {
                    if (!handedOff) {
                        setStatus("CONEX\u00c3O PERDIDA: " + lost.reason(), Palette.BAD);
                        if (waitingRoomShown) {
                            showForms();
                        }
                    }
                }
                case ClientEvent.Reconnecting reconnecting -> setStatus(
                        "RECONECTANDO (" + reconnecting.attempt() + "/" + reconnecting.maxAttempts()
                                + ")...", Palette.GOLD);
                case ClientEvent.Reconnected reconnected -> setStatus("RECONECTADO.", Palette.GOOD);
                case ClientEvent.ReconnectFailed failed -> {
                    setStatus("FALHA AO RECONECTAR: " + failed.reason(), Palette.BAD);
                    closeConnection();
                    showForms();
                }
                default -> {
                }
            }
        }
    }

    private void showWaitingRoom() {
        if (sessionInfo == null) {
            return;
        }
        waitingRoomShown = true;
        body.clearChildren();

        Table panel = Ui.panel(assets, "panelDark");
        panel.pad(30).center();
        panel.add(Ui.label(skin, "SALA " + (createdByMe ? "CRIADA" : "ENCONTRADA"), "h3"))
                .row();
        panel.add(Ui.label(skin, "C\u00d3DIGO DA SALA", "small")).padTop(10).row();
        panel.add(Ui.label(skin, sessionInfo.sessionCode(), "title")).padTop(2).row();

        String address = "HOST".equals(mode)
                ? "IP LOCAL: " + lanAddress() + "   \u00b7   PORTA: " + serverPort
                : "SERVIDOR: " + serverHost + ":" + serverPort;
        panel.add(Ui.label(skin, address, "body")).padTop(14).row();

        Table players = new Table();
        players.left();
        String me = playerNames.getOrDefault(sessionInfo.playerId(), sessionInfo.playerName());
        players.add(Ui.label(skin, "\u25cf " + me + " (VOC\u00ca)", "good")).left().row();
        String opponent = sessionInfo.opponentName();
        if (opponent == null) {
            for (Map.Entry<Integer, String> entry : playerNames.entrySet()) {
                if (entry.getKey() != sessionInfo.playerId()) {
                    opponent = entry.getValue();
                }
            }
        }
        if (opponent == null) {
            players.add(Ui.label(skin, "\u25cb AGUARDANDO OPONENTE...", "body-dim"))
                    .left().padTop(6).row();
        } else {
            players.add(Ui.label(skin, "\u25cf " + opponent, "p2")).left().padTop(6).row();
        }
        panel.add(players).padTop(22).row();

        if ("HOST".equals(mode)) {
            Label hint = Ui.wrapLabel(skin,
                    "NO OUTRO COMPUTADOR: MULTIPLAYER \u2192 ENTRAR EM SALA \u2192 IP "
                            + lanAddress() + " \u00b7 PORTA " + serverPort + " \u00b7 C\u00d3DIGO "
                            + sessionInfo.sessionCode(), "small");
            panel.add(hint).width(640).padTop(16).row();
        }

        panel.add(Ui.label(skin, "A PARTIDA COME\u00c7A ASSIM QUE OS DOIS ESTIVEREM CONECTADOS.",
                "small")).padTop(16).row();

        Table actions = new Table();
        actions.add(Ui.button(skin, "COPIAR C\u00d3DIGO", "green", this::copyCode))
                .width(220).height(46).padRight(12);
        actions.add(Ui.button(skin, "CANCELAR", "danger", this::cancel))
                .width(180).height(46);
        panel.add(actions).padTop(22).row();

        body.add(panel).width(760).center().expand();
    }

    private void copyCode() {
        if (sessionInfo != null) {
            Gdx.app.getClipboard().setContents(sessionInfo.sessionCode());
            setStatus("C\u00d3DIGO " + sessionInfo.sessionCode() + " COPIADO.", Palette.GOOD);
        }
    }

    private void openGame() {
        if (handedOff || client == null) {
            return;
        }
        handedOff = true;
        NetworkMatchSession session = new NetworkMatchSession(client);
        GameServer serverToClose = hostServer;
        client = null;
        hostServer = null;
        Runnable cleanup = serverToClose == null ? null : serverToClose::close;
        game.showNetworkGame(session, cleanup);
    }

    private void cancel() {
        closeConnection();
        showForms();
        setStatus("CONEX\u00c3O ENCERRADA.", Palette.INK_DIM);
    }

    private void closeConnection() {
        if (client != null) {
            client.close();
            client = null;
        }
        if (hostServer != null) {
            hostServer.close();
            hostServer = null;
        }
        connecting = false;
        waitingRoomShown = false;
        sessionInfo = null;
        mode = null;
        playerNames.clear();
    }

    private String playerName() {
        String value = nameField.getText().trim();
        if (value.isEmpty()) {
            value = game.settings().playerName();
        }
        game.settings().setPlayerName(value);
        return value;
    }

    private void setStatus(String text, Color color) {
        statusLabel.setText(text);
        statusLabel.setColor(color);
    }

    private static int parsePort(String text, int fallback) {
        try {
            int port = Integer.parseInt(text.trim());
            return port >= 0 && port <= 65535 ? port : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Primeiro endereço IPv4 que não seja de loopback, usado para informar ao outro jogador onde se conectar. */
    public static String lanAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) {
                    continue;
                }
                Enumeration<java.net.InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // Recai para o loopback.
        }
        return "127.0.0.1";
    }

    @Override
    protected void onEscape() {
        game.showMainMenu();
    }

    @Override
    public void dispose() {
        if (!handedOff) {
            closeConnection();
        }
        super.dispose();
    }
}
