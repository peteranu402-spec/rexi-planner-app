const fs=require('fs'),path=require('path'),os=require('os'),cp=require('child_process'),assert=require('assert/strict');
const bridge=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rexi/planner/AlarmBridge.java'),'utf8');
const a=bridge.indexOf('    private void destroyRecognizer()'),b=bridge.indexOf('    /** Short vibration feedback',a);assert.ok(a>=0&&b>a);
const template=fs.readFileSync(path.join(__dirname,'NativeSpeechTest.java.tmpl'),'utf8');
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'rexi-speech-'));
try{
 fs.writeFileSync(path.join(dir,'NativeSpeechTest.java'),template.replace('// PRODUCTION_SPEECH_METHODS',bridge.slice(a,b)));
 const java=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin/java'):'java';
 for(const args of [['-m','jdk.compiler/com.sun.tools.javac.Main','-d',dir,path.join(dir,'NativeSpeechTest.java')],['-cp',dir,'NativeSpeechTest']]){
   const run=cp.spawnSync(java,args,{encoding:'utf8'});process.stdout.write(run.stdout||'');process.stderr.write(run.stderr||'');assert.equal(run.status,0,run.error?.message||'native speech harness succeeds');
 }
}finally{fs.rmSync(dir,{recursive:true,force:true});}
