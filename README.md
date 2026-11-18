# Mi Economía: app para Android + web del ordenador

Una sola web que se instala en el móvil como app y que se abre igual en el ordenador. Los datos viven en tu Supabase (proyecto `mi-economia`, plan gratis) y se sincronizan en tiempo real entre el móvil y el ordenador.

```
index.html            la app completa
config.js             URL y clave pública de Supabase (ya puestas)
supabase/schema.sql   tablas y permisos (ya aplicados en el proyecto)
manifest + sw.js      lo que la hace instalable como app
vendor/               supabase-js y lector de Excel (se carga solo al importar un .xlsx)
android-app/ + .github/workflows/android-apk.yml   APK opcional
```

## Qué hace
- **Saldo disponible** (Revolut + efectivo) y **pendiente** (lo que tu amigo tiene tuyo por Bizum).
- **Previsión a fin de mes**: saldo de hoy + fijos que faltan − gasto diario medio de los últimos 90 días × días que quedan. No cuenta cobros sueltos de clientes.
- **Fijos mensuales**: lo marcado como «Se repite cada mes» aparece cada mes con su estado (hecho, pendiente, toca desde el día X) y se apunta con un toque. Si viene en el extracto importado, cuenta como hecho.
- **Presupuestos con arrastre** (opcional, en Plan): lo que sobra un mes se suma al siguiente y lo que te pasas se resta.
- **Importar extractos de Revolut** en CSV o Excel (inglés o español). Salta repetidos, cambios de divisa (EXCHANGE) y operaciones pendientes; entiende fechas tipo «Mar 3, 2024» o «3 de marzo de 2024». Los pagos en otra divisa piden el importe en euros, porque Revolut no incluye el tipo de cambio en el extracto.
- Secciones por proyecto (Órbita Webs, Reseñas360, BertoProject), reglas de categoría automáticas, revisión semanal, exportar a CSV.

## Lo que falta por hacer (tú)
1. **Supabase → Authentication → Sign In / Providers → Email**: desactiva **Confirm email**. Si no, al crear la cuenta te llegará un correo con un enlace que no funciona.
2. **Vercel** → Add New → Project → importa `BertoProyect/Mi-econonomia` → **Deploy** (sin build ni ajustes). Te da tu URL.
3. Abre la URL → «Primera vez: crear cuenta» → tu email y contraseña.
4. Vuelve a Supabase → **Authentication → Sign In / Providers** → desactiva **Allow new users to sign up**. Desde ahí nadie más puede registrarse. Aunque alguien entrara, las reglas impiden ver datos ajenos.
5. En el OPPO: abre la URL en **Chrome**, entra y menú ⋮ → **Instalar aplicación**.
6. Pasar lo de la versión de Claude: allí **Ajustes → Exportar todo a CSV**; aquí **Importar** → ese archivo. Los saldos iniciales vuelves a ponerlos con «Poner saldos».

### APK (opcional)
1. GitHub → repo → **Settings → Secrets and variables → Actions → Variables** → crea `APP_URL` con tu URL de Vercel.
2. **Actions → APK Android → Run workflow**. Descarga `mi-economia-apk` en **Artifacts** e instálalo (permitir orígenes desconocidos). No está probado: la versión instalada desde Chrome es la recomendada.

## Seguridad
- La clave de `config.js` es la pública (anon): está pensada para ir en la web. Lo que protege tus datos son las reglas RLS de `schema.sql`: cada usuario solo lee y escribe lo suyo, y sin sesión no hay acceso a nada.
- El repo es público: el código se ve, tus movimientos no (viven en Supabase).
- El lector de Excel es SheetJS 0.18.5 (la versión de npm). Solo procesa archivos que eliges tú, en tu dispositivo.

## Qué no hace
- No lee Revolut sola: no hay API para cuentas personales ni Kids & Teens.
- Sin red no guarda movimientos nuevos (avisa con una barra arriba).
- El análisis con Claude solo existe en la versión de Claude.

## Publicar en Cloudflare Pages (dinero.bertoproject.com)
1. Cloudflare → Workers y Pages → Crear → Pages → Conectar a Git → `Mi-econonomia`. Sin comando de build ni directorio de salida.
2. En el proyecto: Dominios personalizados → `dinero.bertoproject.com`.
3. Supabase → Autenticación → Configuración de URL: pon `https://dinero.bertoproject.com` como URL del sitio y en las de redirección.
La app no se indexa en Google (`noindex` en la página y cabecera `X-Robots-Tag` en `_headers`), así que no afecta al SEO de bertoproject.com.

## Leer capturas con IA (Gemini)
La app manda la captura a la función `leer-captura` de tu Supabase (código en `supabase/functions/leer-captura`), que la lee con Gemini junto a tus cuentas, categorías, reglas, últimos movimientos y el texto de Ajustes → Preferencias → «Lo que la IA sabe de ti».
1. Crea una clave en https://aistudio.google.com/apikey
2. Supabase → Edge Functions → Secrets → añade `GEMINI_API_KEY` con esa clave.
3. Opcional: `GEMINI_MODEL` para fijar un modelo concreto. Si no, usa el Flash más nuevo.
En la capa gratuita, Google puede usar lo que envías para mejorar sus productos. Con la facturación activada, no.
En Android, con la app instalada: galería → Compartir → Mi Economía.
