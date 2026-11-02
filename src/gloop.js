/* ================= GLOOP GANG 2D — squishy jelly beasts that roam the whole screen =================
   Real soft bodies (a ring of springy points), googly eyes that follow your finger, 8 characters,
   6 scenes, ~22 behaviours (solo, pairs, whole-gang), candy you can drop, dancing + singing to music. */
const GLOOP_CAST=[
 {id:'minty',col:'#2dffb0',lite:'#d4fff0',dark:'#067a55',kind:'antenna',eyes:2,mouth:'grin'},
 {id:'rubi',col:'#ff3d6e',lite:'#ffd0dc',dark:'#7a0f2c',kind:'horns',eyes:2,fangs:1},
 {id:'sunny',col:'#ffd23d',lite:'#fff8cc',dark:'#9a6200',kind:'bunny',eyes:2,freck:1},
 {id:'grape',col:'#a35cff',lite:'#eadcff',dark:'#3d137a',kind:'cyclops',eyes:1},
 {id:'blu',col:'#38b8ff',lite:'#dcf3ff',dark:'#0b3d7a',kind:'spikes',eyes:2},
 {id:'peachy',col:'#ff8a4d',lite:'#ffe6d4',dark:'#7a2f0a',kind:'crown',eyes:2,freck:1},
 {id:'limo',col:'#a6ff3d',lite:'#f0ffd4',dark:'#3e7a0a',kind:'sprout',eyes:3},
 {id:'pinky',col:'#ff6fd8',lite:'#ffe0f7',dark:'#7a0f5c',kind:'cat',eyes:2,bow:1}];
const GLOOP_BGS=[['disco','Disco'],['ocean','Deep sea'],['candy','Candy land'],['space','Space'],['lava','Lava lamp'],['void','Void']];
const GLOOP_SOLO=['roam','roam','hop','spin','stretch','gum','blep','wave','sneeze','melt','shy','sing','peek','eyepop','chomp','laugh','nap','roam'];
const GLOOP_DUR={roam:3,hop:2.4,spin:1.8,stretch:2.6,gum:3.4,blep:2.2,wave:2.6,sneeze:2.4,melt:3.4,shy:2.6,sing:3.2,peek:4.2,eyepop:1.5,chomp:5,laugh:2.2,nap:5,poked:1.2};
class GloopScene extends Scene{
  constructor(pv){super(pv);this.variants=['Solo','Trio','Squad','Party'];this.variant=store.get('gloop.variant','Squad');if(!this.variants.includes(this.variant))this.variant='Squad';this.bg=store.get('gloop.bg','disco');this.light=store.get('gloop.light',1);
    this.blobs=null;this.fx=[];this.candy=null;this.fly=[];this.grp=null;this.grpT=rnd(10,18);this.beatN=0;this.bgKey=null;this.bub=[];this.scare=0}
  resize(w,h){super.resize(w,h);if(!this.blobs)this.spawn();else for(const b of this.blobs){b.hx=clamp(b.hx,b.R,w-b.R);b.hy=clamp(b.hy,b.R,h-b.R)}this.bgKey=null}
  setVariant(v){this.variant=v;store.set('gloop.variant',v);this.spawn()}
  controls(){return[{type:'buttons',label:'Scene',items:GLOOP_BGS.map(([id,n])=>({name:n,on:this.bg===id,fn:()=>{this.bg=id;this.bgKey=null;store.set('gloop.bg',id)}}))},
    {type:'slider',label:'Brightness',min:.2,max:1.5,step:.05,value:this.light,set:v=>{this.light=v;store.set('gloop.light',v)}},
    {type:'buttons',label:'Tricks',items:[{name:'✦ Surprise',fn:()=>this.surprise()},{name:'Candy',fn:()=>this.dropCandy(rnd(this.w*.25,this.w*.75),this.h*.25)},{name:'Stack',fn:()=>this.startGroup('stack')},{name:'Conga',fn:()=>this.startGroup('conga')},{name:'Tag',fn:()=>this.startGroup('tag')},{name:'Hug',fn:()=>this.startGroup('hug')}]}]}
  spawn(){const n={Solo:1,Trio:3,Squad:5,Party:8}[this.variant]||5,m=Math.min(this.w,this.h*.6),N=this.preview?16:20,W=this.w,H=this.h,off=(Math.random()*8)|0;this.blobs=[];
    for(let i=0;i<n;i++){const d=GLOOP_CAST[(i+off)%8],R=m*(n===1?.27:n===3?.18:n===5?.145:.115)*rnd(.9,1.1);let hx,hy,tries=0;
      do{hx=rnd(R*1.2,W-R*1.2);hy=rnd(H*.14+R,H*.86-R);tries++}while(tries<40&&this.blobs.some(o=>Math.hypot(o.hx-hx,o.hy-hy)<(o.R+R)*1.25));if(n===1){hx=W/2;hy=H*.48}
      const b=Object.assign({},d,{R,N,pts:[],hx,hy,gx:hx,gy:hy,act:null,actT:rnd(1,4),sx:1,sy:1,sxT:1,syT:1,rot:0,bumpA:-Math.PI/2,bumpK:0,melt:0,puff:0,blink:0,blinkT:rnd(1,4),eye:'n',mouth:'smile',mo:0,look:[0,0],seed:rnd(),grab:null,blush:.35,sad:0,cur:0,shake:0,gum:0,lean:0});
      for(let k=0;k<N;k++){const a=k/N*TAU;b.pts.push({x:hx+Math.cos(a)*R,y:hy+Math.sin(a)*R,vx:0,vy:0})}b.cx=hx;b.cy=hy;this.blobs.push(b)}}
  cen(b){let x=0,y=0;for(const p of b.pts){x+=p.x;y+=p.y}b.cx=x/b.N;b.cy=y/b.N;return[b.cx,b.cy]}
  kick(b,vx,vy){for(const q of b.pts){q.vx+=vx;q.vy+=vy}}
  startAct(b,type){b.act={type,t:0,dur:GLOOP_DUR[type]||2.5,side:Math.random()<.5?-1:1};const W=this.w,H=this.h,R=b.R;
    if(type==='roam'){b.hx=rnd(R*1.2,W-R*1.2);b.hy=rnd(H*.14+R,H*.86-R)}
    if(type==='peek'){b.act.ox=b.hx;b.act.oy=b.hy}
    if(type==='chomp'){this.fly.push({x:Math.random()<.5?-20:W+20,y:rnd(H*.2,H*.6),t:0,owner:b,ph:rnd(TAU)})}}
  surprise(){const r=Math.random();if(this.blobs.length>1&&r<.45)this.startGroup(['stack','conga','tag','hug'][(Math.random()*4)|0]);else{const b=this.blobs[(Math.random()*this.blobs.length)|0];this.startAct(b,GLOOP_SOLO[(Math.random()*GLOOP_SOLO.length)|0])}}
  startGroup(type){const bs=this.blobs;if(bs.length<2&&type!=='stack')return this.surprise();const g={type,t:0,dur:{stack:8,conga:10,tag:9,hug:5}[type]};
    if(type==='tag'||type==='hug'){const a=bs[(Math.random()*bs.length)|0];let c;do{c=bs[(Math.random()*bs.length)|0]}while(c===a);g.a=a;g.b=c;g.flee=[rnd(this.w),rnd(this.h)];g.fleeT=0;g.cool=0}
    if(type==='conga'){g.trail=[];g.lead=bs[0]}
    for(const b of bs)if(!(type==='tag'||type==='hug')||b===g.a||b===g.b)b.act=null;this.grp=g}
  dropCandy(x,y){this.candy={x,y,vy:0,t:0,c:['#ff4fd8','#38e8ff','#ffe066','#7dff6b'][(Math.random()*4)|0]};for(const b of this.blobs){b.act=null;b.cur=0}}
  hit(x,y){let best=null,bd=1e9;for(const b of this.blobs){const d=Math.hypot(x-b.cx,y-b.cy);if(d<b.R*1.25&&d<bd){bd=d;best=b}}return best}
  /* ----- input ----- */
  down(p){p.sx=p.x;p.sy=p.y;p.t0=this.t;const b=this.hit(p.x,p.y);
    if(b){let ki=0,kd=1e9;b.pts.forEach((q,k)=>{const d=Math.hypot(q.x-p.x,q.y-p.y);if(d<kd){kd=d;ki=k}});b.grab={p,k:ki};p.blob=b;b.act=null;b.eye='pop';haptic(6)}
    else for(const o of this.blobs){if(Math.hypot(o.cx-p.x,o.cy-p.y)<this.w*.45&&!o.grab){o.cur=1.6;o.curX=p.x;o.curY=p.y}}}
  up(p){const b=p.blob,dist=Math.hypot(p.x-p.sx,p.y-p.sy),quick=this.t-p.t0<.25&&dist<14,v=Math.hypot(p.vx||0,p.vy||0);
    if(b){b.grab=null;if(quick){for(const q of b.pts){const dx=q.x-p.x,dy=q.y-p.y,d=Math.hypot(dx,dy)+1;if(d<b.R*1.1){q.vx+=dx/d*b.R*6;q.vy+=dy/d*b.R*6}}this.startAct(b,['laugh','eyepop','shy','hop','poked'][(Math.random()*5)|0]);this.burst(p.x,p.y,8,[b.col,'#ffffff']);haptic(8)}
      else{const vx=clamp(p.vx||0,-2600,2600),vy=clamp(p.vy||0,-2600,2600);this.kick(b,vx*.7,vy*.7);if(v>1400){b.spinV=clamp(vx/600,-6,6);b.dizzy=1.6}}}
    else if(this.t-p.t0>.45&&dist<20)this.dropCandy(p.x,p.y);
    else if(v>2200&&dist>80){this.scare=1.3;for(const o of this.blobs){const dx=o.cx-p.x,dy=o.cy-p.y,d=Math.hypot(dx,dy)+1;this.kick(o,dx/d*o.R*7,dy/d*o.R*7);o.act=null}haptic(12)}
    else if(quick)this.burst(p.x,p.y,10,['#80FFDB','#FF4D6D','#FEE440','#CDB4FF'])}
  /* ----- behaviour ----- */
  update(dt,A){this.t+=dt;this.A=A;const W=this.w,H=this.h,bs=this.blobs,music=A.active&&A.level>.06;if(A.beat)this.beatN++;this.scare=Math.max(0,this.scare-dt);
    if(this.grp){const g=this.grp;g.t+=dt;if(g.t>g.dur){if(g.type==='stack')for(const b of bs)this.kick(b,rnd(-1,1)*b.R*5,-b.R*6);this.grp=null}}
    else if(bs.length>1&&!music){this.grpT-=dt;if(this.grpT<0){this.grpT=rnd(16,30);this.startGroup(['stack','conga','tag','hug','tag','hug'][(Math.random()*6)|0])}}
    // candy falls and gets eaten by whoever reaches it first
    const c=this.candy;if(c){c.t+=dt;c.vy=Math.min(c.vy+H*.4*dt,H*.25);c.y=Math.min(c.y+c.vy*dt,H*.88);for(const b of bs){if(Math.hypot(b.cx-c.x,b.cy-c.y)<b.R*.9&&!b.grab){this.candy=null;b.puff=.35;b.happyT=2;b.eye='heart';this.burst(c.x,c.y,14,[c.c,'#ffffff']);for(let i=0;i<4;i++)this.fx.push({k:'heart',x:b.cx+rnd(-b.R,b.R)*.5,y:b.cy-b.R,vx:rnd(-20,20),vy:-rnd(50,90),life:1.4,c:'#ff6fae'});for(const o of bs)if(o!==b)o.sad=1.8;haptic(10);break}}}
    for(const f of this.fly){f.t+=dt}
    for(const b of bs)this.think(b,dt,A,music);
    // physics
    const steps=2,h=dt/steps;
    for(let s=0;s<steps;s++){for(const b of bs){const[cx,cy]=this.cen(b),N=b.N,R=b.R*(1+(music?A.bass*.22:0)+b.puff);let mvx=0,mvy=0;for(const q of b.pts){mvx+=q.vx;mvy+=q.vy}mvx/=N;mvy/=N;
        const hk=b.grab?0:(music?4:2.4),hfx=(b.gx-cx)*hk,hfy=(b.gy-cy)*hk;
        for(let k=0;k<N;k++){const q=b.pts[k],a=k/N*TAU+b.rot;let rr=R;if(b.bumpK){const da=Math.atan2(Math.sin(a-b.bumpA),Math.cos(a-b.bumpA));rr*=1+b.bumpK*Math.exp(-da*da*6)}
          let ox=Math.cos(a)*rr*b.sx,oy=Math.sin(a)*rr*b.sy;if(b.melt>0&&oy>0)oy*=1-b.melt*.55;const tx=cx+ox,ty=cy+oy+(b.melt>0?b.melt*R*.35:0),pa=b.pts[(k+N-1)%N],pb=b.pts[(k+1)%N];
          q.vx+=((tx-q.x)*95+((pa.x+pb.x)/2-q.x)*40+hfx)*h;q.vy+=((ty-q.y)*95+((pa.y+pb.y)/2-q.y)*40+hfy)*h;
          if(b.grab){const gk=b.grab.k,cd=Math.min(Math.abs(k-gk),N-Math.abs(k-gk)),wg=Math.exp(-cd*cd/(2*(N*.12)**2)),gp=b.grab.p,ra=gk/N*TAU+b.rot;q.vx+=(gp.x+(Math.cos(a)-Math.cos(ra))*R*.45-q.x)*wg*160*h;q.vy+=(gp.y+(Math.sin(a)-Math.sin(ra))*R*.45-q.y)*wg*160*h}
          const rd=Math.pow(.02,h),gd=Math.pow(.5,h);q.vx=(mvx+(q.vx-mvx)*rd)*gd;q.vy=(mvy+(q.vy-mvy)*rd)*gd}
        for(const q of b.pts){q.x+=q.vx*h;q.y+=q.vy*h;const peek=b.act&&b.act.type==='peek',mx=peek?-b.R*2:4,Mx=peek?W+b.R*2:W-4;if(q.x<mx){q.x=mx;q.vx=Math.abs(q.vx)*.6}if(q.x>Mx){q.x=Mx;q.vx=-Math.abs(q.vx)*.6}if(q.y<4){q.y=4;q.vy=Math.abs(q.vy)*.6}if(q.y>H-4){q.y=H-4;q.vy=-Math.abs(q.vy)*.6}}}
      for(let i=0;i<bs.length;i++)for(let j=i+1;j<bs.length;j++){const a=bs[i],b=bs[j],dx=b.cx-a.cx,dy=b.cy-a.cy,d=Math.hypot(dx,dy)+.01,mn=(a.R*a.sx+b.R*b.sx)*.5*.95+(a.R+b.R)*.45*.95;if(d<mn){const o=(mn-d)/2,nx=dx/d,ny=dy/d;for(const q of a.pts){q.x-=nx*o;q.y-=ny*o;q.vx-=nx*o*5;q.vy-=ny*o*5}for(const q of b.pts){q.x+=nx*o;q.y+=ny*o;q.vx+=nx*o*5;q.vy+=ny*o*5}}}}
    for(let i=this.fx.length-1;i>=0;i--){const q=this.fx[i];q.life-=dt;q.vy+=(q.g||0)*dt;q.x+=q.vx*dt;q.y+=q.vy*dt;if(q.life<=0)this.fx.splice(i,1)}}
  think(b,dt,A,music){const W=this.w,H=this.h,R=b.R,t=this.t,ac=b.act,g=this.grp;
    let eye='n',mouth='smile',mo=.05+.05*Math.sin(t*2+b.seed*5),sx=1,sy=1,bumpK=0,blush=.35;b.gx=b.hx+Math.sin(t*.5+b.seed*9)*R*.25;b.gy=b.hy+Math.sin(t*1.1+b.seed*6)*R*.15;
    b.blinkT-=dt;if(b.blinkT<0){b.blink=.15;b.blinkT=rnd(2,5)}b.blink=Math.max(0,b.blink-dt);b.sad=Math.max(0,b.sad-dt);b.happyT=Math.max(0,(b.happyT||0)-dt);b.cur=Math.max(0,b.cur-dt);b.dizzy=Math.max(0,(b.dizzy||0)-dt);
    let lt=null;const P=this.ptr;if(P)lt=[P.x,P.y];else if(this.candy)lt=[this.candy.x,this.candy.y];else if(b.cur>0)lt=[b.curX,b.curY];
    if(b.spinV){b.rot+=b.spinV*dt;b.spinV*=Math.pow(.25,dt);if(Math.abs(b.spinV)<.05)b.spinV=0}
    // music: the gang dances together, mouths sing along to the mids
    if(music&&!g&&!b.grab){const mv=Math.floor(this.beatN/8)%4,ph=A.beatPhase||0,bell=Math.sin(ph*Math.PI),i=this.blobs.indexOf(b);mouth='open';mo=clamp(A.mid*1.3,.15,1);eye=this.beatN%8<2?'happy':'n';blush=.7;
      if(A.beat){this.kick(b,0,-R*(1.2+A.bass*1.6));if(Math.random()<.35)this.fx.push({k:'note',x:b.cx+rnd(-R,R)*.6,y:b.cy-R*1.1,vx:rnd(-20,20),vy:-70,life:1.4,c:b.col})}
      if(mv===0){sy=1-.12*bell;sx=1+.1*bell}
      if(mv===1){b.gx=b.hx+Math.sin((this.beatN+ph)*Math.PI)*R*.9*(i%2?1:-1);b.lean=Math.sin((this.beatN+ph)*Math.PI)*.2}
      if(mv===2){b.rot+=dt*TAU*.9*(i%2?1:-1)}
      if(mv===3){bumpK=.4;b.bumpA=-Math.PI/2+Math.sin((this.beatN+ph)*Math.PI)*1.2;eye='happy'}}
    else if(g&&(g.type==='stack'||g.type==='conga'||g.a===b||g.b===b))({eye,mouth,mo,sx,sy,blush}=this.groupThink(b,g,dt,{eye,mouth,mo,sx,sy,blush}));
    else if(this.candy&&!b.grab){b.gx=this.candy.x;b.gy=this.candy.y;eye='pop';mouth='open';mo=.5}
    else if(!b.grab){if(!ac){b.actT-=dt;if(b.actT<0){b.actT=rnd(2.5,6);this.startAct(b,GLOOP_SOLO[(Math.random()*GLOOP_SOLO.length)|0])}}
      if(b.cur>0){const dx=b.cx-b.curX,dy=b.cy-b.curY,d=Math.hypot(dx,dy)+1;b.gx=b.curX+dx/d*R*2.4;b.gy=b.curY+dy/d*R*2.4;eye='pop'}
      if(ac){ac.t+=dt;const k=ac.t/ac.dur,T=ac.type,bell=Math.sin(clamp(k,0,1)*Math.PI);
        if(T==='roam'){eye='n';mouth='smile'}
        if(T==='hop'){const n=Math.floor(k*3);if(n>(ac.n||0)-1&&n<3&&!ac['h'+n]){ac['h'+n]=1;this.kick(b,ac.side*R*1.2,-R*7)}sy=1-.15*Math.max(0,Math.sin(k*3*Math.PI));eye='happy';mouth='open';mo=.35}
        if(T==='spin'){b.rot+=dt*TAU*1.4*ac.side;eye=k>.75?'swirl':'happy';mouth='open';mo=.4}
        if(T==='stretch'){sy=1+.45*bell;sx=1-.2*bell;eye=bell>.4?'closed':'n';mouth='o';mo=bell}
        if(T==='gum'){b.gum=k<.8?ease.inOut(k/.8):0;eye=k<.8?'n':k<.9?'x':'happy';mouth=k<.8?'o':'open';mo=.2;if(k>.8&&!ac.pop){ac.pop=1;this.burst(b.cx,b.cy+R*.35,12,['#ff8fd0','#ffd0ec']);b.gumSplat=1.2}}
        if(T==='blep'){eye='wink';mouth='tongue';blush=.8}
        if(T==='wave'){bumpK=.5;b.bumpA=-Math.PI/2+ac.side*(1.05+Math.sin(ac.t*12)*.35);eye='happy';mouth='open';mo=.4;if(!ac.s){ac.s=1;this.fx.push({k:'txt',txt:'hi!',x:b.cx+ac.side*R*1.2,y:b.cy-R*1.2,vx:0,vy:-30,life:1.4,c:'#ffffff'})}}
        if(T==='sneeze'){if(k<.5){sy=1-.12*k*2;eye='squeeze';mouth='o';mo=k}else if(!ac.done){ac.done=1;const a=rnd(TAU);this.kick(b,Math.cos(a)*R*14,Math.sin(a)*R*10);b.spinV=ac.side*6;this.fx.push({k:'txt',txt:'achoo!',x:b.cx,y:b.cy-R*1.2,vx:0,vy:-40,life:1.2,c:'#9ff3ff'});this.burst(b.cx,b.cy+R*.3,10,['#9ff3ff','#ffffff'])}else{eye='swirl';mouth='open';mo=.3}}
        if(T==='melt'){const mk=k<.3?k/.3:k<.72?1:1-(k-.72)/.28;b.melt=mk;eye=mk>.5?'closed':'n';mouth=mk>.5?'wavy':'o';if(k>.72&&!ac.b){ac.b=1;this.kick(b,0,-R*6)}}
        if(T==='shy'){eye='happy';mouth='smile';blush=1.3;sx=.92;sy=.92;b.lean=Math.sin(ac.t*5)*.15;if(Math.random()<dt*3)this.fx.push({k:'heart',x:b.cx+rnd(-R,R)*.6,y:b.cy-R,vx:0,vy:-50,life:1.2,c:'#ff8fc0'})}
        if(T==='sing'){eye='closed';mouth='open';mo=.4+.4*Math.abs(Math.sin(ac.t*6));if(Math.random()<dt*4)this.fx.push({k:'note',x:b.cx+rnd(-R,R)*.5,y:b.cy-R,vx:rnd(-20,20),vy:-60,life:1.4,c:b.lite})}
        if(T==='peek'){const sd=ac.ox<W/2?-1:1;if(k<.35){b.gx=sd<0?-R*.35:W+R*.35;b.gy=ac.oy;eye='n'}else if(k<.7){b.gx=sd<0?-R*.35:W+R*.35;eye='n';b.lean=-sd*.3}else{b.gx=ac.ox;b.gy=ac.oy;eye='pop';mouth='open';mo=.8;if(!ac.boo){ac.boo=1;this.fx.push({k:'txt',txt:'boo!',x:sd<0?R*1.2:W-R*1.2,y:b.cy-R*1.1,vx:0,vy:-30,life:1.3,c:'#ffe066'})}}}
        if(T==='eyepop'){eye='pop';mouth='o';mo=.7;b.eyeBig=1.6}
        if(T==='poked'){eye='squeeze';mouth='open';mo=.5;blush=.9}
        if(T==='laugh'){eye='happy';mouth='open';mo=.6+.3*Math.sin(ac.t*30);b.shake=1;if(Math.random()<dt*5)this.fx.push({k:'txt',txt:Math.random()<.5?'ha':'he',x:b.cx+rnd(-R,R),y:b.cy-R,vx:rnd(-30,30),vy:-60,life:1,c:b.lite})}
        if(T==='nap'){eye='closed';mouth='smile';sy=.92;sx=1.05;if(Math.random()<dt*1.2)this.fx.push({k:'txt',txt:'z',x:b.cx+R*.6,y:b.cy-R*.8,vx:15,vy:-35,life:2,c:'#cdb4ff'})}
        if(T==='chomp'){const f=this.fly.find(q=>q.owner===b);if(f){f.x=lerp(f.x,b.cx+Math.cos(f.t*1.7+f.ph)*R*2.2,dt*1.2);f.y=b.cy-R*1.6+Math.sin(f.t*2.3)*R*.8;b.gx=f.x;b.gy=f.y+R*1.1;lt=[f.x,f.y];eye='pop';mouth='o';mo=.3;
            if(k>.75){b.gy=f.y;mouth='open';mo=1;if(Math.hypot(f.x-b.cx,f.y-b.cy)<R*1.1||k>.95){this.fly.splice(this.fly.indexOf(f),1);b.puff=.25;b.happyT=1.5;this.burst(f.x,f.y,8,['#fff3a0','#b8ff6b']);haptic(6)}}}else{eye='happy';mouth='smile'}}
        if(ac.t>=ac.dur){b.act=null;b.melt=0;b.gum=0;b.eyeBig=1}}}
    if(b.grab){eye='pop';mouth='open';mo=.6;blush=.9}
    if(b.sad>0&&!b.grab){eye='sad';mouth='frown'}if(b.happyT>0){eye='heart';mouth='open';mo=.4;blush=1}if(b.dizzy>0)eye='swirl';if(this.scare>0){eye='pop';mouth='wavy';b.shake=1}
    if(lt){const dx=lt[0]-b.cx,dy=lt[1]-b.cy,d=Math.hypot(dx,dy)+1;b.lookT=[dx/d*Math.min(1,d/R),dy/d*Math.min(1,d/R)]}else b.lookT=[Math.cos(t*.5+b.seed*9)*.6,Math.sin(t*.6+b.seed*7)*.4];
    b.look[0]=lerp(b.look[0],b.lookT[0],.2);b.look[1]=lerp(b.look[1],b.lookT[1],.2);
    b.eye=eye;b.mouth=mouth;b.mo=lerp(b.mo,mo,.3);b.blush=lerp(b.blush,blush,.1);b.sx=lerp(b.sx,sx,.15);b.sy=lerp(b.sy,sy,.15);b.bumpK=lerp(b.bumpK,bumpK,.15);b.puff*=Math.pow(.05,dt);b.lean*=Math.pow(.2,dt);b.shake=Math.max(0,(b.shake||0)-dt*3);b.gumSplat=Math.max(0,(b.gumSplat||0)-dt);b.eyeBig=lerp(b.eyeBig||1,1,.06);
    if(!b.act||b.act.type!=='spin')if(!b.spinV&&!(music&&Math.floor(this.beatN/8)%4===2)){const r=((b.rot%TAU)+TAU)%TAU;b.rot=r>Math.PI?lerp(r,TAU,.1):lerp(r,0,.1)}}
  groupThink(b,g,dt,E){const W=this.w,H=this.h,R=b.R,bs=this.blobs;
    if(g.type==='stack'){const order=[...bs].sort((a,c)=>c.R-a.R);let y=H*.86;for(const o of order){y-=o.R*.95;if(o===b)break;y-=o.R*.95}b.gx=W/2+Math.sin(this.t*2+order.indexOf(b))*R*.08;b.gy=y;E.sy=.9;E.sx=1.08;E.eye=order.indexOf(b)===order.length-1?'happy':'squeeze';E.mouth=order.indexOf(b)===order.length-1?'open':'wavy';E.mo=.4}
    if(g.type==='conga'){const i=bs.indexOf(b),tt=g.t*.35-i*.11;b.gx=W*(.5+.38*Math.sin(tt*2.2));b.gy=H*(.5+.25*Math.sin(tt*3.1));E.eye='happy';E.mouth='open';E.mo=.3+.3*Math.abs(Math.sin(g.t*6+i));E.sy=1-.1*Math.abs(Math.sin(g.t*6+i));if(Math.random()<dt*.8)this.fx.push({k:'note',x:b.cx,y:b.cy-R,vx:0,vy:-60,life:1.2,c:b.col})}
    if(g.type==='tag'){g.cool=Math.max(0,g.cool-dt*.5);const chaser=g.a,run=g.b;if(b===chaser){b.gx=run.cx;b.gy=run.cy;E.eye='n';E.mouth='grin';E.mo=.3}else{g.fleeT-=dt;if(g.fleeT<0){g.fleeT=1.4;const dx=b.cx-chaser.cx,dy=b.cy-chaser.cy,d=Math.hypot(dx,dy)+1;g.flee=[clamp(b.cx+dx/d*W*.5,R,W-R),clamp(b.cy+dy/d*H*.4+rnd(-H*.2,H*.2),H*.12+R,H*.88-R)]}b.gx=g.flee[0];b.gy=g.flee[1];E.eye='pop';E.mouth='open';E.mo=.6}
      if(b===chaser&&g.cool<=0&&Math.hypot(chaser.cx-run.cx,chaser.cy-run.cy)<(chaser.R+run.R)*1.02){g.a=run;g.b=chaser;g.cool=1.2;g.fleeT=0;this.fx.push({k:'txt',txt:'tag!',x:(chaser.cx+run.cx)/2,y:Math.min(chaser.cy,run.cy)-R,vx:0,vy:-40,life:1.2,c:'#ffe066'});this.burst((chaser.cx+run.cx)/2,(chaser.cy+run.cy)/2,10,['#ffe066','#ffffff']);haptic(8)}}
    if(g.type==='hug'){const o=b===g.a?g.b:g.a,mx=(g.a.hx+g.b.hx)/2,my=(g.a.hy+g.b.hy)/2,sd=b===g.a?-1:1;b.gx=mx+sd*(R*.75);b.gy=my;const close=Math.hypot(b.cx-o.cx,b.cy-o.cy)<(R+o.R)*1.1;E.eye=close?'happy':'n';E.mouth='smile';E.blush=close?1.3:.5;if(close){E.sx=.88;E.sy=1.08;if(Math.random()<dt*2)this.fx.push({k:'heart',x:mx+rnd(-R,R)*.4,y:Math.min(b.cy,o.cy)-R,vx:0,vy:-55,life:1.4,c:'#ff6fae'})}}
    return E}
  burst(x,y,n,cols){for(let i=0;i<n;i++){const a=rnd(TAU),v=rnd(80,240);this.fx.push({k:'star',x,y,vx:Math.cos(a)*v,vy:Math.sin(a)*v,g:300,life:rnd(.5,1),c:cols[i%cols.length],r:rnd(3,6)})}}
  /* ----- backgrounds ----- */
  bgStatic(){const w=this.w,h=this.h,key=this.bg+'|'+w+'|'+h;if(this.bgKey===key)return this.bgC;const c=this.bgC||(this.bgC=document.createElement('canvas'));c.width=Math.max(1,w);c.height=Math.max(1,h);const x=c.getContext('2d');x.fillStyle='#000';x.fillRect(0,0,w,h);
    const blob=(cx,cy,r,col,a)=>{const g=x.createRadialGradient(cx,cy,0,cx,cy,r);g.addColorStop(0,hexA(col,a));g.addColorStop(1,hexA(col,0));x.fillStyle=g;x.fillRect(cx-r,cy-r,2*r,2*r)};const B=this.bg;
    const stars=(n,y1)=>{for(let i=0;i<n;i++){x.globalAlpha=rnd(.2,.8);x.fillStyle=['#fff','#cdb4ff','#9ff3ff','#ffb3e6'][i%4];x.fillRect(rnd(w),rnd(h*y1),rnd(.6,1.6),rnd(.6,1.6))}x.globalAlpha=1};
    if(B==='void')stars(140,1);
    if(B==='disco'){stars(60,.5);const fy=h*.74;for(let r=0;r<7;r++){const v0=Math.pow(r/7,1.4),v1=Math.pow((r+1)/7,1.4),y0=fy+(h-fy)*v0,y1=fy+(h-fy)*v1;for(let cI=-6;cI<6;cI++){const X=(u,v)=>w/2+u*w*(.35+.9*v);const c0=['#ff3fd8','#2fe6ff','#ffe066','#7dff6b','#a35cff'][(r*3+cI+20)%5];x.fillStyle=hexA(c0,.1+v0*.1);x.beginPath();x.moveTo(X(cI/6,v0),y0);x.lineTo(X((cI+1)/6,v0),y0);x.lineTo(X((cI+1)/6,v1),y1);x.lineTo(X(cI/6,v1),y1);x.closePath();x.fill();x.strokeStyle='rgba(0,0,0,.8)';x.lineWidth=1.5;x.stroke()}}}
    if(B==='ocean'){const g=x.createLinearGradient(0,0,0,h);g.addColorStop(0,'#00213d');g.addColorStop(.5,'#000f22');g.addColorStop(1,'#000208');x.fillStyle=g;x.fillRect(0,0,w,h);x.globalCompositeOperation='lighter';
      for(let i=0;i<6;i++){const x0=rnd(w),wd=rnd(w*.05,w*.14),gr=x.createLinearGradient(0,0,0,h*.8);gr.addColorStop(0,'rgba(120,220,255,.16)');gr.addColorStop(1,'rgba(120,220,255,0)');x.fillStyle=gr;x.beginPath();x.moveTo(x0,0);x.lineTo(x0+wd,0);x.lineTo(x0+wd*2.6-w*.15,h*.8);x.lineTo(x0+wd*.6-w*.15,h*.8);x.fill()}
      x.globalCompositeOperation='source-over';for(let i=0;i<14;i++){const bx=rnd(w),hh=rnd(h*.08,h*.26);x.strokeStyle=['#0d4d3a','#0f5a46','#13304d'][i%3];x.lineWidth=rnd(5,10);x.lineCap='round';x.beginPath();x.moveTo(bx,h);for(let k=1;k<=6;k++)x.lineTo(bx+Math.sin(k*1.3+i)*12,h-hh*k/6);x.stroke()}
      x.fillStyle='#04121c';x.beginPath();x.moveTo(0,h);for(let i=0;i<=20;i++)x.lineTo(i/20*w,h-h*.04-Math.sin(i*1.7)*h*.015);x.lineTo(w,h);x.fill()}
    if(B==='candy'){stars(70,.6);const hills=[['#ff4fa8',.82,.06],['#2fd9a0',.87,.05],['#8a5cff',.92,.04]];for(const[col,yy,amp]of hills){const g=x.createLinearGradient(0,h*(yy-amp),0,h);g.addColorStop(0,hexA(col,.6));g.addColorStop(.5,hexA(col,.18));g.addColorStop(1,hexA(col,.02));x.fillStyle=g;x.beginPath();x.moveTo(0,h);for(let i=0;i<=30;i++){const xx=i/30*w;x.lineTo(xx,h*yy-Math.abs(Math.sin(i*.5+yy*9))*h*amp)}x.lineTo(w,h);x.fill()}
      for(let i=0;i<5;i++){const lx=w*(.08+i*.21)+rnd(-10,10),ly=h*.84,r=w*.045;x.strokeStyle='#fff';x.lineWidth=3;x.beginPath();x.moveTo(lx,ly);x.lineTo(lx,ly-h*.08);x.stroke();for(let k=0;k<3;k++){x.fillStyle=['#ff4f9a','#ffe066','#38e8ff','#ffffff'][(i+k)%4];x.beginPath();x.arc(lx,ly-h*.08,r*(1-k*.3),0,TAU);x.fill()}}}
    if(B==='space'){stars(220,1);blob(w*.15,h*.2,w*.5,'#7b2fff',.22);blob(w*.9,h*.55,w*.45,'#ff2fa0',.16);const px=w*.82,py=h*.14,pr=w*.12;const g=x.createRadialGradient(px-pr*.4,py-pr*.4,pr*.1,px,py,pr);g.addColorStop(0,'#ffd8a8');g.addColorStop(.6,'#ff7a45');g.addColorStop(1,'#5a1a2a');x.fillStyle=g;x.beginPath();x.arc(px,py,pr,0,TAU);x.fill();
      x.strokeStyle='rgba(255,210,170,.6)';x.lineWidth=pr*.08;x.beginPath();x.ellipse(px,py,pr*1.8,pr*.42,-.3,0,TAU);x.stroke();const mx=w*.12,my=h*.8,mr=w*.06;const g2=x.createRadialGradient(mx-mr*.4,my-mr*.4,mr*.1,mx,my,mr);g2.addColorStop(0,'#e8f4ff');g2.addColorStop(1,'#3a4a6a');x.fillStyle=g2;x.beginPath();x.arc(mx,my,mr,0,TAU);x.fill()}
    this.bgKey=key;return c}
  drawBg(ctx){const w=this.w,h=this.h,t=this.t,A=this.A||{},bass=A.active?A.bass:0,B=this.bg;ctx.drawImage(this.bgStatic(),0,0);
    if(B==='disco'){const ball=[w/2,h*.07];ctx.globalCompositeOperation='lighter';for(let i=0;i<4;i++){const a=Math.PI/2+Math.sin(t*.7+i*1.7)*.75,col=['#ff3fd8','#2fe6ff','#ffe066','#a35cff'][(i+this.beatN)%4],L=h*.95,sp=.13;const g=ctx.createLinearGradient(ball[0],ball[1],ball[0]+Math.cos(a)*L,ball[1]+Math.sin(a)*L);g.addColorStop(0,hexA(col,.3+bass*.3));g.addColorStop(1,hexA(col,0));ctx.fillStyle=g;ctx.beginPath();ctx.moveTo(ball[0],ball[1]);ctx.lineTo(ball[0]+Math.cos(a-sp)*L,ball[1]+Math.sin(a-sp)*L);ctx.lineTo(ball[0]+Math.cos(a+sp)*L,ball[1]+Math.sin(a+sp)*L);ctx.fill()}
      glow(ctx,'#ffffff',ball[0],ball[1],w*.09,.5+bass*.4);ctx.globalCompositeOperation='source-over';const r=w*.05;const g=ctx.createRadialGradient(ball[0]-r*.3,ball[1]-r*.3,1,ball[0],ball[1],r);g.addColorStop(0,'#ffffff');g.addColorStop(1,'#6a6a8a');ctx.fillStyle=g;ctx.beginPath();ctx.arc(ball[0],ball[1],r,0,TAU);ctx.fill();
      ctx.fillStyle='rgba(255,255,255,.8)';for(let i=0;i<10;i++){const a=t*1.5+i*.63;if(Math.cos(a)>0){ctx.fillRect(ball[0]+Math.sin(a)*r*.8-1.5,ball[1]-r*.6+(i%4)*r*.35,3,3)}}ctx.strokeStyle='rgba(255,255,255,.4)';ctx.beginPath();ctx.moveTo(ball[0],0);ctx.lineTo(ball[0],ball[1]-r);ctx.stroke()}
    if(B==='ocean'){if(!this.bubs)this.bubs=[...Array(30)].map(()=>({x:rnd(),y:rnd(),r:rnd(1.5,5),v:rnd(.03,.08)}));ctx.strokeStyle='rgba(170,230,255,.5)';ctx.lineWidth=1;for(const q of this.bubs){q.y-=q.v*dtFrame*(1+bass);if(q.y<-.02)q.y=1.02;const X=(q.x+Math.sin(t+q.r)*.01)*w;ctx.beginPath();ctx.arc(X,q.y*h,q.r,0,TAU);ctx.stroke()}}
    if(B==='candy'){if(!this.spr)this.spr=[...Array(40)].map(()=>({x:rnd(),y:rnd(),a:rnd(TAU),v:rnd(.02,.05),c:['#ff4f9a','#ffe066','#38e8ff','#7dff6b','#ffffff'][(Math.random()*5)|0]}));for(const q of this.spr){q.y+=q.v*dtFrame;if(q.y>.85)q.y=-.02;q.a+=dtFrame*2;ctx.save();ctx.translate(q.x*w,q.y*h);ctx.rotate(q.a);ctx.fillStyle=q.c;ctx.fillRect(-4,-1.2,8,2.4);ctx.restore()}}
    if(B==='lava'){const lc=this.lavaC||(this.lavaC=document.createElement('canvas')),lw=Math.max(8,w>>3),lh=Math.max(8,h>>3);if(lc.width!==lw||lc.height!==lh){lc.width=lw;lc.height=lh}const x=lc.getContext('2d');x.globalCompositeOperation='source-over';x.fillStyle='#050008';x.fillRect(0,0,lw,lh);x.globalCompositeOperation='lighter';
      for(let i=0;i<7;i++){const cx=lw*(.5+.42*Math.sin(t*.13*(1+i*.17)+i*2)),cy=lh*(.5+.45*Math.sin(t*.09*(1+i*.11)+i*1.3)),r=lw*(.35+.12*Math.sin(t*.3+i))*(1+bass*.2);const col=['#ff3d6e','#ff8a3d','#ff3fd8','#a35cff'][i%4];const g=x.createRadialGradient(cx,cy,0,cx,cy,r);g.addColorStop(0,hexA(col,.55));g.addColorStop(1,hexA(col,0));x.fillStyle=g;x.fillRect(cx-r,cy-r,2*r,2*r)}ctx.imageSmoothingEnabled=true;ctx.drawImage(lc,0,0,w,h)}
    if(B==='space'||B==='void'){if(Math.random()<dtFrame*.15&&!this.shoot)this.shoot={x:rnd(w),y:rnd(h*.4),t:0};if(this.shoot){const s=this.shoot;s.t+=dtFrame;const k=s.t/1;ctx.strokeStyle=`rgba(255,255,255,${.7*(1-k)})`;ctx.lineWidth=1.3;ctx.beginPath();ctx.moveTo(s.x+k*w*.3,s.y+k*h*.08);ctx.lineTo(s.x+k*w*.3-w*.08,s.y+k*h*.08-h*.02);ctx.stroke();if(k>1)this.shoot=null}}}
  /* ----- draw ----- */
  draw(ctx){this.drawBg(ctx);const A=this.A||{},bs=[...this.blobs].sort((a,b)=>a.cy-b.cy);
    for(const f of this.fly){ctx.globalCompositeOperation='lighter';glow(ctx,'#fff3a0',f.x,f.y,14,.9);ctx.globalCompositeOperation='source-over';ctx.fillStyle='#fffbe0';ctx.beginPath();ctx.arc(f.x,f.y,2.5,0,TAU);ctx.fill();ctx.fillStyle='rgba(255,255,255,.5)';const fl=Math.sin(this.t*40)*3;ctx.beginPath();ctx.ellipse(f.x-3,f.y-2,4,2+fl*.3,-.5,0,TAU);ctx.ellipse(f.x+3,f.y-2,4,2+fl*.3,.5,0,TAU);ctx.fill()}
    if(this.candy){const c=this.candy;ctx.globalCompositeOperation='lighter';glow(ctx,c.c,c.x,c.y,22,.8);ctx.globalCompositeOperation='source-over';ctx.save();ctx.translate(c.x,c.y);ctx.rotate(c.t*2);ctx.fillStyle=c.c;ctx.beginPath();ctx.arc(0,0,8,0,TAU);ctx.fill();ctx.beginPath();ctx.moveTo(-8,0);ctx.lineTo(-15,-6);ctx.lineTo(-15,6);ctx.closePath();ctx.moveTo(8,0);ctx.lineTo(15,-6);ctx.lineTo(15,6);ctx.closePath();ctx.fill();ctx.strokeStyle='rgba(255,255,255,.8)';ctx.lineWidth=2;ctx.beginPath();ctx.arc(0,0,5,0,Math.PI);ctx.stroke();ctx.restore()}
    for(const b of bs)this.drawBlob(ctx,b,A);
    for(const q of this.fx){const a=Math.min(1,q.life*2);ctx.globalAlpha=a;if(q.k==='note')drawShape(ctx,'notes',q.x,q.y,9,1,q.c,a);else if(q.k==='heart'){drawShape(ctx,'hearts',q.x,q.y,7,0,q.c,a)}else if(q.k==='star'){drawShape(ctx,'stars',q.x,q.y,q.r,q.life*5,q.c,a)}else if(q.k==='txt'){ctx.fillStyle=q.c;ctx.font='800 17px "Plus Jakarta Sans",system-ui,sans-serif';ctx.textAlign='center';ctx.textBaseline='middle';ctx.fillText(q.txt,q.x,q.y)}}ctx.globalAlpha=1;
    const L=this.light;if(Math.abs(L-1)>.02){ctx.save();ctx.setTransform(1,0,0,1,0,0);if(L<1){ctx.globalAlpha=1-L;ctx.fillStyle='#000';ctx.fillRect(0,0,ctx.canvas.width,ctx.canvas.height)}else{ctx.globalCompositeOperation='lighter';ctx.globalAlpha=Math.min(1,L-1);ctx.drawImage(ctx.canvas,0,0)}ctx.restore()}}
  drawBlob(ctx,b,A){const pts=b.pts,N=b.N,R=b.R,cx=b.cx+(b.shake?Math.sin(this.t*60)*R*.04*b.shake:0),cy=b.cy,t=this.t,music=A.active;
    ctx.globalCompositeOperation='lighter';glow(ctx,b.col,cx,cy,R*2,.28+(music?A.bass*.25:0));ctx.globalCompositeOperation='source-over';
    const up=-Math.PI/2+b.rot,ux=Math.cos(up),uy=Math.sin(up);let top=[cx,cy],bd=-1e9;for(const q of pts){const d=(q.x-cx)*ux+(q.y-cy)*uy;if(d>bd){bd=d;top=[q.x,q.y]}}
    // accessories behind the body, glued to the top point
    ctx.save();ctx.translate(top[0],top[1]);ctx.rotate(b.rot+(b.lean||0)*.5);const k=b.kind,tr=music?A.treble:0;
    if(k==='horns')for(const sd of[-1,1]){ctx.fillStyle=b.lite;ctx.beginPath();ctx.moveTo(sd*R*.3,R*.25);ctx.quadraticCurveTo(sd*R*.68,-R*.05,sd*R*.6,-R*.45);ctx.lineTo(sd*R*.5,R*.2);ctx.fill()}
    if(k==='bunny')for(const sd of[-1,1]){const fl=Math.sin(t*3+sd+b.seed*5)*.15;ctx.save();ctx.translate(sd*R*.32,R*.2);ctx.rotate(sd*.25+fl);ctx.fillStyle=b.col;ctx.beginPath();ctx.ellipse(0,-R*.5,R*.16,R*.5,0,0,TAU);ctx.fill();ctx.fillStyle=b.lite;ctx.beginPath();ctx.ellipse(0,-R*.5,R*.08,R*.36,0,0,TAU);ctx.fill();ctx.restore()}
    if(k==='cat')for(const sd of[-1,1]){ctx.fillStyle=b.col;ctx.beginPath();ctx.moveTo(sd*R*.15,R*.25);ctx.lineTo(sd*R*.5,-R*.32);ctx.lineTo(sd*R*.68,R*.3);ctx.fill();ctx.fillStyle=b.lite;ctx.beginPath();ctx.moveTo(sd*R*.3,R*.22);ctx.lineTo(sd*R*.5,-R*.15);ctx.lineTo(sd*R*.58,R*.25);ctx.fill()}
    if(k==='spikes')for(let i=-2;i<=2;i++){ctx.fillStyle=i%2?b.lite:b.col;ctx.beginPath();ctx.moveTo(i*R*.22-R*.12,R*.12);ctx.lineTo(i*R*.22,-R*(.28-.05*Math.abs(i))-tr*R*.12);ctx.lineTo(i*R*.22+R*.12,R*.12);ctx.fill()}
    if(k==='antenna'){const sx=Math.sin(t*3+b.seed*5)*R*.15+(music?A.mid*R*.2:0),ex=R*.2+sx,ey=-R*.55;ctx.lineWidth=Math.max(1.5,R*.05);ctx.strokeStyle=b.col;ctx.beginPath();ctx.moveTo(R*.05,R*.1);ctx.quadraticCurveTo(R*.1,-R*.25,ex,ey);ctx.stroke();ctx.globalCompositeOperation='lighter';glow(ctx,b.col,ex,ey,R*.3,.9+tr*.4);ctx.globalCompositeOperation='source-over';ctx.fillStyle=b.lite;ctx.beginPath();ctx.arc(ex,ey,R*.09,0,TAU);ctx.fill()}
    if(k==='crown'){ctx.fillStyle='#ffd23d';ctx.beginPath();ctx.moveTo(-R*.32,R*.12);ctx.lineTo(-R*.36,-R*.25);ctx.lineTo(-R*.16,-R*.06);ctx.lineTo(0,-R*.34);ctx.lineTo(R*.16,-R*.06);ctx.lineTo(R*.36,-R*.25);ctx.lineTo(R*.32,R*.12);ctx.fill();for(const[xx,c]of[[-R*.2,'#ff3d6e'],[0,'#38e8ff'],[R*.2,'#7dff6b']]){ctx.fillStyle=c;ctx.beginPath();ctx.arc(xx,R*.02,R*.045,0,TAU);ctx.fill()}}
    if(k==='sprout'){const sw=Math.sin(t*2+b.seed*4)*.2;ctx.rotate(sw);ctx.strokeStyle='#3e7a0a';ctx.lineWidth=R*.05;ctx.beginPath();ctx.moveTo(0,R*.15);ctx.lineTo(0,-R*.25);ctx.stroke();ctx.fillStyle='#7dff6b';for(const sd of[-1,1]){ctx.beginPath();ctx.ellipse(sd*R*.16,-R*.3,R*.17,R*.08,sd*-.5,0,TAU);ctx.fill()}ctx.fillStyle='#ff6fd8';ctx.beginPath();ctx.arc(0,-R*.3,R*.06,0,TAU);ctx.fill()}
    if(b.bow){ctx.fillStyle='#ff3d9a';ctx.save();ctx.translate(R*.38,R*.2);for(const sd of[-1,1]){ctx.beginPath();ctx.moveTo(0,0);ctx.lineTo(sd*R*.2,-R*.12);ctx.lineTo(sd*R*.2,R*.12);ctx.fill()}ctx.beginPath();ctx.arc(0,0,R*.05,0,TAU);ctx.fill();ctx.restore()}
    ctx.restore();
    // body
    ctx.beginPath();const l=pts[N-1],f=pts[0];ctx.moveTo((l.x+f.x)/2,(l.y+f.y)/2);for(let i=0;i<N;i++){const a=pts[i],c=pts[(i+1)%N];ctx.quadraticCurveTo(a.x,a.y,(a.x+c.x)/2,(a.y+c.y)/2)}ctx.closePath();
    const g=ctx.createRadialGradient(cx-R*.35,cy-R*.45,R*.05,cx,cy,R*1.25);g.addColorStop(0,b.lite);g.addColorStop(.45,b.col);g.addColorStop(1,b.dark);ctx.fillStyle=g;ctx.fill();ctx.save();ctx.globalCompositeOperation='lighter';ctx.strokeStyle=hexA(b.col,.75);ctx.lineWidth=2;ctx.stroke();ctx.restore();
    ctx.save();ctx.clip();ctx.fillStyle='rgba(255,255,255,.28)';ctx.beginPath();ctx.ellipse(cx-R*.38,cy-R*.5,R*.28,R*.14,-.6,0,TAU);ctx.fill();ctx.fillStyle='rgba(255,255,255,.5)';ctx.beginPath();ctx.arc(cx-R*.12,cy-R*.66,R*.05,0,TAU);ctx.fill();ctx.restore();
    // face
    ctx.save();ctx.translate(cx+b.look[0]*R*.14,cy+b.look[1]*R*.1);ctx.rotate(b.rot+(b.lean||0));ctx.scale(b.sx,b.sy*(1-b.melt*.35));if(b.melt)ctx.translate(0,R*.25*b.melt);this.face(ctx,b,R);ctx.restore()}
  face(ctx,b,R){const t=this.t,ink='#1a0610',eye=b.eye,E=b.eyeBig||1,lk=b.look;ctx.lineCap='round';ctx.lineJoin='round';
    const eyes=b.eyes===1?[[0,-.14,.36]]:b.eyes===3?[[-.36,-.08,.17],[0,-.3,.17],[.36,-.08,.17]]:[[-.32,-.15,.22],[.32,-.15,.22]],bl=b.blink>0&&(eye==='n'||eye==='pop')?.12:1;
    for(const[i,[ex0,ey0,er0]]of eyes.entries()){const ex=ex0*R,ey=ey0*R,er=er0*R*(eye==='pop'?1.25:1)*E;
      if(eye==='happy'){ctx.strokeStyle=ink;ctx.lineWidth=R*.07;ctx.beginPath();ctx.arc(ex,ey+er*.4,er*.75,Math.PI*1.15,Math.PI*1.85);ctx.stroke();continue}
      if(eye==='closed'){ctx.strokeStyle=ink;ctx.lineWidth=R*.06;ctx.beginPath();ctx.arc(ex,ey-er*.25,er*.7,Math.PI*.15,Math.PI*.85);ctx.stroke();continue}
      if(eye==='squeeze'){const s=ex<0||eyes.length===1?1:-1;ctx.strokeStyle=ink;ctx.lineWidth=R*.07;ctx.beginPath();ctx.moveTo(ex-er*.6*s,ey-er*.55);ctx.lineTo(ex+er*.45*s,ey);ctx.lineTo(ex-er*.6*s,ey+er*.55);ctx.stroke();continue}
      if(eye==='x'){ctx.strokeStyle=ink;ctx.lineWidth=R*.06;ctx.beginPath();ctx.moveTo(ex-er*.5,ey-er*.5);ctx.lineTo(ex+er*.5,ey+er*.5);ctx.moveTo(ex+er*.5,ey-er*.5);ctx.lineTo(ex-er*.5,ey+er*.5);ctx.stroke();continue}
      if(eye==='heart'){const s=er*1.1*(1+.1*Math.sin(t*10));ctx.save();ctx.translate(ex,ey);shapePath(ctx,'hearts',s);ctx.fillStyle='#ff2f7a';ctx.fill();ctx.restore();ctx.fillStyle='#fff';ctx.beginPath();ctx.arc(ex-s*.35,ey-s*.3,s*.15,0,TAU);ctx.fill();continue}
      if(eye==='wink'&&i===eyes.length-1){ctx.strokeStyle=ink;ctx.lineWidth=R*.07;ctx.beginPath();ctx.arc(ex,ey+er*.4,er*.75,Math.PI*1.15,Math.PI*1.85);ctx.stroke();continue}
      ctx.save();ctx.translate(ex,ey);ctx.scale(1,bl);ctx.beginPath();ctx.arc(0,0,er,0,TAU);ctx.fillStyle='#ffffff';ctx.fill();ctx.lineWidth=Math.max(1,R*.025);ctx.strokeStyle=b.dark;ctx.stroke();
      if(eye==='swirl'){ctx.strokeStyle=ink;ctx.lineWidth=R*.035;ctx.beginPath();for(let a=0;a<TAU*2.4;a+=.25){const r=a*er*.12,aa=a+t*10;ctx.lineTo(Math.cos(aa)*r,Math.sin(aa)*r)}ctx.stroke();ctx.restore();continue}
      const po=er*.42,pr=er*(eye==='pop'?.32:.5),px=lk[0]*po,py=lk[1]*po;ctx.beginPath();ctx.arc(px,py,pr,0,TAU);ctx.fillStyle='#0a0a12';ctx.fill();ctx.beginPath();ctx.arc(px-pr*.35,py-pr*.35,pr*.3,0,TAU);ctx.fillStyle='#fff';ctx.fill();ctx.beginPath();ctx.arc(px+pr*.3,py+pr*.35,pr*.12,0,TAU);ctx.fill();
      if(eye==='sad'){ctx.fillStyle=b.col;ctx.beginPath();ctx.moveTo(-er*1.1,-er*1.1);ctx.lineTo(er*1.1,-er*1.1);ctx.lineTo(er*1.1*(ex<0?-1:1)*-1+0,-er*.15);ctx.closePath();ctx.fill()}
      ctx.restore()}
    // blush, freckles
    ctx.fillStyle=`rgba(255,80,130,${.35*b.blush+.1})`;for(const sd of[-1,1]){ctx.beginPath();ctx.ellipse(sd*R*.55,R*.18,R*.11,R*.065,0,0,TAU);ctx.fill()}
    if(b.freck){ctx.fillStyle=hexA(b.dark,.55);for(const sd of[-1,1])for(const[a,c]of[[.48,.1],[.58,.14],[.52,.22]]){ctx.beginPath();ctx.arc(sd*R*a,R*c,R*.018,0,TAU);ctx.fill()}}
    // mouth
    const my=R*.36,mw=R*(b.eyes===1?.3:.24),mo=b.mo,m=b.mouth;ctx.strokeStyle=ink;ctx.lineWidth=Math.max(1.5,R*.05);
    if(b.gum>0){const gr=R*(.08+.55*b.gum);ctx.fillStyle='rgba(255,140,205,.85)';ctx.beginPath();ctx.arc(0,my+gr*.5,gr,0,TAU);ctx.fill();ctx.fillStyle='rgba(255,255,255,.6)';ctx.beginPath();ctx.ellipse(-gr*.35,my+gr*.15,gr*.2,gr*.12,-.6,0,TAU);ctx.fill();return}
    if(b.gumSplat>0){ctx.fillStyle=`rgba(255,140,205,${b.gumSplat*.7})`;for(const[a,c,r]of[[-.4,-.1,.18],[.3,.05,.2],[0,.3,.22],[.45,-.25,.12]]){ctx.beginPath();ctx.arc(a*R,c*R,r*R,0,TAU);ctx.fill()}}
    if(m==='open'||(m==='smile'&&mo>.14)||m==='o'){const ow=m==='o'?mw*.5:mw,oh=R*.3*mo+R*.04;ctx.beginPath();ctx.ellipse(0,my,ow,oh,0,0,TAU);ctx.fillStyle='#1a0610';ctx.fill();ctx.save();ctx.clip();ctx.beginPath();ctx.ellipse(0,my+oh*.9,ow*.7,R*.14,0,0,TAU);ctx.fillStyle='#FF6B8B';ctx.fill();ctx.restore();if(b.fangs){ctx.fillStyle='#fff';for(const sd of[-1,1]){ctx.beginPath();ctx.moveTo(sd*ow*.55,my-oh*.85);ctx.lineTo(sd*ow*.35,my-oh*.85);ctx.lineTo(sd*ow*.45,my-oh*.85+R*.08);ctx.fill()}}}
    else if(m==='grin'){ctx.beginPath();ctx.moveTo(-mw,my-R*.04);ctx.quadraticCurveTo(0,my+R*.3,mw,my-R*.04);ctx.closePath();ctx.fillStyle='#1a0610';ctx.fill();ctx.fillStyle='#fff';ctx.fillRect(-mw*.7,my-R*.03,mw*1.4,R*.06)}
    else if(m==='tongue'){ctx.beginPath();ctx.arc(0,my-R*.08,mw*.8,.35,Math.PI-.35);ctx.stroke();ctx.fillStyle='#FF6B8B';ctx.beginPath();ctx.ellipse(mw*.2,my+R*.08,R*.09,R*.12,0,0,TAU);ctx.fill()}
    else if(m==='frown'){ctx.beginPath();ctx.arc(0,my+R*.12,mw*.6,Math.PI*1.2,Math.PI*1.8);ctx.stroke()}
    else if(m==='wavy'){ctx.beginPath();for(let i=0;i<=8;i++)ctx.lineTo(-mw+i*mw/4,my+Math.sin(i*1.6+t*14)*R*.03);ctx.stroke()}
    else{ctx.beginPath();ctx.arc(0,my-R*.08,mw*.8,.35,Math.PI-.35);ctx.stroke();if(b.fangs){ctx.fillStyle='#fff';ctx.beginPath();ctx.moveTo(mw*.3,my+R*.02);ctx.lineTo(mw*.5,my+R*.01);ctx.lineTo(mw*.4,my+R*.1);ctx.fill()}}}
}
