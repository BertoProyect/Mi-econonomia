package com.bertoproject.mieconomia;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Ventana flotante para apuntar rápido, sin abrir la app entera.
 * Carga la misma web (#rapido), así que el diseño es idéntico y se actualiza sola.
 * Comparte la sesión con la app porque usa el mismo almacenamiento.
 */
public class QuickAddActivity extends Activity {
  static final String APP_URL = "__APP_URL__";
  static final String VERSION = "__VERSION__";
  private WebView web;

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    web = new WebView(this);
    web.setBackgroundColor(Color.TRANSPARENT);
    WebSettings s = web.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDatabaseEnabled(true);
    s.setUserAgentString(s.getUserAgentString() + " MiEconomiaApp/" + VERSION + " MiEconomiaRapido");
    web.addJavascriptInterface(new Puente(), "MiEconomiaNativo");
    final String host = Uri.parse(APP_URL).getHost();
    web.setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
        Uri u = r.getUrl();
        if (host != null && host.equals(u.getHost())) return false;
        try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { }
        return true;
      }
    });
    setContentView(web, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    web.loadUrl(APP_URL + "#rapido");
  }

  class Puente {
    @JavascriptInterface public void cerrar() { runOnUiThread(QuickAddActivity.this::finish); }
    @JavascriptInterface public void abrirApp() {
      runOnUiThread(() -> {
        Intent i = new Intent(QuickAddActivity.this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
      });
    }
  }

  @Override protected void onDestroy() {
    if (web != null) { web.destroy(); web = null; }
    super.onDestroy();
  }
}
