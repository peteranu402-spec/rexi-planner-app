const fs=require('fs'),vm=require('vm'),assert=require('assert/strict'),path=require('path');
const html=fs.readFileSync(path.join(__dirname,'../web/index.html'),'utf8');
const code=html.slice(html.indexOf('const VOICE_ALERTS='),html.indexOf('function line(arr)'));
const manifest=JSON.parse(fs.readFileSync(path.join(__dirname,'../web/voices/manifest.json'),'utf8'));
const calls=[];
const ctx={IS_APP:true,S:{focus:{voice:true}},window:{AndroidAlarm:{speakAlert:(event,text)=>calls.push(event),stopVoice:()=>calls.push('stop'),listen:()=>calls.push('listen')}},speak:()=>calls.push('fallback')};
vm.createContext(ctx);vm.runInContext(code,ctx);
for(const clip of manifest.clips){
  ctx.playVoiceAlert(clip.event);
  assert.equal(calls.at(-1),clip.event,'explicit native identifier');
  assert.ok(fs.statSync(path.join(__dirname,'../web/voices',clip.file)).size>1000);
}
assert.equal(manifest.clips.length,7);
ctx.S.focus.voice=false;const count=calls.length;ctx.playVoiceAlert('study_start');ctx.playVoiceAlert('session_resumed',true);
assert.equal(calls.length,count,'muted study alerts and excluded resume stay silent');
ctx.playVoiceAlert('bible_nlt',true);assert.equal(calls.at(-1),'bible_nlt','Bible instructions remain independent of study voice setting');
// Exercise the real NKJV acceptance handler, rather than matching its source text.
Object.assign(ctx,{SPOKEN:t=>t==='bible',M:{phase:'task',task:{type:'bible',q:'NKJV verse',ref:'John 3:16'},micFails:1},NLT:{'John 3:16':'NLT verse'},similarity:(a,b)=>a===b?1:0.1,buzz(){},drawAlarm(){},missionStep:()=>calls.push('mission_done'),current:null});
vm.runInContext(html.slice(html.indexOf('const normAns='),html.indexOf('function shuffle(')),ctx);
vm.runInContext(html.slice(html.indexOf('function speechReady('),html.indexOf('function startSpeech(')),ctx);
vm.runInContext(html.slice(html.indexOf('function onSpeech('),html.indexOf('function speechFail(')),ctx);
ctx.M.task.listening=true;ctx.onSpeech('NKJV verse');assert.equal(ctx.M.task.step,'nlt');assert.equal(calls.at(-1),'bible_nlt');
ctx.M.task.listening=true;ctx.onSpeech('NLT verse');assert.equal(calls.at(-1),'mission_done');
vm.runInContext('let speechSequence=0;',ctx);
vm.runInContext(html.slice(html.indexOf('function startSpeech('),html.indexOf('function onSpeech(')),ctx);
ctx.SPOKEN=t=>t==='bible';ctx.startSpeech();assert.deepEqual(calls.slice(-2),['stop','listen'],'clip stops before the microphone starts');
// Browser playback uses the same packaged recordings; superseding and stopping release the old audio.
const players=[];
ctx.Audio=class {constructor(src){this.src=src;players.push(this)}play(){return Promise.resolve()}pause(){this.paused=true}removeAttribute(){this.src=''}load(){}};
ctx.IS_APP=false;ctx.S.focus.voice=true;
ctx.playVoiceAlert('study_start');assert.equal(players[0].src,'voices/study_start.mp3');
ctx.playVoiceAlert('break_start');assert.equal(players[0].paused,true);assert.equal(players[1].src,'voices/break_start.mp3');
ctx.stopVoice();assert.equal(players[1].paused,true);
console.log('Seven recordings, mute/force behaviour, Bible transition and microphone routing passed.');
