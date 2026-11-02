package com.omer.belestiviclone.shared.transfer

import kotlinx.coroutines.*
import java.io.*
import java.net.*

class AndroidWebTransferServer(
    private val onPayload: (TransferPayload) -> Unit
) : WebTransferServerHandle {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override var serverInfo: TransferServerInfo? = null
        private set

    override val isRunning: Boolean get() = serverSocket?.isClosed == false && serverJob?.isActive == true

    override fun start(preferredPort: Int): TransferServerInfo {
        stop()
        val ip = findLocalIpAddress() ?: "127.0.0.1"
        var port = preferredPort
        var socket: ServerSocket? = null

        // Try preferred port, then probe up to 10 fallback ports
        for (candidate in port..(port + 10)) {
            try {
                socket = ServerSocket(candidate)
                port = candidate
                break
            } catch (_: IOException) {
                // Try next port
            }
        }

        if (socket == null) {
            val errInfo = TransferServerInfo(localIp = ip, port = preferredPort, isRunning = false, error = "Uygun port bulunamadı")
            serverInfo = errInfo
            return errInfo
        }

        serverSocket = socket
        val info = TransferServerInfo(localIp = ip, port = port, isRunning = true)
        serverInfo = info

        serverJob = scope.launch {
            while (isActive && !socket.isClosed) {
                try {
                    val client = socket.accept()
                    launch(Dispatchers.IO) {
                        handleClient(client)
                    }
                } catch (e: IOException) {
                    if (socket.isClosed) break
                }
            }
        }

        return info
    }

    override fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        serverInfo = null
    }

    private fun handleClient(client: Socket) {
        client.use { s ->
            s.soTimeout = 10000
            val input = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val output = OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8)

            val requestLine = input.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val path = parts[1]

            // Read headers
            var contentLength = 0
            var line: String?
            while (true) {
                line = input.readLine()
                if (line.isNullOrEmpty()) break
                val lower = line.lowercase()
                if (lower.startsWith("content-length:")) {
                    contentLength = lower.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            if (method == "GET" && (path == "/" || path.startsWith("/?"))) {
                val html = buildWebPageHtml()
                val bytes = html.toByteArray(Charsets.UTF_8)
                output.write("HTTP/1.1 200 OK\r\n")
                output.write("Content-Type: text/html; charset=UTF-8\r\n")
                output.write("Content-Length: ${bytes.size}\r\n")
                output.write("Connection: close\r\n\r\n")
                output.flush()
                s.getOutputStream().write(bytes)
                s.getOutputStream().flush()
            } else if (method == "POST" && path == "/api/transfer") {
                val body = if (contentLength > 0) {
                    val charArray = CharArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val count = input.read(charArray, readTotal, contentLength - readTotal)
                        if (count < 0) break
                        readTotal += count
                    }
                    String(charArray, 0, readTotal)
                } else ""

                val responseJson = processTransferRequest(body)
                val respBytes = responseJson.toByteArray(Charsets.UTF_8)
                output.write("HTTP/1.1 200 OK\r\n")
                output.write("Content-Type: application/json; charset=UTF-8\r\n")
                output.write("Access-Control-Allow-Origin: *\r\n")
                output.write("Content-Length: ${respBytes.size}\r\n")
                output.write("Connection: close\r\n\r\n")
                output.flush()
                s.getOutputStream().write(respBytes)
                s.getOutputStream().flush()
            } else {
                val notFound = "404 Not Found"
                output.write("HTTP/1.1 404 Not Found\r\n")
                output.write("Content-Length: ${notFound.length}\r\n")
                output.write("Connection: close\r\n\r\n")
                output.write(notFound)
                output.flush()
            }
        }
    }

    private fun processTransferRequest(body: String): String {
        return try {
            val json = parseSimpleJson(body)
            val type = json["type"] ?: "m3u_url"
            val name = (json["name"] ?: "").trim()

            val payload = when (type.lowercase()) {
                "m3u_url" -> {
                    val url = json["url"] ?: ""
                    TransferPayload(TransferPayloadKind.M3U_URL, name = name.ifEmpty { "Telefondan M3U" }, url = url)
                }
                "m3u_text" -> {
                    val content = json["content"] ?: ""
                    TransferPayload(TransferPayloadKind.M3U_TEXT, name = name.ifEmpty { "Telefondan Liste" }, content = content)
                }
                "xtream" -> {
                    val server = json["server"] ?: ""
                    val username = json["username"] ?: ""
                    val password = json["password"] ?: ""
                    TransferPayload(
                        TransferPayloadKind.XTREAM,
                        name = name.ifEmpty { "Xtream Portal" },
                        serverUrl = server,
                        username = username,
                        password = password
                    )
                }
                "stalker" -> {
                    val portal = json["portal"] ?: ""
                    val mac = json["mac"] ?: ""
                    TransferPayload(
                        TransferPayloadKind.STALKER,
                        name = name.ifEmpty { "Stalker Portal" },
                        portalUrl = portal,
                        mac = mac
                    )
                }
                "github" -> {
                    val repo = (json["url"] ?: json["repo"] ?: "").trim()
                    val path = (json["name"] ?: json["path"] ?: "main/TiviListe.m3u").trim().ifEmpty { "main/TiviListe.m3u" }
                    val token = (json["password"] ?: json["token"] ?: "").trim()
                    val autoUpdate = (json["username"] ?: json["autoUpdate"] ?: "Açık").trim().ifEmpty { "Açık" }
                    TransferPayload(
                        TransferPayloadKind.GITHUB,
                        name = path,
                        url = repo,
                        username = autoUpdate,
                        password = token
                    )
                }
                else -> error("Bilinmeyen aktarım türü: $type")
            }

            onPayload(payload)
            """{"success":true,"message":"Liste TV'ye başarıyla iletildi!"}"""
        } catch (e: Exception) {
            """{"success":false,"message":"Hata: ${e.message?.replace("\"", "'") ?: "İşlenemedi"}"}"""
        }
    }

    private fun parseSimpleJson(text: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val clean = text.trim().removePrefix("{").removeSuffix("}")
        var i = 0
        while (i < clean.length) {
            val keyStart = clean.indexOf('"', i)
            if (keyStart < 0) break
            val keyEnd = clean.indexOf('"', keyStart + 1)
            if (keyEnd < 0) break
            val key = clean.substring(keyStart + 1, keyEnd)

            val colon = clean.indexOf(':', keyEnd + 1)
            if (colon < 0) break

            var valStart = colon + 1
            while (valStart < clean.length && clean[valStart].isWhitespace()) valStart++
            if (valStart >= clean.length) break

            if (clean[valStart] == '"') {
                val sb = java.lang.StringBuilder()
                var p = valStart + 1
                var escape = false
                while (p < clean.length) {
                    val c = clean[p]
                    if (escape) {
                        when (c) {
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            '\\', '"', '/' -> sb.append(c)
                            else -> sb.append(c)
                        }
                        escape = false
                    } else if (c == '\\') {
                        escape = true
                    } else if (c == '"') {
                        p++
                        break
                    } else {
                        sb.append(c)
                    }
                    p++
                }
                map[key] = sb.toString()
                i = p
            } else {
                val comma = clean.indexOf(',', valStart)
                val rawVal = if (comma < 0) clean.substring(valStart).trim() else clean.substring(valStart, comma).trim()
                map[key] = rawVal
                i = if (comma < 0) clean.length else comma + 1
            }
        }
        return map
    }

    private fun findLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (iface in interfaces.asSequence()) {
                if (iface.isLoopback || !iface.isUp) continue
                for (addr in iface.inetAddresses.asSequence()) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun buildWebPageHtml(): String = """
<!DOCTYPE html>
<html lang="tr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Beleş TiVi — Telefondan Aktar</title>
    <style>
        :root {
            --bg-color: #0A1628;
            --card-color: #13233D;
            --card-border: #223858;
            --accent-color: #E67E22;
            --accent-hover: #D35400;
            --text-color: #FFFFFF;
            --text-secondary: #94A2B7;
            --success-color: #0ABB91;
            --error-color: #F44747;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            background-color: var(--bg-color);
            color: var(--text-color);
            padding: 16px;
            display: flex;
            justify-content: center;
        }
        .container {
            width: 100%;
            max-width: 520px;
            background: var(--card-color);
            border-radius: 14px;
            border: 1px solid var(--card-border);
            padding: 24px;
            box-shadow: 0 10px 30px rgba(0,0,0,0.5);
        }
        .header {
            text-align: center;
            margin-bottom: 20px;
        }
        .title {
            font-size: 20px;
            font-weight: bold;
            color: var(--text-color);
        }
        .subtitle {
            font-size: 13px;
            color: var(--text-secondary);
            margin-top: 4px;
        }
        .tabs {
            display: flex;
            background: rgba(0,0,0,0.25);
            border-radius: 10px;
            padding: 4px;
            margin-bottom: 20px;
        }
        .tab {
            flex: 1;
            text-align: center;
            padding: 10px 4px;
            font-size: 12px;
            font-weight: 600;
            color: var(--text-secondary);
            cursor: pointer;
            border-radius: 8px;
            transition: all 0.2s;
        }
        .tab.active {
            background: var(--accent-color);
            color: #FFFFFF;
        }
        .form-group {
            margin-bottom: 14px;
        }
        label {
            display: block;
            font-size: 11px;
            font-weight: bold;
            color: var(--text-secondary);
            text-transform: uppercase;
            letter-spacing: 0.5px;
            margin-bottom: 6px;
        }
        input[type="text"], input[type="password"], textarea {
            width: 100%;
            padding: 12px 14px;
            background: #0A1628;
            border: 1.5px solid var(--card-border);
            border-radius: 8px;
            color: #FFFFFF;
            font-size: 13px;
            outline: none;
            transition: border-color 0.2s;
        }
        input:focus, textarea:focus {
            border-color: var(--accent-color);
        }
        textarea {
            resize: vertical;
            min-height: 90px;
            font-family: monospace;
            font-size: 12px;
        }
        .file-upload {
            display: block;
            margin-top: 6px;
            font-size: 12px;
            color: var(--accent-color);
            cursor: pointer;
        }
        .file-upload input {
            display: none;
        }
        button.submit-btn {
            width: 100%;
            padding: 14px;
            background: var(--accent-color);
            color: #FFFFFF;
            font-size: 14px;
            font-weight: bold;
            border: none;
            border-radius: 8px;
            cursor: pointer;
            transition: background 0.2s;
            margin-top: 10px;
        }
        button.submit-btn:hover {
            background: var(--accent-hover);
        }
        .status-box {
            display: none;
            margin-top: 16px;
            padding: 12px;
            border-radius: 8px;
            font-size: 13px;
            text-align: center;
            font-weight: 500;
        }
        .status-success {
            display: block;
            background: rgba(10, 187, 145, 0.15);
            color: var(--success-color);
            border: 1px solid var(--success-color);
        }
        .status-error {
            display: block;
            background: rgba(244, 71, 71, 0.15);
            color: var(--error-color);
            border: 1px solid var(--error-color);
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div class="title">📺 Beleş TiVi TV Aktarımı</div>
            <div class="subtitle">Telefondan TV'ye kanal listesi veya hesap gönderin</div>
        </div>

        <div class="tabs">
            <div class="tab active" onclick="switchTab('m3u')">M3U Listesi</div>
            <div class="tab" onclick="switchTab('xtream')">Xtream Codes</div>
            <div class="tab" onclick="switchTab('stalker')">Stalker / MAC</div>
            <div class="tab" onclick="switchTab('github')">GitHub Private</div>
        </div>

        <!-- M3U Form -->
        <div id="tab-m3u" class="tab-content">
            <div class="form-group">
                <label>Liste Adı (İsteğe Bağlı)</label>
                <input type="text" id="m3u-name" placeholder="Örn: Favori Kanallarım">
            </div>
            <div class="form-group">
                <label>M3U URL Bağlantısı</label>
                <input type="text" id="m3u-url" placeholder="http://... / liste.m3u">
            </div>
            <div style="text-align: center; margin: 10px 0; font-size: 11px; color: var(--text-secondary);">— VEYA METİN / DOSYA OLARAK YÜKLEYİN —</div>
            <div class="form-group">
                <label>M3U Metni (Paste Content)</label>
                <textarea id="m3u-text" placeholder="#EXTM3U&#10;#EXTINF:-1,Kanal Adı&#10;http://..."></textarea>
                <label class="file-upload">
                    📂 Dosyadan M3U Seç...
                    <input type="file" id="m3u-file" accept=".m3u,.m3u8,.txt" onchange="handleFileSelect(event)">
                </label>
            </div>
            <button class="submit-btn" onclick="sendM3u()">TV'ye Gönder</button>
        </div>

        <!-- Xtream Form -->
        <div id="tab-xtream" class="tab-content" style="display:none;">
            <div class="form-group">
                <label>Bağlantı Adı (İsteğe Bağlı)</label>
                <input type="text" id="xtream-name" placeholder="Örn: Xtream Sunucum">
            </div>
            <div class="form-group">
                <label>Sunucu Adresi (URL & Port)</label>
                <input type="text" id="xtream-server" placeholder="http://sunucu.adresi:8080">
            </div>
            <div class="form-group">
                <label>Kullanıcı Adı</label>
                <input type="text" id="xtream-user" placeholder="Kullanıcı Adı">
            </div>
            <div class="form-group">
                <label>Şifre</label>
                <input type="password" id="xtream-pass" placeholder="Şifre">
            </div>
            <button class="submit-btn" onclick="sendXtream()">TV'ye Gönder</button>
        </div>

        <!-- Stalker Form -->
        <div id="tab-stalker" class="tab-content" style="display:none;">
            <div class="form-group">
                <label>Bağlantı Adı (İsteğe Bağlı)</label>
                <input type="text" id="stalker-name" placeholder="Örn: Stalker Portal">
            </div>
            <div class="form-group">
                <label>Portal URL</label>
                <input type="text" id="stalker-portal" placeholder="http://portal.adresi/c/">
            </div>
            <div class="form-group">
                <label>MAC Adresi</label>
                <input type="text" id="stalker-mac" placeholder="00:1A:79:XX:XX:XX">
            </div>
            <button class="submit-btn" onclick="sendStalker()">TV'ye Gönder</button>
        </div>

        <!-- GitHub Private Form -->
        <div id="tab-github" class="tab-content" style="display:none;">
            <div class="form-group">
                <label>GitHub Depo (kullanici/depo veya URL)</label>
                <input type="text" id="github-repo" placeholder="Örn: omerolgun/tiviliste">
            </div>
            <div class="form-group">
                <label>Dal ve Dosya Yolu (Branch / Path)</label>
                <input type="text" id="github-path" value="main/TiviListe.m3u" placeholder="main/TiviListe.m3u">
            </div>
            <div class="form-group">
                <label>GitHub Token (Private Repo İçin)</label>
                <input type="password" id="github-token" placeholder="github_pat_... veya ghp_...">
            </div>
            <button class="submit-btn" onclick="sendGitHub()">TV'ye Gönder ve Güncelle</button>
        </div>

        <div id="status" class="status-box"></div>
    </div>

    <script>
        function switchTab(tabId) {
            document.querySelectorAll('.tab').forEach((t, idx) => {
                t.classList.toggle('active', (tabId === 'm3u' && idx === 0) || (tabId === 'xtream' && idx === 1) || (tabId === 'stalker' && idx === 2) || (tabId === 'github' && idx === 3));
            });
            document.getElementById('tab-m3u').style.display = tabId === 'm3u' ? 'block' : 'none';
            document.getElementById('tab-xtream').style.display = tabId === 'xtream' ? 'block' : 'none';
            document.getElementById('tab-stalker').style.display = tabId === 'stalker' ? 'block' : 'none';
            document.getElementById('tab-github').style.display = tabId === 'github' ? 'block' : 'none';
            hideStatus();
        }

        function showStatus(msg, isSuccess) {
            const el = document.getElementById('status');
            el.className = 'status-box ' + (isSuccess ? 'status-success' : 'status-error');
            el.innerText = msg;
        }

        function hideStatus() {
            const el = document.getElementById('status');
            el.className = 'status-box';
            el.innerText = '';
        }

        function handleFileSelect(evt) {
            const file = evt.target.files[0];
            if (!file) return;
            const reader = new FileReader();
            reader.onload = function(e) {
                document.getElementById('m3u-text').value = e.target.result;
                if (!document.getElementById('m3u-name').value) {
                    document.getElementById('m3u-name').value = file.name.replace(/\.[^/.]+${'$'}, "");
                }
            };
            reader.readAsText(file);
        }

        function postData(payload) {
            showStatus("TV'ye aktarılıyor...", true);
            fetch('/api/transfer', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            })
            .then(res => res.json())
            .then(data => {
                if (data.success) {
                    showStatus("✅ " + data.message, true);
                } else {
                    showStatus("❌ " + data.message, false);
                }
            })
            .catch(err => {
                showStatus("❌ Aktarım hatası: " + err, false);
            });
        }

        function sendM3u() {
            const name = document.getElementById('m3u-name').value.trim();
            const url = document.getElementById('m3u-url').value.trim();
            const text = document.getElementById('m3u-text').value.trim();

            if (url) {
                postData({ type: 'm3u_url', name: name, url: url });
            } else if (text) {
                postData({ type: 'm3u_text', name: name, content: text });
            } else {
                showStatus("Lütfen bir M3U URL veya M3U metni girin.", false);
            }
        }

        function sendXtream() {
            const name = document.getElementById('xtream-name').value.trim();
            const server = document.getElementById('xtream-server').value.trim();
            const user = document.getElementById('xtream-user').value.trim();
            const pass = document.getElementById('xtream-pass').value.trim();

            if (!server || !user || !pass) {
                showStatus("Lütfen Sunucu, Kullanıcı Adı ve Şifre alanlarını doldurun.", false);
                return;
            }
            postData({ type: 'xtream', name: name, server: server, username: user, password: pass });
        }

        function sendStalker() {
            const name = document.getElementById('stalker-name').value.trim();
            const portal = document.getElementById('stalker-portal').value.trim();
            const mac = document.getElementById('stalker-mac').value.trim();

            if (!portal || !mac) {
                showStatus("Lütfen Portal URL ve MAC Adresi alanlarını doldurun.", false);
                return;
            }
            postData({ type: 'stalker', name: name, portal: portal, mac: mac });
        }

        function sendGitHub() {
            const repo = document.getElementById('github-repo').value.trim();
            const path = document.getElementById('github-path').value.trim() || 'main/TiviListe.m3u';
            const token = document.getElementById('github-token').value.trim();

            if (!repo) {
                showStatus("Lütfen GitHub depo adını (kullanici/depo) veya URL'yi girin.", false);
                return;
            }
            postData({ type: 'github', url: repo, name: path, password: token, username: 'Açık' });
        }
    </script>
</body>
</html>
""".trimIndent()
}
