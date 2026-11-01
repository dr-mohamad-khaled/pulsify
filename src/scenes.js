/* ================= FULL-SCREEN GPU SCENES ================= */
const QUAD_V='varying vec2 vUv;void main(){vUv=uv;gl_Position=vec4(position.xy,0.,1.);}';
class GLScene extends Scene{
  attach(el){if(!HAS3D||this.gl)return;const G=sharedGL();if(G.cv.parentNode!==el)el.insertBefore(G.cv,el.children[1]||null);G.cv.style.display='block';
    this.gl={r:G.r,cv:G.cv,pr:Math.min(G.maxPr,this.preview?1:(this.prMax||1.1)),maxPr:Math.min(G.maxPr,this.prMax||1.4)};const T=T3;this.cam=new T.OrthographicCamera(-1,1,1,-1,0,1);this.sc=new T.Scene();this.geo=new T.PlaneGeometry(2,2);this.init();this.layoutGL()}
  detach(){if(!this.gl)return;const g=this.gl;g.r.setRenderTarget(null);g.r.clear();g.cv.style.display='none';this.free&&this.free();this.sc.clear();this.gl=null}
  layoutGL(){const g=this.gl;if(!g||this.w<2)return;g.r.setPixelRatio(g.pr);g.r.setSize(this.w,this.h,false);GLS.pr=g.pr;g.cv.style.width=this.w+'px';g.cv.style.height=this.h+'px';this.onSize&&this.onSize()}
  resize(w,h){super.resize(w,h);this.layoutGL()}
  adapt(dt){const g=this.gl;g.fa=lerp(g.fa||16,dt*1000,.05);g.cool=(g.cool||2)-dt;if(g.cool>0)return;if(g.fa>22&&g.pr>.6){g.pr=Math.max(.6,g.pr-.12);g.cool=1.5;this.layoutGL()}else if(g.fa<15&&g.pr<g.maxPr){g.pr=Math.min(g.maxPr,g.pr+.08);g.cool=3;this.layoutGL()}}
  draw(ctx){if(this.gl){if(!this.preview)this.adapt(dtFrame);this.render()}this.draw2d&&this.draw2d(ctx)}
  dispose(){this.detach()}}

/* ---------------- LIQUID OBSIDIAN: a real wave simulation on the GPU ---------------- */
const OBS_SIM=`uniform sampler2D uState;uniform vec2 uTexel;uniform vec4 uDrops[8];uniform float uAspect,uDamp;varying vec2 vUv;
void main(){vec4 c=texture2D(uState,vUv);float avg=(texture2D(uState,vUv+vec2(uTexel.x,0.)).r+texture2D(uState,vUv-vec2(uTexel.x,0.)).r+texture2D(uState,vUv+vec2(0.,uTexel.y)).r+texture2D(uState,vUv-vec2(0.,uTexel.y)).r)*.25;
 c.g+=(avg-c.r)*2.;c.g*=uDamp;c.r+=c.g;c.r*=.9993;
 for(int i=0;i<8;i++){vec4 d=uDrops[i];if(d.w!=0.){vec2 q=vUv-d.xy;q.x*=uAspect;float k=max(0.,1.-length(q)/d.z);k=.5-cos(k*3.14159)*.5;c.r+=k*d.w;}}
 gl_FragColor=c;}`;
const OBS_F=`uniform sampler2D uState;uniform vec2 uTexel;uniform vec3 uC1,uC2,uC3;uniform float uBright,uT,uTw,uAspect,uBass;varying vec2 vUv;
float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
void main(){float h=texture2D(uState,vUv).r;float hx=texture2D(uState,vUv+vec2(uTexel.x,0.)).r-texture2D(uState,vUv-vec2(uTexel.x,0.)).r;float hy=texture2D(uState,vUv+vec2(0.,uTexel.y)).r-texture2D(uState,vUv-vec2(0.,uTexel.y)).r;
 vec3 n=normalize(vec3(-hx*11.,-hy*11.,1.));float slope=length(n.xy);vec3 r=reflect(vec3(0.,0.,-1.),n);
 float l1=pow(max(dot(r,normalize(vec3(-.55,.62,.55))),0.),26.);float l2=pow(max(dot(r,normalize(vec3(.6,-.4,.68))),0.),20.);float l3=pow(max(dot(r,normalize(vec3(.15,.85,.5))),0.),70.);
 vec3 col=uC1*l1*1.7+uC2*l2*1.4+mix(uC3,vec3(1.),.6)*l3*.9;
 col+=mix(uC2,uC1,smoothstep(-.02,.02,h))*smoothstep(.05,.7,slope)*.5;
 vec2 sp=(vUv+n.xy*.04)*vec2(uAspect,1.)*70.;vec2 id=floor(sp);float s=hash(id);float st=step(.982,s)*smoothstep(.32,.0,length(fract(sp)-.5))*(.45+.35*sin(uT*(1.+s*3.)+s*40.)+.5*uTw);
 col+=vec3(.85,.88,1.)*st*.32;col+=uC3*.03*(1.-length(vUv-vec2(.5,.35)))*(1.+uBass);
 col*=uBright;gl_FragColor=vec4(col,1.);}`;
const OBS_PALS=[['Lavender','#CDB4FF','#FFB36B','#9D4EDD'],['Aurora','#00F5D4','#7B61FF','#4ADE80'],['Neon','#FF2BD6','#00E5FF','#7CFF4F'],['Ember','#FF7A3D','#FFD166','#EF233C'],['Ice','#E0FBFC','#8FD8FF','#9AB4FF'],['Emerald','#4ADE80','#FACC15','#14B8A6'],['Rose','#FF7FAE','#FFC6E0','#C77DFF'],['Gold','#FFD27A','#FFF3D6','#FF9F1C']];
class ObsidianScene extends GLScene{
  constructor(pv){super(pv);this.pal=store.get('obs.pal',0);this.bright=store.get('obs.bright',.85);this.energy=store.get('obs.energy',.6);this.q=[];this.rainT=0;this.idleT=1;this.prMax=1.6}
  controls(){return[{type:'swatch',label:'Colour',items:OBS_PALS.map(p=>({name:p[0],cols:[p[1],p[2]]})),value:this.pal,set:i=>{this.pal=i;store.set('obs.pal',i)}},
    {type:'slider',label:'Brightness',min:.3,max:1.6,step:.01,value:this.bright,set:v=>{this.bright=v;store.set('obs.bright',v)}},
    {type:'slider',label:'Ripple energy',min:.2,max:1.2,step:.01,value:this.energy,set:v=>{this.energy=v;store.set('obs.energy',v)}}]}
  init(){const T=T3,r=this.gl.r;const half=r.capabilities.isWebGL2&&(r.extensions.has('EXT_color_buffer_float')||r.extensions.has('EXT_color_buffer_half_float'));
    this.rtOpt={type:half?T.HalfFloatType:T.UnsignedByteType,minFilter:T.LinearFilter,magFilter:T.LinearFilter,wrapS:T.ClampToEdgeWrapping,wrapT:T.ClampToEdgeWrapping,depthBuffer:false,stencilBuffer:false};
    const drops=[...Array(8)].map(()=>new T.Vector4());this.simU={uState:{value:null},uTexel:{value:new T.Vector2()},uDrops:{value:drops},uAspect:{value:1},uDamp:{value:.993}};
    this.simM=new T.ShaderMaterial({uniforms:this.simU,vertexShader:QUAD_V,fragmentShader:OBS_SIM});this.ren={uState:{value:null},uTexel:{value:new T.Vector2()},uC1:{value:new T.Color()},uC2:{value:new T.Color()},uC3:{value:new T.Color()},uBright:{value:1},uT:{value:0},uTw:{value:0},uAspect:{value:1},uBass:{value:0}};
    this.renM=new T.ShaderMaterial({uniforms:this.ren,vertexShader:QUAD_V,fragmentShader:OBS_F});this.quad=new T.Mesh(this.geo,this.simM);this.quad.frustumCulled=false;this.sc.add(this.quad)}
  onSize(){const T=T3,sw=this.preview?110:Math.round(clamp(this.w*.62,180,300)),sh=Math.round(sw*this.h/this.w);if(this.rtA&&this.rtA.width===sw&&this.rtA.height===sh)return;this.free();this.rtA=new T.WebGLRenderTarget(sw,sh,this.rtOpt);this.rtB=new T.WebGLRenderTarget(sw,sh,this.rtOpt);
    const r=this.gl.r;for(const rt of[this.rtA,this.rtB]){r.setRenderTarget(rt);r.setClearColor(0,0);r.clear()}r.setRenderTarget(null);this.simU.uTexel.value.set(1/sw,1/sh);this.ren.uTexel.value.set(1/sw,1/sh);this.simU.uAspect.value=this.ren.uAspect.value=this.w/this.h}
  free(){if(this.rtA){this.rtA.dispose();this.rtB.dispose();this.rtA=this.rtB=null}}
  drop(x,y,rad,str){this.q.push([x/this.w,1-y/this.h,rad,str*this.energy]);if(this.q.length>40)this.q.shift()}
  demo(){for(let i=0;i<5;i++)this.drop(this.w*rnd(.2,.8),this.h*rnd(.2,.8),rnd(.04,.1),rnd(.8,1.5))}
  down(p){p.lx=p.x;p.ly=p.y;this.drop(p.x,p.y,.05,.9);this.idleT=0}
  move(p){const d=Math.hypot(p.x-p.lx,p.y-p.ly);if(d>4){const n=Math.min(4,Math.ceil(d/14));for(let i=1;i<=n;i++)this.drop(lerp(p.lx,p.x,i/n),lerp(p.ly,p.y,i/n),.03,.22);p.lx=p.x;p.ly=p.y}this.idleT=0}
  up(p){if(Math.hypot(p.x-p.sx,p.y-p.sy)<12&&this.t-p.t0<.3){this.drop(p.x,p.y,.09,-1.6);haptic(8)}}
  update(dt,A){this.t+=dt;this.A=A;this.idleT+=dt;if(A.active){if(A.beat){this.drop(this.w*rnd(.3,.7),this.h*rnd(.35,.65),.12+A.bass*.08,1.3+A.bass);if(this.beatN=(this.beatN||0)+1,this.beatN%4===0)this.drop(this.w/2,this.h*.5,.22,-2)}if(A.treble>.45&&Math.random()<A.treble*.5)this.drop(rnd(this.w),rnd(this.h),.012,.25)}
    this.rainT-=dt;if(this.rainT<0){this.rainT=A.active?rnd(.2,.6):rnd(.6,1.8);this.drop(rnd(this.w),rnd(this.h),rnd(.015,.035),rnd(.3,.6))}}
  render(){const r=this.gl.r,A=this.A||{};if(!this.rtA)this.onSize();const P=OBS_PALS[this.pal]||OBS_PALS[0];
    for(let s=0;s<2;s++){const ds=this.simU.uDrops.value;for(let i=0;i<8;i++){const d=s===0?this.q.shift():null;d?ds[i].set(d[0],d[1],d[2],d[3]):ds[i].set(0,0,0,0)}this.simU.uState.value=this.rtA.texture;this.quad.material=this.simM;r.setRenderTarget(this.rtB);r.render(this.sc,this.cam);[this.rtA,this.rtB]=[this.rtB,this.rtA]}
    r.setRenderTarget(null);const R=this.ren;R.uState.value=this.rtA.texture;R.uC1.value.set(P[1]);R.uC2.value.set(P[2]);R.uC3.value.set(P[3]);R.uBright.value=this.bright*(1+(A.active?A.bass*.25:0));R.uT.value=this.t;R.uTw.value=A.active?A.treble:0;R.uBass.value=A.active?A.bass:0;
    this.quad.material=this.renM;r.render(this.sc,this.cam)}}

/* ---------------- COSMIC ODYSSEY: ray-traced planets, a live sun and a spiral galaxy ---------------- */
const COS_F=`uniform vec2 uRes;uniform float uT,uKind,uZoom,uBass,uMid,uTre,uSpin,uTilt,uFlA,uFlT,uPulse;varying vec2 vUv;
float h3(vec3 p){p=fract(p*.3183099+.1);p*=17.;return fract(p.x*p.y*p.z*(p.x+p.y+p.z));}
float h2(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
float noise(vec3 x){vec3 i=floor(x),f=fract(x);f=f*f*(3.-2.*f);return mix(mix(mix(h3(i),h3(i+vec3(1,0,0)),f.x),mix(h3(i+vec3(0,1,0)),h3(i+vec3(1,1,0)),f.x),f.y),mix(mix(h3(i+vec3(0,0,1)),h3(i+vec3(1,0,1)),f.x),mix(h3(i+vec3(0,1,1)),h3(i+vec3(1,1,1)),f.x),f.y),f.z);}
float fbm(vec3 p){float s=0.,a=.5;for(int i=0;i<5;i++){s+=a*noise(p);p=p*2.03+vec3(1.7,9.2,3.1);a*=.5;}return s;}
mat3 rY(float a){float c=cos(a),s=sin(a);return mat3(c,0.,-s,0.,1.,0.,s,0.,c);}mat3 rX(float a){float c=cos(a),s=sin(a);return mat3(1.,0.,0.,0.,c,s,0.,-s,c);}
vec3 stars(vec2 p){vec3 c=vec3(0.);for(int L=0;L<3;L++){float fl=float(L),sc=38.+fl*40.;vec2 q=p*sc+vec2(uSpin*(.15+fl*.1),0.)*sc*.1;vec2 id=floor(q);float h=h2(id+fl*17.);if(h>.972){vec2 f=fract(q)-.5-(vec2(h2(id+3.),h2(id+7.))-.5)*.5;float d=length(f);float tw=.55+.45*sin(uT*(.8+h*3.)+h*90.)+uTre*.6*sin(uT*9.+h*30.);c+=mix(vec3(.75,.82,1.),vec3(1.,.86,.72),h2(id+1.))*smoothstep(.11,0.,d)*max(tw,.1)*(.35+(h-.972)*25.)/(1.+fl*.6);}}return c;}
void main(){vec2 p=(vUv-.5)*vec2(uRes.x/uRes.y,1.)*2.;vec3 col=stars(p)*.9;vec3 Ld=normalize(vec3(-.75,.3,.55));int K=int(uKind+.5);
 if(K==4){vec2 g=(p-vec2(0.,.05))/(.95*uZoom);g=mat2(cos(.35),-sin(.35),sin(.35),cos(.35))*g;g.y/=.42+.3*sin(uTilt);float r=length(g);float th=atan(g.y,g.x)-uSpin-uT*.04*(1.+uBass*.8);
   float s=th-log(r+.03)*2.7;float arm=pow(.5+.5*cos(2.*s),2.5);float dust=fbm(vec3(g*5.,uT*.02));float dens=arm*exp(-r*2.)*(.55+.9*dust)*smoothstep(1.5,.2,r);float lane=smoothstep(.45,.75,fbm(vec3(g*7.+3.,1.)))*arm*smoothstep(.05,.3,r);
   vec3 gc=mix(vec3(.4,.5,1.),vec3(1.,.72,.5),exp(-r*2.6));col+=gc*dens*1.6*(1.-lane*.7);col+=vec3(1.,.88,.72)*exp(-r*7.)*(1.4+uBass*1.6)+vec3(1.,.6,.85)*exp(-r*3.)*.25*(1.+uMid);
   vec2 sq=g*55.;vec2 id=floor(sq);float hs=h2(id);col+=vec3(.85,.9,1.)*step(.93,hs)*smoothstep(.3,0.,length(fract(sq)-.5))*arm*exp(-r*1.4)*(1.+uTre*1.5)*(.5+.5*sin(uT*3.+hs*50.));
   float sn=uFlT;if(sn<2.){vec2 sp=vec2(cos(uFlA),sin(uFlA))*.45;float d=length(p-sp);col+=vec3(.8,.9,1.)*exp(-d*d*(400.-sn*170.))*(1.-sn*.5)*2.+vec3(.6,.7,1.)*smoothstep(.02,0.,abs(d-sn*.35))*(1.-sn*.5);}
   col*=1.+uPulse*.3;gl_FragColor=vec4(col,1.);return;}
 float R=(K==1?.36:.5)*uZoom;vec2 cc=(p-vec2(0.,.06))/R;float d2=dot(cc,cc);float rr=sqrt(d2);mat3 M=rX(uTilt)*rY(uSpin);
 if(K==3){float an=atan(cc.y,cc.x);if(d2<1.){float z=sqrt(1.-d2);vec3 n=vec3(cc,z),nn=M*n;float pl=fbm(nn*3.+vec3(0.,0.,uT*.06))+.5*fbm(nn*9.-vec3(uT*.12));float gran=noise(nn*40.+uT*.3);
     vec3 sc=mix(vec3(1.,.32,.04),vec3(1.,.86,.45),smoothstep(.45,1.05,pl+gran*.15));col=sc*(.55+.65*pow(z,.5))*(1.3+uBass*.5);}
   else{float rays=fbm(vec3(an*3.,rr*2.-uT*.25,uT*.05));float co=exp(-(rr-1.)*3.2)*(.7+.7*rays)*(1.+uBass*1.4+uPulse*.6);col+=vec3(1.,.55,.18)*co*.9+vec3(1.,.85,.5)*exp(-(rr-1.)*14.)*.9;
     if(uFlT<3.){float da=atan(sin(an-uFlA),cos(an-uFlA));float reach=.15+uFlT*.9;float jet=exp(-da*da*40.)*exp(-(rr-1.)/reach)*smoothstep(1.,1.02,rr)*(1.-uFlT/3.);float tex=fbm(vec3(da*8.,rr*3.-uFlT*2.,uT));col+=vec3(1.,.5,.15)*jet*(1.2+tex*1.5)*2.;}}
   gl_FragColor=vec4(col,1.);return;}
 vec3 bodyC=vec3(0.);float hit=0.;float z=0.;vec3 n=vec3(0.),nn=vec3(0.);if(d2<1.){hit=1.;z=sqrt(1.-d2);n=vec3(cc,z);nn=M*n;}
 float dif=clamp(dot(n,Ld),0.,1.);float rimS=pow(1.-z,2.5);
 if(K==0){if(hit>0.){float land=smoothstep(.49,.54,fbm(nn*1.7)+.07*fbm(nn*6.));float day=smoothstep(-.05,.35,dot(n,Ld));vec3 oc=vec3(.01,.05,.13);vec3 lc=mix(vec3(.1,.16,.05),vec3(.34,.28,.18),fbm(nn*5.));
     float ct=noise(nn*38.)*noise(nn*95.+3.);float lights=smoothstep(.42,.7,ct)*land*(1.-day)*(1.2+.4*sin(uT*2.+nn.x*30.)*uTre);float cl=smoothstep(.55,.78,fbm(nn*3.+vec3(uT*.008,0.,0.)));
     bodyC=mix(oc,lc,land)*day*1.2+vec3(1.,.7,.32)*lights*1.5+vec3(.95)*cl*(day*.85+.04);bodyC+=vec3(1.,.9,.7)*pow(max(dot(reflect(-Ld,n),vec3(0.,0.,1.)),0.),40.)*(1.-land)*day*.7;
     bodyC+=vec3(.3,.6,1.)*rimS*(.25+day)*(1.+uMid*.8);}
   float halo=exp(-max(rr-1.,0.)*18.)*(1.-hit*.5)*(.35+.65*clamp(dot(normalize(cc),Ld.xy)+.4,0.,1.));col+=vec3(.35,.62,1.)*halo*(.8+uMid*.9+uPulse*.5);}
 if(K==2){if(hit>0.){float cr=1.-abs(fbm(nn*3.5)*2.-1.);float lava=pow(cr,7.)*(1.+uBass*1.6+uPulse);float crust=fbm(nn*6.);bodyC=vec3(.09,.06,.06)*(.3+dif*1.3)*(.6+crust)+vec3(1.,.33,.05)*lava*1.6+vec3(1.,.75,.3)*pow(cr,16.)*1.5;
     bodyC+=vec3(1.,.4,.1)*rimS*.4;}
   float halo=exp(-max(rr-1.,0.)*12.)*(1.-hit*.6);col+=vec3(1.,.35,.08)*halo*(.5+uBass*.9+uPulse*.6);}
 if(K==1){if(hit>0.){float w=fbm(nn*vec3(2.,9.,2.)+vec3(uT*.01,0.,0.));float b=sin(nn.y*24.+w*5.);vec3 sc=mix(vec3(.82,.68,.45),vec3(.98,.9,.72),.5+.5*b);sc=mix(sc,vec3(.6,.45,.3),smoothstep(.6,.9,sin(nn.y*9.+w*3.))*.5);
     bodyC=sc*(.08+dif*1.15);bodyC+=vec3(1.,.82,.55)*rimS*.25*(1.+uMid);}
   col+=vec3(1.,.8,.5)*exp(-max(rr-1.,0.)*16.)*(1.-hit*.6)*(.25+uMid*.5+uPulse*.4);}
 col=mix(col,bodyC,hit);
 if(K==1){vec3 rn=rX(uTilt+.42)*rY(uSpin*.0)*vec3(0.,1.,.0);rn=normalize(rX(.0)*vec3(.0,cos(uTilt+.42),sin(uTilt+.42)));vec3 o=vec3(cc,4.);float t=dot(rn,o)/rn.z;vec3 P=o-vec3(0.,0.,t);float r2=length(P);
   if(r2>1.25&&r2<2.25){float rb=fbm(vec3(r2*22.,0.,1.));float a=smoothstep(1.25,1.32,r2)*smoothstep(2.25,2.15,r2)*(1.-smoothstep(1.86,1.9,r2)*smoothstep(1.97,1.93,r2))*(.45+.55*rb);
     vec3 rc=mix(vec3(.75,.62,.45),vec3(.95,.88,.74),rb);float sh=1.;if(dot(P,Ld)<0.&&length(P-dot(P,Ld)*Ld)<1.)sh=.12;float lit=(.35+.65*abs(Ld.y))*sh;
     float th=atan(P.y,P.x);vec2 sq=vec2(r2*90.,th*120.+uT*(3./r2));float sp=step(.96,h2(floor(sq)))*(.4+uTre*1.5)*(.5+.5*sin(uT*6.+h2(floor(sq))*40.));
     vec3 ring=rc*lit+vec3(1.)*sp*.6*sh;bool front=hit<.5||P.z>z;if(front)col=mix(col,ring,a);}}
 col*=1.+uPulse*.15;gl_FragColor=vec4(col,1.);}`;
const COSMIC_KINDS=['Saturn','Earth','Molten','Sun','Galaxy'],COSMIC_K={Earth:0,Saturn:1,Molten:2,Sun:3,Galaxy:4};
class CosmicScene extends GLScene{
  constructor(pv){super(pv);this.variants=COSMIC_KINDS;this.variant=store.get('cos.v','Saturn');this.spin=0;this.sv=.12;this.tilt=.18;this.zoom=1;this.flT=9;this.flA=0;this.pulse=0;this.prMax=1.3}
  setVariant(v){this.variant=v;store.set('cos.v',v);this.flT=9}
  init(){const T=T3;this.U={uRes:{value:new T.Vector2(1,1)},uT:{value:0},uKind:{value:1},uZoom:{value:1},uBass:{value:0},uMid:{value:0},uTre:{value:0},uSpin:{value:0},uTilt:{value:.2},uFlA:{value:0},uFlT:{value:9},uPulse:{value:0}};
    this.quad=new T.Mesh(this.geo,new T.ShaderMaterial({uniforms:this.U,vertexShader:QUAD_V,fragmentShader:COS_F}));this.quad.frustumCulled=false;this.sc.add(this.quad)}
  down(p){p.sx=p.x;p.sy=p.y;p.t0=this.t;const ps=[...this.ptrs.values()];if(ps.length===2){this.pinch={d:Math.hypot(ps[0].x-ps[1].x,ps[0].y-ps[1].y),z:this.zoom}}}
  move(p){const ps=[...this.ptrs.values()];if(ps.length>=2&&this.pinch){const d=Math.hypot(ps[0].x-ps[1].x,ps[0].y-ps[1].y);this.zoom=clamp(this.pinch.z*d/this.pinch.d,.45,2.4);return}
    this.sv=lerp(this.sv,(p.vx||0)/this.w*2.2,.4);this.tilt=clamp(this.tilt+(p.vy||0)/this.h*.02,-.6,.8)}
  up(p){if(this.ptrs.size<2)this.pinch=null;if(Math.hypot(p.x-p.sx,p.y-p.sy)<12&&this.t-p.t0<.3){const a=Math.atan2(-(p.y-this.h*.47),p.x-this.w/2);this.flA=this.variant==='Galaxy'?Math.atan2(-(p.y-this.h/2),p.x-this.w/2):a;this.flT=0;this.pulse=1;haptic(12);if(!this.preview&&!WP)SFX.tone('sine',90,40,.6,.25)}}
  update(dt,A){this.t+=dt;this.A=A;if(!this.ptrs.size){this.sv=lerp(this.sv,.1*(this.variant==='Galaxy'?.3:1),dt*.5)}this.spin+=this.sv*dt*(A.active?1+A.mid*.6:1);this.flT+=dt;this.pulse=Math.max(0,this.pulse-dt*1.5);if(A.active&&A.beat)this.pulse=Math.max(this.pulse,.6*A.bass)}
  render(){const U=this.U,A=this.A||{};U.uRes.value.set(this.w,this.h);U.uT.value=this.t;U.uKind.value=COSMIC_K[this.variant]??1;U.uZoom.value=this.zoom;U.uBass.value=A.active?A.bass:0;U.uMid.value=A.active?A.mid:0;U.uTre.value=A.active?A.treble:0;
    U.uSpin.value=this.spin;U.uTilt.value=this.tilt;U.uFlA.value=this.flA;U.uFlT.value=this.flT;U.uPulse.value=this.pulse;this.gl.r.setRenderTarget(null);this.gl.r.render(this.sc,this.cam)}}

/* ---------------- GLOOP GANG: four soft-body jelly monsters ---------------- */
const GLOOPS=[{id:'mint',top:'#a8ffe4',bot:'#22d6a0',rim:'#d8fff2',eyes:1,acc:'antenna',s:.82,ink:'#063b2c'},{id:'coral',top:'#ffc0ad',bot:'#ff4f6d',rim:'#ffe0e6',eyes:2,acc:'horns',s:1,ink:'#4a0a1c'},
  {id:'grape',top:'#dcc2ff',bot:'#8048ff',rim:'#efe4ff',eyes:3,acc:'ears',s:.78,ink:'#22074a'},{id:'sunny',top:'#fff0b0',bot:'#ffad1f',rim:'#fff7dc',eyes:2,acc:'leaf',s:.7,ink:'#4a2a00'}];
function drawGloopFace(g,F,sp,t){const W=256;g.clearRect(0,0,W,W);const cx=x=>(x*.5+.5)*W,cy=y=>(1-(y*.5+.5))*W,ink=sp.ink,E=W*(sp.eyes===1?.12:.075);
  const pos=sp.eyes===1?[[0,.22]]:sp.eyes===2?[[-.28,.2],[.28,.2]]:[[-.32,.16],[.32,.16],[0,.36]];g.lineCap='round';
  for(const[i,q]of pos.entries()){const X=cx(q[0])+F.look[0]*E*.5,Y=cy(q[1])+F.look[1]*E*.35,m=F.eye;g.strokeStyle=ink;g.fillStyle=ink;
    if(m==='happy'){g.lineWidth=E*.42;g.beginPath();g.arc(X,Y+E*.35,E*.72,Math.PI*1.12,Math.PI*1.88);g.stroke();continue}
    if(m==='closed'){g.lineWidth=E*.36;g.beginPath();g.arc(X,Y-E*.2,E*.68,Math.PI*.15,Math.PI*.85);g.stroke();continue}
    if(m==='spiral'){g.lineWidth=E*.18;g.beginPath();for(let a=0;a<TAU*2.4;a+=.25){const r=a*E*.065,aa=a+t*9;g.lineTo(X+Math.cos(aa)*r,Y+Math.sin(aa)*r)}g.stroke();continue}
    if(m==='note'){g.font=`900 ${E*2}px system-ui,sans-serif`;g.textAlign='center';g.textBaseline='middle';g.fillText('♪',X,Y);continue}
    const op=clamp(F.eyeOpen,.1,1),rx=E*.8*(m==='wow'?1.2:1),ry=E*.95*op*(m==='wow'?1.2:1);g.fillStyle='#fff';g.beginPath();g.ellipse(X,Y,rx*1.2,ry*1.15,0,0,TAU);g.fill();
    g.fillStyle=ink;g.beginPath();g.ellipse(X+F.look[0]*rx*.2,Y+F.look[1]*ry*.15,rx*.78,ry*.8,0,0,TAU);g.fill();g.fillStyle='#fff';g.beginPath();g.arc(X-rx*.25,Y-ry*.3,rx*.24,0,TAU);g.fill()}
  const mx=cx(0),my=cy(-.12),o=clamp(F.mouthOpen,0,1);g.strokeStyle=ink;g.lineWidth=E*.28;
  if(o<.12){g.beginPath();g.arc(mx,my-E*.35,E*.6,Math.PI*.18,Math.PI*.82);g.stroke()}
  else{const ow=E*(.5+o*.3),oh=E*(.15+o*.85);g.fillStyle='#3a0a1c';g.beginPath();g.ellipse(mx,my+oh*.3,ow,oh,0,0,TAU);g.fill();if(sp.acc==='horns'){g.fillStyle='#fff';for(const s of[-1,1]){g.beginPath();g.moveTo(mx+s*ow*.55,my-oh*.55);g.lineTo(mx+s*ow*.3,my-oh*.55);g.lineTo(mx+s*ow*.42,my-oh*.1);g.fill()}}}
  const r=g.createRadialGradient(0,0,0,0,0,W*.07);for(const x of[cx(-.52),cx(.52)]){g.save();g.translate(x,cy(-.02));g.fillStyle=`rgba(255,80,130,${.45*F.blush})`;g.beginPath();g.ellipse(0,0,W*.07,W*.045,0,0,TAU);g.fill();g.restore()}}
function buildGloop(sp){const T=T3,k=kit(),root=new T.Group(),fc=document.createElement('canvas');fc.width=fc.height=256;const ft=new T.CanvasTexture(fc);ft.encoding=T.sRGBEncoding;const S=[.9,.58,1,.55];
  const U={uT:{value:0},uSq:{value:0},uMelt:{value:0},uPress:{value:0},uBreath:{value:0},uPokeT:{value:9},uPull:{value:new T.Vector3()},uShear:{value:new T.Vector3()},uPoke:{value:new T.Vector4(0,0,1,0)},uS:{value:new T.Vector4(...S)},
    uFace:{value:ft},uTop:{value:new T.Color(sp.top)},uBot:{value:new T.Color(sp.bot)},uRim:{value:new T.Color(sp.rim)},uKey:{value:new T.Vector3(-.45,.75,.7)},uTint:{value:new T.Color('#fff')},uFill:{value:new T.Color('#fff')},uStarC:{value:new T.Color('#fff')},uFillLv:{value:0},uFillA:{value:0},uStar:{value:0},uGlow:{value:0}};
  root.add(new T.Mesh(new T.SphereGeometry(1,64,44),new T.ShaderMaterial({uniforms:U,vertexShader:MOCHI_V,fragmentShader:MOCHI_F})));const jm=new T.MeshPhysicalMaterial({color:sp.bot,roughness:.25,clearcoat:1}),acc=[];
  if(sp.acc==='antenna'){const a=new T.Group(),st=new T.Mesh(new T.CylinderGeometry(.025,.035,.4,10),jm);st.position.y=.2;const b=new T.Mesh(k.SPH,new T.MeshBasicMaterial({color:'#eafff7',toneMapped:false}));b.scale.setScalar(.1);b.position.y=.44;a.add(st,b);acc.push({o:a,p:[0,1,0],r:0})}
  if(sp.acc==='horns')for(const s of[-1,1]){const h=new T.Mesh(new T.ConeGeometry(.11,.32,20),new T.MeshPhysicalMaterial({color:'#fff3e6',roughness:.3,clearcoat:1}));acc.push({o:h,p:[s*.5,.85,.1],r:-s*.45})}
  if(sp.acc==='ears')for(const s of[-1,1]){const e=new T.Mesh(k.SPH,jm);e.scale.set(.2,.32,.1);acc.push({o:e,p:[s*.7,.75,0],r:-s*.7})}
  if(sp.acc==='leaf'){const l=new T.Mesh(k.SPH,new T.MeshPhysicalMaterial({color:'#5be37a',roughness:.4,clearcoat:.6}));l.scale.set(.2,.06,.11);acc.push({o:l,p:[.05,1,0],r:.5})}
  acc.forEach(a=>root.add(a.o));return{root,U,ft,fctx:fc.getContext('2d'),acc,S}}
class GloopScene extends Scene{
  constructor(pv){super(pv);this.gang=[];this.stars=[...Array(160)].map(()=>({x:rnd(),y:Math.pow(rnd(),1.3)*.75,s:rnd(.5,1.6),a:rnd(.1,.45),ph:rnd(TAU)}));this.beatN=0}
  attach(el){if(!HAS3D||this.gl)return;const T=T3;kit();const G=sharedGL();if(G.cv.parentNode!==el)el.insertBefore(G.cv,el.children[1]||null);G.cv.style.display='block';
    const sc=new T.Scene(),cam=new T.PerspectiveCamera(22,1,1,1e5);sc.environment=G.env;const key=new T.DirectionalLight('#fff',1.2);key.position.set(-.5,.9,1.3);sc.add(key,new T.HemisphereLight('#f0e6ff','#140c1e',.4));
    this.gl={r:G.r,cv:G.cv,sc,cam,pr:Math.min(G.maxPr,this.preview?1:1.4)};this.gang=GLOOPS.map((sp,i)=>{const M=buildGloop(sp);sc.add(M.root);return{sp,M,x:0,y:0,vx:0,vy:0,ground:true,sq:0,sqv:0,sh:0,shv:0,poke:9,pd:[0,0,1],F:{eye:'normal',eyeOpen:1,look:[0,0],mouthOpen:0,blush:.6},look:[0,0],blink:rnd(1,3),bt:0,hopT:rnd(2,6),dz:0,grab:null,i,ft:0,mood:null,moodT:0}});this.layoutGL();this.place()}
  detach(){if(!this.gl)return;this.gl.r.clear();this.gl.cv.style.display='none';this.gl.sc.clear();this.gl=null}
  dispose(){this.detach()}
  layoutGL(){const g=this.gl;if(!g||this.w<2)return;g.r.setPixelRatio(g.pr);g.r.setSize(this.w,this.h,false);GLS.pr=g.pr;g.cv.style.width=this.w+'px';g.cv.style.height=this.h+'px';const d=this.h/2/Math.tan(11*Math.PI/180);g.cam.aspect=this.w/this.h;g.cam.position.set(0,0,d);g.cam.near=d*.2;g.cam.far=d*3;g.cam.updateProjectionMatrix()}
  resize(w,h){const first=this.w===1;super.resize(w,h);this.U=Math.min(w*.135,h*.085);this.floor=h*.76;if(first||!this.placed)this.place();this.layoutGL()}
  place(){if(!this.gang.length||this.w<2)return;this.placed=1;const xs=[.16,.4,.64,.86];this.gang.forEach((b,i)=>{b.x=this.w*xs[i];b.y=i===3?this.floor-this.U*1.5:this.floor;b.ground=i!==3})}
  R(b){return this.U*b.sp.s}
  hit(b,x,y){const R=this.R(b);return Math.abs(x-b.x)<R*1.05&&y<b.y+R*.1&&y>b.y-R*1.55}
  down(p){p.sx=p.x;p.sy=p.y;p.t0=this.t;for(const b of[...this.gang].reverse())if(this.hit(b,p.x,p.y)){b.grab={p,ox:b.x-p.x,oy:b.y-p.y};b.ground=false;p.b=b;b.F.eye='wow';haptic(8);return}
    for(const b of this.gang){b.lookAt=[p.x,p.y];b.lookT=1.2}}
  move(p){}
  up(p){const b=p.b;if(!b)return;b.grab=null;const quick=this.t-p.t0<.25&&Math.hypot(p.x-p.sx,p.y-p.sy)<12;
    if(quick){b.poke=0;const R=this.R(b);b.pd=normalize3([(p.x-b.x)/R,(b.y-R*.7-p.y)/R,.8]);b.sqv+=1.4;b.vy=-this.h*.35;b.mood='giggle';b.moodT=1.2;if(!this.preview&&!WP)SFX.tone('sine',500+b.i*120,900+b.i*150,.15,.16)}
    else{b.vx=clamp(p.vx||0,-3000,3000)*.8;b.vy=clamp(p.vy||0,-3000,3000)*.8;if(Math.hypot(b.vx,b.vy)>1800){b.spinny=1;if(!this.preview&&!WP)SFX.tone('sine',900,300,.3,.1)}}}
  update(dt,A){this.t+=dt;this.A=A;const w=this.w,h=this.h,g=h*2.6;if(A.beat)this.beatN++;const night=dayPhase()==='night',mus=A.active&&A.level>.08;
    for(const b of this.gang){const R=this.R(b);
      if(b.grab){const tx=b.grab.p.x+b.grab.ox,ty=b.grab.p.y+b.grab.oy;b.vx=(tx-b.x)/Math.max(dt,.008)*.35;b.vy=(ty-b.y)/Math.max(dt,.008)*.35;b.x+=(tx-b.x)*.35;b.y+=(ty-b.y)*.35}
      else{if(!b.ground){b.vy+=g*dt;b.x+=b.vx*dt;b.y+=b.vy*dt;if(b.y>=this.floor){b.y=this.floor;if(b.vy>h*.35){b.sqv+=Math.min(4,b.vy/h*2.5);b.vy*=-.38;if(!this.preview&&!WP&&b.vy<-h*.2)SFX.tone('sine',220,110,.08,.12)}else{b.vy=0;b.ground=true;if(b.spinny){b.dz=2;b.spinny=0}}}b.vx*=Math.pow(.5,dt)}
        else{b.vx*=Math.pow(.02,dt);b.x+=b.vx*dt}
        if(b.x<R){b.x=R;b.vx=Math.abs(b.vx)*.6;b.sqv+=1}if(b.x>w-R){b.x=w-R;b.vx=-Math.abs(b.vx)*.6;b.sqv+=1}if(b.y<R*1.6){b.y=R*1.6;b.vy=Math.abs(b.vy)*.5}
        b.hopT-=dt;if(mus){if(A.beat&&b.ground&&(this.beatN+b.i)%2===0){b.vy=-h*(.18+A.bass*.12);b.ground=false;b.sqv-=1.5}}
        else if(b.hopT<0&&b.ground&&!night){b.hopT=rnd(3,8);b.vy=-h*rnd(.2,.42);b.vx=rnd(-1,1)*w*.25;b.ground=false;b.sqv-=1.2}}
      for(const o of this.gang){if(o===b)continue;const ro=this.R(o),dx=b.x-o.x,dy=(b.y-R*.7)-(o.y-ro*.7),d=Math.hypot(dx,dy),m=(R+ro)*.88;if(d<m&&d>0){const push=(m-d)/d*.5;b.x+=dx*push;if(!b.ground)b.y+=dy*push;b.sqv+=.15}}
      b.sqv+=(-b.sq*90-b.sqv*7)*dt;b.sq=clamp(b.sq+b.sqv*dt,-.3,.4);const tsh=clamp(-b.vx/w*.25,-.35,.35)+(mus&&A.active?Math.sin((A.beatPhase||0)*TAU+b.i)*.12:0);b.shv+=(-(b.sh-tsh)*100-b.shv*6)*dt;b.sh+=b.shv*dt;b.poke+=dt;b.dz=Math.max(0,b.dz-dt);
      // face
      const F=b.F;let eye='normal',open=1,mo=0,look=[0,0];b.moodT-=dt;
      const p=this.ptr;if(p){const dx=p.x-b.x,dy=p.y-(b.y-R*.8),d=Math.hypot(dx,dy)+1;look=[dx/d,dy/d]}else if(b.lookT>0){b.lookT-=dt;const dx=b.lookAt[0]-b.x,dy=b.lookAt[1]-(b.y-R*.8),d=Math.hypot(dx,dy)+1;look=[dx/d,dy/d]}
      else{const o=this.gang[(b.i+1+((this.t/4)|0))%this.gang.length];if(o!==b){const dx=o.x-b.x,d=Math.abs(dx)+1;look=[dx/d*.8,.1]}}
      if(night&&!mus){eye='closed';open=0}if(b.grab){eye='wow';mo=.5}if(!b.ground&&!b.grab){eye='wow';mo=.35}if(b.moodT>0&&b.mood==='giggle'){eye='happy';mo=.4+.3*Math.sin(this.t*30)}
      if(mus){eye=(this.beatN+b.i)%6<4?'happy':'note';mo=clamp(A.mid*1.2,.1,1)}if(b.dz>0){eye='spiral';mo=.3}
      b.blink-=dt;if(b.blink<0){b.blink=rnd(2,5);b.bt=.14}if(b.bt>0){b.bt-=dt;if(eye==='normal')open=Math.abs(b.bt-.07)/.07}
      b.look[0]=lerp(b.look[0],look[0],.15);b.look[1]=lerp(b.look[1],look[1],.15);Object.assign(F,{eye,eyeOpen:lerp(F.eyeOpen,open,.4),mouthOpen:lerp(F.mouthOpen,mo,.3),look:b.look,blush:mus?1:.6})}
    if(this.gl)this.pose()}
  pose(){const w=this.w,h=this.h;for(const b of this.gang){const M=b.M,R=this.R(b),U=M.U;M.root.position.set(b.x-w/2,h/2-b.y,0);M.root.scale.setScalar(R);M.root.rotation.set(0,b.look[0]*.25,b.dz>0?Math.sin(this.t*9)*.12:0);
      U.uT.value=this.t;U.uSq.value=b.sq;U.uShear.value.set(b.sh,0,0);U.uBreath.value=Math.sin(this.t*2.2+b.i);U.uPoke.value.set(b.pd[0],b.pd[1],b.pd[2],b.poke<1.2?1:0);U.uPokeT.value=b.poke;U.uGlow.value=this.A&&this.A.active?this.A.bass*.6:0;
      const u={sq:b.sq,melt:0,press:0,breath:0,shear:[b.sh,0,0],pull:[0,0,0],S:M.S};for(const a of M.acc){const q=mochiDeform(a.p,u);a.o.position.set(q[0],q[1]-.03,q[2]);a.o.rotation.z=a.r-b.sh*.8}
      if(this.t-b.ft>1/24){b.ft=this.t;drawGloopFace(M.fctx,b.F,b.sp,this.t);M.ft.needsUpdate=true}}}
  draw(ctx){const w=this.w,h=this.h,A=this.A||{},tw=A.active?A.treble:0;for(const s of this.stars){ctx.globalAlpha=s.a*(.55+.45*Math.sin(this.t*(1+tw*3)+s.ph));ctx.fillStyle='#fff';ctx.fillRect(s.x*w,s.y*h,s.s,s.s)}ctx.globalAlpha=1;
    const fy=this.floor;let gr=ctx.createLinearGradient(0,fy-this.U*.3,0,h);gr.addColorStop(0,'rgba(30,20,50,0)');gr.addColorStop(.25,'rgba(30,20,50,.3)');gr.addColorStop(1,'rgba(14,9,26,.7)');ctx.fillStyle=gr;ctx.fillRect(0,fy-this.U*.3,w,h);
    ctx.globalCompositeOperation='lighter';for(const b of this.gang){const R=this.R(b),k=b.ground?1:clamp(1-(fy-b.y)/(h*.5),.2,1);ctx.save();ctx.translate(b.x,fy);ctx.scale(1,.2);glow(ctx,b.sp.bot,0,0,R*1.4*k,.35*k+(A.active?A.bass*.25:0));ctx.restore()}ctx.globalCompositeOperation='source-over';
    if(this.gl){this.gl.r.render(this.gl.sc,this.gl.cam)}
    for(const b of this.gang)if(b.dz>0){const R=this.R(b);for(let i=0;i<3;i++){const a=this.t*5+i*TAU/3;drawShape(ctx,'stars',b.x+Math.cos(a)*R*.7,b.y-R*1.7+Math.sin(a)*R*.15,R*.08,a,'#FFE29A',.9)}}}}

/* ---------------- LOOPS: seamless Flow videos that breathe with the music ---------------- */
const LOOP_DB={db:null,open(){return this.db||(this.db=new Promise((res,rej)=>{try{const r=indexedDB.open('pulsify',1);r.onupgradeneeded=()=>r.result.createObjectStore('media');r.onsuccess=()=>res(r.result);r.onerror=()=>rej(r.error)}catch(e){rej(e)}}))},
  async put(k,v){const d=await this.open();return new Promise((res,rej)=>{const t=d.transaction('media','readwrite');t.objectStore('media').put(v,k);t.oncomplete=res;t.onerror=()=>rej(t.error)})},
  async get(k){const d=await this.open();return new Promise((res,rej)=>{const q=d.transaction('media').objectStore('media').get(k);q.onsuccess=()=>res(q.result);q.onerror=()=>rej(q.error)})},
  async del(k){const d=await this.open();return new Promise(res=>{const t=d.transaction('media','readwrite');t.objectStore('media').delete(k);t.oncomplete=res})}};
class LoopScene extends Scene{
  constructor(pv,cfg){super(pv);this.cfg=cfg;this.poster=cfg.poster?img(cfg.poster):null;this.v=null;this.flash=0;this.z=1;this.load()}
  async load(){let src=this.cfg.src;if(this.cfg.local){try{const b=await LOOP_DB.get(this.cfg.local);if(!b)return;src=URL.createObjectURL(b);this.url=src}catch(e){return}}if(this.dead)return;this.v=mkVideo(src,true);this.v.play().catch(()=>{})}
  update(dt,A){this.t+=dt;this.A=A;if(this.v&&this.v.paused&&!this.dead)this.v.play().catch(()=>{});const tz=1+(A.active?A.bass*.035:0);this.z=lerp(this.z,tz,A.active&&A.beat?.6:.12);if(A.active&&A.beat)this.flash=Math.min(1,.4+A.bass*.6);this.flash*=Math.pow(.02,dt)}
  down(p){p.sx=p.x;p.sy=p.y}up(p){if(Math.hypot(p.x-p.sx,p.y-p.sy)<12){this.flash=.7;this.z=1.03}}
  draw(ctx){const ok=this.v&&this.v.readyState>=2&&drawCover(ctx,this.v,this.w,this.h,this.z);if(!ok&&this.poster&&ready(this.poster))drawCover(ctx,this.poster,this.w,this.h,this.z);
    const A=this.A||{};if(A.active||this.flash>.02){const c=this.cfg.glow||'#CDB4FF';ctx.globalCompositeOperation='lighter';const g=ctx.createRadialGradient(this.w/2,this.h*1.02,0,this.w/2,this.h*1.02,this.h*.9);g.addColorStop(0,hexA(c,.3*(A.active?A.bass:0)+.25*this.flash));g.addColorStop(1,hexA(c,0));ctx.fillStyle=g;ctx.fillRect(0,0,this.w,this.h);ctx.globalCompositeOperation='source-over'}
    const vg=ctx.createRadialGradient(this.w/2,this.h/2,this.h*.35,this.w/2,this.h/2,this.h*.75);vg.addColorStop(0,'rgba(0,0,0,0)');vg.addColorStop(1,'rgba(0,0,0,.55)');ctx.fillStyle=vg;ctx.fillRect(0,0,this.w,this.h)}
  dispose(){this.dead=1;if(this.v){this.v.pause();this.v.removeAttribute('src');this.v.load()}if(this.url)URL.revokeObjectURL(this.url)}}

/* ---------------- CARS: your car photos, lit entirely in code ---------------- */
/* A rig is drawn on the photo in image coordinates (0..1): LED strips (polylines) that light up with an ignition sweep,
   lamp centres that bloom and throw beams, a ground line for reflections and underglow. Strips can be mirrored. */
const CAR_DEFAULT_RIG={mirror:true,strips:[{k:'head',pts:[[.168,.551],[.182,.563],[.212,.576],[.252,.585],[.293,.591]]},{k:'drl',pts:[[.176,.546],[.205,.557],[.245,.571],[.282,.583]]}],lamps:[[.235,.574]],ground:.69,ug:[.2,.8]};
const CAR_GLOWS=[['Ice','#CFF6FF'],['Cyan','#00E5FF'],['Violet','#B388FF'],['Magenta','#FF2BD6'],['Lime','#7CFF4F'],['Amber','#FFB020'],['Red','#FF3B3B'],['Off',null]];
class CarScene extends Scene{
  constructor(pv,cfg){super(pv);this.cfg=cfg;this.im=null;this.iw=900;this.ih=1600;this.rig=store.get('rig.'+cfg.id,null)||cfg.rig||null;this.ug=store.get('car.ug.'+cfg.id,cfg.ugDefault||1);this.on=true;this.ign=pv?1:0;this.flash=0;this.hb=0;this.beam=0;this.bx=0;this.by=0;this.edit=null;
    this.rain=[...Array(pv?40:150)].map(()=>({x:rnd(),y:rnd(),v:rnd(.5,1),l:rnd(.6,1.4)}));this.fog=[...Array(6)].map(()=>({x:rnd(),y:rnd(.5,.9),r:rnd(.3,.6),v:rnd(-.01,.01)}));this.loadImg()}
  async loadImg(){let src=this.cfg.src;if(this.cfg.local){try{const b=await LOOP_DB.get(this.cfg.local);if(!b)return;src=URL.createObjectURL(b);this.url=src}catch(e){return}}this.im=img(src)}
  controls(){return[{type:'swatch',label:'Underglow',items:CAR_GLOWS.map(g=>({name:g[0],cols:g[1]?[g[1],g[1]]:['#222','#444']})),value:this.ug,set:i=>{this.ug=i;store.set('car.ug.'+this.cfg.id,i)}},
    {type:'buttons',label:'Lights',items:[{name:this.edit?'Done editing':'✎ Edit light map',fn:()=>{this.edit?this.endEdit():this.startEdit()}},...(this.edit?[{name:'Draw LED strip',on:this.edit.mode==='strip',fn:()=>this.edit.mode='strip'},{name:'Add lamp',on:this.edit.mode==='lamp',fn:()=>this.edit.mode='lamp'},{name:'Set ground',on:this.edit.mode==='ground',fn:()=>this.edit.mode='ground'},{name:(this.rig&&this.rig.mirror!==false?'Mirror on':'Mirror off'),fn:()=>{this.rig.mirror=!(this.rig.mirror!==false)}},{name:'Clear',fn:()=>{this.rig={mirror:true,strips:[],lamps:[],ground:.7}}}]:[{name:'Reset map',fn:()=>{store.set('rig.'+this.cfg.id,null);this.rig=this.cfg.rig||null}}])]}]}
  startEdit(){if(!this.rig)this.rig={mirror:true,strips:[],lamps:[],ground:.7};this.rig=JSON.parse(JSON.stringify(this.rig));this.edit={mode:'strip',cur:null};this.onCtl&&this.onCtl()}
  endEdit(){this.edit=null;store.set('rig.'+this.cfg.id,this.rig);this.onCtl&&this.onCtl();toast&&toast('Light map saved for this car')}
  layout(){const w=this.w,h=this.h;if(this.im&&ready(this.im)){this.iw=this.im.naturalWidth;this.ih=this.im.naturalHeight}const f=this.cfg.focus||[.5,.6];let s=(w/h>this.iw/this.ih*1.3)?h/this.ih*1.05:Math.max(w/this.iw,h/this.ih);const dw=this.iw*s,dh=this.ih*s;let ox=w/2-f[0]*dw,oy=h/2-f[1]*dh;
    ox=dw>=w?clamp(ox,w-dw,0):(w-dw)/2;oy=dh>=h?clamp(oy,h-dh,0):(h-dh)/2;this.s=s;this.ox=ox;this.oy=oy;this.sc=dw/900}
  P(u,v){return[this.ox+u*this.iw*this.s,this.oy+v*this.ih*this.s]} inv(x,y){return[(x-this.ox)/(this.iw*this.s),(y-this.oy)/(this.ih*this.s)]}
  down(p){p.sx=p.x;p.sy=p.y;p.t0=this.t;if(this.edit){const[u,v]=this.inv(p.x,p.y);const m=this.edit.mode;if(m==='strip'){this.edit.cur={k:'head',pts:[[u,v]]};this.rig.strips.push(this.edit.cur)}else if(m==='lamp')this.rig.lamps.push([u,v]);else if(m==='ground')this.rig.ground=v;return}
    this.beam=1;this.bx=p.x;this.by=p.y}
  move(p){if(this.edit){if(this.edit.cur){const[u,v]=this.inv(p.x,p.y),L=this.edit.cur.pts,q=L[L.length-1];if(Math.hypot(u-q[0],v-q[1])>.008)L.push([u,v])}return}this.bx=p.x;this.by=p.y}
  up(p){if(this.edit){if(this.edit.cur&&this.edit.cur.pts.length<2)this.rig.strips.pop();this.edit.cur=null;return}
    if(Math.hypot(p.x-p.sx,p.y-p.sy)<14&&this.t-p.t0<.3){const[u,v]=this.inv(p.x,p.y);if(u>.05&&u<.95&&v>.3&&v<this.gy()+.02){this.on=!this.on;if(this.on){this.ign=0;this.flash=1;if(!this.preview&&!WP)SFX.ignition()}}else{this.hb=1;if(!this.on){this.on=true;this.ign=0}}haptic(10)}}
  gy(){return this.rig?this.rig.ground:.7}
  update(dt,A){this.t+=dt;this.A=A;if(this.on)this.ign=Math.min(1,this.ign+dt*1.2);else this.ign=Math.max(0,this.ign-dt*3);if(A.active&&A.beat)this.flash=Math.min(1.2,.5+A.bass);this.flash*=Math.pow(.005,dt);this.hb*=Math.pow(.04,dt);if(!this.ptrs.size)this.beam=Math.max(0,this.beam-dt*1.5);
    const mus=A.active?A.bass*.7+A.mid*.2:.1+.06*Math.sin(this.t*1.3);this.I=this.ign*(.55+mus+this.flash*.7+this.hb);for(const r of this.rain){r.y+=r.v*dt*(.5+(A.active?A.treble*.6:0));if(r.y>1){r.y=0;r.x=rnd()}}for(const f of this.fog)f.x=(f.x+f.v*dt+1)%1}
  strips(){const R=this.rig;if(!R)return[];const out=[];for(const s of R.strips){out.push(s.pts);if(R.mirror!==false)out.push(s.pts.map(([u,v])=>[1-u,v]))}return out}
  lamps(){const R=this.rig;if(!R)return[];const out=[];for(const l of R.lamps){out.push(l);if(R.mirror!==false)out.push([1-l[0],l[1]])}if(!out.length)for(const s of this.strips()){const m=s[(s.length/2)|0];out.push(m)}return out}
  led(ctx,pts,k,wd,col,a){ctx.strokeStyle=col;ctx.globalAlpha=a;ctx.lineWidth=wd;ctx.lineCap='round';ctx.lineJoin='round';ctx.beginPath();const n=pts.length-1,end=k*n;for(let i=0;i<=n;i++){if(i>end+1)break;let[u,v]=pts[i];if(i>end&&i>0){const[u0,v0]=pts[i-1],f=end-(i-1);u=lerp(u0,u,f);v=lerp(v0,v,f)}const[x,y]=this.P(u,v);i?ctx.lineTo(x,y):ctx.moveTo(x,y)}ctx.stroke();ctx.globalAlpha=1}
  draw(ctx){this.layout();const A=this.A||{},I=this.I||0,k=this.ign,sc=this.sc,w=this.w,h=this.h;const im=this.im;
    if(im&&ready(im)){ctx.save();const br=1+(A.active?A.bass*.006:0);ctx.translate(w/2,h*.6);ctx.scale(br,br);ctx.translate(-w/2,-h*.6);ctx.filter=`brightness(${.75+I*.12})`;ctx.drawImage(im,this.ox,this.oy,this.iw*this.s,this.ih*this.s);ctx.filter='none';ctx.restore()}
    else{ctx.fillStyle='#9E96B0';ctx.font='600 14px "Plus Jakarta Sans",system-ui';ctx.textAlign='center';ctx.fillText(this.cfg.src||this.cfg.local?'Loading car…':'Add a car photo',w/2,h/2)}
    const ST=this.strips(),LP=this.lamps().map(l=>this.P(l[0],l[1])),[,gy]=this.P(0,this.gy()),[,by]=this.P(0,1.02),ugc=(CAR_GLOWS[this.ug]||[])[1];
    ctx.save();ctx.globalCompositeOperation='lighter';
    for(const f of this.fog){const x=f.x*w,y=f.y*h,r=f.r*w;const g=ctx.createRadialGradient(x,y,0,x,y,r);g.addColorStop(0,`rgba(150,170,210,${.03+I*.02})`);g.addColorStop(1,'rgba(150,170,210,0)');ctx.fillStyle=g;ctx.fillRect(x-r,y-r,r*2,r*2)}
    if(I>0)for(const H of LP){const px=H[0],py=gy+(by-gy)*.3,rr=(by-gy)*.8;ctx.save();ctx.translate(px,py);ctx.scale(1.3,.4);const g=ctx.createRadialGradient(0,0,0,0,0,rr);g.addColorStop(0,`rgba(215,245,255,${.18*Math.min(1.4,I)})`);g.addColorStop(1,'rgba(120,190,255,0)');ctx.fillStyle=g;ctx.beginPath();ctx.arc(0,0,rr,0,TAU);ctx.fill();ctx.restore();
      for(let i=-2;i<=2;i++){const x=H[0]+i*7*sc+Math.sin(this.t*2.3+i)*2*sc,g2=ctx.createLinearGradient(0,gy,0,gy+(by-gy)*.8);g2.addColorStop(0,`rgba(224,251,252,${.3*I/(1+Math.abs(i))})`);g2.addColorStop(1,'rgba(224,251,252,0)');ctx.fillStyle=g2;ctx.fillRect(x-1.5*sc,gy,3*sc,(by-gy)*.8)}}
    if(ugc&&k>0&&this.rig){const kk=k*(.55+(A.active?A.bass*.9:.25+.12*Math.sin(this.t*2))),[cx,cy]=this.P(.5,this.gy()),rx=.42*this.iw*this.s,g=ctx.createRadialGradient(cx,cy,0,cx,cy,rx);g.addColorStop(0,hexA(ugc,.75*kk));g.addColorStop(.45,hexA(ugc,.22*kk));g.addColorStop(1,hexA(ugc,0));ctx.fillStyle=g;ctx.save();ctx.translate(cx,cy);ctx.scale(1,.1);ctx.translate(-cx,-cy);ctx.beginPath();ctx.arc(cx,cy,rx,0,TAU);ctx.fill();ctx.restore();
      ctx.save();ctx.translate(cx,cy+(by-cy)*.12);ctx.scale(1,.32);const g2=ctx.createRadialGradient(0,0,0,0,0,rx*1.1);g2.addColorStop(0,hexA(ugc,.26*kk));g2.addColorStop(1,hexA(ugc,0));ctx.fillStyle=g2;ctx.beginPath();ctx.arc(0,0,rx*1.1,0,TAU);ctx.fill();ctx.restore()}
    if(k>0){const drl=Math.min(1,.55+I*.5+(A.active?A.mid*.3:0));for(const s of ST){this.led(ctx,s,k,16*sc,'#9fe8ff',.22*drl);this.led(ctx,s,k,7*sc,'#cff6ff',.55*drl);this.led(ctx,s,k,2.6*sc,'#ffffff',1)}
      if(k<1)for(const s of ST){const n=s.length-1,e=k*n,i=Math.min(n-1,Math.floor(e)),f=e-i;if(n<1)continue;const[x,y]=this.P(lerp(s[i][0],s[i+1][0],f),lerp(s[i][1],s[i+1][1],f));glow(ctx,'#E0FBFC',x,y,40*sc,1)}}
    if(I>0){const b=Math.min(1.6,I);for(const H of LP){glow(ctx,'#cfefff',H[0],H[1],(70+120*b)*sc,Math.min(1,.5*b));glow(ctx,'#ffffff',H[0],H[1],(24+30*b)*sc,Math.min(1,b));ctx.globalAlpha=Math.min(.85,.4*b);const sw=w*(.5+.6*b);ctx.drawImage(sprite('#7fdcff'),H[0]-sw*.5,H[1]-5*sc,sw,10*sc);ctx.globalAlpha=1}
      if(this.beam>.02)for(const H of LP){const ang=Math.atan2(this.by-H[1],this.bx-H[0]),len=Math.hypot(this.bx-H[0],this.by-H[1])+h*.3,sp=.22;ctx.save();ctx.translate(H[0],H[1]);ctx.rotate(ang);const g=ctx.createLinearGradient(0,0,len,0);g.addColorStop(0,`rgba(225,245,255,${.32*this.beam*b})`);g.addColorStop(1,'rgba(225,245,255,0)');ctx.fillStyle=g;ctx.beginPath();ctx.moveTo(0,-4*sc);ctx.lineTo(len,-len*sp);ctx.lineTo(len,len*sp);ctx.lineTo(0,4*sc);ctx.fill();ctx.restore()}
      if(this.hb>.05){const L0=LP[0]||[w/2,h/2],g=ctx.createRadialGradient(w/2,L0[1],0,w/2,L0[1],w*.9);g.addColorStop(0,`rgba(225,245,255,${.32*this.hb})`);g.addColorStop(1,'rgba(225,245,255,0)');ctx.fillStyle=g;ctx.fillRect(0,0,w,h)}}
    ctx.lineWidth=1;for(const r of this.rain){const x=r.x*w,y=r.y*h;let lit=0;for(const H of LP)lit=Math.max(lit,1-Math.hypot(x-H[0],y-H[1])/(260*sc));lit=Math.max(0,lit)*I;const a=.04+lit*.6;if(a<.05&&!this.preview&&Math.random()<.5)continue;ctx.strokeStyle=`rgba(210,240,255,${Math.min(.8,a)})`;ctx.beginPath();ctx.moveTo(x,y);ctx.lineTo(x-1,y+14*r.l*sc);ctx.stroke()}
    ctx.restore();
    if(this.edit){ctx.save();ctx.strokeStyle='#FFB36B';ctx.fillStyle='#FFB36B';ctx.lineWidth=2;for(const s of ST){ctx.beginPath();s.forEach((q,i)=>{const[x,y]=this.P(q[0],q[1]);i?ctx.lineTo(x,y):ctx.moveTo(x,y)});ctx.stroke()}for(const L of LP){ctx.beginPath();ctx.arc(L[0],L[1],7,0,TAU);ctx.stroke()}
      ctx.setLineDash([6,6]);ctx.beginPath();ctx.moveTo(0,gy);ctx.lineTo(w,gy);ctx.stroke();ctx.setLineDash([]);ctx.font='600 12px "Plus Jakarta Sans",system-ui';ctx.textAlign='center';ctx.fillText({strip:'Drag along a headlight strip',lamp:'Tap the centre of each lamp',ground:'Tap where the tyres meet the road'}[this.edit.mode],w/2,gy+22);ctx.restore()}}
  dispose(){if(this.url)URL.revokeObjectURL(this.url)}}
