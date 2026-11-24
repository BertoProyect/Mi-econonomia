// Añade al AndroidManifest el botón de ajustes rápidos, la ventana flotante y los widgets.
const fs = require('fs');
const p = 'android/app/src/main/AndroidManifest.xml';
let m = fs.readFileSync(p, 'utf8');
if (!m.includes('QuickAddTileService')) {
  const add = `
        <activity android:name=".QuickAddActivity" android:theme="@style/QuickAddTheme" android:exported="false"
            android:excludeFromRecents="true" android:taskAffinity="com.bertoproject.mieconomia.rapido"
            android:launchMode="singleTask" android:windowSoftInputMode="adjustResize" />
        <service android:name=".QuickAddTileService" android:exported="true" android:label="Apuntar"
            android:icon="@drawable/ic_quick_add" android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
            <intent-filter><action android:name="android.service.quicksettings.action.QS_TILE" /></intent-filter>
            <meta-data android:name="android.service.quicksettings.ACTIVE_TILE" android:value="false" />
        </service>
`;
  m = m.replace('</application>', add + '    </application>');
}
// Widgets de la pantalla de inicio: clase Java → recurso (xml/widget_<r>_info, string widget_<r>_nombre)
const widgets = [['WidgetCompleto', 'completo'], ['WidgetSaldo', 'saldo'], ['WidgetGrafica', 'grafica'], ['WidgetApuntar', 'apuntar']];
if (!m.includes('WidgetCompleto')) {
  const add = widgets.map(([cls, r]) => `
        <receiver android:name=".${cls}" android:exported="false" android:label="@string/widget_${r}_nombre">
            <intent-filter><action android:name="android.appwidget.action.APPWIDGET_UPDATE" /></intent-filter>
            <meta-data android:name="android.appwidget.provider" android:resource="@xml/widget_${r}_info" />
        </receiver>`).join('') + '\n';
  m = m.replace('</application>', add + '    </application>');
}
fs.writeFileSync(p, m);
for (const [cls] of widgets) if (!m.includes('.' + cls + '"')) { console.error('Falta el widget ' + cls + ' en el manifiesto'); process.exit(1); }
console.log('Manifest listo (ajustes rápidos, ventana rápida y ' + widgets.length + ' widgets)');
