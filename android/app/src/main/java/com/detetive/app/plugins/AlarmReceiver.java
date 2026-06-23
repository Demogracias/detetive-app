package com.detetive.app.plugins;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.detetive.app.MainActivity;

public class AlarmReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "noir_alarm_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        String message = intent.getStringExtra("message");
        if (message == null || message.isEmpty()) {
            message = "Ligacao da Central";
        }
        int alarmId = intent.getIntExtra("alarm_id", 0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Ligacoes Agendadas",
                NotificationManager.IMPORTANCE_HIGH
            );
            ch.setDescription("Notificacoes de ligacoes agendadas");
            ch.enableVibration(true);
            ch.setBypassDnd(true);
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }

        Intent i = new Intent(context, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        i.putExtra("focus_tab", "detective");

        PendingIntent pi = PendingIntent.getActivity(
            context, alarmId, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("? LIGACAO DA CENTRAL")
            .setContentText(message)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(2000 + alarmId, b.build());
        }

        SchedulingPlugin.removeAlarm(context, alarmId);
    }
}
