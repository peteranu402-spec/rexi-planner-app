const fs=require('fs'),vm=require('vm'),assert=require('assert/strict');
const html=fs.readFileSync(__dirname+'/../web/index.html','utf8');
const script=html.match(/<script>([\s\S]*?)<\/script>/)[1];
new vm.Script(script); // Check the complete app script, including event handlers.
let now=1000000,stops=0;
const alerts=[];
const ctx={Date:{now:()=>now},S:{focus:{rhythm:'90/15',maxBreaks:1,lock:'pin',voice:false},sessions:[],active:null},IS_APP:true,RHYTHMS:{'90/15':[90,15],'off':[0,0]},document:{addEventListener(){}},window:{AndroidAlarm:{focusUpdate(){return true},blockerOn(){return true},focusStop(){stops++},lockTask(){}}},setTimeout,clearTimeout,Math,JSON,toast(){},clock:ms=>Math.ceil(ms/1000)+'s',save(){},closeSheet(){},speak(){},playVoiceAlert:event=>alerts.push(event),line:x=>x[0],LINES:{start:()=>['start'],brk:()=>['break'],back:()=>['back'],end:()=>['end']},goalLabel:x=>x,uid:()=> 'session',todayISO:()=> '2026-10-07',nm:()=>'',callName:()=>'',buzz(){},render(){},hm:x=>String(x),openSheet:x=>ctx.SHEET=x,ask:()=>Promise.resolve(false),SHEET:null};
ctx.QUOTES=['quote'];ctx.QUOTE_NOTES=['note'];ctx.dayOfYear=()=>0;
vm.createContext(ctx);
const study=html.match(/function activeStudyMs\(now\)\{[^\n]+/)[0];
const focus=html.slice(html.indexOf('let FOCUS_OPEN=false'),html.indexOf('function lockPhoneOff('));
vm.runInContext(study+'\n'+focus+'\nrenderFocus=()=>{};closeFocus=()=>{};lockPhoneOff=()=>{};',ctx);
let ready=false,accepted=true;
ctx.window.AndroidAlarm.blockerOn=()=>ready;
ctx.window.AndroidAlarm.focusUpdate=()=>accepted;
vm.runInContext(html.match(/function blockerOn\(\)\{[^\n]+/)[0],ctx);
for(const lock of ['pin','block','off']){
  ctx.S.focus.lock=lock;
  for(const duration of [120,180]){
    assert.equal(ctx.startFocus('Math',duration,'Morning'),false);
    assert.equal(ctx.S.active,null,'unavailable accessibility must not start a timer');
  }
}
ready=true;accepted=false;
assert.equal(ctx.startFocus('Math',180,'Morning'),false);
assert.equal(ctx.S.active,null,'native rejection rolls back the pending session');
accepted=true;ctx.S.focus.lock='pin';
ctx.startFocus('Math',180,'Morning');
assert.deepEqual(alerts,['study_start'],'only an accepted session announces its start');
assert.equal(ctx.breakBudgetMin(180,[90,15]),25,'3 h on 90/15 gives 25 min of break time');
assert.equal(ctx.breakBudgetMin(180,[50,10]),30,'3 h on 50/10 gives 30 min of break time');
assert.equal(ctx.breakBudgetMin(180,[0,0]),0,'Off gives no break time');
assert.equal(ctx.S.active.breakBudgetMs,25*60000,'break pool fixed at session start');
now+=90*60000;ctx.takeBreak(15);
assert.equal(ctx.S.active.state,'break');
assert.equal(alerts.at(-1),'break_start');
now+=15*60000;
assert.equal(ctx.activeSessionMs(),105*60000,'break is inside the 3-hour session');
assert.equal(ctx.activeStudyMs(),90*60000,'break is not credited as study');
const beforeBreakReturn=alerts.length;ctx.endBreak(true);
assert.equal(alerts.length,beforeBreakReturn,'native Android owns automatic break-end speech');
assert.equal(ctx.S.active.state,'study');
assert.equal(ctx.breakLeftMs(),10*60000,'15 min taken from the 25 min pool');
assert.deepEqual([...ctx.breakChoices()],[5,10],'only breaks that fit the remaining pool are offered');
ctx.takeBreak(15);assert.equal(ctx.S.active.state,'study','direct break request cannot go over the pool');
ctx.takeBreak(10);now+=3*60000;ctx.endBreak(false);
assert.equal(ctx.breakLeftMs(),7*60000,'coming back early only uses the minutes taken');
assert.deepEqual([...ctx.breakChoices()],[5]);
ctx.S.focus.rhythm='90/15';ctx.S.active.goalMin=180;
ctx.endFocus();assert.equal(ctx.SHEET.kind,'endEarly','strict early exit requires confirmation');assert.ok(ctx.S.active);
now+=72*60000;assert.equal(ctx.activeSessionMs(),180*60000,'session completes after exactly 3 hours');
ctx.S.active.goalHit=true;ctx.endFocus(true);
assert.equal(ctx.S.active,null);assert.equal(ctx.S.sessions[0].studyMin,162);assert.equal(ctx.S.sessions[0].sessionMin,180);
assert.equal(alerts.at(-1),'study_complete');
ctx.S.focus.rhythm='off';ctx.startFocus('Math',60,'Morning');assert.equal(ctx.breakChoices().length,0,'off means no breaks');
ctx.emergencyPause();ready=false;ctx.resumeFocus();assert.equal(ctx.S.active.state,'paused','unavailable service cannot resume');ready=true;accepted=false;ctx.resumeFocus();assert.equal(ctx.S.active.state,'paused','native rejection preserves paused session');accepted=true;now+=10*60000;assert.equal(ctx.activeSessionMs(),0,'emergency pause freezes session');const beforeResume=alerts.length;ctx.resumeFocus();assert.equal(alerts.length,beforeResume,'session-resumed voice is excluded');now+=5*60000;assert.equal(ctx.activeSessionMs(),5*60000);
console.log('Focus policy tests passed; complete JavaScript syntax valid.');
const handler=html.slice(html.indexOf("case 'exitEarly':"),html.indexOf("case 'pickAllowed':"));
ctx.normAns=x=>x.trim().toLowerCase();ctx.EXIT_PHRASE='I am choosing to stop studying early today and I will make up for it';ctx.renderSheet=()=>{};
let inputs={};ctx.$=key=>({value:inputs[key]||''});
vm.runInContext('handleExit=action=>{switch(action){'+handler+'}}',ctx);
ctx.SHEET={};inputs['#exitIn']='wrong';ctx.handleExit('exitEarly');assert.ok(ctx.SHEET.err);assert.ok(ctx.S.active);
inputs['#exitIn']=ctx.EXIT_PHRASE;ctx.handleExit('exitEarly');assert.equal(ctx.SHEET.kind,'exitReason');assert.ok(ctx.S.active,'typing alone cannot end session');
const sid=ctx.S.active.id;
inputs['#exitReason']='health';inputs['#exitDetail']='sick';ctx.SHEET.wait='no';ctx.handleExit('exitReason');assert.ok(ctx.SHEET.err,'too short asks again');assert.ok(ctx.S.active);
inputs['#exitReason']='distraction';inputs['#exitDetail']='my friends are waiting for me on the group chat now';ctx.SHEET.wait='no';ctx.handleExit('exitReason');
assert.ok(ctx.S.active,'distraction reason keeps session active');assert.ok(ctx.exitCooldownMs()>0,'rejected reason locks End session');
ctx.SHEET=null;ctx.endFocus();assert.equal(ctx.SHEET,null,'End session is locked during the cooldown');
now+=10*60000+1000;
ctx.SHEET={kind:'exitReason',sessionId:sid,wait:'yes'};inputs['#exitReason']='health';inputs['#exitDetail']='I have a headache and I want to rest a little';ctx.handleExit('exitReason');assert.ok(ctx.S.active,'a reason that can wait is rejected');
now+=10*60000+1000;
ctx.SHEET={kind:'exitReason',sessionId:sid,wait:'no'};inputs['#exitReason']='urgent';inputs['#exitDetail']='I just want to watch a football game with my friends tonight';ctx.handleExit('exitReason');assert.ok(ctx.S.active,'distraction words are rejected');
now+=10*60000+1000;
ctx.SHEET={kind:'exitReason',sessionId:sid,wait:'no'};inputs['#exitReason']='health';inputs['#exitDetail']='I have a high fever and need to see a doctor now';ctx.handleExit('exitReason');assert.equal(ctx.S.active,null);assert.equal(ctx.S.sessions.at(-1).exitReason.reason,'health');
console.log('Typed confirmation and exit-reason checks passed.');
// Run the real early-exit input guard with simulated clipboard and keyboard events.
class InputNode{
 constructor(){this.value='';this.listeners={};this.children=[];}
 addEventListener(name,fn){(this.listeners[name]||=[]).push(fn);}
 setAttribute(){} appendChild(node){this.children.push(node);}
 fire(name,fields={}){const e={defaultPrevented:false,preventDefault(){this.defaultPrevented=true},...fields};for(const fn of this.listeners[name]||[])fn(e);return e;}
}
const phrase=new InputNode(),exitInput=new InputNode(),body=new InputNode();
const root={querySelector:q=>q==='.exit-phrase'?phrase:q==='#exitIn'?exitInput:q==='.sheet-body'?body:q==='.exit-type-note'?body.children[0]||null:null};
ctx.document.createElement=()=>new InputNode();
vm.runInContext(html.slice(html.indexOf('function restrictExitTyping('),html.indexOf('/* Wayk-style scroll-wheel time picker')),ctx);
ctx.restrictExitTyping(root);
for(const ev of ['selectstart','copy','cut','contextmenu','dragstart'])assert.equal(phrase.fire(ev).defaultPrevented,true);
for(const ev of ['paste','drop'])assert.equal(exitInput.fire(ev).defaultPrevented,true);
for(const inputType of ['insertFromPaste','insertFromDrop','insertReplacementText','insertText'])assert.equal(exitInput.fire('beforeinput',{inputType,data:ctx.EXIT_PHRASE}).defaultPrevented,true);
assert.equal(exitInput.fire('beforeinput',{inputType:'insertText',data:'I'}).defaultPrevented,false);
exitInput.value='I';exitInput.fire('input');exitInput.value=ctx.EXIT_PHRASE;exitInput.fire('input');assert.equal(exitInput.value,'I','keyboard clipboard fallback restores previously typed input');
exitInput.value='';exitInput.fire('input');assert.equal(exitInput.value,'','normal deletion stays usable');
assert.ok(body.children[0].textContent.includes('Type the sentence yourself'));
console.log('Early-exit phrase selection/copy, paste/drop and keyboard clipboard restrictions passed.');
