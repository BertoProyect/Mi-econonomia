package com.bertoproject.mieconomia;

import android.app.Activity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Puente de la app principal con la web.
 *
 * Se expone de dos formas (las dos hacen lo mismo):
 *  - window.MiEconomiaNativo.widget(jsonTexto): interfaz Java registrada en load(), que Capacitor
 *    llama ANTES de cargar la web, así que existe desde la primera carga de la página.
 *  - Capacitor.Plugins.MiEconomia.widget({ json: jsonTexto }) (devuelve una promesa).
 */
@CapacitorPlugin(name = "MiEconomia")
public class MiEconomiaPlugin extends Plugin {

  @Override
  public void load() {
    WebView w = getBridge().getWebView();
    if (w != null) w.addJavascriptInterface(new Puente(), "MiEconomiaNativo");
  }

  @PluginMethod
  public void widget(PluginCall call) {
    String json = call.getString("json");
    if (json == null) {
      JSObject datos = call.getData();
      if (datos != null && datos.has("v")) json = datos.toString();
    }
    JSObject r = new JSObject();
    r.put("ok", Widgets.guardar(getContext(), json));
    call.resolve(r);
  }

  /** Lo que ve la web como window.MiEconomiaNativo dentro de la app principal. */
  public class Puente {
    /** Guarda la foto de datos (contrato v1 de SPEC.md) y refresca los widgets. */
    @JavascriptInterface
    public boolean widget(String json) {
      return Widgets.guardar(getContext(), json);
    }

    /** La app principal no es la ventana rápida (en QuickAddActivity devuelve true). */
    @JavascriptInterface
    public boolean esRapido() {
      return false;
    }

    @JavascriptInterface
    public String version() {
      return QuickAddActivity.VERSION;
    }

    /** Por compatibilidad con la web: si la app principal se abrió en #rapido, vuelve a Inicio. */
    @JavascriptInterface
    public void cerrar() {
      salirDeRapido();
    }

    @JavascriptInterface
    public void abrirApp() {
      salirDeRapido();
    }
  }

  private void salirDeRapido() {
    final Activity a = getActivity();
    final WebView w = getBridge().getWebView();
    if (a == null || w == null) return;
    a.runOnUiThread(() -> w.evaluateJavascript(
      "(function(){if(/^#rapido/.test(location.hash)){location.replace(location.href.split('#')[0]+'#inicio');location.reload();}})()",
      null));
  }
}
