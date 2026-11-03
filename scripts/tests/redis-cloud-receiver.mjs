// Local acceptance only. Raw remote-write bytes are forwarded unchanged to
// Prometheus's real decoder. Receipts contain counts/hash/status, never payloads.
import https from 'node:https';
import http from 'node:http';
import fs from 'node:fs';
import {createHash,timingSafeEqual} from 'node:crypto';
import {fileURLToPath} from 'node:url';
export async function startReceiver({key,cert,token,host='::',port=9443,upstream='http://prometheus:9090/api/v1/write',mode=()=> 'accept',receipt=()=>{}}){
  const expected=Buffer.from('Basic '+Buffer.from('12345:'+token).toString('base64'));
  const server=https.createServer({key,cert,minVersion:'TLSv1.2'},(req,res)=>{
    const finish=(status,bytes=0,sha256)=>{receipt({at:Date.now(),status,bytes,...(sha256?{sha256}:{})});res.writeHead(status,status===307?{location:'https://receiver:9443/forbidden-redirect'}:{}).end();};
    if(req.url!=='/api/v1/write'||req.method!=='POST'){req.resume();return finish(404);}
    const auth=Buffer.from(req.headers.authorization??'');
    if(auth.length!==expected.length||!timingSafeEqual(auth,expected)||mode()==='unauthorized'){req.resume();return finish(401);}
    if(mode()==='redirect'){req.resume();return finish(307);}
    if(mode()==='unavailable'){req.resume();return finish(503);}
    let size=0;const chunks=[];
    req.on('data',c=>{size+=c.length;if(size>1048576){req.destroy();return;}chunks.push(c);});
    req.on('end',()=>{
      const body=Buffer.concat(chunks),hash=createHash('sha256').update(body).digest('hex');
      const out=http.request(upstream,{method:'POST',timeout:5000,headers:{'content-type':'application/x-protobuf','content-encoding':'snappy','x-prometheus-remote-write-version':'0.1.0','content-length':body.length}},response=>{response.resume();response.on('end',()=>finish(response.statusCode,body.length,hash));});
      out.on('timeout',()=>out.destroy());out.on('error',()=>finish(502,body.length,hash));out.end(body);
    });
  });
  server.requestTimeout=10000;server.headersTimeout=10000;server.on('tlsClientError',()=>{});
  await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(port,host,resolve);});return server;
}
if(process.argv[1]===fileURLToPath(import.meta.url)){
  const root='/fixture';
  await startReceiver({key:fs.readFileSync(root+'/key.pem'),cert:fs.readFileSync(root+'/ca.pem'),token:fs.readFileSync(root+'/token','utf8'),mode:()=>fs.readFileSync(root+'/receiver-mode','utf8').trim(),receipt:r=>fs.appendFileSync(root+'/receipts.jsonl',JSON.stringify(r)+'\n')});
  // An explicitly fixture-only adversarial HTTP relay. Production Alloy bytes
  // and real collector stay unchanged. Prove sanitation at the actual receiver.
  http.createServer((req,res)=>{
    const scrapeAt=Date.now();
    const upstream=http.get('http://collector:19121/metrics',{timeout:5000},response=>{
      let body='';response.setEncoding('utf8');response.on('data',c=>{body+=c;if(body.length>131072)upstream.destroy();});
      response.on('end',()=>{
        fs.appendFileSync(root+'/scrapes.jsonl',JSON.stringify({at:scrapeAt,status:response.statusCode})+'\n');
        body=body.split('\n').map(line=>{if(!line||line.startsWith('#'))return line;const i=line.indexOf(' ');const name=line.slice(0,i),value=line.slice(i);return (name.includes('{')?name.replace('{','{private_host="should-drop",'):name+'{private_host="should-drop",reason="connect"}')+value;}).join('\n');
        res.writeHead(response.statusCode,{'content-type':'text/plain'}).end(body+'\nfasttowin_redis_monitor_unapproved{private_host="should-drop"} 99\nfasttowin_redis_monitor_collection_errors_total{reason="not-approved",private_host="should-drop"} 99\n');
      });
    });upstream.on('timeout',()=>upstream.destroy());upstream.on('error',()=>res.writeHead(503).end());
  }).listen(19121,'::');
}
