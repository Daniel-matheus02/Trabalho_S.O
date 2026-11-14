package com.game.networking;

import com.game.client.network.GameClient;
import com.game.core.model.Card;
import com.game.core.state.GamePhase;
import com.game.core.state.GameSnapshot;
import com.game.net.protocol.ActionPayload;
import com.game.net.protocol.SessionInfoPayload;
import com.game.server.ServerConfig;
import com.game.server.network.GameServer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TempRerollTest {
    @Test
    void rerollOverNetwork() throws Exception {
        GameServer server = new GameServer(new ServerConfig("127.0.0.1", 0, 4, 2, 30, 30));
        server.start();
        GameClient ana = new GameClient();
        GameClient beto = new GameClient();
        try {
            ana.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);
            SessionInfoPayload info = ana.createSession("Ana").get(5, TimeUnit.SECONDS);
            beto.connect("127.0.0.1", server.boundPort()).get(5, TimeUnit.SECONDS);
            beto.joinSession(info.sessionCode(), "Beto", null).get(5, TimeUnit.SECONDS);
            GameSnapshot s = await(ana, st -> st.phase() == GamePhase.BUILD, 5000);
            ana.sendAction(ActionPayload.play(codes(s.player(1).hand().subList(0, 2))));
            s = await(beto, st -> st.phase() == GamePhase.BUILD && st.currentPlayer() == 2, 5000);
            beto.sendAction(ActionPayload.play(codes(s.player(2).hand().subList(0, 2))));
            await(ana, st -> st.phase() == GamePhase.SABOTAGE, 5000);
            ana.sendAction(ActionPayload.passSabotage());
            beto.sendAction(ActionPayload.passSabotage());
            await(ana, st -> st.phase() == GamePhase.REVEAL, 5000);
            ana.sendAction(ActionPayload.advance());
            beto.sendAction(ActionPayload.advance());
            GameSnapshot shop = await(ana, st -> st.phase() == GamePhase.SHOP, 5000);
            List<String> before = shop.player(1).offers();
            for (int i = 0; i < 4; i++) {
                var me = ana.latestSnapshot().player(1);
                if (me.rerolls() == 0 && me.coins() < me.nextRerollCost()) break;
                long version = me.hashCode() * 0L + ana.latestSnapshot().version();
                int coinsBefore = me.coins();
                ana.sendAction(ActionPayload.reroll());
                final long v = version;
                GameSnapshot after = await(ana, st -> st.version() > v, 5000);
                Thread.sleep(80);
                after = ana.latestSnapshot();
                com.game.client.network.ClientEvent ev;
                while ((ev = ana.pollEvent()) != null) {
                    System.out.println("[Temp] event " + ev);
                }
                System.out.println("[Temp] reroll " + i + " offers=" + after.player(1).offers()
                        + " left=" + after.player(1).rerolls() + " coins=" + after.player(1).coins()
                        + " cost=" + after.player(1).nextRerollCost() + " wasCoins=" + coinsBefore);
            }
            assertNotEquals(before, ana.latestSnapshot().player(1).offers());
            assertEquals(3, ana.latestSnapshot().player(1).offers().size());
        } finally {
            ana.close();
            beto.close();
            server.close();
        }
    }

    private static List<String> codes(List<Card> cards) {
        return cards.stream().map(Card::code).toList();
    }

    private static GameSnapshot await(GameClient client, Predicate<GameSnapshot> p, long ms) {
        long end = System.currentTimeMillis() + ms;
        GameSnapshot last = null;
        while (System.currentTimeMillis() < end) {
            last = client.latestSnapshot();
            if (last != null && p.test(last)) return last;
            try { Thread.sleep(15); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        throw new AssertionError("timeout; last=" + last);
    }
}
