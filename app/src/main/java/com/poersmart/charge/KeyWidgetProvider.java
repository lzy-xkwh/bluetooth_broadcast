package com.poersmart.charge;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class KeyWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.key_widget);
            views.setOnClickPendingIntent(R.id.widget_unlock, serviceIntent(context, KeyBroadcastService.ACTION_UNLOCK, 21));
            views.setOnClickPendingIntent(R.id.widget_stop_charge, serviceIntent(context, KeyBroadcastService.ACTION_STOP_CHARGE, 22));
            views.setOnClickPendingIntent(R.id.widget_stop_broadcast, serviceIntent(context, KeyBroadcastService.ACTION_STOP_BROADCAST, 24));
            views.setOnClickPendingIntent(R.id.widget_header, PendingIntent.getActivity(context, 23,
                    new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT));
            manager.updateAppWidget(id, views);
        }
    }

    private PendingIntent serviceIntent(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, KeyBroadcastService.class);
        intent.setAction(action);
        return PendingIntent.getService(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
