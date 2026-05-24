package com.example.skywardblocker;
import android.content.Context;
import android.content.SharedPreferences;

public class StateManager {

    private static final String PREFS_NAME = "skyward_prefs";
    private static final String KEY_APP_STATE = "app_state";

    public enum AppState {
        START_SCREEN,
        ACCESSIBILITY_SCREEN,
        DNS_SCREEN,
        //LAUNCHER_SELECTION,
        EXIT_KIOSK,
        BLOCKING,
    }

    private static final String KEY_MEMBER_ID = "member_id";

    public static void setMemberId(Context context, String memberId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_MEMBER_ID, memberId).apply();
    }

    public static String getMemberId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_MEMBER_ID, null);
    }


    public static void nextState(Context context) {
        AppState currentState = getState(context);
        AppState[] states = AppState.values();
        int currentIndex = currentState.ordinal();
        if (currentIndex >= states.length - 1) {
            setState(context, states[states.length - 1]);
            return;
        }
        AppState nextState = states[currentIndex + 1];
        setState(context, nextState);
    }

    private static void setState(Context context, AppState state) {
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        prefs.edit()
                .putString(KEY_APP_STATE, state.name())
                .apply();
    }

    public static void resetState(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        prefs.edit()
                .putString(KEY_APP_STATE, AppState.START_SCREEN.name())
                .apply();
    }

    public static AppState getState(Context context) {
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        String stateName =
                prefs.getString(KEY_APP_STATE, AppState.START_SCREEN.name());

        return AppState.valueOf(stateName);
    }

}