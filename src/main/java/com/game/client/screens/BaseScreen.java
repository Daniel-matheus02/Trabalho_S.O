package com.game.client.screens;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.game.assets.GameAssets;
import com.game.launcher.DuelGame;
import com.game.rendering.HandReferenceOverlay;
import com.game.rendering.Palette;
import com.game.rendering.Ui;

/** Infraestrutura compartilhada de stage/viewport/plano de fundo para todas as telas. */
public abstract class BaseScreen extends ScreenAdapter {

    protected final DuelGame game;
    protected final GameAssets assets;
    protected final Skin skin;
    protected final Stage stage;
    protected final Viewport viewport;
    protected final Ui.FeltBackground background;
    private final HandReferenceOverlay referenceOverlay;

    protected BaseScreen(DuelGame game) {
        this.game = game;
        this.assets = game.assets();
        this.skin = assets.skin();
        this.viewport = new ScreenViewport();
        this.stage = new Stage(viewport);
        this.background = new Ui.FeltBackground(assets);
        this.background.setSize(Math.max(1, Gdx.graphics.getWidth()),
                Math.max(1, Gdx.graphics.getHeight()));
        stage.addActor(background);
        this.referenceOverlay = new HandReferenceOverlay(assets, skin, stage);
        this.referenceOverlay.setStageSize(Math.max(1, Gdx.graphics.getWidth()),
                Math.max(1, Gdx.graphics.getHeight()));
        stage.addActor(referenceOverlay);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
        onResize(viewport.getWorldWidth(), viewport.getWorldHeight());
        referenceOverlay.setStageSize(viewport.getWorldWidth(), viewport.getWorldHeight());
        referenceOverlay.toFront();
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(Palette.FELT_DARK.r, Palette.FELT_DARK.g, Palette.FELT_DARK.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (referenceOverlay.isOpen()) {
                referenceOverlay.close();
            } else {
                onEscape();
            }
        }
        referenceOverlay.toFront();
        stage.act(Math.min(delta, 1f / 20f));
        stage.draw();
    }

    /** Verdadeiro enquanto a tabela de combinações está aberta (pausa ticks locais). */
    protected boolean isReferenceOpen() {
        return referenceOverlay.isOpen();
    }

    /** Sobrescreva para navegação com Escape. */
    protected void onEscape() {
        // sem operação por padrão
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        // O ScreenViewport muda o mundo, mas NÃO redimensiona o ator raiz do Stage. Sem isto,
        // tabelas com setFillParent continuam no tamanho antigo e o conteúdo é cortado.
        stage.getRoot().setSize(viewport.getWorldWidth(), viewport.getWorldHeight());
        background.setSize(viewport.getWorldWidth(), viewport.getWorldHeight());
        referenceOverlay.setStageSize(viewport.getWorldWidth(), viewport.getWorldHeight());
        onResize(viewport.getWorldWidth(), viewport.getWorldHeight());
    }

    /** Chamado após a mudança do viewport; as telas posicionam decorações absolutas aqui. */
    protected void onResize(float width, float height) {
    }

    /**
     * Verificação de QA: aplica um tamanho lógico, força o layout da árvore e devolve qualquer
     * ator visível que ultrapasse as bordas da janela (os quatro lados), dois controles que se
     * sobreponham ou um controle que não receba o clique no seu próprio centro.
     */
    public List<String> probeOverflow(int width, int height) {
        resize(width, height);
        layoutTree(stage.getRoot());
        List<String> problems = new ArrayList<>();
        collectOverflow(stage.getRoot(), true, width, height, problems);
        collectOverlaps(stage.getRoot(), problems);
        collectHitProblems(problems);
        return problems;
    }

    /** QA: expõe o palco para renderização offscreen. */
    public Stage probeStage() {
        return stage;
    }

    private static void layoutTree(Actor actor) {
        if (actor instanceof WidgetGroup widgetGroup) {
            widgetGroup.validate();
        }
        if (actor instanceof Group group) {
            for (Actor child : group.getChildren()) {
                layoutTree(child);
            }
        }
    }

    private static void collectOverflow(Actor actor, boolean parentVisible, int width, int height,
                                        List<String> problems) {
        boolean visible = parentVisible && actor.isVisible() && actor.getColor().a > 0.01f;
        if (!visible) {
            return;
        }
        Vector2 position = actor.localToStageCoordinates(new Vector2());
        float left = position.x;
        float right = position.x + actor.getWidth();
        float bottom = position.y;
        float top = position.y + actor.getHeight();
        if (left < -0.5f || right > width + 0.5f || bottom < -0.5f || top > height + 0.5f) {
            if (problems.size() < 5) {
                problems.add(actor.getClass().getSimpleName()
                        + "[" + Math.round(left) + ".." + Math.round(right) + " x "
                        + Math.round(bottom) + ".." + Math.round(top) + "] fora de "
                        + width + "x" + height);
            }
        }
        if (actor instanceof Group group) {
            for (Actor child : group.getChildren()) {
                collectOverflow(child, true, width, height, problems);
            }
        }
    }

    /**
     * QA: o clique no centro de cada controle interativo precisa chegar nele — um controle coberto
     * por outro (ou fora da área clicável) é um bug de layout, não de desenho.
     */
    private void collectHitProblems(List<String> problems) {
        List<Actor> controls = new ArrayList<>();
        collectControls(stage.getRoot(), true, controls);
        for (Actor control : controls) {
            if (!control.isTouchable()) {
                continue;
            }
            Vector2 center = new Vector2(control.getWidth() / 2f, control.getHeight() / 2f);
            control.localToStageCoordinates(center);
            Actor hit = stage.hit(center.x, center.y, true);
            boolean ok = hit != null && (hit == control || isAncestor(control, hit)
                    || isAncestor(hit, control));
            if (!ok && problems.size() < 12) {
                problems.add("HIT " + control.getClass().getSimpleName()
                        + " em (" + Math.round(center.x) + "," + Math.round(center.y)
                        + ") devolve " + describeHit(hit));
            }
        }
    }

    /**
     * QA: dois controles interativos (botões e cartas) não podem ocupar a mesma área. É assim que
     * a sobreposição entre o botão COMBINAÇÕES e a barra de ações é detectada.
     */
    private static void collectOverlaps(Actor root, List<String> problems) {
        List<Actor> controls = new ArrayList<>();
        collectControls(root, true, controls);
        for (int i = 0; i < controls.size(); i++) {
            for (int j = i + 1; j < controls.size(); j++) {
                Actor a = controls.get(i);
                Actor b = controls.get(j);
                if (isAncestor(a, b) || isAncestor(b, a)) {
                    continue;
                }
                // Cartas lado a lado dentro do mesmo leque (mão/poderes) sobrepõem de propósito.
                if (sharesFan(a, b)) {
                    continue;
                }
                Vector2 pa = a.localToStageCoordinates(new Vector2());
                Vector2 pb = b.localToStageCoordinates(new Vector2());
                float overlapX = Math.min(pa.x + a.getWidth(), pb.x + b.getWidth())
                        - Math.max(pa.x, pb.x);
                float overlapY = Math.min(pa.y + a.getHeight(), pb.y + b.getHeight())
                        - Math.max(pa.y, pb.y);
                if (overlapX > 3f && overlapY > 3f) {
                    problems.add("OVERLAP " + a.getClass().getSimpleName()
                            + "(" + Math.round(pa.x) + "," + Math.round(pa.y) + ")"
                            + "/" + b.getClass().getSimpleName()
                            + "(" + Math.round(pb.x) + "," + Math.round(pb.y) + ")");
                }
            }
        }
    }

    private static void collectControls(Actor actor, boolean parentVisible, List<Actor> out) {
        boolean visible = parentVisible && actor.isVisible() && actor.getColor().a > 0.01f;
        if (!visible) {
            return;
        }
        if (actor instanceof com.badlogic.gdx.scenes.scene2d.ui.Button
                || actor instanceof com.game.rendering.HandCardActor) {
            out.add(actor);
        }
        if (actor instanceof Group group) {
            for (Actor child : group.getChildren()) {
                collectControls(child, true, out);
            }
        }
    }

    /** Verdadeiro quando os dois atores são cartas do MESMO leque (mão ou mão de poderes). */
    private static boolean sharesFan(Actor a, Actor b) {
        Actor fanA = fanOf(a);
        return fanA != null && fanA == fanOf(b);
    }

    private static Actor fanOf(Actor actor) {
        for (Actor parent = actor.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof com.game.rendering.CardHand
                    || parent instanceof com.game.rendering.PowerHand) {
                return parent;
            }
        }
        return null;
    }


    private static String describeHit(Actor hit) {
        if (hit == null) {
            return "nada";
        }
        StringBuilder text = new StringBuilder(hit.getClass().getSimpleName());
        for (Actor parent = hit.getParent(); parent != null; parent = parent.getParent()) {
            text.append('<').append(parent.getClass().getSimpleName());
        }
        return text.toString();
    }

    private static boolean isAncestor(Actor possibleAncestor, Actor actor) {
        for (Actor parent = actor.getParent(); parent != null; parent = parent.getParent()) {
            if (parent == possibleAncestor) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void hide() {
        if (Gdx.input.getInputProcessor() == stage) {
            Gdx.input.setInputProcessor(null);
        }
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
