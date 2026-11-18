// Genera capacitor.config.json. La app carga tu web en directo: cualquier cambio de la web
// llega al móvil sin reinstalar nada.
const url = (process.env.APP_URL || '').replace(/\/?$/, '/');
const version = process.env.VERSION || '1';
if (!/^https:\/\//.test(url)) { console.error('Falta APP_URL (https://...)'); process.exit(1); }
require('fs').writeFileSync('capacitor.config.json', JSON.stringify({
  appId: 'com.bertoproject.mieconomia',
  appName: 'Mi Economía',
  webDir: 'www',
  backgroundColor: '#000000',
  appendUserAgent: 'MiEconomiaApp/' + version,
  server: { url, cleartext: false, androidScheme: 'https' }
}, null, 2));
console.log('capacitor.config.json listo para', url, 'versión', version);
