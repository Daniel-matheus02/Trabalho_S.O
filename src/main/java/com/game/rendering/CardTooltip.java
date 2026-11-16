package com.game.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.utils.Align;
import com.game.assets.GameAssets;

/**
 * UM único tooltip sobre o palco. As cartas de poder apenas informam entrada/saída do hover; o
 * painel é reposicionado e reutilizado, nunca criado por carta.
 */
public final class CardTooltip extends Group {

    private static final float WIDTH = 286f;
    private static final float PAD = 10f;
    private static final float MARGIN = 8f;
    private static final float GAP = 10f;
    private static final float POINTER = 13f;
    private static final float POINTER_INSET = 18f;
    private static final float POP_IN = 0.12f;

    private final GameAssets assets;
    private final Skin skin;
    private final Stage stage;
    private final Table panel;
    private final Label title;
    private final Label body;
    private final Vector2 scratch = new Vector2();

    private Actor source;
    private boolean above = true;
    private float pointerX;

    public CardTooltip(GameAssets assets, Stage stage) {
        this.assets = assets;
        this.stage = stage;
        this.skin = assets.skin();
        setVisible(false);
        setTouchable(Touchable.disabled);

        panel = new Table();
        panel.setBackground(new NinePatchDrawable(new NinePatch(assets.patch("panelGold"))));
        panel.pad(PAD);
        panel.setTouchable(Touchable.disabled);
        title = Ui.label(skin, "", "h3");
        title.setTouchable(Touchable.disabled);
        body = Ui.label(skin, "", "white-small");
        body.setWrap(true);
        body.setAlignment(Align.left);
        body.setTouchable(Touchable.disabled);
        panel.add(title).left().growX().row();
        panel.add(body).left().growX().padTop(4).row();
        addActor(panel);
    }

    /** Mostra (ou move) o tooltip acima da carta, virando para baixo quando necessário. */
    public void show(Actor source, String titleText, String description) {
        if (source == null || stage == null) {
            return;
        }
        this.source = source;
        title.setText(titleText == null ? "" : titleText);
        body.setText(markup(description));
        layoutTooltip();
        setScale(0.85f);
        setVisible(true);
        toFront();
        clearActions();
        addAction(Actions.scaleTo(1f, 1f, POP_IN, Interpolation.swingOut));
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (!isVisible()) {
            return;
        }
        if (source == null || source.getStage() == null) {
            hide();
            return;
        }
        layoutTooltip();
    }

    /** Recalcula tamanho/posição sem reiniciar o pop-in (também usado após um resize). */
    private void layoutTooltip() {
        float width = Math.min(WIDTH, Math.max(160f, stage.getWidth() - 2f * MARGIN));
        panel.getCell(body).width(width - 2f * PAD);
        panel.pack();
        panel.setSize(width, panel.getPrefHeight());
        setSize(width, panel.getHeight());

        scratch.set(0f, 0f);
        source.localToStageCoordinates(scratch);
        TooltipPlacement.Placement placement = TooltipPlacement.place(
                scratch.x, scratch.y, source.getWidth(), source.getHeight(),
                getWidth(), getHeight(), stage.getWidth(), stage.getHeight(),
                MARGIN, GAP, POINTER_INSET);
        above = placement.above();
        pointerX = placement.pointerX();
        setPosition(placement.x(), placement.y());
        setOrigin(pointerX, above ? 0f : getHeight());
    }

    /** Esconde somente se o tooltip ainda pertencer à carta informada. */
    public void hide(Actor source) {
        if (this.source != null && source != null && this.source != source) {
            return;
        }
        this.source = null;
        clearActions();
        setVisible(false);
    }

    public void hide() {
        hide(null);
    }

    public boolean isShowingFor(Actor actor) {
        return isVisible() && source == actor;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (!isVisible()) {
            return;
        }
        float size = POINTER * 1.5f;
        float pointerY = above ? -size / 2f : getHeight() - size / 2f;
        batch.setColor(Palette.rgb(0x2a2112));
        batch.draw(assets.region("white"), pointerX - size / 2f, pointerY,
                size / 2f, size / 2f, size, size, 1f, 1f, 45f);
        batch.setColor(Color.WHITE);
        super.draw(batch, parentAlpha);
    }

    private String markup(String text) {
        StringBuilder out = new StringBuilder();
        for (TooltipText.Span span : TooltipText.colorize(text)) {
            Color color = colorFor(span.color());
            out.append("[#").append(hex(color)).append(']')
                    .append(escape(span.text())).append("[]");
        }
        return out.toString();
    }

    private static Color colorFor(TooltipColor color) {
        return switch (color) {
            case CHIPS -> Palette.CHIPS;
            case MULT -> Palette.MULT;
            case SPADE -> Palette.SPADE;
            case HEART -> Palette.HEART;
            case DIAMOND -> Palette.DIAMOND;
            case CLUB -> Palette.CLUB;
            default -> Palette.INK;
        };
    }

    private static String hex(Color color) {
        return String.format("%02x%02x%02x",
                Math.round(color.r * 255f), Math.round(color.g * 255f),
                Math.round(color.b * 255f));
    }

    /** Escapa o marcador de cor do BitmapFont. */
    private static String escape(String text) {
        return text.replace("[", "[[");
    }
}
