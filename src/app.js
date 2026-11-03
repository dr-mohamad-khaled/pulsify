/* ================= PULSIFY 2 — app shell ================= */
const HOST=window.PulsifyHost||null;const WP=!!(HOST&&HOST.isWallpaper&&HOST.isWallpaper());
const APK_URL='https://github.com/dr-mohamad-khaled/pulsify/releases/latest/download/pulsify.apk';
function haptic(ms){try{if(WP)return;if(HOST&&HOST.haptic)HOST.haptic(ms|0);else if(navigator.vibrate)navigator.vibrate(ms)}catch(e){}}
function pollHost(){if(!HOST)return;try{Object.assign(HSTATE,JSON.parse(HOST.state()))}catch(e){}}
if(!HOST&&navigator.getBattery)navigator.getBattery().then(b=>{const f=()=>{HSTATE.battery=b.level;HSTATE.charging=b.charging};f();b.onlevelchange=b.onchargingchange=f}).catch(()=>{});
const _aeu=AE.update.bind(AE);
AE.update=function(dt,now){if(HOST&&HSTATE.music&&this.mode==='none')devAudio(dt,now);else{this.device=false;_aeu(dt,now)}};
function devAudio(dt,now){const a=AE;a.device=true;a.active=true;a.beat=false;let raw;
  const sil=HSTATE.bands&&(HSTATE.bands[0]+HSTATE.bands[1]+HSTATE.bands[2])<.03;a.silT=sil?(a.silT||0)+dt:0;
  if(HSTATE.bands&&a.silT<1.2)raw=HSTATE.bands;else{const ph=(now*118/60)%1;raw=[.35+.6*Math.exp(-ph*6),.45+.2*Math.sin(now*3),.3+.2*Math.sin(now*7)]}
  if(!a.fd||a.fd.length!==32)a.fd=new Uint8Array(32);for(let i=0;i<32;i++)a.fd[i]=HSTATE.spec?HSTATE.spec[i]|0:raw[i<8?0:i<20?1:2]*210;
  const k=Math.pow(.997,dt*60),nb=raw.map((v,i)=>{a.pk[i]=Math.max(v,a.pk[i]*k,.06);return clamp(Math.pow(v/a.pk[i],1.6),0,1)});
  a.bass=lerp(a.bass,nb[0],.55);a.mid=lerp(a.mid,nb[1],.4);a.treble=lerp(a.treble,nb[2],.5);a.level=(a.bass+a.mid+a.treble)/3;
  const e=raw[0];if(e>a.ema*1.15&&nb[0]>.55&&now-a.last>.24){a.beat=true;const d=now-a.last;if(d<1.2){a.iv.push(d);if(a.iv.length>8)a.iv.shift()}a.last=now}a.ema=lerp(a.ema,e,.08);
  if(a.iv.length>2){const s=[...a.iv].sort((x,y)=>x-y);a.bpm=clamp(60/s[s.length>>1],70,180)}else a.bpm=118;a.beatPhase=((now-a.last)*a.bpm/60)%1}
const $=s=>document.querySelector(s);
const store={get(k,d){try{const v=localStorage.getItem('pulsify.'+k);return v==null?d:JSON.parse(v)}catch(e){return d}},set(k,v){try{localStorage.setItem('pulsify.'+k,JSON.stringify(v))}catch(e){}}};
function toast(m){const t=$('#toast');t.textContent=m;t.classList.add('show');clearTimeout(toast.h);toast.h=setTimeout(()=>t.classList.remove('show'),3400)}

/* ---------- catalog ---------- */
const BUDDIES={mochi:{name:'Mochi',col:'#ff9ccf',tag:'Cream bean with a glowing antenna',make:pv=>new MochiScene(pv),hint:'Mochi looks wherever you touch. Poke it, pull it like dough, hold to pet. It dances to your music, plays on its phone, sips milk, licks ice cream (take any of them away and it cries and chases you), and watches birds with heart eyes. Try Tricks.'},
  pip:{name:'Pip',col:'#5fd0ff',tag:'Chubby bunny with floppy ears',make:pv=>new PipScene(pv),hint:'Pip looks wherever you touch. It puts its headphones on and dances when music plays, sips carrot juice while charging (take it away and it cries), and does everything Mochi does plus munching a carrot, helicopter ears, giant hops, sniffing flowers, soap bubbles, DJing and waving with its ear. Try Tricks and Specials.'},
  bolt:{name:'Bolt',col:'#5CF2E0',tag:'Hover robot with an LED face',make:pv=>new BoltScene(pv),hint:'Bolt follows your finger with its LED eyes, turns its face into an equaliser with music, hovers on a jet, and does everything Mochi does plus laser scans, glitches, reboots, rocket jumps, a magnet and cool shades. Try Tricks and Specials.'},
  arnoob:{name:'Arnoob',col:'#c8a782',tag:'Real-looking bunny',make:pv=>new ArnoobScene(pv),hint:'Arnoob is a real-looking bunny. It turns its head, ears and nose toward your finger. Hold on it to pet it (it tooth-purrs), poke it three times and it thumps, hold anywhere else to drop a carrot and it hops over to munch. On its own it grooms, washes its ears, does binkies, stands up like a periscope, digs, scratches, loafs and naps. With music its ears bob and it binkies on the drops.'}};
const BUD_IDS=Object.keys(BUDDIES);
const WORLDS=[{id:'cosmos',title:'Cosmic Odyssey',over:'Space · 5 worlds',make:pv=>new CosmicScene(pv),hint:'Swipe to spin, pinch to zoom, tap to fire a solar flare or a supernova. The atmosphere breathes with the melody.',wide:true},
  {id:'obsidian',title:'Liquid Obsidian',over:'Water · Your colours',make:pv=>new ObsidianScene(pv),hint:'Drag to draw ripples, tap to splash. Bass kicks send shockwaves across the surface. Pick colours and brightness below.'},
  {id:'gloop',title:'Gloop Gang',over:'Jelly crew · roam the screen',make:pv=>new GloopScene(pv),hint:'Grab a gloop and fling it, tap one to tickle it. Hold anywhere to drop a candy and watch them race for it. Swipe fast to scare them. They dance and sing to your music, play tag, hug, stack up and do the conga.'}];
const CARS_BUILTIN=[{id:'car_hyper',title:'Midnight Hypercar',src:'media/car.jpg',focus:[.5,.6],rig:CAR_DEFAULT_RIG}];
const LOOPS_BUILTIN=[{id:'loop_aurora',title:'Aurora World',src:'media/planet-loop.mp4',poster:'media/planet-near.jpg',glow:'#00F5D4'}];
let userCars=store.get('cars',[]),userLoops=store.get('loops',[]);
function catalog(){const L=[];for(const id of BUD_IDS)L.push({id:'b_'+id,kind:'buddy',buddy:id,title:BUDDIES[id].name,over:'Buddy',make:BUDDIES[id].make,hint:BUDDIES[id].hint});
  for(const w of WORLDS)L.push(Object.assign({kind:'world'},w));
  for(const c of[...CARS_BUILTIN,...userCars])L.push({id:c.id,kind:'car',title:c.title,over:'Cars · Code-lit',user:!c.src,cfg:c,make:pv=>new CarScene(pv,c),hint:'Tap the car to start it, tap the road for high beams, hold and drag to aim the beams. Lights strobe with the bass. Use “Edit light map” to trace the headlights of your own photos.'});
  for(const l of[...LOOPS_BUILTIN,...userLoops])L.push({id:l.id,kind:'loop',title:l.title,over:'Loop · Music glow',user:!l.src,cfg:l,make:pv=>new LoopScene(pv,l),hint:'A seamless loop that pulses and glows with the beat. Tap for a flash.'});return L}
let CATALOG=catalog();

/* ---------- snapshots (rendered once, cached) ---------- */
const SNAP_V='p3-1';let SNAPS={};try{const c=JSON.parse(localStorage.getItem('pulsify.snaps')||'{}');if(c.v===SNAP_V)SNAPS=c.s||{}}catch(e){}
function snapSave(){try{localStorage.setItem('pulsify.snaps',JSON.stringify({v:SNAP_V,s:SNAPS}))}catch(e){}}
function snapshot(it,w,h,frames){const ch=HSTATE.charging;HSTATE.charging=false;const sc=it.make(true);if(sc.isBuddy)sc.force='day';sc.resize(w,h);const holder=$('#snapHold');if(sc.attach)sc.attach(holder);sc.resize(w,h);if(sc.demo)sc.demo();const A={active:false,bass:0,mid:0,treble:0,level:0,beat:false};
  for(let i=0;i<frames;i++){dtFrame=.05;sc.update(.05,A)}const c=document.createElement('canvas');c.width=w;c.height=h;const x=c.getContext('2d');x.fillStyle='#000';x.fillRect(0,0,w,h);const oc=document.createElement('canvas');oc.width=w;oc.height=h;sc.octx=oc.getContext('2d');sc.draw(x);if(sc.gl&&sc.gl.cv)x.drawImage(sc.gl.cv,0,0,w,h);x.drawImage(oc,0,0);
  let url='';try{url=c.toDataURL('image/webp',.86)}catch(e){}if(sc.dispose)sc.dispose();HSTATE.charging=ch;return url}
async function makeSnaps(){const need=CATALOG.filter(it=>(it.kind==='buddy'||it.kind==='world')&&!SNAPS[it.id]);for(const it of need){await new Promise(r=>setTimeout(r,30));if(view!=='home'||WP)return;try{SNAPS[it.id]=snapshot(it,it.kind==='buddy'?360:420,it.kind==='buddy'?480:560,it.kind==='buddy'?26:30)}catch(e){console.warn(e)}paintThumbs()}snapSave()}
function paintThumbs(){document.querySelectorAll('[data-snap]').forEach(im=>{const s=SNAPS[im.dataset.snap];if(s&&im.src!==s)im.src=s});setHero()}

/* ---------- home ---------- */
let view='home',myBuddy=store.get('buddy','mochi');if(!BUDDIES[myBuddy])myBuddy='mochi';
const GREET={night:'Good night',morning:'Good morning',day:'Good afternoon',evening:'Good evening',late:'Good evening'};
const MOODLINE={night:'is fast asleep',morning:'just woke up',day:'is full of energy',evening:'is chilling',late:'is getting sleepy'};
function homeText(){const d=new Date(),ph=dayPhase(d);$('#greet').textContent=GREET[ph];const bat=Math.round((HSTATE.battery||1)*100);
  $('#sub').textContent=d.toLocaleDateString(undefined,{weekday:'long',day:'numeric',month:'long'})+' · '+bat+'%'+(HSTATE.charging?' charging':'');
  const B=BUDDIES[myBuddy];$('#heroName').textContent=B.name;$('#heroMood').textContent=B.tag+' — '+B.name.toLowerCase().replace(/^./,c=>c.toUpperCase())+' '+(HSTATE.charging?'is charging up':MOODLINE[ph])+'.';
  const mc=$('#musicChip'),on=AE.active;mc.classList.toggle('dim',!on);mc.classList.toggle('live',on);$('#musicTxt').textContent=AE.device?'Music playing':AE.mode==='synth'?'Test beat':AE.mode==='mic'?'Listening':AE.active?'Audio on':'Music idle'}
function setHero(){const B=BUDDIES[myBuddy];$('#heroImg').src=SNAPS['b_'+myBuddy]||'';$('#hero').style.setProperty('--hc',B.col);document.querySelectorAll('.bud').forEach(b=>b.setAttribute('aria-current',b.dataset.id===myBuddy));homeText()}
$('#hero').onclick=e=>{if(e.target.closest('#heroShuffle'))return;openStudio('b_'+myBuddy)};
$('#heroShuffle').onclick=e=>{e.stopPropagation();const pool=BUD_IDS.filter(x=>x!==myBuddy);myBuddy=pool[(Math.random()*pool.length)|0];store.set('buddy',myBuddy);setHero();haptic(8)};
const ARROW='<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.8" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14M13 6l6 6-6 6"/></svg>';
function card(it,media,wide){const el=document.createElement('button');el.className='card'+(wide?' wide':'');el.setAttribute('aria-label','Open '+it.title);
  el.innerHTML=`${media}<div class="info"><div><span class="micro">${it.over}</span><div class="ttl">${it.title}</div></div><span class="go">${ARROW}</span></div>`;el.onclick=e=>{if(e.target.closest('.del'))return;openStudio(it.id)};
  if(it.user){const d=document.createElement('span');d.className='chip dim del tag';d.textContent='Remove';d.onclick=async e=>{e.stopPropagation();if(it.kind==='car'){userCars=userCars.filter(c=>c.id!==it.id);store.set('cars',userCars)}else{userLoops=userLoops.filter(c=>c.id!==it.id);store.set('loops',userLoops)}try{await LOOP_DB.del(it.cfg.local)}catch(_){}CATALOG=catalog();renderHome()};el.appendChild(d)}return el}
function addCard(title,sub,onclick){const el=document.createElement('button');el.className='card add';el.innerHTML=`<span class="plus">+</span><b>${title}</b><small>${sub}</small>`;el.onclick=onclick;return el}
const objURLs=[];
function renderHome(){objURLs.splice(0).forEach(u=>URL.revokeObjectURL(u));
  const bw=$('#buds');bw.innerHTML='';for(const id of BUD_IDS){const B=BUDDIES[id],b=document.createElement('button');b.className='bud';b.dataset.id=id;b.style.setProperty('--c',B.col);b.innerHTML=`<img data-snap="b_${id}" alt=""><b>${B.name}</b><small>${B.tag}</small>`;b.onclick=()=>{myBuddy=id;store.set('buddy',id);setHero();openStudio('b_'+id)};bw.appendChild(b)}
  const ww=$('#worlds');ww.innerHTML='';for(const w of WORLDS)ww.appendChild(card(CATALOG.find(x=>x.id===w.id),`<img data-snap="${w.id}" alt="">`,w.wide));
  const cw=$('#cars');cw.innerHTML='';for(const it of CATALOG.filter(x=>x.kind==='car')){let src=it.cfg.src||'';const el=card(it,`<img alt="" ${src?`src="${src}"`:''}>`);cw.appendChild(el);if(it.cfg.local)LOOP_DB.get(it.cfg.local).then(b=>{if(b){const u=URL.createObjectURL(b);objURLs.push(u);el.querySelector('img').src=u}}).catch(()=>{})}
  cw.appendChild(addCard('Add a car photo','Front view on black works best. You trace its lights once.',()=>$('#carFile').click()));
  const lw=$('#loops');lw.innerHTML='';for(const it of CATALOG.filter(x=>x.kind==='loop')){const el=card(it,`<video muted loop playsinline autoplay ${it.cfg.poster?`poster="${it.cfg.poster}"`:''} ${it.cfg.src?`src="${it.cfg.src}"`:''}></video>`);lw.appendChild(el);if(it.cfg.local)LOOP_DB.get(it.cfg.local).then(b=>{if(b){const u=URL.createObjectURL(b);objURLs.push(u);const v=el.querySelector('video');v.src=u;v.play().catch(()=>{})}}).catch(()=>{})}
  lw.appendChild(addCard('Add a loop video','An MP4 whose first frame matches its last.',()=>$('#loopFile').click()));
  paintThumbs()}
$('#carFile').onchange=async e=>{const f=e.target.files[0];e.target.value='';if(!f)return;const id='car_'+Date.now();try{await LOOP_DB.put(id,f)}catch(err){toast('Could not save that photo on this device');return}
  userCars.push({id,title:(f.name.replace(/\.[^.]+$/,'')||'My car').slice(0,28),local:id,focus:[.5,.6]});store.set('cars',userCars);CATALOG=catalog();renderHome();openStudio(id);setTimeout(()=>{scene.startEdit&&scene.startEdit();buildControls();toast('Drag your finger along each headlight strip, then tap Done')},300)};
$('#loopFile').onchange=async e=>{const f=e.target.files[0];e.target.value='';if(!f)return;const id='loop_'+Date.now();try{await LOOP_DB.put(id,f)}catch(err){toast('Could not save that video on this device');return}
  userLoops.push({id,title:(f.name.replace(/\.[^.]+$/,'')||'My loop').slice(0,28),local:id,glow:'#CDB4FF'});store.set('loops',userLoops);CATALOG=catalog();renderHome();openStudio(id)};

/* ---------- studio ---------- */
const stage=$('#stage'),sctx=stage.getContext('2d'),fxc=$('#fx'),fctx=fxc.getContext('2d'),ptrs=new Map();let scene=null,cur=null,dpr=1,shuffleWp=store.get('shuffleWp',false);
let SW=0,SH=0;function stageWH(){const r=$('#studio').getBoundingClientRect();return[Math.round(r.width)||innerWidth,Math.round(r.height)||innerHeight]}
function sizeStage(){[SW,SH]=stageWH();dpr=Math.min(window.devicePixelRatio||1,WP?1:1.25);for(const c of[stage,fxc]){c.width=Math.round(SW*dpr);c.height=Math.round(SH*dpr)}if(scene)scene.resize(SW,SH)}
function buildControls(){const box=$('#ctl');box.innerHTML='';const row=lab=>{const r=document.createElement('div');r.className='row';if(lab){const s=document.createElement('span');s.className='micro lbl';s.textContent=lab;r.appendChild(s)}box.appendChild(r);return r};
  const pill=(r,txt,on,fn,cls)=>{const b=document.createElement('button');b.className='pill'+(cls?' '+cls:'');b.innerHTML=txt;if(on!=null)b.setAttribute('aria-pressed',on);b.onclick=()=>fn(b);r.appendChild(b);return b};
  if(scene.isBuddy){const r=row();pill(r,'⤮ Shuffle',null,()=>{const pool=BUD_IDS.filter(x=>x!==scene.id),id=pool[(Math.random()*pool.length)|0];myBuddy=id;store.set('buddy',id);haptic(10);const f=scene.force,c=scene.simCharge;openStudio('b_'+id);scene.force=f;scene.simCharge=c;buildControls()},'acc');
    pill(r,'Shuffle on unlock',shuffleWp,b=>{shuffleWp=!shuffleWp;store.set('shuffleWp',shuffleWp);b.setAttribute('aria-pressed',shuffleWp);toast(shuffleWp?'A different buddy greets you each time the screen turns on':'Your buddy stays the same')});
    if(!HOST)pill(r,'⚡ Charging',!!scene.simCharge,b=>{scene.simCharge=!scene.simCharge;b.setAttribute('aria-pressed',scene.simCharge)});
    const r2=row('Time');const ph=[['Auto',null],['Morning','morning'],['Day','day'],['Evening','evening'],['Night','night']];const bs=[];ph.forEach(([t,v])=>bs.push(pill(r2,t,scene.force===v,b=>{scene.force=v;bs.forEach(x=>x.setAttribute('aria-pressed',x===b))})))}
  if(scene.variants){const r=row();const bs=[];scene.variants.forEach(v=>bs.push(pill(r,v,v===scene.variant,b=>{scene.setVariant(v);bs.forEach(x=>x.setAttribute('aria-pressed',x===b))})))}
  if(scene.controls)for(const c of scene.controls()){if(c.type==='swatch'){const r=row(c.label);const bs=c.items.map((it,i)=>{const b=document.createElement('button');b.className='sw';b.title=it.name;b.setAttribute('aria-label',it.name);b.setAttribute('aria-pressed',i===c.value);b.innerHTML=`<i style="background:linear-gradient(135deg,${it.cols[0]} 50%,${it.cols[1]} 50%)"></i>`;b.onclick=()=>{c.set(i);bs.forEach(x=>x.setAttribute('aria-pressed',x===b))};r.appendChild(b);return b})}
    if(c.type==='slider'){const d=document.createElement('div');d.className='slider';d.innerHTML=`<span>${c.label}</span><input type="range" min="${c.min}" max="${c.max}" step="${c.step}" value="${c.value}" aria-label="${c.label}">`;d.querySelector('input').oninput=e=>c.set(+e.target.value);box.appendChild(d)}
    if(c.type==='buttons'){const r=row(c.label);c.items.forEach(it=>pill(r,it.name,it.on==null?null:!!it.on,()=>{it.fn();buildControls()}))}}
  scene.onCtl=buildControls}
function audioUI(){const m=AE.mode;$('#synthBtn').classList.toggle('on',m==='synth');$('#fileBtn').classList.toggle('on',m==='file');$('#micBtn').classList.toggle('on',m==='mic');$('#synthBtn').querySelector('span').textContent=m==='synth'?'Stop':'Test beat';
  $('#synthBtn').querySelector('svg').innerHTML=m==='synth'?'<rect x="6" y="5" width="4" height="14"/><rect x="14" y="5" width="4" height="14"/>':'<path d="M7 4v16l13-8z"/>'}
function openStudio(id){cur=CATALOG.find(x=>x.id===id);if(!cur)return;if(scene&&scene.dispose)scene.dispose();scene=cur.make(false);scene.ptrs=ptrs;view='studio';$('#studio').hidden=false;$('#home').hidden=true;
  document.querySelectorAll('#home video').forEach(v=>v.pause());if(scene.attach)scene.attach($('#studio'));sizeStage();
  $('#sOver').textContent=cur.over;$('#sTitle').textContent=cur.title;$('#hint').textContent=cur.hint;$('#moodChip').hidden=!scene.isBuddy;
  $('#setWp').textContent=HOST?'Set as live wallpaper':'Get the Android app to set it';buildControls();audioUI();try{navigator.wakeLock&&navigator.wakeLock.request('screen').catch(()=>{})}catch(e){}}
function closeStudio(){view='home';if(scene&&scene.dispose)scene.dispose();scene=null;ptrs.clear();$('#studio').hidden=true;$('#home').hidden=false;$('#studio').classList.remove('bare');$('#showUI').hidden=true;document.querySelectorAll('#home video').forEach(v=>v.play().catch(()=>{}));setHero();setTimeout(makeSnaps,400)}
$('#back').onclick=closeStudio;$('#hide').onclick=()=>{$('#studio').classList.add('bare');$('#showUI').hidden=false};$('#showUI').onclick=()=>{$('#studio').classList.remove('bare');$('#showUI').hidden=true};
$('#setWp').onclick=()=>{if(!HOST){const a=document.createElement('a');a.href=APK_URL;a.target='_blank';a.rel='noopener';document.body.appendChild(a);a.click();a.remove();toast('Install the APK on your Android phone, then set any wallpaper from the app');return}
  const ls={};try{for(let i=0;i<localStorage.length;i++){const k=localStorage.key(i);if(k.startsWith('pulsify.')&&k!=='pulsify.snaps')ls[k]=localStorage.getItem(k)}}catch(e){}
  const cfg={id:cur.id,buddy:scene.isBuddy?scene.id:null,shuffle:scene.isBuddy?shuffleWp:false,variant:scene.variant||null,ls,ts:Date.now()};if(HOST.hasAudio&&!HOST.hasAudio())HOST.requestAudio();HOST.setWallpaper(JSON.stringify(cfg))};
addEventListener('keydown',e=>{if(e.key==='Escape'&&view==='studio'){if($('#studio').classList.contains('bare'))$('#showUI').click();else closeStudio()}});
addEventListener('resize',()=>{if(view==='studio')sizeStage()});
function pos(e){const r=stage.getBoundingClientRect();return[e.clientX-r.left,e.clientY-r.top]}
stage.addEventListener('pointerdown',e=>{if(!scene)return;try{stage.setPointerCapture(e.pointerId)}catch(_){}const[x,y]=pos(e);const p={id:e.pointerId,x,y,sx:x,sy:y,lx:x,ly:y,vx:0,vy:0,lt:performance.now(),t0:scene.t};ptrs.set(e.pointerId,p);scene.down(p)});
stage.addEventListener('pointermove',e=>{const p=ptrs.get(e.pointerId);if(!p||!scene)return;const[x,y]=pos(e),now=performance.now(),dtm=Math.max(1,now-p.lt)/1000;p.vx=lerp(p.vx,(x-p.x)/dtm,.5);p.vy=lerp(p.vy,(y-p.y)/dtm,.5);p.x=x;p.y=y;p.lt=now;scene.move(p)});
const endP=e=>{const p=ptrs.get(e.pointerId);if(!p)return;if(performance.now()-p.lt>80){p.vx=0;p.vy=0}ptrs.delete(e.pointerId);if(scene)scene.up(p)};
stage.addEventListener('pointerup',endP);stage.addEventListener('pointercancel',endP);
$('#synthBtn').onclick=()=>{if(AE.mode==='synth')AE.stopAll();else{AE.stopAll();SY.start();AE.mode='synth'}audioUI()};
$('#file').onchange=e=>{const f=e.target.files[0];if(!f)return;AE.stopAll();AE.init();AE.ctx.resume();if(!AE.el){AE.el=new Audio();AE.el.loop=true;AE.elSrc=AE.ctx.createMediaElementSource(AE.el);AE.elSrc.connect(AE.master)}
  if(AE.el.src)URL.revokeObjectURL(AE.el.src);AE.el.src=URL.createObjectURL(f);AE.el.play().then(()=>{AE.mode='file';audioUI();toast('Playing '+f.name)}).catch(()=>toast('That file could not be played. Try MP3, M4A, WAV or OGG.'));e.target.value=''};
$('#micBtn').onclick=async()=>{if(AE.mode==='mic'){AE.stopAll();audioUI();return}if(HOST){toast('The app already listens to music playing on your phone');return}
  try{AE.stopAll();AE.init();await AE.ctx.resume();const s=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:false,noiseSuppression:false,autoGainControl:false}});AE.mic=s;AE.micSrc=AE.ctx.createMediaStreamSource(s);AE.micSrc.connect(AE.an);AE.mode='mic';toast('Listening — play some music near your phone')}catch(err){toast('Microphone access was blocked')}audioUI()};
const spec=$('#spec'),spx=spec.getContext('2d');
function drawSpec(){const r=spec.getBoundingClientRect();if(!r.width)return;const W=Math.round(r.width*dpr),H=Math.round(r.height*dpr);if(spec.width!==W||spec.height!==H){spec.width=W;spec.height=H}
  spx.clearRect(0,0,W,H);const n=Math.max(12,Math.floor(r.width/7)),bw=W/n,fd=AE.fd,g=spx.createLinearGradient(0,H,0,0);g.addColorStop(0,'#9D4EDD');g.addColorStop(.6,'#CDB4FF');g.addColorStop(1,'#FFB36B');spx.fillStyle=g;
  for(let i=0;i<n;i++){let v=0;if(fd&&AE.active){const L=fd.length*.75,a=Math.floor(Math.pow(L,i/n)),b=Math.max(a+1,Math.floor(Math.pow(L,(i+1)/n)));for(let k=a;k<b&&k<fd.length;k++)v=Math.max(v,fd[k]);v/=255}else v=.04+.03*Math.sin(performance.now()/400+i*.5);
    const hh=Math.max(2*dpr,v*(H-6*dpr));spx.globalAlpha=.55+v*.45;spx.fillRect(i*bw+dpr,H-3*dpr-hh,bw-2*dpr,hh)}spx.globalAlpha=1}

/* ---------- live wallpaper mode (inside the Android WallpaperService) ---------- */
let wpCfg=null,lastOff=null,offT=0,lastUnlocks=0,lastOffs=0,wasVisible=true;
let wpRaw='';function wpConfig(){let c={};try{wpRaw=HOST.config()||'{}';c=JSON.parse(wpRaw)}catch(e){}return c}
function wpOpen(c){wpCfg=c;if(c.ls)try{for(const k in c.ls)localStorage.setItem(k,c.ls[k])}catch(e){}let id=c.id&&CATALOG.find(x=>x.id===c.id)?c.id:'b_mochi';openStudio(id);$('#studio').classList.add('bare','wp');if(c.variant&&scene.setVariant&&scene.variants)scene.setVariant(c.variant)}
let cfgT=0;function wpTick(dt){cfgT-=dt;if(HSTATE.visible&&(!wasVisible||cfgT<0)){cfgT=1;const old=wpRaw,c=wpConfig();if(wpRaw!==old)wpOpen(c)}wasVisible=HSTATE.visible;
  if(HSTATE.screenOffs!==lastOffs){lastOffs=HSTATE.screenOffs;if(scene&&scene.isBuddy&&wpCfg&&wpCfg.shuffle){const pool=BUD_IDS.filter(x=>x!==scene.id),id=pool[(Math.random()*pool.length)|0];wpOpen(Object.assign({},wpCfg,{id:'b_'+id,buddy:id}))}}
  if(HSTATE.unlocks!==lastUnlocks){lastUnlocks=HSTATE.unlocks;if(scene&&scene.onUnlock)scene.onUnlock()}
  const o=HSTATE.offset;if(lastOff==null)lastOff=o;const ov=(o-lastOff)/Math.max(dt,1e-3)*4;lastOff=o;offT-=dt;if(scene&&scene.scroll)scene.scroll(ov)}

/* ---------- main loop ---------- */
let last=performance.now(),poll=0,homeT=0,sizeT=0,specT=0,fxUsed=false;
let backT=1;function frame(now){if(WP&&view==='studio'&&scene&&!ptrs.size){const cap=scene.fpsCap?scene.fpsCap():0,minDt=cap?1000/cap-3:(AE.active&&AE.level>.02)?0:28;if(now-last<minDt){requestAnimationFrame(frame);return}}const dt=Math.min(.05,(now-last)/1000);last=now;dtFrame=dt;const t=now/1000;if(HOST&&(poll^=1))pollHost();
  if(WP&&!HSTATE.visible){requestAnimationFrame(frame);return}
  AE.update(dt,t);if(WP)wpTick(dt);
  if(view==='studio'&&scene){if((sizeT=(sizeT||0)+dt)>.5){sizeT=0;const[a,b]=stageWH();if(a!==SW||b!==SH)sizeStage()}const w=SW,h=SH;scene.update(dt,AE);let skipB=false;if(scene.backHz&&scene.isBuddy){backT+=dt;if(backT<1/scene.backHz)skipB=true;else backT=0}if(scene.isBuddy||fxUsed){fctx.setTransform(1,0,0,1,0,0);fctx.clearRect(0,0,fxc.width,fxc.height);fctx.setTransform(dpr,0,0,dpr,0,0)}fxUsed=!!scene.isBuddy;scene.octx=scene.isBuddy?fctx:null;sctx.setTransform(dpr,0,0,dpr,0,0);sctx.globalAlpha=1;sctx.globalCompositeOperation='source-over';if(!skipB){sctx.fillStyle='#000';sctx.fillRect(0,0,w,h)}sctx.save();fctx.save();scene.draw(sctx,skipB);fctx.restore();sctx.restore();
    if(!WP){if((specT=(specT||0)+dt)>.033){specT=0;drawSpec()}if(scene.isBuddy){const mc=$('#moodChip'),txt=scene.mood==='charge'?scene.chargeText:(MOOD_TEXT[scene.mood]||'');if(mc.textContent!==txt)mc.textContent=txt}if((homeT+=dt)>.5){homeT=0;audioUI()}}}
  else if(view==='home'){if((homeT+=dt)>1){homeT=0;homeText()}}
  requestAnimationFrame(frame)}
if(WP){$('#home').hidden=true;pollHost();lastUnlocks=HSTATE.unlocks;lastOffs=HSTATE.screenOffs;wpOpen(wpConfig())}
else{renderHome();setHero();setTimeout(makeSnaps,80)}
audioUI();requestAnimationFrame(frame);
