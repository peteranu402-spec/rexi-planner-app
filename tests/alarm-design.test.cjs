const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert/strict'),crypto=require('crypto');
const root=path.join(__dirname,'..'),html=fs.readFileSync(path.join(root,'web/index.html'),'utf8');
const pane={innerHTML:'',querySelector(){return null;}};
const calls=[];
const ctx={S:{wakeAlarms:[],questions:[]},SHEET:null,IS_APP:false,window:{},navigator:{},
  esc:v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])),
  pad2:n=>String(n).padStart(2,'0'),DAYS:['Mon','Tue','Wed','Thu','Fri','Sat','Sun'],DAY_FULL:{Sun:'Sunday',Mon:'Monday',Tue:'Tuesday',Wed:'Wednesday',Thu:'Thursday',Fri:'Friday',Sat:'Saturday'},
  document:{body:{classList:{add(){},remove(){}}},getElementById(){return null;}},$:()=>pane,
  initWheel(){},restrictExitTyping(){},ringInText:()=>'',nextAlarm:()=>null,
  fmt12:time=>{const [h,m]=time.split(':').map(Number);return (h%12||12)+':'+String(m).padStart(2,'0')+' '+(h<12?'AM':'PM');},
  previewSound:sd=>calls.push(sd.id),stopPreview:()=>calls.push('stop'),toast(){},deleteIcon:()=>'<button>Delete</button>',SPOKEN:()=>false};
vm.createContext(ctx);
function load(start,end){const a=html.indexOf(start),b=html.indexOf(end,a);assert.ok(a>=0&&b>a,start);vm.runInContext(html.slice(a,b),ctx);}
load('const ICON=','/* ---------- state');
load('const BUILTIN_SOUNDS=','let webAudio=');
load('const MTINT=','const LEVEL_KEYS=');
load('function daysSummary(','/* ---------- bottom sheets');
load('function soundName(','function nextOnceAt(');
vm.runInContext(html.match(/function missionLabel\(cfg\)\{[^\n]+/)[0],ctx);
vm.runInContext(html.match(/function openSheet\(sh\)\{[^\n]+/)[0],ctx);
vm.runInContext(html.match(/function closeSheet\(\)\{[^\n]+/)[0],ctx);
load('function renderSheet(first)','/* The early-exit sentence');
load('const WH=','function ringInText(');
const a=html.indexOf("case 'draftDay':"),b=html.indexOf("case 'addQ':",a);
vm.runInContext('function action(a,data={}){const el={dataset:data};switch(a){'+html.slice(a,b)+'}}\nthis.sounds=BUILTIN_SOUNDS;this.cats=SOUND_CATS;this.defaultSound=DEFAULT_SOUND;this.missions=MISSIONS;',ctx);
assert.equal(ctx.sounds.length,20);assert.equal(new Set(ctx.sounds.map(s=>s.id)).size,20);
assert.equal(ctx.cats.length,9);assert.ok(ctx.sounds.every(s=>ctx.cats.includes(s.cat)));
assert.ok(ctx.sounds.find(s=>s.id===ctx.defaultSound).seconds>=60,'new alarms default to a long musical recording');
const manifest=JSON.parse(fs.readFileSync(path.join(root,'web/sounds/manifest.json')));
assert.equal(manifest.new_tracks.length,8);
for(const sound of ctx.sounds){
 const web=fs.readFileSync(path.join(root,'web/sounds',sound.id+'.mp3'));
 const native=fs.readFileSync(path.join(root,'app/src/main/res/raw','alarm_'+sound.id+'.mp3'));
 assert.ok(web.equals(native),sound.id+' is identical in the browser and native alarm resources');
}
for(const sound of manifest.new_tracks){
 assert.ok(sound.duration_seconds>=60&&sound.duration_seconds<61,sound.id+' has a full minute of audio');
 assert.equal(crypto.createHash('sha256').update(fs.readFileSync(path.join(root,'web/sounds',sound.id+'.mp3'))).digest('hex'),sound.sha256);
 assert.equal(sound.license,'CC BY 4.0');
}
const sw={};vm.createContext(sw);const worker=fs.readFileSync(path.join(root,'web/sw.js'),'utf8');
vm.runInContext(worker.slice(0,worker.indexOf('self.addEventListener'))+'\nthis.assets=ASSETS;',sw);
for(const sound of ctx.sounds)assert.ok(sw.assets.includes('./sounds/'+sound.id+'.mp3'),'offline cache includes '+sound.id);
for(const asset of sw.assets)assert.ok(fs.existsSync(path.join(root,'web',asset==='./'?'index.html':asset)),'cache file exists: '+asset);
// Production navigation must save selections into the alarm draft through both sound sheets.
const draft={name:'Morning Alarm',time:'02:00',type:'mission',repeat:'scheduled',days:['Mon','Tue','Wed','Thu','Fri','Sat'],mission:{type:'bible',count:3},sound:{kind:'builtin',id:ctx.defaultSound},snooze:{on:false,min:5},on:true};
const editor={kind:'alarm',draft};ctx.SHEET=editor;ctx.renderSheet();
assert.ok(pane.innerHTML.includes('alarm-editor'));assert.ok(pane.innerHTML.includes('data-action="draftRepeat"'));
for(const text of ['Scheduled','One-time','Mission','Normal'])assert.ok(pane.innerHTML.includes(text));
ctx.action('openSound');assert.equal(ctx.SHEET.kind,'sound');
assert.equal((pane.innerHTML.match(/data-action="openSoundCat"/g)||[]).length,9);
ctx.action('openSoundCat',{cat:'Summer'});assert.equal(ctx.SHEET.kind,'soundTracks');
ctx.action('pickBuiltin',{id:'carefree'});assert.equal(draft.sound.id,'carefree');assert.ok(calls.includes('carefree'));
ctx.action('closeSound');assert.equal(ctx.SHEET,editor);assert.equal(draft.mission.type,'bible');assert.equal(draft.name,'Morning Alarm');
ctx.action('draftKind',{k:'normal'});assert.equal(draft.type,'normal');assert.ok(!pane.innerHTML.includes('data-action="openMission"'));assert.equal(draft.sound.id,'carefree');
ctx.action('draftKind',{k:'mission'});assert.equal(draft.mission.type,'bible');
ctx.action('openSound');const before=ctx.SHEET;ctx.action('openSoundCat',{cat:'Unknown'});assert.equal(ctx.SHEET,before);
// No visible mission name/count badges on alarm cards; accessible mission names stay available.
ctx.S.wakeAlarms=[draft];const card=ctx.homeAlarms();
assert.ok(card.includes('data-mission-icon="bible"'));assert.ok(card.includes('aria-label="Bible verse"'));
assert.ok(!card.includes('class="chip"'));assert.ok(!card.replace(/<[^>]+>/g,'').includes('Bible verse'));
assert.ok(card.indexOf('Weekdays + Sat')<card.indexOf('class="ar-time"'));assert.ok(card.includes('Morning Alarm'));
// Independently verify the 12-hour wheels convert every minute to the intended 24-hour time.
for(let hour=0;hour<24;hour++)for(let minute=0;minute<60;minute++){
 const values={h:(hour%12||12)-1,m:minute,ap:hour>=12?1:0},lengths={h:12,m:60,ap:2};
 ctx.document.getElementById=()=>({querySelector:sel=>{const k=sel.match(/data-w="([^"]+)"/)[1];return {scrollTop:values[k]*48,querySelectorAll:()=>Array(lengths[k])};}});
 assert.equal(ctx.wheelTime(),String(hour).padStart(2,'0')+':'+String(minute).padStart(2,'0'));
}
console.log('Alarm editor, nested sound selection, mission icon cards, 20 matching audio assets, 8 music excerpts, offline cache and all 1,440 wheel times passed.');
