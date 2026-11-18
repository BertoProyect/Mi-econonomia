// Genera capacitor.config.json con la URL de tu web (variable APP_URL).
const url = process.env.APP_URL;
if (!url || !/^https:\/\//.test(url)) { console.error('Falta APP_URL (https://...) en Settings → Variables de GitHub'); process.exit(1); }
require('fs').writeFileSync('capacitor.config.json', JSON.stringify({
  appId: 'com.bertoproject.mieconomia',
  appName: 'Mi Economía',
  webDir: 'www',
  backgroundColor: '#000000',
  server: { url, cleartext: false, androidScheme: 'https' }
}, null, 2));
console.log('capacitor.config.json listo para', url);
