/* ================= NEW 2D CREATURES — puppy, kitten, parrots, anglerfish, a wannabe-evil imp =================
   All slim and cute (big head on a small body), built on the Buddy2D brain so they inherit every
   Mochi story, eye contact, dancing, moods, skies and grounds, plus their own special tricks. */
function cuteEye(c,x,y,r,F,t,i,iris){const e=F.eye,ink='#1a0d10',sd=i?1:-1,open=clamp(F.eyeOpen,0,1);c.lineCap='round';
  const L=e==='wink'?(i?'happy':'normal'):e==='wink2'?(i?'normal':'happy'):e==='peek'?(i?'normal':'closed'):e;
  if(L==='happy'){c.strokeStyle=ink;c.lineWidth=r*.3;c.beginPath();c.arc(x,y+r*.35,r*.8,Math.PI*1.15,Math.PI*1.85);c.stroke();return}
  if(L==='closed'){c.strokeStyle=ink;c.lineWidth=r*.26;c.beginPath();c.arc(x,y-r*.3,r*.75,Math.PI*.18,Math.PI*.82);c.stroke();return}
  if(L==='squeeze'||L==='cry'){c.strokeStyle=ink;c.lineWidth=r*.28;c.beginPath();c.moveTo(x-r*.6*sd,y-r*.5);c.lineTo(x+r*.45*sd,y);c.lineTo(x-r*.6*sd,y+r*.5);c.stroke();if(L==='cry'){c.fillStyle='rgba(130,210,255,.85)';const l=r*(2+.4*Math.sin(t*9+i));c.fillRect(x-r*.2,y+r*.3,r*.4,l)}return}
  if(L==='heart'){c.fillStyle='#ff3d7f';const s=r*1.2*(1+.1*Math.sin(t*10));heartP(c,x,y,s);c.fill();c.fillStyle='#fff';c.beginPath();c.arc(x-s*.35,y-s*.3,s*.15,0,TAU);c.fill();return}
  if(L==='star'){c.fillStyle='#ffd34d';starP(c,x,y,r*1.2,t);c.fill();return}
  if(L==='spiral'){c.strokeStyle=ink;c.lineWidth=r*.15;c.beginPath();for(let a=0;a<TAU*2.4;a+=.25){const rr=a*r*.07,aa=a+t*9*sd;c.lineTo(x+Math.cos(aa)*rr,y+Math.sin(aa)*rr)}c.stroke();return}
  if(L==='note'){c.fillStyle=ink;c.font=`900 ${r*2.2}px system-ui,sans-serif`;c.textAlign='center';c.textBaseline='middle';c.fillText(i?'♪':'♫',x,y);return}
  if(L==='dot'){c.fillStyle=ink;c.beginPath();c.arc(x,y,r*.35,0,TAU);c.fill();return}
  const big=L==='wow'?1.15:1,ry=r*big*Math.max(.08,open),rx=r*big;c.save();c.beginPath();c.ellipse(x,y,rx,ry,0,0,TAU);c.clip();
  const g=c.createRadialGradient(x,y+ry*.3,r*.1,x,y,r*1.1);g.addColorStop(0,iris||'#6b3a1c');g.addColorStop(.55,'#24120a');g.addColorStop(1,'#050303');c.fillStyle=g;c.fillRect(x-r*1.2,y-r*1.2,r*2.4,r*2.4);
  const lx=F.look[0]*r*.18,ly=F.look[1]*r*.12;c.fillStyle='#fff';c.beginPath();c.ellipse(x-r*.32-lx*.3,y-r*.32-ly*.3,r*.3,r*.26,-.4,0,TAU);c.fill();c.globalAlpha=.7;c.beginPath();c.arc(x+r*.35,y+r*.3,r*.12,0,TAU);c.fill();c.globalAlpha=1;
  if(L==='teary'){c.fillStyle='rgba(150,215,255,.75)';c.fillRect(x-r,y+r*.35+Math.sin(t*6)*r*.05,r*2,r)}c.restore()}
function furTile(cols,key){if(!furTile.c)furTile.c={};if(furTile.c[key])return furTile.c[key];const c=document.createElement('canvas');c.width=c.height=160;const x=c.getContext('2d');x.lineCap='round';
  for(let i=0;i<700;i++){const X=Math.random()*160,Y=Math.random()*160,a=rnd(-.5,.5)+Math.PI/2,L=rnd(4,9);x.strokeStyle=hexA(cols[(Math.random()*cols.length)|0],rnd(.12,.32));x.lineWidth=rnd(.8,1.6);x.beginPath();x.moveTo(X,Y);x.lineTo(X+Math.cos(a)*L,Y+Math.sin(a)*L);x.stroke()}return furTile.c[key]=c}
class Critter extends Buddy2D{
  constructor(pv,id){super(pv,id);this.hovK=.95;this.bodyW=.62;this.bodyH=1.0;this.faceCute=true;this.tailA=0}
  resize(w,h){super.resize(w,h);this.U=Math.min(w*this.uw,h*this.uh)}
  update(dt,A){super.update(dt,A);if(!(this.act&&['helicopter','rocket'].includes(this.act.type)))this.hov=lerp(this.hov,this.U*this.hovK+(this.lift||0),.15);this.lift=0;this.tailA+=dt*(this.mood==='dance'?14:this.act&&this.act.type==='wag'?18:3)}
  onBody(x,y){const U=this.U;return Math.abs(x-this.x-this.xOff)<U*1.05&&y<this.y+U*.1&&y>this.y-this.hov-U*1.9}
  tipScr(){const t=this.sp([0,1,0]);return[t[0],t[1]-this.U*.05]}
  paintFace(){if(this.faceCute)drawMochi2Face(this.fcx,this.F,this.t,{skin:this.pal.face||this.pal.top,ink:this.pal.ink,shiver:this.shiver,ax:this.S[0]/this.S[2]})}
  bodyBox(){const U=this.U,r=this.rollAng();return{x:this.x+this.xOff-Math.sin(r)*U*.3,y:this.y-this.hov*.5+U*.05,rx:U*this.bodyW,ry:Math.max(U*.4,this.hov*.55+U*.08),r:r*.5}}
  furry(ctx,path,cols,key,x,y,R){ctx.save();path();ctx.clip();ctx.globalAlpha=.6;const ft=furTile(cols,key),s=R*1.1;for(let yy=y-R*1.6;yy<y+R*1.6;yy+=s)for(let xx=x-R*1.6;xx<x+R*1.6;xx+=s)ctx.drawImage(ft,xx,yy,s,s);ctx.restore()}
  fuzz(ctx,pts,col,len){ctx.strokeStyle=col;ctx.lineWidth=this.U*.04;ctx.lineCap='round';const c=this.cenOf(pts);ctx.beginPath();for(let i=0;i<pts.length;i++){const p=pts[i],dx=p[0]-c[0],dy=p[1]-c[1],d=Math.hypot(dx,dy)+1,w=Math.sin(i*2.7+this.t*1.5)*.3;ctx.moveTo(p[0]-dx/d*len*.4,p[1]-dy/d*len*.4);ctx.lineTo(p[0]+(dx/d+w*dy/d)*len,p[1]+(dy/d-w*dx/d)*len)}ctx.stroke()}
}

/* ---------------- KALBOOB: a little puppy ---------------- */
const PUPPY_PALS=[
 {name:'Golden',top:'#f3c98a',bot:'#c98a45',ear:'#b8742f',face:'#f8dcae',ink:'#2a1408',nose:'#1d1210',glow:'#ffb36b',rim:'#ffd59a',rim2:'#ff8fd0',skin:'#f3c98a',lid:'#f8dcae',belly:'#fff0d8'},
 {name:'Cream',top:'#fff1dc',bot:'#e2c39a',ear:'#d6aa74',face:'#fff6ea',ink:'#2a1408',nose:'#2a1a16',glow:'#ffd59a',rim:'#fff0d6',rim2:'#9fd8ff',skin:'#fff1dc',lid:'#fff6ea',belly:'#ffffff'},
 {name:'Choco',top:'#9a6440',bot:'#5a3420',ear:'#4a2a18',face:'#b98258',ink:'#1a0a04',nose:'#140a08',glow:'#ff9a4d',rim:'#ffbf8a',rim2:'#c79bff',skin:'#9a6440',lid:'#b98258',belly:'#d9b08a'},
 {name:'Husky',top:'#e8ecf2',bot:'#6a7486',ear:'#4a5262',face:'#ffffff',ink:'#0e1a2a',nose:'#141820',glow:'#9fd8ff',rim:'#cfe2ff',rim2:'#7dffb0',skin:'#e8ecf2',lid:'#ffffff',belly:'#ffffff'},
 {name:'Black',top:'#3a3432',bot:'#151211',ear:'#0f0d0c',face:'#5a504a',ink:'#fff0e8',nose:'#000000',glow:'#c79bff',rim:'#d0b8ff',rim2:'#ffb36b',skin:'#3a3432',lid:'#5a504a',belly:'#6a5e56'},
 {name:'Pinky',top:'#ffd6e8',bot:'#ff8fbf',ear:'#ff6fae',face:'#ffe8f2',ink:'#3a0c24',nose:'#7a2a4a',glow:'#ff6fd0',rim:'#ffc2e6',rim2:'#ffd23d',skin:'#ffd6e8',lid:'#ffe8f2',belly:'#fff4fa'}];
const PUPPY_SND=Object.assign({},MOCHI2_SND,{bark(){SFX.voice('bark')},whine(){SFX.tone('sine',900,1300,.35,.06);SFX.tone('sine',1100,800,.3,.05,.35)},pant(){for(let i=0;i<4;i++)SFX.hiss(1500,.06,.06,i*.13)}});
class PuppyScene extends Critter{
  constructor(pv){super(pv,'kalboob');this.snd=PUPPY_SND;this.PALS=PUPPY_PALS;this.defLook={sky:'bokeh',ground:'grass'};this.loadLook();this.col=this.pal.glow;this.chargeText='Chewing a power bone';this.strawToMouth=true;
    this.S=this.u.S=[.9,.82,.95,.82];this.u.bulge=.04;this.SH=[.42,-.9,.7];this.armLen=.9;this.uw=.28;this.uh=.15;this.extra=['wag','fetch','bark','beg','sniff','shake','dig'];
    Object.assign(this.DUR,{wag:2.6,fetch:4.6,bark:2,beg:3.4,sniff:3,shake:2.6,dig:3.2});this.ball=null}
  extraHook(T2,k,ac,E,dt,A){const U=this.U,t=ac.t,s=this.sfx.bind(this);
    switch(T2){
    case'wag':E.eye='happy';E.mouth='open';E.mo=.5;E.blush=1;if(Math.random()<dt*2)s('pant');return true;
    case'fetch':{const ph=k<.25?k/.25:k<.6?1:1-(k-.6)/.4;ac.bx=this.x+ac.side*this.w*.32*Math.min(1,k/.2);this.xOffT=ac.side*this.w*.28*(k<.25?0:ph);E.eye=k<.25?'wow':'happy';E.mouth='open';E.mo=.6;this.focus=[ac.bx,this.floor-U*.2];if(!ac.th){ac.th=1;s('boing')}if(k>.5&&!ac.b){ac.b=1;s('bark')}ac.held=k>.45;return true}
    case'bark':{E.eye='wow';E.mouth='open';E.mo=Math.max(0,Math.sin(t*12))*.9;if(!ac['b'+Math.floor(t*2)]&&t%0.5<dt*2){ac['b'+Math.floor(t*2)]=1;s('bark');this.sqv-=1.5;this.bub={txt:'woof!',life:.5}}return true}
    case'beg':{this.lift=U*.25*Math.sin(clamp(k,0,1)*Math.PI);this.arm(0,1.9+Math.sin(t*8)*.15,.6);this.arm(1,1.9-Math.sin(t*8)*.15,.6);E.eye='wow';E.mouth='tongue';E.mo=.4;E.blush=1.2;if(!ac.w){ac.w=1;s('whine')}return true}
    case'sniff':{this.pitchT=.3;this.lift=-U*.25;E.lookT=[Math.sin(t*3)*.6,1];E.eye='normal';E.mouth='cat';return true}
    case'shake':{this.arm(1,1.6+Math.sin(t*10)*.2,.9);E.eye='happy';E.mouth='open';E.mo=.4;E.blush=1;return true}
    case'dig':{this.arm(0,.3,.6+Math.max(0,Math.sin(t*16))*.6);this.arm(1,.3,.6+Math.max(0,Math.sin(t*16+3))*.6);this.pitchT=.2;E.eye='wow';E.mouth='open';E.mo=.4;if(Math.random()<dt*12)this.fx.push({k:'dot',x:this.x+rnd(-U,U)*.3,y:this.floor,vx:rnd(-90,90),vy:-rnd(80,220),g:600,life:.7,c:'#a5835a'});return true}}
    return false}
  drawCore(ctx,refl){const p=this.pal,U=this.U,t=this.t,A=this.A||{},bass=A.active?A.bass:0,pts=this.silhouette(refl?24:40),hc=this.cenOf(pts),B=this.bodyBox(),ac=this.act,F=this.F;
    if(!refl){ctx.globalCompositeOperation='lighter';glow(ctx,p.glow,hc[0],hc[1]+U*.6,U*1.9,.16+bass*.25);ctx.globalCompositeOperation='source-over'}
    // tail wag
    const tw=Math.sin(this.tailA)*(this.mood==='sleep'?.1:.6);ctx.save();ctx.translate(B.x+U*.45,B.y+U*.1);ctx.rotate(-.9+tw);ctx.strokeStyle=p.top;ctx.lineWidth=U*.16;ctx.lineCap='round';ctx.beginPath();ctx.moveTo(0,0);ctx.quadraticCurveTo(U*.3,-U*.15,U*.42,-U*.5);ctx.stroke();ctx.restore();
    // body + hind legs
    const bodyPath=()=>{ctx.beginPath();ctx.ellipse(B.x,B.y,B.rx,B.ry,B.r,0,TAU)};bodyPath();let g=ctx.createRadialGradient(B.x-U*.2,B.y-U*.3,U*.1,B.x,B.y,B.rx*1.4);g.addColorStop(0,p.top);g.addColorStop(1,p.bot);ctx.fillStyle=g;ctx.fill();if(!refl)this.furry(ctx,bodyPath,[p.top,p.bot,'#ffffff'],'pup'+p.name,B.x,B.y,U);
    for(const s of[-1,1]){ctx.fillStyle=p.bot;ctx.beginPath();ctx.ellipse(B.x+s*B.rx*.75,this.y-U*.22,U*.26,U*.24,0,0,TAU);ctx.fill();ctx.fillStyle=p.top;ctx.beginPath();ctx.ellipse(B.x+s*B.rx*.9,this.y-U*.05,U*.2,U*.09,0,0,TAU);ctx.fill()}
    if(!refl){ctx.fillStyle=hexA(p.belly,.75);ctx.beginPath();ctx.ellipse(B.x,B.y+U*.05,B.rx*.45,B.ry*.6,0,0,TAU);ctx.fill()}
    // front legs (they are the "arms")
    if(!refl)for(let i=0;i<2;i++){if(this.armHide[i]>.6)continue;const sh=this.shoulder(i),hd=this.hand(i);ctx.strokeStyle=p.top;ctx.lineWidth=U*.24;ctx.lineCap='round';ctx.beginPath();ctx.moveTo(sh[0],sh[1]);ctx.lineTo(hd[0],hd[1]);ctx.stroke();ctx.fillStyle=p.face;ctx.beginPath();ctx.ellipse(hd[0],hd[1],U*.14,U*.1,0,0,TAU);ctx.fill();ctx.strokeStyle=hexA(p.bot,.6);ctx.lineWidth=1.2;for(const k of[-1,1]){ctx.beginPath();ctx.moveTo(hd[0]+k*U*.04,hd[1]+U*.02);ctx.lineTo(hd[0]+k*U*.04,hd[1]+U*.09);ctx.stroke()}}
    // floppy ears behind? (in front, hanging at the sides)
    const earDrop=this.mood==='sleep'||F.eye==='cry'||F.eye==='teary'?.3:0;
    // head
    this.bodyPath(ctx,pts);g=ctx.createRadialGradient(hc[0]-U*.3,hc[1]-U*.4,U*.1,hc[0],hc[1],U*1.15);g.addColorStop(0,p.top);g.addColorStop(.7,p.top);g.addColorStop(1,p.bot);ctx.fillStyle=g;ctx.fill();if(!refl){this.furry(ctx,()=>this.bodyPath(ctx,pts),[p.top,p.bot,'#ffffff'],'pup'+p.name,hc[0],hc[1],U);this.fuzz(ctx,pts,hexA(p.top,.45),U*.018)}
    if(this.faceVisible()){ctx.save();const[fw,fh]=this.faceXf(ctx);if(!refl){ctx.fillStyle=hexA(p.face,.9);ctx.beginPath();ctx.ellipse(0,U*.12,U*.42,U*.32,0,0,TAU);ctx.fill()}ctx.drawImage(this.fc2,-fw/2,-fh/2,fw,fh);
      if(!refl){const nx=this.look[0]*U*.04,ny=-U*.02;ctx.fillStyle=p.nose;ctx.beginPath();ctx.ellipse(nx,ny,U*.1,U*.065,0,0,TAU);ctx.fill();ctx.fillStyle='rgba(255,255,255,.55)';ctx.beginPath();ctx.ellipse(nx-U*.03,ny-U*.025,U*.03,U*.015,0,0,TAU);ctx.fill();
        if(F.mouth==='open'||F.mouth==='tongue'){ctx.fillStyle='#ff7d9c';ctx.beginPath();ctx.ellipse(nx,U*.17+F.mouthOpen*U*.05,U*.06,U*.09,0,0,TAU);ctx.fill()}}ctx.restore()}
    for(const s of[-1,1]){const b=this.sp([s*.8,.55,.2]),r=this.rollAng(),fl=Math.sin(this.t*2+s)*.05+(this.antV||0)*.01*s;ctx.save();ctx.translate(b[0],b[1]);ctx.rotate(r+s*(.22+earDrop)+fl);ctx.fillStyle=p.ear;ctx.beginPath();ctx.moveTo(-U*.1,0);ctx.bezierCurveTo(-U*.3,U*.3,-U*.2,U*.85,s*0+U*.02,U*.9);ctx.bezierCurveTo(U*.24,U*.8,U*.22,U*.25,U*.1,0);ctx.closePath();ctx.fill();if(!refl){ctx.strokeStyle=hexA(p.bot,.5);ctx.lineWidth=1.5;ctx.stroke()}ctx.restore()}
    if(refl)return;
    // fetch ball
    if(ac&&ac.type==='fetch'){const held=ac.held,bx=held?this.sp([0,-.3,1])[0]:ac.bx,by=held?this.sp([0,-.3,1])[1]:this.floor-U*.16;ctx.fillStyle='#d6ff3d';ctx.beginPath();ctx.arc(bx,by,U*.15,0,TAU);ctx.fill();ctx.strokeStyle='#fff';ctx.lineWidth=2;ctx.beginPath();ctx.arc(bx-U*.15,by,U*.15,-.8,.8);ctx.stroke();ctx.beginPath();ctx.arc(bx+U*.15,by,U*.15,Math.PI-.8,Math.PI+.8);ctx.stroke()}}
}

/* ---------------- KITTY ---------------- */
const KITTY_PALS=[
 {name:'Tabby',top:'#f2f2f4',bot:'#8c8e96',ear:'#9a9ca6',inner:'#ffb0c0',face:'#ffffff',ink:'#141826',nose:'#ff8fa6',glow:'#9fd8ff',rim:'#cfe2ff',rim2:'#ffc2e6',skin:'#f2f2f4',lid:'#ffffff',belly:'#ffffff',stripe:'#5a5c66',iris:'#3d8bd6'},
 {name:'Ginger',top:'#ffc685',bot:'#e0782a',ear:'#e0782a',inner:'#ffb0a0',face:'#fff0dc',ink:'#2a1206',nose:'#ff8f8f',glow:'#ffb36b',rim:'#ffd59a',rim2:'#ff6fae',skin:'#ffc685',lid:'#fff0dc',belly:'#fff6ea',stripe:'#b8521a',iris:'#7ab82a'},
 {name:'Midnight',top:'#3a3a48',bot:'#14141c',ear:'#1c1c26',inner:'#ff8fb0',face:'#4a4a5a',ink:'#ffffff',nose:'#ff8fb0',glow:'#b48cff',rim:'#d0b8ff',rim2:'#38e8ff',skin:'#3a3a48',lid:'#4a4a5a',belly:'#4a4a58',stripe:'#202028',iris:'#ffd23d'},
 {name:'Snow',top:'#ffffff',bot:'#dfe3ea',ear:'#ffffff',inner:'#ffb0c8',face:'#ffffff',ink:'#1a2033',nose:'#ffa0b8',glow:'#cfe2ff',rim:'#e6f0ff',rim2:'#ffc2e6',skin:'#ffffff',lid:'#ffffff',belly:'#ffffff',stripe:'#e8ecf2',iris:'#38a8ff'},
 {name:'Siamese',top:'#fbf0e0',bot:'#a8825e',ear:'#5a3e2c',inner:'#d89a8a',face:'#fff8ee',ink:'#2a1406',nose:'#6a4a3a',glow:'#9fd8ff',rim:'#fff0d6',rim2:'#38c8ff',skin:'#fbf0e0',lid:'#fff8ee',belly:'#fffaf2',stripe:'#e8d6bc',iris:'#2fa8ff'},
 {name:'Calico',top:'#ffffff',bot:'#e8a050',ear:'#3a3030',inner:'#ffb0c0',face:'#ffffff',ink:'#2a1206',nose:'#ff9fb0',glow:'#ffb36b',rim:'#ffe0b8',rim2:'#c79bff',skin:'#ffffff',lid:'#ffffff',belly:'#ffffff',stripe:'#3a3030',iris:'#c8a02a'}];
const KITTY_SND=Object.assign({},MOCHI2_SND,{meow(){SFX.voice('meow')},purr(){SFX.hiss(140,.8,.16)},hiss2(){SFX.hiss(2500,.5,.18)},chirp2(){SFX.tone('sine',1400,2200,.08,.08);SFX.tone('sine',1600,2400,.08,.07,.12)}});
class KittyScene extends Critter{
  constructor(pv){super(pv,'kitty');this.snd=KITTY_SND;this.PALS=KITTY_PALS;this.defLook={sky:'stars',ground:'clouds'};this.loadLook();this.col=this.pal.glow;this.chargeText='Sipping warm milk';this.strawToMouth=true;
    this.S=this.u.S=[.88,.8,.95,.8];this.u.bulge=.04;this.SH=[.4,-.9,.7];this.armLen=.88;this.uw=.27;this.uh=.145;this.bodyW=.55;this.extra=['laser','yarn','meow','knead','groom','pounce','stretch2'];
    Object.assign(this.DUR,{laser:6,yarn:4.6,meow:2,knead:4,groom:3.6,pounce:2.6,stretch2:3});this.dot=null}
  extraHook(T2,k,ac,E,dt,A){const U=this.U,t=ac.t,s=this.sfx.bind(this);
    switch(T2){
    case'laser':{ac.dx=this.x+Math.sin(t*1.9)*this.w*.38+Math.sin(t*5.3)*U*.5;ac.dy=this.floor-U*.05-Math.abs(Math.sin(t*1.1))*U*1.5;this.focus=[ac.dx,ac.dy];E.eye='wow';E.mouth='o';E.mo=.1;this.pitchT=.1;if(Math.sin(t*3)>.95)this.arm(ac.dx>this.x?1:0,1.2,1.2);if(k>.85&&!ac.p){ac.p=1;this.vy=-this.h*.25;this.ground=false;s('chirp2')}return true}
    case'yarn':{ac.yx=Math.sin(t*2.4)*U*.8;this.focus=[this.x+ac.yx,this.floor-U*.12];this.arm(ac.yx>0?1:0,.6+Math.max(0,Math.sin(t*4.8))*.8,1.1);E.eye='wow';E.mouth='cat';E.blush=1;return true}
    case'meow':{E.eye='closed';E.open=0;E.mouth='open';E.mo=.4+.4*Math.sin(clamp(k,0,1)*Math.PI);if(!ac.m){ac.m=1;s('meow');this.bub={txt:'meow',life:1.2}}return true}
    case'knead':{this.arm(0,.2,.6+Math.max(0,Math.sin(t*6))*.5);this.arm(1,.2,.6+Math.max(0,Math.sin(t*6+Math.PI))*.5);E.eye='closed';E.open=0;E.mouth='cat';E.blush=1.3;if(!ac.p){ac.p=1;s('purr')}return true}
    case'groom':{this.arm(1,-.2,1.5);this.paw2[1]={pt:[.05,-.2,1],r:.15};E.eye='closed';E.open=0;E.mouth='tongue';E.mo=.3+.3*Math.sin(t*12);return true}
    case'pounce':{if(k<.55){this.lift=-U*.2;this.shv[0]+=Math.sin(t*30)*dt*1.5;E.eye='wow';E.mouth='cat'}else if(!ac.j){ac.j=1;this.vy=-this.h*.4;this.ground=false;s('chirp2')}else{E.eye='happy';E.mouth='open';E.mo=.5;this.arm(0,2.4,.4);this.arm(1,2.4,.4)}return true}
    case'stretch2':{this.lift=-U*.3*Math.sin(clamp(k,0,1)*Math.PI);this.arm(0,.9,1.4);this.arm(1,.9,1.4);E.eye='closed';E.open=0;E.mouth='open';E.mo=Math.sin(clamp(k,0,1)*Math.PI);return true}}
    return false}
  drawCore(ctx,refl){const p=this.pal,U=this.U,t=this.t,A=this.A||{},bass=A.active?A.bass:0,pts=this.silhouette(refl?24:40),hc=this.cenOf(pts),B=this.bodyBox(),ac=this.act,F=this.F;
    if(!refl){ctx.globalCompositeOperation='lighter';glow(ctx,p.glow,hc[0],hc[1]+U*.6,U*1.9,.16+bass*.25);ctx.globalCompositeOperation='source-over'}
    // curly tail
    const tw=Math.sin(this.tailA*.5)*.4;ctx.save();ctx.translate(B.x+U*.4,B.y+B.ry*.6);ctx.strokeStyle=p.top;ctx.lineWidth=U*.13;ctx.lineCap='round';ctx.beginPath();ctx.moveTo(0,0);ctx.bezierCurveTo(U*.5,0,U*.6,-U*.5,U*(.35+tw*.3),-U*.9);ctx.stroke();ctx.strokeStyle=p.stripe;ctx.lineWidth=U*.13;ctx.setLineDash([U*.08,U*.12]);ctx.stroke();ctx.setLineDash([]);ctx.restore();
    const bodyPath=()=>{ctx.beginPath();ctx.ellipse(B.x,B.y,B.rx,B.ry,B.r,0,TAU)};bodyPath();let g=ctx.createRadialGradient(B.x-U*.2,B.y-U*.3,U*.1,B.x,B.y,B.rx*1.4);g.addColorStop(0,p.top);g.addColorStop(1,p.bot);ctx.fillStyle=g;ctx.fill();if(!refl)this.furry(ctx,bodyPath,[p.top,p.stripe,'#ffffff'],'kit'+p.name,B.x,B.y,U);
    for(const s of[-1,1]){ctx.fillStyle=p.bot;ctx.beginPath();ctx.ellipse(B.x+s*B.rx*.7,this.y-U*.2,U*.22,U*.22,0,0,TAU);ctx.fill();ctx.fillStyle=p.top;ctx.beginPath();ctx.ellipse(B.x+s*B.rx*.85,this.y-U*.05,U*.17,U*.08,0,0,TAU);ctx.fill()}
    if(!refl){ctx.fillStyle=hexA(p.belly,.8);ctx.beginPath();ctx.ellipse(B.x,B.y+U*.05,B.rx*.45,B.ry*.62,0,0,TAU);ctx.fill();
      for(let i=0;i<2;i++){if(this.armHide[i]>.6)continue;const sh=this.shoulder(i),hd=this.hand(i);ctx.strokeStyle=p.top;ctx.lineWidth=U*.2;ctx.lineCap='round';ctx.beginPath();ctx.moveTo(sh[0],sh[1]);ctx.lineTo(hd[0],hd[1]);ctx.stroke();ctx.fillStyle=p.face;ctx.beginPath();ctx.ellipse(hd[0],hd[1],U*.12,U*.085,0,0,TAU);ctx.fill();ctx.fillStyle=hexA(p.inner,.8);ctx.beginPath();ctx.arc(hd[0],hd[1]+U*.02,U*.03,0,TAU);ctx.fill()}}
    // pointy ears
    const flat=this.mood==='sleep'||F.eye==='cry'?.5:F.eye==='wow'?-.1:0;for(const s of[-1,1]){const b=this.sp([s*.62,.78,.2]),r=this.rollAng()+s*(.25+flat);ctx.save();ctx.translate(b[0],b[1]);ctx.rotate(r);ctx.fillStyle=p.ear;ctx.beginPath();ctx.moveTo(-U*.24,U*.12);ctx.quadraticCurveTo(-U*.06,-U*.4,0,-U*.55);ctx.quadraticCurveTo(U*.08,-U*.4,U*.24,U*.12);ctx.closePath();ctx.fill();ctx.fillStyle=p.inner;ctx.beginPath();ctx.moveTo(-U*.12,U*.06);ctx.quadraticCurveTo(-U*.03,-U*.28,0,-U*.38);ctx.quadraticCurveTo(U*.04,-U*.28,U*.12,U*.06);ctx.closePath();ctx.fill();ctx.restore()}
    this.bodyPath(ctx,pts);g=ctx.createRadialGradient(hc[0]-U*.3,hc[1]-U*.4,U*.1,hc[0],hc[1],U*1.15);g.addColorStop(0,p.top);g.addColorStop(.7,p.top);g.addColorStop(1,p.bot);ctx.fillStyle=g;ctx.fill();
    if(!refl){this.furry(ctx,()=>this.bodyPath(ctx,pts),[p.top,p.stripe,'#ffffff'],'kit'+p.name,hc[0],hc[1],U);ctx.save();this.bodyPath(ctx,pts);ctx.clip();ctx.strokeStyle=hexA(p.stripe,.55);ctx.lineWidth=U*.05;ctx.lineCap='round';const tp=this.sp([0,.95,.3]);for(const dx of[-.18,0,.18]){ctx.beginPath();ctx.moveTo(tp[0]+dx*U,tp[1]+U*.02);ctx.lineTo(tp[0]+dx*U*.8,tp[1]+U*.3);ctx.stroke()}ctx.restore();this.fuzz(ctx,pts,hexA(p.top,.45),U*.018)}
    if(this.faceVisible()){ctx.save();const[fw,fh]=this.faceXf(ctx);ctx.drawImage(this.fc2,-fw/2,-fh/2,fw,fh);if(!refl){const nx=this.look[0]*U*.04,ny=-U*.03;ctx.fillStyle=p.nose;ctx.beginPath();ctx.moveTo(nx-U*.05,ny-U*.02);ctx.lineTo(nx+U*.05,ny-U*.02);ctx.lineTo(nx,ny+U*.035);ctx.closePath();ctx.fill();
      ctx.strokeStyle='rgba(255,255,255,.75)';ctx.lineWidth=1.2;for(const s of[-1,1])for(let i=0;i<3;i++){ctx.beginPath();ctx.moveTo(nx+s*U*.2,ny+U*.06+i*U*.035);ctx.quadraticCurveTo(nx+s*U*.45,ny+U*.03+i*U*.05,nx+s*U*.72,ny+i*U*.08+Math.sin(t*2+i)*U*.02);ctx.stroke()}}ctx.restore()}
    if(refl)return;
    if(ac&&ac.type==='laser'&&ac.dx!=null){ctx.globalCompositeOperation='lighter';glow(ctx,'#ff2a2a',ac.dx,ac.dy,U*.18,.9);ctx.globalCompositeOperation='source-over';ctx.fillStyle='#ff4040';ctx.beginPath();ctx.arc(ac.dx,ac.dy,U*.03,0,TAU);ctx.fill()}
    if(ac&&ac.type==='yarn'){const x=this.x+(ac.yx||0),y=this.floor-U*.12,r=U*.16;ctx.save();ctx.translate(x,y);ctx.rotate((ac.yx||0)/r);ctx.fillStyle='#ff5c8a';ctx.beginPath();ctx.arc(0,0,r,0,TAU);ctx.fill();ctx.strokeStyle='rgba(255,220,235,.8)';ctx.lineWidth=1.5;for(let i=0;i<4;i++){ctx.beginPath();ctx.arc(0,0,r*.8,i*.8,i*.8+2);ctx.stroke()}ctx.restore()}}
}

/* ---------------- PARROTS: budgie, cockatiel, lovebird, ringneck ---------------- */
const PARROT_KINDS={
 Budgie:{body:'#5ec8ff',belly:'#9fe3ff',wing:'#2f7de0',wing2:'#1a3f8a',head:'#ffffff',face:'#ffffff',beak:'#f2c94c',cere:'#3a6bd6',cheek:'#2f5fd0',tail:'#1a3f8a',spots:1},
 Cockatiel:{body:'#b8b8be',belly:'#d8d8de',wing:'#9a9aa2',wing2:'#ffffff',head:'#ffe66b',face:'#ffe66b',beak:'#c9a7a0',cheek:'#ff7a2a',tail:'#7a7a82',crest:'#ffd23d'},
 Lovebird:{body:'#4fd14a',belly:'#a6e86b',wing:'#2fa83a',wing2:'#2f7de0',head:'#ff4a3a',face:'#ff8a4a',beak:'#ffd23d',cheek:'#ff6a4a',tail:'#2f7de0'},
 Ringneck:{body:'#7ed957',belly:'#a8e878',wing:'#4fb83a',wing2:'#2f8a2a',head:'#86e05a',face:'#94e866',beak:'#e8323f',cheek:'#86e05a',tail:'#3f9a3a',ring:1,long:1}};
const PARROT_SND=Object.assign({},MOCHI2_SND,{tweet(){SFX.voice('tweet')},whistle(){[1320,1760,1568,2093].forEach((f,i)=>SFX.tone('sine',f,f*1.05,.12,.07,i*.13))},squawk(){SFX.tone('sawtooth',900,600,.18,.05);SFX.hiss(2000,.12,.1)},talk(){for(let i=0;i<4;i++)SFX.tone('square',rnd(500,900),rnd(500,900),.07,.025,i*.09)}});
const PARROT_PALS=[{name:'Natural',top:'#ffffff',bot:'#cccccc',rim:'#ffd59a',rim2:'#9fd8ff',glow:'#9fffb0',ink:'#1a0d10'},{name:'Warm glow',top:'#ffe0b0',bot:'#ffb36b',rim:'#ffb36b',rim2:'#ff6fae',glow:'#ffb36b',ink:'#1a0d10'},{name:'Cool glow',top:'#cfe2ff',bot:'#7fb0ff',rim:'#7fd8ff',rim2:'#c79bff',glow:'#7fd8ff',ink:'#1a0d10'},{name:'Neon',top:'#ffd0f0',bot:'#ff4fd8',rim:'#ff4fd8',rim2:'#38e8ff',glow:'#ff4fd8',ink:'#1a0d10'}];
class ParrotScene extends Critter{
  constructor(pv){super(pv,'parrot');this.snd=PARROT_SND;this.PALS=PARROT_PALS;this.defLook={sky:'aurora',ground:'grass'};this.loadLook();this.col=this.pal.glow;this.chargeText='Snacking on power seeds';this.strawToMouth=true;this.faceCute=false;
    this.variants=Object.keys(PARROT_KINDS);this.variant=store.get('parrot.kind','Ringneck');if(!PARROT_KINDS[this.variant])this.variant='Ringneck';
    this.S=this.u.S=[.78,.72,.74,.72];this.u.bulge=.02;this.SH=[.62,-.95,.2];this.armLen=1.05;this.uw=.27;this.uh=.145;this.bodyW=.82;this.hovK=.95;this.extra=['talk','whistle','headbob','flap','preen','seed','tilt'];
    Object.assign(this.DUR,{talk:2.6,whistle:2.6,headbob:3.2,flap:2.4,preen:3.4,seed:3.6,tilt:2.6})}
  setVariant(v){this.variant=v;store.set('parrot.kind',v);this.saveLook('kind',v)}
  extraHook(T2,k,ac,E,dt,A){const U=this.U,t=ac.t,s=this.sfx.bind(this);
    switch(T2){
    case'talk':{E.eye='wow';E.mo=Math.max(0,Math.sin(t*14))*.8;this.beakOpen=E.mo;if(!ac.s){ac.s=1;s('talk');this.bub={txt:['Hello!','Pretty bird!','Pulsify!','Habibi!','Love you!'][(Math.random()*5)|0],life:2}}return true}
    case'whistle':{E.eye='closed';E.open=0;this.beakOpen=.15;if(!ac.s){ac.s=1;s('whistle')}if(Math.random()<dt*5)this.fx.push({k:'note',x:this.sp([0,0,1])[0]+U*.3,y:this.sp([0,0,1])[1],vx:40,vy:-60,life:1.2,c:'#9fffb0'});return true}
    case'headbob':{this.lift=Math.abs(Math.sin(t*8))*U*.15;this.pitchT=Math.sin(t*8)*.2;E.eye='happy';return true}
    case'flap':{this.arm(0,2+Math.sin(t*20)*.6,.1);this.arm(1,2+Math.sin(t*20)*.6,.1);this.lift=U*.3*Math.sin(clamp(k,0,1)*Math.PI);E.eye='wow';if(!ac.s){ac.s=1;s('squawk')}return true}
    case'preen':{this.paw2[1]=null;this.arm(1,.6,.2);E.lookT=[.9,.6];E.yawT=.4;E.eye='closed';E.open=0;this.pitchT=.15;return true}
    case'seed':{this.pitchT=Math.max(0,Math.sin(t*4))*.25;E.lookT=[0,1];this.beakOpen=Math.max(0,Math.sin(t*12))*.4;if(Math.random()<dt*4)this.fx.push({k:'dot',x:this.x+rnd(-U,U)*.2,y:this.floor-U*.1,vx:rnd(-40,40),vy:-rnd(40,90),g:500,life:.6,c:'#e8c87a'});return true}
    case'tilt':{this.rollT=.35*Math.sin(clamp(k,0,1)*Math.PI);E.eye='wow';E.lookT=[.3,0];if(!ac.s){ac.s=1;s('tweet')}return true}}
    return false}
  drawCore(ctx,refl){const K=PARROT_KINDS[this.variant]||PARROT_KINDS.Ringneck,U=this.U,t=this.t,A=this.A||{},bass=A.active?A.bass:0,pts=this.silhouette(refl?24:40),hc=this.cenOf(pts),B=this.bodyBox(),F=this.F;
    if(!refl){ctx.globalCompositeOperation='lighter';glow(ctx,this.pal.glow,hc[0],hc[1]+U*.6,U*1.8,.14+bass*.25);ctx.globalCompositeOperation='source-over';
      // perch branch
      const by=this.floor+U*.04;const bg=ctx.createLinearGradient(0,by-U*.08,0,by+U*.1);bg.addColorStop(0,'#8a6a4a');bg.addColorStop(1,'#3a2a1c');ctx.fillStyle=bg;ctx.beginPath();ctx.moveTo(0,by-U*.06);ctx.quadraticCurveTo(this.w/2,by-U*.12,this.w,by-U*.03);ctx.lineTo(this.w,by+U*.11);ctx.quadraticCurveTo(this.w/2,by+U*.05,0,by+U*.1);ctx.fill();ctx.strokeStyle='rgba(255,230,200,.18)';ctx.lineWidth=1;for(let i=0;i<14;i++){const x=i*this.w/13;ctx.beginPath();ctx.moveTo(x,by-U*.04);ctx.lineTo(x+U*.15,by+U*.04);ctx.stroke()}}
    // long tail
    const tl=K.long?U*1.5:U*.8,tw=Math.sin(this.tailA*.3)*.08;ctx.save();ctx.translate(B.x,B.y+B.ry*.6);ctx.rotate(tw);ctx.fillStyle=K.tail;ctx.beginPath();ctx.moveTo(-U*.14,0);ctx.lineTo(-U*.05,tl);ctx.lineTo(U*.05,tl);ctx.lineTo(U*.14,0);ctx.fill();ctx.restore();
    // body teardrop
    const bodyPath=()=>{ctx.beginPath();ctx.ellipse(B.x,B.y,B.rx*.85,B.ry*1.15,B.r,0,TAU)};bodyPath();let g=ctx.createRadialGradient(B.x-U*.2,B.y-U*.3,U*.1,B.x,B.y,B.rx*1.4);g.addColorStop(0,K.belly);g.addColorStop(1,K.body);ctx.fillStyle=g;ctx.fill();
    if(!refl){ctx.save();bodyPath();ctx.clip();ctx.strokeStyle='rgba(255,255,255,.12)';ctx.lineWidth=1;for(let j=0;j<10;j++)for(let i=-4;i<5;i++){const x=B.x+i*U*.14+(j%2)*U*.07,y=B.y-B.ry+j*U*.15;ctx.beginPath();ctx.arc(x,y,U*.07,.3,Math.PI-.3);ctx.stroke()}ctx.restore()}
    // feet on the perch
    if(!refl)for(const s of[-1,1]){ctx.strokeStyle='#c9a7a0';ctx.lineWidth=U*.05;ctx.lineCap='round';const fx=B.x+s*U*.18;ctx.beginPath();ctx.moveTo(fx,this.y-U*.12);ctx.lineTo(fx,this.floor-U*.02);ctx.moveTo(fx-U*.07,this.floor);ctx.lineTo(fx+U*.07,this.floor);ctx.stroke()}
    // wings (the "arms")
    for(let i=0;i<2;i++){if(this.armHide[i]>.6)continue;const s=i?1:-1,sh=this.shoulder(i),hd=this.hand(i),dx=hd[0]-sh[0],dy=hd[1]-sh[1],L=Math.hypot(dx,dy),a=Math.atan2(dy,dx);ctx.save();ctx.translate(sh[0],sh[1]);ctx.rotate(a);const wg=ctx.createLinearGradient(0,0,L,0);wg.addColorStop(0,K.wing);wg.addColorStop(1,K.wing2);ctx.fillStyle=wg;ctx.beginPath();ctx.moveTo(0,-U*.14);ctx.quadraticCurveTo(L*.6,-U*.2*s*0-U*.18,L*1.08,0);ctx.quadraticCurveTo(L*.6,U*.18,0,U*.14);ctx.closePath();ctx.fill();
      if(!refl){ctx.strokeStyle='rgba(0,0,0,.18)';ctx.lineWidth=1;for(let f=1;f<5;f++){ctx.beginPath();ctx.moveTo(L*(.35+f*.12),-U*.1);ctx.lineTo(L*(.3+f*.12),U*.1);ctx.stroke()}}ctx.restore()}
    // ring around the neck
    if(K.ring&&!refl){const nk=this.sp([0,-.82,.6]);ctx.strokeStyle='#14100e';ctx.lineWidth=U*.06;ctx.beginPath();ctx.ellipse(nk[0],nk[1],U*.5,U*.16,0,.15,Math.PI-.15);ctx.stroke();ctx.strokeStyle='#ff8fc0';ctx.lineWidth=U*.035;ctx.beginPath();ctx.ellipse(nk[0],nk[1]+U*.05,U*.52,U*.17,0,.1,Math.PI-.1);ctx.stroke()}
    // head
    this.bodyPath(ctx,pts);g=ctx.createRadialGradient(hc[0]-U*.25,hc[1]-U*.35,U*.1,hc[0],hc[1],U*1.05);g.addColorStop(0,K.face);g.addColorStop(.7,K.head);g.addColorStop(1,K.body);ctx.fillStyle=g;ctx.fill();
    if(!refl){ctx.save();ctx.globalCompositeOperation='lighter';ctx.strokeStyle=hexA(this.pal.rim,.35);ctx.lineWidth=1.5;ctx.stroke();ctx.restore()}
    if(K.crest&&!refl){const tp=this.sp([0,.95,.1]);ctx.fillStyle=K.crest;for(let i=0;i<4;i++){const a=-.5+i*.18+Math.sin(t*2+i)*.05+(F.eye==='wow'?-.2:0);ctx.save();ctx.translate(tp[0],tp[1]);ctx.rotate(a+this.rollAng());ctx.beginPath();ctx.ellipse(0,-U*.35,U*.06,U*.38,0,0,TAU);ctx.fill();ctx.restore()}}
    if(this.faceVisible()&&!refl){ctx.save();this.faceXf(ctx);const fx=0,fy=0;
      ctx.fillStyle=hexA(K.cheek,K.spots?1:.8);for(const s of[-1,1]){ctx.beginPath();ctx.ellipse(s*U*.42,U*.12,U*.12,U*.1,0,0,TAU);ctx.fill()}
      if(K.spots){ctx.fillStyle='#1a2f6a';for(const s of[-1,1])for(const k of[0,1,2]){ctx.beginPath();ctx.arc(s*U*(.12+k*.12),U*.42,U*.03,0,TAU);ctx.fill()}}
      for(let i=0;i<2;i++){const s=i?1:-1,ex=s*U*.34+this.look[0]*U*.05,ey=-U*.1;ctx.fillStyle=K.ring?'#ff9a4d':'rgba(255,255,255,.0)';if(K.ring){ctx.beginPath();ctx.arc(ex,ey,U*.17,0,TAU);ctx.fill()}cuteEye(ctx,ex,ey,U*.14,F,t,i,'#4a2a10')}
      // beak
      const bo=this.beakOpen||0;this.beakOpen=lerp(bo,0,.15);if(K.cere){ctx.fillStyle=K.cere;ctx.beginPath();ctx.ellipse(0,-U*.02,U*.13,U*.06,0,0,TAU);ctx.fill()}
      const bg2=ctx.createLinearGradient(-U*.15,0,U*.15,U*.35);bg2.addColorStop(0,K.beak);bg2.addColorStop(1,hexA(K.beak,.75));ctx.fillStyle=bg2;ctx.beginPath();ctx.moveTo(-U*.15,U*.02);ctx.quadraticCurveTo(0,-U*.04,U*.15,U*.02);ctx.quadraticCurveTo(U*.13,U*.25,0,U*.38);ctx.quadraticCurveTo(-U*.13,U*.25,-U*.15,U*.02);ctx.fill();
      if(bo>.05){ctx.fillStyle='#3a1418';ctx.beginPath();ctx.ellipse(0,U*.2,U*.07,U*.1*bo,0,0,TAU);ctx.fill()}ctx.fillStyle='rgba(255,255,255,.35)';ctx.beginPath();ctx.ellipse(-U*.06,U*.06,U*.03,U*.06,-.4,0,TAU);ctx.fill();ctx.restore()}}
}

/* ---------------- GLOWY: an anglerfish with a shining lure ---------------- */
const FISH_PALS=[
 {name:'Amber',top:'#ffcf8a',bot:'#c8743a',fin:'#ffb36b',lure:'#ffe066',face:'#ffe2b8',ink:'#2a1206',glow:'#ffb347',rim:'#ffd59a',rim2:'#ff6fae',skin:'#ffcf8a',lid:'#ffe2b8',belly:'#fff0d6'},
 {name:'Ghost',top:'#d8e8ff',bot:'#7a8cc8',fin:'#a8c4ff',lure:'#9fffe8',face:'#eef4ff',ink:'#0e1a33',glow:'#7fd8ff',rim:'#cfe2ff',rim2:'#9fffe8',skin:'#d8e8ff',lid:'#eef4ff',belly:'#ffffff'},
 {name:'Coral',top:'#ffb0c8',bot:'#d8406a',fin:'#ff8fb0',lure:'#ffd0f0',face:'#ffd6e4',ink:'#3a0c1a',glow:'#ff6fae',rim:'#ffc2e6',rim2:'#ffd23d',skin:'#ffb0c8',lid:'#ffd6e4',belly:'#fff0f6'},
 {name:'Abyss',top:'#5a5a8a',bot:'#1c1c3a',fin:'#7a6ac8',lure:'#7dffea',face:'#6a6a9a',ink:'#ffffff',glow:'#38e8ff',rim:'#9f9fff',rim2:'#38e8ff',skin:'#5a5a8a',lid:'#6a6a9a',belly:'#7a7aaa'}];
const FISH_SND=Object.assign({},MOCHI2_SND,{blub(){SFX.tone('sine',300,700,.12,.12);SFX.tone('sine',400,900,.1,.08,.12)},gulp2(){SFX.tone('sine',200,120,.15,.18)}});
class FishScene extends Critter{
  constructor(pv){super(pv,'glowy');this.snd=FISH_SND;this.PALS=FISH_PALS;this.defLook={sky:'ocean',ground:'sand'};this.loadLook();this.col=this.pal.glow;this.chargeText='Recharging its lure';this.snotOn=false;
    this.S=this.u.S=[.8,.76,.95,.76];this.u.bulge=.03;this.SH=[.85,-.25,.3];this.armLen=.42;this.uw=.27;this.uh=.15;this.hovK=1.6;this.extra=['luredance','minnows','bubbles2','puff2','swim','yawn2'];
    Object.assign(this.DUR,{luredance:3.4,minnows:6,bubbles2:3.6,puff2:2.6,swim:5,yawn2:2.6});this.mins=[];this.bubs2=[]}
  update(dt,A){super.update(dt,A);this.lift=Math.sin(this.t*1.3)*this.U*.12;const ac=this.act;if(!(ac&&ac.type==='swim'))this.xOffT=Math.sin(this.t*.25)*this.w*.06;
    if(Math.random()<dt*.8)this.bubs2.push({x:this.x+this.xOff+rnd(-this.U,this.U)*.4,y:this.y-this.hov-this.U*.2,r:rnd(2,5),v:rnd(30,60),life:4});for(let i=this.bubs2.length-1;i>=0;i--){const b=this.bubs2[i];b.y-=b.v*dt;b.x+=Math.sin(this.t*3+b.r)*8*dt;b.life-=dt;if(b.life<=0)this.bubs2.splice(i,1)}
    for(let i=this.mins.length-1;i>=0;i--){const m=this.mins[i];m.x+=m.vx*dt;m.y+=Math.sin(this.t*3+m.ph)*10*dt;if(m.eaten||m.x<-40||m.x>this.w+40)this.mins.splice(i,1)}}
  extraHook(T2,k,ac,E,dt,A){const U=this.U,t=ac.t,s=this.sfx.bind(this);
    switch(T2){
    case'luredance':{this.ballBoost=1;this.antV+=Math.sin(t*12)*dt*40;E.eye='happy';E.mouth='open';E.mo=.4;return true}
    case'minnows':{if(!ac.m){ac.m=1;for(let i=0;i<5;i++)this.mins.push({x:Math.random()<.5?-30:this.w+30,y:this.y-this.hov-U*rnd(1,1.6),vx:0,ph:rnd(TAU),c:['#9fffe8','#ffe066','#ff8fd0'][i%3]});this.mins.forEach(m=>m.vx=(m.x<0?1:-1)*rnd(40,70))}const tip=this.tipScr();for(const m of this.mins){m.vx=lerp(m.vx,(tip[0]-m.x)*.8,.02)}
      this.focus=tip;E.eye='wow';E.mouth='o';E.mo=.2;if(k>.75&&!ac.c){ac.c=1;E.mouth='open';this.mins.forEach(m=>m.eaten=1);s('gulp2');this.puffT=.8;this.sqv-=2;haptic(12)}if(k>.75){E.eye='happy';E.mouth='smile'}return true}
    case'bubbles2':{E.mouth='o';E.mo=.3;if(Math.random()<dt*8){const m=this.sp([0,-.2,1]);this.bubs2.push({x:m[0],y:m[1],r:rnd(3,8),v:rnd(40,80),life:4})}if(Math.random()<dt*2)s('blub');return true}
    case'puff2':{this.puffT=Math.sin(clamp(k,0,1)*Math.PI)*1.2;E.eye='wow';E.mouth='o';return true}
    case'swim':{this.xOffT=Math.sin(k*TAU)*this.w*.3;this.yawT=Math.cos(k*TAU)*.6;E.yawT=this.yawT;E.eye='happy';return true}
    case'yawn2':{E.mouth='open';E.mo=Math.sin(clamp(k,0,1)*Math.PI);E.eye='closed';E.open=0;return true}}
    return false}
  tipScr(){const top=this.sp([0,1,.3]),U=this.U,a=this.antA*.8+this.rollAng()+.35;return[top[0]+Math.sin(a)*U*.9,top[1]-Math.cos(a)*U*.55+U*.15]}
  drawCore(ctx,refl){const p=this.pal,U=this.U,t=this.t,A=this.A||{},bass=A.active?A.bass:0,R=1+this.puff*.3,pts=this.silhouette(refl?24:40),hc=this.cenOf(pts),F=this.F;
    ctx.save();ctx.translate(hc[0],hc[1]);ctx.scale(R,R);ctx.translate(-hc[0],-hc[1]);
    // tail fin + top fin
    const tw=Math.sin(t*4)*.25,dir=1;ctx.save();ctx.translate(hc[0]-U*.85,hc[1]+U*.05);ctx.rotate(tw);ctx.fillStyle=hexA(p.fin,.85);ctx.beginPath();ctx.moveTo(0,0);ctx.quadraticCurveTo(-U*.5,-U*.6,-U*.6,-U*.45);ctx.quadraticCurveTo(-U*.35,0,-U*.6,U*.45);ctx.quadraticCurveTo(-U*.5,U*.6,0,0);ctx.fill();ctx.restore();
    ctx.fillStyle=hexA(p.fin,.7);const fp=this.sp([-.2,.9,0]);ctx.beginPath();ctx.moveTo(fp[0]-U*.4,fp[1]+U*.1);ctx.quadraticCurveTo(fp[0]-U*.1,fp[1]-U*.4+Math.sin(t*3)*U*.05,fp[0]+U*.25,fp[1]+U*.05);ctx.fill();
    // body
    this.bodyPath(ctx,pts);let g=ctx.createRadialGradient(hc[0]-U*.3,hc[1]-U*.35,U*.1,hc[0],hc[1],U*1.15);g.addColorStop(0,p.top);g.addColorStop(.6,p.bot);g.addColorStop(1,hexA(p.bot,1));ctx.fillStyle=g;ctx.fill();
    if(!refl){ctx.save();ctx.clip();ctx.fillStyle=hexA(p.belly,.55);ctx.beginPath();ctx.ellipse(hc[0],hc[1]+U*.45,U*.7,U*.4,0,0,TAU);ctx.fill();ctx.fillStyle='rgba(255,255,255,.18)';for(let i=0;i<12;i++){ctx.beginPath();ctx.arc(hc[0]+Math.cos(i*2.3)*U*.6,hc[1]+Math.sin(i*1.7)*U*.5,U*.04,0,TAU);ctx.fill()}ctx.fillStyle='rgba(255,255,255,.35)';ctx.beginPath();ctx.ellipse(hc[0]-U*.35,hc[1]-U*.45,U*.25,U*.1,-.5,0,TAU);ctx.fill();ctx.restore()
      ctx.save();ctx.globalCompositeOperation='lighter';ctx.strokeStyle=hexA(p.rim,.5);ctx.lineWidth=2;ctx.stroke();ctx.restore()}
    // pectoral fins (the "arms")
    for(let i=0;i<2;i++){if(this.armHide[i]>.6)continue;const sh=this.shoulder(i),hd=this.hand(i),fl=Math.sin(t*5+i)*.2;ctx.fillStyle=hexA(p.fin,.85);ctx.beginPath();ctx.moveTo(sh[0],sh[1]-U*.06);ctx.quadraticCurveTo(hd[0]+(i?1:-1)*U*.1,hd[1]-U*.1+fl*U*.2,hd[0],hd[1]+U*.08);ctx.quadraticCurveTo(sh[0],sh[1]+U*.1,sh[0],sh[1]-U*.06);ctx.fill()}
    // face
    if(this.faceVisible()&&!refl){ctx.save();this.faceXf(ctx);for(let i=0;i<2;i++){const s=i?1:-1;ctx.fillStyle=hexA(p.face,.9);ctx.beginPath();ctx.arc(s*U*.36+this.look[0]*U*.04,-U*.15,U*.25,0,TAU);ctx.fill();cuteEye(ctx,s*U*.36+this.look[0]*U*.06,-U*.15,U*.21,F,t,i,'#6a3a10')}
      const mo=clamp(F.mouthOpen,0,1),m=F.mouth,mw=U*.38;ctx.fillStyle='#2a0a14';ctx.beginPath();ctx.moveTo(-mw,U*.28);ctx.quadraticCurveTo(0,U*(.36+mo*.4),mw,U*.28);ctx.quadraticCurveTo(0,U*(.3-(m==='frown'?-.1:.06)),-mw,U*.28);ctx.fill();ctx.fillStyle='#fff';for(let k2=-3;k2<=3;k2++){const x=k2*mw*.26;ctx.beginPath();ctx.moveTo(x-U*.025,U*.29);ctx.lineTo(x,U*.29+U*.06*(1-Math.abs(k2)/4));ctx.lineTo(x+U*.025,U*.29);ctx.fill()}
      if(F.blush>.9){ctx.fillStyle='rgba(255,110,150,.35)';for(const s of[-1,1]){ctx.beginPath();ctx.ellipse(s*U*.6,U*.12,U*.1,U*.06,0,0,TAU);ctx.fill()}}ctx.restore()}
    ctx.restore();if(refl)return;
    // the lure: a stalk from the forehead with a glowing bulb
    const top=this.sp([0,.95,.3]),tip=this.tipScr(),lc=this.ballCol&&this.ballCol.length===7&&this.ballT?this.ballCol:p.lure,pulse=.75+.25*Math.sin(t*2.5)+(this.ballBoost||0)*.5+bass*.5;ctx.strokeStyle=p.bot;ctx.lineWidth=U*.045;ctx.lineCap='round';ctx.beginPath();ctx.moveTo(top[0],top[1]);ctx.quadraticCurveTo(top[0]+U*.35,top[1]-U*.7,tip[0],tip[1]);ctx.stroke();
    ctx.globalCompositeOperation='lighter';glow(ctx,lc,tip[0],tip[1],U*(.55+.25*pulse),.75*pulse*this.lum);glow(ctx,lc,tip[0],tip[1],U*1.4,.12*pulse);ctx.globalCompositeOperation='source-over';ctx.fillStyle='#fffbe0';ctx.beginPath();ctx.arc(tip[0],tip[1],U*.07,0,TAU);ctx.fill();
    for(const m of this.mins){ctx.fillStyle=m.c;ctx.save();ctx.translate(m.x,m.y);ctx.scale(m.vx>0?1:-1,1);ctx.beginPath();ctx.ellipse(0,0,U*.08,U*.035,0,0,TAU);ctx.fill();ctx.beginPath();ctx.moveTo(-U*.07,0);ctx.lineTo(-U*.13,-U*.04);ctx.lineTo(-U*.13,U*.04);ctx.fill();ctx.restore()}
    ctx.strokeStyle='rgba(180,230,255,.55)';ctx.lineWidth=1;for(const b of this.bubs2){ctx.globalAlpha=Math.min(1,b.life);ctx.beginPath();ctx.arc(b.x,b.y,b.r,0,TAU);ctx.stroke()}ctx.globalAlpha=1}
}

/* ---------------- GROMBLE: a tiny imp who really, really wants to be evil ---------------- */
const IMP_PALS=[
 {name:'Shadow',top:'#5a4a7a',bot:'#241a3a',wing:'#8a3a5a',horn:'#2a2030',face:'#6a5a8a',ink:'#ffffff',glow:'#b04dff',rim:'#d06bff',rim2:'#ff4f6a',skin:'#5a4a7a',lid:'#6a5a8a',belly:'#7a6a9a'},
 {name:'Ember',top:'#ff7a5a',bot:'#a8202a',wing:'#5a1018',horn:'#2a1010',face:'#ff9a7a',ink:'#2a0606',glow:'#ff4a2a',rim:'#ffb36b',rim2:'#ffd23d',skin:'#ff7a5a',lid:'#ff9a7a',belly:'#ffb89a'},
 {name:'Soot',top:'#3a3a3e',bot:'#121214',wing:'#5a2a6a',horn:'#e8e0f0',face:'#4a4a50',ink:'#ffffff',glow:'#ff4fd8',rim:'#ff8fe8',rim2:'#38e8ff',skin:'#3a3a3e',lid:'#4a4a50',belly:'#56565c',rainbow:1},
 {name:'Slime',top:'#8aff7a',bot:'#2a8a3a',wing:'#1a5a2a',horn:'#e8ff9a',face:'#a8ffa0',ink:'#0a2a0a',glow:'#7dff6b',rim:'#c8ffb0',rim2:'#ffd23d',skin:'#8aff7a',lid:'#a8ffa0',belly:'#c8ffc0'}];
const IMP_SND=Object.assign({},MOCHI2_SND,{laugh(){[0,1,2,3].forEach(i=>SFX.tone('sawtooth',260-i*20,200-i*20,.14,.05,i*.18))},cough(){SFX.hiss(1200,.1,.15);SFX.hiss(1000,.1,.13,.18)},poof(){SFX.hiss(600,.3,.2)},grr(){SFX.tone('sawtooth',140,110,.4,.05)}});
class ImpScene extends Critter{
  constructor(pv){super(pv,'gromble');this.snd=IMP_SND;this.PALS=IMP_PALS;this.defLook={sky:'sunset',ground:'moon'};this.loadLook();this.col=this.pal.glow;this.chargeText='Charging evil powers';this.strawToMouth=true;this.snotOn=true;
    this.S=this.u.S=[.88,.8,.92,.8];this.u.bulge=.05;this.SH=[.45,-.85,.6];this.armLen=.65;this.uw=.27;this.uh=.145;this.hovK=.75;this.bodyW=.5;this.extra=['evillaugh','scheme','fireball','trident','scare','cape'];
    Object.assign(this.DUR,{evillaugh:3.4,scheme:4.4,fireball:3.6,trident:3,scare:3,cape:3.2});this.smoke=[]}
  moodHook(m,E,dt,A){MochiScene.prototype.moodHook.call(this,m,E,dt,A);if(m!=='sleep'&&m!=='dance'){E.brow=.85;E.browOn=true}}
  extraHook(T2,k,ac,E,dt,A){const U=this.U,t=ac.t,s=this.sfx.bind(this);E.browOn=true;
    switch(T2){
    case'evillaugh':{if(k<.7){E.brow=1;E.eye='squeeze';E.mouth='grin';this.arm(0,-.4,1.1+Math.sin(t*14)*.15);this.arm(1,-.4,1.1-Math.sin(t*14)*.15);this.shv[0]+=Math.sin(t*30)*dt;if(!ac.l){ac.l=1;s('laugh');this.bub={txt:'mwahaha!',life:1.6,col:'#ff3d6e'}}}else{E.brow=-.6;E.eye='cry';E.mouth='o';E.mo=.6;this.paw2[1]={pt:[.05,-.1,1],r:.15};if(!ac.c){ac.c=1;s('cough');this.bub={txt:'*cough*',life:1}}}return true}
    case'scheme':{this.hold2();E.eye=k<.6?'side':'wow';E.mouth=k<.6?'grin':'o';E.lookT=[.2,.5];ac.plan=1;if(k>.6&&!ac.f){ac.f=1;this.sqv+=3;this.burst(this.handsMid()[0],this.handsMid()[1],8,'#ffffff');this.bub={txt:'oops',life:1.2}}if(k>.6){ac.plan=0;E.brow=-.5}return true}
    case'fireball':{this.arm(1,2.1,1.1);E.eye='wow';E.mouth='grin';E.brow=1;const h=this.hand(1);if(k<.55){ac.fb=k/.55}else if(!ac.p){ac.p=1;ac.fb=0;s('poof');for(let i=0;i<10;i++)this.smoke.push({x:h[0],y:h[1]-U*.1,vx:rnd(-30,30),vy:-rnd(30,70),r:rnd(6,14),life:1.4})}if(k>.55){E.eye='teary';E.mouth='wobble';E.brow=-.6}return true}
    case'trident':{this.arm(1,1.2+Math.max(0,Math.sin(t*9))*.6,1.2);E.eye='side';E.mouth='grin';E.brow=1;if(k>.7&&!ac.tr){ac.tr=1;this.vy=-this.h*.12;this.ground=false;this.rollT=.6;this.dz=1.2;s('boing');haptic(10)}return true}
    case'scare':{if(k<.4){E.eye='side';E.mouth='grin';this.lift=-U*.25}else if(k<.65){this.arm(0,2.5,.6);this.arm(1,2.5,.6);E.eye='wow';E.mouth='open';E.mo=1;if(!ac.b){ac.b=1;s('grr');this.bub={txt:'BOO!',life:1};this.sqv-=3}}else{this.paw2[0]={pt:[-.3,.16,.95],r:.24};this.paw2[1]={pt:[.3,.16,.95],r:.24};E.eye='closed';E.blush=1.4;E.brow=-.5}return true}
    case'cape':{ac.cape=Math.sin(clamp(k,0,1)*Math.PI);this.arm(1,2.4,.3);E.eye='side';E.mouth='grin';E.brow=1;if(k>.8){E.eye='spiral';E.mouth='wobble'}return true}}
    return false}
  update(dt,A){super.update(dt,A);for(let i=this.smoke.length-1;i>=0;i--){const q=this.smoke[i];q.x+=q.vx*dt;q.y+=q.vy*dt;q.r+=dt*12;q.life-=dt;if(q.life<=0)this.smoke.splice(i,1)}}
  drawCore(ctx,refl){const p=this.pal,U=this.U,t=this.t,A=this.A||{},bass=A.active?A.bass:0,pts=this.silhouette(refl?24:40),hc=this.cenOf(pts),B=this.bodyBox(),ac=this.act,F=this.F;
    if(!refl){ctx.globalCompositeOperation='lighter';glow(ctx,p.glow,hc[0],hc[1]+U*.4,U*1.8,.18+bass*.25);ctx.globalCompositeOperation='source-over'}
    // bat wings flapping behind
    const fl=Math.sin(t*(this.mood==='dance'?12:4))*.25;for(const s of[-1,1]){ctx.save();ctx.translate(B.x+s*U*.3,B.y-B.ry*.6);ctx.rotate(s*(.3+fl));ctx.scale(s,1);ctx.fillStyle=p.wing;ctx.beginPath();ctx.moveTo(0,0);ctx.lineTo(U*.95,-U*.55);ctx.quadraticCurveTo(U*.8,-U*.15,U*.95,0);ctx.quadraticCurveTo(U*.65,-U*.05,U*.62,U*.18);ctx.quadraticCurveTo(U*.38,0,U*.3,U*.25);ctx.quadraticCurveTo(U*.15,U*.05,0,U*.1);ctx.fill();ctx.restore()}
    // devil tail
    ctx.save();ctx.translate(B.x+U*.3,B.y+B.ry*.5);ctx.strokeStyle=p.bot;ctx.lineWidth=U*.07;ctx.lineCap='round';const tw=Math.sin(this.tailA*.5)*.3;ctx.beginPath();ctx.moveTo(0,0);ctx.bezierCurveTo(U*.5,U*.1,U*.4,-U*.5,U*(.7+tw*.2),-U*.6);ctx.stroke();ctx.fillStyle=p.bot;ctx.translate(U*(.7+tw*.2),-U*.6);ctx.beginPath();ctx.moveTo(0,-U*.12);ctx.lineTo(U*.1,U*.05);ctx.lineTo(-U*.1,U*.05);ctx.fill();ctx.restore();
    // body + little legs
    for(const s of[-1,1]){ctx.fillStyle=p.bot;ctx.beginPath();ctx.ellipse(B.x+s*U*.22,this.y-U*.08,U*.14,U*.1,0,0,TAU);ctx.fill()}
    const bodyPath=()=>{ctx.beginPath();ctx.ellipse(B.x,B.y,B.rx,B.ry,B.r,0,TAU)};bodyPath();let g=ctx.createRadialGradient(B.x-U*.15,B.y-U*.2,U*.05,B.x,B.y,B.rx*1.4);g.addColorStop(0,p.top);g.addColorStop(1,p.bot);ctx.fillStyle=g;ctx.fill();if(!refl){this.furry(ctx,bodyPath,[p.top,p.bot],'imp'+p.name,B.x,B.y,U);ctx.fillStyle=hexA(p.belly,.6);ctx.beginPath();ctx.ellipse(B.x,B.y+U*.04,B.rx*.5,B.ry*.6,0,0,TAU);ctx.fill()}
    if(ac&&ac.type==='cape'&&!refl){const c=ac.cape||0;ctx.fillStyle='#b0102a';ctx.beginPath();const nk=this.sp([0,-.85,.3]);ctx.moveTo(nk[0]-U*.35,nk[1]);ctx.quadraticCurveTo(nk[0]-U*(.6+c*.6),nk[1]+U*.5,nk[0]-U*(.4+c*.9),this.y);ctx.lineTo(nk[0]+U*(.4+c*.3),this.y);ctx.quadraticCurveTo(nk[0]+U*.5,nk[1]+U*.4,nk[0]+U*.35,nk[1]);ctx.fill()}
    // arms
    if(!refl)for(let i=0;i<2;i++){if(this.armHide[i]>.6)continue;const sh=this.shoulder(i),hd=this.hand(i);ctx.strokeStyle=p.top;ctx.lineWidth=U*.13;ctx.lineCap='round';ctx.beginPath();ctx.moveTo(sh[0],sh[1]);ctx.lineTo(hd[0],hd[1]);ctx.stroke();ctx.fillStyle=p.top;ctx.beginPath();ctx.arc(hd[0],hd[1],U*.08,0,TAU);ctx.fill();ctx.fillStyle=p.rainbow?`hsl(${(t*60+i*90)%360},90%,65%)`:'#1a1020';for(let k=-1;k<=1;k++){ctx.beginPath();ctx.arc(hd[0]+k*U*.05,hd[1]+U*.07,U*.018,0,TAU);ctx.fill()}}
    // horns
    for(const s of[-1,1]){const b=this.sp([s*.5,.82,.2]),r=this.rollAng();ctx.save();ctx.translate(b[0],b[1]);ctx.rotate(r+s*.2);ctx.fillStyle=p.rainbow?(()=>{const lg=ctx.createLinearGradient(0,0,0,-U*.5);['#ff4040','#ffd23d','#4dff7a','#38c8ff','#c04dff'].forEach((c,i)=>lg.addColorStop(i/4,c));return lg})():p.horn;ctx.beginPath();ctx.moveTo(-U*.12,U*.05);ctx.quadraticCurveTo(-U*.05,-U*.3,s*U*.18,-U*.5);ctx.quadraticCurveTo(U*.02,-U*.2,U*.12,U*.05);ctx.closePath();ctx.fill();ctx.restore()}
    // fuzzy head
    this.bodyPath(ctx,pts);g=ctx.createRadialGradient(hc[0]-U*.3,hc[1]-U*.4,U*.1,hc[0],hc[1],U*1.15);g.addColorStop(0,p.top);g.addColorStop(.7,p.top);g.addColorStop(1,p.bot);ctx.fillStyle=g;ctx.fill();if(!refl){this.furry(ctx,()=>this.bodyPath(ctx,pts),[p.top,p.bot],'imp'+p.name,hc[0],hc[1],U)}
    // pointy ears
    if(!refl)for(const s of[-1,1]){const b=this.sp([s*.95,.25,0]);ctx.fillStyle=p.top;ctx.beginPath();ctx.moveTo(b[0],b[1]-U*.12);ctx.lineTo(b[0]+s*U*.32,b[1]-U*.22);ctx.lineTo(b[0],b[1]+U*.12);ctx.fill()}
    if(this.faceVisible()){ctx.save();const[fw,fh]=this.faceXf(ctx);ctx.drawImage(this.fc2,-fw/2,-fh/2,fw,fh);if(!refl&&(F.mouth==='grin'||F.mouth==='open'||F.mouth==='cat'||F.mouth==='smile')){ctx.fillStyle='#fff';for(const s of[-1,1]){ctx.beginPath();ctx.moveTo(s*U*.1,U*.05);ctx.lineTo(s*U*.04,U*.05);ctx.lineTo(s*U*.07,U*.15);ctx.fill()}}ctx.restore()}
    if(refl)return;
    // props: evil plan scroll, fireball, trident, smoke
    if(ac&&ac.type==='scheme'&&ac.plan){const h=this.handsMid();ctx.fillStyle='#f2e6c8';ctx.fillRect(h[0]-U*.35,h[1]-U*.28,U*.7,U*.4);ctx.strokeStyle='#8a5a2a';ctx.lineWidth=1.5;ctx.strokeRect(h[0]-U*.35,h[1]-U*.28,U*.7,U*.4);ctx.fillStyle='#b0102a';ctx.font=`800 ${U*.1}px system-ui`;ctx.textAlign='center';ctx.fillText('EVIL PLAN',h[0],h[1]-U*.14);ctx.strokeStyle='#3a2a1a';ctx.beginPath();ctx.arc(h[0]-U*.1,h[1]+U*.02,U*.06,0,TAU);ctx.moveTo(h[0]-U*.04,h[1]+U*.02);ctx.lineTo(h[0]+U*.2,h[1]+U*.02);ctx.stroke()}
    if(ac&&ac.type==='fireball'&&ac.fb){const h=this.hand(1),r=U*(.05+ac.fb*.18);ctx.globalCompositeOperation='lighter';glow(ctx,'#ff7a1a',h[0],h[1]-U*.15,r*2.5,.9);glow(ctx,'#ffd23d',h[0],h[1]-U*.15,r,1);ctx.globalCompositeOperation='source-over'}
    if(ac&&ac.type==='trident'){const h=this.hand(1);ctx.save();ctx.translate(h[0],h[1]);ctx.rotate(-.4);ctx.strokeStyle='#d8c060';ctx.lineWidth=U*.035;ctx.beginPath();ctx.moveTo(0,U*.3);ctx.lineTo(0,-U*.5);ctx.moveTo(-U*.1,-U*.38);ctx.lineTo(-U*.1,-U*.55);ctx.moveTo(U*.1,-U*.38);ctx.lineTo(U*.1,-U*.55);ctx.moveTo(-U*.1,-U*.38);ctx.lineTo(U*.1,-U*.38);ctx.stroke();ctx.restore()}
    for(const q of this.smoke){ctx.fillStyle=`rgba(120,110,130,${Math.min(.6,q.life*.5)})`;ctx.beginPath();ctx.arc(q.x,q.y,q.r,0,TAU);ctx.fill()}}
}
