package com.game.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/** Preferências locais persistentes (nome do jogador, movimento, visibilidade do log). */
public final class GameSettings {

    private static final String PREFERENCES_NAME = "duelo-de-fichas";

    private final Preferences preferences;
    private String playerName;
    private boolean reducedMotion;
    private boolean showProtocolLog;

    public GameSettings() {
        this.preferences = Gdx.app.getPreferences(PREFERENCES_NAME);
        this.playerName = preferences.getString("playerName", "Jogador");
        this.reducedMotion = preferences.getBoolean("reducedMotion", false);
        this.showProtocolLog = preferences.getBoolean("showProtocolLog", true);
    }

    public String playerName() {
        return playerName;
    }

    public void setPlayerName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        this.playerName = trimmed.length() > 20 ? trimmed.substring(0, 20) : trimmed;
        preferences.putString("playerName", playerName);
        preferences.flush();
    }

    public boolean reducedMotion() {
        return reducedMotion;
    }

    public void setReducedMotion(boolean reducedMotion) {
        this.reducedMotion = reducedMotion;
        preferences.putBoolean("reducedMotion", reducedMotion);
        preferences.flush();
    }

    public boolean showProtocolLog() {
        return showProtocolLog;
    }

    public void setShowProtocolLog(boolean showProtocolLog) {
        this.showProtocolLog = showProtocolLog;
        preferences.putBoolean("showProtocolLog", showProtocolLog);
        preferences.flush();
    }
}
