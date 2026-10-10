const fs=require('fs'),path=require('path'),os=require('os'),cp=require('child_process'),assert=require('assert/strict');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/java/com/rexi/planner/FocusEngine.java'),'utf8');
function between(a,b){const start=source.indexOf(a),end=source.indexOf(b,start);assert.ok(start>=0&&end>start);return source.slice(start,end);}
const methods=between('    static boolean shouldBlock(', '    static boolean isStrict(')+between('    static synchronized void onEvent(', '    /** ", Temitope"')+between('    static boolean canShowStudy(', '    private static void alert(');
const template=fs.readFileSync(path.join(__dirname,'BreakExpiryTest.java.tmpl'),'utf8');
const dir=fs.mkdtempSync(path.join(os.tmpdir(),'rexi-break-'));
try{
 const file=path.join(dir,'BreakExpiryTest.java');fs.writeFileSync(file,template.replace('// PRODUCTION_FOCUS_METHODS',methods));
 const java=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin/java'):'java';
 for(const args of [['-m','jdk.compiler/com.sun.tools.javac.Main','-d',dir,file,path.join(__dirname,'../app/src/main/java/com/rexi/planner/FocusReturnPolicy.java'),path.join(__dirname,'../app/src/main/java/com/rexi/planner/FocusVoicePolicy.java')],['-cp',dir,'com.rexi.planner.BreakExpiryTest']]){
  const run=cp.spawnSync(java,args,{encoding:'utf8'});process.stdout.write(run.stdout||'');process.stderr.write(run.stderr||'');assert.equal(run.status,0,run.error?.message||'break integration harness succeeds');
 }
}finally{fs.rmSync(dir,{recursive:true,force:true});}
