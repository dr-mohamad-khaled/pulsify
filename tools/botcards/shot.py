import asyncio, sys
from playwright.async_api import async_playwright
html = '<html><body style="margin:0;background:#000"><canvas id="c" style="width:600px;height:800px"></canvas><script>'+open('head.js').read()+open('body.js').read()+'</script></body></html>'
open('card.html','w').write(html)
jobs=[(0,'u=400&gy=.70&aR=2.4'),(1,'u=380&gy=.78&aR=2.4'),(2,'u=335&gy=.84&aR=2.3'),(3,'u=400&gy=.76&aR=2.0&expr=happy'),(4,'u=430&gy=.76&aR=2.2&expr=heart')]
async def main():
    async with async_playwright() as p:
        b = await p.chromium.launch(executable_path='/usr/bin/google-chrome', args=['--no-sandbox'])
        pg = await b.new_page(viewport={'width':600,'height':800})
        for i,q in jobs:
            await pg.goto(f'file:///tmp/botcards/card.html?bot={i}&{q}')
            await pg.wait_for_function('document.title=="done"')
            await pg.wait_for_timeout(200)
            data = await pg.evaluate("document.getElementById('c').toDataURL('image/png')")
            import base64; open(f'b{i}.png','wb').write(base64.b64decode(data.split(',')[1]))
        await b.close()
asyncio.run(main())
