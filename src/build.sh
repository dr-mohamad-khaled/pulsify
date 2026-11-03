cd /home/claude/pulsify3 && python3 -c "
s=open('shell.html').read();e=''.join(open(f).read()+'\n' for f in ['core.js','buddy_base.js','pipbolt.js','mochi.js','buddies2d.js','gloop.js','scenes.js','app.js'])
open('index.html','w').write(s.replace('/*ENGINE*/',e))" && node -e "
const s=require('fs').readFileSync('index.html','utf8');const i=s.lastIndexOf('<script>');new Function(s.slice(i+8,s.lastIndexOf('</script>')));console.log('syntax ok')"
