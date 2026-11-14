package com.game.launcher;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

/**
 * Calcula o tamanho inicial da janela a partir da área útil real do monitor (sem a barra de
 * tarefas) e do fator de escala do sistema. Nunca abre maior que a tela.
 */
public final class WindowSizing {

    /** Tamanho máximo de projeto (o layout foi desenhado para até 1600×900 lógicos). */
    public static final int DESIGN_MAX_WIDTH = 1600;
    public static final int DESIGN_MAX_HEIGHT = 900;
    /** Tamanho mínimo para o qual o layout responsivo foi validado. */
    public static final int MIN_WIDTH = 1000;
    public static final int MIN_HEIGHT = 620;

    private WindowSizing() {
    }

    /**
     * @param scale fator de escala do monitor (1.0 = 100%, 1.25 = 125%, 1.5 = 150%).
     */
    public record Fit(int width, int height, int minWidth, int minHeight,
                      float scale, Rectangle usable, String monitor) {

        public int x() {
            return usable.x + Math.max(0, (usable.width - width) / 2);
        }

        public int y() {
            return usable.y + Math.max(0, (usable.height - height) / 2);
        }

        public String describe() {
            return "monitor=" + monitor
                    + " usable=" + usable.width + "x" + usable.height + "@" + usable.x + "," + usable.y
                    + " scale=" + Math.round(scale * 100) + "%"
                    + " window=" + width + "x" + height
                    + " min=" + minWidth + "x" + minHeight;
        }
    }

    public static Fit compute() {
        try {
            GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice device = environment.getDefaultScreenDevice();
            GraphicsConfiguration configuration = device.getDefaultConfiguration();
            Rectangle usable = environment.getMaximumWindowBounds();
            if (usable == null || usable.width < 320 || usable.height < 240) {
                usable = configuration.getBounds();
            }
            float scale = (float) configuration.getDefaultTransform().getScaleX();
            int width = clamp(Math.round(usable.width * 0.9f), MIN_WIDTH, DESIGN_MAX_WIDTH);
            int height = clamp(Math.round(usable.height * 0.9f), MIN_HEIGHT, DESIGN_MAX_HEIGHT);
            // Margem para as bordas da janela: o conteúdo nunca deve encostar na borda da tela.
            width = Math.min(width, Math.max(320, usable.width - 16));
            height = Math.min(height, Math.max(240, usable.height - 16));
            return new Fit(width, height,
                    Math.min(MIN_WIDTH, width), Math.min(MIN_HEIGHT, height),
                    scale > 0f ? scale : 1f, usable, device.getIDstring());
        } catch (Throwable ignored) {
            // Sem ambiente gráfico (ou AWT indisponível): cai em um tamanho conservador.
            Rectangle fallback = new Rectangle(0, 0, 1280, 720);
            return new Fit(1280, 720, MIN_WIDTH, MIN_HEIGHT, 1f, fallback, "fallback");
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
