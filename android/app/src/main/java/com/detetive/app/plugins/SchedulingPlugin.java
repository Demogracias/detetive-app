package com.detetive.app.plugins;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

@CapacitorPlugin(name = "Scheduling")
public class SchedulingPlugin extends Plugin {

    private static final String PREFS_NAME = "noir_scheduled_alarms";
    private static final String KEY_ALARMS = "alarms";
    private static final String CHANNEL_ID = "noir_scheduling_channel";

    @PluginMethod
    public void scheduleAlarm(PluginCall call) {
        String dateStr = call.getString("date", "");
        String timeStr = call.getString("time", ""); // HH:MM
        String message = call.getString("message", "");

        if (dateStr.isEmpty() || timeStr.isEmpty()) {
            call.reject("date and time required");
            return;
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            Date alarmDate = sdf.parse(dateStr + " " + timeStr);
            if (alarmDate == null || alarmDate.before(new Date())) {
                call.reject("Data deve ser no futuro");
                return;
            }

            long alarmMillis = alarmDate.getTime();
            int alarmId = (int) (alarmMillis % Integer.MAX_VALUE);

            Context ctx = getContext();
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) {
                call.reject("AlarmManager unavailable");
                return;
            }

            Intent intent = new Intent(ctx, AlarmReceiver.class);
            intent.putExtra("message", message);
            intent.putExtra("alarm_id", alarmId);
            intent.setAction("com.detetive.app.ALARM_TRIGGER");

            PendingIntent pi = PendingIntent.getBroadcast(
                ctx, alarmId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!am.canScheduleExactAlarms()) {
                    Intent settingsIntent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(settingsIntent);
                    call.reject("Permissao SCHEDULE_EXACT_ALARM necessaria");
                    return;
                }
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmMillis, pi);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmMillis, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, alarmMillis, pi);
            }

            saveAlarm(ctx, alarmId, dateStr, timeStr, message);

            JSObject ret = new JSObject();
            ret.put("alarmId", alarmId);
            ret.put("scheduled", true);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Erro ao agendar: " + e.getMessage());
        }
    }

    @PluginMethod
    public void cancelAlarm(PluginCall call) {
        int alarmId = call.getInt("alarmId", -1);
        if (alarmId < 0) {
            call.reject("alarmId invalido");
            return;
        }

        Context ctx = getContext();
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            Intent intent = new Intent(ctx, AlarmReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(
                ctx, alarmId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            am.cancel(pi);
            pi.cancel();
        }

        removeAlarm(ctx, alarmId);
        call.resolve(new JSObject());
    }

    @PluginMethod
    public void getAllScheduled(PluginCall call) {
        Context ctx = getContext();
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_ALARMS, "[]");
        JSObject ret = new JSObject();
        try {
            ret.put("alarms", new JSONArray(json));
        } catch (Exception e) {
            ret.put("alarms", new JSONArray());
        }
        call.resolve(ret);
    }

    public static void saveAlarm(Context ctx, int alarmId, String date, String time, String message) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_ALARMS, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            JSONObject obj = new JSONObject();
            obj.put("alarmId", alarmId);
            obj.put("date", date);
            obj.put("time", time);
            obj.put("message", message);
            arr.put(obj);
            prefs.edit().putString(KEY_ALARMS, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static void removeAlarm(Context ctx, int alarmId) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_ALARMS, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            JSONArray newArr = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (obj.getInt("alarmId") != alarmId) {
                    newArr.put(obj);
                }
            }
            prefs.edit().putString(KEY_ALARMS, newArr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static JSONArray getAllAlarms(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_ALARMS, "[]");
        try {
            return new JSONArray(json);
        } catch (Exception e) {
            return new JSONArray();
        }
    }
}
