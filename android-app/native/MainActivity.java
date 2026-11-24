package com.bertoproject.mieconomia;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

/**
 * Sustituye al MainActivity que genera Capacitor. Registra el plugin MiEconomia antes de
 * crear el puente: así window.MiEconomiaNativo existe desde la primera carga de la web.
 */
public class MainActivity extends BridgeActivity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    registerPlugin(MiEconomiaPlugin.class);
    super.onCreate(savedInstanceState);
  }
}
