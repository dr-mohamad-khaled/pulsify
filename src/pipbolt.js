/* ================= PIP & BOLT — built on the same soft-body brain as Mochi ================= */
const T_=(t,a,b,d,v,dl)=>SFX.tone(t,a,b,d,v,dl);
const SNDS={mochi:MOCHI_SND,
  pip:{squish(){SFX.hiss(900,.12,.12);T_('sine',420,640,.12,.12)},boing(){T_('sine',220,700,.2,.16)},giggle(){for(let i=0;i<5;i++)T_('sine',1100+i*120,1500+i*120,.05,.08,i*.07)},
    pop(){T_('sine',800,1700,.06,.14)},achoo(){T_('sine',900,1300,.25,.06);SFX.hiss(3000,.18,.25,.35)},gulp(){T_('sine',300,180,.07,.1)},chomp(){T_('sine',1200,2400,.25,.08)},
    hmph(){T_('triangle',300,200,.14,.06)},boo(){T_('sine',400,1000,.12,.16)},purr(){SFX.hiss(180,.6,.12)},sob(){T_('sine',900,600,.2,.07);T_('sine',820,560,.22,.06,.22)},
    yay(){T_('sine',784,1175,.1,.12);T_('sine',988,1568,.12,.1,.1)},zap(){SFX.hiss(5000,.12,.35);T_('sawtooth',1800,300,.12,.05)},
    hum(){const n=[659,784,880,784,659];n.forEach((f,i)=>T_('sine',f,f,.22,.06,i*.24))},puff(){SFX.hiss(1200,.25,.18);T_('sine',300,900,.2,.08)}},
  bolt:{squish(){T_('square',660,660,.05,.05)},boing(){T_('square',300,1200,.15,.05)},giggle(){[1046,1318,1568,2093].forEach((f,i)=>T_('square',f,f,.05,.04,i*.06))},
    pop(){T_('square',1500,1500,.03,.05)},achoo(){SFX.hiss(4000,.3,.2);T_('sawtooth',200,2000,.2,.04,.1)},gulp(){T_('square',400,400,.03,.04)},chomp(){T_('square',800,1600,.1,.05)},
    hmph(){T_('sawtooth',140,110,.25,.05)},boo(){T_('square',500,1500,.1,.06)},purr(){T_('sine',110,110,.6,.06)},sob(){T_('square',880,440,.25,.04);T_('square',780,390,.25,.035,.28)},
    yay(){[523,659,784,1046].forEach((f,i)=>T_('square',f,f,.07,.05,i*.07))},alarm(){for(let i=0;i<3;i++){T_('square',1400,1400,.08,.05,i*.2);T_('square',1000,1000,.08,.05,i*.2+.1)}},
    plug(){T_('sine',160,90,.08,.2);SFX.hiss(2500,.04,.2)},reboot(){[392,523,659,784].forEach((f,i)=>T_('sine',f,f,.18,.06,i*.12))},beep(){const f=rnd(600,1500);T_('square',f,f*rnd(.8,1.3),.07,.045)},
    idea(){T_('sine',1318,2637,.15,.08);T_('sine',1760,1760,.3,.05,.12)},scan(){T_('sine',300,1800,.9,.03)}}};

/* ---------------- PIP: a static-electric puffball who lives in its headphones ---------------- */
const PIP_V=SOFT_HEAD+`uniform float uLayer,uLen;varying vec3 vN,vV,vP0;varying float vFace;
void main(){vP0=position;vec3 q=deform(position);vec3 n=normalize(position);vec3 t1=normalize(cross(n,abs(n.y)<.99?vec3(0.,1.,0.):vec3(1.,0.,0.)));vec3 t2=cross(n,t1);
 vec3 a=deform(normalize(position+t1*.02)),b=deform(normalize(position+t2*.02));vec3 N=normalize(cross(a-q,b-q));if(dot(N,n)<0.)N=-N;
 float fd=length(vec2(n.x/.6,(n.y+.02)/.48));float face=(1.-smoothstep(.72,1.,fd))*smoothstep(.35,.65,n.z);vFace=face;
 float len=uLen*(1.-face*.82)*(.85+.15*sin(n.x*9.+n.y*7.)+.12*sin(n.z*13.+uT*1.3));
 q+=N*uLayer*len;q.y-=uLayer*uLayer*len*.3;
 vec4 mv=modelViewMatrix*vec4(q,1.);vN=normalize(normalMatrix*N);vV=normalize(-mv.xyz);gl_Position=projectionMatrix*mv;}`;
const PIP_F=`uniform sampler2D uNoise,uFace;uniform float uLayer,uDen,uT,uCharge,uLv,uGlow,uFlash;uniform vec3 uRoot,uTip,uKey,uTint,uRim,uCh,uSkin;varying vec3 vN,vV,vP0;varying float vFace;
void main(){vec3 p=normalize(vP0);vec2 uv=vec2(atan(p.z,p.x)/6.2832*uDen,acos(clamp(p.y,-1.,1.))/3.1416*uDen*.5);float n=texture2D(uNoise,uv).r;float L=uLayer;
 if(L>.001&&n<L*.78+.04+vFace*1.4)discard;
 vec3 nn=normalize(vN),v=normalize(vV),Lk=normalize(uKey);float dif=dot(nn,Lk)*.5+.5;float ao=mix(.35,1.,L);
 vec3 col=mix(uRoot,uTip,clamp(pow(L,1.4)*.9+n*.15,0.,1.))*(.26+.72*dif)*ao*uTint;col+=uRim*pow(1.-max(dot(nn,v),0.),3.)*.55*(.3+L);
 if(L<.001){vec3 skin=uSkin*(.62+.45*dif)*uTint;col=mix(col,skin,smoothstep(.2,.8,vFace));vec4 f=texture2D(uFace,vec2(p.x*.5+.5,p.y*.5+.5));col=mix(col,f.rgb,f.a*smoothstep(.1,.4,p.z));
   vec3 H=normalize(Lk+v);col+=vec3(pow(max(dot(nn,H),0.),60.)*.25*vFace);}
 if(uCharge>.01){float lv=mix(-1.,1.,uLv);float inB=smoothstep(lv+.06,lv-.06,p.y);float wave=.5+.5*sin(p.y*16.-uT*5.+n*6.);col+=uCh*inB*uCharge*L*(.35+wave*.9);}
 col+=uTip*uGlow*L*.4+vec3(.75,.95,1.)*uFlash*L;
 gl_FragColor=vec4(col,1.);
 #include <tonemapping_fragment>
 #include <encodings_fragment>
}`;
const PIP_S=[.92,.78,.94,.74];
function drawPipFace(g,F,t){drawMochiFace(g,F,t,{ink:'#0d1d44',skin:'#bfe6ff',blush:'255,110,160',inner:'#123069',tongue:'#ff7fa6',eyeY:.12,eyeX:.27,mouthY:-.16})}
function buildPip(){const T=T3,k=kit(),root=new T.Group(),fc=document.createElement('canvas');fc.width=fc.height=512;const ft=new T.CanvasTexture(fc);ft.encoding=T.sRGBEncoding;
  const U={uT:{value:0},uSq:{value:0},uMelt:{value:0},uPress:{value:0},uBreath:{value:0},uPokeT:{value:9},uPull:{value:new T.Vector3()},uShear:{value:new T.Vector3()},uPoke:{value:new T.Vector4(0,0,1,0)},uS:{value:new T.Vector4(...PIP_S)},
    uFace:{value:ft},uNoise:{value:k.noise},uLen:{value:.16},uDen:{value:80},uCharge:{value:0},uLv:{value:.5},uGlow:{value:0},uFlash:{value:0},
    uRoot:{value:new T.Color('#0f2f9e')},uTip:{value:new T.Color('#55b4ff')},uSkin:{value:new T.Color('#bfe6ff')},uKey:{value:new T.Vector3(-.45,.75,.7)},uTint:{value:new T.Color('#fff')},uRim:{value:new T.Color('#9fe8ff')},uCh:{value:new T.Color('#5dff8a')}};
  const geo=new T.SphereGeometry(1,80,56),N=HAS3D&&/Android|iPhone/i.test(navigator.userAgent)?14:18,layers=[];
  for(let i=0;i<N;i++){const uu=Object.assign({},U,{uLayer:{value:i/(N-1)}});const m=new T.ShaderMaterial({uniforms:uu,vertexShader:PIP_V,fragmentShader:PIP_F});const me=new T.Mesh(geo,m);me.renderOrder=i;me.frustumCulled=false;root.add(me);layers.push(me)}
  const furM=new T.MeshStandardMaterial({color:'#8fe0ff',roughness:.8}),tuft=new T.Group();
  for(let i=0;i<3;i++){const pts=[];for(let a=0;a<1;a+=.05)pts.push(new T.Vector3(Math.sin(a*5+i)*.05*a+(i-1)*.05*a,a*.28,Math.cos(a*5+i)*.05*a));const tb=new T.Mesh(new T.TubeGeometry(new T.CatmullRomCurve3(pts),20,.025*(1.2-i*.15),8),furM);tb.rotation.z=(i-1)*.5;tuft.add(tb)}root.add(tuft);
  const hpM=new T.MeshPhysicalMaterial({color:'#1d2440',roughness:.25,clearcoat:1,metalness:.3}),pad=new T.MeshStandardMaterial({color:'#2b3358',roughness:.9}),led=new T.MeshBasicMaterial({color:'#5ff8ff',toneMapped:false});
  const hp=new T.Group(),band=new T.Mesh(new T.TorusGeometry(1,.05,12,48,Math.PI),hpM);hp.add(band);
  const cups=[-1,1].map(s=>{const c=new T.Group();const shell=new T.Mesh(new T.CylinderGeometry(.21,.21,.16,32),hpM);shell.rotation.z=Math.PI/2;c.add(shell);const cu=new T.Mesh(new T.TorusGeometry(.17,.05,10,28),pad);cu.rotation.y=Math.PI/2;cu.position.x=-s*.08;c.add(cu);
    const ring=new T.Mesh(new T.TorusGeometry(.13,.022,8,32),led);ring.rotation.y=Math.PI/2;ring.position.x=s*.085;c.add(ring);hp.add(c);return c});root.add(hp);
  const feet=[-1,1].map(s=>{const f=new T.Mesh(k.SPH,new T.MeshPhysicalMaterial({color:'#79c7ff',roughness:.6,sheen:1,sheenColor:new T.Color('#d8f4ff')}));f.scale.set(.17,.1,.16);root.add(f);return f});
  return{root,U,ft,fc,fctx:fc.getContext('2d'),layers,hp,band,cups,led,tuft,feet}}
const V3=(a)=>new T3.Vector3(a[0],a[1],a[2]);
class PipScene extends Buddy{
  constructor(pv){super(pv,'pip');this.S=PIP_S;this.u.S=PIP_S;this.col='#5fd0ff';this.rimCol='#9fe8ff';this.hemiCol='#d8f0ff';this.chargeText='Static charging';this.hpOn=0;this.puff=0;this.flash=0;this.sparks=[];this.fluff=[];this.zaps=[];
    this.DUR={hum:3.6,puff:1.8,listen:5.5,shake:1.6,zap:1.6,peek:2.4};
    this.POOLS={day:['lookAround','hum','listen','puff','shake','hop','stargaze','sneeze','press','lookAround','hum'],evening:['hum','listen','stargaze','lookAround','yawn'],wakeup:['stretch','yawn','shake','lookAround','nod'],late:['yawn','nod','hum'],low:['sigh']}}
  build(){return buildPip()} drawFace(g,F,t){drawPipFace(g,F,t)}
  poke(x,y){if(this.charging&&this.mood!=='sleep'){this.pokeT=0;this.pokeAmp=1;this.sqv+=1.5;this.startAct('zap');this.flash=1;this.zaps.push({x0:x,y0:y,x1:this.x,y1:this.y-this.U*.8,life:.35});haptic(20);if(!this.preview&&!WP)this.snd.zap();return}super.poke(x,y)}
  moodHook(m,E,dt,A){let hp=0;
    if(m==='dance'){hp=1;E.eye=this.beatN%4<2?'happy':'closed';E.open=1;E.mouth='open';E.mo=clamp(A.mid*1.2,.15,1);E.blush=1;if(A.beat&&this.beatN%2===0)this.note()}
    if(m==='charge'){E.eye=this.flash>.2?'wow':'happy';E.mouth='smile';E.blush=1.2;E.sparkle=.8}
    if(m==='sleep'){E.mouth='cat'}
    this.hpT=hp}
  actHook(T2,k,ac,E,dt){
    if(T2==='hum'){E.eye='closed';E.open=0;E.mouth='o';E.mo=.15+.1*Math.sin(ac.t*8);this.shv[0]+=Math.sin(ac.t*4)*dt*.6;ac.n=(ac.n||0)-dt;if(ac.n<0){ac.n=.55;this.note()}if(!ac.s&&!this.preview&&!WP){ac.s=1;this.snd.hum()}}
    if(T2==='puff'){E.eye='wow';E.mouth='o';E.mo=.4;E.browUp=.8;E.browOn=true;if(!ac.s){ac.s=1;this.puff=1;this.sqv-=2;if(!this.preview&&!WP)this.snd.puff()}}
    if(T2==='listen'){this.hpT=1;if(k>.12&&k<.9){E.eye='closed';E.open=0;E.mouth='smile';E.blush=1;const b=Math.sin(ac.t*TAU*2);if(b>.95&&!ac.b){ac.b=1;this.sqv+=1.4}if(b<0)ac.b=0;ac.n=(ac.n||0)-dt;if(ac.n<0){ac.n=.7;this.note()}}else{E.eye='wow';E.mouth='o'}}
    if(T2==='shake'){E.eye='squeeze';E.mouth='open';E.mo=.4;this.shv[0]+=Math.sin(ac.t*46)*dt*9*(1-k);if(Math.random()<.5)this.fluffBurst(1)}
    if(T2==='zap'){E.eye=k<.25?'spiral':'happy';E.mouth=k<.25?'open':'smile';E.mo=.6;E.blush=1.3;if(k>.25&&!ac.s){ac.s=1;if(!this.preview&&!WP)this.snd.giggle()}}}
  postHook(E,dt){const tgt=this.mood==='sleep'?0:Math.max(this.hpT||0,this.act&&this.act.type==='listen'?1:0);this.hpOn=lerp(this.hpOn,tgt,dt*3.2);this.hpT=0;
    this.puff=Math.max(0,this.puff-dt*.9);this.flash=Math.max(0,this.flash-dt*2.5);
    if(this.charging&&!this.preview){this.spT=(this.spT||0)-dt;if(this.spT<0){this.spT=rnd(.15,.5);const a=rnd(TAU),c=this.scr([Math.cos(a)*1.05,Math.sin(a)*.8+.1,.3]);this.sparks.push({x:c[0],y:c[1],a:a,life:.18})}}
    for(const L of[this.sparks,this.zaps])for(let i=L.length-1;i>=0;i--){L[i].life-=dt;if(L[i].life<=0)L.splice(i,1)}
    for(let i=this.fluff.length-1;i>=0;i--){const f=this.fluff[i];f.vy+=60*dt;f.vx*=.98;f.x+=f.vx*dt;f.y+=f.vy*dt;f.r+=f.vr*dt;f.life-=dt;if(f.life<=0)this.fluff.splice(i,1)}}
  note(){this.notes.push({x:this.x+rnd(-this.U*.5,this.U*.5),y:this.y-this.U*1.55,life:1.6,g:'♪',c:['#8fd8ff','#c9a6ff','#7dffb0','#ffe08a'][(Math.random()*4)|0]})}
  fluffBurst(n){for(let i=0;i<n;i++){const a=rnd(TAU),c=this.scr([Math.cos(a),Math.sin(a)*.8+.1,.2]);this.fluff.push({x:c[0],y:c[1],vx:Math.cos(a)*rnd(80,220),vy:-Math.abs(Math.sin(a))*rnd(60,180),r:rnd(TAU),vr:rnd(-4,4),life:rnd(.8,1.5),s:rnd(.6,1.2)})}}
  place(dt,A){const M=this.M,u=this.u,Uf=M.U,t=this.t;Uf.uLen.value=.15*(1+this.puff*.7+(this.charging?.35:0)+(A.active?A.bass*.25:0));Uf.uCharge.value=lerp(Uf.uCharge.value,this.charging?1:0,.05);Uf.uLv.value=HSTATE.charging?HSTATE.battery:.4+.5*((t*.03)%1);
    Uf.uFlash.value=this.flash;Uf.uGlow.value=A.active?A.treble*.6+A.bass*.4:0;M.led.color.setHSL(A.active?(.5+A.mid*.25+t*.05)%1:.5,1,.6+(A.active?A.bass*.25:0));
    const ph=this.force||dayPhase();Uf.uTint.value.set(ph==='night'?'#c4ccff':ph==='evening'?'#ffe6d6':'#ffffff');
    const L=mochiDeform([-1,.22,0],u),R=mochiDeform([1,.22,0],u),Tp=mochiDeform([0,1,0],u),Ln=mochiDeform([-.8,-.45,.55],u),Rn=mochiDeform([.8,-.45,.55],u),k=ease.inOut(clamp(this.hpOn,0,1)),lift=Math.sin(k*Math.PI)*.35,fl=.2*(1+this.puff*.5);
    const cl=[lerp(Ln[0]-.05,L[0]-fl,k),lerp(Ln[1],L[1],k)+lift,lerp(Ln[2],L[2],k)+lift*.6],cr=[lerp(Rn[0]+.05,R[0]+fl,k),lerp(Rn[1],R[1],k)+lift,lerp(Rn[2],R[2],k)+lift*.6];
    M.cups[0].position.set(...cl);M.cups[1].position.set(...cr);const bump=A.active?1+A.bass*.08:1;M.cups.forEach(c=>c.scale.setScalar(bump));const mid=[(cl[0]+cr[0])/2,(cl[1]+cr[1])/2,(cl[2]+cr[2])/2],rad=Math.max(.3,(cr[0]-cl[0])/2);
    M.band.position.set(...mid);M.band.scale.set(rad,lerp(.55*rad,(Tp[1]+fl+.06-mid[1]),k),1);M.band.rotation.set(lerp(1.35,0,k),0,lerp(Math.PI,0,k));
    const tp=mochiDeform([0,1,0],u);M.tuft.position.set(tp[0],tp[1]+.08*(1+this.puff),tp[2]-.05);M.tuft.rotation.z=u.shear[0]*-.8+Math.sin(t*2.3)*.08;M.tuft.scale.setScalar(1+this.puff*.4+(this.charging?.25:0));
    for(const[i,f]of M.feet.entries()){const s=i?1:-1,p=mochiDeform([s*.5,-.97,.45],u);f.position.set(p[0],Math.max(.06,p[1]+.06),p[2]+.04)}}
  over(ctx){const U=this.U;ctx.lineCap='round';
    for(const s of this.sparks){const r=U*1.1,a=s.a;ctx.strokeStyle='rgba(170,255,190,'+(s.life/.18)+')';ctx.lineWidth=1.6;ctx.beginPath();let x=s.x,y=s.y;ctx.moveTo(x,y);for(let i=0;i<4;i++){x+=Math.cos(a)*U*.07+rnd(-1,1)*U*.04;y-=Math.abs(Math.sin(a))*U*.05+rnd(-1,1)*U*.04;ctx.lineTo(x,y)}ctx.stroke();ctx.globalCompositeOperation='lighter';glow(ctx,'#7dffb0',s.x,s.y,U*.08,.7);ctx.globalCompositeOperation='source-over'}
    for(const z of this.zaps){ctx.strokeStyle='rgba(220,255,240,'+(z.life/.35)+')';ctx.lineWidth=2.4;ctx.beginPath();ctx.moveTo(z.x0,z.y0);for(let i=1;i<=8;i++){const k=i/8;ctx.lineTo(lerp(z.x0,z.x1,k)+(i<8?rnd(-1,1)*U*.08:0),lerp(z.y0,z.y1,k)+(i<8?rnd(-1,1)*U*.08:0))}ctx.stroke();ctx.globalCompositeOperation='lighter';glow(ctx,'#9dffc0',z.x0,z.y0,U*.2,z.life*2);ctx.globalCompositeOperation='source-over'}
    for(const f of this.fluff){ctx.globalAlpha=Math.min(1,f.life)*.85;ctx.fillStyle='#bfeeff';ctx.save();ctx.translate(f.x,f.y);ctx.rotate(f.r);for(let i=0;i<5;i++){ctx.beginPath();ctx.ellipse(0,0,U*.05*f.s,U*.012*f.s,i*Math.PI/5,0,TAU);ctx.fill()}ctx.restore()}ctx.globalAlpha=1}}

/* ---------------- BOLT: a squishy silicone robot with an LED face ---------------- */
const BOLT_F=`uniform sampler2D uFace;uniform vec3 uKey,uTint,uLed,uBatC;uniform float uT,uBat,uCharge,uGlow;varying vec3 vN,vV,vP0;
void main(){vec3 n=normalize(vN),v=normalize(vV);float ndv=clamp(dot(n,v),0.,1.),fr=1.-ndv;vec3 p=normalize(vP0);vec3 L=normalize(uKey);float dif=max(dot(n,L),0.);
 vec3 base=mix(vec3(.74,.79,.88),vec3(.98,.99,1.),smoothstep(-.8,.7,p.y));vec3 col=base*(.48+.6*dif)*uTint;col+=vec3(.55,.75,1.)*pow(fr,2.4)*.28;
 col+=vec3(.6,.8,1.)*pow(max(dot(n,vec3(0.,-.6,-.8)),0.),2.)*.08;
 vec2 vc=vec2(p.x/.7,(p.y-.1)/.47);float vr=length(vc);float fz=smoothstep(.12,.32,p.z);float vis=(1.-smoothstep(.95,1.,vr))*fz;float edge=smoothstep(.93,.985,vr)*(1.-smoothstep(1.,1.06,vr))*fz;
 vec3 glass=vec3(.012,.022,.035)+vec3(.12,.17,.26)*pow(fr,2.);vec4 f=texture2D(uFace,vec2(p.x*.5+.5,p.y*.5+.5));col=mix(col,glass,vis);col+=f.rgb*f.a*vis*1.5;col+=uLed*edge*(.55+uGlow);
 vec2 bc=vec2(p.x,p.y+.5)/.15;float br=length(bc);float bz=smoothstep(.55,.75,p.z);float ring=smoothstep(.62,.74,br)*(1.-smoothstep(.92,1.04,br))*bz;float ang=atan(bc.x,-bc.y)/6.2832+.5;float on=step(ang,uBat);
 col=mix(col,mix(vec3(.16,.19,.25),uBatC*(1.3+uCharge*.7*sin(uT*4.)),on),ring);col=mix(col,vec3(.05,.07,.1),(1.-smoothstep(.55,.62,br))*bz*.85);
 vec3 H=normalize(L+v);float sp=pow(max(dot(n,H),0.),70.)*(.35+vis*.9);float w1=smoothstep(.975,.988,dot(n,normalize(vec3(-.5,.62,.6))))*(.25+vis*.5);float w2=smoothstep(.994,.997,dot(n,normalize(vec3(.45,.35,.82))))*.4*vis;
 col+=vec3(sp+w1+w2);
 gl_FragColor=vec4(col,1.);
 #include <tonemapping_fragment>
 #include <encodings_fragment>
}`;
const BOLT_S=[1.06,.6,.88,.6];
function buildBolt(){const T=T3,k=kit(),root=new T.Group(),fc=document.createElement('canvas');fc.width=fc.height=512;const ft=new T.CanvasTexture(fc);ft.encoding=T.sRGBEncoding;
  const U={uT:{value:0},uSq:{value:0},uMelt:{value:0},uPress:{value:0},uBreath:{value:0},uPokeT:{value:9},uPull:{value:new T.Vector3()},uShear:{value:new T.Vector3()},uPoke:{value:new T.Vector4(0,0,1,0)},uS:{value:new T.Vector4(...BOLT_S)},
    uFace:{value:ft},uKey:{value:new T.Vector3(-.45,.75,.7)},uTint:{value:new T.Color('#fff')},uLed:{value:new T.Color('#5CF2E0')},uBatC:{value:new T.Color('#5dff8a')},uBat:{value:1},uCharge:{value:0},uGlow:{value:0}};
  const body=new T.Mesh(new T.SphereGeometry(1,96,64),new T.ShaderMaterial({uniforms:U,vertexShader:MOCHI_V,fragmentShader:BOLT_F}));root.add(body);
  const white=new T.MeshPhysicalMaterial({color:'#eef2f8',roughness:.35,clearcoat:.6,sheen:.4}),metal=new T.MeshPhysicalMaterial({color:'#9aa8bb',metalness:1,roughness:.25}),led=new T.MeshBasicMaterial({color:'#5CF2E0',toneMapped:false}),bulbM=new T.MeshBasicMaterial({color:'#ffb000',toneMapped:false});
  const ant=new T.Group(),stalk=new T.Mesh(new T.CylinderGeometry(.018,.026,.3,12),metal);stalk.position.y=.15;const bulb=new T.Mesh(k.SPH,bulbM);bulb.scale.setScalar(.075);bulb.position.y=.33;const cap=new T.Mesh(new T.CylinderGeometry(.06,.08,.05,20),white);ant.add(stalk,bulb,cap);root.add(ant);
  const ears=[-1,1].map(s=>{const e=new T.Group(),d=new T.Mesh(new T.CylinderGeometry(.17,.17,.1,32),white);d.rotation.z=Math.PI/2;const r=new T.Mesh(new T.TorusGeometry(.12,.02,8,32),led);r.rotation.y=Math.PI/2;r.position.x=s*.052;
    const fan=new T.Group();for(let i=0;i<3;i++){const b=new T.Mesh(new T.BoxGeometry(.012,.17,.04),metal);b.rotation.x=i*TAU/3;fan.add(b)}fan.position.x=s*.055;e.add(d,r,fan);e.userData.fan=fan;root.add(e);return e});
  const arms=[-1,1].map(s=>{const a=new T.Group(),m=new T.Mesh(k.SPH,white);m.scale.set(.11,.17,.11);m.position.y=-.12;a.add(m);root.add(a);return a});
  const thr=new T.Mesh(new T.TorusGeometry(.22,.03,8,36),led.clone());thr.rotation.x=Math.PI/2;root.add(thr);
  return{root,U,ft,fc,fctx:fc.getContext('2d'),ant,bulb,bulbM,ears,arms,led,thr}}
function heartPath(g,x,y,s){g.beginPath();g.moveTo(x,y+s*.45);g.bezierCurveTo(x-s*1.15,y-s*.3,x-s*.5,y-s*1.1,x,y-s*.42);g.bezierCurveTo(x+s*.5,y-s*1.1,x+s*1.15,y-s*.3,x,y+s*.45)}
function drawBoltFace(g,F,t,B){const W=512;g.clearRect(0,0,W,W);const cx=x=>(x*.5+.5)*W,cy=y=>(1-(y*.5+.5))*W,E=W*.062,col=B.led,eyeY=cy(.16)+F.look[1]*E*.6,ex=[cx(-.27),cx(.27)].map(x=>x+F.look[0]*E*.9);
  g.save();g.strokeStyle=g.fillStyle=col;g.shadowColor=col;g.shadowBlur=22;g.lineCap='round';g.lineJoin='round';const fd=B.fd;
  const eye=(X,mode,i)=>{const Y=eyeY;g.lineWidth=E*.42;
    if(mode==='happy'){g.beginPath();g.arc(X,Y+E*.5,E*.85,Math.PI*1.15,Math.PI*1.85);g.stroke();return}
    if(mode==='closed'){g.beginPath();g.moveTo(X-E*.75,Y+E*.1);g.lineTo(X+E*.75,Y+E*.1);g.stroke();return}
    if(mode==='squeeze'||mode==='cry'){const s=i?-1:1;g.beginPath();g.moveTo(X-E*.65*s,Y-E*.55);g.lineTo(X+E*.45*s,Y);g.lineTo(X-E*.65*s,Y+E*.55);g.stroke();
      if(mode==='cry')for(let k=0;k<3;k++){const yy=Y+E*(.9+((t*3+k/3)%1)*2.4);g.globalAlpha=1-((t*3+k/3)%1);g.fillRect(X-E*.13,yy,E*.26,E*.32)}g.globalAlpha=1;return}
    if(mode==='heart'){const s=E*1.15*(1+.08*Math.sin(t*10));heartPath(g,X,Y,s);g.fill();return}
    if(mode==='star'){g.save();g.translate(X,Y);g.rotate(t);g.beginPath();for(let k=0;k<10;k++){const r=k%2?E*.45:E*1.1,a=k*Math.PI/5-Math.PI/2;g.lineTo(Math.cos(a)*r,Math.sin(a)*r)}g.fill();g.restore();return}
    if(mode==='spiral'){g.lineWidth=E*.2;g.beginPath();for(let a=0;a<TAU*2.5;a+=.2){const r=a*E*.07,aa=a+t*9*(i?-1:1);g.lineTo(X+Math.cos(aa)*r,Y+Math.sin(aa)*r)}g.stroke();return}
    if(mode==='note'){g.font=`900 ${E*2.3}px system-ui,sans-serif`;g.textAlign='center';g.textBaseline='middle';g.fillText(i?'♪':'♫',X,Y);return}
    if(mode==='wow'){g.lineWidth=E*.3;g.beginPath();g.arc(X,Y,E*.95,0,TAU);g.stroke();g.beginPath();g.arc(X,Y,E*.32,0,TAU);g.fill();return}
    if(mode==='eq'){for(let k=0;k<3;k++){let v=.3;if(fd){const b=(i*3+k+1)*2;v=fd[b]/255}const hh=E*(.4+v*2.2);g.fillRect(X-E*.9+k*E*.66,Y+E*1.1-hh,E*.44,hh)}return}
    if(mode==='scan'){const sx=Math.sin(t*5)*E*.6;g.beginPath();g.arc(X+sx,Y,E*.42,0,TAU);g.fill();g.globalAlpha=.35;g.fillRect(X-E,Y-E*.1,E*2,E*.2);g.globalAlpha=1;return}
    const op=clamp(F.eyeOpen,.08,1),h=E*1.9*op,w=E*1.15;if(mode==='side'){g.beginPath();g.roundRect?g.roundRect(X-w/2,Y-h*.05,w,h*.45,w*.25):g.rect(X-w/2,Y,w,h*.4);g.fill();g.fillRect(X-w*.7,Y-h*.15,w*1.4,E*.22);return}
    g.beginPath();g.roundRect?g.roundRect(X-w/2,Y-h/2,w,h,Math.min(w,h)/2):g.rect(X-w/2,Y-h/2,w,h);g.fill();
    g.shadowBlur=0;g.fillStyle='rgba(255,255,255,.8)';if(op>.4){g.beginPath();g.arc(X-w*.15,Y-h*.22,w*.16,0,TAU);g.fill()}g.fillStyle=col;g.shadowBlur=22};
  if(F.eye==='battery'){const bw=W*.34,bh=W*.17,bx=cx(0)-bw/2,by=cy(.2)-bh/2;g.lineWidth=E*.28;g.beginPath();g.roundRect?g.roundRect(bx,by,bw,bh,E*.4):g.rect(bx,by,bw,bh);g.stroke();g.fillRect(bx+bw+E*.15,by+bh*.3,E*.35,bh*.4);
    const lv=B.bat,ani=(t*.6)%1,fw=(bw-E*.9)*Math.max(.06,lv);g.globalAlpha=.9;g.fillRect(bx+E*.45,by+E*.45,fw,bh-E*.9);g.globalAlpha=.5;g.fillRect(bx+E*.45,by+E*.45,Math.min(bw-E*.9,fw+(bw-E*.9-fw)*ani),bh-E*.9);g.globalAlpha=1;
    g.shadowBlur=0;g.fillStyle='#04120a';g.beginPath();const zx=cx(0),zy=cy(.2);g.moveTo(zx+E*.2,zy-E*1);g.lineTo(zx-E*.55,zy+E*.1);g.lineTo(zx-E*.02,zy+E*.1);g.lineTo(zx-E*.25,zy+E*1);g.lineTo(zx+E*.55,zy-E*.15);g.lineTo(zx+E*.02,zy-E*.15);g.fill();g.fillStyle=col;g.shadowBlur=22;
    g.font=`800 ${E*.95}px "Plus Jakarta Sans",system-ui,sans-serif`;g.textAlign='center';g.textBaseline='middle';g.fillText(Math.round(lv*100)+'%',cx(0),cy(-.12))}
  else if(F.eye==='load'){g.lineWidth=E*.3;g.beginPath();g.arc(cx(0),cy(.15),E*1.3,t*6,t*6+Math.PI*1.4);g.stroke();for(let k=0;k<3;k++){g.globalAlpha=.3+.7*((Math.sin(t*8-k)+1)/2);g.beginPath();g.arc(cx(-.12+k*.12),cy(-.15),E*.22,0,TAU);g.fill()}g.globalAlpha=1}
  else if(F.eye==='alert'){g.font=`900 ${E*3.2}px system-ui,sans-serif`;g.textAlign='center';g.textBaseline='middle';const b=Math.sin(t*20)>0?1:.4;g.globalAlpha=b;g.fillText('!',ex[0],eyeY);g.fillText('!',ex[1],eyeY);g.globalAlpha=1}
  else{const em=F.eye;eye(ex[0],em==='wink'?'normal':em==='wink2'?'happy':em,0);eye(ex[1],em==='wink'?'happy':em==='wink2'?'normal':em,1)}
  if(F.browOn||Math.abs(F.brow)>.3){g.lineWidth=E*.25;for(const[i,x]of ex.entries()){const s=i?1:-1,a=F.brow*.45*s,yb=eyeY-E*1.55-(F.browUp||0)*E*.4;g.beginPath();g.moveTo(x-E*.55*Math.cos(a),yb+E*.55*Math.sin(a));g.lineTo(x+E*.55*Math.cos(a),yb-E*.55*Math.sin(a));g.stroke()}}
  if(F.eye!=='battery'&&F.eye!=='load'){const mx=cx(0)+F.look[0]*E*.5,my=cy(-.17),m=F.mouth,o=clamp(F.mouthOpen,0,1);g.lineWidth=E*.3;
    if(m==='wave'){g.beginPath();for(let k=0;k<=24;k++){const x=mx-E*1.6+k*E*3.2/24;let v=Math.sin(k*.9+t*14)*.5;if(fd)v=(fd[2+k]/255-.3)*1.4*Math.sin(k*1.7+t*6);g.lineTo(x,my+v*E*.9)}g.stroke()}
    else if(m==='cat'&&o<.12){g.beginPath();g.arc(mx-E*.36,my,E*.36,0,Math.PI);g.arc(mx+E*.36,my,E*.36,0,Math.PI);g.stroke()}
    else if(m==='smile'&&o<.12){g.beginPath();g.arc(mx,my-E*.4,E*.75,Math.PI*.18,Math.PI*.82);g.stroke()}
    else if(m==='frown'){g.beginPath();g.arc(mx,my+E*.6,E*.55,Math.PI*1.2,Math.PI*1.8);g.stroke()}
    else if(m==='pout'){g.beginPath();g.moveTo(mx-E*.6,my);for(let k=1;k<=4;k++)g.lineTo(mx-E*.6+k*E*.3,my+(k%2?-1:1)*E*.18);g.stroke()}
    else if(m==='o'){g.beginPath();g.arc(mx,my+E*.1,E*(.3+o*.15),0,TAU);g.stroke()}
    else{const ow=E*(.55+o*.3),oh=E*(.14+o*.75);g.beginPath();g.roundRect?g.roundRect(mx-ow,my-oh*.3,ow*2,oh,Math.min(ow,oh)*.6):g.rect(mx-ow,my-oh*.3,ow*2,oh);g.fill()}}
  if(F.blush>.9){g.shadowBlur=10;g.fillStyle='rgba(255,120,170,.75)';for(const x of[cx(-.5),cx(.5)])for(let k=0;k<3;k++){g.save();g.translate(x-E*.5+k*E*.5,cy(-.04));g.rotate(-.5);g.fillRect(-E*.06,-E*.25,E*.12,E*.5);g.restore()}}
  g.restore();
  // LED dot-matrix look
  g.globalCompositeOperation='destination-out';g.fillStyle='rgba(0,0,0,.42)';for(let y=0;y<W;y+=7)g.fillRect(0,y,W,2.2);for(let x=0;x<W;x+=7)g.fillRect(x,0,2.2,W);g.globalCompositeOperation='source-over';
  if(B.glitch>0){const d=g.getImageData(0,0,W,W);for(let k=0;k<6;k++){const y=(Math.random()*W)|0,hh=(rnd(4,30))|0,dx=(rnd(-30,30))|0;g.putImageData(d,dx,0,0,y,W,hh)}}}
class BoltScene extends Buddy{
  constructor(pv){super(pv,'bolt');this.S=BOLT_S;this.u.S=BOLT_S;this.col='#5CF2E0';this.rimCol='#bfe9ff';this.hemiCol='#e2efff';this.chargeText='Plugged in';this.snotOn=false;this.led='#5CF2E0';this.bulb=0;this.glitch=0;this.antV=0;this.antA=0;this.plugK=1;this.wave=0;
    this.DUR={scan:3.2,idea:2.2,glitch:2.6,beep:2.4,wave:2,spin:1.4};
    this.POOLS={day:['lookAround','scan','idea','beep','wave','hop','stargaze','glitch','press','spin','lookAround'],evening:['lookAround','beep','stargaze','scan','yawn'],wakeup:['stretch','yawn','lookAround','glitch','nod'],late:['yawn','nod','beep'],low:['sigh']}}
  build(){return buildBolt()}
  drawFace(g,F,t){const A=this.A||{};drawBoltFace(g,F,t,{led:this.led,fd:A.active?A.fd:null,bat:HSTATE.charging?HSTATE.battery:(.35+.6*((this.t*.04)%1)),glitch:this.glitch})}
  sock(){return[Math.min(this.w-this.U*.3,this.w/2+this.U*1.35),this.floor+this.U*.02]}
  jarScreen(){if(!this.charging||this.jState!=='mochi')return null;return this.sock()}
  grabBack(){this.jState='mochi';this.plugK=0;this.plugFrom=[this.jx,this.jy];this.act=null;this.startAct('happy');this.say={g:'♥',life:1.4};this.tears.length=0;haptic(18);if(!this.preview&&!WP)this.snd.plug()}
  down(p){const was=this.jState;super.down(p);if(was==='mochi'&&this.jState==='user'&&!this.preview&&!WP){this.snd.alarm()}}
  moodHook(m,E,dt,A){let led='#5CF2E0';
    if(m==='dance'){E.eye=this.beatN%8<6?'eq':'note';E.mouth='wave';E.mo=.5;E.blush=1;led=null}
    if(m==='charge'){const c=(this.t%6)<3.6;E.eye=c?'battery':'happy';E.mouth='smile';led='#6dff9a'}
    if(m==='low'){led='#ffb347'}
    if(m==='sleep'){led='#3a6f8a'}
    if(m==='dizzy')led='#ffd34d';
    this.ledT=led}
  actHook(T2,k,ac,E,dt){
    if(T2==='scan'){E.eye='scan';E.mouth='cat';this.ledT='#ff5c7a';if(!ac.s){ac.s=1;if(!this.preview&&!WP)this.snd.scan()}}
    if(T2==='idea'){E.eye=k<.15?'wow':'star';E.mouth='open';E.mo=.5;E.browUp=1;E.browOn=true;this.bulb=1;if(!ac.s&&k>.1){ac.s=1;this.say={g:'!',life:1.2};this.sqv-=2;if(!this.preview&&!WP)this.snd.idea()}}
    if(T2==='glitch'){if(k<.45){this.glitch=1;E.eye=Math.random()<.5?'spiral':'squeeze';E.mouth='pout';this.shv[0]+=rnd(-1,1)*dt*6;if(!ac.s){ac.s=1;if(!this.preview&&!WP)this.snd.achoo()}}else{this.glitch=0;E.eye=k<.8?'load':'happy';E.mouth='smile';if(k>.8&&!ac.r){ac.r=1;if(!this.preview&&!WP)this.snd.reboot()}}}
    if(T2==='beep'){E.mouth='wave';E.eye=k%.3<.15?'normal':'happy';ac.b=(ac.b||0)-dt;if(ac.b<0){ac.b=rnd(.1,.22);if(!this.preview&&!WP)this.snd.beep()}}
    if(T2==='wave'){E.eye='happy';E.mouth='smile';E.blush=1;this.wave=1}
    if(T2==='spin'){E.eye='spiral';E.mouth='o';this.spin=(this.spin||0)+dt*TAU/1.4}}
  postHook(E,dt,A){if(this.jState!=='mochi'&&this.charging){E.eye=(this.t%1.6)<.5?'alert':'cry';this.ledT='#ff4d6d'}
    this.led=this.ledT===null?hslHex(((this.t*60)+(A.active?A.mid*120:0))%360,1,.65):this.ledT||'#5CF2E0';this.ledT=undefined;
    if(!(this.act&&this.act.type==='glitch'))this.glitch=0;this.bulb=Math.max(0,this.bulb-dt*.8);this.wave=Math.max(0,this.wave-dt*2);if(!(this.act&&this.act.type==='spin'))this.spin=lerp(this.spin||0,Math.round((this.spin||0)/TAU)*TAU,.1);
    const fl=this.ground?0:1;this.hov=this.U*(.1+.035*Math.sin(this.t*2.3)+(this.mood==='sleep'?-.06:0))*(1-fl*.5);
    this.antV+=(-this.antA*60-this.antV*5-(this.vx||0)*.0008-this.shv[0]*.6)*dt;this.antA+=this.antV*dt;this.plugK=Math.min(1,this.plugK+dt*2.5)}
  place(dt,A){const M=this.M,u=this.u,Uf=M.U,t=this.t,ph=this.force||dayPhase();Uf.uTint.value.set(ph==='night'?'#c4ccff':ph==='evening'?'#ffe6d6':'#ffffff');
    Uf.uLed.value.set(this.led);M.led.color.set(this.led);M.thr.material.color.set(this.led);const bat=HSTATE.charging||!this.charging?HSTATE.battery:.6;Uf.uBat.value=this.charging?(HSTATE.charging?HSTATE.battery:(.3+.7*((t*.08)%1))):bat;
    Uf.uBatC.value.set(Uf.uBat.value<.2?'#ff4d6d':Uf.uBat.value<.5?'#ffc24d':'#5dff8a');Uf.uCharge.value=this.charging?1:0;Uf.uGlow.value=A.active?A.bass:0;
    M.root.rotation.y+=this.spin||0;
    const tp=mochiDeform([0,1,0],u);M.ant.position.set(tp[0],tp[1]-.04,tp[2]);M.ant.rotation.set(0,0,this.antA+u.shear[0]*-1.2);M.ant.scale.y=1+(this.act&&this.act.type==='stretch'?ease.bell(this.act.t/this.act.dur)*.8:0);
    const beat=A.active&&A.beat;if(beat&&this.beatN%2===0)this.bulb=Math.max(this.bulb,.9);M.bulbM.color.setHSL(.11,1,.42+this.bulb*.4+(this.charging?.1*Math.sin(t*4):0));M.bulb.scale.setScalar(.075*(1+this.bulb*.35));
    for(const[i,e]of M.ears.entries()){const s=i?1:-1,p=mochiDeform([s,.18,0],u);e.position.set(p[0]+s*.02,p[1],p[2]);e.userData.fan.rotation.x+=dt*(A.active?6+A.bass*30:this.charging?10:1.5)*s}
    for(const[i,a]of M.arms.entries()){const s=i?1:-1,p=mochiDeform([s*.93,-.35,.25],u);a.position.set(p[0]+s*.04,p[1]+.04,p[2]);let r=s*.25+Math.sin(t*2+i)*.06;
      if(this.mood==='dance')r=s*(.6+.5*Math.sin((A.beatPhase||0)*TAU+i*Math.PI));if(i===1&&this.wave>0)r=2.4+Math.sin(t*16)*.4;if(this.jState!=='mochi'&&this.charging)r=s*(1.6+Math.sin(t*18+i)*.4);if(this.jState==='mochi'&&this.charging&&i===1)r=.9;a.rotation.z=r}
    M.thr.position.set(0,-.08,0);M.thr.scale.setScalar(.9+(A.active?A.bass*.3:0)+Math.sin(t*9)*.03);M.thr.visible=this.hov>this.U*.02}
  under(ctx){const U=this.U,A=this.A||{};const c=[this.x,this.floor];ctx.save();ctx.translate(c[0],c[1]);ctx.scale(1,.25);ctx.globalCompositeOperation='lighter';glow(ctx,this.led,0,0,U*.55*(1+(A.active?A.bass*.4:0)),.5);ctx.restore();
    if(!this.charging)return;const s=this.sock();ctx.fillStyle='#0b0f16';ctx.strokeStyle='rgba(120,255,170,.6)';ctx.lineWidth=1.5;ctx.beginPath();ctx.ellipse(s[0],s[1],U*.16,U*.045,0,0,TAU);ctx.fill();ctx.stroke();
    ctx.globalCompositeOperation='lighter';glow(ctx,this.jState==='mochi'?'#6dff9a':'#ff4d6d',s[0],s[1],U*.25,.6);ctx.globalCompositeOperation='source-over';
    let px,py;if(this.jState==='mochi'){const k=ease.inOut(this.plugK);px=this.plugFrom&&k<1?lerp(this.plugFrom[0],s[0],k):s[0];py=this.plugFrom&&k<1?lerp(this.plugFrom[1],s[1]-U*.12,k):s[1]-U*.12}else{px=this.jx;py=this.jy}
    const b=this.scr([.8,-.45,-.4]),mx=(b[0]+px)/2,my=Math.max(b[1],py)+U*.35;ctx.strokeStyle='#1b2333';ctx.lineWidth=U*.05;ctx.lineCap='round';ctx.beginPath();ctx.moveTo(b[0],b[1]);ctx.quadraticCurveTo(mx,my,px,py+U*.08);ctx.stroke();
    if(this.jState==='mochi'){ctx.strokeStyle='rgba(109,255,154,.85)';ctx.lineWidth=U*.014;ctx.setLineDash([U*.03,U*.09]);ctx.lineDashOffset=this.t*U*.6;ctx.beginPath();ctx.moveTo(px,py+U*.08);ctx.quadraticCurveTo(mx,my,b[0],b[1]);ctx.stroke();ctx.setLineDash([])}
    ctx.save();ctx.translate(px,py);ctx.rotate(this.jState==='mochi'?0:(this.jTilt||0));ctx.fillStyle='#e9eef6';ctx.beginPath();ctx.roundRect?ctx.roundRect(-U*.09,-U*.06,U*.18,U*.16,U*.04):ctx.rect(-U*.09,-U*.06,U*.18,U*.16);ctx.fill();
    ctx.fillStyle='#9aa8bb';ctx.fillRect(-U*.05,U*.1,U*.025,U*.07);ctx.fillRect(U*.025,U*.1,U*.025,U*.07);ctx.fillStyle=this.jState==='mochi'?'#6dff9a':'#ff4d6d';ctx.beginPath();ctx.arc(0,U*.01,U*.025,0,TAU);ctx.fill();ctx.restore()}
  over(ctx){const ac=this.act;if(ac&&ac.type==='scan'){const k=ac.t/ac.dur,c=this.scr([0,.15,1]),U=this.U,tx=this.x+Math.sin(k*TAU*1.5)*U*1.6,ty=this.floor+U*.15;
      ctx.globalCompositeOperation='lighter';const gr=ctx.createLinearGradient(c[0],c[1],tx,ty);gr.addColorStop(0,'rgba(255,92,122,.5)');gr.addColorStop(1,'rgba(255,92,122,.05)');ctx.fillStyle=gr;ctx.beginPath();ctx.moveTo(c[0]-U*.05,c[1]);ctx.lineTo(tx-U*.5,ty);ctx.lineTo(tx+U*.5,ty);ctx.lineTo(c[0]+U*.05,c[1]);ctx.fill();
      ctx.strokeStyle='rgba(255,140,160,.9)';ctx.lineWidth=2;ctx.beginPath();ctx.ellipse(tx,ty,U*.5,U*.07,0,0,TAU);ctx.stroke();ctx.globalCompositeOperation='source-over'}
    if(this.bulb>.05){const p=this.M?this.projW(this.M.bulb.getWorldPosition(new T3.Vector3())):null;if(p){ctx.globalCompositeOperation='lighter';glow(ctx,'#ffb000',p[0],p[1],this.U*.35*this.bulb,this.bulb*.7);ctx.globalCompositeOperation='source-over'}}}}
