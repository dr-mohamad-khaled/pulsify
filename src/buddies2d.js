/* ================= PIP & BOLT 2D — drawn buddies that share Mochi's whole brain =================
   They inherit every Mochi story (phone, juice, ice cream take-away + crying chase, bird with heart
   eyes, rain + umbrella, snow, balloon, selfie, juggling, …), eye contact, dancing, moods, skies and
   grounds — and each one adds its own six tricks. Drawn in 2D: no WebGL, very light on the battery. */
class Buddy2D extends MochiScene{
  constructor(pv,id){super(pv,id);this.is2D=true;this.backHz=30;this.faceHz=20;this.fc2=document.createElement('canvas');this.fc2.width=this.fc2.height=320;this.fcx=this.fc2.getContext('2d');this.ft2=-1;this.xOff=0;this.jarS=0;this.puff=0;this.zaps=[]}
  attach(){if(typeof GLS!=='undefined'&&GLS)GLS.cv.style.display='none'}
  detach(){} layoutGL(){} renderGL(){}
  /* body-space → screen (front view; yaw slides the front around, roll tilts) */
  sp(p){const q=mochiDeform(p,this.u),s=this.U*this.scale,ya=this.yaw*1.2+this.spinA,X=q[0]*s*Math.cos(ya)+q[2]*s*Math.sin(ya),Y=q[1]*s+q[2]*s*this.pitch*-.5,r=-this.rot+this.roll+(this.dz>0?Math.sin(this.t*9)*.12:0),c=Math.cos(r),sn=Math.sin(r);
    return[this.x+this.xOff+X*c-Y*sn,this.y-this.hov-(X*sn+Y*c)]}
  cenOf(pts){let x=0,y=0;for(const q of pts){x+=q[0];y+=q[1]}return[x/pts.length,y/pts.length]}
  rollAng(){return-this.rot+this.roll+(this.dz>0?Math.sin(this.t*9)*.12:0)}
  shoulder(i){const s=i?1:-1,P=this.SH||[.74,-.1,.66];return this.sp([s*P[0],P[1],P[2]])}
  hand(i){const s=i?1:-1,sh=this.shoulder(i),[out,fwd]=this.armS[i],L=this.U*(this.armLen||.36)*(1-.32*Math.sin(clamp(fwd,0,1.6)));let dx=s*Math.sin(out)-s*.22*Math.sin(clamp(fwd,0,1.6)),dy=-Math.cos(out);const r=this.rollAng(),c=Math.cos(r),sn=Math.sin(r),X=dx*c-dy*sn,Y=dx*sn+dy*c;return[sh[0]+X*L,sh[1]-Y*L]}
  ballScr(){return this.tipScr()}
  update(dt,A){super.update(dt,A);const p=this.prop;this.spinA=0;this.u.melt=Math.min(this.u.melt,.08);
    if(!(this.act&&this.act.type==='spin')&&this.spinT===0){this.spinA=this.spinA>Math.PI?lerp(this.spinA,TAU,.12):lerp(this.spinA,0,.12);if(Math.abs(this.spinA-TAU)<.01)this.spinA=0}
    if(p&&p.grab&&this.jState==='mochi'){const hm=this.handsMid(),hr=this.hand(1);p.pos=p.type==='icecream'?[hr[0],hr[1]-this.U*.2]:[hm[0],hm[1]+this.U*(p.type==='juice'?.02:.1)]}
    this.jarS=lerp(this.jarS,this.charging?1:0,.1);if(this.charging&&this.jState==='mochi'){const hm=this.handsMid();this.jarPos=[hm[0],hm[1]-this.U*.05]}
    const bt=this.ballT,dc=this.pal.led||this.pal.tip;let tc=dc;if(bt&&bt[0]==='#')tc=bt;else if(bt&&bt.startsWith('hsl'))tc=hslHex(parseFloat(bt.slice(4)),1,.7);this.ballCol=tc;this.ballBoost=Math.max(0,(this.ballBoost||0)-dt);
    this.puff=lerp(this.puff,this.puffT||0,.12);this.puffT=0;this.xOff=lerp(this.xOff,this.xOffT||0,.08);this.xOffT=0;
    for(let i=this.zaps.length-1;i>=0;i--){this.zaps[i].life-=dt;if(this.zaps[i].life<=0)this.zaps.splice(i,1)}
    if(this.t-this.ft2>1/this.faceHz){this.ft2=this.t;const sig=this.faceSig();if(sig!==this.lastSig){this.lastSig=sig;this.paintFace()}}}
  controls(){const c=MochiScene.prototype.controls.call(this);c.push({type:'buttons',label:'Specials',items:this.extra.map(a=>({name:a[0].toUpperCase()+a.slice(1),fn:()=>this.forceAct(a)}))});return c}
  jarScreen(){if(this.charging)return this.jState==='mochi'&&this.jarS>.5?this.jarPos:null;const p=this.prop;if(!p||!p.grab||p.away||this.jState!=='mochi')return null;return p.pos||null}
  extraHook(){return false}
  actHook(T2,k,ac,E,dt,A){if(!this.extraHook(T2,k,ac,E,dt,A))MochiScene.prototype.actHook.call(this,T2,k,ac,E,dt,A)}
  silhouette(n){const pts=[];for(let i=0;i<n;i++){const a=i/n*TAU;pts.push(this.sp([Math.cos(a),Math.sin(a),0]))}return pts}
  bodyPath(ctx,pts){const n=pts.length;ctx.beginPath();const l=pts[n-1],f=pts[0];ctx.moveTo((l[0]+f[0])/2,(l[1]+f[1])/2);for(let i=0;i<n;i++){const a=pts[i],c=pts[(i+1)%n];ctx.quadraticCurveTo(a[0],a[1],(a[0]+c[0])/2,(a[1]+c[1])/2)}ctx.closePath()}
  faceXf(ctx){const S=this.S,c=this.sp([0,0,1]);ctx.translate(c[0],c[1]);ctx.rotate(this.rollAng());ctx.scale(Math.max(.05,Math.cos(this.yaw*1.2+this.spinA)),1);return[S[2]*this.U*this.scale/.625,S[0]*this.U*this.scale/.625]}
  faceVisible(){return Math.cos(this.yaw*1.2+this.spinA)>.15}
  drawJar2D(ctx,x,y,s,t,rot){const U=this.U*s,w=U*.42,h=U*.56,lv=clamp(HSTATE.charging?HSTATE.battery:.45+.4*((t*.02)%1),.08,1);ctx.save();ctx.translate(x,y);ctx.rotate(rot||0);
    ctx.globalCompositeOperation='lighter';glow(ctx,'#4dff7a',0,0,U*.7,.35);ctx.globalCompositeOperation='source-over';
    rrP(ctx,-w/2,-h/2,w,h,U*.08);ctx.fillStyle='rgba(200,255,235,.12)';ctx.fill();ctx.save();ctx.clip();const lh=h*.92*lv;const g=ctx.createLinearGradient(0,h/2-lh,0,h/2);g.addColorStop(0,'#b9ffc9');g.addColorStop(.15,'#42ff74');g.addColorStop(1,'#1fb8ff');ctx.fillStyle=g;ctx.fillRect(-w/2,h/2-lh,w,lh);
    ctx.fillStyle='rgba(255,255,255,.6)';for(let i=0;i<6;i++){const by=h/2-((t*.35+i*.17)%1)*lh;ctx.beginPath();ctx.arc(-w*.3+((i*.37)%1)*w*.6,by,U*.012,0,TAU);ctx.fill()}ctx.restore();
    ctx.strokeStyle='rgba(220,255,240,.7)';ctx.lineWidth=1.5;rrP(ctx,-w/2,-h/2,w,h,U*.08);ctx.stroke();ctx.fillStyle='#d3dbe6';ctx.fillRect(-w*.53,-h/2-U*.04,w*1.06,U*.05);ctx.fillRect(-w*.53,h/2-U*.01,w*1.06,U*.04);ctx.fillRect(-U*.05,-h/2-U*.08,U*.1,U*.05);
    ctx.fillStyle='#f4ffb0';ctx.beginPath();ctx.moveTo(U*.03,-U*.12);ctx.lineTo(-U*.07,U*.01);ctx.lineTo(-U*.005,U*.01);ctx.lineTo(-U*.03,U*.12);ctx.lineTo(U*.07,-U*.02);ctx.lineTo(U*.005,-U*.02);ctx.fill();
    ctx.fillStyle='rgba(255,255,255,.3)';ctx.fillRect(-w*.38,-h*.4,w*.1,h*.78);ctx.restore()}
  drawArm(ctx,i,col,w,handCol,handR){if(this.armHide[i]>.6)return;const sh=this.shoulder(i),hd=this.hand(i);ctx.lineCap='round';ctx.strokeStyle=col;ctx.lineWidth=w;ctx.beginPath();ctx.moveTo(sh[0],sh[1]);ctx.lineTo(hd[0],hd[1]);ctx.stroke();ctx.fillStyle=handCol;ctx.beginPath();ctx.arc(hd[0],hd[1],handR,0,TAU);ctx.fill()}
  draw2D(ctx){const k={grid:.14,glass:.22,water:.14}[this.gnd];if(k){ctx.save();ctx.beginPath();ctx.rect(0,this.floor,this.w,this.h-this.floor);ctx.clip();ctx.translate(0,this.floor*2);ctx.scale(1,-1);ctx.globalAlpha=k;this.drawCore(ctx,true);ctx.restore();
      const g=ctx.createLinearGradient(0,this.floor,0,this.floor+this.U*1.4);g.addColorStop(0,'rgba(0,0,0,0)');g.addColorStop(1,'rgba(0,0,0,1)');ctx.save();ctx.globalCompositeOperation='destination-out';ctx.fillStyle=g;ctx.fillRect(0,this.floor,this.w,this.U*1.4);ctx.restore()}
    this.drawCore(ctx,false);
    if(this.jarS>.02){const s=ease.outBack(clamp(this.jarS,0,1)),pos=this.jState==='mochi'?this.jarPos:[this.jx,this.jy];if(pos){if(this.jState==='mochi'&&this.strawToMouth){const m=this.sp([0,-.06,1]);ctx.strokeStyle='#9fe8ff';ctx.lineWidth=this.U*.03;ctx.lineCap='round';ctx.beginPath();ctx.moveTo(pos[0],pos[1]-this.U*.3);ctx.quadraticCurveTo(pos[0],m[1],m[0],m[1]+this.U*.02);ctx.stroke()}
      if(this.jState==='mochi'&&this.cable)this.cable(ctx,pos);this.drawJar2D(ctx,pos[0],pos[1],s,this.t,this.jState==='mochi'?0:this.jTilt)}}}
}

/* ---------------- PIP: a chubby little bunny with floppy ears ---------------- */
const PIP_PALS=[
 {name:'Snow',top:'#ffffff',bot:'#e6d8f7',inner:'#ffa6c9',rim:'#ff9ad6',rim2:'#8fd0ff',skin:'#fff6fb',lid:'#fbf2f8',face:'#fbf2f8',ink:'#2b1426',glow:'#ff8fd0',belly:'#ffffff'},
 {name:'Peach',top:'#fff0e6',bot:'#ffb48f',inner:'#ff7f9f',rim:'#ff6f8f',rim2:'#ffd23d',skin:'#ffe6d8',lid:'#ffeadf',face:'#ffeadf',ink:'#3a160c',glow:'#ff8a5c',belly:'#fff8f2'},
 {name:'Mocha',top:'#f7e2cc',bot:'#b98258',inner:'#ffa6b8',rim:'#ffb36b',rim2:'#ff6fae',skin:'#f0d4b8',lid:'#f3dcc6',face:'#f3dcc6',ink:'#2a1408',glow:'#ffa04d',belly:'#fff4e8'},
 {name:'Lilac',top:'#f6efff',bot:'#b48aff',inner:'#ff9ad6',rim:'#d06bff',rim2:'#4fd8ff',skin:'#ece0ff',lid:'#efe6ff',face:'#efe6ff',ink:'#1e0b3d',glow:'#a95cff',belly:'#fbf8ff'},
 {name:'Mint',top:'#effff8',bot:'#7fe6c0',inner:'#ffa6c9',rim:'#2dffb0',rim2:'#4f9dff',skin:'#dcfff0',lid:'#e6fff5',face:'#e6fff5',ink:'#062a20',glow:'#1fffb0',belly:'#ffffff'},
 {name:'Sky',top:'#f0f8ff',bot:'#8ec4ff',inner:'#ffa6c9',rim:'#38c8ff',rim2:'#c04dff',skin:'#e0efff',lid:'#e8f3ff',face:'#e8f3ff',ink:'#0c1a33',glow:'#3db8ff',belly:'#ffffff'},
 {name:'Honey',top:'#fff8e0',bot:'#ffc94d',inner:'#ff9fb0',rim:'#ffb31a',rim2:'#ff5c8a',skin:'#fff0c2',lid:'#fff4d4',face:'#fff4d4',ink:'#3a2a05',glow:'#ffc21a',belly:'#fffcf0'},
 {name:'Midnight',top:'#6b6fa8',bot:'#24264a',inner:'#ff7fd0',rim:'#ff5cd8',rim2:'#38e8ff',skin:'#555a90',lid:'#5f639a',face:'#5f639a',ink:'#fff0fa',glow:'#b04dff',belly:'#8a8ec4'}];
const PIP_SND=Object.assign({},MOCHI2_SND,SNDS.pip);
class PipScene extends Buddy2D{
  constructor(pv){super(pv,'pip');this.snd=PIP_SND;this.PALS=PIP_PALS;this.defLook={sky:'bokeh',ground:'grass'};this.loadLook();this.col=this.pal.glow;this.chargeText='Sipping carrot juice';this.strawToMouth=true;
    this.S=this.u.S=[.92,.84,.74,.84];this.u.bulge=.05;this.SH=[.62,-.22,.75];this.armLen=.28;this.sleepMelt=.06;this.extra=['carrot','helicopter','bighop','flower','bubbles','dj','earwave'];
    Object.assign(this.DUR,{carrot:12,helicopter:3.6,bighop:2.6,flower:4.4,bubbles:4.6,dj:5,earwave:2.4});this.ears=[{a:0,v:0},{a:0,v:0}];this.soap=[]}
  resize(w,h){super.resize(w,h);this.U=Math.min(w*.32,h*.19)}
  tipScr(){const t=this.sp([0,1,0]);return[t[0],t[1]-this.U*.06]}
  paintFace(){drawMochi2Face(this.fcx,this.F,this.t,{skin:this.pal.face,ink:this.pal.ink,shiver:this.shiver,ax:this.S[0]/this.S[2]})}
  startAct(type){super.startAct(type);if(type==='carrot'&&this.act&&this.act.type==='carrot')this.prop={type:'carrot',grab:true,act:this.act,s:0,left:1,happy:0}}
  update(dt,A){super.update(dt,A);const ac=this.act,m=this.mood;
    // ears: springy, droop when sad/sleepy, perk when surprised, bounce to the beat, spin for the helicopter
    let base=.16,perk=0;const F=this.F;if(m==='sleep'||m==='low'||F.eye==='cry'||F.eye==='teary'||F.mouth==='wobble')base=1.25;else if(F.eye==='wow'||F.eye==='heart')base=.02;if(m==='wakeup')base=.7;
    for(let i=0;i<2;i++){const e=this.ears[i];let tg=base+(i?.08:0);if(ac&&ac.type==='earwave'&&i===1)tg=-.6+Math.sin(ac.t*14)*.5;if(ac&&ac.type==='helicopter'){e.a+=dt*22;e.v=0;continue}if(e.a>Math.PI)e.a=(e.a%TAU)>Math.PI?(e.a%TAU)-TAU:(e.a%TAU);
      e.v+=(-(e.a-tg)*90-e.v*7)*dt+(A.active&&A.beat?(i?3:-3)*A.bass:0)+(this.vy-(this.pvy2||0))/this.h*(i?-6:6);e.a+=e.v*dt}this.pvy2=this.vy;
    this.hovT2=ac&&ac.type==='helicopter'?Math.sin(clamp(ac.t/ac.dur,0,1)*Math.PI)*this.h*.25:0;this.hov=lerp(this.hov,this.hovT2,.1);
    for(let i=this.soap.length-1;i>=0;i--){const b=this.soap[i];b.y-=b.v*dt;b.x+=Math.sin(this.t*2+b.ph)*12*dt;b.life-=dt;if(b.life<=0)this.soap.splice(i,1)}}
  extraHook(T2,k,ac,E,dt,A){const U=this.U,t=ac.t,s=this.sfx.bind(this),p=this.prop;
    switch(T2){
    case'carrot':{this.hold2();E.lookT=[0,.25];if(p&&p.happy>0){E.eye='heart';E.mouth='smile';E.blush=1.4;return true}const ph=t%1.6;if(ph<.9){E.eye='happy';E.mouth=Math.sin(t*22)>0?'open':'cat';E.mo=.25;if(p){p.left=Math.max(.12,p.left-dt*.05)}if(Math.random()<dt*6){const c=this.sp([0,-.1,1]);this.fx.push({k:'dot',x:c[0]+rnd(-U*.1,U*.1),y:c[1],vx:rnd(-60,60),vy:-rnd(30,80),g:600,life:.6,c:'#ff9a3d'})}if(ph<dt*1.2)s('chomp')}else{E.eye='normal';E.mouth='cat';E.blush=1}return true}
    case'helicopter':{E.eye=k<.85?'wow':'happy';E.mouth='open';E.mo=.6;this.arm(0,1.5,.1);this.arm(1,1.5,.1);if(!ac.w){ac.w=1;s('whee')}return true}
    case'bighop':{const n=Math.floor(k*3);if(!ac['h'+n]&&n<3&&this.ground){ac['h'+n]=1;this.vy=-this.h*.36;this.ground=false;this.sqv-=2;s('boing')}E.eye='happy';E.mouth='open';E.mo=.5;this.arm(0,2.2,.3);this.arm(1,2.2,.3);return true}
    case'flower':{ac.fl=Math.min(1,k/.15);if(k<.6){E.eye='closed';E.open=0;E.mouth='smile';E.blush=1.4;this.pitchT=.12;E.lookT=[.4,.8];if(Math.random()<dt*2)this.fx.push({k:'heart',x:this.x+U*.5,y:this.y-U*1.3,vx:0,vy:-40,life:1.2,c:'#ff8fc0'})}
      else if(k<.72){E.eye='squeeze';E.mouth='o';E.mo=(k-.6)/.12}else{E.eye='happy';E.mouth='open';E.mo=.6;if(!ac.a){ac.a=1;s('achoo');this.sqv+=4;haptic(14);const c=this.sp([0,-.05,1]);for(let i=0;i<12;i++)this.fx.push({k:'heart',x:c[0]+U*.4,y:c[1]+U*.4,vx:rnd(40,260),vy:-rnd(60,220),g:200,life:1.4,c:['#ff8fd0','#ffd23d','#ffffff'][i%3]})}}return true}
    case'bubbles':{this.arm(1,.9,1.25);E.mouth='o';E.mo=.15;E.eye=Math.sin(t*2)>0?'wow':'normal';E.sparkle=1;if(Math.random()<dt*4){const h=this.hand(1);this.soap.push({x:h[0]+U*.1,y:h[1]-U*.15,r:rnd(.05,.12)*U,v:rnd(30,60),ph:rnd(TAU),life:rnd(3,5)})}return true}
    case'dj':{ac.hp=1;this.arm(1,.6,1.2+Math.sin(t*10)*.25);this.arm(0,1.9+Math.sin(t*5)*.2,.4);this.pitchT=Math.max(0,Math.sin(t*8))*.12;E.eye='closed';E.open=0;E.mouth='grin';if(!ac.h){ac.h=1;s('hum')}if(Math.random()<dt*4){const c=this.headScr();this.fx.push({k:'note',x:c[0]+rnd(-U,U)*.6,y:c[1]-U*.1,vx:rnd(-20,20),vy:-70,life:1.3,c:this.pal.rim})}return true}
    case'earwave':{E.eye='happy';E.mouth='open';E.mo=.4;E.blush=1;if(!ac.s){ac.s=1;this.bub={txt:'Hi!',life:1.6}}return true}}
    return false}
  ear(ctx,i,refl){const s=i?1:-1,p=this.pal,U=this.U,e=this.ears[i],b=this.sp([s*.34,.94,.15]),r=this.rollAng(),a=s*e.a+r,L=U*.92,W=U*.25,dx=Math.sin(a),dy=-Math.cos(a);
    ctx.save();ctx.translate(b[0],b[1]);ctx.rotate(a);
    const bend=i&&!(this.act&&this.act.type==='helicopter')?.35+Math.max(0,e.a)*.2:0;const path=(w,l,o)=>{ctx.beginPath();ctx.moveTo(-w*.5,o);ctx.bezierCurveTo(-w*.75,-l*.45,-w*.45,-l*.95,0+(bend*l*.25),-l);ctx.bezierCurveTo(w*.45+bend*l*.3,-l*.95,w*.75,-l*.45,w*.5,o);ctx.closePath()};
    path(W,L,U*.05);const g=ctx.createLinearGradient(0,0,0,-L);g.addColorStop(0,p.bot);g.addColorStop(.4,p.top);g.addColorStop(1,p.top);ctx.fillStyle=g;ctx.fill();if(!refl){ctx.save();ctx.globalCompositeOperation='lighter';ctx.strokeStyle=hexA(p.rim,.55);ctx.lineWidth=1.5;ctx.stroke();ctx.restore();
      path(W*.5,L*.8,-U*.02);const g2=ctx.createLinearGradient(0,0,0,-L*.8);g2.addColorStop(0,hexA(p.inner,.95));g2.addColorStop(1,hexA(p.inner,.55));ctx.fillStyle=g2;ctx.fill()}ctx.restore()}
  drawCore(ctx,refl){const p=this.pal,U=this.U,t=this.t,A=this.A||{},bass=A.active?A.bass:0,pts=this.silhouette(refl?24:44),cen=this.cenOf(pts),ac=this.act;
    if(!refl){ctx.globalCompositeOperation='lighter';glow(ctx,p.glow,cen[0],cen[1],U*1.6,.2+bass*.25+(this.charging?.1:0));ctx.globalCompositeOperation='source-over'}
    const back=!this.faceVisible();
    // cotton tail peeks out behind
    const tl=this.sp([.9,-.55,-.2]);ctx.fillStyle=p.top;ctx.beginPath();ctx.arc(tl[0]+U*.05,tl[1],U*.16,0,TAU);ctx.fill();
    for(let i=0;i<2;i++)this.ear(ctx,i,refl);
    const hp=A.active||(ac&&ac.type==='dj');if(hp&&!refl){const l=this.sp([-1,.4,0]),r=this.sp([1,.4,0]),top=this.sp([0,1.12,0]);ctx.strokeStyle='#2a2440';ctx.lineWidth=U*.08;ctx.lineCap='round';ctx.beginPath();ctx.moveTo(l[0],l[1]);ctx.quadraticCurveTo(top[0],top[1]-U*.3,r[0],r[1]);ctx.stroke();ctx.strokeStyle=hexA(p.rim,.7);ctx.lineWidth=U*.022;ctx.stroke()}
    // body
    this.bodyPath(ctx,pts);const g=ctx.createRadialGradient(cen[0]-U*.3,cen[1]-U*.42,U*.08,cen[0],cen[1],U*1.12);g.addColorStop(0,p.top);g.addColorStop(.55,p.top);g.addColorStop(1,p.bot);ctx.fillStyle=g;ctx.fill();
    if(!refl){ctx.save();ctx.clip();const bl=this.sp([0,-.5,1]);const bg=ctx.createRadialGradient(bl[0],bl[1],0,bl[0],bl[1],U*.55);bg.addColorStop(0,hexA(p.belly,.8));bg.addColorStop(1,hexA(p.belly,0));ctx.fillStyle=bg;ctx.beginPath();ctx.ellipse(bl[0],bl[1],U*.55,U*.45,0,0,TAU);ctx.fill();
      ctx.fillStyle='rgba(255,255,255,.45)';ctx.beginPath();ctx.ellipse(cen[0]-U*.42,cen[1]-U*.5,U*.2,U*.09,-.6,0,TAU);ctx.fill();ctx.restore();ctx.save();ctx.globalCompositeOperation='lighter';ctx.strokeStyle=hexA(p.rim,.6);ctx.lineWidth=2;this.bodyPath(ctx,pts);ctx.stroke();ctx.restore()}
    // face + little nose
    if(!back){ctx.save();const[fw,fh]=this.faceXf(ctx);ctx.drawImage(this.fc2,-fw/2,-fh/2,fw,fh);if(!refl){const ny=-.03*this.S[0]*U+this.look[1]*U*.02,nx=this.look[0]*U*.03;ctx.fillStyle=p.inner;ctx.beginPath();ctx.moveTo(nx-U*.045,ny-U*.02);ctx.quadraticCurveTo(nx,ny-U*.045,nx+U*.045,ny-U*.02);ctx.quadraticCurveTo(nx+U*.01,ny+U*.03,nx,ny+U*.03);ctx.quadraticCurveTo(nx-U*.01,ny+U*.03,nx-U*.045,ny-U*.02);ctx.fill();ctx.fillStyle='rgba(255,255,255,.7)';ctx.beginPath();ctx.arc(nx-U*.015,ny-U*.02,U*.01,0,TAU);ctx.fill()}ctx.restore()}
    if(refl)return;
    // big bunny feet with toe beans
    for(const s of[-1,1]){const f=this.sp([s*.42,-.96,.6]),fy=Math.min(f[1],this.floor-U*.02);ctx.fillStyle=p.top;ctx.beginPath();ctx.ellipse(f[0]+s*U*.04,fy,U*.2,U*.13,s*.15,0,TAU);ctx.fill();ctx.strokeStyle=hexA(p.bot,.6);ctx.lineWidth=1.2;ctx.stroke();
      ctx.fillStyle=p.inner;ctx.beginPath();ctx.ellipse(f[0]+s*U*.04,fy+U*.02,U*.07,U*.05,0,0,TAU);ctx.fill();for(const k of[-1,0,1]){ctx.beginPath();ctx.arc(f[0]+s*U*.04+k*U*.065,fy-U*.06,U*.025,0,TAU);ctx.fill()}}
    if(hp){for(const s of[-1,1]){const c=this.sp([s*1,.4,.1]);ctx.fillStyle='#231d36';ctx.beginPath();ctx.ellipse(c[0],c[1],U*.13,U*.2,0,0,TAU);ctx.fill();ctx.strokeStyle=hexA(p.rim,.6+bass*.4);ctx.lineWidth=U*.03;ctx.stroke();ctx.globalCompositeOperation='lighter';glow(ctx,p.rim,c[0],c[1],U*.2,.3+bass*.6);ctx.globalCompositeOperation='source-over'}}
    // paws
    for(let i=0;i<2;i++){if(this.armHide[i]>.6||back)continue;const sh=this.shoulder(i),hd=this.hand(i);ctx.lineCap='round';ctx.strokeStyle=p.top;ctx.lineWidth=U*.15;ctx.beginPath();ctx.moveTo(sh[0],sh[1]);ctx.lineTo(hd[0],hd[1]);ctx.stroke();ctx.strokeStyle=hexA(p.bot,.35);ctx.lineWidth=1.2;ctx.beginPath();ctx.arc(hd[0],hd[1],U*.075,0,TAU);ctx.stroke();ctx.fillStyle=hexA(p.inner,.8);ctx.beginPath();ctx.arc(hd[0],hd[1]+U*.02,U*.022,0,TAU);ctx.fill()}
    // props of its own
    const pr=this.prop;if(pr&&pr.type==='carrot'&&pr.s>0){const pos=this.jState==='mochi'?pr.pos:[this.jx,this.jy];if(pos){const k=ease.outBack(pr.s),L=U*.6*k*(.35+.65*pr.left);ctx.save();ctx.translate(pos[0],pos[1]);ctx.rotate(this.jState==='mochi'?-.5:(this.jTilt||0)-.5);const cg=ctx.createLinearGradient(-U*.08,0,U*.08,0);cg.addColorStop(0,'#ff8a2a');cg.addColorStop(.5,'#ffb35c');cg.addColorStop(1,'#e8661a');ctx.fillStyle=cg;ctx.beginPath();ctx.moveTo(-U*.09*k,-U*.02);ctx.quadraticCurveTo(-U*.07*k,L*.6,0,L*.75);ctx.quadraticCurveTo(U*.07*k,L*.6,U*.09*k,-U*.02);ctx.closePath();ctx.fill();ctx.strokeStyle='rgba(160,70,10,.5)';ctx.lineWidth=1.2;for(let i=1;i<4;i++){ctx.beginPath();ctx.moveTo(-U*.05,L*i*.18);ctx.lineTo(U*.01,L*i*.18+U*.01);ctx.stroke()}
      ctx.fillStyle='#4dd17a';for(const a of[-.5,0,.5]){ctx.save();ctx.rotate(a+Math.PI);ctx.beginPath();ctx.ellipse(0,U*.12*k,U*.035*k,U*.12*k,0,0,TAU);ctx.fill();ctx.restore()}ctx.restore()}}
    if(ac&&ac.type==='flower'&&ac.fl){const x=this.x+U*.75,y=this.floor,hh=U*.55*ac.fl;ctx.strokeStyle='#3fbf6a';ctx.lineWidth=U*.03;ctx.beginPath();ctx.moveTo(x,y);ctx.quadraticCurveTo(x+U*.05,y-hh*.5,x,y-hh);ctx.stroke();if(ac.t/ac.dur<.72){for(let i=0;i<6;i++){const a=i*TAU/6+t*.5;ctx.fillStyle=i%2?'#ff8fd0':'#ffb3e0';ctx.beginPath();ctx.ellipse(x+Math.cos(a)*U*.07,y-hh+Math.sin(a)*U*.07,U*.06,U*.035,a,0,TAU);ctx.fill()}}ctx.fillStyle='#ffd23d';ctx.beginPath();ctx.arc(x,y-hh,U*.045,0,TAU);ctx.fill()}
    if(ac&&ac.type==='bubbles'){const h=this.hand(1);ctx.strokeStyle='#ff8fd0';ctx.lineWidth=2;ctx.beginPath();ctx.moveTo(h[0],h[1]);ctx.lineTo(h[0]+U*.1,h[1]-U*.12);ctx.stroke();ctx.beginPath();ctx.arc(h[0]+U*.12,h[1]-U*.17,U*.05,0,TAU);ctx.stroke()}
    for(const b of this.soap){const a=Math.min(1,b.life);ctx.globalAlpha=a;const g3=ctx.createRadialGradient(b.x-b.r*.3,b.y-b.r*.3,b.r*.1,b.x,b.y,b.r);g3.addColorStop(0,'rgba(255,255,255,.25)');g3.addColorStop(.75,'rgba(180,220,255,.12)');g3.addColorStop(1,`hsla(${(b.ph*60+t*40)%360},90%,75%,.6)`);ctx.fillStyle=g3;ctx.beginPath();ctx.arc(b.x,b.y,b.r,0,TAU);ctx.fill();ctx.fillStyle='rgba(255,255,255,.8)';ctx.beginPath();ctx.ellipse(b.x-b.r*.4,b.y-b.r*.4,b.r*.18,b.r*.1,-.6,0,TAU);ctx.fill()}ctx.globalAlpha=1;
    if(ac&&ac.type==='dj'){const c=this.hand(1),x=c[0]-U*.1,y=c[1]+U*.12;ctx.fillStyle='#1b1626';rrP(ctx,x-U*.35,y-U*.08,U*.7,U*.2,U*.04);ctx.fill();ctx.save();ctx.translate(x,y-U*.02);ctx.scale(1,.35);ctx.rotate(this.t*8);ctx.fillStyle='#0b0b12';ctx.beginPath();ctx.arc(0,0,U*.24,0,TAU);ctx.fill();ctx.strokeStyle=hexA(p.rim,.8);ctx.lineWidth=2;ctx.beginPath();ctx.arc(0,0,U*.16,0,4);ctx.stroke();ctx.fillStyle=p.rim;ctx.beginPath();ctx.arc(0,0,U*.05,0,TAU);ctx.fill();ctx.restore()}}
}

/* ---------------- BOLT: a glossy little robot with an LED face ---------------- */
const BOLT_PALS=[
 {name:'Classic',top:'#ffffff',bot:'#b3bdd0',rim:'#5ce1ff',rim2:'#9d6bff',skin:'#e9eef6',lid:'#0a0d14',ink:'#0a0d14',glow:'#3fd6ff',led:'#5CF2E0',sw0:'#ffffff',sw1:'#5CF2E0'},
 {name:'Midnight',top:'#3a3f55',bot:'#12141f',rim:'#ff4fd8',rim2:'#38e8ff',skin:'#2a2e40',lid:'#05060a',ink:'#05060a',glow:'#ff4fd8',led:'#ff6be0',sw0:'#2a2e40',sw1:'#ff6be0'},
 {name:'Toy red',top:'#ff8f8f',bot:'#d0202f',rim:'#ffd23d',rim2:'#ffffff',skin:'#ff9f9f',lid:'#140608',ink:'#140608',glow:'#ff4040',led:'#ffd23d',sw0:'#e8303f',sw1:'#ffd23d'},
 {name:'Mint',top:'#e8fff5',bot:'#6ad6b0',rim:'#2dffb0',rim2:'#4f9dff',skin:'#c8f5e4',lid:'#06120e',ink:'#06120e',glow:'#2dffb0',led:'#7dffb0',sw0:'#c8f5e4',sw1:'#2dffb0'},
 {name:'Gold',top:'#fff4c2',bot:'#c8901a',rim:'#ffe066',rim2:'#ff8a3d',skin:'#f2d48a',lid:'#120c02',ink:'#120c02',glow:'#ffc21a',led:'#ffb36b',sw0:'#e8b94a',sw1:'#ffe066'},
 {name:'Sky',top:'#e6f4ff',bot:'#4a8fe8',rim:'#38c8ff',rim2:'#ffffff',skin:'#bcd9ff',lid:'#040a14',ink:'#040a14',glow:'#38a8ff',led:'#9fe8ff',sw0:'#8ab8ff',sw1:'#9fe8ff'},
 {name:'Grape',top:'#e6d4ff',bot:'#6a35c8',rim:'#c58bff',rim2:'#ff6fd8',skin:'#cbb0ff',lid:'#0a0516',ink:'#0a0516',glow:'#a35cff',led:'#e0b8ff',sw0:'#9a6bff',sw1:'#e0b8ff'},
 {name:'Lime',top:'#f4ffd6',bot:'#7ab82a',rim:'#b6ff4f',rim2:'#38e8ff',skin:'#d8f5a8',lid:'#081004',ink:'#081004',glow:'#9dff3d',led:'#c8ff6b',sw0:'#b6e86b',sw1:'#c8ff6b'}];
const BOLT_SND=Object.assign({},MOCHI2_SND,SNDS.bolt);
/* LED face: the expression is drawn on a tiny grid, then every lit cell becomes a glowing dot */
function drawBoltLED(out,F,t,O){const GW=34,GH=18,g=O.grid,x=g.getContext('2d');x.clearRect(0,0,GW,GH);const led=O.led,lk=F.look,lx=lk[0]*3,ly=lk[1]*1.6;x.lineCap='round';x.lineJoin='round';
  const eye=(cx,i)=>{const X=cx+lx,Y=6.5+ly,e=F.eye,op=clamp(F.eyeOpen,0,1),sd=i?1:-1;x.fillStyle=led;x.strokeStyle=led;x.lineWidth=1.6;
    if(O.shades){x.fillStyle='#ffffff';x.fillRect(X-4,Y-2,8,4);return}
    if(e==='happy'||(e==='wink'&&i)||(e==='wink2'&&!i)){x.beginPath();x.arc(X,Y+1.5,3,Math.PI*1.1,Math.PI*1.9);x.stroke();return}
    if(e==='closed'||(e==='peek'&&!i)){x.beginPath();x.arc(X,Y-1,3,Math.PI*.15,Math.PI*.85);x.stroke();return}
    if(e==='squeeze'||e==='cry'){x.beginPath();x.moveTo(X-2.5*sd,Y-2.4);x.lineTo(X+2*sd,Y);x.lineTo(X-2.5*sd,Y+2.4);x.stroke();if(e==='cry'){x.fillStyle='#6fc8ff';const d=(t*14+i*5)%9;x.fillRect(X-.5,Y+3+d,1.4,2)}return}
    if(e==='heart'){x.fillStyle='#ff4f8a';const s=3.4*(1+.12*Math.sin(t*10));x.save();x.translate(X,Y);x.beginPath();x.moveTo(0,s*.5);x.bezierCurveTo(-s*1.1,-s*.3,-s*.5,-s*1.05,0,-s*.4);x.bezierCurveTo(s*.5,-s*1.05,s*1.1,-s*.3,0,s*.5);x.fill();x.restore();return}
    if(e==='star'){x.fillStyle='#ffe066';starP(x,X,Y,3.6,t);x.fill();return}
    if(e==='spiral'){x.lineWidth=1;x.beginPath();for(let a=0;a<TAU*2;a+=.3){const r=a*.28,aa=a+t*9*sd;x.lineTo(X+Math.cos(aa)*r,Y+Math.sin(aa)*r)}x.stroke();return}
    if(e==='note'){x.font='bold 9px sans-serif';x.textAlign='center';x.textBaseline='middle';x.fillText(i?'♪':'♫',X,Y);return}
    if(e==='dot'){x.fillRect(X-1,Y-1,2,2);return}
    const big=e==='wow'?1.25:e==='teary'?1.1:1,h=Math.max(.6,5.4*op*big),w=4.2*big;rrP(x,X-w/2,Y-h/2,w,h,Math.min(1.8,h/2));x.fill();
    if(e==='side'){x.clearRect(X-w/2-1,Y-h/2-1,w+2,h*.55)}if(e==='teary'){x.fillStyle='#6fc8ff';x.fillRect(X-w/2,Y+h/2-1.2,w,1.2);x.fillRect(X+sd*2.6,Y+2+((t*3)%1)*4,1.2,1.6)}};
  eye(10,0);eye(24,1);
  if(F.blush>.9){x.fillStyle='rgba(255,90,150,.9)';x.fillRect(4+lx*.4,10.5,3,1.4);x.fillRect(27+lx*.4,10.5,3,1.4)}
  const mx=17+lx*.6,my=13.6+ly*.4,m=F.mouth,o=clamp(F.mouthOpen,0,1);x.strokeStyle=led;x.fillStyle=led;x.lineWidth=1.3;
  if(O.eq){for(let i=0;i<9;i++){const v=O.eq[i]||0,hh=1+v*4.5;x.fillRect(mx-8+i*2,my+1.5-hh,1.3,hh)}}
  else if(m==='cat'&&o<.15){x.beginPath();x.arc(mx-1.5,my-1,1.5,.2,Math.PI-.2);x.arc(mx+1.5,my-1,1.5,.2,Math.PI-.2);x.stroke()}
  else if(m==='smile'&&o<.15){x.beginPath();x.arc(mx,my-3,3.4,Math.PI*.2,Math.PI*.8);x.stroke()}
  else if(m==='frown'){x.beginPath();x.arc(mx,my+2.5,3,Math.PI*1.2,Math.PI*1.8);x.stroke()}
  else if(m==='wobble'||m==='pout'){x.beginPath();for(let i=0;i<=8;i++)x.lineTo(mx-4+i,my+Math.sin(i*1.6+t*14)*.8);x.stroke()}
  else if(m==='kiss'){x.beginPath();x.arc(mx,my-.8,1,-1.6,1.6);x.arc(mx,my+1,1,-1.6,1.6);x.stroke()}
  else if(m==='tongue'){x.beginPath();x.arc(mx,my-2,2.5,Math.PI*.2,Math.PI*.8);x.stroke();x.fillStyle='#ff6f97';x.fillRect(mx-.8,my,1.8,2)}
  else if(m==='grin'){x.fillRect(mx-4,my-1.2,8,2.4);x.clearRect(mx-3,my-.6,6,.8)}
  else{const w=2.2+o*1.5,h=.8+o*3;rrP(x,mx-w,my-h/2,w*2,h,Math.min(1,h/2));x.fill()}
  if(O.boot!=null){x.clearRect(0,0,GW,GH);x.fillStyle=led;x.strokeStyle=led;x.lineWidth=1;x.strokeRect(6.5,7.5,21,4);x.fillRect(8,9,19*O.boot,1.5)}
  if(O.noise){for(let i=0;i<40;i++){x.fillStyle=Math.random()<.5?led:'#ff4f8a';x.fillRect((Math.random()*GW)|0,(Math.random()*GH)|0,1,1)}}
  if(O.zz){x.clearRect(0,0,GW,GH);x.fillStyle=led;x.globalAlpha=.6;x.fillRect(7,7,7,1.3);x.fillRect(20,7,7,1.3);x.font='bold 7px sans-serif';x.fillText('z',27+((t*2)%3),4);x.globalAlpha=1}
  // dots
  const d=x.getImageData(0,0,GW,GH).data,W=out.width,H=out.height,cw=W/GW,ch=H/GH;const o2=out.getContext('2d');o2.clearRect(0,0,W,H);
  o2.globalAlpha=.5;o2.imageSmoothingEnabled=true;o2.filter='none';o2.drawImage(g,0,0,W,H);o2.globalAlpha=1;
  for(let j=0;j<GH;j++)for(let i=0;i<GW;i++){const k=(j*GW+i)*4,a=d[k+3];if(a<70){o2.fillStyle='rgba(255,255,255,.04)';o2.fillRect(i*cw+cw*.35,j*ch+ch*.35,cw*.3,ch*.3);continue}o2.fillStyle=`rgb(${d[k]},${d[k+1]},${d[k+2]})`;o2.beginPath();o2.arc(i*cw+cw/2,j*ch+ch/2,Math.min(cw,ch)*.42,0,TAU);o2.fill()}}
class BoltScene extends Buddy2D{
  constructor(pv){super(pv,'bolt');this.snd=BOLT_SND;this.PALS=BOLT_PALS;this.defLook={sky:'city',ground:'glass'};this.loadLook();this.col=this.pal.glow;this.chargeText='Charging up';this.snotOn=false;
    this.S=this.u.S=[.84,.74,.8,.74];this.u.bulge=.02;this.SH=[.96,-.35,.15];this.armLen=.42;this.sleepMelt=0;this.extra=['scan','glitch','reboot','rocket','magnet','shades'];
    Object.assign(this.DUR,{scan:3.2,glitch:1.8,reboot:4.2,rocket:3.6,magnet:4,shades:3.4});this.fc2.width=340;this.fc2.height=180;this.grid=document.createElement('canvas');this.grid.width=34;this.grid.height=18;this.nuts=[];this.fan=0;this.faceHz=15}
  resize(w,h){super.resize(w,h);this.U=Math.min(w*.31,h*.18)}
  update(dt,A){super.update(dt,A);const ac=this.act,hv=ac&&ac.type==='rocket'?Math.sin(clamp(ac.t/ac.dur,0,1)*Math.PI)*this.h*.28:0;this.hovT=this.U*(this.mood==='sleep'?.06:.16)+Math.sin(this.t*2.2)*this.U*.03+hv;this.hov=lerp(this.hov,this.hovT,.12);this.fan+=dt*((A.active?8+A.bass*30:2)+(this.charging?8:0))}
  tipScr(){const top=this.sp([0,1,0]),U=this.U,a=this.antA*.9+this.rollAng();return[top[0]+Math.sin(a)*U*.42,top[1]-Math.cos(a)*U*.42]}
  faceSig(){const A=this.A||{},ac=this.act;return MochiScene.prototype.faceSig.call(this)+'|'+(this.mood==='dance'?Math.floor(this.t*15):0)+'|'+(ac&&['reboot','glitch','shades'].includes(ac.type)?ac.type+Math.floor(this.t*12):'')+'|'+this.mood}
  paintFace(){const A=this.A||{},ac=this.act,O={grid:this.grid,led:this.ballT&&this.mood!=='dance'?this.pal.led:this.pal.led};
    if(this.mood==='dance'&&A.fd){O.eq=[];for(let i=0;i<9;i++)O.eq.push((A.fd[2+i*3]||0)/255)}
    if(ac&&ac.type==='reboot'){const k=ac.t/ac.dur;if(k<.25)O.boot=0;else if(k<.8)O.boot=(k-.25)/.55}if(ac&&ac.type==='glitch')O.noise=1;if(ac&&ac.type==='shades'&&ac.t/ac.dur<.75)O.shades=1;if(this.mood==='sleep'&&!this.act)O.zz=1;
    drawBoltLED(this.fc2,this.F,this.t,O)}
  extraHook(T2,k,ac,E,dt,A){const U=this.U,t=ac.t,s=this.sfx.bind(this);
    switch(T2){
    case'scan':{E.eye='normal';E.open=.4;E.mouth='o';E.mo=.1;this.pitchT=.1;ac.sweep=Math.sin(k*Math.PI*2)*.9;E.lookT=[ac.sweep,.4];if(!ac.s){ac.s=1;s('scan')}return true}
    case'glitch':{E.eye=Math.random()<.5?'spiral':'squeeze';E.mouth='wobble';this.shv[0]+=rnd(-1,1)*dt*8;if(Math.random()<dt*8)s('beep');this.glitchT=.1;return true}
    case'reboot':{if(k>.8){E.eye='wow';E.mouth='open';E.mo=.5;if(!ac.b){ac.b=1;s('reboot');this.sqv-=2;this.ballBoost=1}}else{E.eye='closed';E.open=0;this.arm(0,.05,.1);this.arm(1,.05,.1);this.ballT='#333a4a'}return true}
    case'rocket':{this.arm(0,2.2,.2);this.arm(1,2.2,.2);E.eye=k<.85?'wow':'happy';E.mouth='open';E.mo=.7;if(!ac.r){ac.r=1;s('whee');if(!this.preview&&!WP)SFX.whoosh()}return true}
    case'magnet':{this.arm(1,1.3,.9);E.eye='normal';E.mouth='grin';E.lookT=[.6,0];if(!ac.n){ac.n=1;for(let i=0;i<7;i++)this.nuts.push({x:Math.random()<.5?-20:this.w+20,y:rnd(this.h*.2,this.h*.8),vx:0,vy:0,r:rnd(TAU),st:0})}if(k>.85){for(const n of this.nuts){n.st=2}E.eye='squeeze';E.mouth='wobble'}return true}
    case'shades':{E.eye=k<.75?'normal':'wink';E.mouth='grin';this.arm(1,2.3,.6);if(!ac.c){ac.c=1;s('yay')}return true}}
    return false}
  cable(ctx,pos){const c=this.sp([0,-.2,1]);ctx.strokeStyle='#2a3040';ctx.lineWidth=this.U*.05;ctx.lineCap='round';ctx.beginPath();ctx.moveTo(pos[0],pos[1]-this.U*.3);ctx.quadraticCurveTo(pos[0]+this.U*.3,c[1]-this.U*.2,c[0],c[1]);ctx.stroke()}
  drawCore(ctx,refl){const p=this.pal,U=this.U,t=this.t,A=this.A||{},bass=A.active?A.bass:0,pts=this.silhouette(refl?24:44),cen=this.cenOf(pts),gx=this.glitchT>0?rnd(-3,3):0;this.glitchT=Math.max(0,(this.glitchT||0)-dtFrame);
    if(!refl){ctx.globalCompositeOperation='lighter';glow(ctx,p.glow,cen[0],cen[1],U*1.5,.2+bass*.25);
      // hover jet
      const b=this.sp([0,-1,0]),fl=U*(.35+(this.act&&this.act.type==='rocket'?.9:0)+bass*.4)*(.85+.15*Math.sin(t*40));const g=ctx.createLinearGradient(b[0],b[1],b[0],b[1]+fl);g.addColorStop(0,hexA(p.rim,.9));g.addColorStop(1,hexA(p.rim,0));ctx.fillStyle=g;ctx.beginPath();ctx.moveTo(b[0]-U*.18,b[1]);ctx.quadraticCurveTo(b[0],b[1]+fl*1.2,b[0]+U*.18,b[1]);ctx.fill();glow(ctx,p.rim,b[0],b[1]+U*.05,U*.3,.6);ctx.globalCompositeOperation='source-over'}
    ctx.save();ctx.translate(gx,0);
    // ear fans
    for(const s of[-1,1]){const c=this.sp([s*1.02,.15,0]);ctx.fillStyle=p.bot;ctx.beginPath();ctx.arc(c[0],c[1],U*.2,0,TAU);ctx.fill();if(refl)continue;ctx.fillStyle='#1a1f2c';ctx.beginPath();ctx.arc(c[0],c[1],U*.14,0,TAU);ctx.fill();ctx.strokeStyle=hexA(p.rim,.9);ctx.lineWidth=U*.03;for(let i=0;i<3;i++){const a=this.fan*s+i*TAU/3;ctx.beginPath();ctx.moveTo(c[0],c[1]);ctx.lineTo(c[0]+Math.cos(a)*U*.12,c[1]+Math.sin(a)*U*.12);ctx.stroke()}ctx.fillStyle=p.rim;ctx.beginPath();ctx.arc(c[0],c[1],U*.035,0,TAU);ctx.fill()}
    // shell
    this.bodyPath(ctx,pts);const g=ctx.createLinearGradient(cen[0]-U,cen[1]-U*.9,cen[0]+U*.6,cen[1]+U*.8);g.addColorStop(0,p.top);g.addColorStop(.55,p.skin);g.addColorStop(1,p.bot);ctx.fillStyle=g;ctx.fill();
    if(!refl){ctx.save();ctx.globalCompositeOperation='lighter';ctx.strokeStyle=hexA(p.rim,.75);ctx.lineWidth=2;ctx.stroke();ctx.restore();ctx.save();ctx.clip();ctx.fillStyle='rgba(255,255,255,.35)';ctx.beginPath();ctx.ellipse(cen[0]-U*.45,cen[1]-U*.55,U*.32,U*.12,-.5,0,TAU);ctx.fill();ctx.strokeStyle='rgba(0,0,0,.12)';ctx.lineWidth=2;const a=this.sp([-1,-.45,0]),b=this.sp([1,-.45,0]),m=this.sp([0,-.62,1]);ctx.beginPath();ctx.moveTo(a[0],a[1]);ctx.quadraticCurveTo(m[0],m[1]+U*.1,b[0],b[1]);ctx.stroke();ctx.restore()
      // chest light
      const cl=this.sp([0,-.62,1]);ctx.fillStyle='#10141c';ctx.beginPath();ctx.arc(cl[0],cl[1],U*.08,0,TAU);ctx.fill();ctx.globalCompositeOperation='lighter';glow(ctx,this.charging?'#4dff7a':p.led,cl[0],cl[1],U*.15,.7+bass*.3);ctx.globalCompositeOperation='source-over';ctx.fillStyle=this.charging?'#7dff9a':p.led;ctx.beginPath();ctx.arc(cl[0],cl[1],U*.04,0,TAU);ctx.fill()}
    // visor + LED face
    if(this.faceVisible()){ctx.save();const[fw,fh]=this.faceXf(ctx),vw=U*1.25,vh=U*.66,vy=-U*.12;rrP(ctx,-vw/2,vy-vh/2,vw,vh,vh*.42);ctx.fillStyle=p.lid;ctx.fill();if(!refl){ctx.save();ctx.clip();ctx.drawImage(this.fc2,-vw*.46,vy-vh*.44,vw*.92,vh*.88);const gg=ctx.createLinearGradient(0,vy-vh/2,0,vy);gg.addColorStop(0,'rgba(255,255,255,.14)');gg.addColorStop(1,'rgba(255,255,255,0)');ctx.fillStyle=gg;ctx.fillRect(-vw/2,vy-vh/2,vw,vh*.4);ctx.restore();ctx.strokeStyle=hexA(p.rim,.5);ctx.lineWidth=1.5;rrP(ctx,-vw/2,vy-vh/2,vw,vh,vh*.42);ctx.stroke()}ctx.restore()}
    ctx.restore();if(refl)return;
    // antenna
    const top=this.sp([0,1,0]),tip=this.tipScr();ctx.strokeStyle='#8a93a8';ctx.lineWidth=U*.035;ctx.lineCap='round';ctx.beginPath();ctx.moveTo(top[0],top[1]+U*.03);ctx.lineTo(tip[0],tip[1]);ctx.stroke();const bc=this.ballCol&&this.ballCol.length===7?this.ballCol:p.led;ctx.globalCompositeOperation='lighter';glow(ctx,bc,tip[0],tip[1],U*(.25+(this.ballBoost||0)*.2),.6+bass*.4);ctx.globalCompositeOperation='source-over';ctx.fillStyle=bc;ctx.beginPath();ctx.arc(tip[0],tip[1],U*.07,0,TAU);ctx.fill();
    // arms
    for(let i=0;i<2;i++)this.drawArm(ctx,i,'#8a93a8',U*.07,p.rim,U*.075);
    // scan laser
    const ac=this.act;if(ac&&ac.type==='scan'){const e=this.sp([0,.1,1]),sw=ac.sweep||0,ex=this.x+sw*this.w*.55,ey=this.h*.98;ctx.globalCompositeOperation='lighter';const g2=ctx.createLinearGradient(e[0],e[1],ex,ey);g2.addColorStop(0,'rgba(255,60,90,.9)');g2.addColorStop(1,'rgba(255,60,90,.05)');ctx.strokeStyle=g2;ctx.lineWidth=2;ctx.beginPath();ctx.moveTo(e[0],e[1]);ctx.lineTo(ex,ey);ctx.stroke();ctx.fillStyle='rgba(255,60,90,.12)';ctx.beginPath();ctx.moveTo(e[0],e[1]);ctx.lineTo(ex-this.w*.1,ey);ctx.lineTo(ex+this.w*.1,ey);ctx.fill();glow(ctx,'#ff3c5a',ex,ey,U*.25,.8);ctx.globalCompositeOperation='source-over'}
    // magnet + flying nuts
    if(ac&&ac.type==='magnet'){const h=this.hand(1);ctx.save();ctx.translate(h[0],h[1]-U*.08);ctx.lineWidth=U*.09;ctx.lineCap='butt';ctx.strokeStyle='#e8303f';ctx.beginPath();ctx.arc(0,0,U*.12,Math.PI*.05,Math.PI*.95,true);ctx.stroke();ctx.strokeStyle='#dfe6f0';for(const sd of[-1,1]){ctx.beginPath();ctx.moveTo(sd*U*.12,0);ctx.lineTo(sd*U*.12,U*.07);ctx.stroke()}ctx.restore();
      for(const n of this.nuts){if(n.st<2){const dx=h[0]-n.x,dy=h[1]-n.y,d=Math.hypot(dx,dy)+1;if(d<U*.2){n.st=1;n.x=h[0]+Math.cos(n.r)*U*.16;n.y=h[1]+U*.05+Math.sin(n.r)*U*.08}else{n.vx+=dx/d*900*dtFrame;n.vy+=dy/d*900*dtFrame;n.x+=n.vx*dtFrame;n.y+=n.vy*dtFrame}}else{n.vy+=1200*dtFrame;n.y+=n.vy*dtFrame;n.x+=n.vx*dtFrame*.3}
        n.r+=dtFrame*4;ctx.save();ctx.translate(n.x,n.y);ctx.rotate(n.r);ctx.fillStyle='#c9d1de';ctx.beginPath();for(let i=0;i<6;i++){const a=i*TAU/6;ctx.lineTo(Math.cos(a)*U*.045,Math.sin(a)*U*.045)}ctx.fill();ctx.fillStyle='#5a6378';ctx.beginPath();ctx.arc(0,0,U*.018,0,TAU);ctx.fill();ctx.restore()}}
    else if(this.nuts.length)this.nuts.length=0}
}
