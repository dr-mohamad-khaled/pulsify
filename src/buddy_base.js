/* ================= MOCHI — a strawberry-milk jelly cat, rebuilt from scratch =================
   Body: soft-body jelly shader (dents where you poke, stretches like dough, wobbles, melts, presses
   its face on the glass). Face: hand-drawn every frame onto the jelly, so it can make any expression.
   Brain: remembers how you treat it (affection), gets grumpy if over-poked, notices shooting stars,
   eats falling stars, blows bubbles, sneezes, hides and says boo, sleeps with a bubble, drinks juice. */
const SOFT_HEAD=`uniform float uT,uSq,uMelt,uPress,uBreath,uPokeT;uniform vec3 uPull,uShear;uniform vec4 uPoke,uS;
vec3 shp(vec3 p){vec3 q=p;q.y=p.y<0.?p.y*uS.y:p.y*uS.x;float w=1.+.08*(1.-smoothstep(-.6,.8,p.y));q.x*=w*uS.z;q.z*=w*.9*uS.z;q.y+=uS.w;return q;}
vec3 deform(vec3 p){vec3 q=shp(p);float h=clamp(q.y/1.45,0.,1.);
 q.y*=1.-.55*uMelt;q.xz*=1.+.4*uMelt*(1.-h*.4);
 q.y*=1.-uSq;q.xz*=1.+uSq*.85;q*=1.+uBreath*.016;
 q.x+=uShear.x*h*h;q.z+=uShear.z*h*h;q.y+=uShear.y*h;
 float pl=length(uPull);if(pl>.001){vec3 pd=uPull/pl;float wg=smoothstep(-.25,1.,dot(normalize(p+vec3(0.,.25,0.)),pd));q+=uPull*pow(wg,2.)*(.3+h*.7);}
 if(uPoke.w>0.){float d=dot(normalize(p),uPoke.xyz);float dent=smoothstep(.78,1.,d);float a=acos(clamp(d,-1.,1.));float rip=sin(a*10.-uPokeT*22.)*exp(-uPokeT*5.)*(1.-smoothstep(0.,1.6,a));q-=normalize(p)*(dent*uPoke.w*.2*exp(-uPokeT*3.)+rip*uPoke.w*.03);}
 float zm=mix(2.5,.5,uPress);if(q.z>zm){q.xy*=1.+(q.z-zm)*.7;q.z=zm;}
 return q;}`;
const MOCHI_V=SOFT_HEAD+`varying vec3 vN,vV,vP0;
void main(){vP0=position;vec3 q=deform(position);vec3 n=normalize(position);vec3 t1=normalize(cross(n,abs(n.y)<.99?vec3(0.,1.,0.):vec3(1.,0.,0.)));vec3 t2=cross(n,t1);
 vec3 a=deform(normalize(position+t1*.02)),b=deform(normalize(position+t2*.02));vec3 N=normalize(cross(a-q,b-q));if(dot(N,n)<0.)N=-N;
 vec4 mv=modelViewMatrix*vec4(q,1.);vN=normalize(normalMatrix*N);vV=normalize(-mv.xyz);gl_Position=projectionMatrix*mv;}`;
const MOCHI_F=`uniform sampler2D uFace;uniform vec3 uTop,uBot,uRim,uKey,uTint,uFill,uStarC;uniform float uT,uFillLv,uFillA,uStar,uGlow;varying vec3 vN,vV,vP0;
void main(){vec3 n=normalize(vN),v=normalize(vV);float ndv=clamp(dot(n,v),0.,1.),fr=1.-ndv;
 vec3 base=mix(uBot,uTop,smoothstep(-.9,.9,vP0.y));vec3 col=mix(base,vec3(1.,.78,.88),pow(fr,1.8)*.35);
 vec3 L=normalize(uKey);col*=(max(dot(n,L),0.)*.45+.55)*uTint;
 col+=vec3(1.,.55,.7)*pow(max(dot(n,vec3(0.,-.6,-.8)),0.),2.)*.12;
 if(uFillA>.01){float lv=mix(-1.,.55,uFillLv)+sin(vP0.x*7.+uT*3.)*.035+sin(vP0.z*5.-uT*2.)*.02;float inF=smoothstep(lv+.03,lv-.03,vP0.y);col=mix(col,uFill*(.55+.45*ndv),inF*uFillA*.7);col+=uFill*inF*uFillA*.18;}
 if(uStar>.01){float d=length(vP0-vec3(.05,-.35,.75));col+=uStarC*uStar*(exp(-d*d*9.)*1.1+exp(-d*d*2.)*.25);}
 vec2 fu=vec2(vP0.x*.5+.5,vP0.y*.5+.5);float front=smoothstep(.08,.4,vP0.z);vec4 f=texture2D(uFace,fu);col=mix(col,f.rgb,f.a*front);
 vec3 H=normalize(L+v);float sp=pow(max(dot(n,H),0.),110.)*.8;float w1=smoothstep(.972,.985,dot(n,normalize(vec3(-.58,.68,.45))))*.55;float w2=smoothstep(.993,.997,dot(n,normalize(vec3(.5,.3,.81))))*.35;
 col+=vec3(sp*.7+w1+w2)+uRim*pow(fr,3.)*.45+uGlow*vec3(1.,.6,.8)*.15;
 gl_FragColor=vec4(col,1.);
 #include <tonemapping_fragment>
 #include <encodings_fragment>
}`;
/* CPU mirror of the shader deformation, for placing ears, feet, juice box and screen effects */
function mochiDeform(p,u){const l=Math.hypot(p[0],p[1],p[2]),n=[p[0]/l,p[1]/l,p[2]/l];const S=u.S||MOCHI_S;let x=n[0],y=n[1],z=n[2];y=y<0?y*S[1]:y*S[0];const w=1+(u.bulge||.08)*(1-smoothstepJ(-.6,.8,n[1]));x*=w*S[2];z*=w*.9*S[2];y+=S[3];const h=clamp(y/1.45,0,1);
  y*=1-.55*u.melt;const mw=1+.4*u.melt*(1-h*.4);x*=mw;z*=mw;y*=1-u.sq;x*=1+u.sq*.85;z*=1+u.sq*.85;const br=1+u.breath*.016;x*=br;y*=br;z*=br;
  x+=u.shear[0]*h*h;z+=u.shear[2]*h*h;y+=u.shear[1]*h;const pl=Math.hypot(...u.pull);if(pl>.001){const d=(n[0]*u.pull[0]+(n[1]+.25)*u.pull[1]+n[2]*u.pull[2])/pl/Math.hypot(n[0],n[1]+.25,n[2]);const wg=Math.pow(smoothstepJ(-.25,1,d),2)*(.3+h*.7);x+=u.pull[0]*wg;y+=u.pull[1]*wg;z+=u.pull[2]*wg}
  const zm=lerp(2.5,.5,u.press);if(z>zm){x*=1+(z-zm)*.7;y*=1+(z-zm)*.7;z=zm}return[x,y,z]}
const MOCHI_S=[.9,.58,1,.55];
function smoothstepJ(a,b,x){const t=clamp((x-a)/(b-a),0,1);return t*t*(3-2*t)}
const MOCHI_SND={squish(){SFX.tone('sine',260,520,.16,.18)},boing(){SFX.tone('sine',180,640,.22,.2);SFX.tone('triangle',360,900,.12,.05,.05)},giggle(){for(let i=0;i<4;i++)SFX.tone('sine',900+i*90,1250+i*90,.06,.09,i*.08)},
  pop(){SFX.tone('sine',700,1600,.06,.16)},achoo(){SFX.tone('sine',500,900,.35,.08);SFX.hiss(2400,.25,.35,.5);SFX.tone('triangle',900,300,.18,.15,.5)},gulp(){SFX.tone('sine',220,140,.08,.12)},
  chomp(){SFX.tone('square',300,220,.05,.06);SFX.tone('sine',1200,2400,.3,.08,.08)},hmph(){SFX.tone('sawtooth',200,150,.15,.05)},boo(){SFX.tone('sine',300,900,.12,.2)},purr(){SFX.hiss(140,.6,.18)},sob(){SFX.tone('sine',720,430,.24,.09);SFX.tone('sine',660,400,.26,.07,.27)},yay(){SFX.tone('sine',660,990,.1,.14);SFX.tone('sine',880,1320,.12,.12,.1)}};

/* ---------- face, drawn fresh every frame ---------- */
function drawMochiFace(g,F,t,O){O=O||{};const SKIN=O.skin||'#ff9dc1',BL=O.blush||'255,60,120',INNER=O.inner||'#4a1030',TONG=O.tongue||'#ff6f97';const W=512;g.clearRect(0,0,W,W);const cx=x=>(x*.5+.5)*W,cy=y=>(1-(y*.5+.5))*W;
  const eyeY=cy(O.eyeY||.18),ex=[cx(-(O.eyeX||.29)),cx(O.eyeX||.29)],E=W*.066,lx=F.look[0]*E*.55,ly=F.look[1]*E*.4;
  // blush
  g.save();for(const x of[cx(-.5),cx(.5)]){const r=g.createRadialGradient(x,cy(.0),0,x,cy(.0),W*.08);r.addColorStop(0,`rgba(${BL},${.6*F.blush})`);r.addColorStop(1,`rgba(${BL},0)`);g.fillStyle=r;g.beginPath();g.ellipse(x,cy(.0),W*.085,W*.055,0,0,TAU);g.fill()}g.restore();
  g.lineCap='round';g.lineJoin='round';const ink=O.ink||'#3a1630';
  const drawEye=(x,mode,i)=>{const X=x+lx,Y=eyeY+ly;
    if(mode==='happy'){g.strokeStyle=ink;g.lineWidth=E*.42;g.beginPath();g.arc(X,Y+E*.35,E*.72,Math.PI*1.12,Math.PI*1.88);g.stroke();return}
    if(mode==='closed'){g.strokeStyle=ink;g.lineWidth=E*.36;g.beginPath();g.arc(X,Y-E*.2,E*.68,Math.PI*.15,Math.PI*.85);g.stroke();return}
    if(mode==='squeeze'){g.strokeStyle=ink;g.lineWidth=E*.36;const s=i?-1:1;g.beginPath();g.moveTo(X-E*.6*s,Y-E*.5);g.lineTo(X+E*.45*s,Y);g.lineTo(X-E*.6*s,Y+E*.5);g.stroke();return}
    if(mode==='heart'){g.fillStyle='#ff3d7f';g.save();g.translate(X,Y);g.scale(E*1.15*(1+.08*Math.sin(t*10)),E*1.15*(1+.08*Math.sin(t*10)));g.beginPath();g.moveTo(0,.45);g.bezierCurveTo(-1.15,-.3,-.5,-1.1,0,-.42);g.bezierCurveTo(.5,-1.1,1.15,-.3,0,.45);g.fill();g.fillStyle='#fff';g.beginPath();g.arc(-.35,-.42,.16,0,TAU);g.fill();g.restore();return}
    if(mode==='star'){g.fillStyle='#ffd34d';g.save();g.translate(X,Y);g.rotate(t*.8);g.beginPath();for(let k=0;k<10;k++){const r=k%2?E*.45:E*1.05,a=k*Math.PI/5-Math.PI/2;k?g.lineTo(Math.cos(a)*r,Math.sin(a)*r):g.moveTo(Math.cos(a)*r,Math.sin(a)*r)}g.fill();g.restore();return}
    if(mode==='spiral'){g.strokeStyle=ink;g.lineWidth=E*.18;g.beginPath();for(let a=0;a<TAU*2.6;a+=.2){const r=a*E*.065,aa=a+t*9*(i?-1:1);g.lineTo(X+Math.cos(aa)*r,Y+Math.sin(aa)*r)}g.stroke();return}
    if(mode==='cry'){g.strokeStyle=ink;g.lineWidth=E*.36;const s=i?-1:1;g.beginPath();g.moveTo(X-E*.6*s,Y-E*.45);g.lineTo(X+E*.45*s,Y);g.lineTo(X-E*.6*s,Y+E*.45);g.stroke();
      g.fillStyle='rgba(110,200,255,.9)';const L2=E*(1.5+.35*Math.sin(t*10+i*2));g.beginPath();g.roundRect?g.roundRect(X-E*.2,Y+E*.3,E*.4,L2,E*.2):g.rect(X-E*.2,Y+E*.3,E*.4,L2);g.fill();g.fillStyle='rgba(255,255,255,.7)';g.fillRect(X-E*.08,Y+E*.45,E*.07,L2*.6);return}
    if(mode==='note'){g.fillStyle=ink;g.font=`900 ${E*2.2}px system-ui,sans-serif`;g.textAlign='center';g.textBaseline='middle';g.fillText(i?'♪':'♫',X,Y);return}
    const big=mode==='wow'?1.22:1,open=clamp(F.eyeOpen,0,1);if(open<.12){g.strokeStyle=ink;g.lineWidth=E*.34;g.beginPath();g.moveTo(X-E*.62,Y);g.quadraticCurveTo(X,Y+E*.25,X+E*.62,Y);g.stroke();return}
    const rx=E*.74*big,ry=E*.98*big*open;let gr=g.createLinearGradient(X,Y-ry,X,Y+ry);gr.addColorStop(0,'#1a0a1a');gr.addColorStop(.62,'#2a1030');gr.addColorStop(1,'#7a2f6a');g.fillStyle=gr;g.beginPath();g.ellipse(X,Y,rx,ry,0,0,TAU);g.fill();
    g.save();g.beginPath();g.ellipse(X,Y,rx,ry,0,0,TAU);g.clip();g.fillStyle='#fff';const hx=-lx*.35,hy=-ly*.3;g.beginPath();g.ellipse(X-rx*.32+hx,Y-ry*.38+hy,rx*.34,rx*.3*Math.min(1,open*1.6),-.4,0,TAU);g.fill();g.beginPath();g.arc(X+rx*.3+hx,Y+ry*.32+hy,rx*.14,0,TAU);g.fill();
    if(mode==='wow'||F.sparkle>0){g.globalAlpha=Math.max(F.sparkle,mode==='wow'?1:0);g.beginPath();g.arc(X+rx*.42,Y-ry*.1,rx*.09,0,TAU);g.arc(X-rx*.05,Y+ry*.5,rx*.07,0,TAU);g.fill();g.globalAlpha=1}g.restore();
    if(mode==='side'){g.fillStyle=SKIN;g.beginPath();g.rect(X-rx*1.1,Y-ry*1.1,rx*2.2,ry*.95);g.fill();g.strokeStyle=ink;g.lineWidth=E*.16;g.beginPath();g.moveTo(X-rx*1.05,Y-ry*.15);g.lineTo(X+rx*1.05,Y-ry*.15);g.stroke()}};
  const em=F.eye;drawEye(ex[0],em==='wink'?'normal':em==='wink2'?'happy':em,0);drawEye(ex[1],em==='wink'?'happy':em==='wink2'?'normal':em,1);
  if(F.browOn||Math.abs(F.brow)>.3){g.strokeStyle=ink;g.lineWidth=E*.2;for(const[i,x]of ex.entries()){const s=i?1:-1,a=F.brow*.5*s,yb=eyeY-E*1.45-(F.browUp||0)*E*.4;g.beginPath();g.moveTo(x-E*.5*Math.cos(a),yb+E*.5*Math.sin(a)*-1*s*s);g.lineTo(x+E*.5*Math.cos(a),yb-E*.5*Math.sin(a));g.stroke()}}
  // mouth
  const mx=cx(0)+F.look[0]*E*.25,my=cy(O.mouthY||-.1);const m=F.mouth,o=clamp(F.mouthOpen,0,1);g.strokeStyle=ink;g.lineWidth=E*.24;
  if((m==='cat'||m==='smile')&&o<.12){if(m==='cat'){g.beginPath();g.arc(mx-E*.32,my,E*.32,0,Math.PI);g.arc(mx+E*.32,my,E*.32,0,Math.PI);g.stroke()}else{g.beginPath();g.arc(mx,my-E*.25,E*.55,Math.PI*.15,Math.PI*.85);g.stroke()}}
  else if(m==='frown'){g.beginPath();g.arc(mx,my+E*.5,E*.45,Math.PI*1.2,Math.PI*1.8);g.stroke()}
  else if(m==='pout'){g.beginPath();g.moveTo(mx-E*.35,my+E*.05);g.quadraticCurveTo(mx-E*.1,my-E*.2,mx,my+E*.05);g.quadraticCurveTo(mx+E*.1,my-E*.2,mx+E*.35,my+E*.05);g.stroke()}
  else if(m==='o'){g.fillStyle=INNER;g.beginPath();g.ellipse(mx,my+E*.1,E*.22+o*E*.1,E*.25+o*E*.15,0,0,TAU);g.fill()}
  else{const ow=E*(.45+o*.25),oh=E*(.12+o*.75);g.fillStyle=INNER;g.beginPath();g.ellipse(mx,my+oh*.4,ow,oh,0,0,TAU);g.fill();g.save();g.beginPath();g.ellipse(mx,my+oh*.4,ow,oh,0,0,TAU);g.clip();g.fillStyle=TONG;g.beginPath();g.ellipse(mx,my+oh*1.15,ow*.75,oh*.6,0,0,TAU);g.fill();g.restore()}
  if(F.sweat>0){g.globalAlpha=F.sweat;g.fillStyle='#8fd8ff';const sx=cx(.62),sy=cy(.38);g.beginPath();g.moveTo(sx,sy-E*.7);g.quadraticCurveTo(sx+E*.45,sy,sx,sy+E*.35);g.quadraticCurveTo(sx-E*.45,sy,sx,sy-E*.7);g.fill();g.globalAlpha=1}}

/* ---------- model (shared by the live scene and the portrait thumbnail) ---------- */
function buildMochi(){const T=T3,k=kit(),root=new T.Group(),fc=document.createElement('canvas');fc.width=fc.height=512;const ft=new T.CanvasTexture(fc);ft.encoding=T.sRGBEncoding;ft.anisotropy=4;
  const U={uT:{value:0},uSq:{value:0},uMelt:{value:0},uPress:{value:0},uBreath:{value:0},uPokeT:{value:9},uPull:{value:new T.Vector3()},uShear:{value:new T.Vector3()},uPoke:{value:new T.Vector4(0,0,1,0)},uS:{value:new T.Vector4(...MOCHI_S)},
    uFace:{value:ft},uTop:{value:new T.Color('#ffa9c9')},uBot:{value:new T.Color('#ff6fa3')},uRim:{value:new T.Color('#ffd9e8')},uKey:{value:new T.Vector3(-.45,.75,.7)},uTint:{value:new T.Color('#ffffff')},
    uFill:{value:new T.Color('#5dff9e')},uStarC:{value:new T.Color('#ffe08a')},uFillLv:{value:.5},uFillA:{value:0},uStar:{value:0},uGlow:{value:0}};
  const mat=new T.ShaderMaterial({uniforms:U,vertexShader:MOCHI_V,fragmentShader:MOCHI_F});const body=new T.Mesh(new T.SphereGeometry(1,96,64),mat);root.add(body);
  const jm=new T.MeshPhysicalMaterial({color:'#ff9cc0',roughness:.22,clearcoat:1,clearcoatRoughness:.08,sheen:.6,sheenColor:new T.Color('#ffe6f0')}),inner=new T.MeshStandardMaterial({color:'#ff8fb5',roughness:.5});
  const lg=new T.LatheGeometry([[0,0],[.17,.0],[.16,.08],[.11,.19],[.05,.27],[.0,.29]].map(([x,y])=>new T.Vector2(x,y)),28);
  const ears=[-1,1].map(s=>{const g=new T.Group();const e=new T.Mesh(lg,jm);e.scale.set(1,1,.62);g.add(e);const ie=new T.Mesh(lg,inner);ie.scale.set(.55,.62,.3);ie.position.set(0,.03,.045);g.add(ie);root.add(g);return g});
  const feet=[-1,1].map(s=>{const f=new T.Mesh(k.SPH,jm);f.scale.set(.17,.09,.15);root.add(f);return f});
  const paws=[-1,1].map(s=>{const p=new T.Mesh(k.SPH,jm);p.scale.set(.15,.13,.13);p.visible=false;root.add(p);return p});
  return{root,body,mat,U,ft,fc,fctx:fc.getContext('2d'),ears,feet,paws}}
function makeJar(){const T=T3,g=new T.Group(),R=.24,H=.42;
  const glass=new T.Mesh(new T.CylinderGeometry(R,R,H,40,1,true),new T.MeshPhysicalMaterial({color:'#c8fff0',roughness:.05,clearcoat:1,transparent:true,opacity:.16,side:T.DoubleSide,depthWrite:false}));glass.position.y=H/2+.05;
  const liqM=new T.MeshBasicMaterial({color:'#42ff74',transparent:true,opacity:.92,toneMapped:false});const liq=new T.Mesh(new T.CylinderGeometry(R*.93,R*.93,1,40),liqM);
  const surf=new T.Mesh(new T.CircleGeometry(R*.93,40),new T.MeshBasicMaterial({color:'#b9ffc9',transparent:true,opacity:.85,toneMapped:false}));surf.rotation.x=-Math.PI/2;
  const metal=new T.MeshPhysicalMaterial({color:'#d3dbe6',metalness:1,roughness:.22,clearcoat:.5});const capB=new T.Mesh(new T.CylinderGeometry(R*1.05,R*1.05,.05,40),metal);capB.position.y=.025;
  const capT=new T.Mesh(new T.CylinderGeometry(R*1.05,R*1.05,.045,40),metal);capT.position.y=H+.07;const nub=new T.Mesh(new T.CylinderGeometry(.06,.07,.04,24),metal);nub.position.y=H+.11;
  const bs=new T.Shape();bs.moveTo(.025,.11);bs.lineTo(-.065,-.01);bs.lineTo(-.005,-.01);bs.lineTo(-.03,-.11);bs.lineTo(.065,.018);bs.lineTo(.006,.018);bs.closePath();
  const bolt=new T.Mesh(new T.ShapeGeometry(bs),new T.MeshBasicMaterial({color:'#f4ffb0',transparent:true,opacity:.95,toneMapped:false}));bolt.position.set(-.05,.27,R+.004);
  const bars=[];for(let i=0;i<5;i++){const b=new T.Mesh(new T.PlaneGeometry(.075,.028),new T.MeshBasicMaterial({color:i<2?'#ffe25a':'#c6ff5a',transparent:true}));b.position.set(.12,.11+i*.055,R*.86);b.rotation.y=.52;bars.push(b)}
  const bub=new T.InstancedMesh(new T.SphereGeometry(1,10,8),new T.MeshBasicMaterial({color:'#efffe0',transparent:true,opacity:.8}),12);const bd=[...Array(12)].map(()=>({a:rnd(TAU),r:rnd(.03,.17),y:rnd(),s:rnd(.008,.017),v:rnd(.12,.3)}));
  const sc=document.createElement('canvas');sc.width=128;sc.height=4;const sx=sc.getContext('2d'),gr=sx.createLinearGradient(0,0,128,0);['#ff9ecf','#ffd36b','#9dffb0','#8fd8ff','#c9a6ff','#ff9ecf'].forEach((c,i)=>gr.addColorStop(i/5,c));sx.fillStyle=gr;sx.fillRect(0,0,128,4);
  const stex=new T.CanvasTexture(sc);stex.encoding=T.sRGBEncoding;stex.wrapS=T.RepeatWrapping;stex.repeat.set(3,1);const strawM=new T.MeshPhysicalMaterial({map:stex,roughness:.3,clearcoat:.8});let straw=null,lastEnd=null;
  g.add(glass,liq,surf,capB,capT,nub,bolt,bub,...bars);const dm=new T.Object3D();
  return{g,s:0,H,setLevel(lv,t){const L=clamp(lv,.05,1),hh=H*.92*L;liq.scale.y=hh;liq.position.y=.05+hh/2;surf.position.y=.05+hh+.002;bars.forEach((b,i)=>{b.material.opacity=i<Math.ceil(L*5)?.95:.14});
      bd.forEach((d,i)=>{d.y+=d.v*.016;if(d.y>1)d.y=0;dm.position.set(Math.cos(d.a+t*.5)*d.r,.05+hh*d.y,Math.sin(d.a+t*.5)*d.r);dm.scale.setScalar(d.s);dm.updateMatrix();bub.setMatrixAt(i,dm.matrix)});bub.instanceMatrix.needsUpdate=true},
    straw(end){if(lastEnd&&lastEnd.distanceTo(end)<.006)return;lastEnd=end.clone();if(straw){g.remove(straw);straw.geometry.dispose()}
      const curve=new T.CatmullRomCurve3([new T.Vector3(0,H*.45,0),new T.Vector3(0,H+.12,0),new T.Vector3(end.x*.2,H+.22,end.z*.2),new T.Vector3(end.x*.65,Math.max(end.y,H+.2)+.05,end.z*.65),end]);straw=new T.Mesh(new T.TubeGeometry(curve,48,.019,10),strawM);g.add(straw)}}}
function placeMochiParts(M,u,t,earTw,earDroop){for(const[i,e]of M.ears.entries()){const s=i?1:-1,p=mochiDeform([s*.55,.8,.05],u);e.position.set(p[0],p[1]-.05,p[2]);e.rotation.set(0,0,-s*(.38+earDroop*.7)+s*earTw[i]*.5+Math.sin(t*1.7+i)*.03)}
  for(const[i,f]of M.feet.entries()){const s=i?1:-1,p=mochiDeform([s*.55,-.95,.45],u);f.position.set(p[0],Math.max(.05,p[1]*.4),p[2]+.04)}}

class Buddy extends Scene{
  constructor(pv,id){super(pv);this.isBuddy=true;this.id=id;this.snd=SNDS[id];this.hov=0;this.force=null;this.simCharge=false;
    this.u={sq:0,melt:0,press:0,breath:0,shear:[0,0,0],pull:[0,0,0],S:this.S||MOCHI_S};this.sqv=0;this.shv=[0,0,0];this.pullv=[0,0,0];this.pullT=null;this.pokeT=9;this.pokeAmp=0;this.pokeDir=[0,0,1];
    this.x=0;this.y=0;this.vx=0;this.vy=0;this.rot=0;this.vr=0;this.toss=false;this.yaw=0;this.dz=0;this.ground=true;
    this.F={eye:'normal',eyeOpen:1,look:[0,0],brow:0,browUp:0,browOn:false,mouth:'cat',mouthOpen:0,blush:.6,sparkle:0,sweat:0};this.look=[0,0];this.sacc=[0,0];this.saccT=1;this.blinkT=2;this.blink=0;
    this.act=null;this.actT=rnd(3,5);this.idleT=0;this.pokes=[];this.hold=null;this.affection=store.get(id+'.aff',.4);this.star=0;this.fall=null;this.fallT=rnd(25,45);this.shoot=null;this.shootT=rnd(8,16);
    this.bubbles=[];this.tears=[];this.jState='mochi';this.jx=0;this.jy=0;this.jvy=0;this.traces=null;this.zz=[];this.zT=0;this.fog=[];this.snot=0;this.lastCharging=null;this.wake=0;this.beatN=0;this.earTw=[0,0];this.mood='day';this.gulpT=0;this.gulp=0;this.notes=[];this.say=null;
    this.scale=1;this.sy=1;this.faceT=0;this.stars=[];for(let i=0;i<200;i++){const r=Math.random();this.stars.push({x:rnd(),y:Math.pow(rnd(),1.3)*.8,s:r<.9?rnd(.5,1.1):rnd(1.2,1.9),a:rnd(.12,.5),ph:rnd(TAU),sp:rnd(.4,1.4),c:r<.7?'#ffffff':r<.85?'#d6deff':'#ffe0ee'})}
    const last=store.get(id+'.seen',0);this.away=last?(Date.now()-last)/60000:0;store.set(id+'.seen',Date.now());this.gl=null}
  /* ----- GPU layer ----- */
  attach(el){if(!HAS3D||this.gl)return;const T=T3;kit();const G=sharedGL();if(G.cv.parentNode!==el)el.insertBefore(G.cv,el.children[1]||null);G.cv.style.display='block';
    const sc=new T.Scene(),cam=new T.PerspectiveCamera(22,1,1,1e5);sc.environment=G.env;const key=new T.DirectionalLight('#fff',1.25);key.position.set(-.5,.9,1.3);const rim=new T.DirectionalLight(this.rimCol||'#ffd6e8',.9);rim.position.set(.8,.5,-1);sc.add(key,rim,new T.HemisphereLight(this.hemiCol||'#ffe6f0','#1a0d18',.35));
    this.M=this.build();sc.add(this.M.root);this.gl={r:G.r,cv:G.cv,sc,cam,key,pr:Math.min(G.maxPr,WP?1:1.25),maxPr:Math.min(G.maxPr,WP?1.25:1.5),juice:null};this.layoutGL()}
  detach(){if(!this.gl)return;const g=this.gl;g.r.clear();g.cv.style.display='none';GLS.pr=g.pr;g.sc.clear();this.gl=null}
  layoutGL(){const g=this.gl;if(!g||this.w<2)return;g.r.setPixelRatio(g.pr);g.r.setSize(this.w,this.h,false);GLS.pr=g.pr;g.cv.style.width=this.w+'px';g.cv.style.height=this.h+'px';const d=this.h/2/Math.tan(11*Math.PI/180);g.cam.aspect=this.w/this.h;g.cam.position.set(0,0,d);g.cam.near=d*.2;g.cam.far=d*3;g.cam.updateProjectionMatrix()}
  resize(w,h){const first=this.w===1;super.resize(w,h);this.U=Math.min(w*.44,h*.5/1.75);this.floor=h*.73;if(first){this.x=w/2;this.y=this.floor}this.layoutGL()}
  // screen position of a point on Mochi's (deformed) body
  scr(p){const q=mochiDeform(p,this.u),s=this.U*this.scale;const c=Math.cos(this.rot),sn=Math.sin(this.rot);const X=q[0]*s*Math.cos(this.yaw),Y=q[1]*s*this.sy;return[this.x+X*c+Y*sn,this.y-this.hov-(Y*c-X*sn)]}
  /* ----- input ----- */
  onBody(x,y){const c=this.scr([0,0,1]),s=this.U;return Math.abs(x-this.x)<s*1.05&&y<this.y-this.hov+s*.1&&y>this.y-this.hov-s*(this.S||MOCHI_S)[0]*1.9}
  down(p){p.sx=p.x;p.sy=p.y;p.t0=this.t;this.idleT=0;const jc=this.itemOn()&&this.jState==='mochi'&&this.jarScreen();if(jc&&Math.hypot(p.x-jc[0],p.y-jc[1])<this.U*.45){p.juice=true;this.jState='user';this.jGrace=this.t+1.4;this.jx=p.x;this.jy=p.y;this.jvy=0;this.act=null;this.sqv+=1.5;haptic(14);if(!this.preview&&!WP)this.snd.sob();return}p.body=this.onBody(p.x,p.y)&&!this.toss;if(p.body){this.hold={p,t:0,gx:p.x,gy:p.y};this.act=null;this.wakeUp()}else{this.lookAt=[p.x,p.y];if(p.y<this.h*.45)this.spawnFall(p.x,p.y)}}
  move(p){if(p.juice){if(this.jState==='user'){this.jTilt=clamp(-(p.vx||0)*.0004,-.5,.5);this.jx=p.x;this.jy=p.y}return}if(!p.body){const v=(p.vx||0)/this.w;this.scroll(v*.6)}}
  up(p){if(p.juice){if(this.jState==='user'){this.jState='drop';this.jvy=clamp(p.vy||0,-1500,1500)*.3}return}const quick=this.t-p.t0<.25&&Math.hypot(p.x-p.sx,p.y-p.sy)<14;const H=this.hold;this.hold=null;
    if(p.body){if(quick)this.poke(p.x,p.y);else if(H&&H.t>.45&&Math.hypot(p.x-p.sx,p.y-p.sy)<30)this.endPet();else{const v=Math.hypot(p.vx||0,p.vy||0);
        if(v>3200){this.toss=true;this.ground=false;this.vx=clamp(p.vx,-3500,3500)*.7;this.vy=clamp(p.vy,-3500,3500)*.7-400;this.vr=this.vx*.002;this.u.pull=[0,0,0]}
        else{this.pullv=[-this.u.pull[0]*4,-this.u.pull[1]*4,0];this.F.eye='normal';if(!this.preview&&!WP)this.snd.boing();haptic(8)}}}
    else if(!WP&&Math.abs(p.vx||0)>2600&&Math.abs(p.x-p.sx)>80)this.swipe(Math.sign(p.sx-p.x),Math.abs(p.vx)/this.w)}
  poke(x,y){const now=this.t;this.pokes=this.pokes.filter(t=>now-t<2.6);this.pokes.push(now);const n=this.pokes.length;
    const dx=(x-this.x)/(this.U*1.1),dy=(this.y-this.U*.75-y)/(this.U*.9);this.pokeDir=normalize3([dx,dy,Math.sqrt(Math.max(.05,1-dx*dx-dy*dy))]);this.pokeT=0;this.pokeAmp=1;this.sqv+=1.2;haptic(6);
    if(this.act&&this.act.type==='grumpy'){this.act.t=Math.max(0,this.act.t-1);if(!this.preview&&!WP)this.snd.hmph();return}
    if(this.mood==='sleep'){this.wakeUp();return}
    this.affection=clamp(this.affection-(n>4?.04:0)+(n<=2?.01:0),0,1);
    if(n>=6){this.startAct('grumpy');if(!this.preview&&!WP)this.snd.hmph()}else if(n>=2){this.startAct('giggle');if(!this.preview&&!WP)this.snd.giggle()}else{this.startAct('curious');if(!this.preview&&!WP)this.snd.squish()}}
  endPet(){this.affection=clamp(this.affection+.06,0,1);store.set(this.id+'.aff',this.affection);if(this.act&&this.act.type==='grumpy')this.startAct('forgive');else this.startAct(this.affection>.75?'love':'happy')}
  scroll(v){if(this.toss)return;this.shv[0]-=v*.9;this.saccT=0;this.sacc=[clamp(v*.8,-1,1),0];if(Math.abs(v)>5.5)this.swipe(Math.sign(v),Math.abs(v))}
  swipe(dir,speed=8){if(this.toss||speed<5.5)return;this.toss=true;this.ground=false;this.vx=-dir*this.w*rnd(1.5,2);this.vy=-this.h*rnd(.5,.8);this.vr=-dir*rnd(6,9);this.act=null;if(!this.preview&&!WP)SFX.whoosh()}
  wakeUp(){if(this.mood==='sleep'||this.wake>0){this.wake=10;if(this.mood==='sleep')this.startAct('wake')}}
  spawnFall(x,y){if(this.fall||this.mood==='sleep')return;this.fall={x0:x,y0:y,t:0,dur:1.6};this.act=null;this.startAct('catch')}
  /* ----- behaviour ----- */
  startAct(type){const D=Object.assign({curious:1.4,giggle:1.4,grumpy:6,forgive:1.6,happy:1.6,love:2.4,lookAround:3.2,earTwitch:.6,bubble:3.2,sneeze:2.4,hide:3.4,press:3.2,stargaze:2.6,catch:2.4,wake:3,stretch:2.6,yawn:2.2,nod:3,sigh:2.4,hop:1.1,greet:2.4,smug:4},this.DUR||{});this.act={type,t:0,dur:D[type]||2,side:Math.random()<.5?-1:1};
    if(type==='hop'&&this.ground){this.vy=-this.h*.32;this.ground=false}if(type==='greet'&&this.ground){this.vy=-this.h*.38;this.ground=false}}
  pickAct(){const m=this.mood;let pool=(this.POOLS||{day:['lookAround','earTwitch','bubble','hop','stargaze','lookAround','press','sneeze','hide'],evening:['lookAround','bubble','stargaze','earTwitch','yawn'],wakeup:['stretch','yawn','lookAround','nod'],late:['yawn','nod','stargaze'],low:['sigh']})[m];if(!pool)return;
    if(this.idleT>24&&(m==='day'||m==='evening')){this.startAct('press');this.idleT=6;return}this.startAct(pool[(Math.random()*pool.length)|0])}
  onUnlock(){this.wakeUp();if(this.away>30){this.startAct('greet');this.say={g:'♥',life:1.6}}else this.startAct(dayPhase()==='morning'?'stretch':'hop');this.away=0}
  phaseMood(A){const ph=this.force||dayPhase(),charging=HSTATE.charging||this.simCharge,low=!charging&&HSTATE.battery<.2;
    if(this.lastCharging!==null&&charging!==this.lastCharging){if(charging){this.act=null;this.startAct('curious');this.wakeUp()}else this.F.eye='wow'}this.lastCharging=charging;this.charging=charging;
    if(this.dz>0)return'dizzy';if(A.active&&A.level>.08)return'dance';if(ph==='night'&&this.wake<=0)return'sleep';if(charging)return'charge';if(low)return'low';if(ph==='night'||ph==='morning')return'wakeup';return ph==='late'?'late':ph==='evening'?'evening':'day'}
  update(dt,A){this.t+=dt;this.A=A;const w=this.w,h=this.h,u=this.u,U=this.U;if(A.beat)this.beatN++;this.wake=Math.max(0,this.wake-dt);this.mood=this.phaseMood(A);const m=this.mood;
    let ac=this.act;if(ac){ac.t+=dt;if(ac.t>=ac.dur){if(ac.type==='catch'&&this.star>.5)this.startAct('smug');else this.act=null;ac=this.act}}
    const k=ac?ac.t/ac.dur:0,F=this.F;
    if(!this.hold&&!this.toss&&this.dz<=0&&['day','evening','wakeup','late','low'].includes(m)){this.idleT+=dt;if(!ac){this.actT-=dt;if(this.actT<0){this.pickAct();this.actT=rnd(3,6.5);ac=this.act}}}
    // shooting stars Mochi notices; occasionally a star falls to it
    if(this.shoot){this.shoot.t+=dt;if(this.shoot.t>1.1)this.shoot=null}else{this.shootT-=dt;if(this.shootT<0){this.shootT=rnd(10,22);this.shoot={x:rnd(.15,.85),y:rnd(.06,.3),dir:Math.random()<.5?1:-1,t:0};if(!ac&&m!=='sleep'&&m!=='dance')this.startAct('stargaze')}}
    if(!WP||true){this.fallT-=dt;if(this.fallT<0&&!this.fall&&['day','evening'].includes(m)&&!ac&&!this.hold){this.fallT=rnd(40,80);this.spawnFall(rnd(w*.15,w*.85),h*.05)}}
    if(this.fall){const f=this.fall;f.t+=dt;if(f.t>=f.dur){this.fall=null;this.star=1;this.sqv+=2.5;haptic(15);if(!this.preview&&!WP)this.snd.chomp()}}
    this.star=Math.max(0,this.star-dt/18);
    // ----- expression targets -----
    let eye='normal',open=1,mouth='cat',mo=0,brow=0,browUp=0,browOn=false,blush=.55+this.affection*.35,sparkle=0,sweat=0,lookT=[0,0],earD=0,lean=0,yawT=0;
    const P=this.ptr;if(P){const dx=P.x-this.x,dy=P.y-(this.y-U*.8),d=Math.hypot(dx,dy)+1;lookT=[clamp(dx/d*Math.min(1,d/U),-1,1),clamp(dy/d*Math.min(1,d/U),-1,1)]}
    else if(this.lookAt){const dx=this.lookAt[0]-this.x,dy=this.lookAt[1]-(this.y-U*.8),d=Math.hypot(dx,dy)+1;lookT=[clamp(dx/d,-1,1)*.9,clamp(dy/d,-1,1)*.9];this.lookAtT=(this.lookAtT||0)+dt;if(this.lookAtT>1.2){this.lookAt=null;this.lookAtT=0}}
    else{this.saccT-=dt;if(this.saccT<0){this.saccT=rnd(1.2,3.2);this.sacc=Math.random()<.65?[0,0]:[rnd(-.45,.45),rnd(-.25,.2)]}lookT=this.sacc}
    if(this.shoot&&m!=='sleep'){const sx=(this.shoot.x+this.shoot.dir*this.shoot.t*.4)*w,sy=(this.shoot.y+this.shoot.t*.1)*h,dx=sx-this.x,dy=sy-(this.y-U*.8),d=Math.hypot(dx,dy);lookT=[dx/d,dy/d];sparkle=1;eye='wow'}
    if(this.fall){const p=this.fallPos();const dx=p[0]-this.x,dy=p[1]-(this.y-U*.8),d=Math.hypot(dx,dy)+1;lookT=[dx/d,dy/d];eye='wow';mouth='open';mo=.3+this.fall.t/this.fall.dur*.7}
    switch(m){case'sleep':eye='closed';open=0;mouth='cat';lookT=[0,.2];blush=.7;break;case'wakeup':open=.62+.1*Math.sin(this.t*.8);break;case'late':open=.75;break;case'low':open=.6;brow=-.9;browOn=true;mouth='frown';sweat=.8;earD=.8;break;
      case'charge':eye=this.gulp>.5?'happy':'normal';mouth='o';mo=.1;blush=1.1;sparkle=.6;break;case'dance':eye=this.beatN%8<2?'note':'happy';mouth='open';mo=clamp(A.mid*1.1,.15,1);blush=.85;break;case'dizzy':eye='spiral';mouth='open';mo=.3;break}
    if(this.moodHook){const E={eye,open,mouth,mo,brow,browUp,browOn,blush,sparkle,sweat,lookT,yawT,lean,earD};this.moodHook(m,E,dt,A);({eye,open,mouth,mo,brow,browUp,browOn,blush,sparkle,sweat,lookT,yawT,lean,earD}=E)}
    if(ac){const T2=ac.type;
      if(T2==='curious'){eye='wow';mouth='o';mo=.2;browUp=.6;browOn=true;yawT=ac.side*.15}
      if(T2==='giggle'){eye='happy';mouth='open';mo=.5+.3*Math.sin(ac.t*30);blush=1;this.shv[0]+=Math.sin(ac.t*40)*dt*1.2}
      if(T2==='grumpy'){eye='side';mouth='pout';brow=.9;browOn=true;blush=1;yawT=-.55*ac.side;lookT=[.9*ac.side,0];u.press=0;earD=.35}
      if(T2==='forgive'){eye='happy';mouth='smile';blush=1}
      if(T2==='happy'){eye='happy';mouth='smile';blush=1}
      if(T2==='love'){eye='heart';mouth='smile';blush=1.2}
      if(T2==='lookAround'){lookT=[k<.33?-.95:k<.66?.95:0,k<.66?-.1:0];yawT=lookT[0]*.18}
      if(T2==='earTwitch'){this.earTw[ac.side>0?1:0]=Math.sin(ac.t*30)*(1-k)}
      if(T2==='bubble'){mouth='o';mo=.15;eye=k>.85?'wow':'normal';open=k>.85?1:.8;if(k>.85&&k-dt/ac.dur<=.85){this.bubblePop()}}
      if(T2==='sneeze'){if(k<.45){eye='squeeze';mouth='o';mo=k;this.sqv-=dt*6}else if(k<.55){eye='closed';mouth='open';mo=1;if(!ac.done){ac.done=1;this.sqv+=6;this.shv[2]+=1.2;haptic(18);if(!this.preview&&!WP)this.snd.achoo()}}else{eye='happy';mouth='smile';blush=1.3}}
      if(T2==='hide'){const mk=k<.3?k/.3:k<.75?1:1-(k-.75)/.25;u.melt=Math.max(u.melt,mk*.9);if(k<.75){open=.7;lookT=[0,-.3]}else{eye='wow';mouth='open';mo=.8;if(!ac.done){ac.done=1;this.sqv-=4;haptic(14);if(!this.preview&&!WP)this.snd.boo();this.say={g:'!',life:1}}}}
      if(T2==='press'){const pk=k<.2?ease.inOut(k/.2):k>.8?1-ease.inOut((k-.8)/.2):1;u.press=pk;lean=pk*.22;eye=k<.4?'wow':'happy';mouth=k<.4?'o':'smile';mo=.3;if(k>.35&&!ac.fog){ac.fog=1;const c=this.scr([0,-.05,1]);this.fog.push({x:c[0],y:c[1],life:3.2})}}
      if(T2==='stargaze'){lookT=this.shoot?lookT:[.2,-.95];sparkle=1;eye='wow';mouth='o';mo=.15}
      if(T2==='catch'&&!this.fall){eye='happy';mouth='smile'}
      if(T2==='smug'){eye='wink';mouth='smile';blush=1}
      if(T2==='wake'){eye=k<.2?'wow':'normal';open=k<.2?1:.55;mouth=k>.25&&k<.7?'open':'cat';mo=k>.25&&k<.7?ease.bell((k-.25)/.45):0;lookT=[0,0]}
      if(T2==='stretch'){const sk=ease.bell(k);u.pull=[0,sk*.5,0];eye='closed';open=0;mouth='open';mo=sk*.9}
      if(T2==='yawn'){const yk=ease.bell(k);eye=yk>.3?'closed':'normal';open=1-yk;mouth='open';mo=yk;this.sqv-=yk*dt*3}
      if(T2==='nod'){open=k<.7?1-k/.7:1;if(k>.7&&!ac.done){ac.done=1;this.sqv-=3;eye='wow'}lean=k<.7?k*.25:0}
      if(T2==='sigh'){eye='normal';open=.5;mouth='frown';u.melt=Math.max(u.melt,ease.bell(k)*.3)}
      if(T2==='hop'){eye='happy';mouth='smile'}
      if(T2==='greet'){eye='heart';mouth='open';mo=.6;blush=1.2}}
    if(ac&&this.actHook){const E={eye,open,mouth,mo,brow,browUp,browOn,blush,sparkle,sweat,lookT,yawT,lean,earD};this.actHook(ac.type,k,ac,E,dt,A);({eye,open,mouth,mo,brow,browUp,browOn,blush,sparkle,sweat,lookT,yawT,lean,earD}=E)}
    // holding: pull like dough, or petting
    if(this.hold){const H=this.hold,p=H.p;H.t+=dt;const dx=(p.x-H.gx)/U,dy=-(p.y-H.gy)/U,dd=Math.hypot(dx,dy);
      if(dd>.12){const L=Math.min(dd,.75)/dd;u.pull=[dx*L*.9,dy*L*.9,0];eye=dd>.5?'squeeze':'wow';mouth='open';mo=clamp(dd,0,1);blush=1}
      else if(H.t>.45){eye=this.affection>.75?'heart':'happy';mouth='cat';blush=1.3;u.melt=Math.max(u.melt,.18);this.shv[0]+=Math.sin(this.t*26)*dt*.25;if(!H.purr&&!this.preview&&!WP){H.purr=1;this.snd.purr()}this.affection=clamp(this.affection+dt*.02,0,1);
        H.ht=(H.ht||0)+dt;if(H.ht>.8){H.ht=0;this.notes.push({x:this.x+rnd(-U*.3,U*.3),y:this.y-U*1.6,life:1.5,g:'♥'})}}}
    else if(!(ac&&ac.type==='stretch')){for(let i=0;i<3;i++){this.pullv[i]+=(-u.pull[i]*160-this.pullv[i]*9)*dt;u.pull[i]+=this.pullv[i]*dt}}
    // juice taken away: cry, chase, jump to snatch it back
    const chasing=this.itemOn()&&(this.jState==='user'||this.jState==='drop');
    if(chasing){eye='cry';mouth='open';mo=.7+.25*Math.sin(this.t*18);blush=1.3;brow=-.9;browOn=true;lookT=[clamp((this.jx-this.x)/U,-1,1),clamp((this.jy-(this.y-U))/U,-1,1)];yawT=clamp((this.jx-this.x)/U,-1,1)*.3;
      this.tearT=(this.tearT||0)-dt;if(this.tearT<0){this.tearT=.1;for(const sd of[-1,1]){const c=this.scr([sd*.3,.12,.95]);this.tears.push({x:c[0],y:c[1],vx:sd*rnd(70,150),vy:-rnd(80,180),life:1})}}
      this.sobT=(this.sobT||0)-dt;if(this.sobT<0){this.sobT=rnd(.8,1.2);this.sqv-=1.2;if(!this.preview&&!WP)this.snd.sob()}
      if(this.jState==='drop'){this.jx=lerp(this.jx,clamp(this.jx,U*.9,w-U*.9),.08);this.jvy+=h*2.4*dt;this.jy+=this.jvy*dt;const fy=this.floor-U*.1;if(this.jy>fy){this.jy=fy;this.jvy=Math.abs(this.jvy)>120?-this.jvy*.3:0}this.jTilt=lerp(this.jTilt||0,0,.1)}
      const gap=this.jx-this.x,reach=this.y-U*1.15;if(Math.abs(gap)<U*.6&&this.t>(this.jGrace||0)){if(this.jy>reach)this.grabBack();else if(this.ground&&this.jState==='user'){this.vy=-h*.62;this.ground=false;this.sqv-=2}}}
    for(let i=this.tears.length-1;i>=0;i--){const q=this.tears[i];q.vy+=900*dt;q.x+=q.vx*dt;q.y+=q.vy*dt;q.life-=dt;if(q.life<=0)this.tears.splice(i,1)}
    if(this.postHook){const E={eye,open,mouth,mo,brow,browUp,browOn,blush,sparkle,sweat,lookT,yawT,lean,earD};this.postHook(E,dt,A);({eye,open,mouth,mo,brow,browUp,browOn,blush,sparkle,sweat,lookT,yawT,lean,earD}=E)}
    // blink
    this.blinkT-=dt;if(this.blinkT<0){this.blinkT=Math.random()<.2?.25:rnd(2,4.5);this.blink=.14}if(this.blink>0){this.blink-=dt;if(eye==='normal'||eye==='wow')open=Math.min(open,Math.abs(this.blink-.07)/.07)}
    this.look[0]=lerp(this.look[0],lookT[0],.16);this.look[1]=lerp(this.look[1],lookT[1],.16);
    Object.assign(F,{eye,eyeOpen:lerp(F.eyeOpen,open,.35),mouth,mouthOpen:lerp(F.mouthOpen,mo,.3),brow:lerp(F.brow,brow,.2),browUp,browOn:browOn||Math.abs(F.brow)>.1,blush:lerp(F.blush,blush,.1),sparkle,sweat:lerp(F.sweat,sweat,.05),look:this.look});
    // ----- body physics -----
    if(!(ac&&ac.type==='hide')&&m!=='sleep'&&m!=='low')u.melt=lerp(u.melt,this.hold&&this.hold.t>.45?.18:0,.08);if(m==='sleep')u.melt=lerp(u.melt,this.sleepMelt!=null?this.sleepMelt:.42,.03);if(m==='low')u.melt=lerp(u.melt,.25,.03);
    if(!(ac&&ac.type==='press'))u.press=lerp(u.press,0,.15);
    if(m==='dance'&&A.beat){this.sqv+=2.2;if(this.beatN%2===0&&this.ground){this.vy=-h*.16;this.ground=false}}
    if(m==='charge'){this.gulpT-=dt;if(this.gulpT<0){this.gulpT=rnd(1,1.5);this.gulp=1;this.sqv-=1.2;if(!this.preview&&!WP&&Math.random()<.4)this.snd.gulp()}this.gulp=Math.max(0,this.gulp-dt*3)}
    this.sqv+=(-u.sq*95-this.sqv*7.5)*dt;u.sq=clamp(u.sq+this.sqv*dt,-.3,.4);
    const sway=m==='dance'?Math.sin((A.beatPhase||0)*TAU)*.12:m==='evening'?Math.sin(this.t*1.1)*.04:0;
    for(const i of[0,2]){const tgt=i===0?sway:0;this.shv[i]+=(-(u.shear[i]-tgt)*110-this.shv[i]*6.5)*dt;u.shear[i]+=this.shv[i]*dt}
    u.breath=Math.sin(this.t*(m==='sleep'?1.3:2.2));this.pokeT+=dt;
    // stays home in the centre; only a very fast throw sends it flying
    const hx=w/2;if(this.toss){this.vy+=h*2.6*dt;this.x+=this.vx*dt;this.y+=this.vy*dt;this.rot+=this.vr*dt;const r=U*.9;if(this.x<r){this.x=r;this.vx=Math.abs(this.vx)*.5;this.vr*=-.6;this.sqv+=2}if(this.x>w-r){this.x=w-r;this.vx=-Math.abs(this.vx)*.5;this.vr*=-.6;this.sqv+=2}if(this.y-U*1.6<0){this.y=U*1.6;this.vy=Math.abs(this.vy)*.4}
      if(this.y>=this.floor){this.y=this.floor;if(this.vy>h*.4){this.sqv+=3;this.vy=-this.vy*.35}else{this.vy=0;this.toss=false;this.ground=true;this.dz=2.4;haptic(16)}}this.vx*=Math.pow(.6,dt)}
    else{if(!this.ground){this.vy+=h*2.8*dt;this.y+=this.vy*dt;if(this.y>=this.floor){this.y=this.floor;this.sqv+=Math.min(3,this.vy/h*2.2);this.vy=0;this.ground=true}}
      if(this.itemOn()&&(this.jState==='user'||this.jState==='drop')){const tx=clamp(this.jx,U*.9,w-U*.9),gap=tx-this.x;this.x+=clamp(gap,-w*.42*dt,w*.42*dt);if(this.ground&&Math.abs(gap)>U*.12){this.hopT=(this.hopT||0)-dt;if(this.hopT<0){this.hopT=.26;this.vy=-h*.16;this.ground=false;this.sqv-=1.2}}}else this.x+=(hx-this.x)*Math.min(1,dt*3);this.rot=lerp(this.rot,0,Math.min(1,dt*6))}
    this.dz=Math.max(0,this.dz-dt);this.yaw=lerp(this.yaw,yawT,.08);this.lean=lerp(this.lean||0,lean,.15);
    for(let i=0;i<2;i++)this.earTw[i]*=Math.pow(.02,dt);this.earD=lerp(this.earD||0,earD,.08);
    if(this.say){this.say.life-=dt;if(this.say.life<=0)this.say=null}
    if(m==='sleep'){this.zT-=dt;if(this.zT<0){this.zT=1.4;const c=this.scr([.4,.7,.6]);this.zz.push({x:c[0],y:c[1],life:3.2,s:rnd(.7,1.1)})}this.snot=(this.snot+dt)%3.2}
    for(const L of[this.zz,this.notes,this.fog,this.bubbles])for(let i=L.length-1;i>=0;i--){const z=L[i];z.life-=dt;if(z.y!=null&&L!==this.fog&&L!==this.bubbles)z.y-=dt*U*.18;if(z.life<=0)L.splice(i,1)}
    this.sy=1;this.scale=1+this.lean;
    if(this.gl)this.pose(dt,A)}
  itemOn(){return this.charging}
  fallPos(){const f=this.fall,k=ease.inOut(clamp(f.t/f.dur,0,1)),m=this.scr([0,-.05,1]);return[lerp(f.x0,m[0],k)+Math.sin(k*Math.PI)*this.w*.08,lerp(f.y0,m[1],k)-Math.sin(k*Math.PI)*this.h*.06]}
  bubblePop(){const c=this.scr([0,-.12,1]);this.bubbles.push({x:c[0],y:c[1],life:.45,pop:1});this.sqv-=1.5;if(!this.preview&&!WP)this.snd.pop()}
  /* ----- pose ----- */
  pose(dt,A){const g=this.gl,M=this.M,U=this.U,u=this.u,w=this.w,h=this.h,m=this.mood;const ph=this.force||dayPhase();const L=PHASE_LIGHT[ph];
    g.key.color.set(L.key);g.key.intensity=L.ki*.95;g.r.toneMappingExposure=L.exp;M.U.uTint.value.set(ph==='night'?'#c9d0ff':ph==='evening'?'#ffe2cf':ph==='morning'?'#fff0e2':'#ffffff');
    const s=U*this.scale;M.root.position.set(this.x-w/2,h/2-this.y,0);M.root.scale.set(s,s,s);M.root.rotation.set(this.lean*.6,this.yaw,-this.rot+(this.dz>0?Math.sin(this.t*9)*.12:0));
    const Uf=M.U;Uf.uT.value=this.t;Uf.uSq.value=u.sq;Uf.uMelt.value=u.melt;Uf.uPress.value=u.press;Uf.uBreath.value=u.breath;Uf.uPull.value.set(...u.pull);Uf.uShear.value.set(...u.shear);
    Uf.uPoke.value.set(this.pokeDir[0],this.pokeDir[1],this.pokeDir[2],this.pokeT<1.2?this.pokeAmp:0);Uf.uPokeT.value=this.pokeT;
    if(Uf.uFillA){Uf.uFillA.value=lerp(Uf.uFillA.value,0,.05);Uf.uFillLv.value=HSTATE.charging?HSTATE.battery:.45+.4*((this.t*.02)%1);Uf.uStar.value=this.star*(.8+.2*Math.sin(this.t*5));Uf.uGlow.value=A.active?A.bass*.6:0}
    M.root.position.y+=this.hov;if(this.t-this.faceT>1/(this.faceHz||30)){this.faceT=this.t;this.drawFace(M.fctx,this.F,this.t);M.ft.needsUpdate=true}
    this.place(dt,A)}
  /* ----- Mochi only from here ----- */
  mochiPose(dt,A){const g=this.gl,M=this.M,u=this.u,w=this.w,h=this.h,Uf=M.U;placeMochiParts(M,u,this.t,this.earTw,this.earD);
    M.root.updateMatrixWorld(true);const T=T3;
    if(this.charging&&!g.jar){g.jar=makeJar();g.sc.add(g.jar.g);this.jState='mochi'}
    if(g.jar){const J=g.jar,G=J.g,sz=this.U*this.scale;J.s=lerp(J.s,this.charging?1:0,.1);G.visible=J.s>.02;const kk=ease.outBack(clamp(J.s,0,1));J.setLevel(Uf.uFillLv.value,this.t);
      if(this.jState==='mochi'){G.position.copy(M.root.localToWorld(new T.Vector3(-.16,.04-this.gulp*.012,.98)));G.quaternion.copy(M.root.quaternion);G.scale.setScalar(sz*kk)}
      else{G.position.set(this.jx-w/2,h/2-this.jy-J.H*.5*sz,sz*1.1);G.rotation.set(0,0,this.jTilt||0);G.scale.setScalar(sz*kk)}
      G.updateMatrixWorld(true);let end;if(this.jState==='mochi'){const mp=mochiDeform([0,-.1,1],u);end=G.worldToLocal(M.root.localToWorld(new T.Vector3(mp[0]+.01,mp[1]-.01,mp[2]+.02)))}else end=new T.Vector3(.14,J.H+.3,0);J.straw(end);
      const PW=M.paws;if(this.jState==='mochi'&&G.visible){PW[0].visible=PW[1].visible=true;PW[0].position.set(-.47,.28,.98);PW[1].position.set(.15,.32,1.04)}
      else if(this.charging){const dx=clamp((this.jx-this.x)/this.U,-1.5,1.5);PW.forEach((p,i)=>{const sd=i?1:-1;p.visible=true;p.position.set(sd*.66+dx*.12,.62+(dx*sd>0?.25:0)+Math.sin(this.t*14+i*2)*.1,.78)})}
      if(!this.charging&&J.s<.02){g.sc.remove(G);g.jar=null;this.jState='mochi';PW.forEach(p=>p.visible=false)}}
    else M.paws.forEach(p=>p.visible=false)}
  projW(v){const q=v.clone().project(this.gl.cam);return[(q.x+1)/2*this.w,(1-q.y)/2*this.h]}
  jarScreen(){const G=this.gl&&this.gl.jar&&this.gl.jar.g;if(!G||!G.visible)return null;return this.projW(G.localToWorld(new T3.Vector3(0,.25,0)))}
  grabBack(){this.jState='mochi';this.act=null;this.startAct('happy');this.say={g:'♥',life:1.4};this.tears.length=0;haptic(18);if(!this.preview&&!WP)this.snd.yay()}
  /* ----- draw ----- */
  draw(ctx){const w=this.w,h=this.h,U=this.U;const A=this.A||{active:false},night=(this.force||dayPhase())==='night',tw=A.active?A.treble:0;
    if(this.drawSky)this.drawSky(ctx);else for(const s of this.stars){ctx.globalAlpha=s.a*(night?1:.7)*(.55+.45*Math.sin(this.t*s.sp*(1+tw*3)+s.ph));ctx.fillStyle=s.c;ctx.fillRect(s.x*w,s.y*h,s.s,s.s)}ctx.globalAlpha=1;
    if(this.shoot){const sh=this.shoot,k=sh.t/1.1,x=(sh.x+sh.dir*k*.4)*w,y=(sh.y+k*.1)*h,tx=x-sh.dir*w*.11,ty=y-h*.03,gr=ctx.createLinearGradient(tx,ty,x,y);gr.addColorStop(0,'rgba(255,255,255,0)');gr.addColorStop(1,`rgba(255,255,255,${.7*(1-k)})`);ctx.strokeStyle=gr;ctx.lineWidth=1.3;ctx.beginPath();ctx.moveTo(tx,ty);ctx.lineTo(x,y);ctx.stroke()}
    if(this.fall){const p=this.fallPos(),r=4+3*Math.sin(this.t*12);ctx.globalCompositeOperation='lighter';glow(ctx,'#ffe08a',p[0],p[1],r*5,.8);ctx.globalCompositeOperation='source-over';drawShape(ctx,'stars',p[0],p[1],r*1.6,this.t*4,'#fff3c4',1)}
    for(const f of this.fog){const a=Math.min(1,f.life/1.5)*.16;const g2=ctx.createRadialGradient(f.x,f.y,0,f.x,f.y,U*.55);g2.addColorStop(0,`rgba(235,240,255,${a})`);g2.addColorStop(1,'rgba(235,240,255,0)');ctx.fillStyle=g2;ctx.beginPath();ctx.ellipse(f.x,f.y,U*.6,U*.38,0,0,TAU);ctx.fill()}
    this.drawFloor(ctx);this.drawSparkles(ctx,false);if(this.under)this.under(ctx);
    // the 3D jelly
    if(this.gl){this.adapt(dtFrame);if(this.renderGL)this.renderGL();else this.gl.r.render(this.gl.sc,this.gl.cam);if(this.reflect)this.reflect(ctx)}
    const bk=ctx;ctx=this.octx||ctx;
    // bubble being blown / snot bubble while sleeping
    const ac=this.act;if(ac&&ac.type==='bubble'){const k=ac.t/ac.dur;if(k<.85){const c=this.scr([0,-.12,1]),r=U*.05+U*.32*ease.inOut(k/.85)+Math.sin(this.t*9)*U*.01;this.drawBubble(ctx,c[0],c[1]+r*.6,r,'255,170,205')}}
    if(this.over)this.over(ctx);
    if(this.mood==='sleep'&&this.snotOn!==false){const c=this.scr([.12,.05,1]),k=this.snot/3.2,r=U*(.04+.11*Math.sin(k*Math.PI));this.drawBubble(ctx,c[0]+r*.7,c[1]-r*.2,r,'200,220,255')}
    for(const b of this.bubbles){const k=1-b.life/.45;ctx.strokeStyle=`rgba(255,200,225,${1-k})`;ctx.lineWidth=2;for(let i=0;i<8;i++){const a=i*TAU/8,r1=U*.15+k*U*.3,r0=r1-U*.06;ctx.beginPath();ctx.moveTo(b.x+Math.cos(a)*r0,b.y+Math.sin(a)*r0);ctx.lineTo(b.x+Math.cos(a)*r1,b.y+Math.sin(a)*r1);ctx.stroke()}}
    this.drawSparkles(ctx,true);for(const q of this.tears){ctx.globalAlpha=Math.min(1,q.life*2);ctx.fillStyle='#8fd8ff';ctx.beginPath();ctx.ellipse(q.x,q.y,U*.025,U*.035,Math.atan2(q.vy,q.vx)-Math.PI/2,0,TAU);ctx.fill()}ctx.globalAlpha=1;
    ctx.textAlign='center';ctx.textBaseline='middle';for(const z of this.zz){ctx.globalAlpha=Math.min(.8,z.life*.6,(3.2-z.life)*2);ctx.fillStyle='#d9c9ff';ctx.font=`800 ${U*.2*z.s*(1.4-z.life*.12)}px "Plus Jakarta Sans",system-ui,sans-serif`;ctx.fillText('z',z.x,z.y)}
    for(const n of this.notes){ctx.globalAlpha=Math.min(1,n.life);drawShape(ctx,n.g==='♪'?'notes':'hearts',n.x,n.y,U*(n.g==='♪'?.09:.07),0,n.c||'#ff7fae',Math.min(1,n.life))}ctx.globalAlpha=1;
    if(this.dz>0){const c=this.scr([0,1,0]);for(let i=0;i<4;i++){const a=this.t*5+i*TAU/4;drawShape(ctx,'stars',c[0]+Math.cos(a)*U*.7,c[1]-U*.15+Math.sin(a)*U*.15,U*.07,a,i%2?'#FFE29A':'#FFFFFF',.9)}}
    if(this.say){const c=this.scr([.5,.95,0]),r=U*.18,a=Math.min(1,this.say.life*3);ctx.globalAlpha=a;ctx.fillStyle='rgba(255,255,255,.95)';ctx.beginPath();ctx.roundRect?ctx.roundRect(c[0]-r,c[1]-r*.85-U*.15,r*2,r*1.7,r*.7):ctx.rect(c[0]-r,c[1]-r,r*2,r*1.7);ctx.fill();ctx.fillStyle=this.say.g==='♥'?'#ff3d7f':'#5B2BB5';ctx.font=`800 ${r*1.1}px system-ui,sans-serif`;ctx.fillText(this.say.g,c[0],c[1]-U*.15);ctx.globalAlpha=1}}
  floorP(u,v){const hz=this.floor-this.U*.28;return[this.w/2+u*this.w*(.28+.9*v),hz+(this.h-hz)*Math.pow(v,1.35)]}
  drawFloor(ctx){const w=this.w,h=this.h,U=this.U,hz=this.floor-U*.28,A=this.A||{},bass=A.active?A.bass:0,ch=this.charging;
    if(!this.traces){this.traces=[];for(let i=0;i<14;i++){let u=rnd(-1,1),v=rnd(.02,.12);const pts=[[u,v]];while(v<1.05){if(Math.random()<.55)v+=rnd(.08,.2);else u+=rnd(-.25,.25);pts.push([u,Math.min(v,1.05)])}this.traces.push({pts,c:Math.random()<.6?'120,220,255':'190,150,255',ph:rnd(),sp:rnd(.15,.3)})}}
    let g=ctx.createLinearGradient(0,hz,0,h);g.addColorStop(0,'rgba(22,15,40,0)');g.addColorStop(.18,'rgba(22,15,40,.6)');g.addColorStop(1,'rgba(10,7,20,.95)');ctx.fillStyle=g;ctx.fillRect(0,hz,w,h-hz);
    const lg=ctx.createLinearGradient(0,0,w,0);lg.addColorStop(0,'rgba(205,180,255,0)');lg.addColorStop(.5,'rgba(205,180,255,.22)');lg.addColorStop(1,'rgba(205,180,255,0)');ctx.fillStyle=lg;ctx.fillRect(0,hz,w,1);
    ctx.lineWidth=1;ctx.lineJoin='round';for(const L of this.traces){ctx.strokeStyle=`rgba(${L.c},.16)`;ctx.beginPath();L.pts.forEach((q,i)=>{const P=this.floorP(q[0],q[1]);i?ctx.lineTo(P[0],P[1]):ctx.moveTo(P[0],P[1])});ctx.stroke();
      L.ph=(L.ph+(ch?-1:1)*L.sp*dtFrame*(1+bass*2))%1;if(L.ph<0)L.ph+=1;const f=L.ph*(L.pts.length-1),i0=Math.floor(f),q0=L.pts[i0],q1=L.pts[Math.min(i0+1,L.pts.length-1)],P=this.floorP(lerp(q0[0],q1[0],f-i0),lerp(q0[1],q1[1],f-i0));
      ctx.globalCompositeOperation='lighter';glow(ctx,ch?'#6dff8e':(L.c[0]==='1'&&L.c[1]==='2'?'#7fdcff':'#c39bff'),P[0],P[1],U*.05,.8);ctx.globalCompositeOperation='source-over'}
    ctx.save();ctx.translate(this.x,this.floor);ctx.scale(1,.2);ctx.globalCompositeOperation='lighter';glow(ctx,ch?'#4dff7a':(this.col||'#ff7fb0'),0,0,U*1.5,.32+bass*.3);glow(ctx,'#c9a6ff',0,0,U*2.2,.12);ctx.restore()}
  drawSparkles(ctx,front){if(!this.spark){this.spark=[...Array(18)].map((_,i)=>({a:rnd(TAU),r:rnd(1.05,1.7),y:rnd(-.5,.5),ph:rnd(TAU),s:rnd(.035,.08),c:['#ffe08a','#7dffb0','#8fd8ff','#c9a6ff','#ff9ecf'][i%5],front:i%3===0,bub:i%4===1}))}
    const U=this.U,cx=this.x,cy=this.y-U*.8,ch=this.charging,A=this.A||{},tw=A.active?A.treble:0;
    for(const s of this.spark){if(s.front!==front)continue;s.a+=dtFrame*.12;const x=cx+Math.cos(s.a)*U*s.r*1.15,y=cy+Math.sin(s.a)*U*s.r*.55+s.y*U*.6+Math.sin(this.t*.8+s.ph)*U*.05,tw2=.5+.5*Math.sin(this.t*2.2+s.ph+tw*4);
      if(s.bub){const r=U*s.s*1.4;ctx.globalAlpha=.75;ctx.strokeStyle=ch?'#7dffb0':s.c;ctx.lineWidth=1.3;ctx.beginPath();ctx.arc(x,y,r,0,TAU);ctx.stroke();ctx.fillStyle='rgba(255,255,255,.6)';ctx.beginPath();ctx.arc(x-r*.35,y-r*.35,r*.18,0,TAU);ctx.fill();
        if(ch){ctx.fillStyle='#b8ff9a';ctx.beginPath();ctx.moveTo(x+r*.1,y-r*.6);ctx.lineTo(x-r*.35,y+r*.05);ctx.lineTo(x,y+r*.05);ctx.lineTo(x-r*.12,y+r*.6);ctx.lineTo(x+r*.35,y-r*.08);ctx.lineTo(x,y-r*.08);ctx.fill()}ctx.globalAlpha=1;continue}
      const r=U*s.s*(.6+.6*tw2);ctx.globalCompositeOperation='lighter';glow(ctx,s.c,x,y,r*2.2,.55*tw2);ctx.globalCompositeOperation='source-over';ctx.globalAlpha=.6+.4*tw2;ctx.fillStyle=s.c;ctx.beginPath();ctx.moveTo(x,y-r);ctx.quadraticCurveTo(x,y,x+r*.8,y);ctx.quadraticCurveTo(x,y,x,y+r);ctx.quadraticCurveTo(x,y,x-r*.8,y);ctx.quadraticCurveTo(x,y,x,y-r);ctx.fill();ctx.globalAlpha=1}}
  drawBubble(ctx,x,y,r,rgb){const g=ctx.createRadialGradient(x-r*.3,y-r*.3,r*.1,x,y,r);g.addColorStop(0,`rgba(255,255,255,.35)`);g.addColorStop(.7,`rgba(${rgb},.18)`);g.addColorStop(1,`rgba(${rgb},.45)`);ctx.fillStyle=g;ctx.beginPath();ctx.arc(x,y,r,0,TAU);ctx.fill();ctx.strokeStyle=`rgba(255,255,255,.55)`;ctx.lineWidth=1.2;ctx.stroke();ctx.fillStyle='rgba(255,255,255,.8)';ctx.beginPath();ctx.ellipse(x-r*.4,y-r*.42,r*.18,r*.1,-.6,0,TAU);ctx.fill()}
  adapt(dt){const g=this.gl;g.fa=lerp(g.fa||16,dt*1000,.05);g.cool=(g.cool||0)-dt;if(g.cool>0)return;if(g.fa>18.5&&g.pr>.75){g.pr=Math.max(.75,g.pr-.15);g.cool=1.5;this.layoutGL()}else if(g.fa<15&&g.pr<g.maxPr){g.pr=Math.min(g.maxPr,g.pr+.1);g.cool=3;this.layoutGL()}}
  dispose(){store.set(this.id+'.aff',this.affection);store.set(this.id+'.seen',Date.now());this.detach()}
}
function normalize3(v){const l=Math.hypot(...v)||1;return[v[0]/l,v[1]/l,v[2]/l]}

