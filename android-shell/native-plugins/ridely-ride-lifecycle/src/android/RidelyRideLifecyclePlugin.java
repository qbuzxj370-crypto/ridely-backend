package kr.ridely.app;

import android.content.SharedPreferences;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;

/** Records an unpaused ride's Activity destruction for the next WebView launch. */
public class RidelyRideLifecyclePlugin extends CordovaPlugin {
    private static final String PREFS = "ridely_ride_lifecycle";
    private static final String KEY_ACTIVE_UNPAUSED = "active_unpaused";
    private static final String KEY_PENDING_FORCE_END = "pending_force_end";

    private SharedPreferences prefs() {
        return cordova.getActivity().getSharedPreferences(PREFS, 0);
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if ("setActive".equals(action)) {
            prefs().edit().putBoolean(KEY_ACTIVE_UNPAUSED, args.getBoolean(0)).apply();
            callbackContext.success();
            return true;
        }
        if ("consumePendingForceEnd".equals(action)) {
            boolean pending = prefs().getBoolean(KEY_PENDING_FORCE_END, false);
            if (pending) prefs().edit().putBoolean(KEY_PENDING_FORCE_END, false).apply();
            callbackContext.success(pending ? 1 : 0);
            return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        // WebView storage is unavailable here. Persist the marker before the process exits.
        if (prefs().getBoolean(KEY_ACTIVE_UNPAUSED, false)) {
            prefs().edit().putBoolean(KEY_PENDING_FORCE_END, true).commit();
        }
        super.onDestroy();
    }
}
