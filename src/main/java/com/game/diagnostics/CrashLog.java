package com.game.diagnostics;

import com.game.net.protocol.MessageType;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Grava um relatório de falha em {@code logs/duelo-crash-<timestamp>.log} contendo a pilha, a
 * thread, a fase/rodada/versão correntes e as últimas 20 mensagens de protocolo (tipo, tamanho e
 * id). É instalado como manipulador padrão de exceções não tratadas no cliente e no servidor.
 */
public final class CrashLog {

    private static final Logger LOG = Logger.getLogger(CrashLog.class.getName());
    private static final int MAX_MESSAGES = 20;
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final Deque<String> MESSAGES = new ArrayDeque<>();

    private static volatile boolean installed;
    private static volatile String phase = "desconhecida";
    private static volatile int round = -1;
    private static volatile long version = -1;

    private CrashLog() {
    }

    /** Instala o manipulador padrão de exceções não tratadas (idempotente). */
    public static void install() {
        if (installed) {
            return;
        }
        synchronized (CrashLog.class) {
            if (installed) {
                return;
            }
            installed = true;
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                Path file = write(throwable, "uncaught");
                System.err.println("Exceção não tratada na thread " + thread.getName());
                throwable.printStackTrace();
                if (file != null) {
                    System.err.println("Log de falha: " + file.toAbsolutePath());
                }
            });
        }
    }

    /** Atualiza o contexto mostrado no relatório. */
    public static void context(String currentPhase, int currentRound, long currentVersion) {
        phase = currentPhase == null ? "desconhecida" : currentPhase;
        round = currentRound;
        version = currentVersion;
    }

    /** Registra uma mensagem de protocolo (mantém apenas as últimas 20). */
    public static void protocol(String direction, MessageType type, int bytes, String messageId) {
        String line = String.format("%s %-18s %5dB id=%s", direction,
                type == null ? "?" : type, bytes, messageId == null ? "-" : messageId);
        synchronized (MESSAGES) {
            MESSAGES.addLast(line);
            while (MESSAGES.size() > MAX_MESSAGES) {
                MESSAGES.removeFirst();
            }
        }
    }

    /**
     * Escreve o relatório da falha. Devolve o arquivo criado, ou {@code null} se não foi possível
     * gravar (o erro original nunca é mascarado por uma falha de log).
     */
    public static Path write(Throwable throwable, String source) {
        String stamp = LocalDateTime.now().format(STAMP);
        Path directory = Path.of("logs");
        Path file = directory.resolve("duelo-crash-" + stamp + ".log");
        try {
            Files.createDirectories(directory);
            StringBuilder text = new StringBuilder();
            text.append("Duelo de Fichas - relatorio de falha\n");
            text.append("data: ").append(LocalDateTime.now()).append('\n');
            text.append("origem: ").append(source).append('\n');
            text.append("thread: ").append(Thread.currentThread().getName()).append('\n');
            text.append("fase: ").append(phase).append('\n');
            text.append("rodada: ").append(round).append('\n');
            text.append("versao do instantaneo: ").append(version).append("\n\n");
            text.append("=== ultimas mensagens de protocolo ===\n");
            synchronized (MESSAGES) {
                if (MESSAGES.isEmpty()) {
                    text.append("(nenhuma)\n");
                }
                for (String message : MESSAGES) {
                    text.append(message).append('\n');
                }
            }
            text.append("\n=== pilha ===\n");
            StringWriter trace = new StringWriter();
            throwable.printStackTrace(new PrintWriter(trace));
            text.append(trace);
            Files.writeString(file, text.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return file;
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Não foi possível gravar o log de falha em " + file, e);
            return null;
        }
    }
}
