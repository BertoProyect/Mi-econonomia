package com.bertoproject.mieconomia;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.SizeF;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Widgets de la pantalla de inicio («Mi Economía», «Saldo», «Gráfica» y «Apuntar»).
 *
 * La web manda una foto de los datos (JSON, contrato v1) con MiEconomiaNativo.widget(json).
 * Aquí se guarda y se dibujan todos los widgets. Cada widget elige su disposición según
 * el tamaño real que le da el lanzador (vertical y horizontal), así que vale cualquier
 * tamaño, desde 1×1 hasta pantalla completa.
 */
public final class Widgets {
  private static final String TAG = "MiEconomiaWidget";
  static final String PREFS = "mieconomia_widget";
  static final String CLAVE = "foto";

  static final int COMPLETO = 0, SALDO = 1, GRAFICA = 2, APUNTAR = 3;
  private static final Class<?>[] PROVEEDORES = {
    WidgetCompleto.class, WidgetSaldo.class, WidgetGrafica.class, WidgetApuntar.class
  };

  // Disposiciones del panel (layout widget_panel)
  private static final int P_SALDO = 0;          // solo saldo
  private static final int P_SALDO_GRAFICA = 1;  // saldo encima, gráfica debajo
  private static final int P_CABECERA = 2;       // saldo y botones redondos arriba, gráfica debajo
  private static final int P_COMPLETO = 3;       // saldo, gráfica y botones Gasto/Ingreso abajo
  private static final int P_FILA = 4;           // saldo | botones (widgets bajos y anchos)
  private static final int P_FILA_GRAFICA = 5;   // saldo | gráfica | botones
  private static final int P_GRAFICA = 6;        // gráfica (con el cambio a 30 días si cabe)

  // Un código de petición distinto para cada acción
  private static final int RC_APP = 7101, RC_RAPIDO = 7102, RC_GASTO = 7103, RC_INGRESO = 7104;

  private static final float BOTONES_FILA = 36 + 8 + 36 + 10; // dp: dos botones redondos y su margen
  private static final float LINEA = 12 * 1.2f + 3;           // dp: línea de texto pequeña con su margen

  private Widgets() {}

  // ───────────────────────── Datos ─────────────────────────

  /** Guarda la foto de datos y refresca todos los widgets. Devuelve false si el JSON no vale. */
  public static synchronized boolean guardar(Context ctx, String json) {
    if (ctx == null || json == null || json.length() > 200000) return false;
    try {
      JSONObject o = new JSONObject(json);
      if (o.optInt("v", 0) < 1) return false;
    } catch (Exception e) {
      return false;
    }
    Context app = ctx.getApplicationContext();
    SharedPreferences p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    if (json.equals(p.getString(CLAVE, null))) return true;
    p.edit().putString(CLAVE, json).apply();
    refrescarTodos(app);
    return true;
  }

  /** Vuelve a dibujar todos los widgets puestos en la pantalla de inicio. */
  public static void refrescarTodos(Context ctx) {
    AppWidgetManager m;
    try {
      m = AppWidgetManager.getInstance(ctx);
    } catch (Exception e) {
      return;
    }
    if (m == null) return;
    for (int t = 0; t < PROVEEDORES.length; t++) {
      int[] ids;
      try {
        ids = m.getAppWidgetIds(new ComponentName(ctx, PROVEEDORES[t]));
      } catch (Exception e) {
        continue;
      }
      if (ids == null) continue;
      for (int id : ids) actualizar(ctx, m, id, t, null);
    }
  }

  static final class Datos {
    boolean hay;
    String saldo = "";
    String cambio = "";
    double cambioNum;
    double[] serie = new double[0];
    final List<String> extra = new ArrayList<>();

    static Datos leer(Context c) {
      Datos d = new Datos();
      String s = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CLAVE, null);
      if (s == null) return d;
      try {
        JSONObject o = new JSONObject(s);
        d.saldo = texto(o, "disponibleTexto");
        if (d.saldo.isEmpty() && o.has("disponible") && !o.isNull("disponible")) {
          d.saldo = String.format(new Locale("es", "ES"), "%,.2f €", o.optDouble("disponible", 0));
        }
        d.cambioNum = finito(o.optDouble("cambio30", 0));
        d.cambio = texto(o, "cambio30Texto");
        if (d.cambioNum > 0.005 && !d.cambio.isEmpty() && !d.cambio.startsWith("+")) d.cambio = "+" + d.cambio;
        JSONArray a = o.optJSONArray("serie");
        if (a != null && a.length() > 0) {
          int ini = Math.max(0, a.length() - 400);
          double[] v = new double[a.length() - ini];
          int n = 0;
          double ultimo = Double.NaN;
          for (int i = ini; i < a.length(); i++) {
            double x = a.optDouble(i, Double.NaN);
            if (Double.isNaN(x) || Double.isInfinite(x)) x = ultimo;
            if (Double.isNaN(x)) continue;
            v[n++] = x;
            ultimo = x;
          }
          if (n == 1) d.serie = new double[] { v[0], v[0] };
          else if (n > 1) {
            d.serie = new double[n];
            System.arraycopy(v, 0, d.serie, 0, n);
          }
        }
        JSONObject ah = o.optJSONObject("ahorro");
        if (ah != null) {
          String t = texto(ah, "saldoTexto");
          if (!t.isEmpty()) {
            String nombre = texto(ah, "nombre");
            d.extra.add((nombre.isEmpty() ? "Caja fuerte" : nombre) + " " + t);
          }
        }
        String deben = texto(o, "meDebenTexto");
        if (!deben.isEmpty() && finito(o.optDouble("meDeben", 0)) > 0.005) d.extra.add("Te deben " + deben);
        d.hay = !d.saldo.isEmpty();
      } catch (Exception e) {
        d.hay = false;
      }
      return d;
    }

    private static String texto(JSONObject o, String k) {
      if (o == null || !o.has(k) || o.isNull(k)) return "";
      Object v = o.opt(k);
      return v == null ? "" : String.valueOf(v).trim();
    }

    private static double finito(double x) {
      return (Double.isNaN(x) || Double.isInfinite(x)) ? 0 : x;
    }
  }

  // ───────────────────────── Tamaños ─────────────────────────

  static void actualizar(Context ctx, AppWidgetManager m, int id, int tipo, Bundle opciones) {
    try {
      Datos d = Datos.leer(ctx);
      Bundle op = opciones != null ? opciones : m.getAppWidgetOptions(id);
      List<float[]> tam = tamanos(ctx, m, id, op);
      // Memoria de las gráficas repartida entre las variantes (límite de bitmaps de los widgets).
      float maxPx = Math.min(650000f, 1200000f / tam.size());
      RemoteViews rv;
      if (tam.size() == 1) {
        rv = construir(ctx, tipo, d, tam.get(0)[0], tam.get(0)[1], maxPx);
      } else if (Build.VERSION.SDK_INT >= 31) {
        // Android 12+: una vista por cada tamaño real; el sistema elige la que toca.
        Map<SizeF, RemoteViews> mapa = new LinkedHashMap<>();
        for (float[] s : tam) {
          SizeF k = new SizeF(s[0], s[1]);
          if (!mapa.containsKey(k)) mapa.put(k, construir(ctx, tipo, d, s[0], s[1], maxPx));
        }
        rv = mapa.size() == 1 ? mapa.values().iterator().next() : new RemoteViews(mapa);
      } else {
        // Antes de Android 12: [0] = en vertical, [1] = en horizontal.
        RemoteViews vertical = construir(ctx, tipo, d, tam.get(0)[0], tam.get(0)[1], maxPx);
        RemoteViews horizontal = construir(ctx, tipo, d, tam.get(1)[0], tam.get(1)[1], maxPx);
        rv = new RemoteViews(horizontal, vertical);
      }
      m.updateAppWidget(id, rv);
    } catch (Throwable e) {
      Log.w(TAG, "No se pudo dibujar el widget " + id, e);
    }
  }

  /** Tamaños reales del widget en dp: [ancho, alto]. */
  private static List<float[]> tamanos(Context ctx, AppWidgetManager m, int id, Bundle op) {
    List<float[]> r = new ArrayList<>();
    if (op != null && Build.VERSION.SDK_INT >= 31) {
      try {
        ArrayList<SizeF> s = op.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
        if (s != null) {
          for (SizeF z : s) {
            if (z != null && z.getWidth() > 0 && z.getHeight() > 0 && r.size() < 8) {
              r.add(new float[] { z.getWidth(), z.getHeight() });
            }
          }
        }
      } catch (Exception e) {
        r.clear();
      }
      if (!r.isEmpty()) return r;
    }
    if (op != null) {
      int minW = op.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
      int maxW = op.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0);
      int minH = op.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
      int maxH = op.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
      if (minW > 0 && maxH > 0) r.add(new float[] { minW, maxH });
      if (maxW > 0 && minH > 0) r.add(new float[] { maxW, minH });
    }
    if (r.isEmpty()) {
      float w = 180, h = 110;
      try {
        AppWidgetProviderInfo info = m.getAppWidgetInfo(id);
        float dens = ctx.getResources().getDisplayMetrics().density;
        if (info != null && info.minWidth > 0 && info.minHeight > 0) {
          w = info.minWidth / dens;
          h = info.minHeight / dens;
        }
      } catch (Exception e) {
        // nos quedamos con el tamaño por defecto
      }
      r.add(new float[] { w, h });
    }
    return r;
  }

  /** Elige la disposición de cada widget según su tamaño (dp). */
  private static RemoteViews construir(Context c, int tipo, Datos d, float w, float h, float maxPx) {
    switch (tipo) {
      case SALDO:
        return panel(c, d, w, h, P_SALDO, true, maxPx);
      case GRAFICA:
        return panel(c, d, w, h, P_GRAFICA, false, maxPx);
      case APUNTAR:
        if (w < 100) return h >= 96 ? botones(c, w, h, true) : mas(c);
        if (h >= 130 && w < 200) return botones(c, w, h, true);
        return botones(c, w, h, false);
      default:
        if (w < 100) return h >= 150 ? botones(c, w, h, true) : mas(c);
        if (h < 130) return panel(c, d, w, h, w < 200 ? P_SALDO : (w < 330 ? P_FILA : P_FILA_GRAFICA), false, maxPx);
        if (h >= 250 || (h >= 190 && w >= 170)) return panel(c, d, w, h, P_COMPLETO, false, maxPx);
        if (w >= 170) return panel(c, d, w, h, P_CABECERA, false, maxPx);
        return panel(c, d, w, h, P_SALDO_GRAFICA, false, maxPx);
    }
  }

  private static float relleno(float w, float h) {
    float m = Math.min(w, h);
    return m < 64 ? 6 : (m < 90 ? 10 : (m < 140 ? 14 : 16));
  }

  // ───────────────────────── Disposiciones ─────────────────────────

  private static RemoteViews panel(Context c, Datos d, float w, float h, int modo, boolean grande, float maxPx) {
    Medidas md = new Medidas(c);
    RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_panel);
    float pad = relleno(w, h);
    float iw = Math.max(1, w - 2 * pad), ih = Math.max(1, h - 2 * pad);
    int p = md.px(pad);
    v.setViewPadding(android.R.id.background, p, p, p, p);

    boolean gAbajo = modo == P_SALDO_GRAFICA || modo == P_CABECERA || modo == P_COMPLETO || modo == P_GRAFICA;
    boolean gFila = modo == P_FILA_GRAFICA;
    boolean bFila = modo == P_CABECERA || modo == P_FILA || modo == P_FILA_GRAFICA;
    boolean bAbajo = modo == P_COMPLETO;
    boolean conSaldo = modo != P_GRAFICA;
    boolean hayCambio = d.hay && !d.cambio.isEmpty();

    float anchoTexto = iw - (bFila ? BOTONES_FILA : 0) - (gFila ? Math.max(64, iw * 0.3f) + 18 : 0);
    String textoSaldo = d.hay ? d.saldo : c.getString(R.string.widget_abre);
    String sufijo = c.getString(R.string.widget_30d);

    boolean etq = false, extra = false, filaVisible;
    float tam = 0, altoCab = 0, anchoBloque = 0;
    CharSequence txtCambio = null;
    String txtExtra = null;

    if (conSaldo) {
      boolean cambio;
      float maxTam;
      if (modo == P_SALDO) {
        etq = d.hay && ih >= 62;
        cambio = hayCambio && ih >= 34;
        extra = d.hay && !d.extra.isEmpty() && ih >= 100;
        float otros = (etq ? 16 : 0) + (cambio ? LINEA : 0) + (extra ? LINEA - 1 : 0);
        maxTam = Math.min(grande ? 46 : 36, (ih - otros) / 1.2f);
      } else if (modo == P_FILA || modo == P_FILA_GRAFICA) {
        etq = d.hay && ih >= 66;
        cambio = hayCambio && ih >= 36;
        float otros = (etq ? 16 : 0) + (cambio ? LINEA : 0);
        maxTam = Math.min(30, (ih - otros) / 1.2f);
      } else {
        etq = d.hay && ih >= (modo == P_COMPLETO ? 230 : 160);
        cambio = hayCambio;
        extra = d.hay && !d.extra.isEmpty() && ih >= 300;
        maxTam = Math.max(20, Math.min(32, ih * 0.16f));
      }
      if (d.hay) tam = md.ajustar(md.medio, textoSaldo, anchoTexto, maxTam, 9);
      else tam = md.ajustar(md.normal, textoSaldo, anchoTexto, Math.max(9, Math.min(16, maxTam)), 9);
      anchoBloque = md.ancho(d.hay ? md.medio : md.normal, textoSaldo, tam);
      if (etq) anchoBloque = Math.max(anchoBloque, md.ancho(md.normal, c.getString(R.string.widget_disponible), 12));
      if (cambio) {
        boolean conSufijo = md.ancho(md.normal, d.cambio + sufijo, 12) <= anchoTexto * 0.95f;
        txtCambio = textoCambio(d, conSufijo ? sufijo : "", md);
        anchoBloque = Math.max(anchoBloque, md.ancho(md.normal, txtCambio.toString(), 12));
      }
      if (extra) {
        for (String parte : d.extra) {
          String prueba = txtExtra == null ? parte : txtExtra + " · " + parte;
          if (txtExtra == null || md.ancho(md.normal, prueba, 12) <= anchoTexto * 0.95f) txtExtra = prueba;
          else break;
        }
        extra = txtExtra != null;
        if (extra) anchoBloque = Math.max(anchoBloque, Math.min(anchoTexto, md.ancho(md.normal, txtExtra, 12)));
      }
      altoCab = (etq ? 16 : 0) + tam * 1.2f + (cambio ? LINEA : 0) + (extra ? LINEA - 1 : 0);
      filaVisible = true;
    } else {
      // Widget «Gráfica»: arriba solo el cambio a 30 días, si hay sitio.
      if (ih >= 70 && iw >= 80) {
        if (hayCambio) {
          boolean conSufijo = md.ancho(md.normal, d.cambio + sufijo, 12) <= iw * 0.95f;
          txtCambio = textoCambio(d, conSufijo ? sufijo : "", md);
        } else if (!d.hay) {
          txtCambio = c.getString(R.string.widget_abre);
        }
      }
      filaVisible = txtCambio != null;
      altoCab = filaVisible ? LINEA : 0;
    }

    float anchoGraf = 0, altoGraf = 0;
    if (gAbajo) {
      anchoGraf = iw;
      altoGraf = ih - (bAbajo ? 44 + 12 : 0) - (filaVisible ? Math.max(altoCab, bFila ? 36 : 0) + 10 : 0);
      if (altoGraf < 22) gAbajo = false; // demasiado baja: mejor sin gráfica
    }
    if (gFila) {
      anchoGraf = iw - anchoBloque - 18 - BOTONES_FILA;
      altoGraf = ih;
      if (anchoGraf < 40) gFila = false;
    }

    v.setViewVisibility(R.id.w_fila, filaVisible ? View.VISIBLE : View.GONE);
    v.setViewPadding(R.id.w_fila, 0, 0, 0, filaVisible && gAbajo ? md.px(10) : 0);
    v.setViewVisibility(R.id.w_etiqueta, etq ? View.VISIBLE : View.GONE);
    v.setViewVisibility(R.id.w_saldo, conSaldo ? View.VISIBLE : View.GONE);
    if (conSaldo) {
      v.setTextViewText(R.id.w_saldo, textoSaldo);
      v.setTextViewTextSize(R.id.w_saldo, TypedValue.COMPLEX_UNIT_DIP, tam);
      v.setTextColor(R.id.w_saldo, d.hay ? md.texto : md.texto2);
    }
    v.setViewVisibility(R.id.w_cambio, txtCambio != null ? View.VISIBLE : View.GONE);
    if (txtCambio != null) v.setTextViewText(R.id.w_cambio, txtCambio);
    v.setViewVisibility(R.id.w_extra, extra ? View.VISIBLE : View.GONE);
    if (extra) v.setTextViewText(R.id.w_extra, txtExtra);
    v.setViewVisibility(R.id.w_grafica_fila, gFila ? View.VISIBLE : View.GONE);
    v.setViewVisibility(R.id.w_hueco, bFila && !gFila ? View.VISIBLE : View.GONE);
    v.setViewVisibility(R.id.w_botones_fila, bFila ? View.VISIBLE : View.GONE);
    v.setViewVisibility(R.id.w_grafica, gAbajo ? View.VISIBLE : View.GONE);
    v.setViewVisibility(R.id.w_botones, bAbajo ? View.VISIBLE : View.GONE);
    if (gAbajo) v.setImageViewBitmap(R.id.w_grafica, grafica(md, d, anchoGraf, altoGraf, maxPx));
    if (gFila) v.setImageViewBitmap(R.id.w_grafica_fila, grafica(md, d, anchoGraf, altoGraf, maxPx));
    if (bAbajo) {
      boolean etiquetas = (iw - 8) / 2 >= 24 + md.ancho(md.medio, c.getString(R.string.widget_ingreso), 14) + 10;
      v.setViewVisibility(R.id.w_gasto_t, etiquetas ? View.VISIBLE : View.GONE);
      v.setViewVisibility(R.id.w_ingreso_t, etiquetas ? View.VISIBLE : View.GONE);
    }

    v.setOnClickPendingIntent(android.R.id.background, abrirApp(c));
    PendingIntent gasto = apuntar(c, "gasto"), ingreso = apuntar(c, "ingreso");
    v.setOnClickPendingIntent(R.id.w_gasto, gasto);
    v.setOnClickPendingIntent(R.id.w_ingreso, ingreso);
    v.setOnClickPendingIntent(R.id.w_gasto_r, gasto);
    v.setOnClickPendingIntent(R.id.w_ingreso_r, ingreso);
    return v;
  }

  /** Botones Gasto e Ingreso (en fila o en columna); con texto si caben. */
  private static RemoteViews botones(Context c, float w, float h, boolean vertical) {
    Medidas md = new Medidas(c);
    RemoteViews v = new RemoteViews(c.getPackageName(), vertical ? R.layout.widget_botones_v : R.layout.widget_botones_h);
    float pad = relleno(w, h);
    float iw = Math.max(1, w - 2 * pad), ih = Math.max(1, h - 2 * pad);
    float padV, anchoPastilla;
    if (vertical) {
      float alto = Math.max(1, Math.min(52, (ih - 8) / 2));
      padV = Math.max(pad, (h - 2 * alto - 8) / 2);
      anchoPastilla = iw;
    } else {
      float alto = Math.min(52, ih);
      padV = Math.max(pad, (h - alto) / 2);
      anchoPastilla = (iw - 8) / 2;
    }
    boolean etiquetas = anchoPastilla >= 24 + md.ancho(md.medio, c.getString(R.string.widget_ingreso), 14) + 10;
    v.setViewPadding(android.R.id.background, md.px(pad), md.px(padV), md.px(pad), md.px(padV));
    v.setViewVisibility(R.id.w_gasto_t, etiquetas ? View.VISIBLE : View.GONE);
    v.setViewVisibility(R.id.w_ingreso_t, etiquetas ? View.VISIBLE : View.GONE);
    v.setOnClickPendingIntent(android.R.id.background, abrirApp(c));
    v.setOnClickPendingIntent(R.id.w_gasto, apuntar(c, "gasto"));
    v.setOnClickPendingIntent(R.id.w_ingreso, apuntar(c, "ingreso"));
    return v;
  }

  /** 1×1: un botón + que abre la ventana para apuntar. */
  private static RemoteViews mas(Context c) {
    RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_mas);
    PendingIntent pi = apuntar(c, null);
    v.setOnClickPendingIntent(R.id.w_mas, pi);
    v.setOnClickPendingIntent(android.R.id.background, pi);
    return v;
  }

  private static CharSequence textoCambio(Datos d, String sufijo, Medidas md) {
    SpannableStringBuilder sb = new SpannableStringBuilder(d.cambio);
    int col = d.cambioNum > 0.005 ? md.positivo : (d.cambioNum < -0.005 ? md.negativo : md.texto2);
    sb.setSpan(new ForegroundColorSpan(col), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    sb.append(sufijo);
    return sb;
  }

  // ───────────────────────── Acciones ─────────────────────────

  private static int banderas() {
    int f = PendingIntent.FLAG_UPDATE_CURRENT;
    if (Build.VERSION.SDK_INT >= 23) f |= PendingIntent.FLAG_IMMUTABLE;
    return f;
  }

  /** Abre la app completa. */
  static PendingIntent abrirApp(Context c) {
    Intent i = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
    if (i == null) i = new Intent(c, MainActivity.class);
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    return PendingIntent.getActivity(c, RC_APP, i, banderas());
  }

  /** Abre la ventana rápida: tipo "gasto", "ingreso" o null (sin elegir). */
  static PendingIntent apuntar(Context c, String tipo) {
    Intent i = new Intent(c, QuickAddActivity.class);
    i.setAction(c.getPackageName() + (tipo == null ? ".APUNTAR" : ".APUNTAR_" + tipo));
    if (tipo != null) i.putExtra(QuickAddActivity.EXTRA_TIPO, tipo);
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    int rc = tipo == null ? RC_RAPIDO : ("ingreso".equals(tipo) ? RC_INGRESO : RC_GASTO);
    return PendingIntent.getActivity(c, rc, i, banderas());
  }

  // ───────────────────────── Gráfica ─────────────────────────

  /**
   * Dibuja la línea del saldo (interpolación monótona: nunca pasa del máximo ni del mínimo
   * reales), un relleno muy suave y un punto en el día de hoy. Sin ejes.
   * El tamaño del bitmap está acotado para no pasar el límite de memoria de los widgets.
   */
  private static Bitmap grafica(Medidas md, Datos d, float wDp, float hDp, float maxPx) {
    wDp = Math.max(4, wDp);
    hDp = Math.max(4, hDp);
    float esc = md.dens;
    if (wDp * esc > 1100f) esc = 1100f / wDp;
    if (hDp * esc > 700f) esc = 700f / hDp;
    if (wDp * hDp * esc * esc > maxPx) esc = (float) Math.sqrt(maxPx / (wDp * hDp));
    int bw = Math.max(4, Math.round(wDp * esc)), bh = Math.max(4, Math.round(hDp * esc));
    Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
    b.setDensity(Math.max(1, Math.round(160f * esc))); // así mide wDp × hDp en pantalla
    Canvas cv = new Canvas(b);
    float grosor = Math.max(1f, 2f * esc);
    float radio = 3f * esc;
    double[] y = d.serie;

    if (!d.hay || y.length < 2) {
      Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(Math.max(1f, 1.5f * esc));
      p.setColor(md.texto3);
      p.setPathEffect(new DashPathEffect(new float[] { 4 * esc, 5 * esc }, 0));
      float yy = bh * 0.6f;
      cv.drawLine(grosor, yy, bw - grosor, yy, p);
      return b;
    }

    int n = y.length;
    double lo = y[0], hi = y[0];
    for (double x : y) {
      if (x < lo) lo = x;
      if (x > hi) hi = x;
    }
    double rango = hi - lo;
    float izq = grosor, der = Math.max(izq + 1, bw - radio - grosor * 1.5f);
    float arriba = radio + grosor;
    float abajo = Math.max(arriba + 1, bh - Math.max(radio + grosor, bh * 0.12f));
    float k = rango < 1e-9 ? 0 : (float) ((abajo - arriba) / rango);
    float paso = (der - izq) / (n - 1);
    float[] xs = new float[n], ys = new float[n];
    for (int i = 0; i < n; i++) {
      xs[i] = izq + paso * i;
      ys[i] = rango < 1e-9 ? (arriba + abajo) / 2f : (float) (abajo - (y[i] - lo) * k);
    }
    double[] m = tangentes(y);
    Path linea = new Path();
    linea.moveTo(xs[0], ys[0]);
    for (int i = 0; i < n - 1; i++) {
      linea.cubicTo(
        xs[i] + paso / 3f, ys[i] - (float) (m[i] / 3.0) * k,
        xs[i + 1] - paso / 3f, ys[i + 1] + (float) (m[i + 1] / 3.0) * k,
        xs[i + 1], ys[i + 1]);
    }

    Path relleno = new Path(linea);
    relleno.lineTo(xs[n - 1], bh);
    relleno.lineTo(xs[0], bh);
    relleno.close();
    Paint pr = new Paint(Paint.ANTI_ALIAS_FLAG);
    pr.setStyle(Paint.Style.FILL);
    pr.setShader(new LinearGradient(0, arriba, 0, bh, conAlfa(md.acento, 0.18f), conAlfa(md.acento, 0f), Shader.TileMode.CLAMP));
    cv.drawPath(relleno, pr);

    Paint pl = new Paint(Paint.ANTI_ALIAS_FLAG);
    pl.setStyle(Paint.Style.STROKE);
    pl.setStrokeWidth(grosor);
    pl.setStrokeCap(Paint.Cap.ROUND);
    pl.setStrokeJoin(Paint.Join.ROUND);
    pl.setColor(md.acento);
    cv.drawPath(linea, pl);

    Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG);
    pp.setStyle(Paint.Style.FILL);
    pp.setColor(md.fondo);
    cv.drawCircle(xs[n - 1], ys[n - 1], radio + grosor * 0.9f, pp);
    pp.setColor(md.acento);
    cv.drawCircle(xs[n - 1], ys[n - 1], radio, pp);
    return b;
  }

  /** Pendientes de Fritsch–Carlson: curva suave y monótona entre puntos (sin picos falsos). */
  static double[] tangentes(double[] y) {
    int n = y.length;
    double[] m = new double[n];
    if (n < 2) return m;
    double[] d = new double[n - 1];
    for (int i = 0; i < n - 1; i++) d[i] = y[i + 1] - y[i];
    m[0] = d[0];
    m[n - 1] = d[n - 2];
    for (int i = 1; i < n - 1; i++) m[i] = d[i - 1] * d[i] <= 0 ? 0 : (d[i - 1] + d[i]) / 2;
    for (int i = 0; i < n - 1; i++) {
      if (d[i] == 0) {
        m[i] = 0;
        m[i + 1] = 0;
        continue;
      }
      double a = m[i] / d[i], b = m[i + 1] / d[i];
      if (a < 0) {
        m[i] = 0;
        a = 0;
      }
      if (b < 0) {
        m[i + 1] = 0;
        b = 0;
      }
      double s = a * a + b * b;
      if (s > 9) {
        double t = 3 / Math.sqrt(s);
        m[i] = t * a * d[i];
        m[i + 1] = t * b * d[i];
      }
    }
    return m;
  }

  private static int conAlfa(int color, float a) {
    return (Math.round(a * 255) << 24) | (color & 0x00FFFFFF);
  }

  // ───────────────────────── Medidas y colores ─────────────────────────

  private static final class Medidas {
    final float dens;
    final Paint medio, normal;
    final int texto, texto2, texto3, acento, positivo, negativo, fondo;

    Medidas(Context c) {
      dens = c.getResources().getDisplayMetrics().density;
      medio = new Paint(Paint.ANTI_ALIAS_FLAG);
      medio.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
      medio.setLetterSpacing(-0.01f);
      normal = new Paint(Paint.ANTI_ALIAS_FLAG);
      normal.setTypeface(Typeface.DEFAULT);
      texto = color(c, R.color.widget_texto);
      texto2 = color(c, R.color.widget_texto2);
      texto3 = color(c, R.color.widget_texto3);
      acento = color(c, R.color.widget_acento);
      positivo = color(c, R.color.widget_positivo);
      negativo = color(c, R.color.widget_negativo);
      fondo = color(c, R.color.widget_fondo);
    }

    int px(float dp) {
      return Math.round(dp * dens);
    }

    /** Ancho en dp de un texto a un tamaño en dp. */
    float ancho(Paint p, String s, float tamDp) {
      p.setTextSize(tamDp * 10f);
      return p.measureText(s) / 10f;
    }

    /** Mayor tamaño (entre min y max) con el que el texto cabe en el ancho dado. */
    float ajustar(Paint p, String s, float anchoDp, float maxDp, float minDp) {
      if (maxDp < minDp) maxDp = minDp;
      float a = ancho(p, s, maxDp);
      float limite = anchoDp * 0.94f;
      if (a <= limite) return maxDp;
      float t = maxDp * limite / Math.max(a, 0.01f);
      return Math.max(minDp, Math.min(maxDp, (float) Math.floor(t * 2) / 2f));
    }

    @SuppressWarnings("deprecation")
    private static int color(Context c, int id) {
      return Build.VERSION.SDK_INT >= 23 ? c.getColor(id) : c.getResources().getColor(id);
    }
  }
}
