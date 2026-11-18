// Añade al AndroidManifest el botón de ajustes rápidos y la ventana flotante.
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
  fs.writeFileSync(p, m);
}
console.log('Manifest listo');
