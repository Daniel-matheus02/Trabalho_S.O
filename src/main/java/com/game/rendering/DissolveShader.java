package com.game.rendering;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

import java.util.logging.Logger;

/**
 * Shader de dissolução procedural por pixel. Sem textura de ruído: o valor é derivado dos
 * texcoords com hash/value-noise. Compila uma vez e cai para um fade alfa se falhar.
 */
public final class DissolveShader {

    private static final Logger LOG = Logger.getLogger(DissolveShader.class.getName());
    private static final float EDGE_WIDTH = 0.08f;
    private static final String VERTEX = """
            attribute vec4 a_position;
            attribute vec4 a_color;
            attribute vec2 a_texCoord0;
            uniform mat4 u_projTrans;
            varying vec4 v_color;
            varying vec2 v_texCoords;
            void main() {
                v_color = a_color;
                v_texCoords = a_texCoord0;
                gl_Position = u_projTrans * a_position;
            }
            """;
    private static final String FRAGMENT = """
            #ifdef GL_ES
            precision mediump float;
            #endif
            uniform sampler2D u_texture;
            uniform float u_progress;
            uniform float u_edgeWidth;
            varying vec4 v_color;
            varying vec2 v_texCoords;

            float hash(vec2 p) {
                return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
            }
            float noise(vec2 p) {
                vec2 i = floor(p);
                vec2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                float a = hash(i);
                float b = hash(i + vec2(1.0, 0.0));
                float c = hash(i + vec2(0.0, 1.0));
                float d = hash(i + vec2(1.0, 1.0));
                return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
            }
            void main() {
                vec4 tex = texture2D(u_texture, v_texCoords) * v_color;
                if (tex.a < 0.01) discard;
                float n = noise(v_texCoords * 14.0);
                if (n < u_progress) discard;
                float t = (n - u_progress) / max(u_edgeWidth, 0.0001);
                if (t < 1.0) {
                    vec3 yellow = vec3(1.0, 0.92, 0.35);
                    vec3 orange = vec3(1.0, 0.45, 0.08);
                    vec3 red = vec3(0.55, 0.06, 0.02);
                    vec3 ash = vec3(0.16, 0.14, 0.13);
                    vec3 glow = t < 0.35 ? mix(yellow, orange, t / 0.35)
                            : t < 0.70 ? mix(orange, red, (t - 0.35) / 0.35)
                            : mix(red, ash, (t - 0.70) / 0.30);
                    gl_FragColor = vec4(glow * 1.7, tex.a);
                } else {
                    gl_FragColor = tex;
                }
            }
            """;

    private static boolean initialized;
    private static ShaderProgram program;

    private DissolveShader() {
    }

    public static boolean available() {
        initialize();
        return program != null;
    }

    private static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ShaderProgram.pedantic = false;
        ShaderProgram candidate = new ShaderProgram(VERTEX, FRAGMENT);
        if (!candidate.isCompiled()) {
            LOG.warning("DissolveShader indisponível; usando fade alfa: "
                    + candidate.getLog());
            candidate.dispose();
            return;
        }
        program = candidate;
    }

    /** Desenha a textura com a frente de dissolução em {@code progress} (0..1). */
    public static void draw(Batch batch, TextureRegion region,
                            float x, float y, float originX, float originY,
                            float width, float height, float rotation, float progress,
                            float alpha) {
        if (progress >= 1f || alpha <= 0.01f) {
            return;
        }
        if (progress <= 0f || !available()) {
            batch.setColor(1f, 1f, 1f, alpha * (progress <= 0f ? 1f : 1f - progress));
            batch.draw(region, x, y, originX, originY, width, height, 1f, 1f, rotation);
            batch.setColor(Color.WHITE);
            return;
        }
        batch.flush();
        batch.setShader(program);
        program.setUniformf("u_progress", progress);
        program.setUniformf("u_edgeWidth", EDGE_WIDTH);
        program.setUniformf("u_texture", 0);
        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(region, x, y, originX, originY, width, height, 1f, 1f, rotation);
        batch.flush();
        batch.setShader(null);
        batch.setColor(Color.WHITE);
    }

    /** Descarte explícito do programa (a vida útil normal é a da aplicação). */
    public static void dispose() {
        if (program != null) {
            program.dispose();
            program = null;
        }
        initialized = false;
    }
}
