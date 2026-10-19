"""Run browser checks with isolated Java/PostgreSQL and local provider fixtures; no live book APIs."""
from contextlib import contextmanager
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import json
import os
import re
import subprocess
import sys
import tempfile
import threading
import time

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
from smoke_api import temporary_database, running_api
from catalog_browser import check_catalog
from auth_browser import check_auth
from library_browser import check_library


class Provider(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith("/search.json"):
            status, data = 200, {"docs": []}
        elif self.path == "/isbn/9780140449242.json":
            status, data = 200, {"key": "/books/OL3701480M", "covers": [8457009]}
        else:
            status, data = 404, {}
        body = json.dumps(data).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


@contextmanager
def vite(api_url):
    with tempfile.TemporaryFile(mode="w+") as log:
        process = subprocess.Popen(["node", "node_modules/vite/bin/vite.js", "--port", "0"],
            cwd=ROOT / "frontend", env=dict(os.environ, API_TARGET=api_url, NO_COLOR="1"),
            stdout=log, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 30
            while time.monotonic() < deadline:
                log.seek(0)
                output = log.read()
                match = re.search(r"http://127\.0\.0\.1:(\d+)", output)
                if match:
                    yield "http://127.0.0.1:" + match.group(1)
                    return
                if process.poll() is not None:
                    raise RuntimeError(output)
                time.sleep(0.1)
            raise TimeoutError("Vite did not start")
        finally:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()


def main():
    provider = ThreadingHTTPServer(("127.0.0.1", 0), Provider)
    thread = threading.Thread(target=provider.serve_forever, daemon=True)
    thread.start()
    try:
        with temporary_database() as (environment, _):
            url = f"http://127.0.0.1:{provider.server_port}"
            environment.update(OPEN_LIBRARY_BASE_URL=url, ITUNES_BASE_URL=url)
            with running_api(environment) as api_url, vite(api_url) as frontend_url:
                check_catalog(frontend_url)
                check_auth(frontend_url)
                check_library(frontend_url)
    finally:
        provider.shutdown()
        provider.server_close()
        thread.join()


if __name__ == "__main__":
    main()
