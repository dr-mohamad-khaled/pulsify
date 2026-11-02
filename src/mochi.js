/* ================= MOCHI 3 — the cream bean buddy (matches the reference art) =================
   Tall round jelly body, huge glossy galaxy eyes, blush with little hatch marks, an antenna with a
   glowing bulb, stubby arms that hold things, round feet, pink rim glow, mirror floor reflection.
   Looks at wherever you touch (head turns + eyes), dances to music, and has 22 little stories:
   phone, juice box, ice cream (take any of them away and it cries and chases you), a bird that lands
   on its antenna (heart eyes), firefly, rain + umbrella, snow on the tongue, balloon, selfie, … */
const MOCHI2_S=[.92,.8,.84,.8];
const MOCHI2_V=MOCHI_V.replace('float w=1.+.08*','float w=1.+.1*');
const MOCHI2_F=`uniform sampler2D uFace;uniform vec3 uTop,uBot,uRim,uRim2,uKey,uTint,uFill,uStarC,uLit,uBounce;uniform float uT,uFillLv,uFillA,uStar,uGlow,uRimK,uGal;varying vec3 vN,vV,vP0;
void main(){vec3 n=normalize(vN),v=normalize(vV);float ndv=clamp(dot(n,v),0.,1.),fr=1.-ndv;
 vec3 base=mix(uBot,uTop,smoothstep(-.9,.8,vP0.y));
 vec3 L=normalize(uKey);float wrap=dot(n,L)*.5+.5;vec3 col=base*vec3(1.08,1.02,.98)*(.56+.72*wrap)*uTint;
 col*=mix(.82,1.,smoothstep(-1.,-.3,vP0.y));
 col+=vec3(1.,.55,.75)*pow(fr,1.4)*.22;
 col+=uBounce*pow(max(-n.y,0.),1.4)*.45;
 col+=uLit*smoothstep(.15,.95,vP0.z)*smoothstep(.85,-.5,vP0.y)*.6;
 if(uGal>0.){vec3 cell=floor(vP0*26.);float hs=fract(sin(dot(cell,vec3(12.9,78.2,37.7)))*43758.5);vec3 f3=fract(vP0*26.)-.5;float tw=.5+.5*sin(uT*3.+hs*40.);col+=vec3(1.,.9,1.)*step(.93,hs)*smoothstep(.22,.0,length(f3))*tw*uGal*1.2;col+=mix(vec3(.9,.2,.8),vec3(.2,.7,1.),smoothstep(-1.,1.,vP0.x+vP0.y*.5))*.12*uGal;}
 if(uStar>.01){float d=length(vP0-vec3(.0,-.2,.9));col+=uStarC*uStar*(exp(-d*d*8.)*.9+exp(-d*d*2.)*.2);}
 vec2 fu=vP0.xy*.625+.5;float front=smoothstep(.05,.35,vP0.z);vec4 f=texture2D(uFace,fu);col=mix(col,f.rgb,f.a*front);
 vec3 rc=mix(uRim,uRim2,smoothstep(-.8,.8,n.x*.75-n.y*.45));col=mix(col,rc,clamp(pow(fr,2.2)*.62*uRimK,0.,1.));col+=rc*pow(fr,4.)*.9*uRimK*(1.+uGlow);
 vec3 H=normalize(L+v);float sp=pow(max(dot(n,H),0.),70.)*.32;float w1=smoothstep(.955,.985,dot(n,normalize(vec3(-.52,.62,.58))))*.3;float w2=smoothstep(.994,.998,dot(n,normalize(vec3(.42,.38,.82))))*.28;
 col+=vec3(sp+w1+w2);
 gl_FragColor=vec4(col,1.);
 #include <tonemapping_fragment>
 #include <encodings_fragment>
}`;
const MOCHI2_SND=Object.assign({},MOCHI_SND,{
  chirp(){for(let i=0;i<3;i++)SFX.tone('sine',2600+i*200,3400+i*150,.07,.07,i*.11)},
  hic(){SFX.tone('sine',380,760,.07,.16)},
  knock(){SFX.tone('square',190,110,.06,.07);SFX.hiss(900,.05,.12)},
  shutter(){SFX.hiss(5000,.05,.25);SFX.hiss(3000,.06,.2,.07)},
  boom(){SFX.tone('sine',130,55,.22,.3)},
  kiss(){SFX.tone('sine',1500,2600,.06,.12);SFX.hiss(4000,.04,.08,.02)},
  whee(){SFX.tone('sine',500,1400,.4,.12)},
  ding(){SFX.tone('sine',1320,1320,.5,.12);SFX.tone('sine',1980,1980,.4,.06,.02)},
  sip(){SFX.hiss(1800,.18,.08)},
  lick(){SFX.hiss(2500,.08,.08)},
  burr(){SFX.tone('triangle',300,180,.4,.08)}
});

/* ---------- small 2D drawing helpers for props ---------- */
function glowM(c,col,x,y,r,a){if(r<1)return;const g=c.createRadialGradient(x,y,0,x,y,r);g.addColorStop(0,hexA(col,a));g.addColorStop(.35,hexA(col,a*.35));g.addColorStop(1,hexA(col,0));c.fillStyle=g;c.beginPath();c.arc(x,y,r,0,TAU);c.fill()}
function rrP(c,x,y,w,h,r){c.beginPath();c.moveTo(x+r,y);c.arcTo(x+w,y,x+w,y+h,r);c.arcTo(x+w,y+h,x,y+h,r);c.arcTo(x,y+h,x,y,r);c.arcTo(x,y,x+w,y,r);c.closePath()}
function heartP(c,x,y,s){c.beginPath();c.moveTo(x,y+s*.45);c.bezierCurveTo(x-s*1.15,y-s*.3,x-s*.5,y-s*1.1,x,y-s*.42);c.bezierCurveTo(x+s*.5,y-s*1.1,x+s*1.15,y-s*.3,x,y+s*.45);c.closePath()}
function starP(c,x,y,r,rot){c.beginPath();for(let k=0;k<10;k++){const rr=k%2?r*.45:r,a=k*Math.PI/5-Math.PI/2+rot;k?c.lineTo(x+Math.cos(a)*rr,y+Math.sin(a)*rr):c.moveTo(x+Math.cos(a)*rr,y+Math.sin(a)*rr)}c.closePath()}
function sparkP(c,x,y,r){c.beginPath();c.moveTo(x,y-r);c.quadraticCurveTo(x,y,x+r*.8,y);c.quadraticCurveTo(x,y,x,y+r);c.quadraticCurveTo(x,y,x-r*.8,y);c.quadraticCurveTo(x,y,x,y-r);c.closePath()}
function drawPhone2(c,x,y,U,rot,t,sel){const w=sel?U*.3:U*.72,h=sel?U*.52:U*.42;c.save();c.translate(x,y);c.rotate(rot);
  c.globalCompositeOperation='lighter';glowM(c,'#b48cff',0,0,U*.55,.35);c.globalCompositeOperation='source-over';
  rrP(c,-w/2,-h/2,w,h,U*.06);c.fillStyle='#16121f';c.fill();c.lineWidth=U*.014;c.strokeStyle='rgba(205,180,255,.9)';c.stroke();
  const m=U*.03;rrP(c,-w/2+m,-h/2+m,w-2*m,h-2*m,U*.04);c.save();c.clip();
  if(sel){const g=c.createLinearGradient(0,-h/2,0,h/2);g.addColorStop(0,'#2a2140');g.addColorStop(1,'#120e1c');c.fillStyle=g;c.fillRect(-w/2,-h/2,w,h);c.fillStyle='#fff';c.beginPath();c.arc(0,h*.33,U*.045,0,TAU);c.fill()}
  else{const hue=(t*35)%360,g=c.createLinearGradient(-w/2,0,w/2,0);g.addColorStop(0,`hsl(${hue},85%,58%)`);g.addColorStop(1,`hsl(${hue+70},85%,48%)`);c.fillStyle=g;c.fillRect(-w/2,-h/2,w,h);
    // a tiny cartoon: a round cat bouncing over hills
    c.fillStyle='rgba(255,255,255,.28)';c.beginPath();for(let i=-3;i<=3;i++){const xx=((i*.22-(t*.25)%.22))*w*1.2;c.ellipse(xx,h*.42,w*.16,h*.22,0,Math.PI,0)}c.fill();
    const b=Math.abs(Math.sin(t*3.6)),cx0=Math.sin(t*.9)*w*.18,cy0=h*.12-b*h*.32;c.fillStyle='#fff';c.beginPath();c.ellipse(cx0,cy0,U*.07,U*.06*(1-.15*(1-b)),0,0,TAU);c.fill();
    c.beginPath();c.moveTo(cx0-U*.06,cy0-U*.03);c.lineTo(cx0-U*.045,cy0-U*.085);c.lineTo(cx0-U*.02,cy0-U*.05);c.moveTo(cx0+U*.06,cy0-U*.03);c.lineTo(cx0+U*.045,cy0-U*.085);c.lineTo(cx0+U*.02,cy0-U*.05);c.fill();
    c.fillStyle='#2b1426';c.beginPath();c.arc(cx0-U*.025,cy0,U*.009,0,TAU);c.arc(cx0+U*.025,cy0,U*.009,0,TAU);c.fill();
    if(Math.sin(t*2.2)>.6){heartP(c,w*.32,-h*.25,U*.035);c.fillStyle='#fff';c.fill()}
    c.fillStyle='rgba(255,255,255,.75)';c.fillRect(-w/2+m*1.4,h/2-m*2.2,(w-m*2.8)*((t*.07)%1),U*.008)}
  c.restore();c.fillStyle='rgba(255,255,255,.18)';c.beginPath();c.moveTo(-w/2+m,-h/2+m);c.lineTo(-w/2+w*.35,-h/2+m);c.lineTo(-w/2+m,-h/2+h*.6);c.closePath();c.fill();c.restore()}
function drawJuice2(c,x,y,U,rot,lv,t){const w=U*.42,h=U*.5;c.save();c.translate(x,y);c.rotate(rot);
  c.fillStyle='#ffd6e8';c.beginPath();c.moveTo(-w/2,-h/2);c.lineTo(-w*.32,-h/2-w*.3);c.lineTo(w*.32,-h/2-w*.3);c.lineTo(w/2,-h/2);c.closePath();c.fill();c.fillStyle='#ffc2dc';c.fillRect(-w*.32,-h/2-w*.36,w*.64,w*.07);
  const g=c.createLinearGradient(-w/2,0,w/2,0);g.addColorStop(0,'#ff86b8');g.addColorStop(.45,'#ffb4d3');g.addColorStop(1,'#f871a9');c.fillStyle=g;rrP(c,-w/2,-h/2,w,h,U*.035);c.fill();
  c.fillStyle='#fff7fb';c.beginPath();c.ellipse(0,h*.06,w*.33,w*.3,0,0,TAU);c.fill();
  c.fillStyle='#ff4d6d';c.beginPath();c.moveTo(0,h*.2);c.bezierCurveTo(-w*.24,h*.04,-w*.18,-h*.1,0,-h*.08);c.bezierCurveTo(w*.18,-h*.1,w*.24,h*.04,0,h*.2);c.fill();
  c.fillStyle='#4dd17a';c.beginPath();c.ellipse(-w*.05,-h*.1,w*.07,w*.03,-.5,0,TAU);c.ellipse(w*.05,-h*.1,w*.07,w*.03,.5,0,TAU);c.fill();
  c.fillStyle='#ffe9a6';for(const[a,b]of[[-.06,.0],[.06,.02],[0,.1],[-.08,.1],[.08,.12]]){c.beginPath();c.ellipse(a*w,b*h,w*.012,w*.02,0,0,TAU);c.fill()}
  c.fillStyle='rgba(255,255,255,.35)';c.fillRect(-w/2+w*.08,-h/2+h*.06,w*.08,h*.84);
  c.fillStyle='rgba(255,255,255,.85)';c.font=`800 ${U*.055}px "Plus Jakarta Sans",system-ui,sans-serif`;c.textAlign='center';c.textBaseline='middle';c.fillText('MILK',0,h*.38);
  c.restore()}
function drawCone(c,x,y,U,rot,left,t){c.save();c.translate(x,y);c.rotate(rot);const w=U*.26,h=U*.34;
  const g=c.createLinearGradient(-w/2,0,w/2,0);g.addColorStop(0,'#d99a55');g.addColorStop(.5,'#f2c27e');g.addColorStop(1,'#c9853f');c.fillStyle=g;c.beginPath();c.moveTo(-w/2,0);c.lineTo(w/2,0);c.lineTo(0,h);c.closePath();c.fill();
  c.save();c.clip();c.strokeStyle='rgba(150,90,40,.55)';c.lineWidth=U*.008;for(let i=-4;i<5;i++){c.beginPath();c.moveTo(i*w*.18-w,0);c.lineTo(i*w*.18+w,h*1.6);c.stroke();c.beginPath();c.moveTo(i*w*.18+w,0);c.lineTo(i*w*.18-w,h*1.6);c.stroke()}c.restore();
  const sc=[['#8ff0c8','#5fd6a6'],['#ffb3d1','#ff86b6']];const n=left>.5?2:1,fr=left>.5?(left-.5)*2:left*2;
  for(let i=0;i<n;i++){const r=w*(.55-.04*i)*(i===n-1?(.55+.45*fr):1),cy=-r*.55-i*w*.62;const gg=c.createRadialGradient(-r*.3,cy-r*.3,r*.1,0,cy,r);gg.addColorStop(0,'#fff');gg.addColorStop(.25,sc[i][0]);gg.addColorStop(1,sc[i][1]);c.fillStyle=gg;c.beginPath();c.arc(0,cy,r,0,TAU);c.fill();
    if(i===0){c.beginPath();c.arc(-w*.3,r*.15,w*.07,0,TAU);c.arc(w*.22,r*.2+Math.sin(t*2)*U*.01,w*.06,0,TAU);c.fill()}
    for(let k=0;k<5;k++){c.fillStyle=['#fff36b','#7fd8ff','#fff','#ff8fd0','#b48cff'][k];c.save();c.translate(Math.cos(k*2.3+i)*r*.5,cy+Math.sin(k*1.7+i)*r*.45);c.rotate(k);c.fillRect(-U*.012,-U*.004,U*.024,U*.008);c.restore()}}
  c.restore()}
function drawBird(c,x,y,U,flap,dir,t,perch){c.save();c.translate(x,y);c.scale(dir,1);const s=U*.14;
  c.globalCompositeOperation='lighter';glowM(c,'#7fd8ff',0,0,s*3.2,.35);c.globalCompositeOperation='source-over';
  c.fillStyle='#4fb5ff';c.beginPath();c.moveTo(-s*.8,s*.1);c.lineTo(-s*1.55,-s*.25);c.lineTo(-s*1.45,s*.35);c.closePath();c.fill();
  const g=c.createRadialGradient(-s*.3,-s*.35,s*.1,0,0,s);g.addColorStop(0,'#bfe9ff');g.addColorStop(.6,'#6cc4ff');g.addColorStop(1,'#3d8cf0');c.fillStyle=g;c.beginPath();c.ellipse(0,0,s,s*.88,0,0,TAU);c.fill();
  c.fillStyle='#fff6e0';c.beginPath();c.ellipse(s*.15,s*.32,s*.55,s*.45,0,0,TAU);c.fill();
  c.fillStyle='#ffb347';c.beginPath();c.moveTo(s*.85,-s*.12);c.lineTo(s*1.35,s*.02);c.lineTo(s*.85,s*.16);c.closePath();c.fill();
  c.fillStyle='#1d1030';c.beginPath();c.arc(s*.45,-s*.22,s*.17,0,TAU);c.fill();c.fillStyle='#fff';c.beginPath();c.arc(s*.5,-s*.28,s*.06,0,TAU);c.fill();
  c.fillStyle='rgba(255,120,170,.6)';c.beginPath();c.ellipse(s*.55,s*.1,s*.14,s*.08,0,0,TAU);c.fill();
  c.fillStyle='#5fc0ff';c.beginPath();c.moveTo(-s*.1,-s*.5);c.lineTo(s*.05,-s*.95);c.lineTo(s*.2,-s*.5);c.fill();
  const a=perch?.25+Math.sin(t*3)*.08:Math.sin(flap)*1.1;c.save();c.translate(-s*.15,-s*.05);c.rotate(-a);const wg=c.createLinearGradient(0,0,-s*1.2,-s*.4);wg.addColorStop(0,'#7fd0ff');wg.addColorStop(1,'#2f7de0');c.fillStyle=wg;c.beginPath();c.ellipse(-s*.55,0,s*.7,s*.32,-.2,0,TAU);c.fill();c.restore();
  if(perch){c.strokeStyle='#ffb347';c.lineWidth=s*.09;c.lineCap='round';c.beginPath();c.moveTo(-s*.15,s*.8);c.lineTo(-s*.15,s*1.05);c.moveTo(s*.2,s*.8);c.lineTo(s*.2,s*1.05);c.stroke()}
  c.restore()}
function drawCloud(c,x,y,U,a,dark){c.save();c.globalAlpha=a;const s=U*.32;c.fillStyle=dark?'#5b5470':'#e9e4f5';c.beginPath();for(const[dx,dy,r]of[[-1,.15,.55],[-.4,-.2,.7],[.35,-.25,.65],[.95,.12,.5],[0,.25,.6]])c.arc(x+dx*s,y+dy*s,r*s,0,TAU);c.fill();
  c.fillStyle=dark?'#6e6787':'#fff';c.beginPath();c.arc(x-.4*s,y-.32*s,.42*s,0,TAU);c.fill();c.restore()}
function drawUmbrella(c,x,y,U,rot,open){c.save();c.translate(x,y);c.rotate(rot);const R=U*.62*open;
  c.strokeStyle='#e8dcff';c.lineWidth=U*.022;c.lineCap='round';c.beginPath();c.moveTo(0,0);c.lineTo(0,-U*.62);c.stroke();c.beginPath();c.arc(-U*.05,0,U*.05,0,Math.PI);c.stroke();
  if(R>2){const g=c.createLinearGradient(-R,0,R,0);g.addColorStop(0,'#ff7fb0');g.addColorStop(.5,'#ffb3d1');g.addColorStop(1,'#c99bff');c.fillStyle=g;c.beginPath();c.moveTo(-R,-U*.62);c.quadraticCurveTo(-R,-U*.62-R*.95,0,-U*.62-R*.95);c.quadraticCurveTo(R,-U*.62-R*.95,R,-U*.62);
    for(let i=4;i>0;i--){const x1=-R+(i-1)*R/2,x0=x1+R/2;c.quadraticCurveTo((x0+x1)/2,-U*.62-R*.18,x1,-U*.62)}c.closePath();c.fill();c.fillStyle='rgba(255,255,255,.3)';c.beginPath();c.ellipse(-R*.4,-U*.62-R*.55,R*.18,R*.1,-.6,0,TAU);c.fill()}
  c.restore()}

/* ---------- the face ---------- */
function drawMochi2Face(g,F,t,O){O=O||{};const W=g.canvas.width,P=W*.625,AX=.801/.625;g.setTransform(1,0,0,1,0,0);g.clearRect(0,0,W,W);g.setTransform(AX,0,0,1,W/2*(1-AX),0);
  const X=x=>W/2+x*P,Y=y=>W/2-y*P,ink=O.ink||'#2b1426',SKIN=O.skin||'#fbeee9';g.lineCap='round';g.lineJoin='round';
  const E=P*.16,eyeY=Y(.16),ex=[X(-.27),X(.27)],lx=F.look[0]*E*.5,ly=F.look[1]*E*.38;
  // blush with hatch marks
  for(const s of[-1,1]){const bx=X(s*.46)+lx*.3,by=Y(-.03);const r=g.createRadialGradient(bx,by,0,bx,by,E*1.05);r.addColorStop(0,`rgba(255,95,145,${.75*F.blush})`);r.addColorStop(.6,`rgba(255,110,155,${.42*F.blush})`);r.addColorStop(1,'rgba(255,120,160,0)');g.fillStyle=r;g.beginPath();g.ellipse(bx,by,E*1.1,E*.62,0,0,TAU);g.fill();
    g.strokeStyle=`rgba(255,255,255,${.75*Math.min(1,F.blush)})`;g.lineWidth=E*.08;for(let i=-1;i<=1;i++){g.beginPath();g.moveTo(bx+i*E*.28-E*.08,by+E*.14);g.lineTo(bx+i*E*.28+E*.08,by-E*.14);g.stroke()}}
  const gem=(x,y,rx,ry,open,i,big)=>{
    g.save();g.beginPath();g.ellipse(x,y,rx,ry,0,0,TAU);g.clip();
    let gr=g.createLinearGradient(x,y-ry,x,y+ry);gr.addColorStop(0,'#160818');gr.addColorStop(.5,'#2c1233');gr.addColorStop(1,'#6a2f6e');g.fillStyle=gr;g.fillRect(x-rx,y-ry,rx*2,ry*2);
    gr=g.createRadialGradient(x,y+ry*.55,0,x,y+ry*.55,rx*1.1);gr.addColorStop(0,'rgba(190,110,220,.6)');gr.addColorStop(1,'rgba(190,110,220,0)');g.fillStyle=gr;g.fillRect(x-rx,y-ry,rx*2,ry*2);
    const sp=[[-.3,.45,'#ffb3e6'],[.25,.55,'#9ff3ff'],[.05,.3,'#fff'],[-.45,.15,'#c9a6ff'],[.42,.2,'#ffe7a6'],[-.1,.62,'#fff']];for(const[a,b,c]of sp){g.globalAlpha=.5+.5*Math.sin(t*3+a*9+i*2);g.fillStyle=c;g.beginPath();g.arc(x+a*rx,y+b*ry,rx*.045,0,TAU);g.fill()}g.globalAlpha=1;
    const hx=-lx*.25,hy=-ly*.2;g.fillStyle='#fff';g.beginPath();g.ellipse(x-rx*.32+hx,y-ry*.36+hy,rx*.36*big,ry*.32*big,-.3,0,TAU);g.fill();g.beginPath();g.arc(x+rx*.36+hx,y+ry*.3+hy,rx*.15,0,TAU);g.fill();
    g.globalAlpha=.9;sparkP(g,x+rx*.36+hx,y-ry*.42+hy,rx*.17*(1+.15*Math.sin(t*5+i)));g.fill();g.globalAlpha=1;
    if(open<.98){g.fillStyle=SKIN;g.fillRect(x-rx*1.1,y-ry*1.1,rx*2.2,ry*2.2*(1-open));}
    g.restore();
    if(open<.98){const ly2=y-ry+ry*2*(1-open);g.strokeStyle=ink;g.lineWidth=E*.13;g.beginPath();g.ellipse(x,y,rx,ry,0,Math.asin(clamp((ly2-y)/ry,-1,1))+Math.PI,-Math.asin(clamp((ly2-y)/ry,-1,1)),false);g.stroke()}
    g.strokeStyle='rgba(43,20,38,.55)';g.lineWidth=E*.06;g.beginPath();g.ellipse(x,y,rx,ry,0,0,TAU);g.stroke()};
  const drawEye=(x0,mode,i)=>{const x=x0+lx,y=eyeY+ly,sd=i?1:-1;
    if(mode==='happy'){g.strokeStyle=ink;g.lineWidth=E*.3;g.beginPath();g.arc(x,y+E*.45,E*.8,Math.PI*1.15,Math.PI*1.85);g.stroke();return}
    if(mode==='closed'){g.strokeStyle=ink;g.lineWidth=E*.26;g.beginPath();g.arc(x,y-E*.25,E*.78,Math.PI*.18,Math.PI*.82);g.stroke();g.lineWidth=E*.12;for(const a of[.3,.5,.7]){const aa=Math.PI*a,px=x+Math.cos(aa)*E*.78,py=y-E*.25+Math.sin(aa)*E*.78;g.beginPath();g.moveTo(px,py);g.lineTo(px+Math.cos(aa)*E*.22,py+Math.sin(aa)*E*.22);g.stroke()}return}
    if(mode==='squeeze'||mode==='cry'){g.strokeStyle=ink;g.lineWidth=E*.28;g.beginPath();g.moveTo(x-E*.6*sd,y-E*.5);g.lineTo(x+E*.45*sd,y);g.lineTo(x-E*.6*sd,y+E*.5);g.stroke();
      if(mode==='cry'){const L=E*(2.6+.4*Math.sin(t*9+i*2));const gr=g.createLinearGradient(0,y,0,y+L);gr.addColorStop(0,'rgba(140,215,255,.95)');gr.addColorStop(1,'rgba(140,215,255,.15)');g.fillStyle=gr;g.beginPath();g.moveTo(x-E*.35,y+E*.25);g.quadraticCurveTo(x-E*.45,y+L*.6,x-E*.15+Math.sin(t*12+i)*E*.1,y+L);g.lineTo(x+E*.3+Math.sin(t*12+i)*E*.1,y+L);g.quadraticCurveTo(x+E*.4,y+L*.6,x+E*.25,y+E*.25);g.closePath();g.fill();
        g.fillStyle='rgba(255,255,255,.7)';g.fillRect(x-E*.12,y+E*.5,E*.08,L*.6)}return}
    if(mode==='heart'){const s=E*1.15*(1+.1*Math.sin(t*10));g.fillStyle='#ff3d7f';heartP(g,x,y+E*.05,s);g.fill();g.strokeStyle='#c9175b';g.lineWidth=E*.06;g.stroke();g.fillStyle='#fff';g.beginPath();g.ellipse(x-s*.4,y-s*.35,s*.2,s*.13,-.6,0,TAU);g.fill();return}
    if(mode==='star'){g.fillStyle='#ffd34d';starP(g,x,y,E*1.15,t*.8);g.fill();g.fillStyle='#fff';g.beginPath();g.arc(x-E*.25,y-E*.3,E*.15,0,TAU);g.fill();return}
    if(mode==='spiral'){g.strokeStyle=ink;g.lineWidth=E*.14;g.beginPath();for(let a=0;a<TAU*2.6;a+=.2){const r=a*E*.07,aa=a+t*9*(i?-1:1);g.lineTo(x+Math.cos(aa)*r,y+Math.sin(aa)*r)}g.stroke();return}
    if(mode==='note'){g.fillStyle=ink;g.font=`900 ${E*2.3}px system-ui,sans-serif`;g.textAlign='center';g.textBaseline='middle';g.fillText(i?'♪':'♫',x,y);return}
    if(mode==='dot'){g.fillStyle=ink;g.beginPath();g.ellipse(x,y,E*.3,E*.38,0,0,TAU);g.fill();g.fillStyle='#fff';g.beginPath();g.arc(x-E*.1,y-E*.14,E*.1,0,TAU);g.fill();return}
    if(mode==='side'){gem(x,y,E,E*1.15,.45,i,.8);return}
    const open=clamp(F.eyeOpen,0,1);if(open<.1){g.strokeStyle=ink;g.lineWidth=E*.26;g.beginPath();g.moveTo(x-E*.7,y);g.quadraticCurveTo(x,y+E*.3,x+E*.7,y);g.stroke();return}
    const big=mode==='wow'?1.14:mode==='teary'?1.08:1;gem(x,y,E*big,E*1.15*big,open,i,big);
    if(mode==='wow'||F.sparkle>0){g.globalAlpha=Math.max(F.sparkle,mode==='wow'?1:0);g.fillStyle='#fff';sparkP(g,x-E*.1,y+E*.55,E*.16);g.fill();g.globalAlpha=1}
    if(mode==='teary'){g.save();g.beginPath();g.ellipse(x,y,E*big,E*1.15*big,0,0,TAU);g.clip();const wl=y+E*.35+Math.sin(t*6+i)*E*.06;const gr=g.createLinearGradient(0,wl,0,y+E*1.2);gr.addColorStop(0,'rgba(170,230,255,.85)');gr.addColorStop(1,'rgba(110,190,255,.5)');g.fillStyle=gr;g.beginPath();g.moveTo(x-E*1.2,wl);for(let k=0;k<=12;k++)g.lineTo(x-E*1.2+k*E*.2,wl+Math.sin(t*8+k)*E*.04);g.lineTo(x+E*1.2,y+E*1.3);g.lineTo(x-E*1.2,y+E*1.3);g.fill();g.restore();
      g.fillStyle='rgba(150,220,255,.9)';const dy=((t*.7+i*.5)%1)*E*1.4;g.beginPath();g.ellipse(x+sd*E*.95,y+E*.6+dy,E*.12,E*.17,0,0,TAU);g.fill()}};
  let em=F.eye;const L=em==='wink'?'normal':em==='wink2'?'happy':em==='peek'?'closed':em,R=em==='wink'?'happy':em==='wink2'?'normal':em==='peek'?'normal':em;drawEye(ex[0],L,0);drawEye(ex[1],R,1);
  if(F.browOn||Math.abs(F.brow)>.3){g.strokeStyle=ink;g.lineWidth=E*.16;for(const[i,x]of ex.entries()){const s=i?1:-1,a=F.brow*.5*s,yb=eyeY-E*1.55-(F.browUp||0)*E*.35;g.beginPath();g.moveTo(x-E*.45*Math.cos(a)+lx,yb+E*.45*Math.sin(a));g.lineTo(x+E*.45*Math.cos(a)+lx,yb-E*.45*Math.sin(a));g.stroke()}}
  // mouth
  const mx=X(0)+lx*.35,my=Y(-.06)+ly*.15,m=F.mouth,o=clamp(F.mouthOpen,0,1);g.strokeStyle=ink;g.lineWidth=E*.14;g.fillStyle='#4a1630';
  const omouth=(w,h)=>{g.beginPath();g.ellipse(mx,my+h*.35,w,h,0,0,TAU);g.fill();g.save();g.clip();g.fillStyle='#ff7d9c';g.beginPath();g.ellipse(mx,my+h*1.1,w*.75,h*.62,0,0,TAU);g.fill();g.restore()};
  if(m==='cat'&&o<.15){g.beginPath();g.arc(mx-E*.2,my,E*.2,.1,Math.PI-.1);g.arc(mx+E*.2,my,E*.2,.1,Math.PI-.1);g.stroke()}
  else if(m==='smile'&&o<.15){g.beginPath();g.arc(mx,my-E*.25,E*.42,Math.PI*.2,Math.PI*.8);g.stroke()}
  else if(m==='tongue'){g.beginPath();g.arc(mx-E*.2,my,E*.2,.1,Math.PI-.1);g.arc(mx+E*.2,my,E*.2,.1,Math.PI-.1);g.stroke();g.fillStyle='#ff7d9c';g.beginPath();g.ellipse(mx,my+E*(.3+o*.25),E*.17,E*(.18+o*.25),0,0,TAU);g.fill();g.strokeStyle='rgba(200,60,100,.6)';g.lineWidth=E*.04;g.beginPath();g.moveTo(mx,my+E*.15);g.lineTo(mx,my+E*(.3+o*.35));g.stroke()}
  else if(m==='kiss'){g.lineWidth=E*.13;g.beginPath();g.arc(mx,my-E*.12,E*.13,-Math.PI*.6,Math.PI*.5);g.arc(mx,my+E*.13,E*.13,-Math.PI*.5,Math.PI*.6);g.stroke()}
  else if(m==='frown'){g.beginPath();g.arc(mx,my+E*.4,E*.35,Math.PI*1.2,Math.PI*1.8);g.stroke()}
  else if(m==='pout'){g.beginPath();g.moveTo(mx-E*.28,my+E*.05);g.quadraticCurveTo(mx-E*.08,my-E*.16,mx,my+E*.04);g.quadraticCurveTo(mx+E*.08,my-E*.16,mx+E*.28,my+E*.05);g.stroke()}
  else if(m==='wobble'){g.beginPath();for(let k=0;k<=8;k++){const xx=mx-E*.4+k*E*.1;g.lineTo(xx,my+E*.08+Math.sin(k*1.6+t*14)*E*.07)}g.stroke();if(o>.3)omouth(E*.25,E*.2*o)}
  else if(m==='o'){omouth(E*(.15+o*.08),E*(.18+o*.12))}
  else if(m==='grin'){g.beginPath();g.moveTo(mx-E*.42,my-E*.05);g.quadraticCurveTo(mx,my+E*.55,mx+E*.42,my-E*.05);g.closePath();g.fill();g.fillStyle='#fff';g.fillRect(mx-E*.32,my-E*.04,E*.64,E*.09)}
  else omouth(E*(.3+o*.12),E*(.1+o*.42));
  if(F.sweat>0){g.globalAlpha=F.sweat;g.fillStyle='#8fd8ff';const sx=X(.5),sy=Y(.42);g.beginPath();g.moveTo(sx,sy-E*.6);g.quadraticCurveTo(sx+E*.38,sy,sx,sy+E*.3);g.quadraticCurveTo(sx-E*.38,sy,sx,sy-E*.6);g.fill();g.globalAlpha=1}
  if(O.shiver){g.fillStyle='rgba(150,210,255,.35)';g.fillRect(X(-.62),Y(.5),P*1.24,P*.25)}
  g.setTransform(1,0,0,1,0,0)}

/* ---------- model ---------- */
function buildMochi2(){const T=T3,k=kit(),root=new T.Group(),fc=document.createElement('canvas');fc.width=fc.height=512;const ft=new T.CanvasTexture(fc);ft.encoding=T.sRGBEncoding;ft.anisotropy=4;ft.generateMipmaps=false;ft.minFilter=T.LinearFilter;
  const U={uT:{value:0},uSq:{value:0},uMelt:{value:0},uPress:{value:0},uBreath:{value:0},uPokeT:{value:9},uPull:{value:new T.Vector3()},uShear:{value:new T.Vector3()},uPoke:{value:new T.Vector4(0,0,1,0)},uS:{value:new T.Vector4(...MOCHI2_S)},
    uFace:{value:ft},uTop:{value:new T.Color('#fff5ec')},uBot:{value:new T.Color('#ffd6e4')},uRim:{value:new T.Color('#ff9ad6')},uRim2:{value:new T.Color('#8a7bff')},uRimK:{value:1},uGal:{value:0},uKey:{value:new T.Vector3(-.45,.75,.7)},uTint:{value:new T.Color('#ffffff')},
    uFill:{value:new T.Color('#5dff9e')},uStarC:{value:new T.Color('#ffe08a')},uLit:{value:new T.Color(0,0,0)},uBounce:{value:new T.Color('#9d7bff')},uFillLv:{value:.5},uFillA:{value:0},uStar:{value:0},uGlow:{value:0}};
  const mat=new T.ShaderMaterial({uniforms:U,vertexShader:MOCHI2_V,fragmentShader:MOCHI2_F});const body=new T.Mesh(new T.SphereGeometry(1,84,60),mat);root.add(body);
  const skin=new T.MeshPhysicalMaterial({color:'#ffe6ec',roughness:.4,clearcoat:.6,clearcoatRoughness:.25,sheen:1,sheenColor:new T.Color('#ff9ad6'),emissive:new T.Color('#4a1838'),emissiveIntensity:.18});
  const arms=[-1,1].map(s=>{const g=new T.Group(),m=new T.Mesh(k.SPH,skin);m.scale.set(.115,.18,.115);m.position.y=-.13;g.add(m);root.add(g);g.userData.m=m;return g});
  const feet=[-1,1].map(()=>{const f=new T.Mesh(k.SPH,skin);f.scale.set(.17,.1,.19);root.add(f);return f});
  const ant=new T.Group(),curve=new T.CatmullRomCurve3([new T.Vector3(0,-.04,0),new T.Vector3(.005,.1,0),new T.Vector3(.035,.2,0),new T.Vector3(.08,.27,0)]);ant.add(new T.Mesh(new T.TubeGeometry(curve,20,.02,8),skin));
  const ballM=new T.MeshBasicMaterial({color:'#fff2d0',toneMapped:false}),ball=new T.Mesh(k.SPH,ballM);ball.scale.setScalar(.075);ball.position.set(.085,.31,0);ant.add(ball);root.add(ant);
  return{root,body,mat,U,ft,fc,fctx:fc.getContext('2d'),arms,feet,ant,ball,ballM,ears:[],paws:[]}}

/* ---------- the scene ---------- */
const MOCHI_ACTS=['wave','phone','juice','bird','hiccup','peekaboo','selfie','drum','spin','joy','balloon','firefly','shy','think','icecream','kiss','juggle','rain','knock','hearts','airplane','snow'];
class MochiScene extends Buddy{
  constructor(pv){super(pv,'mochi');this.snd=MOCHI2_SND;this.col='#ff9ccf';this.rimCol='#ffc2e6';this.chargeText='Drinking battery juice';this.u.S=MOCHI2_S;this.S=MOCHI2_S;this.u.bulge=.1;this.sleepMelt=.12;
    this.armS=[[.15,.55],[.15,.55]];this.armT=[[.15,.55],[.15,.55]];this.armHide=[0,0];this.paw2=[null,null];this.antA=0;this.antV=0;this.antX=0;this.antXV=0;this.pvy=0;this.pitch=0;this.pitchT=0;this.spinA=0;this.roll=0;this.lit=[0,0,0];
    this.prop=null;this.bird=null;this.fx=[];this.drops=[];this.bub=null;this.flash=0;this.gz=null;this.peekT=0;this.recent=[];this.ballCol='#fff2d0';this.nextAct=null;
    this.faceHz=22;this.loadLook();this.DUR={wave:2.6,phone:16,juice:14,bird:10,hiccup:3.6,peekaboo:3.4,selfie:4.4,drum:3.6,spin:2.2,joy:2.4,balloon:11,firefly:9,shy:3.2,think:4.4,icecream:13,kiss:2.6,juggle:5.5,rain:9,knock:3.6,hearts:3.2,airplane:3.6,snow:8}}
  build(){const M=buildMochi2();this.applyPal(M);return M}
  drawFace(g,F,t){drawMochi2Face(g,F,t,{shiver:this.shiver,skin:this.pal.lid,ink:this.pal.ink})}
  resize(w,h){super.resize(w,h);this.U=Math.min(w*.4,h*.235);this.floor=h*.72;if(!this.toss)this.y=this.floor}
  sfx(n){if(!this.preview&&!WP&&this.snd[n])this.snd[n]()}
  controls(){const go=a=>()=>{this.forceAct(a)};return[...this.lookControls(),{type:'buttons',label:'Tricks',items:[{name:'✦ Surprise',fn:()=>this.forceAct(null)},{name:'Phone',fn:go('phone')},{name:'Juice',fn:go('juice')},{name:'Bird',fn:go('bird')},{name:'Ice cream',fn:go('icecream')},{name:'Rain',fn:go('rain')}]}]}
  forceAct(a){if(this.jState!=='mochi'||this.charging)return;this.wake=40;this.prop=null;this.bird=null;this.act=null;this.pickAct(a)}
  /* ----- items Mochi holds (juice box, phone, ice cream) can be taken away ----- */
  itemOn(){return this.charging||!!(this.prop&&this.prop.grab&&!this.prop.away)}
  jarScreen(){if(this.charging)return super.jarScreen();const p=this.prop;if(!p||!p.grab||p.away||this.jState!=='mochi')return null;return p.pos||null}
  grabBack(){if(this.charging){super.grabBack();return}const p=this.prop;this.jState='mochi';this.tears.length=0;haptic(18);this.sfx('yay');this.bub={txt:'♥',life:1.4,col:'#ff3d7f'};if(p){p.happy=1.6;this.act=p.act}}
  startAct(type){if(this.prop&&this.jState!=='mochi'&&!this.charging)return;super.startAct(type);const ac=this.act;if(!ac)return;
    if(['phone','juice','icecream'].includes(type)){this.prop={type,grab:true,act:ac,s:0,left:1,happy:0};ac.side=1}
    if(type==='selfie')this.prop={type:'selfie',act:ac,s:0};if(type==='balloon')this.prop={type:'balloon',act:ac,s:0,bx:null,by:null,bvx:0,bvy:0,pop:0};if(type==='rain')this.prop={type:'umbrella',act:ac,s:0};
    if(type==='bird'){const d=Math.random()<.5?-1:1;this.bird={x:d<0?this.w+60:-60,y:this.h*rnd(.12,.25),vx:0,vy:0,dir:-d,st:'in',t:0,flap:0,chirpT:1}}
    if(type==='firefly')this.ffly={x:rnd(this.w*.15,this.w*.85),y:this.h*.3,st:'fly'};if(type==='snow')this.snowF=[...Array(26)].map(()=>({x:rnd(this.w),y:rnd(-this.h*.6,0),v:rnd(30,60),r:rnd(.02,.04),ph:rnd(TAU)}));
    if(type==='juggle')this.jug=[0,1,2].map(i=>({ph:i/3}));if(type==='hiccup')ac.n=0;if(type==='knock')ac.n=0;if(type==='drum')ac.n=0;if(type==='joy')ac.n=0}
  pickAct(force){if(this.prop&&this.jState!=='mochi')return;const m=this.mood;
    const P={day:['phone','juice','bird','phone','juice','bird','wave','hiccup','peekaboo','selfie','drum','spin','joy','balloon','firefly','shy','think','icecream','kiss','juggle','rain','knock','hearts','airplane','snow','lookAround','bubble','sneeze','press','stargaze'],
      evening:['phone','juice','bird','firefly','balloon','kiss','think','shy','snow','icecream','stargaze','yawn','wave','hearts','rain'],wakeup:['stretch','yawn','juice','phone','wave','nod','bird'],late:['yawn','nod','phone','firefly','stargaze','snow'],low:['sigh']};
    let pool=P[m]||P.day;if(force===undefined&&!pool)return;if(force){this.startAct(force);return}
    if(force===undefined&&this.idleT>24&&(m==='day'||m==='evening')){this.startAct(Math.random()<.5?'press':'knock');this.idleT=6;return}
    let a,n=0;do{a=pool[(Math.random()*pool.length)|0];n++}while(this.recent.includes(a)&&n<12);this.recent.push(a);if(this.recent.length>6)this.recent.shift();this.startAct(a)}
  /* ----- input ----- */
  down(p){const sleeping=this.mood==='sleep';this.gz={p:[p.x,p.y],t:this.t};
    if(this.bird&&this.bird.st!=='out'&&Math.hypot(p.x-this.bird.x,p.y-this.bird.y)<this.U*.3){this.bird.st='out';this.bird.t=0;this.sfx('chirp');return}
    const pr=this.prop;if(pr&&pr.type==='balloon'&&pr.bx!=null&&!pr.pop&&Math.hypot(p.x-pr.bx,p.y-pr.by)<this.U*.32){pr.pop=1;this.burst(pr.bx,pr.by,'#ff5c8a',14);this.sfx('pop');haptic(20);this.sqv-=3;this.startPopSad();return}
    if(sleeping&&!this.onBody(p.x,p.y)){this.peekT=2.2;return}
    super.down(p)}
  startPopSad(){const ac=this.act;if(ac&&ac.type==='balloon'){ac.popAt=ac.t}}
  /* ----- behaviour (runs inside the base update via hooks) ----- */
  update(dt,A){this.armT=[[.12+Math.sin(this.t*1.3)*.04,.55],[.12+Math.sin(this.t*1.3+1)*.04,.55]];this.paw2=[null,null];this.focus=null;this.pitchT=0;this.spinT=0;this.rollT=0;this.litT=[0,0,0];this.shiver=0;this.ballT=null;
    this.peekT=Math.max(0,this.peekT-dt);if(this.charging&&this.prop){this.prop=null;this.jState='mochi'}super.update(dt,A);
    // a prop whose act finished gets put away
    const p=this.prop;if(p){p.happy=Math.max(0,(p.happy||0)-dt);const live=this.act===p.act||this.jState!=='mochi';p.s=clamp(p.s+(live&&!p.away?dt*3:-dt*3),0,1);if(!live)p.away=true;if(p.away&&p.s<=0)this.prop=null}
    if(this.bird)this.updBird(dt);
    for(let i=this.fx.length-1;i>=0;i--){const q=this.fx[i];q.life-=dt;q.vy+=(q.g||0)*dt;q.x+=q.vx*dt;q.y+=q.vy*dt;if(q.life<=0)this.fx.splice(i,1)}
    if(this.bub){this.bub.life-=dt;if(this.bub.life<=0)this.bub=null}this.flash=Math.max(0,this.flash-dt*2.5);
    // antenna spring
    const dv=(this.vy-this.pvy)/this.h;this.pvy=this.vy;this.antV+=dv*14;if(A.active&&A.beat)this.antV+=(this.beatN%2?1:-1)*4*A.bass;
    this.antV+=(-(this.antA+this.u.shear[0]*1.4+this.yaw*.5)*90-this.antV*5)*dt;this.antA+=this.antV*dt;this.antXV+=(-(this.antX-this.pitch*.6)*80-this.antXV*5)*dt+dv*6;this.antX+=this.antXV*dt;
    for(let i=0;i<2;i++){for(let j=0;j<2;j++)this.armS[i][j]=lerp(this.armS[i][j],this.armT[i][j],Math.min(1,dt*11));this.armHide[i]=lerp(this.armHide[i],this.paw2[i]?1:0,Math.min(1,dt*10))}
    this.pitch=lerp(this.pitch,this.pitchT,Math.min(1,dt*6));this.roll=lerp(this.roll,this.rollT,Math.min(1,dt*6));for(let i=0;i<3;i++)this.lit[i]=lerp(this.lit[i],this.litT[i],Math.min(1,dt*6))}
  arm(i,out,fwd){this.armT[i]=[out,fwd]}
  hold2(){this.arm(0,-.35,1.0);this.arm(1,-.35,1.0)}
  moodHook(m,E,dt,A){if(m==='sleep'&&this.peekT>0){E.eye='peek';E.open=.55;const g=this.gz;if(g){const dx=g.p[0]-this.x,dy=g.p[1]-(this.y-this.U);const d=Math.hypot(dx,dy)+1;E.lookT=[dx/d,dy/d]}}
    if(m==='sleep'){this.ballT='#7d6fb0';this.arm(0,.15,.5);this.arm(1,.15,.5)}
    if(m==='charge'){this.hold2();this.ballT='#7dffb0'}
    if(m==='low'){this.arm(0,.05,.1);this.arm(1,.05,.1);this.ballT='#ff8a6b'}
    if(m==='dance')this.dance(E,dt,A)}
  dance(E,dt,A){const b=this.beatN,ph=A.beatPhase||0,bell=Math.sin(ph*Math.PI),mv=Math.floor(b/8)%6,s=b%2?1:-1,mid=A.mid||0;
    E.eye=b%16<2?'note':b%16<4?'star':'happy';E.mouth='open';E.mo=clamp(.2+mid*1.2,.2,1);E.blush=1;this.ballT=`hsl(${(b*40+ph*40)%360},100%,75%)`;
    if(mv===0){this.arm(0,.5+1.6*(s<0?bell:0),.3);this.arm(1,.5+1.6*(s>0?bell:0),.3)}
    else if(mv===1){const sw=Math.sin((b+ph)*Math.PI);this.arm(0,1.1+sw*.6,.4);this.arm(1,1.1-sw*.6,.4);E.yawT=sw*.25;this.rollT=sw*.12}
    else if(mv===2){const up=b%2?2.7:1.2;this.arm(0,up-bell*.3,.2);this.arm(1,up-bell*.3,.2);E.mouth='grin'}
    else if(mv===3){const tw=Math.sin((b+ph)*Math.PI);E.yawT=tw*.45;this.arm(0,.9-tw*.7,.6);this.arm(1,.9+tw*.7,.6)}
    else if(mv===4){this.spinT=1;this.spinA=((b%2)+ph)/2*TAU;this.arm(0,1.5,.1);this.arm(1,1.5,.1)}
    else{this.pitchT=bell*.18;this.arm(0,.4,1-bell*.6);this.arm(1,.4,1-bell*.6);E.eye='closed';E.open=0}
    if(A.beat&&Math.random()<.5){const c=this.headScr();this.fx.push({k:'note',x:c[0]+rnd(-this.U*.6,this.U*.6),y:c[1]-this.U*.2,vx:rnd(-20,20),vy:-rnd(60,110),life:1.4,c:['#cdb4ff','#ffb36b','#7dffb0','#ff9ecf'][b%4]})}
  }
  actHook(T2,k,ac,E,dt,A){const U=this.U,t=ac.t,s=this.sfx.bind(this);
    switch(T2){
    case'stretch':this.arm(0,2.7,.1);this.arm(1,2.7,.1);break;
    case'yawn':this.paw2[1]={pt:[0,-.08,1],r:.16};break;
    case'happy':case'love':case'greet':this.arm(0,.9+Math.sin(t*14)*.3,.3);this.arm(1,.9-Math.sin(t*14)*.3,.3);break;
    case'giggle':this.paw2[1]={pt:[.05,-.1,1],r:.15};break;
    case'wave':{this.arm(1,2.55+Math.sin(t*12)*.35,.35);E.eye='happy';E.mouth='open';E.mo=.4;E.blush=1;if(!ac.said){ac.said=1;this.bub={txt:'Hi!',life:1.8}}break}
    case'phone':case'juice':case'icecream':this.propAct(T2,k,ac,E,dt);break;
    case'selfie':{this.arm(1,2.2,.65);this.arm(0,.6,.6);const tt=k;E.lookT=[.5,-.6];this.focus=null;
      if(tt<.45){E.eye='normal';E.mouth='smile'}else if(tt<.6){E.eye='wink';E.mouth='tongue';E.mo=.2;if(!ac.snap&&tt>.5){ac.snap=1;this.flash=1;s('shutter');haptic(10)}}else{E.eye='happy';E.mouth='open';E.mo=.4;E.blush=1.2}this.litT=[.25,.22,.35];break}
    case'hiccup':{const hs=[.12,.42,.72];for(let i=0;i<3;i++)if(k>hs[i]&&ac.n===i){ac.n++;this.sqv+=3;if(this.ground){this.vy=-this.h*.12;this.ground=false}s('hic');this.bub={txt:'hic!',life:.6}}
      E.eye=hs.some(h=>k>h&&k<h+.08)?'dot':'normal';E.mouth='o';E.mo=.15;E.blush=.9;this.paw2[0]={pt:[-.05,-.12,1],r:.14};break}
    case'peekaboo':{if(k<.68){this.paw2[0]={pt:[-.3,.16,.95],r:.25};this.paw2[1]={pt:[.3,.16,.95],r:.25};E.eye='closed';E.open=0;E.mouth='smile';E.blush=1.1;this.rollT=Math.sin(t*3)*.05;this.arm(0,-.4,1.2);this.arm(1,-.4,1.2)}
      else{this.arm(0,2.4,.4);this.arm(1,2.4,.4);E.eye='wow';E.mouth='open';E.mo=.9;if(!ac.boo){ac.boo=1;this.sqv-=4;this.bub={txt:'boo!',life:1};s('boo');haptic(14)}}break}
    case'drum':{const n=Math.floor(k*8);const hit=n%2;this.arm(0,-.3,hit?.4:1.1);this.arm(1,-.3,hit?1.1:.4);if(n>ac.n){ac.n=n;this.sqv-=1.6;s('boom');this.fx.push({k:'note',x:this.x+rnd(-U*.5,U*.5),y:this.y-U*1.2,vx:0,vy:-80,life:1.2,c:'#ffb36b'})}E.eye='happy';E.mouth='open';E.mo=.3+.3*hit;break}
    case'spin':{this.spinT=1;this.arm(0,1.5,0);this.arm(1,1.5,0);E.eye=k<.8?'happy':'spiral';E.mouth='open';E.mo=.6;if(!ac.w){ac.w=1;s('whee')}this.spinA=ease.inOut(clamp(k/.8,0,1))*TAU;break}
    case'joy':{if(k>ac.n*.5&&ac.n<2&&this.ground){ac.n++;this.vy=-this.h*.3;this.ground=false;this.burst(this.x,this.y-U*1.9,'#ffe08a',10);s('yay')}this.arm(0,2.7,.2);this.arm(1,2.7,.2);E.eye='star';E.mouth='open';E.mo=.8;E.blush=1.2;break}
    case'balloon':this.balloonAct(k,ac,E,dt);break;
    case'bird':this.birdAct(k,ac,E,dt);break;
    case'firefly':this.fireflyAct(k,ac,E,dt);break;
    case'shy':{this.paw2[0]={pt:[-.42,.0,.85],r:.17};this.paw2[1]={pt:[.42,.0,.85],r:.17};E.eye='happy';E.mouth='smile';E.blush=1.8;this.rollT=Math.sin(t*4)*.1;E.yawT=Math.sin(t*2)*.2;if(Math.random()<dt*3)this.fx.push({k:'heart',x:this.x+rnd(-U*.5,U*.5),y:this.y-U*1.7,vx:0,vy:-50,life:1.3,c:'#ff7fae'});break}
    case'think':{this.paw2[1]={pt:[.22,-.34,.92],r:.15};if(k<.65){E.lookT=[.6,-.7];E.eye='normal';E.mouth='pout';E.brow=.4;E.browOn=true;this.ballT='#5a4f7a';if(!ac.q){ac.q=1;this.bub={txt:'?',life:2.2}}}
      else{E.eye='wow';E.mouth='open';E.mo=.6;this.ballT='#fff3a0';this.ballBoost=1;if(!ac.idea){ac.idea=1;s('ding');this.bub={txt:'!',life:1.3,col:'#ffb36b'};this.sqv-=2;if(this.ground){this.vy=-this.h*.12;this.ground=false}this.burst(...this.ballScr(),'#fff3a0',10)}this.arm(0,2.3,.3)}break}
    case'kiss':{if(k<.45){this.paw2[1]={pt:[.06,-.06,1],r:.14};E.eye='closed';E.open=0;E.mouth='kiss'}else{this.arm(1,1.6,1.3);E.eye='wink';E.mouth='smile';E.blush=1.4;if(!ac.k){ac.k=1;s('kiss');haptic(12);const c=this.sp([0,-.05,1]);this.fx.push({k:'bigheart',x:c[0],y:c[1],vx:0,vy:-30,life:1.4})}}break}
    case'juggle':{const hn=this.handsScr();if(hn){for(const j of this.jug){j.ph+=dt*.55}}this.arm(0,.5+Math.max(0,Math.sin(t*7))*.8,.9);this.arm(1,.5+Math.max(0,Math.sin(t*7+Math.PI))*.8,.9);E.lookT=[0,-.6];E.eye='wow';E.mouth='o';E.mo=.2;
      if(k>.85&&!ac.bonk){ac.bonk=1;this.sqv+=3;s('boing');haptic(10)}if(k>.85){E.eye='spiral';E.mouth='open';E.mo=.4}break}
    case'rain':this.rainAct(k,ac,E,dt);break;
    case'knock':{this.arm(1,.2,1.5+Math.max(0,Math.sin(t*12))*.25);E.eye=k<.75?'wow':'happy';E.lookT=[0,0];E.mouth=k<.75?'o':'smile';E.mo=.2;this.pitchT=-.04;this.u.press=Math.max(this.u.press,.25);
      const n=Math.floor(k*10);if(n>ac.n&&n<8&&n%2===0){ac.n=n;s('knock');haptic(8);const c=this.hand(1);if(c)this.fx.push({k:'ring',x:c[0],y:c[1],vx:0,vy:0,life:.6})}if(n>ac.n)ac.n=n;break}
    case'hearts':{this.arm(0,2.75,.35);this.arm(1,2.75,.35);E.eye='heart';E.mouth='smile';E.blush=1.4;ac.heart=1;break}
    case'airplane':{this.arm(0,1.55,.0);this.arm(1,1.55,.0);this.rollT=Math.sin(t*3)*.3;E.yawT=Math.sin(t*3)*.25;E.eye='happy';E.mouth='open';E.mo=.5;if(!ac.w){ac.w=1;s('whee');SFX&&!this.preview&&!WP&&SFX.whoosh()}if(Math.random()<dt*14){const c=this.hand(Math.random()<.5?0:1);if(c)this.fx.push({k:'streak',x:c[0],y:c[1],vx:-Math.sin(t*3)*120,vy:0,life:.4})}break}
    case'snow':{E.lookT=[0,-.85];this.pitchT=-.12;E.eye='wow';E.mouth='tongue';E.mo=.4+.3*Math.sin(t*4);E.sparkle=1;this.arm(0,1.2,.5);this.arm(1,1.2,.5);if(k>.5&&!ac.c){ac.c=1;s('burr');haptic(8)}if(k>.5&&k<.7){E.eye='squeeze';E.mouth='smile';this.shiver=1;this.shv[0]+=Math.sin(t*60)*dt*2}break}
    case'sneeze':if(k<.45)this.paw2[1]={pt:[.06,-.08,1],r:.15};break}}
  propAct(T2,k,ac,E,dt){const t=ac.t,p=this.prop;if(!p)return;const happy=p.happy>0;
    if(T2==='phone'){this.hold2();E.lookT=[0,.55];this.pitchT=.1;this.litT=[.35,.25,.5];const ph=(t%7)/7;
      if(ph<.35){E.eye='normal';E.mouth='o';E.mo=.1}else if(ph<.55){E.eye='wow';E.mouth='open';E.mo=.3;E.sparkle=1}else if(ph<.8){E.eye='happy';E.mouth='open';E.mo=.4+.3*Math.sin(t*28);if(!ac['g'+Math.floor(t/7)]){ac['g'+Math.floor(t/7)]=1;this.sfx('giggle')}this.shv[0]+=Math.sin(t*40)*dt}else{E.eye='normal';E.mouth='smile'}}
    if(T2==='juice'){this.hold2();E.lookT=[0,.15];const ph=t%2.6;if(ph<1.5){E.mouth='o';E.mo=.05;E.eye='closed';E.open=0;E.blush=1.2;p.left=Math.max(.15,p.left-dt*.03);if(ph<dt*1.2)this.sfx('sip');if(Math.abs(ph-1.2)<dt){this.sqv-=1;}}else{E.eye='happy';E.mouth='smile';E.blush=1.3;if(ph<1.5+dt*1.2&&Math.random()<.5)this.fx.push({k:'heart',x:this.x+rnd(-this.U*.4,this.U*.4),y:this.y-this.U*1.8,vx:0,vy:-50,life:1.2,c:'#ff9ecf'})}}
    if(T2==='icecream'){this.arm(1,-.35,1.25);this.arm(0,.35,.4);E.lookT=[.15,.4];const ph=t%2.2;
      if(k>.82){E.eye='squeeze';E.mouth='wobble';E.mo=.2;this.shiver=1;this.shv[0]+=Math.sin(t*50)*dt*2;if(!ac.freeze){ac.freeze=1;this.bub={txt:'brrr',life:1.4,col:'#7fd8ff'}}}
      else if(ph<.9){E.mouth='tongue';E.mo=.6+.4*Math.sin(t*10);E.eye='happy';p.left=Math.max(.08,p.left-dt*.07);if(ph<dt*1.2)this.sfx('lick')}else{E.eye='normal';E.mouth='cat';E.blush=1}}
    if(happy){E.eye='heart';E.mouth='smile';E.blush=1.4}}
  balloonAct(k,ac,E,dt){const p=this.prop,U=this.U;this.arm(1,1.1,.4);if(!p)return;const h=this.hand(1);if(!h)return;
    if(p.bx==null){p.bx=h[0];p.by=h[1];}const free=p.pop||k>.82;
    if(!free){const tx=Math.min(this.w-U*.35,h[0]+U*.1)+Math.sin(ac.t*1.3)*U*.1,ty=h[1]-U*1.1,kk=Math.min(1,dt*4);p.bx=lerp(p.bx,tx,kk);p.by=lerp(p.by,ty,kk)}else if(!p.pop){p.bvy=Math.max(-U*2.2,p.bvy-U*1.2*dt);p.bx+=Math.sin(ac.t*2)*U*.3*dt;p.by+=p.bvy*dt}E.focus=1;this.focus=[p.bx,p.by];
    if(p.pop){const pk=ac.t-(ac.popAt||ac.t);E.eye=pk<.5?'wow':'teary';E.mouth=pk<.5?'o':'wobble';E.mo=.4;E.blush=1;this.arm(1,1.6,.6);if(pk>2.4)ac.t=ac.dur}
    else if(k>.82){E.eye='teary';E.mouth='wobble';this.arm(1,2.5+Math.sin(ac.t*12)*.3,.3)}else{E.eye='happy';E.mouth='smile';E.blush=1.2}}
  birdAct(k,ac,E,dt){const b=this.bird;if(!b)return;this.focus=[b.x,b.y];E.eye='heart';E.mouth='open';E.mo=.35+.15*Math.sin(ac.t*6);E.blush=1.5;E.sparkle=1;this.ballT='#ffb3d9';
    if(b.st==='perch'){this.pitchT=-.1;this.arm(0,.8+Math.sin(ac.t*8)*.15,.5);this.arm(1,.8-Math.sin(ac.t*8)*.15,.5)}if(b.st==='out'&&b.t<1.6){E.eye='happy';E.mouth='smile';this.arm(1,2.5+Math.sin(ac.t*12)*.3,.3)}
    if(Math.random()<dt*2.5)this.fx.push({k:'heart',x:this.x+rnd(-this.U*.6,this.U*.6),y:this.y-this.U*1.6,vx:rnd(-15,15),vy:-55,life:1.4,c:Math.random()<.5?'#ff7fae':'#ffb3d9'});
    if(k>.97&&b.st!=='out'){b.st='out';b.t=0}}
  updBird(dt){const b=this.bird,U=this.U;b.t+=dt;b.flap+=dt*(b.st==='perch'?0:22);
    if(b.st==='in'){const tp=this.ballScr();const tx=tp[0],ty=tp[1]-U*.2;const k=Math.min(1,b.t/2.6);const cx=lerp(b.x,tx,.02+k*.08),cy=lerp(b.y,ty,.02+k*.08)+Math.sin(b.t*5)*U*.02*(1-k);b.dir=tx>b.x?1:-1;b.x=cx;b.y=cy;if(Math.hypot(tx-b.x,ty-b.y)<U*.03||b.t>3.6){b.st='perch';b.t=0;this.sfx('chirp')}}
    else if(b.st==='perch'){const tp=this.ballScr();b.x=tp[0];b.y=tp[1]-U*.2;b.chirpT-=dt;if(b.chirpT<0){b.chirpT=rnd(.9,1.6);this.sfx('chirp');this.fx.push({k:'note',x:b.x+U*.1,y:b.y-U*.15,vx:30,vy:-50,life:1.2,c:'#7fd8ff'})}if(b.t>4.2||(this.act&&this.act.type!=='bird')){b.st='out';b.t=0}}
    else{b.vx=lerp(b.vx,b.dir*this.w*.9,.04);b.vy=lerp(b.vy,-this.h*.35,.04);b.x+=b.vx*dt;b.y+=b.vy*dt;if(b.y<-80||b.x<-80||b.x>this.w+80)this.bird=null}}
  fireflyAct(k,ac,E,dt){const f=this.ffly,U=this.U,t=ac.t;if(!f)return;const hc=this.handsMid();
    if(k<.55){f.x=this.x+Math.sin(t*1.3)*U*1.1+Math.sin(t*3.1)*U*.25;f.y=this.y-U*1.3+Math.cos(t*1.7)*U*.7;f.st='fly';this.focus=[f.x,f.y];E.eye='wow';E.mouth='o';E.mo=.15;E.sparkle=1;if(k>.45)this.hold2()}
    else if(k<.82){f.st='held';if(hc){f.x=hc[0];f.y=hc[1]}this.hold2();E.lookT=[0,.7];this.pitchT=.12;E.eye=k<.62?'squeeze':'wow';E.mouth=k<.62?'smile':'o';this.litT=[.5,.45,.1];if(!ac.c){ac.c=1;this.sqv-=2;this.sfx('pop');haptic(10)}}
    else{if(f.st!=='go'){f.st='go';this.burst(f.x,f.y,'#fff3a0',8)}f.y-=U*1.4*dt;f.x+=Math.sin(t*4)*U*1.2*dt;this.focus=[f.x,f.y];E.eye='happy';E.mouth='open';E.mo=.4;this.arm(1,2.4+Math.sin(t*12)*.3,.3)}}
  rainAct(k,ac,E,dt){const U=this.U,c=this.headScr();ac.cx=c[0];ac.cy=c[1]-U*.75;const up=k>.32&&k<.9;
    if(k<.9&&Math.random()<dt*30)this.drops.push({x:ac.cx+rnd(-U*.42,U*.42),y:ac.cy+U*.1,vy:rnd(320,420)});
    if(k<.32){E.eye='teary';E.mouth='wobble';E.mo=.1;E.lookT=[0,-.7];this.arm(0,.2,.3);this.arm(1,.2,.3);if(!ac.s){ac.s=1;this.sfx('sob')}}
    else{this.arm(1,1.9,.5);E.eye=k<.4?'wow':'happy';E.mouth='smile';E.blush=1.1;if(this.prop)this.prop.s=Math.min(1,(k-.32)/.08)}
    if(k>.9&&this.prop)this.prop.away=true;ac.umb=up}
  /* ----- screen positions ----- */
  sp(p){const g=this.gl;if(!g)return this.scr(p);const q=mochiDeform(p,this.u);return this.projW(this.M.root.localToWorld(new T3.Vector3(q[0],q[1],q[2])))}
  headScr(){return this.sp([0,1,0])}
  ballScr(){const g=this.gl;if(!g)return this.scr([.1,1.2,0]);return this.projW(this.M.ball.getWorldPosition(new T3.Vector3()))}
  hand(i){const g=this.gl;if(!g)return null;return this.projW(this.M.arms[i].userData.m.localToWorld(new T3.Vector3(0,-.85,.2)))}
  handsMid(){const a=this.hand(0),b=this.hand(1);return a&&b?[(a[0]+b[0])/2,(a[1]+b[1])/2]:null}
  handsScr(){return this.handsMid()}
  burst(x,y,c,n){for(let i=0;i<n;i++){const a=rnd(TAU),v=rnd(80,220);this.fx.push({k:'star',x,y,vx:Math.cos(a)*v,vy:Math.sin(a)*v-60,g:260,life:rnd(.6,1.1),c,r:rnd(.03,.06)})}}
  postHook(E,dt,A){const m=this.mood,U=this.U;
    // eye contact: wherever you touch, Mochi looks there (eyes + head turn), like Tom spotting Jerry
    let tg=null;const P=this.ptr;if(P&&!this.hold&&!P.juice)tg=[P.x,P.y];else if(this.gz&&this.t-this.gz.t<2.4)tg=this.gz.p;if(this.focus&&!P)tg=this.focus;
    const chasing=this.itemOn()&&(this.jState==='user'||this.jState==='drop');
    if(tg&&m!=='sleep'&&!chasing&&!this.toss){const hx=this.x,hy=this.y-U*1.05,dx=tg[0]-hx,dy=tg[1]-hy,d=Math.hypot(dx,dy)+1,r=Math.max(d,U*.7);E.lookT=[clamp(dx/r*1.15,-1,1),clamp(dy/r*1.15,-1,1)];E.yawT=clamp(dx/(this.w*.5),-1,1)*.45;this.pitchT=clamp(dy/(this.h*.5),-1,1)*.2;
      if(P&&!this.act&&m!=='dance'&&E.eye==='normal'){E.eye='wow';E.sparkle=1}}
    if(chasing&&!this.charging){E.eye='cry';E.mouth='wobble';E.mo=.8;const gp=this.jx-this.x;this.arm(0,2.1+Math.sin(this.t*16)*.3,.6+(gp<0?.3:0));this.arm(1,2.1+Math.sin(this.t*16+2)*.3,.6+(gp>0?.3:0))}
    if(chasing&&this.charging){this.arm(0,2.2+Math.sin(this.t*16)*.3,.6);this.arm(1,2.2+Math.sin(this.t*16+2)*.3,.6)}
    if(this.hold&&this.hold.t>.45&&Math.hypot(...this.u.pull)<.1){this.arm(0,1.1,.5);this.arm(1,1.1,.5)}
    if(this.dz>0){this.arm(0,1.2+Math.sin(this.t*9)*.4,.2);this.arm(1,1.2-Math.sin(this.t*9)*.4,.2)}}
  /* ----- pose: arms, feet, antenna, jar ----- */
  place(dt,A){const g=this.gl,M=this.M,u=this.u,Uf=M.U,T=T3,w=this.w,h=this.h;
    M.root.rotation.x+=this.pitch;M.root.rotation.y+=this.spinA;M.root.rotation.z+=this.roll;if(!(this.act&&this.act.type==='spin')&&this.spinT===0)this.spinA=this.spinA>Math.PI?lerp(this.spinA,TAU,.12):lerp(this.spinA,0,.12);if(Math.abs(this.spinA-TAU)<.01)this.spinA=0;
    for(const[i,f]of M.feet.entries()){const s=i?1:-1,p=mochiDeform([s*.45,-.92,.55],u);f.position.set(p[0],.07+Math.max(0,p[1]-.05)*.3,p[2]+.02);f.rotation.z=-s*.2}
    for(const[i,a]of M.arms.entries()){const s=i?1:-1,p=mochiDeform([s*.74,-.1,.66],u),[out,fwd]=this.armS[i];a.position.set(p[0]-s*.02,p[1],p[2]-.03);a.rotation.set(-fwd,0,s*out,'XYZ');const hs=1-this.armHide[i]*.85;a.scale.setScalar(hs)}
    const tp=mochiDeform([.1,1,.08],u);M.ant.position.set(tp[0],tp[1]-.02,tp[2]);M.ant.rotation.set(this.antX,0,-.1+this.antA);
    const bc=this.ballT||(this.act&&['love','greet','happy'].includes(this.act.type)?'#ffb3d9':'#fff2d0');M.ballM.color.lerp(new T.Color(bc),.08);this.ballCol='#'+M.ballM.color.getHexString();M.ball.scale.setScalar(.075*(1+(A.active?A.bass*.35:0)+(this.ballBoost||0)*.3));this.ballBoost=Math.max(0,(this.ballBoost||0)-dt);
    this.lookTick();Uf.uLit.value.setRGB(...this.lit);const ph=this.force||dayPhase();Uf.uBounce.value.set(this.charging?'#3dff7a':A.active?`hsl(${(this.beatN*40)%360},90%,60%)`:this.pal.glow);Uf.uFillA.value=0;
    M.root.updateMatrixWorld(true);
    // battery-juice jar while charging (3D), held in both arms, straw to the mouth
    if(this.charging&&!g.jar){g.jar=makeJar();(this.world||g.sc).add(g.jar.g);this.jState='mochi'}
    if(g.jar){const J=g.jar,G=J.g,sz=this.U*this.scale;J.s=lerp(J.s,this.charging?1:0,.1);G.visible=J.s>.02;const kk=ease.outBack(clamp(J.s,0,1));J.setLevel(HSTATE.charging?HSTATE.battery:.45+.4*((this.t*.02)%1),this.t);
      if(this.jState==='mochi'){G.position.copy(M.root.localToWorld(new T.Vector3(0,.04-this.gulp*.015,.72)));G.quaternion.copy(M.root.quaternion);G.scale.setScalar(sz*kk*1.25)}
      else{G.position.set(this.jx-w/2,h/2-this.jy-J.H*.6*sz,sz*1.1);G.rotation.set(0,0,this.jTilt||0);G.scale.setScalar(sz*kk*1.25)}
      G.updateMatrixWorld(true);let end;if(this.jState==='mochi'){const mp=mochiDeform([0,-.06,1],u);end=G.worldToLocal(M.root.localToWorld(new T.Vector3(mp[0],mp[1]-.01,mp[2]+.02)))}else end=new T.Vector3(.14,J.H+.3,0);J.straw(end);
      if(!this.charging&&J.s<.02){(this.world||g.sc).remove(G);g.jar=null;this.jState='mochi'}}
    // where the held prop is on screen (used for grabbing it)
    const p=this.prop;if(p&&p.grab&&this.jState==='mochi'){const hm=this.handsMid(),hr=this.hand(1);p.pos=p.type==='icecream'?(hr?[hr[0],hr[1]-this.U*.2]:null):hm?[hm[0],hm[1]+this.U*(p.type==='juice'?.02:.1)]:null}}
  /* ----- draw: reflection on the glossy floor, grid, props, bird, effects ----- */
  reflect(ctx){const g=this.gl;if(!g)return;const pr=g.r.getPixelRatio(),cv=g.cv,fl=this.floor,Hs=Math.min(fl,this.U*2.3);const R=Math.min(this.h-fl,Hs*.5);if(R<4)return;
    const rc=this.rc||(this.rc=document.createElement('canvas')),rw=Math.max(2,Math.round(this.w/2)),rh=Math.max(2,Math.round(R/2));if(rc.width!==rw||rc.height!==rh){rc.width=rw;rc.height=rh}const x=rc.getContext('2d');
    x.setTransform(1,0,0,1,0,0);x.globalCompositeOperation='copy';x.setTransform(1,0,0,-1,0,rh);try{x.drawImage(cv,0,(fl-Hs)*pr,cv.width,Hs*pr,0,0,rw,rh)}catch(e){return}
    x.setTransform(1,0,0,1,0,0);x.globalCompositeOperation='destination-in';const gr=x.createLinearGradient(0,0,0,rh);gr.addColorStop(0,'rgba(0,0,0,.3)');gr.addColorStop(.55,'rgba(0,0,0,.06)');gr.addColorStop(1,'rgba(0,0,0,0)');x.fillStyle=gr;x.fillRect(0,0,rw,rh);x.globalCompositeOperation='source-over';
    ctx.drawImage(rc,0,fl-1,this.w,R)}
  drawSparkles(ctx,front){if(front)return;super.drawSparkles(ctx,false);super.drawSparkles(ctx,true)}
  drawFloor(ctx){super.drawFloor(ctx);const w=this.w,h=this.h,hz=this.floor-this.U*.28,col=this.charging?'93,255,140':'190,160,255';ctx.lineWidth=1;
    for(let i=1;i<9;i++){const v=Math.pow(i/9,1.6),y=hz+(h-hz)*v;ctx.strokeStyle=`rgba(${col},${.05+v*.08})`;ctx.beginPath();ctx.moveTo(0,y);ctx.lineTo(w,y);ctx.stroke()}
    for(let i=-8;i<=8;i++){const P0=this.floorP(i*.12,0),P1=this.floorP(i*.12*1.0,1);ctx.strokeStyle=`rgba(${col},.07)`;ctx.beginPath();ctx.moveTo(P0[0],P0[1]);ctx.lineTo(P1[0],P1[1]);ctx.stroke()}}
  over(ctx){const U=this.U,t=this.t,ac=this.act,p=this.prop;
    // antenna bulb glow
    if(this.gl){const b=this.ballScr();ctx.globalCompositeOperation='lighter';glowM(ctx,this.ballCol.length===7?this.ballCol:'#fff2d0',b[0],b[1],U*(.22+(this.A&&this.A.active?this.A.bass*.15:0)+(this.ballBoost||0)*.2),.55);ctx.globalCompositeOperation='source-over'}
    // rain cloud + drops (behind the umbrella)
    if(ac&&ac.type==='rain'&&ac.cx!=null){const k=ac.t/ac.dur,a=k<.08?k/.08:k>.88?Math.max(0,1-(k-.88)/.12):1;drawCloud(ctx,ac.cx,ac.cy-U*.15,U,a*.95,true);
      if(k>.88){ctx.save();ctx.globalAlpha=Math.min(1,(k-.88)/.06)*.5;const cx=this.x,cy=this.y-U*.6;['#ff6b6b','#ffb36b','#ffe66b','#7dffb0','#7fd8ff','#b48cff'].forEach((c,i)=>{ctx.strokeStyle=c;ctx.lineWidth=U*.04;ctx.beginPath();ctx.arc(cx,cy,U*(1.5-i*.05),Math.PI*1.1,Math.PI*1.9);ctx.stroke()});ctx.restore()}}
    for(let i=this.drops.length-1;i>=0;i--){const d=this.drops[i];d.y+=d.vy*dtFrame;const umb=ac&&ac.umb,lim=umb?this.y-U*2.35:this.y-U*1.4;if(d.y>lim){if(Math.random()<.5)this.fx.push({k:'dot',x:d.x,y:lim,vx:rnd(-60,60),vy:-rnd(40,90),g:500,life:.3,c:'#9fdcff'});this.drops.splice(i,1);continue}ctx.strokeStyle='rgba(160,215,255,.75)';ctx.lineWidth=1.5;ctx.beginPath();ctx.moveTo(d.x,d.y);ctx.lineTo(d.x-1,d.y-U*.08);ctx.stroke()}
    // snow
    if(ac&&ac.type==='snow'&&this.snowF){const k=ac.t/ac.dur;for(const f of this.snowF){f.y+=f.v*dtFrame;f.x+=Math.sin(t*1.5+f.ph)*.4;if(f.y>this.h&&k<.85)f.y=-10;ctx.globalAlpha=k>.85?Math.max(0,1-(k-.85)/.15):.9;ctx.fillStyle='#fff';sparkP(ctx,f.x,f.y,U*f.r);ctx.fill();ctx.beginPath();ctx.arc(f.x,f.y,U*f.r*.35,0,TAU);ctx.fill()}ctx.globalAlpha=1;
      if(k<.5){const m=this.sp([0,-.1,1]);const fk=k/.5,fx=lerp(this.x+U*.6,m[0],fk),fy=lerp(this.y-U*2.4,m[1],fk);ctx.fillStyle='#e8f6ff';ctx.globalCompositeOperation='lighter';glowM(ctx,'#bfe6ff',fx,fy,U*.12,.8);ctx.globalCompositeOperation='source-over';sparkP(ctx,fx,fy,U*.06);ctx.fill()}}
    // paws over the face (peekaboo, shy, think, yawn, kiss)
    for(let i=0;i<2;i++){const pw=this.paw2[i];if(!pw||!this.gl)continue;const c=this.sp(pw.pt),r=U*pw.r*this.armHide[i];if(r<2)continue;const g=ctx.createRadialGradient(c[0]-r*.3,c[1]-r*.35,r*.1,c[0],c[1],r);g.addColorStop(0,this.pal.top);g.addColorStop(.7,this.pal.skin);g.addColorStop(1,this.pal.bot);ctx.fillStyle=g;ctx.beginPath();ctx.ellipse(c[0],c[1],r,r*.88,0,0,TAU);ctx.fill();ctx.strokeStyle='rgba(180,120,160,.35)';ctx.lineWidth=1.2;ctx.stroke()}
    // held props
    if(p&&p.s>0){const k=ease.outBack(p.s),held=this.jState==='mochi'||!p.grab;let pos=held?p.pos:[this.jx,this.jy];const rot=held?Math.sin(t*1.3)*.05:(this.jTilt||0);
      if(p.type==='phone'&&pos){drawPhone2(ctx,pos[0],pos[1],U*k,rot,t,false)}
      if(p.type==='juice'&&pos){const m=this.sp([0,-.06,1]),top=[pos[0]+U*.05,pos[1]-U*.25*k-U*.1];ctx.strokeStyle='#9fe8ff';ctx.lineWidth=U*.035;ctx.lineCap='round';ctx.beginPath();ctx.moveTo(top[0],top[1]+U*.08);if(held){ctx.quadraticCurveTo(top[0],m[1]-U*.02,m[0]+U*.02,m[1]+U*.02)}else ctx.lineTo(top[0]+U*.12,top[1]-U*.12);ctx.stroke();drawJuice2(ctx,pos[0],pos[1],U*k,rot,p.left,t)}
      if(p.type==='icecream'&&pos)drawCone(ctx,pos[0],pos[1]+U*.05,U*k,rot-.15,p.left,t);
      if(p.type==='selfie'){const h=this.hand(1);if(h)drawPhone2(ctx,h[0],h[1]-U*.22,U*k,-.2,t,true)}
      if(p.type==='umbrella'){const h=this.hand(1);if(h)drawUmbrella(ctx,h[0],h[1],U*1.15,-.42+Math.sin(t*2)*.04,k)}
      if(p.type==='balloon'&&p.bx!=null&&!p.pop){const h=this.hand(1),free=ac&&ac.type==='balloon'&&ac.t/ac.dur>.82;ctx.strokeStyle='rgba(255,255,255,.7)';ctx.lineWidth=1.3;ctx.beginPath();ctx.moveTo(p.bx,p.by+U*.22);if(free||!h)ctx.quadraticCurveTo(p.bx-U*.05,p.by+U*.45,p.bx+Math.sin(t*3)*U*.05,p.by+U*.7);else ctx.quadraticCurveTo(p.bx,(p.by+h[1])/2,h[0],h[1]);ctx.stroke();
        const s=U*.27;ctx.globalCompositeOperation='lighter';glowM(ctx,'#ff5c8a',p.bx,p.by,s*2,.35);ctx.globalCompositeOperation='source-over';const g=ctx.createRadialGradient(p.bx-s*.35,p.by-s*.4,s*.05,p.bx,p.by,s*1.2);g.addColorStop(0,'#ffd0de');g.addColorStop(.35,'#ff5c8a');g.addColorStop(1,'#c41d55');ctx.fillStyle=g;heartP(ctx,p.bx,p.by+s*.05,s);ctx.fill();ctx.fillStyle='rgba(255,255,255,.6)';ctx.beginPath();ctx.ellipse(p.bx-s*.42,p.by-s*.35,s*.14,s*.08,-.7,0,TAU);ctx.fill()}}
    // firefly
    if(ac&&ac.type==='firefly'&&this.ffly){const f=this.ffly,a=f.st==='held'?.5+.3*Math.sin(t*9):1;ctx.globalCompositeOperation='lighter';glowM(ctx,'#fff3a0',f.x,f.y,U*(f.st==='held'?.35:.22),a*.9);glowM(ctx,'#b8ff6b',f.x,f.y,U*.08,a);ctx.globalCompositeOperation='source-over';if(f.st!=='held'){ctx.fillStyle='#fffbe0';ctx.beginPath();ctx.arc(f.x,f.y,U*.018,0,TAU);ctx.fill()}}
    // juggling stars
    if(ac&&ac.type==='juggle'&&this.jug){const a=this.hand(0),b=this.hand(1);if(a&&b){const k=ac.t/ac.dur;this.jug.forEach((j,i)=>{let ph=j.ph%1;const from=i%2?a:b,to=i%2?b:a;let x=lerp(from[0],to[0],ph),y=lerp(from[1],to[1],ph)-Math.sin(ph*Math.PI)*U*1.1;if(k>.85&&i===0){const kk=(k-.85)/.15;const hd=this.headScr();x=lerp(x,hd[0],kk);y=lerp(hd[1]-U*.9,hd[1]-U*.1,kk)}ctx.fillStyle=['#ffe08a','#ff9ecf','#7fd8ff'][i];ctx.globalCompositeOperation='lighter';glowM(ctx,ctx.fillStyle,x,y,U*.12,.5);ctx.globalCompositeOperation='source-over';starP(ctx,x,y,U*.065,t*4+i);ctx.fill()})}}
    // heart hands
    if(ac&&ac.type==='hearts'){const hd=this.headScr(),k=ac.t/ac.dur,s=U*.45*(1+.08*Math.sin(t*8))*ease.outBack(Math.min(1,k*3));ctx.save();ctx.globalCompositeOperation='lighter';glowM(ctx,'#ff5c8a',hd[0],hd[1]-U*.35,s*1.8,.5);ctx.globalCompositeOperation='source-over';ctx.strokeStyle='#ff7fae';ctx.lineWidth=U*.04;heartP(ctx,hd[0],hd[1]-U*.35,s);ctx.stroke();ctx.restore()}
    // bird
    if(this.bird){const b=this.bird;drawBird(ctx,b.x,b.y,U,b.flap,b.dir,t,b.st==='perch')}
    // particles
    for(const q of this.fx){const a=Math.min(1,q.life*2);ctx.globalAlpha=a;
      if(q.k==='note')drawShape(ctx,'notes',q.x,q.y,U*.08,1,q.c,a);else if(q.k==='heart'){ctx.fillStyle=q.c;heartP(ctx,q.x,q.y,U*.06);ctx.fill()}
      else if(q.k==='star'){ctx.fillStyle=q.c;starP(ctx,q.x,q.y,U*q.r,q.life*6);ctx.fill()}else if(q.k==='dot'){ctx.fillStyle=q.c;ctx.beginPath();ctx.arc(q.x,q.y,1.8,0,TAU);ctx.fill()}
      else if(q.k==='ring'){const r=U*(.1+(.6-q.life)*.9);ctx.strokeStyle=`rgba(220,235,255,${q.life*1.4})`;ctx.lineWidth=2;ctx.beginPath();ctx.arc(q.x,q.y,r,0,TAU);ctx.stroke();ctx.beginPath();ctx.arc(q.x,q.y,r*.6,0,TAU);ctx.stroke()}
      else if(q.k==='streak'){ctx.strokeStyle='rgba(255,255,255,.6)';ctx.lineWidth=1.5;ctx.beginPath();ctx.moveTo(q.x,q.y);ctx.lineTo(q.x-q.vx*.15-U*.2,q.y);ctx.stroke()}
      else if(q.k==='bigheart'){const k=1-q.life/1.4,s=U*(.15+k*1.6);ctx.globalAlpha=Math.min(1,q.life*1.5)*.85;ctx.globalCompositeOperation='lighter';glowM(ctx,'#ff5c8a',q.x,q.y-k*U*.6,s*1.6,.6);ctx.globalCompositeOperation='source-over';ctx.fillStyle='#ff5c8a';heartP(ctx,q.x,q.y-k*U*.6,s);ctx.fill();ctx.fillStyle='rgba(255,255,255,.55)';ctx.beginPath();ctx.ellipse(q.x-s*.42,q.y-k*U*.6-s*.35,s*.15,s*.09,-.6,0,TAU);ctx.fill()}}ctx.globalAlpha=1;
    // speech bubble
    if(this.bub){const hd=this.headScr(),a=Math.min(1,this.bub.life*3);ctx.globalAlpha=a;ctx.font=`800 ${U*.17}px "Plus Jakarta Sans",system-ui,sans-serif`;const tw=Math.max(U*.2,ctx.measureText(this.bub.txt).width),bw=tw+U*.2,bh=U*.3,bx=hd[0]+U*.55,by=hd[1]-U*.25;
      ctx.fillStyle='rgba(255,255,255,.96)';rrP(ctx,bx-bw/2,by-bh/2,bw,bh,bh*.45);ctx.fill();ctx.beginPath();ctx.moveTo(bx-bw*.3,by+bh*.4);ctx.lineTo(bx-bw*.45,by+bh*.75);ctx.lineTo(bx-bw*.1,by+bh*.45);ctx.fill();
      ctx.fillStyle=this.bub.col||'#5B2BB5';ctx.textAlign='center';ctx.textBaseline='middle';ctx.fillText(this.bub.txt,bx,by+U*.01);ctx.globalAlpha=1}
    // selfie flash
    if(this.flash>0){ctx.fillStyle=`rgba(255,255,255,${this.flash*.55})`;ctx.fillRect(0,0,this.w,this.h)}}
}

/* ================= LOOK: Mochi colours, skies, grounds, brightness ================= */
const MOCHI_PALS=[
 {name:'Cream',top:'#fff0e0',bot:'#ffa9cf',rim:'#ff3fbc',rim2:'#6f5cff',skin:'#ffd9e6',lid:'#fbe9e6',ink:'#2b1426',glow:'#ff6fd0'},
 {name:'Sakura',top:'#ff9ccb',bot:'#ff2f86',rim:'#ff1f8f',rim2:'#ffb347',skin:'#ff8fbe',lid:'#ff9fca',ink:'#3a0c24',glow:'#ff3fa0'},
 {name:'Mint',top:'#8dffd2',bot:'#00c98a',rim:'#00ffd0',rim2:'#3d7bff',skin:'#70f0c0',lid:'#92fbd2',ink:'#062a20',glow:'#1fffb0'},
 {name:'Sky',top:'#96d4ff',bot:'#1f5fff',rim:'#2fe0ff',rim2:'#c04dff',skin:'#7ec2ff',lid:'#98d2ff',ink:'#08163a',glow:'#2fb0ff'},
 {name:'Lilac',top:'#d2b2ff',bot:'#7a2dff',rim:'#e33dff',rim2:'#3dd8ff',skin:'#bf96ff',lid:'#d0b2ff',ink:'#1e0b3d',glow:'#a95cff'},
 {name:'Peach',top:'#ffc08f',bot:'#ff4a2f',rim:'#ff2f6b',rim2:'#ffd23d',skin:'#ffaa7a',lid:'#ffbf90',ink:'#3a120a',glow:'#ff6a45'},
 {name:'Lemon',top:'#fff27a',bot:'#ffa600',rim:'#ff7a1a',rim2:'#5aff6b',skin:'#ffe15a',lid:'#fff07c',ink:'#3a2a05',glow:'#ffc21a'},
 {name:'Galaxy',top:'#8a72ff',bot:'#241060',rim:'#ff3fd8',rim2:'#2fe6ff',skin:'#6a52e6',lid:'#7a64f0',ink:'#fff0fa',glow:'#a64dff',gal:1}];
const MOCHI_SKIES=[['stars','Stars'],['aurora','Aurora'],['nebula','Nebula'],['sunset','Synth sunset'],['bokeh','Candy lights'],['city','Neon city']];
const MOCHI_GROUNDS=[['grid','Circuit'],['glass','Mirror'],['water','Water'],['grass','Glow grass'],['clouds','Clouds'],['moon','Moon']];
Object.assign(MochiScene.prototype,{
  loadLook(){this.palI=clamp(store.get('mochi.pal',0)|0,0,MOCHI_PALS.length-1);this.pal=MOCHI_PALS[this.palI];this.col=this.pal.glow;this.sky=store.get('mochi.sky','nebula');this.ground=store.get('mochi.ground','grid');this.lum=store.get('mochi.lum',1);this.slum=store.get('mochi.slum',1);
    this.st2=[...Array(240)].map(()=>{const r=Math.random();return{x:rnd(),y:Math.pow(rnd(),1.25)*.78,s:r<.88?rnd(.6,1.3):rnd(1.4,2.4),a:rnd(.25,.9),ph:rnd(TAU),sp:rnd(.5,1.8),c:['#ffffff','#ffffff','#cdb4ff','#9ff3ff','#ffb3e6','#ffe7a6','#7dffb0'][(Math.random()*7)|0],big:r>.975}})},
  applyPal(M){M=M||this.M;if(!M)return;const p=this.pal,U=M.U;U.uTop.value.set(p.top);U.uBot.value.set(p.bot);U.uRim.value.set(p.rim);U.uRim2.value.set(p.rim2);U.uGal.value=p.gal||0;
    const m=M.arms[0].userData.m.material;m.color.set(p.skin);m.sheenColor.set(p.rim);m.emissive.set(p.bot);m.emissiveIntensity=p.gal?.5:.35;m.roughness=.55;this.col=p.glow},
  saveLook(k,v){store.set('mochi.'+k,v);try{delete SNAPS.b_mochi;snapSave()}catch(e){}},
  lookControls(){return[
    {type:'swatch',label:'Color',value:this.palI,items:MOCHI_PALS.map(p=>({name:p.name,cols:[p.top,p.bot]})),set:i=>{this.palI=i;this.pal=MOCHI_PALS[i];this.applyPal();this.saveLook('pal',i)}},
    {type:'buttons',label:'Sky',items:MOCHI_SKIES.map(([id,n])=>({name:n,on:this.sky===id,fn:()=>{this.sky=id;this.bgKey=null;this.saveLook('sky',id)}}))},
    {type:'buttons',label:'Ground',items:MOCHI_GROUNDS.map(([id,n])=>({name:n,on:this.ground===id,fn:()=>{this.ground=id;this.gdKey=null;this.saveLook('ground',id)}}))},
    {type:'slider',label:'Mochi glow',min:.5,max:1.7,step:.05,value:this.lum,set:v=>{this.lum=v;this.saveLook('lum',v)}},
    {type:'slider',label:'Scene light',min:.15,max:1.6,step:.05,value:this.slum,set:v=>{this.slum=v;this.saveLook('slum',v)}}]},
  /* brightness: applied right before the 3D body is drawn (dims or boosts sky + ground) */
  under(ctx){const L=this.slum;if(Math.abs(L-1)<.02)return;ctx.save();ctx.setTransform(1,0,0,1,0,0);if(L<1){ctx.globalAlpha=1-L;ctx.fillStyle='#000';ctx.fillRect(0,0,ctx.canvas.width,ctx.canvas.height)}else{ctx.globalCompositeOperation='lighter';ctx.globalAlpha=Math.min(1,L-1);ctx.drawImage(ctx.canvas,0,0)}ctx.restore()},
  lookTick(){const M=this.M;if(!M)return;const U=M.U;U.uTint.value.multiplyScalar(this.lum);U.uRimK.value=.55+.55*this.lum},
  /* ----- skies ----- */
  skyStatic(){const w=this.w,h=this.h,hz=this.floor-this.U*.28,key=this.sky+'|'+w+'|'+h+'|'+Math.round(hz);if(this.bgKey===key)return this.bgC;
    const c=this.bgC||(this.bgC=document.createElement('canvas'));c.width=Math.max(1,w);c.height=Math.max(1,h);const x=c.getContext('2d');x.clearRect(0,0,w,h);
    const blob=(cx,cy,r,col,a)=>{const g=x.createRadialGradient(cx,cy,0,cx,cy,r);g.addColorStop(0,hexA(col,a));g.addColorStop(.5,hexA(col,a*.35));g.addColorStop(1,hexA(col,0));x.fillStyle=g;x.fillRect(cx-r,cy-r,2*r,2*r)};
    const sky=this.sky;x.globalCompositeOperation='lighter';
    if(sky==='nebula'){blob(w*.2,h*.22,w*.75,'#ff2fa0',.32);blob(w*.85,h*.12,w*.65,'#3d6bff',.34);blob(w*.6,h*.42,w*.6,'#9d3dff',.3);blob(w*.1,h*.5,w*.45,'#00d9ff',.18);blob(w*.75,h*.3,w*.25,'#ffb36b',.2);
      for(let i=0;i<60;i++)blob(rnd(w),rnd(h*.6),rnd(4,18),['#ffffff','#ff9ecf','#9ff3ff'][i%3],.25)}
    if(sky==='aurora'){blob(w*.5,0,w*.9,'#0b3d3a',.6);blob(w*.2,h*.1,w*.5,'#1a0b3d',.5)}
    if(sky==='sunset'){x.globalCompositeOperation='source-over';const g=x.createLinearGradient(0,h*.15,0,hz);g.addColorStop(0,'rgba(40,0,60,0)');g.addColorStop(.55,'rgba(110,10,120,.55)');g.addColorStop(.85,'rgba(255,40,120,.75)');g.addColorStop(1,'rgba(255,140,60,.9)');x.fillStyle=g;x.fillRect(0,0,w,hz);
      const R=Math.min(w*.36,h*.2),sy=hz-R*.35,sg=x.createLinearGradient(0,sy-R,0,sy+R*.4);sg.addColorStop(0,'#fff27a');sg.addColorStop(.5,'#ff9a3d');sg.addColorStop(1,'#ff2f8f');x.fillStyle=sg;x.beginPath();x.arc(w/2,sy,R,Math.PI,0);x.lineTo(w/2+R,hz);x.lineTo(w/2-R,hz);x.fill();
      x.globalCompositeOperation='destination-out';for(let i=0;i<7;i++){const yy=sy-R*.05+i*R*.13;x.fillRect(w/2-R,yy,R*2,R*.025*(i+1))}x.globalCompositeOperation='lighter';blob(w/2,sy,R*2,'#ff4f9a',.35);
      x.globalCompositeOperation='source-over';for(const[ph,col,amp]of[[0,'#16061f',.11],[2,'#0b0312',.07]]){x.fillStyle=col;x.beginPath();x.moveTo(0,hz);for(let i=0;i<=24;i++){const xx=i/24*w,yy=hz-h*amp*(.35+.65*Math.abs(Math.sin(i*1.7+ph))*(Math.abs(i-12)>3?1:.25));x.lineTo(xx,yy)}x.lineTo(w,hz);x.fill();x.strokeStyle='rgba(255,80,180,.5)';x.lineWidth=1;x.stroke()}}
    if(sky==='city'){x.globalCompositeOperation='source-over';const g=x.createLinearGradient(0,h*.3,0,hz);g.addColorStop(0,'rgba(30,0,60,0)');g.addColorStop(1,'rgba(90,20,140,.55)');x.fillStyle=g;x.fillRect(0,0,w,hz);
      for(const[L,col,hm]of[[0,'#120724',.32],[1,'#07030f',.22]]){let xx=-10;while(xx<w){const bw=rnd(w*.06,w*.14),bh=h*hm*rnd(.35,1);x.fillStyle=col;x.fillRect(xx,hz-bh,bw,bh);if(Math.random()<.3){x.fillRect(xx+bw*.45,hz-bh-h*.03,bw*.1,h*.03)}
        for(let wy=hz-bh+6;wy<hz-4;wy+=7)for(let wx=xx+4;wx<xx+bw-4;wx+=6)if(Math.random()<(L?.18:.1)){x.fillStyle=['#ff4fd8','#38e8ff','#ffe066','#9d6bff'][(Math.random()*4)|0];x.globalAlpha=rnd(.4,.95);x.fillRect(wx,wy,2.5,3.5);x.globalAlpha=1;x.fillStyle=col}
        if(L&&Math.random()<.25){const nc=Math.random()<.5?'#ff3fa8':'#2fe6ff';x.strokeStyle=nc;x.lineWidth=2;x.strokeRect(xx+bw*.2,hz-bh+8,bw*.6,8);x.globalCompositeOperation='lighter';blob(xx+bw*.5,hz-bh+12,bw*.8,nc,.35);x.globalCompositeOperation='source-over'}xx+=bw+rnd(1,6)}}}
    x.globalCompositeOperation='source-over';this.bgKey=key;return c},
  drawSky(ctx){const w=this.w,h=this.h,t=this.t,A=this.A||{},tw=A.active?A.treble:0,bass=A.active?A.bass:0,sky=this.sky,hz=this.floor-this.U*.28;
    const bg=this.skyStatic();if(sky==='nebula'){const s=1+.02*Math.sin(t*.2)+bass*.03;ctx.save();ctx.translate(w/2,h*.35);ctx.scale(s,s);ctx.rotate(Math.sin(t*.05)*.03);ctx.drawImage(bg,-w/2,-h*.35);ctx.restore()}else ctx.drawImage(bg,0,0);
    const lim=sky==='sunset'||sky==='city'?.55:1;for(const s of this.st2){if(s.y>lim*.78&&lim<1)continue;const a=s.a*(.5+.5*Math.sin(t*s.sp*(1+tw*3)+s.ph));ctx.globalAlpha=a;ctx.fillStyle=s.c;if(s.big){ctx.globalCompositeOperation='lighter';glow(ctx,s.c,s.x*w,s.y*h,s.s*5,a*.8);ctx.globalCompositeOperation='source-over';sparkP(ctx,s.x*w,s.y*h,s.s*2.6);ctx.fill()}else ctx.fillRect(s.x*w,s.y*h,s.s,s.s)}ctx.globalAlpha=1;
    if(sky==='aurora'){ctx.globalCompositeOperation='lighter';[['#2dffb0',.16,0],['#8a5cff',.24,1.7],['#ff4fd8',.3,3.1],['#2fd2ff',.12,4.4]].forEach(([c,yb,ph],i)=>{const top=[];for(let k=0;k<=28;k++){const xx=k/28*w;top.push([xx,h*yb+Math.sin(xx*.006+t*.35+ph)*h*.045+Math.sin(xx*.017-t*.6+ph)*h*.018])}
      const th=h*(.16+.05*Math.sin(t*.4+i))*(1+bass*.4);const g=ctx.createLinearGradient(0,h*yb-h*.05,0,h*yb+th);g.addColorStop(0,hexA(c,0));g.addColorStop(.18,hexA(c,.42));g.addColorStop(1,hexA(c,0));ctx.fillStyle=g;ctx.beginPath();top.forEach(([a,b],k)=>k?ctx.lineTo(a,b):ctx.moveTo(a,b));for(let k=28;k>=0;k--)ctx.lineTo(top[k][0],top[k][1]+th);ctx.fill()});ctx.globalCompositeOperation='source-over'}
    if(sky==='bokeh'){if(!this.bok)this.bok=[...Array(26)].map(()=>({x:rnd(),y:rnd(),r:rnd(.03,.11),v:rnd(.004,.012),c:['#ff4fd8','#38e8ff','#ffe066','#9d6bff','#2dffb0','#ff8a3d'][(Math.random()*6)|0],ph:rnd(TAU)}));ctx.globalCompositeOperation='lighter';
      for(const b of this.bok){b.y-=b.v*dtFrame;if(b.y<-.1)b.y=1.05;const x=(b.x+Math.sin(t*.2+b.ph)*.03)*w,y=b.y*hz,r=b.r*w*(1+bass*.25);const g=ctx.createRadialGradient(x,y,0,x,y,r);g.addColorStop(0,hexA(b.c,.22));g.addColorStop(.8,hexA(b.c,.16));g.addColorStop(1,hexA(b.c,0));ctx.fillStyle=g;ctx.beginPath();ctx.arc(x,y,r,0,TAU);ctx.fill()}ctx.globalCompositeOperation='source-over'}
    if(sky==='sunset'&&bass>.05){ctx.globalCompositeOperation='lighter';glow(ctx,'#ff4f9a',w/2,hz-Math.min(w*.36,h*.2)*.35,Math.min(w*.36,h*.2)*2.2,bass*.5);ctx.globalCompositeOperation='source-over'}},
  /* ----- grounds ----- */
  groundStatic(){const w=this.w,h=this.h,hz=this.floor-this.U*.28,key=this.ground+'|'+w+'|'+h+'|'+Math.round(hz);if(this.gdKey===key)return this.gdC;const c=this.gdC||(this.gdC=document.createElement('canvas'));c.width=Math.max(1,w);c.height=Math.max(1,h);const x=c.getContext('2d');x.clearRect(0,0,w,h);const G=this.ground;
    if(G==='moon'){const g=x.createLinearGradient(0,hz,0,h);g.addColorStop(0,'#3a3352');g.addColorStop(1,'#16121f');x.fillStyle=g;x.beginPath();x.moveTo(0,hz+8);x.quadraticCurveTo(w/2,hz-14,w,hz+8);x.lineTo(w,h);x.lineTo(0,h);x.fill();
      for(let i=0;i<34;i++){const v=Math.pow(rnd(),.8),P=this.floorP(rnd(-1.1,1.1),v),r=(4+v*30)*rnd(.5,1.4);if(Math.abs(P[0]-w/2)<this.U*.7&&v<.3)continue;x.fillStyle='rgba(10,8,18,.45)';x.beginPath();x.ellipse(P[0],P[1],r,r*.32,0,0,TAU);x.fill();x.strokeStyle='rgba(190,175,230,.25)';x.lineWidth=1.2;x.beginPath();x.ellipse(P[0],P[1]+r*.05,r,r*.32,0,0,Math.PI);x.stroke()}}
    if(G==='clouds'){const g=x.createLinearGradient(0,hz,0,h);g.addColorStop(0,'rgba(60,30,90,.0)');g.addColorStop(.2,'rgba(60,30,90,.5)');g.addColorStop(1,'rgba(20,10,35,.9)');x.fillStyle=g;x.fillRect(0,hz-10,w,h-hz+10);
      for(let row=0;row<6;row++){const v=row/5,y=hz+(h-hz)*Math.pow(v,1.2)+6,r=12+v*46;for(let xx=-r;xx<w+r;xx+=r*1.5){const cy=y+rnd(-r*.15,r*.15),gr=x.createRadialGradient(xx-r*.2,cy-r*.25,r*.1,xx,cy,r*1.7);const c=['#ffb8e0','#c4a8ff','#9fd8ff','#ffc8ec'][(row+Math.abs((xx/r)|0))%4];gr.addColorStop(0,hexA(c,.7));gr.addColorStop(.6,hexA(c,.45));gr.addColorStop(1,hexA(c,0));x.fillStyle=gr;x.beginPath();x.ellipse(xx,cy,r*1.7,r*.75,0,0,TAU);x.fill()}}
      x.globalCompositeOperation='source-atop';const sh=x.createLinearGradient(0,hz,0,h);sh.addColorStop(0,'rgba(0,0,0,.05)');sh.addColorStop(1,'rgba(10,0,30,.55)');x.fillStyle=sh;x.fillRect(0,hz-30,w,h-hz+30);x.globalCompositeOperation='source-over'}
    this.gdKey=key;return c},
  drawFloor(ctx){const w=this.w,h=this.h,U=this.U,t=this.t,A=this.A||{},bass=A.active?A.bass:0,G=this.ground,hz=this.floor-U*.28,gc=this.charging?'#4dff7a':this.pal.glow;
    if(G==='grid'){Buddy.prototype.drawFloor.call(this,ctx);const col=this.charging?'93,255,140':'205,170,255';ctx.lineWidth=1;
      for(let i=1;i<9;i++){const v=Math.pow(i/9,1.6),y=hz+(h-hz)*v;ctx.strokeStyle=`rgba(${col},${.07+v*.1})`;ctx.beginPath();ctx.moveTo(0,y);ctx.lineTo(w,y);ctx.stroke()}
      for(let i=-8;i<=8;i++){const P0=this.floorP(i*.12,0),P1=this.floorP(i*.12,1);ctx.strokeStyle=`rgba(${col},.09)`;ctx.beginPath();ctx.moveTo(P0[0],P0[1]);ctx.lineTo(P1[0],P1[1]);ctx.stroke()}return}
    if(G==='glass'){const g=ctx.createLinearGradient(0,hz,0,h);g.addColorStop(0,hexA(this.pal.bot,.18));g.addColorStop(.3,'rgba(12,8,24,.85)');g.addColorStop(1,'rgba(4,3,8,1)');ctx.fillStyle=g;ctx.fillRect(0,hz,w,h-hz);
      const lg=ctx.createLinearGradient(0,0,w,0);lg.addColorStop(0,hexA(this.pal.rim2,0));lg.addColorStop(.5,hexA(this.pal.rim,.8));lg.addColorStop(1,hexA(this.pal.rim2,0));ctx.fillStyle=lg;ctx.fillRect(0,hz,w,1.5);
      for(let i=0;i<5;i++){const v=.15+i*.18,y=hz+(h-hz)*v;ctx.fillStyle=`rgba(255,255,255,${.035*(1-v)})`;ctx.fillRect(0,y,w,1)}}
    if(G==='water'){const g=ctx.createLinearGradient(0,hz,0,h);g.addColorStop(0,'#04122a');g.addColorStop(1,'#01040c');ctx.fillStyle=g;ctx.fillRect(0,hz,w,h-hz);
      ctx.globalCompositeOperation='lighter';for(let i=0;i<70;i++){const v=((i*.137)%1),y=hz+(h-hz)*Math.pow(v,1.3),x=((i*.618+t*.02*(1+v))%1)*w,len=(8+v*40)*(.6+.4*Math.sin(t*2+i));ctx.strokeStyle=hexA(i%3?'#38c8ff':this.pal.rim,.12+.18*(1-v)*(.5+.5*Math.sin(t*3+i*1.7)));ctx.lineWidth=1+v;ctx.beginPath();ctx.moveTo(x-len,y);ctx.lineTo(x+len,y);ctx.stroke()}ctx.globalCompositeOperation='source-over';
      if(!this.rips)this.rips=[];this.ripT=(this.ripT||0)-dtFrame;if(this.ripT<0||(A.active&&A.beat)){this.ripT=1.6;this.rips.push({r:0})}for(let i=this.rips.length-1;i>=0;i--){const r=this.rips[i];r.r+=dtFrame*U*.9;const a=Math.max(0,1-r.r/(U*2.2));if(a<=0){this.rips.splice(i,1);continue}ctx.strokeStyle=hexA(gc,a*.6);ctx.lineWidth=1.5;ctx.beginPath();ctx.ellipse(this.x,this.floor,U*.7+r.r,(U*.7+r.r)*.16,0,0,TAU);ctx.stroke()}}
    if(G==='grass'){const g=ctx.createLinearGradient(0,hz,0,h);g.addColorStop(0,'#03140c');g.addColorStop(1,'#000503');ctx.fillStyle=g;ctx.fillRect(0,hz,w,h-hz);
      if(!this.blades){this.blades=[...Array(190)].map(()=>({u:rnd(-1.2,1.2),v:Math.pow(rnd(),.9),hh:rnd(.6,1.3),ph:rnd(TAU),c:['#2dffb0','#7dff6b','#38e8ff','#b6ff4f','#2dffb0'][(Math.random()*5)|0],fl:Math.random()<.07?['#ff6fd0','#ffe066','#9d6bff'][(Math.random()*3)|0]:null})).sort((a,b)=>a.v-b.v)}
      ctx.lineCap='round';const byC={};for(const b of this.blades){const P=this.floorP(b.u,b.v),L=U*(.07+.22*b.v)*b.hh,sw=Math.sin(t*1.4+b.u*3+b.ph)*.35+(A.active?Math.sin(t*8+b.u*6)*bass*.5:0)-(this.u.shear[0]||0)*.5;b.tx=P[0]+Math.sin(sw)*L;b.ty=P[1]-Math.cos(sw)*L;b.px=P[0];b.py=P[1];b.L=L;const key=b.c+(b.v>.5?'1':'0');(byC[key]||(byC[key]=[])).push(b)}
      for(const key in byC){const arr=byC[key],far=key.endsWith('0');ctx.strokeStyle=hexA(key.slice(0,7),far?.45:.8);ctx.lineWidth=far?1.3:2.8;ctx.beginPath();for(const b of arr){ctx.moveTo(b.px,b.py);ctx.quadraticCurveTo(b.px,b.py-b.L*.5,b.tx,b.ty)}ctx.stroke()}
      ctx.globalCompositeOperation='lighter';for(const b of this.blades)if(b.fl)glow(ctx,b.fl,b.tx,b.ty,4+b.v*10,.8);ctx.globalCompositeOperation='source-over';for(const b of this.blades)if(b.fl){ctx.fillStyle=b.fl;ctx.beginPath();ctx.arc(b.tx,b.ty,1.5+b.v*2.5,0,TAU);ctx.fill()}}
    if(G==='clouds'){const gs=this.groundStatic();ctx.drawImage(gs,Math.sin(t*.15)*8,0)}
    if(G==='moon'){ctx.drawImage(this.groundStatic(),0,0);const lg=ctx.createLinearGradient(0,0,w,0);lg.addColorStop(0,'rgba(200,190,255,0)');lg.addColorStop(.5,'rgba(220,210,255,.5)');lg.addColorStop(1,'rgba(200,190,255,0)');ctx.fillStyle=lg;ctx.fillRect(0,hz-4,w,1)}
    ctx.save();ctx.translate(this.x,this.floor);ctx.scale(1,.2);ctx.globalCompositeOperation='lighter';glow(ctx,gc,0,0,U*1.5,.35+bass*.3);glow(ctx,this.pal.rim2,0,0,U*2.3,.14);ctx.restore()},
  reflect(ctx){const k={grid:.3,glass:.6,water:.32}[this.ground];if(!k)return;const g=this.gl;if(!g)return;const pr=g.r.getPixelRatio(),cv=g.cv,fl=this.floor,Hs=Math.min(fl,this.U*2.3);const R=Math.min(this.h-fl,Hs*.5);if(R<4)return;
    const rc=this.rc||(this.rc=document.createElement('canvas')),rw=Math.max(2,Math.round(this.w/2)),rh=Math.max(2,Math.round(R/2));if(rc.width!==rw||rc.height!==rh){rc.width=rw;rc.height=rh}const x=rc.getContext('2d');
    x.setTransform(1,0,0,1,0,0);x.globalCompositeOperation='copy';x.setTransform(1,0,0,-1,0,rh);try{x.drawImage(cv,0,(fl-Hs)*pr,cv.width,Hs*pr,0,0,rw,rh)}catch(e){return}
    x.setTransform(1,0,0,1,0,0);x.globalCompositeOperation='destination-in';const gr=x.createLinearGradient(0,0,0,rh);gr.addColorStop(0,`rgba(0,0,0,${k})`);gr.addColorStop(.55,`rgba(0,0,0,${k*.2})`);gr.addColorStop(1,'rgba(0,0,0,0)');x.fillStyle=gr;x.fillRect(0,0,rw,rh);x.globalCompositeOperation='source-over';
    if(this.ground==='water'){const t=this.t;for(let i=0;i<rh;i+=2){const off=Math.sin(i*.5+t*4)*(1+i*.06);ctx.drawImage(rc,0,i,rw,2,off,fl-1+i*R/rh,this.w,2*R/rh+.5)}}else ctx.drawImage(rc,0,fl-1,this.w,R)}
});

/* ----- GPU mirror reflection (no canvas read-back): the body is rendered upside-down into a
   half-resolution target, then laid under the floor line with a fade ----- */
Object.assign(MochiScene.prototype,{
  reflect(){},
  attach(el){Buddy.prototype.attach.call(this,el);const g=this.gl;if(!g)return;const T=T3;this.world=new T.Group();g.sc.remove(this.M.root);this.world.add(this.M.root);g.sc.add(this.world);
    this.rt=new T.WebGLRenderTarget(4,4);const mm=new T.ShaderMaterial({uniforms:{uTex:{value:this.rt.texture},uRes:{value:new T.Vector2(1,1)},uFl:{value:0},uR:{value:1},uK:{value:.3},uT:{value:0},uW:{value:0}},
      vertexShader:'void main(){gl_Position=vec4(position.xy,0.,1.);}',
      fragmentShader:'uniform sampler2D uTex;uniform vec2 uRes;uniform float uFl,uR,uK,uT,uW;void main(){vec2 fc=gl_FragCoord.xy;float d=uFl-fc.y;if(d<0.)discard;float f=1.-clamp(d/uR,0.,1.);vec2 uv=fc/uRes;uv.x+=sin(fc.y*.07+uT*4.)*.005*uW;vec4 c=texture2D(uTex,uv);gl_FragColor=vec4(c.rgb,1.);\n#include <encodings_fragment>\ngl_FragColor*=uK*f*f*c.a;}',
      transparent:true,depthTest:false,depthWrite:false,premultipliedAlpha:true});
    this.mir=new T.Mesh(new T.PlaneGeometry(2,2),mm);this.mir.frustumCulled=false;this.mir.renderOrder=-10;g.sc.add(this.mir);this.layoutGL()},
  layoutGL(){Buddy.prototype.layoutGL.call(this);const g=this.gl;if(!g||!this.rt)return;const c=g.r.domElement;this.rt.setSize(Math.max(2,c.width>>1),Math.max(2,c.height>>1))},
  renderGL(){const g=this.gl,r=g.r,k={grid:.32,glass:.6,water:.36}[this.ground];
    if(k&&this.world&&this.mir){const pr=r.getPixelRatio(),Yf=this.h/2-this.floor,c=r.domElement;this.world.scale.y=-1;this.world.position.y=2*Yf;this.mir.visible=false;
      r.setRenderTarget(this.rt);r.clear();r.render(g.sc,g.cam);r.setRenderTarget(null);this.world.scale.y=1;this.world.position.y=0;this.mir.visible=true;
      const U=this.mir.material.uniforms;U.uRes.value.set(c.width,c.height);U.uFl.value=(this.h-this.floor)*pr;U.uR.value=Math.min(this.h-this.floor,this.U*1.3)*pr;U.uK.value=k;U.uT.value=this.t;U.uW.value=this.ground==='water'?1:0}
    else if(this.mir)this.mir.visible=false;r.render(g.sc,g.cam)},
  detach(){if(this.rt){this.rt.dispose();this.rt=null}Buddy.prototype.detach.call(this)}
});
