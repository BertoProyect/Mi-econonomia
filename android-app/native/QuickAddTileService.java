package com.bertoproject.mieconomia;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Botón «Apuntar» en los ajustes rápidos (donde están el wifi y el bluetooth). */
public class QuickAddTileService extends TileService {
  @Override public void onStartListening() {
    Tile t = getQsTile();
    if (t == null) return;
    t.setState(Tile.STATE_INACTIVE);
    t.setLabel("Apuntar");
    if (Build.VERSION.SDK_INT >= 29) t.setSubtitle("Mi Economía");
    t.updateTile();
  }

  @Override public void onClick() {
    if (isLocked()) unlockAndRun(this::abrir); else abrir();
  }

  @SuppressWarnings("deprecation")
  private void abrir() {
    Intent i = new Intent(this, QuickAddActivity.class);
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    if (Build.VERSION.SDK_INT >= 34) {
      PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
      startActivityAndCollapse(pi);
    } else {
      startActivityAndCollapse(i);
    }
  }
}
