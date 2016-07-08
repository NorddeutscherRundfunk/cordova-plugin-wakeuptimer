package org.nypr.cordova.wakeupplugin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;

public class WakeupClickActivity extends Activity {

    private static final String TAG = "WakeupClickActivity";

    /**
     * Called when local notification was clicked to launch the main intent.
     *
     * @param state
     *      Saved instance state
     */
    @Override
    public void onCreate (Bundle state) {
        super.onCreate(state);

        Log.d(TAG,"notification click received");

        WakeupPlugin.fireEvent("clickedOnAlarmNotification");

    }


}
