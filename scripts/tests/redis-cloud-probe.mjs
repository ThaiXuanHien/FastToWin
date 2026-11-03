// Execute only in the owned fixture container. No generic Redis client.
import fs from 'node:fs';
import http from 'node:http';
import https from 'node:https';
import dns from 'node:dns/promises';
const mode=process.argv[2],arg=process.argv[3];
const timeout=AbortSignal.timeout(5000);
if(mode==='query'){
  const result=await (await fetch('http://prometheus:9090/api/v1/query?query='+encodeURIComponent(arg),{signal:timeout})).json();
  if(result.status!=='success')throw new Error('query_failed');console.log(JSON.stringify(result.data));
}else if(mode==='network'){
  const addresses=await dns.lookup('collector',{all:true}),result=[];
  for(const address of addresses){const host=address.family===6?'['+address.address+']':address.address;const response=await fetch('http://'+host+':19121/ready',{signal:AbortSignal.timeout(5000)});result.push({family:address.family,status:response.status});}
  console.log(JSON.stringify(result));
}else if(mode==='wal'){
  let bytes=0,files=0;function walk(p){for(const item of fs.readdirSync(p,{withFileTypes:true})){const file=p+'/'+item.name;if(item.isDirectory())walk(file);else{files++;bytes+=fs.statSync(file).size;}}}walk('/var/lib/alloy');console.log(JSON.stringify({files,bytes}));
}else if(mode==='tls'){
  const token=fs.readFileSync('/fixture/token','utf8'),results={};
  for(const kind of ['wrong-token','hostname','untrusted']){
    results[kind]=await new Promise(resolve=>{const request=https.request({hostname:'receiver',port:9443,path:'/api/v1/write',method:'POST',timeout:4000,...(kind==='untrusted'?{}:{ca:fs.readFileSync('/fixture/ca.pem')}),...(kind==='hostname'?{servername:'wrong.invalid'}:{}),headers:{authorization:'Basic '+Buffer.from('12345:'+(kind==='wrong-token'?'deliberately-wrong':token)).toString('base64')}},response=>{response.resume();response.on('end',()=>resolve(response.statusCode));});request.on('timeout',()=>request.destroy());request.on('error',e=>resolve(e.code));request.end();});
  }console.log(JSON.stringify(results));
}else throw new Error('unknown_fixture_probe');
