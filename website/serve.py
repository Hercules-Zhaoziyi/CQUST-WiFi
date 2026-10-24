"""Preview server with explicit asset MIME types on Windows."""
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from functools import partial
from pathlib import Path

class Handler(SimpleHTTPRequestHandler):
    extensions_map={**SimpleHTTPRequestHandler.extensions_map,'.svg':'image/svg+xml','.css':'text/css','.js':'text/javascript','.png':'image/png','.html':'text/html; charset=utf-8'}

if __name__=='__main__':
    print('Local: http://127.0.0.1:4173/',flush=True)
    ThreadingHTTPServer(('127.0.0.1',4173),partial(Handler,directory=str(Path(__file__).resolve().parent/'dist'))).serve_forever()
