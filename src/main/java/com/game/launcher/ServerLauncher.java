package com.game.launcher;

import com.game.server.ServerConfig;
import com.game.server.network.GameServer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Ponto de entrada do servidor autoritativo autônomo. Executa sem nenhum cliente gráfico:
 * {@code java -jar duelo-server.jar --port=7777}.
 */
public final class ServerLauncher {

    private ServerLauncher() {
    }

    public static void main(String[] args) {
        ServerConfig config;
        try {
            config = configure(args);
        } catch (Exception e) {
            System.err.println("Configuração de servidor inválida: " + e.getMessage());
            System.exit(2);
            return;
        }

        GameServer server = new GameServer(config);
        try {
            server.start();
        } catch (Exception e) {
            System.err.println("Não foi possível iniciar o servidor em " + config.bindAddress() + ":"
                    + config.port() + " - " + e.getMessage());
            server.close();
            System.exit(1);
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "duelo-shutdown"));
        printBanner(server);
        try {
            new java.util.concurrent.CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static ServerConfig configure(String[] args) throws Exception {
        Path configPath = Path.of("config", "server.properties");
        String bind = null;
        Integer port = null;
        for (String arg : args) {
            if (arg.startsWith("--config=")) {
                configPath = Path.of(arg.substring("--config=".length()));
            } else if (arg.startsWith("--bind=")) {
                bind = arg.substring("--bind=".length());
            } else if (arg.startsWith("--port=")) {
                port = Integer.parseInt(arg.substring("--port=".length()));
            } else if (arg.equals("--help") || arg.equals("-h")) {
                System.out.println("""
                        Duelo de Fichas - servidor autoritativo
                        Uso: java -jar duelo-server.jar [opcoes]
                          --config=<arquivo>  caminho do server.properties
                          --bind=<ip>         endereco de escuta (padrao 0.0.0.0)
                          --port=<porta>      porta TCP (padrao 7777)
                          --help              esta ajuda
                        """);
                System.exit(0);
            }
        }
        ServerConfig config = ServerConfig.load(configPath);
        if (bind != null) {
            config = config.withBindAddress(bind);
        }
        if (port != null) {
            config = config.withPort(port);
        }
        return config;
    }

    private static void printBanner(GameServer server) {
        ServerConfig config = server.config();
        System.out.println("==============================================");
        System.out.println("  DUELO DE FICHAS - SERVIDOR AUTORITATIVO");
        System.out.println("==============================================");
        System.out.println("Escutando em: " + config.bindAddress() + ":" + server.boundPort());
        System.out.println("Sessoes simultaneas: " + config.maxSessions());
        System.out.println("Jogadores por sessao: " + config.maxPlayersPerSession());
        System.out.println("Timeout de sessao: " + config.sessionTimeoutSeconds() + "s");
        System.out.println("Timeout de conexao: " + config.connectionTimeoutSeconds() + "s");
        List<String> addresses = localAddresses();
        if (!addresses.isEmpty()) {
            System.out.println("Enderecos LAN:");
            for (String address : addresses) {
                System.out.println("  " + address + ":" + server.boundPort());
            }
        }
        System.out.println("No cliente: MULTIPLAYER -> ENTRAR EM SALA");
        System.out.println("Pressione Ctrl+C para encerrar.");
    }

    private static List<String> localAddresses() {
        List<String> result = new ArrayList<>();
        try {
            var interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                var network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) {
                    continue;
                }
                var addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    var address = addresses.nextElement();
                    if (address instanceof java.net.Inet4Address && address.isSiteLocalAddress()) {
                        result.add(address.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
            // Nenhuma informação de LAN disponível.
        }
        return result;
    }
}
