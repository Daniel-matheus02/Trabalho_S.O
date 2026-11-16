package com.game.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.game.assets.GameAssets;

/**
 * Sistema de partículas leve, desenhado nas coordenadas locais do actor que o possui.
 * É usado para brasas quando a barra de vida cai e para fagulhas quando as cartas são reveladas.
 */
public final class Particles {

    private static final int MAX_PARTICLES = 150;
    private static final float FADE_IN = 0.08f;

    private final GameAssets assets;
    private final boolean additive;
    private final Array<Particle> items = new Array<>();
    private final Color tmp = new Color();

    public Particles(GameAssets assets, boolean additive) {
        this.assets = assets;
        this.additive = additive;
    }

    public void clear() {
        items.clear();
    }

    /**
     * Brasas e detritos arremessados a partir de um ponto (o bordo da vida que acabou de cair).
     *
     * @param x      posição horizontal de origem
     * @param y      posição vertical de origem
     * @param spread intensidade horizontal da explosão
     * @param count  quantidade de partículas
     */
    public void damage(float x, float y, float spread, int count) {
        TextureRegion region = assets.region("white");
        for (int i = 0; i < count; i++) {
            Particle p = obtain();
            if (p == null) {
                return;
            }
            p.region = region;
            p.x = x + MathUtils.random(-5f, 5f);
            p.y = y + MathUtils.random(-4f, 4f);
            p.vx = MathUtils.random(-spread, spread);
            p.vy = MathUtils.random(45f, 175f);
            p.gravity = -430f;
            p.drag = 1.3f;
            p.life = MathUtils.random(0.35f, 0.85f);
            p.maxLife = p.life;
            p.size = MathUtils.random(2f, 5.5f);
            p.rotation = MathUtils.random(0f, 360f);
            p.spin = MathUtils.random(-420f, 420f);
            tmp.set(Palette.BAD).lerp(Palette.GOLD, MathUtils.random(0.15f, 0.75f));
            p.color.set(tmp);
        }
    }

    /** Fagulhas douradas que sobem ao redor de um ponto (carta sendo revelada ou selecionada). */
    public void sparkles(float x, float y, float radius, int count) {
        TextureRegion region = assets.region("star");
        for (int i = 0; i < count; i++) {
            Particle p = obtain();
            if (p == null) {
                return;
            }
            float angle = MathUtils.random(0f, MathUtils.PI2);
            float distance = MathUtils.random(0f, radius);
            p.region = region;
            p.x = x + MathUtils.cos(angle) * distance;
            p.y = y + MathUtils.sin(angle) * distance * 0.6f;
            p.vx = MathUtils.random(-22f, 22f);
            p.vy = MathUtils.random(24f, 78f);
            p.gravity = -46f;
            p.drag = 1.1f;
            p.life = MathUtils.random(0.35f, 0.9f);
            p.maxLife = p.life;
            p.size = MathUtils.random(3f, 7f);
            p.rotation = MathUtils.random(0f, 360f);
            p.spin = MathUtils.random(-180f, 180f);
            tmp.set(Palette.GOLD).lerp(Palette.WHITE, MathUtils.random(0f, 0.75f));
            p.color.set(tmp);
        }
    }

    /** Brasas de fogo que sobem da área de uma carta em dissolução. */
    public void fire(float x, float y, float spreadX, float spreadY, int count) {
        TextureRegion region = assets.region("white");
        for (int i = 0; i < count; i++) {
            Particle p = obtain();
            if (p == null) {
                return;
            }
            p.region = region;
            p.x = x + MathUtils.random(-spreadX, spreadX);
            p.y = y + MathUtils.random(-spreadY, spreadY);
            p.vx = MathUtils.random(-16f, 16f);
            p.vy = MathUtils.random(28f, 95f);
            p.gravity = -22f;
            p.drag = 0.9f;
            p.life = MathUtils.random(0.35f, 0.95f);
            p.maxLife = p.life;
            p.size = MathUtils.random(2f, 5.5f);
            p.rotation = MathUtils.random(0f, 360f);
            p.spin = MathUtils.random(-180f, 180f);
            tmp.set(Palette.GOLD).lerp(Palette.MULT, MathUtils.random(0.15f, 0.9f));
            tmp.lerp(Palette.BAD, MathUtils.random(0f, 0.35f));
            p.color.set(tmp);
        }
    }

    /** Fumaça escura que sobe devagar quando as novas cartas surgem. */
    public void smoke(float x, float y, float spreadX, float spreadY, int count) {
        TextureRegion region = assets.region("white");
        for (int i = 0; i < count; i++) {
            Particle p = obtain();
            if (p == null) {
                return;
            }
            p.region = region;
            p.x = x + MathUtils.random(-spreadX, spreadX);
            p.y = y + MathUtils.random(-spreadY, spreadY);
            p.vx = MathUtils.random(-10f, 10f);
            p.vy = MathUtils.random(10f, 42f);
            p.gravity = 8f;
            p.drag = 0.6f;
            p.life = MathUtils.random(0.7f, 1.3f);
            p.maxLife = p.life;
            p.size = MathUtils.random(6f, 14f);
            p.rotation = MathUtils.random(0f, 360f);
            p.spin = MathUtils.random(-30f, 30f);
            tmp.set(Palette.FELT_DARK).lerp(Palette.INK_DIM, MathUtils.random(0f, 0.35f));
            tmp.a = MathUtils.random(0.18f, 0.38f);
            p.color.set(tmp);
        }
    }

    public void update(float delta) {
        for (int i = items.size - 1; i >= 0; i--) {
            Particle p = items.get(i);
            p.life -= delta;
            if (p.life <= 0f) {
                items.removeIndex(i);
                continue;
            }
            p.vy += p.gravity * delta;
            float damping = Math.max(0f, 1f - p.drag * delta);
            p.vx *= damping;
            p.vy *= damping;
            p.x += p.vx * delta;
            p.y += p.vy * delta;
            p.rotation += p.spin * delta;
        }
    }

    public void draw(Batch batch, float parentAlpha) {
        if (items.isEmpty()) {
            return;
        }
        if (additive) {
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        }
        for (Particle p : items) {
            float remaining = p.life / p.maxLife;
            float born = Math.min(1f, (p.maxLife - p.life) / FADE_IN);
            float alpha = p.color.a * remaining * born * parentAlpha;
            if (alpha <= 0.01f) {
                continue;
            }
            tmp.set(p.color.r, p.color.g, p.color.b, alpha);
            batch.setColor(tmp);
            batch.draw(p.region,
                    p.x - p.size / 2f, p.y - p.size / 2f,
                    p.size / 2f, p.size / 2f,
                    p.size, p.size,
                    1f, 1f, p.rotation);
        }
        batch.setColor(Color.WHITE);
        if (additive) {
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    private Particle obtain() {
        if (items.size >= MAX_PARTICLES) {
            return null;
        }
        Particle particle = new Particle();
        items.add(particle);
        return particle;
    }

    private static final class Particle {
        private TextureRegion region;
        private float x;
        private float y;
        private float vx;
        private float vy;
        private float gravity;
        private float drag;
        private float life;
        private float maxLife;
        private float size;
        private float rotation;
        private float spin;
        private final Color color = new Color(Palette.GOLD);
    }
}
