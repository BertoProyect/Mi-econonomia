package com.bertoproject.mieconomia;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

/** Base de los cuatro widgets: se redibujan al ponerlos, al cambiar de tamaño y al llegar datos nuevos. */
public abstract class WidgetBase extends AppWidgetProvider {
  abstract int tipo();

  @Override
  public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
    for (int id : ids) Widgets.actualizar(c, m, id, tipo(), null);
  }

  @Override
  public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle opciones) {
    Widgets.actualizar(c, m, id, tipo(), opciones);
  }
}
