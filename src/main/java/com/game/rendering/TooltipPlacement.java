package com.game.rendering;

/**
 * Matemática pura do posicionamento do tooltip: prefere acima da carta, vira para baixo quando
 * não cabe, mantém o painel dentro do palco e calcula o x do triângulo-ponteiro.
 */
public final class TooltipPlacement {

    public record Placement(float x, float y, float pointerX, boolean above) {
    }

    private TooltipPlacement() {
    }

    /**
     * Coordenadas em pixels do palco (origem embaixo à esquerda, como no Scene2D).
     *
     * @param cardX     borda esquerda da carta
     * @param cardY     borda inferior da carta
     * @param tipWidth  largura final do painel
     * @param tipHeight altura final do painel
     * @param margin    distância mínima até as bordas do palco
     * @param gap       distância entre a carta e o painel (onde fica o triângulo)
     */
    public static Placement place(float cardX, float cardY, float cardWidth, float cardHeight,
                                  float tipWidth, float tipHeight, float stageWidth,
                                  float stageHeight, float margin, float gap,
                                  float pointerInset) {
        float cardCenterX = cardX + cardWidth / 2f;
        float cardTop = cardY + cardHeight;
        boolean above = cardTop + gap + tipHeight <= stageHeight - margin;
        float y = above ? cardTop + gap : cardY - gap - tipHeight;
        float maxY = Math.max(margin, stageHeight - tipHeight - margin);
        y = Math.max(margin, Math.min(y, maxY));

        float maxX = Math.max(margin, stageWidth - tipWidth - margin);
        float x = Math.max(margin, Math.min(cardCenterX - tipWidth / 2f, maxX));

        float pointerX;
        if (tipWidth <= pointerInset * 2f) {
            pointerX = tipWidth / 2f;
        } else {
            pointerX = Math.max(pointerInset,
                    Math.min(cardCenterX - x, tipWidth - pointerInset));
        }
        return new Placement(x, y, pointerX, above);
    }
}
