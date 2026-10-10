const fs=require('fs'),vm=require('vm'),assert=require('assert/strict'),crypto=require('crypto'),path=require('path');
const html=fs.readFileSync(path.join(__dirname,'../web/index.html'),'utf8');
const provenance=JSON.parse(fs.readFileSync(path.join(__dirname,'../web/nkjv-sources.json'),'utf8'));
const ctx={MISSIONS:{bible:{}},S:{myVerses:[]},M:{phase:'task',cfg:{type:'bible',level:'medium'},need:1,done:0,msg:'',micFails:0},NLT:{},IS_APP:true,current:null,
  fetchNLT(){},pick:items=>items[0],esc:s=>String(s),svg:()=>'',SPOKEN:t=>t==='bible',canSpeak:()=>true,drawAlarm(){},buzz(){},
  playVoiceAlert:event=>ctx.alerts.push(event),alerts:[],missionStep:()=>ctx.steps++ ,steps:0,
  similarity:(a,b)=>a===b?1:0.1};
vm.createContext(ctx);
vm.runInContext(html.slice(html.indexOf('const VERSE_PACKS='),html.indexOf('const AFFIRM_PACKS='))+'\nthis.packs=VERSE_PACKS;',ctx);
const verses=ctx.packs.flatMap(p=>p.v),sources=new Map(provenance.sources.map(v=>[v.reference,v]));
assert.equal(verses.length,60);assert.equal(ctx.packs.length,10);assert.equal(sources.size,60);assert.equal(provenance.translation,'NKJV');
assert.equal(new Set(verses.map(v=>v[1])).size,60);assert.ok(ctx.packs.every(p=>p.v.length===6));
assert.equal(verses.find(v=>v[1]==='Proverbs 6:6')[0],'Go to the ant, you sluggard! Consider her ways and be wise,');
for(const [text,ref] of verses){
  assert.equal(crypto.createHash('sha256').update(text).digest('hex'),sources.get(ref).sha256,ref+' matches its verified source text');
  assert.ok(!text.includes('strengtheneth')&&!text.includes('whosoever')&&!text.includes('thine'),ref+' uses the new translation');
}
assert.equal(verses.find(v=>v[1]==='Philippians 4:13')[0],'I can do all things through Christ who strengthens me.');
vm.runInContext(html.match(/function allVerseIds\(\)\{[^\n]+/)[0],ctx);
vm.runInContext(html.slice(html.indexOf('function versePool('),html.indexOf('function affirmPool(')),ctx);
vm.runInContext(html.slice(html.indexOf('const normAns='),html.indexOf('function shuffle(')),ctx);
vm.runInContext("var LEVEL_KEYS=['easy','medium','hard'];",ctx);
vm.runInContext(html.slice(html.indexOf('function resolveType('),html.indexOf('function canSpeak(')),ctx);
for(const pack of ctx.packs){ctx.M.cfg.verses=[pack.id];const task=ctx.newTask();assert.equal(task.type,'bible');assert.ok(pack.v.some(v=>v[0]===task.q&&v[1]===task.ref));}
// User-authored text retains its own wording and is never labelled as built-in NKJV.
ctx.S.myVerses=[{text:'A personally supplied verse',ref:'My reference'}];ctx.M.cfg.verses=['mine'];const own=ctx.newTask();assert.equal(own.q,'A personally supplied verse');assert.equal(own.mine,true);
vm.runInContext(html.slice(html.indexOf('function wkDots('),html.indexOf('function drawAlarm(')),ctx);
let screen=ctx.wkScreen({type:'bible',ref:'Philippians 4:13',q:verses.find(v=>v[1]==='Philippians 4:13')[0]});assert.ok(screen.includes(' · NKJV'));assert.ok(!screen.includes(' · KJV'));
screen=ctx.wkScreen({...own});assert.ok(!screen.includes(' · NKJV'),'custom verse is not relabelled');
vm.runInContext(html.slice(html.indexOf('function missionReady('),html.indexOf('function shuffle(')),ctx);
vm.runInContext(html.slice(html.indexOf('function speechReady('),html.indexOf('function startSpeech(')),ctx);
vm.runInContext(html.slice(html.indexOf('function onSpeech('),html.indexOf('function speechFail(')),ctx);
ctx.M.task={type:'bible',ref:'Test 1:1',q:'Same wording in both translations'};ctx.NLT['Test 1:1']=ctx.M.task.q;
ctx.M.task.listening=true;ctx.onSpeech(ctx.M.task.q);assert.equal(ctx.M.task.step,'nlt');assert.equal(ctx.steps,0);assert.equal(ctx.alerts.at(-1),'bible_nlt','second translation still offered when wording is identical');
screen=ctx.wkScreen(ctx.M.task);assert.ok(screen.includes('NKJV')&&screen.includes('NLT'));
ctx.M.task.listening=true;ctx.onSpeech(ctx.M.task.q);assert.equal(ctx.steps,1,'NLT completes the mission step');
console.log('60 verified NKJV passages, category selection, custom verses, labels and NKJV-to-NLT sequence passed.');
