"""Receive one CASMS QA employee activation message on loopback."""

from email import policy
from email.parser import BytesParser
from queue import Queue
import re
from socketserver import StreamRequestHandler, ThreadingTCPServer
from threading import Thread


class ActivationMessage(StreamRequestHandler):
    def handle(self):
        self.connection.settimeout(5)
        self.wfile.write(b"220 localhost QA activation\r\n")
        recipient = None
        while line := self.rfile.readline(8192):
            command = line.decode("ascii", errors="replace").strip()
            verb = command.split(" ", 1)[0].upper()
            answer = b"250 OK"
            if verb == "MAIL" or verb == "RSET":
                recipient = None
            elif verb == "RCPT":
                match = re.fullmatch(r"rcpt to:<([^>]+)>", command.lower())
                recipient = match[1] if match and match[1] in self.server.tokens else None
                if not recipient:
                    answer = b"550 Recipient outside QA activation scope"
            elif verb == "DATA" and recipient:
                self.wfile.write(b"354 Send message\r\n")
                body = bytearray()
                while part := self.rfile.readline(8192):
                    if part == b".\r\n":
                        break
                    body.extend(part)
                    if len(body) > 65536:
                        return
                message = BytesParser(policy=policy.default).parsebytes(bytes(body))
                plain = message.get_body(preferencelist=("plain",))
                if plain is not None:
                    token = re.search(r"#token=([a-f0-9-]{36}\.[A-Za-z0-9_-]{43})", plain.get_content())
                    if token:
                        self.server.tokens[recipient].put(token[1])
                recipient = None
            elif verb == "QUIT":
                self.wfile.write(b"221 Goodbye\r\n")
                return
            elif verb not in {"EHLO", "HELO", "NOOP"}:
                answer = b"503 Unsupported command or recipient"
            self.wfile.write(answer + b"\r\n")


class ActivationInbox(ThreadingTCPServer):
    daemon_threads = True

    def __init__(self, recipients):
        self.tokens = {recipient: Queue() for recipient in recipients}
        super().__init__(("127.0.0.1", 0), ActivationMessage)
        self.worker = Thread(target=self.serve_forever, daemon=True)

    def __enter__(self):
        self.worker.start()
        return self

    def __exit__(self, *_):
        self.shutdown()
        self.server_close()
        self.worker.join()
