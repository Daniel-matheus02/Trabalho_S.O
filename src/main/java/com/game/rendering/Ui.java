package com.game.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.game.assets.GameAssets;

/** Pequenos auxiliares de fábrica para manter as telas declarativas. */
public final class Ui {

    private Ui() {
    }

    public static Label label(Skin skin, String text, String style) {
        return new Label(text == null ? "" : text, skin, style);
    }

    public static Label wrapLabel(Skin skin, String text, String style) {
        Label label = label(skin, text, style);
        label.setWrap(true);
        label.setAlignment(Align.left);
        return label;
    }

    public static TextButton button(Skin skin, String text, String style, Runnable action) {
        TextButton button = new TextButton(text, skin, style);
        if (action != null) {
            button.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                @Override
                public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                    action.run();
                }
            });
        }
        return button;
    }

    public static Table panel(GameAssets assets, String patchName) {
        Table table = new Table();
        table.setBackground(new NinePatchDrawable(new NinePatch(assets.patch(patchName))));
        return table;
    }

    public static Image image(GameAssets assets, String regionName) {
        return new Image(new TextureRegionDrawable(assets.region(regionName)));
    }

    public static Image suitIcon(GameAssets assets, com.game.core.model.Suit suit, Color color, float size) {
        Image image = new Image(new TextureRegionDrawable(assets.region("suit-" + suit.code())).tint(color));
        image.setSize(size, size);
        return image;
    }

    /** Tingir uma região para uso como drawable. */
    public static com.badlogic.gdx.scenes.scene2d.utils.Drawable tintedRegion(GameAssets assets,
                                                                             String name, Color color) {
        return new TextureRegionDrawable(assets.region(name)).tint(color);
    }

    /** Feltro em tela cheia + vinheta usados por todas as telas. */
    public static class FeltBackground extends Actor {
        private final GameAssets assets;
        private final Color tint = new Color(Palette.WHITE);

        public FeltBackground(GameAssets assets) {
            this.assets = assets;
            setTouchable(Touchable.disabled);
        }

        public void setTint(Color color) {
            tint.set(color);
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            Texture felt = assets.felt();
            batch.setColor(tint.r, tint.g, tint.b, parentAlpha);
            batch.draw(felt, getX(), getY(), getWidth(), getHeight(),
                    0f, 0f, getWidth() / 256f, getHeight() / 256f);
            batch.setColor(1f, 1f, 1f, parentAlpha);
            batch.draw(assets.vignette(), getX(), getY(), getWidth(), getHeight());
        }
    }

    /** Brilho suave e animado usado atrás de títulos e destaques. */
    public static class GlowActor extends Actor {
        private final Texture texture;
        private final Color color = new Color(Palette.GOLD);
        private float pulse = 1f;

        public GlowActor(Texture texture) {
            this.texture = texture;
            setTouchable(Touchable.disabled);
        }

        public void setColor(Color value) {
            color.set(value);
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            pulse += delta;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            float alpha = 0.10f + (float) (Math.sin(pulse * 1.6f) * 0.03f);
            batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,
                    com.badlogic.gdx.graphics.GL20.GL_ONE);
            batch.setColor(color.r, color.g, color.b, alpha * parentAlpha);
            batch.draw(texture, getX(), getY(), getWidth(), getHeight());
            batch.setBlendFunction(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,
                    com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);
            batch.setColor(Color.WHITE);
        }
    }

    /** Ícones de naipes flutuantes que deslizam pelos fundos dos menus. */
    public static class Ambience extends Actor {
        private final GameAssets assets;
        private final com.badlogic.gdx.utils.Array<Floater> floaters = new com.badlogic.gdx.utils.Array<>();
        private final java.util.Random random = new java.util.Random(7);
        private boolean initialised;

        public Ambience(GameAssets assets) {
            this.assets = assets;
            setTouchable(Touchable.disabled);
        }

        private void seed() {
            if (initialised) {
                return;
            }
            initialised = true;
            com.game.core.model.Suit[] suits = com.game.core.model.Suit.values();
            for (int i = 0; i < 16; i++) {
                Floater f = new Floater();
                f.suit = suits[random.nextInt(suits.length)];
                f.x = random.nextFloat();
                f.y = random.nextFloat();
                f.speed = 0.006f + random.nextFloat() * 0.012f;
                f.size = 16f + random.nextFloat() * 26f;
                f.alpha = 0.04f + random.nextFloat() * 0.07f;
                f.rotation = random.nextFloat() * 360f;
                f.spin = (random.nextFloat() - 0.5f) * 12f;
                floaters.add(f);
            }
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            seed();
            for (Floater f : floaters) {
                f.y += f.speed * delta;
                f.rotation += f.spin * delta;
                if (f.y > 1.1f) {
                    f.y = -0.1f;
                    f.x = random.nextFloat();
                }
            }
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            seed();
            for (Floater f : floaters) {
                TextureRegion region = assets.region("suit-" + f.suit.code());
                float px = getX() + f.x * getWidth();
                float py = getY() + f.y * getHeight();
                batch.setColor(Palette.INK.r, Palette.INK.g, Palette.INK.b, f.alpha * parentAlpha);
                batch.draw(region, px, py, f.size / 2f, f.size / 2f, f.size, f.size,
                        1f, 1f, f.rotation);
            }
            batch.setColor(Color.WHITE);
        }

        private static final class Floater {
            com.game.core.model.Suit suit;
            float x;
            float y;
            float speed;
            float size;
            float alpha;
            float rotation;
            float spin;
        }
    }
}
