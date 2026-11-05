package com.game.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Disposable;
import com.game.core.model.Suit;
import com.game.rendering.Palette;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detém todos os assets em tempo de execução: um atlas de texturas procedural, as texturas de
 * feltro/vinheta/brilho, a fonte bitmap em várias escalas inteiras e o skin do Scene2D. Tudo é
 * criado em código e liberado junto com a aplicação.
 */
public final class GameAssets implements Disposable {

    private static final int ATLAS_WIDTH = 512;
    private static final int ATLAS_HEIGHT = 512;

    private final TextureAtlas atlas;
    private final Texture atlasTexture;
    private final Texture feltTexture;
    private final Texture glowTexture;
    private final Texture vignetteTexture;
    private final Map<String, NinePatch> patches = new HashMap<>();
    private final Map<Integer, BitmapFont> fonts = new HashMap<>();
    private final Skin skin;

    public GameAssets() {
        Pixmap atlasPixmap = new Pixmap(ATLAS_WIDTH, ATLAS_HEIGHT, Pixmap.Format.RGBA8888);
        atlasPixmap.setBlending(Pixmap.Blending.None);
        atlasPixmap.setColor(0);
        atlasPixmap.fill();

        List<RegionSpec> defs = buildRegionSpecs();
        int cursorX = 2;
        int cursorY = 2;
        int rowHeight = 0;
        Map<String, int[]> rects = new LinkedHashMap<>();
        Map<String, Integer> splitsByName = new LinkedHashMap<>();
        for (RegionSpec spec : defs) {
            if (cursorX + spec.pixmap.getWidth() + 2 > ATLAS_WIDTH) {
                cursorX = 2;
                cursorY += rowHeight + 2;
                rowHeight = 0;
            }
            atlasPixmap.drawPixmap(spec.pixmap, cursorX, cursorY);
            rects.put(spec.name, new int[]{cursorX, cursorY,
                    spec.pixmap.getWidth(), spec.pixmap.getHeight()});
            splitsByName.put(spec.name, spec.splits);
            cursorX += spec.pixmap.getWidth() + 2;
            rowHeight = Math.max(rowHeight, spec.pixmap.getHeight());
            spec.pixmap.dispose();
        }

        atlasTexture = new Texture(atlasPixmap);
        atlasTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        atlas = new TextureAtlas();
        for (Map.Entry<String, int[]> entry : rects.entrySet()) {
            int[] rect = entry.getValue();
            TextureRegion region = new TextureRegion(atlasTexture, rect[0], rect[1], rect[2], rect[3]);
            atlas.addRegion(entry.getKey(), region);
            int splits = splitsByName.getOrDefault(entry.getKey(), 0);
            if (splits > 0) {
                patches.put(entry.getKey(), new NinePatch(region, splits, splits, splits, splits));
            }
        }
        atlasPixmap.dispose();

        feltTexture = new Texture(ProceduralArt.felt(256, 20261105L));
        feltTexture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        feltTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        glowTexture = new Texture(ProceduralArt.glow(128));
        glowTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        vignetteTexture = new Texture(ProceduralArt.vignette(256));
        vignetteTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        for (int scale : new int[]{1, 2, 3, 4, 5, 6, 7}) {
            fonts.put(scale, loadFont(scale));
        }

        skin = SkinFactory.build(this);
    }

    private List<RegionSpec> buildRegionSpecs() {
        List<RegionSpec> specs = new ArrayList<>();
        specs.add(spec("white", solid(4, 4, Palette.WHITE), 0));

        specs.add(spec("panelDark", ProceduralArt.roundedRect(48, 48, 10,
                Palette.PANEL, Palette.LINE, 1), 12));
        specs.add(spec("panelGreen", ProceduralArt.roundedRect(48, 48, 10,
                Palette.rgba(0x0e2a20, 0.96f), Palette.LINE, 1), 12));
        specs.add(spec("panelDeep", ProceduralArt.roundedRect(48, 48, 10,
                Palette.rgba(0x071a13, 0.95f), Palette.rgba(0x14382a, 0.9f), 1), 12));
        specs.add(spec("panelGold", ProceduralArt.roundedRect(48, 48, 10,
                Palette.rgba(0x2a2112, 0.96f), Palette.GOLD, 2), 12));

        specs.add(spec("cardFace", ProceduralArt.cardFace(72, 100), 18));
        specs.add(spec("cardBack", ProceduralArt.cardBack(72, 100), 18));
        specs.add(spec("cardFaceSmall", ProceduralArt.cardFace(48, 64), 8));
        specs.add(spec("cardBackSmall", ProceduralArt.cardBack(48, 64), 8));

        specs.add(spec("buttonGold", ProceduralArt.roundedRect(48, 48, 11,
                Palette.GOLD, Palette.GOLD_DARK, 3), 14));
        specs.add(spec("buttonGreen", ProceduralArt.roundedRect(48, 48, 11,
                Palette.rgb(0x1f5240), Palette.rgb(0x0d2d21), 2), 14));
        specs.add(spec("buttonDanger", ProceduralArt.roundedRect(48, 48, 11,
                Palette.rgb(0x7a2b22), Palette.rgb(0x4a1812), 2), 14));
        specs.add(spec("buttonGhost", ProceduralArt.roundedRect(48, 48, 11,
                Palette.rgba(0x1f5240, 0.35f), Palette.rgba(0x2f6b54, 0.9f), 1), 14));

        specs.add(spec("pillWhite", ProceduralArt.roundedRect(40, 28, 14, Palette.WHITE,
                Palette.WHITE, 0), 12));
        specs.add(spec("barBg", ProceduralArt.roundedRect(32, 16, 8,
                Palette.rgba(0x000000, 0.55f), Palette.rgba(0x000000, 0.0f), 0), 7));

        for (Suit suit : Suit.values()) {
            specs.add(spec("suit-" + suit.code(), ProceduralArt.suitIcon(suit), 0));
        }
        specs.add(spec("star", ProceduralArt.starIcon(), 0));
        specs.add(spec("crown", ProceduralArt.crownIcon(), 0));

        return specs;
    }

    private static RegionSpec spec(String name, Pixmap pixmap, int splits) {
        return new RegionSpec(name, pixmap, splits);
    }

    private static Pixmap solid(int width, int height, com.badlogic.gdx.graphics.Color color) {
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        return pixmap;
    }

    private static BitmapFont loadFont(int scale) {
        BitmapFont font = new BitmapFont(Gdx.files.internal("assets/fonts/pixel.fnt"), false);
        font.getRegion().getTexture().setFilter(Texture.TextureFilter.Nearest,
                Texture.TextureFilter.Nearest);
        font.getData().setScale(scale);
        font.setUseIntegerPositions(true);
        return font;
    }

    public BitmapFont font(int scale) {
        BitmapFont font = fonts.get(scale);
        if (font == null) {
            throw new IllegalArgumentException("Nenhuma fonte na escala " + scale);
        }
        return font;
    }

    public NinePatch patch(String name) {
        NinePatch patch = patches.get(name);
        if (patch == null) {
            throw new IllegalArgumentException("Nenhum nine-patch chamado " + name);
        }
        return patch;
    }

    public TextureRegion region(String name) {
        TextureRegion region = atlas.findRegion(name);
        if (region == null) {
            throw new IllegalArgumentException("Nenhuma região do atlas chamada " + name);
        }
        return region;
    }

    public Texture felt() {
        return feltTexture;
    }

    public Texture glow() {
        return glowTexture;
    }

    public Texture vignette() {
        return vignetteTexture;
    }

    public TextureAtlas atlas() {
        return atlas;
    }

    public Skin skin() {
        return skin;
    }

    public Texture atlasTexture() {
        return atlasTexture;
    }

    @Override
    public void dispose() {
        skin.dispose();
        for (BitmapFont font : fonts.values()) {
            font.dispose();
        }
        fonts.clear();
        atlas.dispose();
        atlasTexture.dispose();
        feltTexture.dispose();
        glowTexture.dispose();
        vignetteTexture.dispose();
    }

    private record RegionSpec(String name, Pixmap pixmap, int splits) {
    }
}
