package org.nypr.cordova.wakeupplugin;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;

public class WakeupPlugin extends CordovaPlugin {

    private static final String TAG = "WakeupPlugin";

    private static final int ID_DAYLIST_OFFSET = 10010;
    private static final int ID_ONETIME_OFFSET = 10000;
    private static final int ID_SNOOZE_OFFSET = 10001;


    public static final Map<String, Integer> daysOfWeek = new HashMap<String, Integer>() {
        private static final long serialVersionUID = 1L;

        {
            put("sunday", 0);
            put("monday", 1);
            put("tuesday", 2);
            put("wednesday", 3);
            put("thursday", 4);
            put("friday", 5);
            put("saturday", 6);
        }
    };

    public static CallbackContext connectionCallbackContext;


    public void onNewIntent(Intent intent) {
        Log.d(TAG, "onNewIntent: " + intent);
        String playStream = intent.getStringExtra("playStream");

        if (Boolean.parseBoolean(playStream)) {
            Log.d(TAG, "Starting stream from Wakeupplugin");
            WakeupPlugin.fireEvent("please start the stream :)");
        }
    }

    @Override
    public void onReset() {
        // app startup
        Log.d(TAG, "Wakeup Plugin onReset");
        //    Bundle extras = cordova.getActivity().getIntent().getExtras();
        //    if (extras!=null && !extras.getBoolean("wakeup", false)) {
        //      setAlarmsFromPrefs( cordova.getActivity().getApplicationContext() );
        //    }
        super.onReset();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        boolean ret = true;

        Log.v(TAG, "-in-> " + action + " " + ((args.length() > 0) ? args.getJSONObject(0).toString() : ""));
        try {
            if ("wakeup".equalsIgnoreCase(action)) {
                Log.d(TAG, "scheduling alarm...");
                JSONObject options = args.getJSONObject(0);

                JSONArray alarms;
                if (options.has("alarms")) {
                    alarms = options.getJSONArray("alarms");
                } else {
                    alarms = new JSONArray(); // default to empty array
                }

                saveToPrefs(cordova.getActivity().getApplicationContext(), alarms);
                setAlarms(cordova.getActivity().getApplicationContext(), alarms, true);

                WakeupPlugin.connectionCallbackContext = callbackContext;
                sendPluginResult(callbackContext, PluginResult.Status.OK, null);
            } else if ("snooze".equalsIgnoreCase(action)) {
                JSONObject options = args.getJSONObject(0);

                if (options.has("alarms")) {
                    Log.d(TAG, "scheduling snooze...");
                    JSONArray alarms = options.getJSONArray("alarms");
                    setAlarms(cordova.getActivity().getApplicationContext(), alarms, false);
                }

                WakeupPlugin.connectionCallbackContext = callbackContext;
                sendPluginResult(callbackContext, PluginResult.Status.OK, null);
            } else if ("getup".equalsIgnoreCase(action)) {
                WakeupPlugin.connectionCallbackContext = callbackContext;
                sendPluginResult(callbackContext, PluginResult.Status.OK, null);
            } else if ("cancel".equalsIgnoreCase(action)) {
                Log.d(TAG, "canceling alarm...");
                //                JSONObject options = args.getJSONObject(0);

                cancelAlarms(cordova.getActivity().getApplicationContext());

            } else {
                sendPluginResult(callbackContext, PluginResult.Status.ERROR, TAG + " error: invalid action (" + action + ")");
                ret = false;
            }
        } catch (JSONException e) {
            sendPluginResult(callbackContext, PluginResult.Status.ERROR, TAG + " error: invalid json");
            ret = false;
            Log.e(TAG, " error: invalid json", e);

        } catch (Exception e) {
            sendPluginResult(callbackContext, PluginResult.Status.ERROR, TAG + " error: " + e.getMessage());
            ret = false;
            Log.e(TAG, " error: invalid json", e);
        }
        return ret;
    }


    private void sendPluginResult(CallbackContext callbackContext, PluginResult.Status status, String message) {

        PluginResult pluginResult = (message == null) ? new PluginResult(status) : new PluginResult(status, message);
        pluginResult.setKeepCallback(true);
        callbackContext.sendPluginResult(pluginResult);
        Log.d(TAG, "<-ou- sendPluginResult() status = [" + status + "], message = [" + message + "]");
    }

    public static void setAlarmsFromPrefs(Context context) {
        try {
            SharedPreferences prefs;
            prefs = PreferenceManager.getDefaultSharedPreferences(context);
            String a = prefs.getString("alarms", "[]");
            Log.d(TAG, "setting alarms:\n" + a);
            JSONArray alarms = new JSONArray(a);
            WakeupPlugin.setAlarms(context, alarms, true);
        } catch (JSONException e) {
            Log.w(TAG, "setAlarmsFromPrefs() called with: context = [" + context + "]", e);
        }
    }

    @SuppressLint({"SimpleDateFormat", "NewApi"})
    private static void setAlarms(Context context, JSONArray alarms, boolean cancelAlarms) throws JSONException {

        if (cancelAlarms) {
            cancelAlarms(context);
        }

        for (int i = 0; i < alarms.length(); i++) {
            JSONObject alarm = alarms.getJSONObject(i);

            String type = "onetime";
            if (alarm.has("type")) {
                type = alarm.getString("type");
            }

            if (!alarm.has("time")) {
                throw new JSONException("alarm missing time: " + alarm.toString());
            }

            JSONObject time = alarm.getJSONObject("time");

            if ("onetime".equals(type)) {

                Log.d(TAG, "onetime");

                Calendar alarmDate = getOneTimeAlarmDate(time);
                Intent intent = new Intent(context, WakeupReceiver.class);
                if (alarm.has("extra")) {
                    intent.putExtra("extra", alarm.getJSONObject("extra").toString());
                    intent.putExtra("type", type);
                }

                Log.d(TAG, "Setting Notification to: " + alarmDate);

                setNotification(context, type, alarmDate, intent, ID_ONETIME_OFFSET);

            } else if ("daylist".equals(type)) {

                Log.d(TAG, "daylist");

                JSONArray days = alarm.getJSONArray("days");

                for (int j = 0; j < days.length(); j++) {
                    Calendar alarmDate = getAlarmDate(time, daysOfWeek.get(days.getString(j)));
                    Intent intent = new Intent(context, WakeupReceiver.class);
                    if (alarm.has("extra")) {
                        intent.putExtra("extra", alarm.getJSONObject("extra").toString());
                        intent.putExtra("type", type);
                        intent.putExtra("time", time.toString());
                        intent.putExtra("day", days.getString(j));
                    }

                    setNotification(context, type, alarmDate, intent, ID_DAYLIST_OFFSET + daysOfWeek.get(days.getString(j)));
                }
            } else if ("snooze".equals(type)) {

                Log.d(TAG, "snooze");

                cancelSnooze(context);
                Calendar alarmDate = getTimeFromNow(time);
                Intent intent = new Intent(context, WakeupReceiver.class);
                if (alarm.has("extra")) {
                    intent.putExtra("extra", alarm.getJSONObject("extra").toString());
                    intent.putExtra("type", type);
                }
                setNotification(context, type, alarmDate, intent, ID_SNOOZE_OFFSET);
            }
        }
    }


    private static void setNotification(Context context, String type, Calendar alarmDate, Intent intent, int id) throws JSONException {
        if (alarmDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMANY);
            Log.d(TAG, "setting alarm at " + sdf.format(alarmDate.getTime()) + "; id " + id);

            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent sender = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= 19) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmDate.getTimeInMillis(), sender);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, alarmDate.getTimeInMillis(), sender);
            }

            if (WakeupPlugin.connectionCallbackContext != null) {
                JSONObject o = new JSONObject();
                o.put("type", "set");
                o.put("alarm_type", type);
                o.put("alarm_date", alarmDate.getTimeInMillis());

                Log.d(TAG, "alarm time in millis: " + alarmDate.getTimeInMillis());

                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, o);
                pluginResult.setKeepCallback(true);
                Log.d(TAG, "<-ou- setNotification() "+ o.toString());
                WakeupPlugin.connectionCallbackContext.sendPluginResult(pluginResult);
            } else {
                Log.d(TAG, "WakeupPlugin.connectionCallbackContext==null");
            }
            listAlarms(context);
        }
    }

    private static void cancelAlarms(Context context) {
        Log.d(TAG, "canceling alarms");
        Intent intent = new Intent(context, WakeupReceiver.class);
        PendingIntent sender = PendingIntent.getBroadcast(context, ID_ONETIME_OFFSET, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Log.d(TAG, "cancelling alarm id " + ID_ONETIME_OFFSET);
        alarmManager.cancel(sender);

        cancelSnooze(context);

        for (int i = 0; i < 7; i++) {
            intent = new Intent(context, WakeupReceiver.class);
            Log.d(TAG, "cancelling alarm id " + (ID_DAYLIST_OFFSET + i));
            sender = PendingIntent.getBroadcast(context, ID_DAYLIST_OFFSET + i, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            alarmManager.cancel(sender);
        }
    }

    private static void cancelSnooze(Context context) {
        Log.d(TAG, "canceling snooze");
        Intent intent = new Intent(context, WakeupReceiver.class);
        PendingIntent sender = PendingIntent.getBroadcast(context, ID_SNOOZE_OFFSET, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Log.d(TAG, "cancelling alarm id " + ID_SNOOZE_OFFSET);
        alarmManager.cancel(sender);
    }

    private static Calendar getOneTimeAlarmDate(JSONObject time) throws JSONException {
        TimeZone defaultz = TimeZone.getDefault();
        Calendar calendar = new GregorianCalendar(defaultz);
        Calendar now = new GregorianCalendar(defaultz);
        now.setTime(new Date());
        calendar.setTime(new Date());

        int hour = (time.has("hour")) ? time.getInt("hour") : -1;
        int minute = (time.has("minute")) ? time.getInt("minute") : 0;

        if (hour >= 0) {
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            if (calendar.before(now)) {
                calendar.set(Calendar.DATE, calendar.get(Calendar.DATE) + 1);
            }
        } else {
            calendar = null;
        }

        return calendar;
    }

    private static Calendar getAlarmDate(JSONObject time, int dayOfWeek) throws JSONException {
        TimeZone defaultz = TimeZone.getDefault();
        Calendar calendar = new GregorianCalendar(defaultz);
        Calendar now = new GregorianCalendar(defaultz);
        now.setTime(new Date());
        calendar.setTime(new Date());

        int hour = (time.has("hour")) ? time.getInt("hour") : -1;
        int minute = (time.has("minute")) ? time.getInt("minute") : 0;

        if (hour >= 0) {
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            int currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK); // 1-7 = Sunday-Saturday
            currentDayOfWeek--; // make zero-based

            // add number of days until 'dayOfWeek' occurs
            int daysUntilAlarm;
            if (currentDayOfWeek > dayOfWeek) {
                // currentDayOfWeek=thursday (4); alarm=monday (1) -- add 4 days
                daysUntilAlarm = (6 - currentDayOfWeek) + dayOfWeek + 1; // (days until the end of week) + dayOfWeek + 1
            } else if (currentDayOfWeek < dayOfWeek) {
                // example: currentDayOfWeek=monday (1); dayOfWeek=thursday (4) -- add three days
                daysUntilAlarm = dayOfWeek - currentDayOfWeek;
            } else {
                if (now.after(calendar.getTime())) {
                    daysUntilAlarm = 7;
                } else {
                    daysUntilAlarm = 0;
                }
            }

            calendar.set(Calendar.DATE, now.get(Calendar.DATE) + daysUntilAlarm);
        } else {
            calendar = null;
        }

        return calendar;
    }

    private static Calendar getTimeFromNow(JSONObject time) throws JSONException {
        TimeZone defaultz = TimeZone.getDefault();
        Calendar calendar = new GregorianCalendar(defaultz);
        calendar.setTime(new Date());

        int seconds = (time.has("seconds")) ? time.getInt("seconds") : -1;

        if (seconds >= 0) {
            calendar.add(Calendar.SECOND, seconds);
        } else {
            calendar = null;
        }

        return calendar;
    }

    private static void saveToPrefs(Context context, JSONArray alarms) {
        SharedPreferences prefs;
        SharedPreferences.Editor editor;

        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        editor = prefs.edit();
        editor.putString("alarms", alarms.toString());

        Log.d(TAG, "alarms saveToPrefs() " + alarms.toString());
        // TODO SiM 13.12.17: CHECK davor war es editor.commit();
        editor.apply();
    }

    /**
     * Fire given event on JS side. Does inform all event listeners.
     *
     * @param event The event name
     */
    static void fireEvent(String event) {
        Log.d(TAG, "fireEvent() event = [" + event + "]");


        if (WakeupPlugin.connectionCallbackContext != null) {
            try {
                JSONObject parameter = new JSONObject();
                parameter.put("type", "broadcast");
                parameter.put("rootScopeBroadcast", "fireRootscopeBroadcast");

                // callback.success(parameter);
                PluginResult result = new PluginResult(PluginResult.Status.OK, parameter);
                result.setKeepCallback(true);
                Log.d(TAG, "<-ou- fireEvent() "+ parameter.toString());
                WakeupPlugin.connectionCallbackContext.sendPluginResult(result);

            } catch (JSONException e) {
                Log.e(TAG, e.toString());
            }
        }
    }


    private static void listAlarms(Context context) {
        if (Build.VERSION.SDK_INT >= 21) {
            AlarmManager mAlarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            for (AlarmManager.AlarmClockInfo aci = mAlarmManager.getNextAlarmClock();
                 aci != null;
                 aci = mAlarmManager.getNextAlarmClock()) {
                Log.d(TAG, "listAlarms() "+aci.getShowIntent().toString() +"\n"+String.format("Trigger time: %d", aci.getTriggerTime()));
            }
        }
    }

}