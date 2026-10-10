const fs=require('fs'),vm=require('vm'),assert=require('assert/strict'),path=require('path');
const html=fs.readFileSync(path.join(__dirname,'../web/index.html'),'utf8');
let clock=1000,nextTimer=1;const timers=new Map(),calls=[];
const ctx={S:{myVerses:[],questions:[{id:'q1',q:'Length?',a:'12 m'},{id:'q2',q:'Value?',a:'1.5'}],sound:true},M:null,NLT:{},current:null,IS_APP:true,
 norm:v=>String(v||'').trim(),fetchNLT(){},drawAlarm:()=>calls.push('draw'),buzz:v=>calls.push(v),pauseWebSound(){},stopVoice(){},
 document:{querySelector:()=>null},$:()=>null,window:{AndroidAlarm:{listenMission:token=>calls.push(['listen',token]),cancelListening:()=>calls.push('cancel'),resumeSound(){},missionDone(){}}},navigator:{},
 setTimeout(fn,ms){const id=nextTimer++;timers.set(id,{fn,ms});return id;},clearTimeout(id){timers.delete(id)},webQuietUntil:0,webQuietT:null,lastQuietPing:0,alarmTimer:null,
 playVoiceAlert:event=>calls.push(event),finishAlarm(){},Date:{now:()=>clock}};
vm.createContext(ctx);
function load(start,end){const a=html.indexOf(start),b=html.indexOf(end,a);assert.ok(a>=0&&b>a,start+' has valid extraction boundaries');vm.runInContext(html.slice(a,b),ctx)}
load('const VERSE_PACKS=','/* ---------- extra icons');
vm.runInContext(html.match(/function allVerseIds\(\)\{[^\n]+/)[0],ctx);
vm.runInContext(html.match(/const POOL_TYPES=[^\n]+/)[0],ctx);
load('function versePool(','/* ---------- sun mascot');
load('const PHRASES=','let alarmTimer=');
load('function resolveType(','function canSpeak(');
load('function startShakeFallbackTimer(','function buzz(');
load('let webRec=null','/* shake detection */');
load('function onMotion(','function setupShake(');
load('function missionBack(','function renderTask(');
vm.runInContext('this.normAns=normAns;this.wordsOf=wordsOf;this.missions=MISSIONS;this.phrases=PHRASES;this.packs=AFFIRM_PACKS;this.quiz=QUIZ_TOPICS;this.pool=POOL_TYPES;',ctx);
function mission(type,task,cfg={}){ctx.M={phase:'task',cfg:{type,level:'medium',...cfg},task:task||null,need:3,done:0,msg:'',micFails:0};return ctx.M;}
function pending(){return timers.get(ctx.M.stepTimer).fn;}
function clear(){timers.clear();calls.length=0;}
assert.equal(ctx.packs.flatMap(p=>p.v).length,64);assert.equal(new Set(ctx.packs.flatMap(p=>p.v)).size,64);assert.ok(ctx.packs.every(p=>p.v.length===8));
assert.equal(ctx.normAns(0),'0');assert.equal(ctx.numericAnswer('1/2'),null);assert.equal(ctx.numericAnswer('12 m'),null);assert.equal(ctx.numericAnswer('1.50'),1.5);
assert.equal(ctx.similarity("I'm ready to learn.",'I am ready to learn.'),1);
assert.equal(ctx.similarity('I need to be perfect','I do not need to be perfect'),0);
assert.ok(ctx.similarity('learn to ready am I','I am ready to learn')<0.8);
assert.ok(ctx.similarity('I can','I can learn and I can improve')<0.8);
assert.ok(ctx.similarity('I can learn with steady practice','I can learn through steady practice')>=0.8);
// Only Math keeps the chosen difficulty; other missions randomise it every round.
{const seen=new Set();for(let i=0;i<60;i++){mission('memory',null,{level:'easy'});seen.add(ctx.newTask().digits.length);}assert.ok(seen.size>1,'memory difficulty is randomised, not fixed to the saved level');}
for(let i=0;i<20;i++){mission('math',null,{level:'easy'});const t=ctx.newTask();assert.ok(!/×/.test(t.q),'math keeps the chosen easy level');}
// Generate every mission at each difficulty, checking independent content invariants.
for(const type of Object.keys(ctx.missions))for(const level of ['easy','medium','hard']){
 mission(type,null,{level});const task=ctx.newTask();assert.ok(ctx.pool.includes(task.type));
 if(type!=='random')assert.equal(task.type,type);
 if(task.type==='math'){const expression=task.q.replace(/×/g,'*').replace(/−/g,'-');assert.equal(vm.runInNewContext(expression),task.a);}
 if(type==='memory'){assert.ok([4,6,8].includes(task.digits.length),'memory length is one of the three random levels');assert.match(task.digits,/^[1-9]\d+$/);}
 if(type==='tiles'){assert.equal(new Set(task.order).size,task.n);assert.deepEqual(Array.from(task.order).sort((a,b)=>a-b),Array.from({length:task.n},(_,i)=>i+1));}
 if(type==='unscramble'){assert.notEqual(task.letters,task.word);assert.equal([...task.letters].sort().join(''),[...task.word].sort().join(''));}
 if(type==='bible'){assert.ok(ctx.wordsOf(task.q).length>0,'bible verse length is picked at random');}
 if(type==='quiz'){assert.equal(task.opts.length,4);assert.equal(task.opts.filter(a=>a===task.a).length,1);}
}
for(const topic of ctx.quiz)for(const q of topic.q){assert.equal(q.length,5);assert.equal(new Set(q.slice(1)).size,4);}
// Deterministic randomness used to expose retry-loop repeats and original-word scrambles.
vm.runInContext('Math.random=()=>0;',ctx);
for(const type of ['affirm','bible','question','type','quiz','unscramble']){mission(type);ctx.M.task=ctx.newTask();const first=ctx.M.task;const next=ctx.newTask();assert.notEqual(next.q||next.word,first.q||first.word,type+' changes when alternatives exist');}
mission('random',null,{pool:['math','tiles']});ctx.M.task=ctx.newTask();assert.equal(ctx.M.task.type,'math');assert.equal(ctx.newTask().type,'tiles');
mission('random',null,{pool:['unknown','random']});assert.equal(ctx.newTask().type,'math');
mission('question');ctx.S.questions=[];assert.equal(ctx.newTask().type,'math');ctx.S.questions=[{id:'q1',q:'Length?',a:'12 m'}];
mission('affirm');ctx.M.noMic=true;assert.equal(ctx.newTask().type,'math');mission('shake');ctx.M.noShake=true;assert.equal(ctx.newTask().type,'math');
// Numeric input must match the whole answer, including units and fractions.
for(const bad of ['2.1','answer 21','21abc']){clear();mission('math',{type:'math',q:'12 + 9',a:21});ctx.checkAnswer(bad);assert.equal(ctx.M.done,0);assert.equal(ctx.M.flashing,undefined);}
clear();mission('math',{type:'math',q:'12 + 9',a:21});ctx.checkAnswer('21');assert.equal(ctx.M.flashing,true);pending()();assert.equal(ctx.M.done,1);
for(const [answer,bad] of [['12 m','12'],['1/2','1']]){clear();mission('question',{type:'question',a:answer});ctx.checkAnswer(bad);assert.equal(ctx.M.flashing,undefined);}
clear();mission('question',{type:'question',a:'1.5'});ctx.checkAnswer('1.50');assert.equal(ctx.M.flashing,true);
clear();mission('memory',{type:'memory',digits:'1234',phase:'show',showMs:3500});ctx.startMemoryTimer();const reveal=timers.get(ctx.M.task.timer).fn;
ctx.checkAnswer('1234');assert.equal(ctx.M.flashing,undefined,'cannot answer during reveal');ctx.missionBack();assert.equal(timers.size,0,'back clears memory timer');reveal();assert.equal(ctx.M.task.phase,'show');
ctx.M.phase='task';ctx.startMemoryTimer();timers.get(ctx.M.task.timer).fn();assert.equal(ctx.M.task.phase,'input');ctx.checkAnswer('1a234');assert.equal(ctx.M.flashing,false);assert.equal(ctx.M.done,0);
clear();mission('memory',{type:'memory',digits:'1234',phase:'input'});ctx.checkAnswer('1234');assert.equal(ctx.M.flashing,true);
clear();mission('type',{type:'type',q:'Small steps build results.'});ctx.checkAnswer('small steps build results.');assert.equal(ctx.M.flashing,true);
clear();mission('unscramble',{type:'unscramble',word:'STUDY'});ctx.checkAnswer('study');assert.equal(ctx.M.flashing,true);
clear();mission('tiles',{type:'tiles',n:3,next:1,order:[3,1,2]});ctx.tapTile(2);assert.equal(ctx.M.task.next,1);[1,2,3].forEach(n=>ctx.tapTile(n));assert.equal(ctx.M.flashing,true);ctx.tapTile(3);assert.equal(ctx.M.task.next,4,'extra tap during success cannot reset tiles');
clear();mission('quiz',{type:'quiz',q:'Test',a:'yes',opts:['no','yes','maybe','other']});assert.equal(ctx.chooseQuiz(-1),false);assert.equal(ctx.chooseQuiz(1),true);assert.equal(ctx.chooseQuiz(0),false,'extra tap cannot replace completed quiz');
// Old success timers must never advance a replacement task or a replacement alarm.
clear();mission('math',{type:'math',a:1});ctx.missionStep();const stale=pending();mission('math',{type:'math',a:2});stale();assert.equal(ctx.M.done,0);
ctx.missionStep();const old=pending();ctx.missionBack();old();assert.equal(ctx.M.done,0);assert.equal(ctx.M.phase,'intro');
// Actual pulses only: no counting at intro, during success, or from sustained acceleration.
clear();mission('shake',{type:'shake',need:3,got:0,armed:true,lastShake:0});const high={acceleration:{x:20,y:0,z:0}},low={acceleration:{x:0,y:0,z:0}};
ctx.M.phase='intro';ctx.onMotion(high);assert.equal(ctx.M.task.got,0);ctx.M.phase='task';ctx.onMotion(high);assert.equal(ctx.M.task.got,1);
clock+=300;ctx.onMotion(high);assert.equal(ctx.M.task.got,1);ctx.onMotion(low);ctx.onMotion(high);assert.equal(ctx.M.task.got,2);
clock+=300;ctx.onMotion(low);ctx.onMotion(high);assert.equal(ctx.M.flashing,true);clock+=300;ctx.onMotion(low);ctx.onMotion(high);assert.equal(ctx.M.task.got,3);
// Sensor fallback is scheduled for every shake task, including later random rounds.
clear();mission('shake',{type:'shake',got:0,need:3});ctx.startShakeFallbackTimer();const fallback=timers.get(ctx.M.task.timer).fn;fallback();assert.equal(ctx.M.task.fallbackReady,true);
clear();mission('shake',{type:'shake',got:0,need:3});ctx.startShakeFallbackTimer();const abandonedFallback=timers.get(ctx.M.task.timer).fn;mission('math',{type:'math',a:1});abandonedFallback();assert.equal(ctx.M.task.fallbackReady,undefined);
// Per-request speech results, retries, alternate transcripts and NKJV then NLT.
clear();mission('affirm',{type:'affirm',q:'I can learn with steady practice.'});ctx.startSpeech();const token=ctx.M.task.speechRequest;ctx.startSpeech();assert.equal(calls.filter(v=>Array.isArray(v)&&v[0]==='listen').length,1);
ctx.onSpeech('I can learn with steady practice.','old');assert.equal(ctx.M.task.listening,true);ctx.onSpeech('wrong words|I can learn with steady practice.',token);assert.equal(ctx.M.flashing,true);
clear();mission('bible',{type:'bible',q:'Trust in the Lord with all your heart',ref:'Test 1:1'});ctx.NLT['Test 1:1']='Trust in the Lord with all your heart';ctx.startSpeech();const firstToken=ctx.M.task.speechRequest;
ctx.onSpeech(ctx.M.task.q,firstToken);assert.equal(ctx.M.task.step,'nlt');assert.equal(ctx.M.flashing,undefined);assert.ok(calls.includes('bible_nlt'));
ctx.onSpeech(ctx.M.task.q,firstToken);assert.equal(ctx.M.flashing,undefined,'duplicate first result cannot complete NLT');ctx.startSpeech();const secondToken=ctx.M.task.speechRequest;ctx.speechFail(-1,firstToken);assert.equal(ctx.M.task.listening,true,'stale error cannot cancel retry');ctx.onSpeech(ctx.M.task.q,secondToken);assert.equal(ctx.M.flashing,true);
clear();mission('affirm',{type:'affirm',q:'I can learn.'});ctx.startSpeech();const abandoned=ctx.M.task.speechRequest;ctx.missionBack();ctx.onSpeech('I can learn.',abandoned);assert.equal(ctx.M.done,0);assert.equal(ctx.M.task.listening,false);
console.log('All 11 mission types: content, difficulty, answers, sequence, timers, speech requests, fallbacks and shake pulses passed.');
