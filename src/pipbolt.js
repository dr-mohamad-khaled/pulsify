/* ================= PIP & BOLT — sounds (the 2D characters live in buddies2d.js) ================= */
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

