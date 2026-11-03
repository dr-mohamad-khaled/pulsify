/* ================= ARNOOB — a realistic bunny, painted in code =================
   Every fur layer (body, head, ears, paws, feet, tail) is painted once from thousands of tiny
   lit hair strokes into an off-screen canvas, then animated with cheap transforms, so it looks
   photographic but costs almost nothing per frame. Live parts: glossy eyes, twitching nose,
   whiskers, chewing mouth. Real bunny behaviour: grooming, ear washing, sniffing your finger,
   binkies, periscope, thumping, digging, scratching, loafing, napping, munching carrots. */
const ARNOOB_COATS=[
 {name:'Wild',base:'#86664a',light:'#c8a782',dark:'#3f2e20',belly:'#efe5d8',inner:'#e9a7a0',tip:'#2a1d14',eye:'#2a160c',glow:'#ffb36b',top:'#c8a782',bot:'#86664a',rim:'#ffcf9a',rim2:'#9d7bff'},
 {name:'Snow',base:'#ece8e2',light:'#ffffff',dark:'#bdb4aa',belly:'#ffffff',inner:'#f2b5b5',tip:'#d8d0c6',eye:'#1a0f0c',glow:'#cfe2ff',top:'#ffffff',bot:'#d8d0c6',rim:'#e6f0ff',rim2:'#ffc2e6'},
 {name:'Grey',base:'#8a8f99',light:'#c9cdd5',dark:'#4c505a',belly:'#dfe2e6',inner:'#e0a8ab',tip:'#3a3d45',eye:'#120d0c',glow:'#9fc4ff',top:'#c9cdd5',bot:'#8a8f99',rim:'#cfe2ff',rim2:'#c79bff'},
 {name:'Ginger',base:'#c57a3c',light:'#f2b77c',dark:'#6e3a14',belly:'#f7e6d2',inner:'#eba29a',tip:'#8a4a1c',eye:'#2a140a',glow:'#ff9a4d',top:'#f2b77c',bot:'#c57a3c',rim:'#ffd0a0',rim2:'#ff6fae'},
 {name:'Black',base:'#2b2725',light:'#5e5650',dark:'#100e0d',belly:'#3d3734',inner:'#b98a8a',tip:'#0c0a09',eye:'#0a0605',glow:'#b48cff',top:'#5e5650',bot:'#2b2725',rim:'#d0b8ff',rim2:'#38e8ff'},
 {name:'Cream',base:'#e2c9a4',light:'#fbecd6',dark:'#a8875c',belly:'#fff7ec',inner:'#f0b0a8',tip:'#c4a275',eye:'#2a160c',glow:'#ffd59a',top:'#fbecd6',bot:'#e2c9a4',rim:'#fff0d6',rim2:'#ff9ad6'},
 {name:'Dutch',base:'#2d2826',light:'#5a524c',dark:'#120f0e',belly:'#ffffff',inner:'#eaa9a5',tip:'#0c0a09',eye:'#160c08',glow:'#cfe2ff',top:'#ffffff',bot:'#2d2826',rim:'#e6f0ff',rim2:'#ffb36b',dutch:1}];
const ARNOOB_SND={thump(){SFX.tone('sine',90,45,.18,.4);SFX.hiss(300,.08,.2)},munch(){SFX.hiss(2600,.04,.1);SFX.hiss(1800,.03,.08,.07)},sniff(){SFX.hiss(5000,.05,.05);SFX.hiss(5000,.05,.05,.09)},purr(){for(let i=0;i<5;i++)SFX.hiss(3200,.03,.05,i*.06)},
  hop(){SFX.hiss(400,.06,.1)},dig(){SFX.hiss(900,.08,.12)},squeak(){SFX.tone('sine',1700,2300,.08,.05)}};
const ARNOOB_DUR={groom:4.2,earwash:4.4,hop:1.5,binky:1.3,periscope:3.6,loaf:7,sniff:3,yawn:2.6,thump:1.6,scratch:2.8,dig:3.4,nap:8,munch:9,flinch:.9,pet:0,look:2.4};
class ArnoobScene extends Scene{
  constructor(pv){super(pv);this.isBuddy=true;this.id='arnoob';this.backHz=30;this.PALS=ARNOOB_COATS;this.defLook={sky:'stars',ground:'grass'};this.loadLook();this.snd=ARNOOB_SND;this.chargeText='Munching a power carrot';
    this.x=0;this.home=0;this.bx=0;this.vx=0;this.jump=0;this.vy=0;this.mood='day';this.act=null;this.actT=rnd(2,4);this.force=null;this.simCharge=false;this.wake=0;this.fx=[];this.carrot=null;this.pokes=[];this.hold=null;
    this.P={sy:1,hx:0,hy:0,tilt:0,turn:0,ear:[.12,.12],earF:[0,0],eye:1,look:[0,0],paw:0,pawY:0,nose:0,chew:0,body:0,peri:0,loaf:0,foot:0,dig:0};this.T=null;this.blinkT=2;this.blink=0;this.noseT=0;this.nosePh=0;this.L=null;this.layKey=null;this.u={shear:[0,0,0]};this.dz=0}
  attach(){if(typeof GLS!=='undefined'&&GLS)GLS.cv.style.display='none'}
  dispose(){}
  resize(w,h){const first=this.w===1;super.resize(w,h);this.U=Math.min(w*.26,h*.15);this.floor=h*.74;if(first){this.home=w/2;this.x=w/2}this.home=w/2;this.layKey=null;this.bgKey=null;this.gdKey=null}
  sfx(n){if(!this.preview&&!WP&&this.snd[n])this.snd[n]()}
  controls(){return[...this.lookControls().map(c=>c.label==='Color'?Object.assign(c,{label:'Coat',set:i=>{this.palI=i;this.pal=this.PALS[i];this.layKey=null;this.saveLook('pal',i)}}):c),
    {type:'buttons',label:'Tricks',items:[['✦ Surprise',null],['Groom','groom'],['Binky','binky'],['Periscope','periscope'],['Ear wash','earwash'],['Dig','dig'],['Thump','thump'],['Carrot','carrot']].map(([n,a])=>({name:n,fn:()=>{this.wake=40;if(a==='carrot')this.dropCarrot(this.home+rnd(-this.U,this.U),this.floor);else this.start(a||['groom','binky','periscope','earwash','dig','scratch','hop','sniff','yawn'][(Math.random()*9)|0])}}))}]}
  /* ----- fur painting ----- */
  layers(){const key=this.pal.name+'|'+Math.round(this.U);if(this.layKey===key)return this.L;this.layKey=key;const U=this.U,Q=Math.min(2,(window.devicePixelRatio||1)),c=this.pal;
    const mk=(x0,y0,x1,y1,fn)=>{const cv=document.createElement('canvas');cv.width=Math.ceil((x1-x0)*U*Q);cv.height=Math.ceil((y1-y0)*U*Q);const g=cv.getContext('2d');g.setTransform(U*Q,0,0,U*Q,-x0*U*Q,-y0*U*Q);fn(g);return{c:cv,x0,y0,w:x1-x0,h:y1-y0}};
    const hx=h=>{const n=parseInt(h.slice(1),16);return[n>>16,n>>8&255,n&255]},mix=(a,b,t)=>a.map((v,i)=>v+(b[i]-v)*t),rgb=(a,al)=>`rgba(${a[0]|0},${a[1]|0},${a[2]|0},${al})`;
    const B=hx(c.base),Li=hx(c.light),D=hx(c.dark),Be=hx(c.belly),Tp=hx(c.tip);
    const fur=(g,inside,dir,col,n,len,w)=>{for(let i=0;i<n;i++){const x=rnd(inside.b[0],inside.b[2]),y=rnd(inside.b[1],inside.b[3]),v=inside.f(x,y);if(v>1.06)continue;if(v>1&&Math.random()<.6)continue;const d=dir(x,y),L=len*rnd(.6,1.3),cc=col(x,y);const j=rnd(-.12,.12);
        g.strokeStyle=rgb(mix(cc,j>0?[255,255,255]:[0,0,0],Math.abs(j)),rnd(.45,.85));g.lineWidth=w*rnd(.6,1.2)/U;g.beginPath();g.moveTo(x,y);g.quadraticCurveTo(x+d[0]*L*.5+d[1]*L*.15,y+d[1]*L*.5-d[0]*L*.15,x+d[0]*L,y+d[1]*L);g.stroke()}};
    const norm=(x,y)=>{const l=Math.hypot(x,y)||1;return[x/l,y/l]},ell=(x,y,cx,cy,rx,ry)=>((x-cx)/rx)**2+((y-cy)/ry)**2;
    const shade=(x,y,base)=>{const s=clamp(.55-x*.22+(-y-.55)*.45,0,1);return s>.5?mix(base,Li,(s-.5)*1.4):mix(D,base,s*2)};
    const blobFill=(g,f,b,colr)=>{const res=.02;g.fillStyle=colr;for(let y=b[1];y<b[3];y+=res)for(let x=b[0];x<b[2];x+=res)if(f(x,y)<.97)g.fillRect(x,y,res*1.6,res*1.6)};
    // body (loaf + haunches)
    const bodyF=(x,y)=>Math.min(ell(x,y,0,-.62,.74,.62),ell(x,y,-.44,-.32,.34,.32),ell(x,y,.44,-.32,.34,.32)),bodyB=[-1.1,-1.3,1.1,0];
    const body=mk(-1.15,-1.35,1.15,.06,g=>{blobFill(g,bodyF,bodyB,rgb(B,1));const dutch=c.dutch;
      fur(g,{f:bodyF,b:bodyB},(x,y)=>norm(x*.45,1+(y<-.9?-.4:0)),(x,y)=>{let base=B;const belly=Math.abs(x)<.38&&y>-.75;if(dutch)base=(Math.abs(x)<.45&&y<-.55)||belly?Be:B;else if(belly)base=mix(B,Be,clamp(1-Math.abs(x)/.38,0,1)*clamp((y+.75)/.3,0,1));return shade(x,y,base)},8500,.065,1.3);
      const ao=g.createLinearGradient(0,-.25,0,0);ao.addColorStop(0,'rgba(0,0,0,0)');ao.addColorStop(1,'rgba(0,0,0,.45)');g.globalCompositeOperation='source-atop';g.fillStyle=ao;g.fillRect(-1.2,-.3,2.4,.4);g.globalCompositeOperation='source-over'});
    // head
    const headF=(x,y)=>Math.min(ell(x,y,0,-1.06,.5,.43),ell(x,y,-.22,-.9,.3,.26),ell(x,y,.22,-.9,.3,.26)),headB=[-.6,-1.55,.6,-.62];
    const head=mk(-.65,-1.55,.65,-.58,g=>{blobFill(g,headF,headB,rgb(B,1));const dutch=c.dutch;
      fur(g,{f:headF,b:headB},(x,y)=>norm(x,(y+.9)+.05),(x,y)=>{let base=B;const muz=ell(x,y,0,-.88,.28,.2);if(dutch)base=Math.abs(x)<.11+(-y-.9)*-.25||muz<1?Be:B;else if(muz<1)base=mix(B,Be,.55*(1-muz));const s=clamp(.6-x*.2+(-y-1)*.6,0,1);return s>.5?mix(base,Li,(s-.5)*1.2):mix(D,base,.4+s*1.2)},6500,.045,1.05)});
    // ear (left; right is mirrored) — furry back, translucent pink inside with veins
    const earW=y=>.135*Math.pow(Math.max(0,Math.sin(Math.PI*clamp(-y/1.02,0,1))),.62),earF=(x,y)=>{const w=earW(y);return w<=0?9:(x/w)**2},earB=[-.16,-1.04,.16,.04];
    const ear=mk(-.2,-1.08,.2,.08,g=>{blobFill(g,earF,earB,rgb(B,1));fur(g,{f:earF,b:earB},(x,y)=>norm(x*.25,-1),(x,y)=>shade(x-.3,y-.6,B),2400,.045,.9);
      g.save();g.beginPath();for(let y=-.06;y>-.95;y-=.02)g.lineTo(earW(y)*.6,y);for(let y=-.95;y<-.06;y+=.02)g.lineTo(-earW(y)*.6,y);g.closePath();g.clip();const ig=g.createLinearGradient(0,0,0,-.95);const IN=hx(c.inner);ig.addColorStop(0,rgb(mix(IN,[255,255,255],.25),1));ig.addColorStop(.5,rgb(IN,1));ig.addColorStop(1,rgb(mix(IN,D,.3),1));g.fillStyle=ig;g.fillRect(-.2,-1,.4,1);
        g.strokeStyle=rgb(mix(IN,[170,40,60],.5),.55);g.lineWidth=1.2/U;const vein=(x,y,a,l,d)=>{if(d>3||l<.04)return;const ex=x+Math.sin(a)*l,ey=y-Math.cos(a)*l;g.beginPath();g.moveTo(x,y);g.lineTo(ex,ey);g.stroke();vein(ex,ey,a+rnd(.2,.5),l*.6,d+1);vein(ex,ey,a-rnd(.2,.5),l*.6,d+1)};vein(0,-.1,0,.3,0);
        for(let i=0;i<260;i++){const y=rnd(-.9,-.08),x=rnd(-1,1)*earW(y)*.55;g.strokeStyle=`rgba(255,240,235,${rnd(.15,.4)})`;g.lineWidth=.7/U;g.beginPath();g.moveTo(x,y);g.lineTo(x+rnd(-.02,.02),y-rnd(.02,.05));g.stroke()}g.restore();
      g.strokeStyle=rgb(mix(B,Li,.5),.6);g.lineWidth=1.2/U;g.beginPath();for(let y=-.02;y>-1.01;y-=.02)g.lineTo(earW(y),y);g.stroke()});
    // front paw, hind foot, tail
    const pawF=(x,y)=>ell(x,y,0,-.12,.11,.15),paw=mk(-.16,-.3,.16,.04,g=>{blobFill(g,pawF,[-.14,-.29,.14,.03],rgb(c.dutch?Be:B,1));fur(g,{f:pawF,b:[-.14,-.29,.14,.03]},()=>norm(0,1),(x,y)=>shade(x,y-.5,c.dutch?Be:mix(B,Be,.2)),420,.035,1.1)});
    const footF=(x,y)=>ell(x,y,0,-.09,.34,.1),foot=mk(-.38,-.2,.38,.03,g=>{blobFill(g,footF,[-.36,-.19,.36,.02],rgb(c.dutch?Be:B,1));fur(g,{f:footF,b:[-.36,-.19,.36,.02]},(x)=>norm(x>0?1:-1,.2),(x,y)=>shade(x,y-.4,c.dutch?Be:B),700,.04,1.1)});
    const tailF=(x,y)=>ell(x,y,0,0,.17,.15),tail=mk(-.22,-.2,.22,.2,g=>{blobFill(g,tailF,[-.2,-.18,.2,.18],rgb(Be,1));fur(g,{f:tailF,b:[-.2,-.18,.2,.18]},(x,y)=>norm(x,y),(x,y)=>mix(Be,[255,255,255],.3),700,.05,1.2)});
    return this.L={body,head,ear,paw,foot,tail}}
  dl(ctx,L,x,y,rot,sx,sy){ctx.save();ctx.translate(x,y);if(rot)ctx.rotate(rot);ctx.scale(sx||1,sy||1);ctx.drawImage(L.c,L.x0*this.U,L.y0*this.U,L.w*this.U,L.h*this.U);ctx.restore()}
  /* ----- behaviour ----- */
  start(type,o){if(this.act&&this.act.type===type)return;this.act=Object.assign({type,t:0,dur:ARNOOB_DUR[type]||3,side:Math.random()<.5?-1:1},o||{});if(type==='hop'){this.vy=-this.h*.55;this.vx=this.act.side*this.U*1.6;this.jump=.01;this.sfx('hop')}if(type==='binky'){this.vy=-this.h*.95;this.jump=.01;this.vx=this.act.side*this.U*.6}if(type==='thump')this.sfx('thump')}
  dropCarrot(x,y){this.carrot={x:clamp(x,this.U,this.w-this.U),y:this.floor,s:0,left:1};this.act=null;this.wake=40}
  phaseMood(A){const ph=this.force||dayPhase(),ch=HSTATE.charging||this.simCharge;this.charging=ch;if(A.active&&A.level>.08)return'dance';if(ph==='night'&&this.wake<=0)return'sleep';if(ch)return'charge';if(!ch&&HSTATE.battery<.2)return'low';if(ph==='night'||ph==='morning')return'wakeup';return ph==='late'?'late':ph==='evening'?'evening':'day'}
  update(dt,A){this.t+=dt;this.A=A;const U=this.U,P=this.P;this.wake=Math.max(0,this.wake-dt);this.mood=this.phaseMood(A);const m=this.mood;
    let ac=this.act;if(ac){ac.t+=dt;if(ac.t>=ac.dur){this.act=null;ac=null}}
    // targets
    const T={sy:1+Math.sin(this.t*(m==='sleep'?1.4:2.4))*.012,hx:0,hy:0,tilt:Math.sin(this.t*.4)*.04,turn:0,ear:[.12,.12],earF:[0,0],eye:1,look:[Math.sin(this.t*.3)*.3,0],paw:0,pawY:0,nose:.4,chew:0,peri:0,loaf:0,foot:0,dig:0};
    if(!ac&&!this.hold&&m!=='sleep'&&m!=='dance'&&!this.carrot){this.actT-=dt;if(this.actT<0){this.actT=rnd(3,7);const pool=m==='charge'?['munch','loaf','groom']:m==='low'?['loaf','yawn']:m==='wakeup'?['yawn','groom','earwash','scratch']:m==='evening'||m==='late'?['loaf','groom','earwash','yawn','nap','sniff','periscope']:['groom','earwash','hop','hop','binky','periscope','sniff','scratch','dig','yawn','loaf','look'];this.start(pool[(Math.random()*pool.length)|0])}}
    // gaze: wherever you touch, Arnoob turns its head, points its ears and sniffs toward it
    const ptr=this.ptr;let tg=null;if(ptr&&!this.hold)tg=[ptr.x,ptr.y];else if(this.gz&&this.t-this.gz.t<2.5)tg=this.gz.p;else if(this.carrot)tg=[this.carrot.x,this.carrot.y];
    if(tg&&m!=='sleep'){const dx=(tg[0]-this.x)/(this.w*.5),dy=(tg[1]-(this.floor-U*1.05))/(this.h*.5);T.turn=clamp(dx*1.4,-1,1);T.tilt=clamp(dx*.18,-.2,.2);T.hy=clamp(dy*.12,-.12,.12);T.look=[clamp(dx*1.6,-1,1),clamp(dy*1.4,-1,1)];T.ear=[.05-dx*.25,.05+dx*.25];T.nose=1}
    // moods
    if(m==='sleep'){T.eye=0;T.ear=[1.05,1.15];T.loaf=1;T.nose=.1;T.tilt=.06;if(Math.random()<dt*.5)this.fx.push({k:'z',x:this.x+U*.45,y:this.floor-U*1.5,vx:12,vy:-28,life:2.4})}
    if(m==='low'){T.eye=.45;T.ear=[.9,.9];T.loaf=.7}
    if(m==='wakeup')T.eye=.7;
    if(m==='dance'){const b=A.beatPhase||0,bell=Math.sin(b*Math.PI);T.hy=-bell*.06;T.ear=[.1+bell*.35,.1+bell*.35];T.tilt=Math.sin(this.t*A.bpm/60*Math.PI)*.12;T.nose=1;if(A.beat&&A.bass>.75&&this.jump<=0&&Math.random()<.35)this.start('binky');if(A.beat&&Math.random()<.4)this.fx.push({k:'note',x:this.x+rnd(-U,U)*.7,y:this.floor-U*1.8,vx:rnd(-20,20),vy:-60,life:1.4,c:this.pal.glow})}
    if(m==='charge'&&!ac)this.start('munch');
    // petting
    if(this.hold&&this.hold.t>.4){T.eye=.15;T.ear=[1,1];T.loaf=.5;T.nose=.1;this.hold.pt=(this.hold.pt||0)-dt;if(this.hold.pt<0){this.hold.pt=1.2;this.sfx('purr');this.fx.push({k:'heart',x:this.x+rnd(-U,U)*.4,y:this.floor-U*1.7,vx:0,vy:-40,life:1.4,c:'#ff9ecf'})}}
    // carrot: hop to it and munch
    const c=this.carrot;if(c){c.s=Math.min(1,c.s+dt*3);const gap=c.x-(this.x+U*.0);if(Math.abs(gap)>U*.55){if(this.jump<=0&&(!ac||ac.type!=='hop')){this.start('hop',{side:Math.sign(gap)})}}else{if(!ac||ac.type!=='munch')this.start('munch');c.x=lerp(c.x,this.x,.1);c.y=lerp(c.y,this.floor-U*.55,.1)}}
    if(ac){const k=ac.t/ac.dur,t=ac.t,T2=ac.type,bell=Math.sin(clamp(k,0,1)*Math.PI);
      if(T2==='groom'){T.paw=1;T.pawY=Math.sin(t*9)*.06;T.eye=.15;T.hy=.05;T.tilt=Math.sin(t*4.5)*.12;T.ear=[.35,.35]}
      if(T2==='earwash'){T.paw=1;T.ear=[ac.side<0?1.5:.2,ac.side>0?1.5:.2];T.earF=[ac.side<0?.6:0,ac.side>0?.6:0];T.tilt=ac.side*.25;T.eye=.2;T.pawY=Math.sin(t*8)*.05}
      if(T2==='periscope'){T.peri=bell;T.ear=[-.05,-.05];T.eye=1.1;T.paw=.5;T.turn=Math.sin(t*1.8)*.8;T.look=[T.turn,-.2];T.nose=1}
      if(T2==='sniff'){T.hy=.08;T.nose=1.4;T.turn=Math.sin(t*1.3)*.5;if(Math.random()<dt*1.5)this.sfx('sniff')}
      if(T2==='yawn'){T.eye=.1;T.chew=bell*1.2;T.hy=-bell*.05;T.ear=[.3+bell*.4,.3+bell*.4]}
      if(T2==='thump'){T.foot=Math.max(0,Math.sin(t*14))*(k<.6?1:0);T.eye=1.25;T.ear=[-.1,-.1];if(!ac.d&&k>.1){ac.d=1;haptic(25);for(let i=0;i<10;i++)this.fx.push({k:'dust',x:this.x+U*.6+rnd(-U,U)*.2,y:this.floor,vx:rnd(-60,90),vy:-rnd(20,90),g:200,life:.8})}}
      if(T2==='scratch'){T.foot=.5+Math.sin(t*22)*.5;T.ear=[ac.side<0?.9:.2,ac.side>0?.9:.2];T.tilt=-ac.side*.2;T.eye=.4}
      if(T2==='dig'){T.dig=1;T.hy=.14;T.paw=.3;T.pawY=Math.sin(t*20)*.08;T.eye=.8;if(Math.random()<dt*14)this.fx.push({k:'dust',x:this.x+rnd(-U,U)*.25,y:this.floor-U*.05,vx:rnd(-50,50),vy:-rnd(80,200),g:500,life:.7});if(Math.random()<dt*4)this.sfx('dig')}
      if(T2==='loaf'){T.loaf=1;T.eye=.55;T.ear=[.7,.7]}
      if(T2==='nap'){T.loaf=1;T.eye=0;T.ear=[1,1.1];T.nose=.1}
      if(T2==='munch'){T.chew=.5+.5*Math.sin(t*16);T.nose=1.2;T.hy=.03;T.paw=.6;if(Math.random()<dt*3)this.sfx('munch');if(this.carrot){this.carrot.left=Math.max(0,this.carrot.left-dt*.12);if(this.carrot.left<=0){this.carrot=null;this.act=null;this.fx.push({k:'heart',x:this.x,y:this.floor-U*1.7,vx:0,vy:-40,life:1.4,c:'#ff9ecf'})}}else if(!this.charging&&k>.3)ac.t=ac.dur}
      if(T2==='binky'){T.ear=[.6,-.2];T.eye=1.15;T.tilt=Math.sin(k*Math.PI*2)*.35}
      if(T2==='hop'){T.ear=[-.15,-.15];T.eye=1.1}
      if(T2==='flinch'){T.eye=1.3;T.ear=[-.15,-.15];T.hy=.04;T.sy=.94}
      if(T2==='look'){T.turn=Math.sin(t*2.6)*.9;T.look=[T.turn,0];T.ear=[.05-T.turn*.3,.05+T.turn*.3]}}
    // blinking, nose twitching
    this.blinkT-=dt;if(this.blinkT<0){this.blinkT=rnd(2.5,6);this.blink=.18}this.blink=Math.max(0,this.blink-dt);
    this.noseT-=dt;if(this.noseT<0){this.noseT=rnd(.8,3);this.noseBurst=rnd(.4,1.2)}this.noseBurst=Math.max(0,(this.noseBurst||0)-dt);this.nosePh+=dt*(this.noseBurst>0||T.nose>.9?(14+T.nose*6):2);
    const kk=Math.min(1,dt*8);for(const k2 in T){if(Array.isArray(T[k2]))for(let i=0;i<2;i++)P[k2][i]=lerp(P[k2][i],T[k2][i],k2==='ear'?Math.min(1,dt*6):kk);else P[k2]=lerp(P[k2],T[k2],kk)}
    // hopping physics
    if(this.jump>0||this.vy<0){this.vy+=this.h*2.6*dt;this.jump=Math.max(0,this.jump-this.vy*dt);this.x+=this.vx*dt;if(this.jump<=0){this.jump=0;this.vy=0;this.vx=0;this.sfx('hop')}}
    else if(!this.carrot&&(!ac||ac.type!=='hop')){this.x+=(this.home-this.x)*Math.min(1,dt*.8)}
    this.x=clamp(this.x,U*1.1,this.w-U*1.1);
    for(let i=this.fx.length-1;i>=0;i--){const q=this.fx[i];q.life-=dt;q.vy+=(q.g||0)*dt;q.x+=q.vx*dt;q.y+=q.vy*dt;if(q.life<=0)this.fx.splice(i,1)}
    if(this.hold)this.hold.t+=dt}
  onBody(x,y){const U=this.U;return Math.abs(x-this.x)<U*1.05&&y>this.floor-this.jump-U*1.6&&y<this.floor+U*.1}
  down(p){p.sx=p.x;p.sy=p.y;p.t0=this.t;this.gz={p:[p.x,p.y],t:this.t};if(this.mood==='sleep'){this.wake=12;this.start('look');return}if(this.onBody(p.x,p.y)){this.hold={p,t:0};p.body=true}}
  up(p){const quick=this.t-p.t0<.25&&Math.hypot(p.x-p.sx,p.y-p.sy)<14,v=Math.hypot(p.vx||0,p.vy||0);if(p.body){this.hold=null;if(quick){this.pokes=this.pokes.filter(t=>this.t-t<2.5);this.pokes.push(this.t);if(this.pokes.length>=3){this.start('thump');this.pokes=[]}else{this.start('flinch');this.sfx('squeak')}haptic(8)}return}
    if(this.t-p.t0>.45&&Math.hypot(p.x-p.sx,p.y-p.sy)<20){this.dropCarrot(p.x,this.floor);return}if(v>2200)this.start('thump');else if(quick&&this.jump<=0&&Math.random()<.4)this.start('sniff')}
  scroll(v){}
  onUnlock(){this.wake=8;this.start(Math.random()<.5?'binky':'periscope')}
  /* ----- draw ----- */
  draw(ctx,skipBack){if(!skipBack){this.drawSky(ctx);this.drawFloor(ctx);this.under(ctx)}const o=this.octx||ctx,U=this.U,P=this.P,L=this.layers(),c=this.pal,t=this.t;
    const x=this.x,fy=this.floor-this.jump,ry=this.floor,shadowK=1-clamp(this.jump/(this.h*.25),0,.7);
    // contact shadow + soft rim glow for OLED
    o.save();o.translate(x,ry);o.scale(1,.18);const sg=o.createRadialGradient(0,0,0,0,0,U*1.2);sg.addColorStop(0,`rgba(0,0,0,${.7*shadowK})`);sg.addColorStop(1,'rgba(0,0,0,0)');o.fillStyle=sg;o.beginPath();o.arc(0,0,U*1.2,0,TAU);o.fill();o.restore();
    o.globalCompositeOperation='lighter';glow(o,c.glow,x,fy-U*.8,U*1.7,.12*this.lum+(this.A&&this.A.active?this.A.bass*.15:0));o.globalCompositeOperation='source-over';
    const bin=this.act&&this.act.type==='binky'?Math.sin(clamp(this.act.t/this.act.dur,0,1)*Math.PI*2)*.25:0,peri=P.peri,loaf=P.loaf;
    const bodySy=P.sy*(1+peri*.28-loaf*.06),bodySx=1+loaf*.05-peri*.08;
    // tail peeks from behind
    this.dl(o,L.tail,x+U*.74,fy-U*.28,0,.8,.8);
    // hind feet (thump lifts the right one)
    this.dl(o,L.foot,x-U*.5,fy,-.04,-1,1);this.dl(o,L.foot,x+U*.5,fy-P.foot*U*.12,.04+P.foot*.15,1,1);
    // body
    o.save();o.translate(x,fy);o.rotate(bin*.5);o.scale(bodySx,bodySy);this.dl(o,L.body,0,0);o.restore();
    // ears behind the head
    const hx=x+P.turn*U*.08+P.hx*U,hy=fy-U*(peri*.42-loaf*.08)+P.hy*U+(1-bodySy)*U*.6,tilt=P.tilt+bin*.4;
    o.save();o.translate(hx,hy);o.rotate(tilt);
    for(let i=0;i<2;i++){const s=i?1:-1,a=s*(.14+P.ear[i])+Math.sin(t*1.7+i)*.02,fl=P.earF[i];o.save();o.translate(s*U*.19+P.turn*U*.05,-U*1.4);o.rotate(a);if(fl>.05){o.scale(1,1-fl*.45)}this.dl(o,L.ear,0,0,0,s>0?-1:1,1);o.restore()}
    // head (with a soft shadow where it meets the body)
    const hs=o.createRadialGradient(0,-U*.72,0,0,-U*.72,U*.6);hs.addColorStop(0,'rgba(0,0,0,.22)');hs.addColorStop(1,'rgba(0,0,0,0)');o.fillStyle=hs;o.beginPath();o.ellipse(0,-U*.7,U*.62,U*.3,0,0,TAU);o.fill();
    this.dl(o,L.head,P.turn*U*.03,0);
    // eyes
    const eo=Math.min(1.3,P.eye)*(this.blink>0?.08:1);for(let i=0;i<2;i++){const s=i?1:-1,ex=s*U*(.31-Math.max(0,s*P.turn)*.06)+P.turn*U*.1,ey=-U*1.12,r=U*.095*(1+Math.max(0,P.eye-1)*.6);
      o.save();o.translate(ex,ey);const open=clamp(eo,0,1.3);o.beginPath();o.ellipse(0,0,r*1.15,r*1.08*Math.max(.05,Math.min(1,open)),s*-.15,0,TAU);o.fillStyle='#1a120e';o.fill();
      if(open>.15){o.save();o.clip();const ig=o.createRadialGradient(-r*.2,-r*.2,r*.1,0,0,r*1.15);ig.addColorStop(0,c.eye==='#1a0f0c'?'#3a2016':'#4a2c1c');ig.addColorStop(.55,c.eye);ig.addColorStop(1,'#050303');o.fillStyle=ig;o.fillRect(-r*1.3,-r*1.3,r*2.6,r*2.6);
        const lx=P.look[0]*r*.18,ly=P.look[1]*r*.12;o.fillStyle='rgba(255,255,255,.92)';o.beginPath();o.ellipse(-r*.38+lx*.3,-r*.38+ly*.3,r*.26,r*.2,-.5,0,TAU);o.fill();o.fillStyle='rgba(255,255,255,.55)';o.beginPath();o.arc(r*.4+lx*.3,r*.35,r*.09,0,TAU);o.fill();
        const rg=o.createLinearGradient(0,r*.2,0,r);rg.addColorStop(0,'rgba(140,160,200,0)');rg.addColorStop(1,'rgba(140,160,200,.25)');o.fillStyle=rg;o.fillRect(-r*1.3,0,r*2.6,r*1.3);o.restore()}
      o.strokeStyle='rgba(20,12,8,.8)';o.lineWidth=U*.012;o.beginPath();o.ellipse(0,0,r*1.15,r*1.08*Math.max(.05,Math.min(1,open)),s*-.15,Math.PI*1.05,Math.PI*1.95);o.stroke();o.restore()}
    // nose, mouth, whiskers
    const tw=Math.sin(this.nosePh)*.5+.5,nx=P.turn*U*.13,ny=-U*.92-tw*U*.012;o.save();o.translate(nx,ny);o.scale(1,1+tw*.08);
    const ng=o.createLinearGradient(0,-U*.04,0,U*.04);ng.addColorStop(0,'#f2a3a8');ng.addColorStop(1,'#c9737c');o.fillStyle=ng;o.beginPath();o.moveTo(-U*.055,-U*.025);o.quadraticCurveTo(0,-U*.05,U*.055,-U*.025);o.quadraticCurveTo(U*.02,U*.035,0,U*.035);o.quadraticCurveTo(-U*.02,U*.035,-U*.055,-U*.025);o.fill();
    o.strokeStyle='rgba(90,40,45,.7)';o.lineWidth=U*.008;o.beginPath();o.moveTo(0,U*.035);o.lineTo(0,U*.075+P.chew*U*.02);o.moveTo(0,U*.075+P.chew*U*.02);o.quadraticCurveTo(-U*.04,U*.1+P.chew*U*.04,-U*.07,U*.07);o.moveTo(0,U*.075+P.chew*U*.02);o.quadraticCurveTo(U*.04,U*.1+P.chew*U*.04,U*.07,U*.07);o.stroke();
    if(P.chew>.6){o.fillStyle='#fff8ee';o.fillRect(-U*.018,U*.08,U*.036,U*.03*P.chew)}
    o.strokeStyle='rgba(255,255,255,.55)';o.lineWidth=Math.max(.6,U*.004);for(const s of[-1,1])for(let i=0;i<5;i++){const a=s*(.1+i*.09)+Math.sin(this.nosePh*.5+i)*.03,len=U*(.55+i*.04);o.beginPath();o.moveTo(s*U*.07,U*.04+i*U*.006);o.quadraticCurveTo(s*len*.5,U*.02+i*U*.02+Math.sin(a)*U*.1,s*len*Math.cos(a),U*.04+Math.sin(a)*len*.35+i*U*.01);o.stroke()}
    o.restore();o.restore();
    // front paws (grooming lifts them to the face)
    for(let i=0;i<2;i++){const s=i?1:-1,up=P.paw,px=x+s*U*(.2-up*.08)+P.turn*U*.05,py=fy-up*U*(.72-P.pawY*2)+(1-bodySy)*U*.2-P.dig*U*.02+(i?P.pawY:-P.pawY)*U*.8;this.dl(o,L.paw,px,py,s*up*.3,s>0?-1:1,1)}
    // carrot
    const cr=this.carrot;if(cr){const L2=U*.55*cr.s*(.25+.75*cr.left);o.save();o.translate(cr.x,cr.y);o.rotate(-.9);const cg=o.createLinearGradient(-U*.07,0,U*.07,0);cg.addColorStop(0,'#e8661a');cg.addColorStop(.5,'#ffa64d');cg.addColorStop(1,'#c9530f');o.fillStyle=cg;o.beginPath();o.moveTo(-U*.07,0);o.quadraticCurveTo(-U*.05,L2*.7,0,L2);o.quadraticCurveTo(U*.05,L2*.7,U*.07,0);o.closePath();o.fill();o.strokeStyle='rgba(140,60,10,.5)';o.lineWidth=1;for(let i=1;i<4;i++){o.beginPath();o.moveTo(-U*.04,L2*i*.22);o.lineTo(U*.02,L2*i*.22+2);o.stroke()}
      o.fillStyle='#3fae5a';for(const a of[-.4,0,.4]){o.save();o.rotate(Math.PI+a);o.beginPath();o.ellipse(0,U*.12*cr.s,U*.025,U*.12*cr.s,0,0,TAU);o.fill();o.restore()}o.restore()}
    if(this.charging&&!cr){const hm=[x,fy-U*.62];o.save();o.translate(hm[0],hm[1]);o.rotate(-1.2);o.fillStyle='#ffa64d';o.beginPath();o.moveTo(-U*.06,0);o.quadraticCurveTo(-U*.04,U*.3,0,U*.4);o.quadraticCurveTo(U*.04,U*.3,U*.06,0);o.fill();o.globalCompositeOperation='lighter';glow(o,'#4dff7a',0,U*.2,U*.3,.5);o.restore()}
    // particles
    for(const q of this.fx){const a=Math.min(1,q.life*2);if(q.k==='dust'){o.fillStyle=`rgba(190,170,140,${a*.7})`;o.beginPath();o.arc(q.x,q.y,2.2,0,TAU);o.fill()}else if(q.k==='heart')drawShape(o,'hearts',q.x,q.y,U*.05,0,q.c,a*.85);else if(q.k==='note')drawShape(o,'notes',q.x,q.y,U*.07,1,q.c,a);else if(q.k==='z'){o.globalAlpha=a*.7;o.fillStyle='#d9c9ff';o.font=`700 ${U*.16}px "Plus Jakarta Sans",system-ui,sans-serif`;o.fillText('z',q.x,q.y);o.globalAlpha=1}}}
}
Object.assign(ArnoobScene.prototype,{loadLook:MochiScene.prototype.loadLook,saveLook:MochiScene.prototype.saveLook,lookControls:MochiScene.prototype.lookControls,under:MochiScene.prototype.under,skyStatic:MochiScene.prototype.skyStatic,drawSky:MochiScene.prototype.drawSky,groundStatic:MochiScene.prototype.groundStatic,drawFloor:MochiScene.prototype.drawFloor,floorP:Buddy.prototype.floorP});
