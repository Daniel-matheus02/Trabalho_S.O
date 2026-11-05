package com.game.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configurações de rede do servidor autônomo. Os padrões são seguros para uma partida na LAN.
 */
public record ServerConfig(
        String bindAddress,
        int port,
        int maxSessions,
        int maxPlayersPerSession,
        int sessionTimeoutSeconds,
        int connectionTimeoutSeconds
) {

    public static final int SUPPORTED_PLAYERS = 2;

    public ServerConfig {
        if (bindAddress == null || bindAddress.isBlank()) {
            throw new IllegalArgumentException("bindAddress não pode estar em branco");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("porta fora do intervalo");
        }
        if (maxSessions <= 0) {
            throw new IllegalArgumentException("maxSessions deve ser positivo");
        }
        if (maxPlayersPerSession != SUPPORTED_PLAYERS) {
            throw new IllegalArgumentException("cada sessão suporta exatamente dois jogadores");
        }
        if (sessionTimeoutSeconds <= 0 || connectionTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("os tempos limite devem ser positivos");
        }
    }

    public static ServerConfig defaults() {
        return new ServerConfig("0.0.0.0", 7777, 32, SUPPORTED_PLAYERS, 120, 60);
    }

    /** Carrega um arquivo de propriedades, recorrendo aos padrões embutidos para chaves ausentes. */
    public static ServerConfig load(Path file) throws IOException {
        ServerConfig defaults = defaults();
        Properties properties = new Properties();
        if (file != null && Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                properties.load(reader);
            }
        } else {
            try (InputStream in = ServerConfig.class.getResourceAsStream("/config/server.properties")) {
                if (in != null) {
                    properties.load(in);
                }
            }
        }
        return new ServerConfig(
                properties.getProperty("bindAddress", defaults.bindAddress()),
                Integer.parseInt(properties.getProperty("port", String.valueOf(defaults.port()))),
                Integer.parseInt(properties.getProperty("maxSessions",
                        String.valueOf(defaults.maxSessions()))),
                Integer.parseInt(properties.getProperty("maxPlayersPerSession",
                        String.valueOf(defaults.maxPlayersPerSession()))),
                Integer.parseInt(properties.getProperty("sessionTimeoutSeconds",
                        String.valueOf(defaults.sessionTimeoutSeconds()))),
                Integer.parseInt(properties.getProperty("connectionTimeoutSeconds",
                        String.valueOf(defaults.connectionTimeoutSeconds())))
        );
    }

    public ServerConfig withPort(int newPort) {
        return new ServerConfig(bindAddress, newPort, maxSessions, maxPlayersPerSession,
                sessionTimeoutSeconds, connectionTimeoutSeconds);
    }

    public ServerConfig withBindAddress(String address) {
        return new ServerConfig(address, port, maxSessions, maxPlayersPerSession,
                sessionTimeoutSeconds, connectionTimeoutSeconds);
    }
}
