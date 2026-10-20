const {spawn}=require("node:child_process");
const {createServer}=require("node:net");
const path=require("node:path");
const {test}=require("node:test");
const assert=require("node:assert/strict");
const WebSocket=require("ws");

async function freePort(){
  const server=createServer();
  await new Promise((resolve,reject)=>server.listen(0,"127.0.0.1",resolve).once("error",reject));
  const port=server.address().port;
  await new Promise(resolve=>server.close(resolve));
  return port;
}
async function startRelay(t){
  const port=await freePort();
  const child=spawn(process.execPath,[path.join(__dirname,"index.js")],{env:{...process.env,PORT:String(port)},stdio:"ignore"});
  t.after(()=>{if(!child.killed)child.kill("SIGTERM");});
  for(let i=0;i<60;i++){
    try{const response=await fetch(`http://127.0.0.1:${port}/healthz`);if(response.ok)return port;}catch{}
    await new Promise(resolve=>setTimeout(resolve,100));
  }
  throw new Error("Relay did not become healthy");
}
function connect(port,role,room){
  return new Promise((resolve,reject)=>{
    const ws=new WebSocket(`ws://127.0.0.1:${port}/ws?role=${role}&room=${room}`);
    ws.once("open",()=>resolve(ws));ws.once("error",reject);
  });
}
function nextMessage(ws,predicate,timeout=2500){
  return new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>{cleanup();reject(new Error("Timed out waiting for WebSocket message"));},timeout);
    const onMessage=raw=>{let message;try{message=JSON.parse(raw.toString());}catch{return;}if(predicate(message)){cleanup();resolve(message);}};
    const onClose=()=>{cleanup();reject(new Error("Socket closed before expected message"));};
    const cleanup=()=>{clearTimeout(timer);ws.off("message",onMessage);ws.off("close",onClose);};
    ws.on("message",onMessage);ws.once("close",onClose);
  });
}
function roomCode(){return require("node:crypto").randomBytes(24).toString("hex");}

test("relay forwards validated navigation commands between controller and device",async t=>{
  const port=await startRelay(t);const room=roomCode();
  const controller=await connect(port,"controller",room);t.after(()=>controller.terminate());
  const device=await connect(port,"device",room);t.after(()=>device.terminate());
  controller.send(JSON.stringify({type:"remote.gesture",payload:{x1:0.2,y1:0.8,x2:0.8,y2:0.2,durationMs:350}}));
  const forwarded=await nextMessage(device,m=>m.type==="remote.gesture");
  assert.equal(forwarded.payload.x1,0.2);
  assert.equal(forwarded.payload.durationMs,350);
});

test("explicit controller disconnect notifies the device before closing the room",async t=>{
  const port=await startRelay(t);const room=roomCode();
  const controller=await connect(port,"controller",room);t.after(()=>controller.terminate());
  const device=await connect(port,"device",room);t.after(()=>device.terminate());
  const disconnected=nextMessage(device,m=>m.type==="controller.disconnect");
  controller.send(JSON.stringify({type:"controller.disconnect",payload:{}}));
  const message=await disconnected;
  assert.equal(message.payload.reason,"owner_disconnected");
});


test("relay forwards validated remote tap coordinates to the Android device",async t=>{
  const port=await startRelay(t);const room=roomCode();
  const controller=await connect(port,"controller",room);t.after(()=>controller.terminate());
  const device=await connect(port,"device",room);t.after(()=>device.terminate());
  controller.send(JSON.stringify({type:"remote.tap",payload:{x:0.42,y:0.63}}));
  const forwarded=await nextMessage(device,m=>m.type==="remote.tap");
  assert.equal(forwarded.payload.x,0.42);
  assert.equal(forwarded.payload.y,0.63);
});

test("relay rejects remote taps with coordinates outside the normalized screen",async t=>{
  const port=await startRelay(t);const room=roomCode();
  const controller=await connect(port,"controller",room);t.after(()=>controller.terminate());
  const device=await connect(port,"device",room);t.after(()=>device.terminate());
  controller.send(JSON.stringify({type:"remote.tap",payload:{x:1.5,y:0.2}}));
  const error=await nextMessage(controller,m=>m.type==="relay.error");
  assert.equal(error.payload.message,"invalid_tap_coordinates");
});

test("controller reconnects to the same pairing room after a transient disconnect",async t=>{
  const port=await startRelay(t);const room=roomCode();
  const device=await connect(port,"device",room);t.after(()=>device.terminate());
  const firstController=await connect(port,"controller",room);
  await nextMessage(firstController,m=>m.type==="device.connected");
  const controllerGone=nextMessage(device,m=>m.type==="controller.disconnected");
  firstController.close();
  await controllerGone;
  const secondController=await connect(port,"controller",room);t.after(()=>secondController.terminate());
  const restored=await nextMessage(secondController,m=>m.type==="device.connected");
  assert.equal(restored.type,"device.connected");
  const paired=await nextMessage(device,m=>m.type==="controller.connected");
  assert.equal(paired.type,"controller.connected");
});

test("device can rejoin its saved room after a transient network disconnect",async t=>{
  const port=await startRelay(t);const room=roomCode();
  const controller=await connect(port,"controller",room);t.after(()=>controller.terminate());
  const firstDevice=await connect(port,"device",room);
  await nextMessage(controller,m=>m.type==="device.connected");
  const deviceGone=nextMessage(controller,m=>m.type==="device.disconnected");
  firstDevice.close();
  await deviceGone;
  const secondDevice=await connect(port,"device",room);t.after(()=>secondDevice.terminate());
  const restored=await nextMessage(controller,m=>m.type==="device.connected");
  assert.equal(restored.type,"device.connected");
});
