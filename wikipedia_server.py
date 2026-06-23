import json
import re
import sys
import urllib.parse
from http.server import HTTPServer, BaseHTTPRequestHandler

ZIM_PATH = r"C:\Users\Joaov\Desktop\ver final\wikipedia_br_all_maxi_2026-04.zim"
PORT = 8085

try:
    from libzim import Archive
    zim = Archive(ZIM_PATH)
    print(f"[WIKI] ZIM carregado: {zim.all_entry_count} entradas")
except Exception as e:
    print(f"[WIKI] Erro ao abrir ZIM: {e}")
    zim = None


class WikiHandler(BaseHTTPRequestHandler):
    def do_OPTIONS(self):
        self.send_response(200)
        self.send_cors()
        self.end_headers()

    def send_cors(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "*")

    def send_json(self, data, status=200):
        self.send_response(status)
        self.send_cors()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.end_headers()
        self.wfile.write(json.dumps(data, ensure_ascii=False).encode("utf-8"))

    def send_error_json(self, msg, status=400):
        self.send_json({"error": msg}, status)

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        params = urllib.parse.parse_qs(parsed.query)
        path = parsed.path.rstrip("/")

        if path == "/api/status":
            return self.send_json({
                "online": zim is not None,
                "entries": zim.all_entry_count if zim else 0,
                "path": ZIM_PATH
            })

        if zim is None:
            return self.send_error_json("ZIM não carregado", 503)

        if path == "/api/search":
            q = params.get("q", [None])[0]
            if not q or len(q) < 2:
                return self.send_error_json("Consulta muito curta (mín 2 caracteres)")
            max_results = int(params.get("max", ["10"])[0])
            try:
                results = []
                q_lower = q.lower()
                count = 0
                for entry in zim.iter_everything():
                    if count >= max_results * 3:
                        break
                    title = entry.title
                    if title and q_lower in title.lower():
                        snippet = ""
                        try:
                            content = entry.get_data().tobytes().decode("utf-8", errors="replace")
                            # Strip HTML tags for snippet
                            snippet = re.sub(r'<[^>]+>', '', content)
                            # Find the query context
                            idx = snippet.lower().find(q_lower)
                            if idx > 0:
                                start = max(0, idx - 100)
                                end = min(len(snippet), idx + len(q) + 200)
                                snippet = snippet[start:end] + ("..." if end < len(snippet) else "")
                            snippet = snippet[:300]
                        except:
                            snippet = ""
                        results.append({
                            "title": title,
                            "snippet": snippet.strip()[:200]
                        })
                        count += 1
                        if len(results) >= max_results:
                            break
                return self.send_json({"query": q, "results": results})
            except Exception as e:
                return self.send_error_json(f"Erro na busca: {str(e)}", 500)

        if path == "/api/article":
            title = params.get("title", [None])[0]
            if not title:
                return self.send_error_json("Parâmetro 'title' é obrigatório")
            try:
                # Find the article by title
                entry = None
                for e in zim.iter_everything():
                    if e.title and e.title.lower() == title.lower():
                        entry = e
                        break
                if entry is None:
                    return self.send_error_json("Artigo não encontrado", 404)
                raw = entry.get_data().tobytes().decode("utf-8", errors="replace")
                # Extract plain text from HTML
                text = re.sub(r'<[^>]+>', '', raw)
                text = re.sub(r'\s+', ' ', text).strip()
                # Truncate to reasonable length
                if len(text) > 5000:
                    text = text[:5000] + "..."
                return self.send_json({
                    "title": entry.title,
                    "content": text[:5000]
                })
            except Exception as e:
                return self.send_error_json(f"Erro ao ler artigo: {str(e)}", 500)

        return self.send_error_json("Rota não encontrada. Use /api/status, /api/search?q=..., /api/article?title=...", 404)

    def log_message(self, format, *args):
        print(f"[WIKI] {args[0]} {args[1]} {args[2]}")


if __name__ == "__main__":
    if zim is None:
        print("[WIKI] Servidor iniciando SEM ZIM (modo status-only)")
    server = HTTPServer(("127.0.0.1", PORT), WikiHandler)
    print(f"[WIKI] Servidor Wikipedia rodando em http://127.0.0.1:{PORT}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[WIKI] Servidor encerrado")
        server.server_close()
