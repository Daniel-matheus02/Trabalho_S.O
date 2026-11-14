package com.game.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.game.assets.GameAssets;
import com.game.core.model.Card;
import com.game.core.model.HandType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Botão fixo e modal de referência das nove combinações de pôquer.
 *
 * <p>Vive fora do palco de jogo, como um ator de {@link Stage} criado uma única vez em
 * {@code BaseScreen}. A ordem e os pontos vêm de {@link HandType}; este componente só guarda
 * descrições e exemplos visuais.</p>
 */
public final class HandReferenceOverlay extends WidgetGroup {

    private static final Color DARK_EDGE = Palette.rgb(0x0b241b);
    private static final Color DARK_INK = Palette.rgb(0x06241a);
    private static final Color KICKER_INK = Palette.rgb(0x8d8163);

    private record Example(HandType type, String description, String[] cards, Set<String> kickers) {
    }

    private static final List<Example> EXAMPLES = List.of(
            new Example(HandType.STRAIGHT_FLUSH,
                    "5 em sequência do mesmo naipe",
                    new String[]{"9C", "8C", "7C", "6C", "5C"}, Set.of()),
            new Example(HandType.FOUR_OF_A_KIND,
                    "4 cartas iguais",
                    new String[]{"QC", "QE", "QO", "QP", "3E"}, Set.of("3E")),
            new Example(HandType.FULL_HOUSE,
                    "Trinca + par",
                    new String[]{"AC", "AE", "AO", "KE", "KC"}, Set.of()),
            new Example(HandType.FLUSH,
                    "5 do mesmo naipe",
                    new String[]{"2E", "5E", "7E", "8E", "QE"}, Set.of()),
            new Example(HandType.STRAIGHT,
                    "5 em ordem seguida",
                    new String[]{"9C", "8O", "7E", "6E", "5C"}, Set.of()),
            new Example(HandType.THREE_OF_A_KIND,
                    "3 cartas iguais",
                    new String[]{"AC", "AE", "AO", "KE", "4O"}, Set.of("KE", "4O")),
            new Example(HandType.TWO_PAIR,
                    "2 pares diferentes",
                    new String[]{"AC", "AO", "KE", "KC", "6P"}, Set.of("6P")),
            new Example(HandType.PAIR,
                    "2 cartas iguais",
                    new String[]{"AC", "AO", "8E", "5O", "3P"}, Set.of("8E", "5O", "3P")),
            new Example(HandType.HIGH_CARD,
                    "Nenhuma combinação",
                    new String[]{"AC", "JE", "8O", "5P", "2C"}, Set.of("JE", "8O", "5P", "2C"))
    );

    private static final Map<HandType, Example> BY_TYPE = new EnumMap<>(HandType.class);

    static {
        for (Example example : EXAMPLES) {
            BY_TYPE.put(example.type(), example);
        }
    }

    private final GameAssets assets;
    private final Skin skin;
    private final Stage stage;

    private final Stack stack;
    private final Table buttonLayer;
    private final Table modalLayer;
    private ScrollPane scroll;
    private TextButton openButton;
    private TextButton closeButton;
    private Cell<Table> panelCell;

    public HandReferenceOverlay(GameAssets assets, Skin skin, Stage stage) {
        this.assets = assets;
        this.skin = skin;
        this.stage = stage;
        setTransform(false);
        setTouchable(Touchable.childrenOnly);

        stack = new Stack();
        buttonLayer = buildButtonLayer();
        modalLayer = buildModalLayer();
        stack.add(buttonLayer);
        stack.add(modalLayer);
        addActor(stack);
    }

    /** Ajusta o overlay ao tamanho atual do palco (o Table interno acompanha). */
    public void setStageSize(float width, float height) {
        setSize(Math.max(1f, width), Math.max(1f, height));
        stack.setBounds(0f, 0f, getWidth(), getHeight());
        if (panelCell != null) {
            float panelWidth = Math.max(320f, Math.min(640f, getWidth() - 48f));
            float panelHeight = Math.max(320f, Math.min(620f, getHeight() - 48f));
            panelCell.width(panelWidth).height(panelHeight);
        }
        invalidateHierarchy();
    }

    public boolean isOpen() {
        return modalLayer.isVisible();
    }

    public void open() {
        if (isOpen()) {
            return;
        }
        buttonLayer.setVisible(false);
        modalLayer.setVisible(true);
        modalLayer.setTouchable(Touchable.enabled);
        scroll.setScrollY(0f);
        if (stage != null) {
            stage.setKeyboardFocus(closeButton);
        }
    }

    public void close() {
        if (!isOpen()) {
            return;
        }
        modalLayer.setVisible(false);
        modalLayer.setTouchable(Touchable.disabled);
        buttonLayer.setVisible(true);
        if (stage != null) {
            stage.setKeyboardFocus(openButton);
        }
    }

    @Override
    public void layout() {
        if (stack != null) {
            stack.setBounds(0f, 0f, getWidth(), getHeight());
        }
    }

    private Table buildButtonLayer() {
        Table layer = new Table();
        layer.setTouchable(Touchable.childrenOnly);
        layer.bottom().right().pad(18f);
        openButton = Ui.button(skin, "COMBINAÇÕES", "gold", this::open);
        layer.add(openButton).width(220f).height(46f);
        return layer;
    }

    private Table buildModalLayer() {
        Table layer = new Table();
        layer.setBackground(new TextureRegionDrawable(assets.region("white"))
                .tint(Palette.rgba(0x000000, 0.6f)));
        layer.setVisible(false);
        layer.setTouchable(Touchable.disabled);
        layer.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (event.getTarget() == layer) {
                    close();
                }
            }
        });

        Table panel = Ui.panel(assets, "panelGreen");
        panel.pad(16f).top();

        Label title = Ui.label(skin, "COMBINAÇÕES", "h2");
        title.setColor(Palette.GOLD);
        closeButton = Ui.button(skin, "X", "ghost", this::close);
        closeButton.getLabel().setAlignment(Align.center);

        Table header = new Table();
        header.add(title).left().expandX();
        header.add(closeButton).width(42f).height(36f).right();
        panel.add(header).growX().row();
        panel.add(Ui.label(skin, "DA MAIS FORTE PARA A MAIS FRACA", "small"))
                .left().padTop(2f).row();

        Table content = new Table();
        content.top();
        HandType[] types = HandType.values();
        int position = 0;
        for (int i = types.length - 1; i >= 0; i--) {
            Example example = BY_TYPE.get(types[i]);
            if (example == null) {
                continue;
            }
            position++;
            content.add(buildRow(position, example)).growX().padBottom(9f).row();
        }

        scroll = new ScrollPane(content, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        scroll.setOverscroll(false, false);
        panel.add(scroll).grow().padTop(10f).row();

        Table footerStrip = new Table();
        footerStrip.setBackground(new NinePatchDrawable(new NinePatch(assets.patch("panelDeep"))));
        footerStrip.pad(8f, 10f, 8f, 10f);
        Label footer = Ui.label(skin,
                "As fichas das cartas, jokers e modificadores somam por cima.",
                Typography.CAPTION);
        footer.setEllipsis(true);
        footerStrip.add(footer).left().growX();
        panel.add(footerStrip).growX().padTop(8f).row();

        panelCell = layer.add(panel);
        return layer;
    }

    private Table buildRow(int position, Example example) {
        HandType type = example.type();
        Table row = Ui.panel(assets, position == 1 ? "panelGold" : "panelDark");
        row.pad(5f, 10f, 5f, 10f);

        Image goldBar = new Image(new TextureRegionDrawable(assets.region("white")).tint(Palette.GOLD));
        row.add(goldBar).width(3f).growY().padRight(10f);

        row.add(Ui.label(skin, position + ".", "h3")).width(32f).padRight(6f);

        Table info = new Table();
        info.left();
        info.add(Ui.label(skin, type.displayName(), "body")).left().row();
        Label description = Ui.label(skin, example.description(), Typography.CAPTION);
        description.setEllipsis(true);
        info.add(description).width(190f).left().padTop(1f).row();
        row.add(info).width(190f).padRight(8f);

        Table cards = new Table();
        for (String code : example.cards()) {
            cards.add(refCard(code, example.kickers().contains(code)))
                    .size(44f, 60f).padRight(6f);
        }
        row.add(cards).padRight(8f);

        int points = type.baseChips() * type.baseMultiplier();
        row.add(pointsBadge(points)).width(84f);

        return row;
    }

    /**
     * Carta simplificada da tabela: apenas rank acima e um único naipe 9×9 ampliado 2×.
     * O rank e o naipe usam exatamente a mesma cor, derivada do naipe da carta.
     */
    private Table refCard(String code, boolean kicker) {
        Card card = Card.fromCode(code);
        Color ink = kicker ? KICKER_INK : suitColor(card);

        Table outline = new Table();
        outline.setBackground(new NinePatchDrawable(new NinePatch(assets.patch("pillWhite")))
                .tint(DARK_EDGE));
        outline.pad(4f);

        Table face = new Table();
        face.setBackground(new NinePatchDrawable(new NinePatch(assets.patch("cardFaceSmall"))));
        face.pad(1f);

        String rankText = card.rank().code().equals("T") ? "10" : card.rank().display();
        Label rank = Ui.label(skin, rankText, "suit");
        rank.setColor(ink);
        rank.setAlignment(Align.center);

        Image suit = new Image(new TextureRegionDrawable(
                assets.region("suit-" + card.suit().code())).tint(ink));

        face.add(rank).center().row();
        face.add(suit).size(18f, 18f).padTop(2f).center();
        outline.add(face).grow().row();

        if (!kicker) {
            Image goldBar = new Image(new TextureRegionDrawable(assets.region("white"))
                    .tint(Palette.GOLD));
            outline.add(goldBar).growX().height(2f);
        } else {
            outline.getColor().a = 0.4f;
        }
        return outline;
    }

    private Color suitColor(Card card) {
        // Mesma função central usada por todas as cartas do jogo.
        return CardRenderer.suitColor(card);
    }

    private Table pointsBadge(int points) {
        Table outline = new Table();
        outline.setBackground(new NinePatchDrawable(new NinePatch(assets.patch("pillWhite")))
                .tint(DARK_EDGE));
        outline.pad(4f);

        Table gold = new Table();
        gold.setBackground(new NinePatchDrawable(new NinePatch(assets.patch("pillWhite")))
                .tint(Palette.GOLD));
        gold.pad(3f, 8f, 3f, 8f);

        Label number = Ui.label(skin, Integer.toString(points), "h2");
        number.setColor(DARK_INK);
        Label unit = Ui.label(skin, "PTS", "white-small");
        unit.setColor(DARK_INK);
        gold.add(number).row();
        gold.add(unit).padTop(-1f).row();

        outline.add(gold).width(72f);
        return outline;
    }
}
