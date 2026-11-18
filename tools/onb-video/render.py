import asyncio, sys, subprocess, os, shutil
from playwright.async_api import async_playwright
MODE=sys.argv[1]  # preview | full
FPS=30
async def main():
  async with async_playwright() as p:
    b=await p.chromium.launch(executable_path='/usr/bin/google-chrome',args=['--no-sandbox','--font-render-hinting=none'])
    pg=await b.new_page(viewport={'width':720,'height':720},device_scale_factor=1)
    await pg.goto('file:///home/claude/onb-video/scenes.html'); await pg.wait_for_timeout(300)
    dur=await pg.evaluate('DUR')
    if MODE=='preview':
      for n in range(1,6):
        for t in (1.0,3.0,4.8):
          await pg.evaluate(f'render({n},{t})'); await pg.screenshot(path=f'prev-{n}-{t}.png')
    else:
      for n in range(1,6):
        d=f'frames/{n}'; shutil.rmtree(d,ignore_errors=True); os.makedirs(d)
        T=float(dur[str(n)]); N=int(round(T*FPS))
        for i in range(N):
          await pg.evaluate(f'render({n},{i/FPS})'); await pg.screenshot(path=f'{d}/f{i:04d}.png')
        # póster: fotograma con la escena completa
        await pg.evaluate(f'render({n},{T-1.0})'); await pg.screenshot(path=f'{d}/poster.png')
        print('escena',n,N,'fotogramas',flush=True)
    await b.close()
asyncio.run(main())
