/* ===== Pulsify 2 — core: math, audio engine, synth, sfx, host state, shared WebGL ===== */

"use strict";
const TAU=Math.PI*2, rnd=(a=1,b)=>b===undefined?Math.random()*a:a+Math.random()*(b-a);
const clamp=(v,a,b)=>v<a?a:v>b?b:v, lerp=(a,b,t)=>a+(b-a)*t;
function hexA(h,a){const n=parseInt(h.slice(1),16);return `rgba(${n>>16},${n>>8&255},${n&255},${a})`}
const SPR={};
function sprite(col){if(SPR[col])return SPR[col];const c=document.createElement('canvas');c.width=c.height=64;const g=c.getContext('2d');const gr=g.createRadialGradient(32,32,0,32,32,32);gr.addColorStop(0,'rgba(255,255,255,1)');gr.addColorStop(.12,hexA(col,1));gr.addColorStop(.4,hexA(col,.35));gr.addColorStop(1,hexA(col,0));g.fillStyle=gr;g.fillRect(0,0,64,64);return SPR[col]=c}
function glow(ctx,col,x,y,s,a=1){ctx.globalAlpha=a;ctx.drawImage(sprite(col),x-s,y-s,s*2,s*2);ctx.globalAlpha=1}

/* ================= AUDIO ENGINE ================= */
const AE={ctx:null,an:null,master:null,fd:null,mode:'none',bass:0,mid:0,treble:0,level:0,beat:false,bpm:0,active:false,
  ema:0,last:0,iv:[],pk:[.2,.2,.2],raw:[0,0,0],el:null,elSrc:null,mic:null,beatPhase:0,
  init(){if(this.ctx)return;const C=window.AudioContext||window.webkitAudioContext;this.ctx=new C();
    this.an=this.ctx.createAnalyser();this.an.fftSize=2048;this.an.smoothingTimeConstant=.55;
    this.master=this.ctx.createGain();this.master.gain.value=.85;this.master.connect(this.ctx.destination);this.master.connect(this.an);
    const z=this.ctx.createGain();z.gain.value=0;this.an.connect(z);z.connect(this.ctx.destination);
    this.fd=new Uint8Array(this.an.frequencyBinCount)},
  band(lo,hi){const hz=this.ctx.sampleRate/this.an.fftSize;let a=Math.max(1,Math.floor(lo/hz)),b=Math.min(this.fd.length-1,Math.ceil(hi/hz)),s=0;for(let i=a;i<=b;i++)s+=this.fd[i];return s/((b-a+1)*255)},
  update(dt,now){
    this.beat=false;
    if(!this.ctx||this.mode==='none'){this.bass*=.9;this.mid*=.9;this.treble*=.9;this.level*=.9;this.active=false;if(this.fd)this.fd.fill(0);return}
    this.active=true;this.an.getByteFrequencyData(this.fd);
    const raw=[this.band(20,250),this.band(250,4000),this.band(4000,16000)];
    const k=Math.pow(.997,dt*60);
    const nb=raw.map((v,i)=>{this.pk[i]=Math.max(v,this.pk[i]*k,.06);return clamp(Math.pow(v/this.pk[i],1.6),0,1)});
    this.bass=lerp(this.bass,nb[0],.55);this.mid=lerp(this.mid,nb[1],.4);this.treble=lerp(this.treble,nb[2],.5);this.level=(this.bass+this.mid+this.treble)/3;
    const e=raw[0];
    if(e>this.ema*1.15&&nb[0]>.55&&now-this.last>.24){this.beat=true;const d=now-this.last;if(d<1.2){this.iv.push(d);if(this.iv.length>8)this.iv.shift()}this.last=now}
    this.ema=lerp(this.ema,e,.08);
    if(this.mode==='synth')this.bpm=SY.bpm;else if(this.iv.length>2){const s=[...this.iv].sort((a,b)=>a-b);this.bpm=clamp(60/s[s.length>>1],70,180)}else this.bpm=120;
    this.beatPhase=((now-this.last)*this.bpm/60)%1;
  },
  stopAll(){SY.stop();if(this.el){this.el.pause()}if(this.mic){this.mic.getTracks().forEach(t=>t.stop());this.mic=null;if(this.micSrc)this.micSrc.disconnect()}this.mode='none';this.iv=[];this.pk=[.2,.2,.2]}
};
/* ---- procedural synth: kick / snare / hats / bass / pad / pluck, A-minor progression ---- */
const SY={bpm:122,on:false,step:0,next:0,timer:null,noise:null,
  midi:m=>440*Math.pow(2,(m-69)/12),
  chords:[[57,60,64],[53,57,60],[52,55,60],[55,59,62]],roots:[45,41,48,43],
  start(){AE.init();const c=AE.ctx;c.resume();if(!this.noise){const b=c.createBuffer(1,c.sampleRate,c.sampleRate),d=b.getChannelData(0);for(let i=0;i<d.length;i++)d[i]=Math.random()*2-1;this.noise=b}
    this.on=true;this.step=0;this.next=c.currentTime+.06;clearInterval(this.timer);this.timer=setInterval(()=>this.sched(),25)},
  stop(){clearInterval(this.timer);this.on=false},
  sched(){const c=AE.ctx,spb=60/this.bpm/4;while(this.next<c.currentTime+.12){this.play(this.step,this.next);this.next+=spb;this.step=(this.step+1)%64}},
  env(g,t,a,peak,dec){g.gain.setValueAtTime(0.0001,t);g.gain.exponentialRampToValueAtTime(peak,t+a);g.gain.exponentialRampToValueAtTime(0.0001,t+a+dec)},
  play(s,t){const st=s%16,bar=(s>>4)%4,c=AE.ctx,M=AE.master;
    if(st%4===0||(st===10&&bar%2))this.kick(t);
    if(st===4||st===12)this.snare(t);
    this.hat(t,st%4===2?.11:.045,st===14);
    if(st%4===2||st===7||st===15)this.bassN(t,this.midi(this.roots[bar]+(st===15?12:0)));
    if(st===0)this.pad(t,this.chords[bar]);
    if(st%2===0){const ch=this.chords[bar];this.pluck(t,this.midi(ch[(st>>1)%3]+12+((st>>3)%2?12:0)))}
  },
  kick(t){const c=AE.ctx,o=c.createOscillator(),g=c.createGain();o.frequency.setValueAtTime(155,t);o.frequency.exponentialRampToValueAtTime(42,t+.13);g.gain.setValueAtTime(1,t);g.gain.exponentialRampToValueAtTime(.001,t+.42);o.connect(g).connect(AE.master);o.start(t);o.stop(t+.45)},
  snare(t){const c=AE.ctx,n=c.createBufferSource();n.buffer=this.noise;const f=c.createBiquadFilter();f.type='highpass';f.frequency.value=1300;const g=c.createGain();g.gain.setValueAtTime(.45,t);g.gain.exponentialRampToValueAtTime(.001,t+.2);n.connect(f).connect(g).connect(AE.master);n.start(t,Math.random()*.5);n.stop(t+.22);
    const o=c.createOscillator(),g2=c.createGain();o.type='triangle';o.frequency.value=190;g2.gain.setValueAtTime(.3,t);g2.gain.exponentialRampToValueAtTime(.001,t+.1);o.connect(g2).connect(AE.master);o.start(t);o.stop(t+.12)},
  hat(t,v,open){const c=AE.ctx,n=c.createBufferSource();n.buffer=this.noise;const f=c.createBiquadFilter();f.type='highpass';f.frequency.value=7500;const g=c.createGain();const d=open?.22:.045;g.gain.setValueAtTime(v,t);g.gain.exponentialRampToValueAtTime(.001,t+d);n.connect(f).connect(g).connect(AE.master);n.start(t,Math.random()*.5);n.stop(t+d+.02)},
  bassN(t,hz){const c=AE.ctx,o=c.createOscillator(),f=c.createBiquadFilter(),g=c.createGain();o.type='sawtooth';o.frequency.value=hz;f.type='lowpass';f.Q.value=7;f.frequency.setValueAtTime(900,t);f.frequency.exponentialRampToValueAtTime(160,t+.22);this.env(g,t,.005,.32,.24);o.connect(f).connect(g).connect(AE.master);o.start(t);o.stop(t+.3)},
  pad(t,ch){const c=AE.ctx,f=c.createBiquadFilter(),g=c.createGain();f.type='lowpass';f.frequency.value=1500;g.gain.setValueAtTime(.0001,t);g.gain.exponentialRampToValueAtTime(.05,t+.35);g.gain.exponentialRampToValueAtTime(.0001,t+1.95);f.connect(g).connect(AE.master);
    ch.forEach(m=>[-7,7].forEach(dt=>{const o=c.createOscillator();o.type='sawtooth';o.frequency.value=this.midi(m);o.detune.value=dt;o.connect(f);o.start(t);o.stop(t+2)}))},
  pluck(t,hz){const c=AE.ctx,o=c.createOscillator(),g=c.createGain();o.type='square';o.frequency.value=hz;const f=c.createBiquadFilter();f.type='lowpass';f.frequency.value=2600;this.env(g,t,.004,.06,.16);o.connect(f).connect(g).connect(AE.master);o.start(t);o.stop(t+.2)}
};
/* idle "demo" signal used by home previews when no audio is running */
function fakeA(t){const ph=(t*2.03)%1,pp=((t-1/60)*2.03)%1;return{bass:Math.exp(-ph*5)*.8,mid:.35+.25*Math.sin(t*3.1),treble:.35+.3*Math.sin(t*7.3)*Math.sin(t*1.3),level:.4,beat:ph<pp,bpm:122,active:true,beatPhase:ph}}

/* ================= SHARED ================= */
class Particles{constructor(max){this.p=[];this.max=max}
  add(o){if(this.p.length<this.max)this.p.push(Object.assign({life:1,max:1,size:2,g:0,drag:.98,col:'#ffffff'},o))}
  update(dt){const p=this.p;for(let i=p.length-1;i>=0;i--){const q=p[i];q.life-=dt;if(q.life<=0){p[i]=p[p.length-1];p.pop();continue}const d=Math.pow(q.drag,dt*60);q.vx*=d;q.vy*=d;q.vy+=q.g*dt;q.x+=q.vx*dt;q.y+=q.vy*dt}}
  draw(ctx){ctx.globalCompositeOperation='lighter';for(const q of this.p){const a=q.life/q.max,s=q.size*(.5+.5*a)*2.2;ctx.globalAlpha=Math.min(1,a*1.4);ctx.drawImage(sprite(q.col),q.x-s,q.y-s,s*2,s*2)}ctx.globalAlpha=1;ctx.globalCompositeOperation='source-over'}
  burst(x,y,n,cols,spd=200,o={}){for(let i=0;i<n;i++){const a=rnd(TAU),s=rnd(.2,1)*spd,l=rnd(.6,1.4)*(o.life||1);this.add(Object.assign({x,y,vx:Math.cos(a)*s,vy:Math.sin(a)*s,life:l,max:l,size:rnd(1,2.6)*(o.size||1),col:cols[(Math.random()*cols.length)|0],drag:.95},o.extra||{}))}}
}
class Scene{constructor(pv){this.preview=pv;this.w=1;this.h=1;this.t=0;this.ptrs=new Map();this.variant=null}
  resize(w,h){this.w=w;this.h=h} setVariant(v){this.variant=v} down(){} move(){} up(){} update(){} draw(){}
  get ptr(){for(const p of this.ptrs.values())return p;return null}}

/* ================= MODULE 1: WATER ================= */
/* ================= SHARED v2: images, shapes, sounds ================= */
const IMGS={};function img(src){if(!IMGS[src]){const i=new Image();i.decoding='async';i.src=src;IMGS[src]=i}return IMGS[src]}
const ready=i=>i&&i.complete&&i.naturalWidth>0;
const PALS={aurora:['#00F5D4','#7B61FF','#4ADE80','#E0FBFC'],solar:['#FFBE0B','#FB5607','#FF006E','#FFE29A'],neon:['#FF2BD6','#00E5FF','#7CFF4F','#FEE440'],ice:['#FFFFFF','#BDE0FE','#A2D2FF','#CAF0F8'],rose:['#FF4D6D','#FF8FA3','#FFC2D1','#FFB36B'],rainbow:['#FF595E','#FFCA3A','#8AC926','#1982C4','#B388FF']};
const SHAPES=[['dots','•'],['stars','✦'],['hearts','♥'],['notes','♪'],['sparks','⟋'],['bubbles','○'],['paws','🐾']];
function shapePath(ctx,shape,s){ctx.beginPath();
  if(shape==='stars'){for(let i=0;i<10;i++){const r=i%2?s*.42:s,a=i*Math.PI/5-Math.PI/2;i?ctx.lineTo(Math.cos(a)*r,Math.sin(a)*r):ctx.moveTo(Math.cos(a)*r,Math.sin(a)*r)}ctx.closePath()}
  else if(shape==='hearts'){ctx.moveTo(0,s*.35);ctx.bezierCurveTo(-s*1.1,-s*.35,-s*.45,-s*1.05,0,-s*.42);ctx.bezierCurveTo(s*.45,-s*1.05,s*1.1,-s*.35,0,s*.35);ctx.closePath()}
  else if(shape==='feather'){ctx.ellipse(0,0,s,s*.28,0,0,TAU)}
  else if(shape==='paws'){ctx.ellipse(0,s*.25,s*.42,s*.34,0,0,TAU);for(const[x,y]of[[-.5,-.25],[-.18,-.55],[.18,-.55],[.5,-.25]]){ctx.moveTo(x*s+s*.17,y*s);ctx.arc(x*s,y*s,s*.17,0,TAU)}}
  else ctx.arc(0,0,s,0,TAU)}
function drawShape(ctx,shape,x,y,s,rot,col,a){ctx.globalAlpha=a;
  ctx.globalCompositeOperation='lighter';const g=shape==='sparks'?s*1.2:s*2.2;ctx.drawImage(sprite(col),x-g,y-g,g*2,g*2);
  if(shape==='dots'){ctx.globalCompositeOperation='source-over';ctx.globalAlpha=1;return}
  ctx.save();ctx.translate(x,y);ctx.rotate(rot);
  if(shape==='sparks'){ctx.strokeStyle=col;ctx.lineWidth=Math.max(1,s*.25);ctx.beginPath();ctx.moveTo(-s*1.6,0);ctx.lineTo(s*1.6,0);ctx.stroke()}
  else if(shape==='notes'){ctx.fillStyle=col;ctx.font=`700 ${s*2.4}px system-ui,sans-serif`;ctx.textAlign='center';ctx.textBaseline='middle';ctx.fillText(rot>0?'♪':'♫',0,0)}
  else if(shape==='bubbles'){ctx.strokeStyle=col;ctx.lineWidth=Math.max(1,s*.14);ctx.beginPath();ctx.arc(0,0,s,0,TAU);ctx.stroke();ctx.fillStyle='rgba(255,255,255,.7)';ctx.beginPath();ctx.arc(-s*.35,-s*.35,s*.18,0,TAU);ctx.fill()}
  else{shapePath(ctx,shape,s);ctx.fillStyle=col;ctx.fill();if(shape==='feather'){ctx.strokeStyle='rgba(255,255,255,.6)';ctx.lineWidth=1;ctx.beginPath();ctx.moveTo(-s,0);ctx.lineTo(s,0);ctx.stroke()}}
  ctx.restore();ctx.globalCompositeOperation='source-over';ctx.globalAlpha=1}

/* ---- music particle overlay: works on top of every wallpaper ---- */
const SFX={
  ctx(){AE.init();AE.ctx.resume();if(!this.bus){const c=AE.ctx;this.bus=c.createGain();this.bus.gain.value=1;const d=c.createDelay(1);d.delayTime.value=.27;const fb=c.createGain();fb.gain.value=.28;const lp=c.createBiquadFilter();lp.type='lowpass';lp.frequency.value=2500;this.bus.connect(AE.master);this.bus.connect(d);d.connect(lp);lp.connect(fb);fb.connect(d);lp.connect(AE.master)}return AE.ctx},
  noiseBuf(){const c=AE.ctx;if(!this.nb){const b=c.createBuffer(1,c.sampleRate,c.sampleRate),d=b.getChannelData(0);for(let i=0;i<d.length;i++)d[i]=Math.random()*2-1;this.nb=b}return this.nb},
  ks:{},
  pluck(f,vel=1){const c=this.ctx(),key=Math.round(f*10);let b=this.ks[key];if(!b){const sr=c.sampleRate,len=(sr*3.2)|0,N=Math.max(2,Math.round(sr/f));b=c.createBuffer(1,len,sr);const d=b.getChannelData(0);let prev=0;for(let i=0;i<N;i++){const r=Math.random()*2-1;prev=prev*.5+r*.5;d[i]=prev}for(let i=N;i<len;i++)d[i]=.4985*(d[i-N]+d[i-N+1]);this.ks[key]=b}
    const s=c.createBufferSource();s.buffer=b;const g=c.createGain();g.gain.value=.55*vel;const lp=c.createBiquadFilter();lp.type='lowpass';lp.frequency.value=3200+vel*2500;s.connect(lp).connect(g).connect(this.bus);s.start()},
  pan(f){const c=this.ctx(),t=c.currentTime;[[1,.42,3.4],[2,.2,2],[2.98,.08,1.1],[4.2,.03,.5]].forEach(([m,a,d])=>{const o=c.createOscillator(),g=c.createGain();o.frequency.value=f*m;o.detune.value=rnd(-4,4);g.gain.setValueAtTime(.0001,t);g.gain.exponentialRampToValueAtTime(a,t+.006);g.gain.exponentialRampToValueAtTime(.0001,t+d);o.connect(g).connect(this.bus);o.start(t);o.stop(t+d+.05)});
    const n=c.createBufferSource();n.buffer=this.noiseBuf();const bp=c.createBiquadFilter();bp.type='bandpass';bp.frequency.value=f*3;bp.Q.value=4;const g=c.createGain();g.gain.setValueAtTime(.12,t);g.gain.exponentialRampToValueAtTime(.0001,t+.06);n.connect(bp).connect(g).connect(this.bus);n.start(t);n.stop(t+.08)},
  voice(kind){const c=this.ctx(),t=c.currentTime,o=c.createOscillator(),f1=c.createBiquadFilter(),f2=c.createBiquadFilter(),g=c.createGain(),m=c.createGain();o.type='sawtooth';f1.type='bandpass';f2.type='bandpass';f1.Q.value=6;f2.Q.value=8;m.gain.value=.5;
    o.connect(f1);o.connect(f2);f1.connect(g);f2.connect(m);m.connect(g);g.connect(this.bus);
    if(kind==='meow'){const p=rnd(.9,1.15);o.frequency.setValueAtTime(480*p,t);o.frequency.linearRampToValueAtTime(820*p,t+.25);o.frequency.linearRampToValueAtTime(560*p,t+.7);f1.frequency.setValueAtTime(700,t);f1.frequency.linearRampToValueAtTime(1500,t+.25);f1.frequency.linearRampToValueAtTime(800,t+.7);f2.frequency.setValueAtTime(2600,t);g.gain.setValueAtTime(.0001,t);g.gain.exponentialRampToValueAtTime(.5,t+.06);g.gain.setValueAtTime(.5,t+.45);g.gain.exponentialRampToValueAtTime(.0001,t+.75);o.start(t);o.stop(t+.8)}
    else if(kind==='bark'){o.frequency.setValueAtTime(330,t);o.frequency.exponentialRampToValueAtTime(190,t+.14);f1.frequency.value=900;f2.frequency.value=2200;g.gain.setValueAtTime(.0001,t);g.gain.exponentialRampToValueAtTime(.9,t+.015);g.gain.exponentialRampToValueAtTime(.0001,t+.17);
      g.gain.setValueAtTime(.0001,t+.22);g.gain.exponentialRampToValueAtTime(.7,t+.235);g.gain.exponentialRampToValueAtTime(.0001,t+.36);o.frequency.setValueAtTime(350,t+.22);o.frequency.exponentialRampToValueAtTime(200,t+.34);o.start(t);o.stop(t+.4)}
    else{const lf=c.createOscillator(),lg=c.createGain();lf.frequency.value=38;lg.gain.value=160;lf.connect(lg).connect(o.frequency);o.frequency.setValueAtTime(1300,t);o.frequency.linearRampToValueAtTime(900,t+.35);f1.frequency.value=2000;f2.frequency.value=3400;f1.Q.value=3;g.gain.setValueAtTime(.0001,t);g.gain.exponentialRampToValueAtTime(.45,t+.02);g.gain.exponentialRampToValueAtTime(.0001,t+.42);o.start(t);o.stop(t+.45);lf.start(t);lf.stop(t+.45)}},
  ignition(){const c=this.ctx(),t=c.currentTime,o=c.createOscillator(),g=c.createGain();o.type='sine';o.frequency.setValueAtTime(140,t);o.frequency.exponentialRampToValueAtTime(1100,t+.35);g.gain.setValueAtTime(.0001,t);g.gain.exponentialRampToValueAtTime(.18,t+.05);g.gain.exponentialRampToValueAtTime(.0001,t+.5);o.connect(g).connect(this.bus);o.start(t);o.stop(t+.55);
    const n=c.createBufferSource();n.buffer=this.noiseBuf();const hp=c.createBiquadFilter();hp.type='highpass';hp.frequency.value=3000;const g2=c.createGain();g2.gain.setValueAtTime(.3,t+.33);g2.gain.exponentialRampToValueAtTime(.0001,t+.4);n.connect(hp).connect(g2).connect(this.bus);n.start(t+.32);n.stop(t+.42)},
  whoosh(){const c=this.ctx(),t=c.currentTime,n=c.createBufferSource();n.buffer=this.noiseBuf();n.loop=true;const bp=c.createBiquadFilter();bp.type='bandpass';bp.Q.value=1.2;bp.frequency.setValueAtTime(200,t);bp.frequency.exponentialRampToValueAtTime(3000,t+1);bp.frequency.exponentialRampToValueAtTime(300,t+1.6);const g=c.createGain();g.gain.setValueAtTime(.0001,t);g.gain.exponentialRampToValueAtTime(.5,t+.8);g.gain.exponentialRampToValueAtTime(.0001,t+1.7);n.connect(bp).connect(g).connect(this.bus);n.start(t);n.stop(t+1.8)}
};

/* ================= PHOTO SCENES (Flow stills + code lighting) ================= */
SFX.tone=function(type,f0,f1,dur,vol=.22,delay=0){const c=this.ctx(),t=c.currentTime+delay,o=c.createOscillator(),g=c.createGain();o.type=type;o.frequency.setValueAtTime(f0,t);o.frequency.exponentialRampToValueAtTime(Math.max(20,f1),t+dur);g.gain.setValueAtTime(.0001,t);g.gain.exponentialRampToValueAtTime(vol,t+.012);g.gain.exponentialRampToValueAtTime(.0001,t+dur);o.connect(g).connect(this.bus);o.start(t);o.stop(t+dur+.03)};
SFX.hiss=function(f,dur,vol=.3,delay=0){const c=this.ctx(),t=c.currentTime+delay,n=c.createBufferSource();n.buffer=this.noiseBuf();const fl=c.createBiquadFilter();fl.type='bandpass';fl.frequency.value=f;fl.Q.value=.8;const g=c.createGain();g.gain.setValueAtTime(.0001,t);g.gain.exponentialRampToValueAtTime(vol,t+.03);g.gain.exponentialRampToValueAtTime(.0001,t+dur);n.connect(fl).connect(g).connect(this.bus);n.start(t,Math.random()*.4);n.stop(t+dur+.05)};
const T3=window.THREE;const HAS3D=!!T3;
let K=null; // lazily built shared kit
function kit(){if(K)return K;const T=T3;T.ColorManagement&&(T.ColorManagement.legacyMode=false);K={};
  K.SPH=new T.SphereGeometry(1,40,28);K.CAP=new T.SphereGeometry(1,40,20,0,TAU,0,Math.PI/2);K.IRIS=new T.SphereGeometry(1,40,12,0,TAU,0,.66);K.SPHL=new T.SphereGeometry(1,24,16);K.CIRC=new T.CircleGeometry(1,40);
  const cv=(w,h,f)=>{const c=document.createElement('canvas');c.width=w;c.height=h;f(c.getContext('2d'),w,h);const t=new T.CanvasTexture(c);t.encoding=T.sRGBEncoding;return t};
  K.spiral=cv(128,128,(g,w)=>{g.translate(64,64);g.strokeStyle='#fff';g.lineWidth=7;g.lineCap='round';g.beginPath();for(let a=0;a<TAU*3.2;a+=.08){const r=a*5.6;g.lineTo(Math.cos(a)*r,Math.sin(a)*r)}g.stroke()});
  K.cheek=cv(64,64,(g)=>{const r=g.createRadialGradient(32,32,0,32,32,32);r.addColorStop(0,'rgba(255,110,150,.85)');r.addColorStop(1,'rgba(255,110,150,0)');g.fillStyle=r;g.fillRect(0,0,64,64)});
  K.iris=c=>cv(128,128,(g)=>{let r=g.createRadialGradient(64,64,8,64,64,62);r.addColorStop(0,'#000');r.addColorStop(.38,'#000');r.addColorStop(.42,c[0]);r.addColorStop(.85,c[1]);r.addColorStop(1,'#1a0f00');g.fillStyle=r;g.beginPath();g.arc(64,64,63,0,TAU);g.fill()});
  K.irisV=c=>cv(8,128,(g,w,h)=>{const r=g.createLinearGradient(0,0,0,h);r.addColorStop(0,'#000');r.addColorStop(.42,'#050308');r.addColorStop(.47,c[0]);r.addColorStop(.86,c[1]);r.addColorStop(1,'#0a0604');g.fillStyle=r;g.fillRect(0,0,w,h)});
  K.glowTex=cv(64,64,(g)=>{const r=g.createRadialGradient(32,32,0,32,32,32);r.addColorStop(0,'rgba(255,255,255,1)');r.addColorStop(.3,'rgba(255,255,255,.35)');r.addColorStop(1,'rgba(255,255,255,0)');g.fillStyle=r;g.fillRect(0,0,64,64)});
  // fur strand noise
  const N=256,d=new Uint8Array(N*N*4);for(let i=0;i<N*N;i++){const v=Math.random()*255;d[i*4]=d[i*4+1]=d[i*4+2]=v;d[i*4+3]=255}K.noise=new T.DataTexture(d,N,N);K.noise.wrapS=K.noise.wrapT=T.RepeatWrapping;K.noise.magFilter=T.NearestFilter;K.noise.needsUpdate=true;
  K.L=new T.Vector3(-.45,.75,.6).normalize();
  K.M={sclera:new T.MeshPhysicalMaterial({color:'#ffffff',roughness:.25,clearcoat:.6,clearcoatRoughness:.2}),eye:new T.MeshPhysicalMaterial({color:'#05050a',roughness:.06,clearcoat:1,clearcoatRoughness:.02,metalness:0}),
    hi:new T.MeshBasicMaterial({color:'#ffffff'}),mouth:new T.MeshStandardMaterial({color:'#3a0a1c',roughness:.55}),tongue:new T.MeshStandardMaterial({color:'#ff6f91',roughness:.5}),
    spiral:new T.MeshBasicMaterial({map:K.spiral,transparent:true,depthWrite:false}),cheek:new T.MeshBasicMaterial({map:K.cheek,transparent:true,depthWrite:false}),teeth:new T.MeshStandardMaterial({color:'#fffaf0',roughness:.3})};
  return K}
function mkVideo(src,loop){const v=document.createElement('video');v.muted=true;v.loop=loop;v.playsInline=true;v.setAttribute('playsinline','');v.preload='auto';v.src=src;return v}
function drawCover(ctx,m,w,h,z=1,fx=.5,fy=.5){const mw=m.videoWidth||m.naturalWidth,mh=m.videoHeight||m.naturalHeight;if(!mw)return false;let s=(w/h>mw/mh*1.3)?h/mh*1.05:Math.max(w/mw,h/mh);s*=z;const dw=mw*s,dh=mh*s;ctx.drawImage(m,w*fx-dw*fx,h*fy-dh*fy,dw,dh);return true}
function dayPhase(d=new Date()){const h=d.getHours()+d.getMinutes()/60;if(h>=23||h<6)return'night';if(h<10)return'morning';if(h<17)return'day';if(h<20.5)return'evening';return'late'}
const PHASE_LIGHT={
  night:{key:'#9fb0ff',ki:.7,rimA:'#6b7cff',rimB:'#8a6cff',hemi:.14,exp:.85},
  morning:{key:'#ffe0bd',ki:1.3,rimA:'#ffc08a',rimB:'#ffb3d6',hemi:.26,exp:1},
  day:{key:'#fff6ea',ki:1.4,rimA:'#d6c2ff',rimB:'#9ff0ff',hemi:.26,exp:1.02},
  evening:{key:'#ffd2a0',ki:1.2,rimA:'#ff9a5c',rimB:'#d07bff',hemi:.2,exp:.97},
  late:{key:'#d4c8ff',ki:1,rimA:'#a77cff',rimB:'#7a9cff',hemi:.18,exp:.92}};
const MOOD_TEXT={sleep:'Sleeping',wakeup:'Just woke up',day:'Full of energy',evening:'Chilling',late:'Getting sleepy',charge:'Drinking battery juice',low:'Low battery…',dance:'Dancing',dizzy:'Dizzy!'};
const HSTATE={music:false,bands:null,spec:null,battery:1,charging:false,offset:0,visible:true,unlocks:0,screenOffs:0};
const ease={outBack:t=>{const c=1.7;return 1+(c+1)*Math.pow(t-1,3)+c*Math.pow(t-1,2)},inOut:t=>t<.5?2*t*t:1-Math.pow(-2*t+2,2)/2,bell:t=>Math.sin(clamp(t,0,1)*Math.PI)};
let dtFrame=1/60,GLS=null;
function sharedGL(){if(GLS)return GLS;const T=T3,cv=document.createElement('canvas');cv.className='gl';const r=new T.WebGLRenderer({canvas:cv,alpha:true,antialias:true,powerPreference:'high-performance'});r.outputEncoding=T.sRGBEncoding;r.toneMapping=T.ACESFilmicToneMapping;r.setClearColor(0,0);
  const pm=new T.PMREMGenerator(r),env=pm.fromScene(new T.RoomEnvironment(),.04).texture;pm.dispose();const mx=Math.min(devicePixelRatio||1,WP?1.3:1.6);return GLS={r,cv,env,pr:mx,maxPr:mx}}

function hslHex(h,s=1,l=.62){h=((h%360)+360)%360;const a=s*Math.min(l,1-l),f=n=>{const k=(n+h/30)%12,c=l-a*Math.max(-1,Math.min(k-3,9-k,1));return Math.round(255*c).toString(16).padStart(2,'0')};return'#'+f(0)+f(8)+f(4)}
