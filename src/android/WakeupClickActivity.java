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

import de.ndr.audioplugin.AudioPlayerService;

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

        Context localContext;

        localContext = getApplicationContext();
        Intent intent = getIntent();

        String streamurl = intent.getStringExtra("streamurl");

        Log.d(TAG,"notification click received for [" + streamurl + "]");

        startStream(localContext, streamurl);

        WakeupPlugin.fireEvent("clickedOnAlarmNotification");

    }

    private void startStream(Context context, String toBeStreamedUrl) {

        //hardcoded for testing purposes
        String playbackUrl = toBeStreamedUrl;

        Intent startIntent = new Intent(context, AudioPlayerService.class);
        startIntent.setAction(AudioPlayerService.START_SERVICE_PLAY_ACTION);
        startIntent.putExtra(AudioPlayerService.URL_EXTRA, playbackUrl);
        startIntent.putExtra(AudioPlayerService.NOTIFICATION_INTENT_CLASS_EXTRA, context.getClass().getName());
        //mFrequencyCallbackContext = callbackContext;
        context.startService(startIntent);

    }


}
