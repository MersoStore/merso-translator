package com.merso.gametranslate

import android.content.Context
import android.net.wifi.WifiManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

object TvServer {
    const val PORT = 8080
    @Volatile var running = false
    private var serverSocket: ServerSocket? = null
    private val exec = Executors.newCachedThreadPool()
    @Volatile private var fontBytes: ByteArray? = null

    // Loads the Cairo caption font from assets so the TV page can use it
    // over the LAN (no internet needed on the TV).
    fun init(ctx: Context) {
        if (fontBytes != null) return
        try {
            fontBytes = ctx.applicationContext.assets.open("cairo.ttf").readBytes()
        } catch (_: Exception) {}
    }

    fun localIp(ctx: Context): String? {
        return try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            if (ip == 0) null else String.format(
                "%d.%d.%d.%d",
                ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff
            )
        } catch (e: Exception) {
            null
        }
    }

    fun start() {
        if (running) return
        running = true
        exec.execute {
            try {
                serverSocket = ServerSocket(PORT)
                while (running) {
                    val socket = serverSocket?.accept() ?: break
                    exec.execute { handle(socket) }
                }
            } catch (e: Exception) {
                running = false
            }
        }
    }

    private fun handle(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val requestLine = reader.readLine() ?: ""
            var host = "127.0.0.1:$PORT"
            var line = reader.readLine()
            while (line != null && line.isNotEmpty()) {
                if (line.lowercase().startsWith("host:")) {
                    host = line.substring(5).trim()
                }
                line = reader.readLine()
            }
            val path = requestLine.split(" ").getOrNull(1) ?: "/"
            val (contentType, body) = when {
                path.startsWith("/cairo.ttf") -> "font/ttf" to (fontBytes ?: ByteArray(0))
                path.startsWith("/msx/start.json") -> "application/json" to startJson().toByteArray(Charsets.UTF_8)
                path.startsWith("/msx/menu.json") -> "application/json" to menuJson(host).toByteArray(Charsets.UTF_8)
                path.startsWith("/tv") -> "text/html; charset=utf-8" to page().toByteArray(Charsets.UTF_8)
                else -> "text/html; charset=utf-8" to page().toByteArray(Charsets.UTF_8)
            }
            val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
            val out = socket.getOutputStream()
            out.write(header.toByteArray())
            out.write(body)
            out.flush()
        } catch (e: Exception) {
            // ignore
        } finally {
            try { socket.close() } catch (e: Exception) {}
        }
    }

    private fun startJson(): String = """
{
  "name": "${Str.p("مرسو للترجمة", "Merso Translator")}",
  "version": "1.0.0",
  "parameter": "menu:http://{SERVER}/msx/menu.json",
  "welcome": "none"
}
""".trimIndent()

    private fun menuJson(host: String): String = """
{
  "name": "${Str.p("مرسو للترجمة", "Merso Translator")}",
  "version": "1.0.0",
  "headline": "${Str.p("مرسو للترجمة", "Merso Translator")}",
  "menu": [
    {
      "label": "${Str.p("تشغيل الترجمة", "Start Translation")}",
      "focus": true,
      "execute": true,
      "data": {
        "headline": "${Str.p("الترجمة", "Translation")}",
        "pages": [
          {
            "items": [
              {
                "layout": "0,0,12,6",
                "type": "button",
                "label": "${Str.p("افتح صفحة الترجمة", "Open Translation Page")}",
                "execute": true,
                "action": "link:http://$host/tv"
              }
            ]
          }
        ]
      }
    }
  ]
}
""".trimIndent()

    private fun page(): String = PAGE
        .replace("%LANG%", if (Str.lang == "en") "en" else "ar")
        .replace("%DIR%", if (Str.lang == "en") "ltr" else "rtl")
        .replace("%TITLE%", Str.p("مرسو للترجمة - التلفزيون", "Merso Translator - TV"))
        .replace("%CODELABEL%", Str.p("كود الربط — اكتبه في تطبيق الموبايل", "Pairing code — enter it in the phone app"))
        .replace("%CONNECTING%", Str.p("بيتصل...", "Connecting..."))
        .replace("%WAITPHONE%", Str.p("متصل، مستني الموبايل...", "Connected, waiting for the phone..."))
        .replace("%PHONEWAIT%", Str.p("الموبايل متصل ✓ مستني الترجمة...", "Phone connected ✓ waiting for translation..."))
        .replace("%PHONECONN%", Str.p("الموبايل متصل ✓", "Phone connected ✓"))
        .replace("%ERR%", Str.p("مشكلة في الاتصال، حدّث الصفحة", "Connection problem, refresh the page"))

    private val PAGE = """
<!DOCTYPE html>
<html lang="%LANG%" dir="%DIR%">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>%TITLE%</title>
<script src="https://unpkg.com/mqtt@4.3.7/dist/mqtt.min.js"></script>
<style>
  @font-face{font-family:'Cairo';src:url('/cairo.ttf') format('truetype');font-weight:900;font-style:normal}
  html,body{margin:0;padding:0;background:#000;color:#fff;font-family:sans-serif;height:100%;overflow:hidden}
  #hdmi{position:absolute;top:0;left:0;width:100%;height:100%;background:#000}
  #codeBox{position:absolute;top:3%;width:100%;text-align:center;z-index:3}
  #codeLabel{font-size:2.2vw;color:#B0B7C3;text-shadow:0 0 6px #000}
  #code{font-size:7vw;font-weight:bold;color:#FFC400;letter-spacing:.3vw;direction:ltr;text-shadow:0 0 8px #000}
  #status{font-size:1.8vw;color:#8A93A3;margin-top:1vw;text-shadow:0 0 6px #000}
  #inputs{position:absolute;top:3%;right:2%;z-index:4}
  #inputs button{font-size:1.6vw;padding:.6vw 1.2vw;margin:.3vw;background:#222;color:#FFC400;border:1px solid #FFC400;border-radius:8px}
  .cap{position:absolute;transform:translate(-50%,-50%);max-width:80%;text-align:center;z-index:3;
    white-space:pre-line;font-family:'Cairo',sans-serif;font-weight:900;
    font-size:2.6vw;line-height:1.4;color:#fff;
    text-shadow:0 0 8px #000,0 0 8px #000,2px 2px 4px #000;
    background:rgba(0,0,0,.45);border-radius:10px;padding:.6vw 1.2vw;display:none}
  #hideBtn{position:absolute;top:2%;left:1.5%;z-index:5;font-size:1.3vw;padding:.25vw .55vw;background:rgba(34,34,34,.7);color:#FFC400;border:1px solid rgba(255,196,0,.6);border-radius:50%;opacity:.8;line-height:1}
</style>
</head>
<body>
<video id="hdmi" autoplay muted playsinline></video>
<button id="hideBtn" onclick="toggleUi()">👁</button>
<div id="inputs">
  <button onclick="setInput(1)">HDMI 1</button>
  <button onclick="setInput(2)">HDMI 2</button>
  <button onclick="setInput(3)">HDMI 3</button>
  <button onclick="setInput(4)">HDMI 4</button>
</div>
<div id="codeBox">
  <div id="codeLabel">%CODELABEL%</div>
  <div id="code">------</div>
  <div id="status">%CONNECTING%</div>
</div>
<div id="caps"></div>
<script>
var uiHidden = false;
function toggleUi(){
  uiHidden = !uiHidden;
  document.getElementById('codeBox').style.display = uiHidden ? 'none' : 'block';
  document.getElementById('inputs').style.display = uiHidden ? 'none' : 'block';
  var b = document.getElementById('hideBtn');
  b.style.textDecoration = uiHidden ? 'line-through' : 'none';
  b.style.opacity = uiHidden ? '.45' : '.8';
}
function setInput(n){
  var v = document.getElementById('hdmi');
  v.innerHTML = '';
  var s = document.createElement('source');
  s.src = 'ext://hdmi:' + n;
  s.type = 'service/webos-external';
  v.appendChild(s);
  v.load();
  var p = v.play();
  if (p && p.catch) p.catch(function(){});
}
setInput(1);
</script>
<script>
(function(){
  var code = localStorage.getItem('merso_code') || String(Math.floor(100000 + Math.random()*900000));
  localStorage.setItem('merso_code', code);
  document.getElementById('code').textContent = code;
  var statusEl = document.getElementById('status');
  var capsEl = document.getElementById('caps');
  var caps = {};
  var timers = {};
  var curStyle = {fontSize:'2.6vw', color:'#fff', background:'rgba(0,0,0,.45)', dur:'M'};
  var SIZES = {S:'1.8vw', M:'2.6vw', L:'3.4vw', H:'4.4vw'};
  function applyStyle(el){
    if (!el.dataset.customSize) el.style.fontSize = curStyle.fontSize;
    el.style.background = curStyle.background;
    if (!el.dataset.custom) el.style.color = curStyle.color;
  }
  function getCap(box){
    if (!caps[box]) {
      var d = document.createElement('div');
      d.className = 'cap';
      applyStyle(d);
      capsEl.appendChild(d);
      caps[box] = d;
    }
    return caps[box];
  }
  var client = mqtt.connect('wss://broker.emqx.io:8084/mqtt', {connectTimeout: 10000});
  client.on('connect', function(){
    statusEl.textContent = '%WAITPHONE%';
    client.subscribe('merso/gametranslate/v1/' + code + '/sub');
    client.subscribe('merso/gametranslate/v1/' + code + '/presence');
    client.subscribe('merso/gametranslate/v1/' + code + '/style');
    client.publish('merso/gametranslate/v1/' + code + '/presence',
      JSON.stringify({role:'tv'}), {retain:true});
  });
  client.on('message', function(topic, payload){
    try {
      var msg = JSON.parse(payload.toString());
      if (topic.slice(-6) === '/style') {
        var sizes = SIZES;
        if (msg.size && SIZES[msg.size]) curStyle.fontSize = SIZES[msg.size];
        if (msg.color) curStyle.color = (msg.color === 'yellow') ? '#FFD400' : '#fff';
        if (typeof msg.bg === 'number') curStyle.background = 'rgba(0,0,0,' + (msg.bg/100) + ')';
        if (msg.dur) curStyle.dur = msg.dur;
        for (var k in caps) applyStyle(caps[k]);
        return;
      }
      if (msg.role === 'phone') {
        statusEl.textContent = '%PHONEWAIT%';
        return;
      }
      if (msg.clear) {
        if (typeof msg.box === 'number') {
          var g = msg.box;
          for (var k in caps) {
            var n = parseInt(k, 10);
            if (n === g || (n >= g * 10 && n < g * 10 + 10)) {
              caps[k].style.display = 'none';
              if (timers[k]) clearTimeout(timers[k]);
            }
          }
        } else {
          for (var k in caps) {
            caps[k].style.display = 'none';
            if (timers[k]) clearTimeout(timers[k]);
          }
        }
        return;
      }
      if (msg.ar) {
        var box = (typeof msg.box === 'number') ? msg.box : 0;
        var x = (typeof msg.x === 'number') ? msg.x : 0.5;
        var y = (typeof msg.y === 'number') ? msg.y : 0.9;
        var el = getCap(box);
        el.textContent = msg.ar;
        if (msg.size && SIZES[msg.size]) { el.style.fontSize = SIZES[msg.size]; el.dataset.customSize = '1'; }
        else { el.style.fontSize = curStyle.fontSize; el.dataset.customSize = ''; }
        if (msg.color) { el.style.color = msg.color; el.dataset.custom = '1'; }
        else { el.style.color = curStyle.color; el.dataset.custom = ''; }
        el.style.left = (x * 100) + '%';
        el.style.top = (y * 100) + '%';
        el.style.display = 'block';
        statusEl.textContent = '%PHONECONN%';
        if (timers[box]) clearTimeout(timers[box]);
        var readMs;
        if (curStyle.dur === 'S') readMs = Math.max(2500, Math.min(7000, 1800 + msg.ar.length * 55));
        else if (curStyle.dur === 'L') readMs = Math.max(5000, Math.min(15000, 3500 + msg.ar.length * 110));
        else readMs = Math.max(3500, Math.min(10000, 2500 + msg.ar.length * 80));
        timers[box] = setTimeout(function(){ el.style.display='none'; }, readMs);
      }
    } catch(e){}
  });
  client.on('error', function(){ statusEl.textContent = '%ERR%'; });
})();
</script>
</body>
</html>
""".trimIndent()
}
