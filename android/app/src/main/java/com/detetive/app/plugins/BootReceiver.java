package com.detetive.app.plugins;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.detetive.app.MainActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        String channelId = "boot_restart";
        String channelName = "Reinicializacao";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Notificacao de reinicializacao do app apos boot");
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        Intent i = new Intent(context, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Detetive")
            .setContentText("Toque para abrir")
            .setContentIntent(pi)
            .setAutoCancel(true);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(1001, b.build());

        // Reagendar alarmes após boot
        JSONArray alarms = SchedulingPlugin.getAllAlarms(context);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        for (int j = 0; j < alarms.length(); j++) {
            try {
                JSONObject obj = alarms.getJSONObject(j);
                int alarmId = obj.getInt("alarmId");
                String date = obj.getString("date");
                String time = obj.getString("time");
                String message = obj.optString("message", "");
                Date alarmDate = sdf.parse(date + " " + time);
                if (alarmDate == null || alarmDate.before(new Date())) {
                    SchedulingPlugin.removeAlarm(context, alarmId);
                    continue;
                }
                Intent alarmIntent = new Intent(context, AlarmReceiver.class);
                alarmIntent.putExtra("message", message);
                alarmIntent.putExtra("alarm_id", alarmId);
                alarmIntent.setAction("com.detetive.app.ALARM_TRIGGER");
                PendingIntent alarmPi = PendingIntent.getBroadcast(
                    context, alarmId, alarmIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmDate.getTime(), alarmPi);
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, alarmDate.getTime(), alarmPi);
                }
            } catch (Exception ignored) {}
        }
    }
}
