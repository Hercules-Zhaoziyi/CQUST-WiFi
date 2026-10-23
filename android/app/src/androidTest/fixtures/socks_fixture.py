"""Isolated SOCKS fixture: returns owned HTTP content, never connects to real targets."""
import socketserver
import struct

class Proxy(socketserver.BaseRequestHandler):
    def read(self, count):
        value = b''
        while len(value) < count:
            part = self.request.recv(count - len(value))
            if not part:
                raise EOFError()
            value += part
        return value

    def handle(self):
        self.request.settimeout(10)
        try:
            version, count = self.read(2)
            assert version == 5 and 2 in self.read(count)
            self.request.sendall(b'\x05\x02')
            assert self.read(1) == b'\x01'
            user = self.read(self.read(1)[0])
            password = self.read(self.read(1)[0])
            assert user == b'fixture-user' and password == b'fixture-password'
            self.request.sendall(b'\x01\x00')
            version, command, _, kind = self.read(4)
            assert version == 5 and command == 1
            self.read(4 if kind == 1 else 16 if kind == 4 else self.read(1)[0])
            self.read(2)
            self.request.sendall(b'\x05\x00\x00\x01\x7f\x00\x00\x01\x00\x50')
            data = b''
            while b'\r\n\r\n' not in data and len(data) < 16384:
                data += self.read(1)
            payload = b'CQUST_TUN_OK'
            self.request.sendall(b'HTTP/1.0 200 OK\r\nContent-Length: 12\r\n\r\n' + payload)
        except (EOFError, OSError, AssertionError):
            pass

class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

Server(('0.0.0.0', 17890), Proxy).serve_forever()
