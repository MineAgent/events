#!/usr/bin/env python3
"""Minecraft 事件接收端：把客户端模组 POST 过来的 JSON 打印到屏幕上。

用法：
    python3 tcp_server.py                # 监听 127.0.0.1:3422
    python3 tcp_server.py --port 4000    # 换端口

对每个连接：
  * 标准 HTTP/1.1 POST（模组用这种方式）→ 打印请求体中的 JSON，回 200；
  * 不是 HTTP 的原始字节 → 原样打印。
"""

from __future__ import annotations

import argparse
import json
import socket
import sys
import threading
from datetime import datetime

MAX_HEADER_BYTES = 64 * 1024
MAX_BODY_BYTES = 8 * 1024 * 1024
IDLE_TIMEOUT_SECONDS = 300.0

_print_lock = threading.Lock()


def log(line: str) -> None:
    with _print_lock:
        print(line, flush=True)


def stamp() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]


def print_payload(peer: str, body: bytes) -> None:
    text = body.decode("utf-8", "replace").strip()
    if not text:
        log(f"[{stamp()}] {peer} (空请求体)")
        return
    try:
        parsed = json.loads(text)
    except json.JSONDecodeError:
        log(f"[{stamp()}] {peer} {text}")
        return

    kind = parsed.get("type", "?") if isinstance(parsed, dict) else "?"
    compact = json.dumps(parsed, ensure_ascii=False, separators=(",", ":"))
    log(f"[{stamp()}] {peer} [{kind}] {compact}")


def read_headers(conn: socket.socket, buf: bytes) -> tuple[bytes, bytes] | None:
    """读到 \\r\\n\\r\\n 为止，返回 (头部, 剩余字节)；连接提前关闭时返回 None。"""
    while b"\r\n\r\n" not in buf:
        chunk = conn.recv(4096)
        if not chunk:
            return None
        buf += chunk
        if len(buf) > MAX_HEADER_BYTES:
            raise ConnectionError("HTTP 头部过长")
    head, rest = buf.split(b"\r\n\r\n", 1)
    return head, rest


def serve_connection(conn: socket.socket, peer: str) -> None:
    conn.settimeout(IDLE_TIMEOUT_SECONDS)
    buf = b""
    try:
        while True:
            got = read_headers(conn, buf)
            if got is None:
                if buf.strip():
                    print_payload(peer, buf)
                return
            head, buf = got

            lines = head.split(b"\r\n")
            try:
                request_line = lines[0].decode("latin-1")
            except UnicodeDecodeError:
                print_payload(peer, head)
                return

            parts = request_line.split(" ")
            if len(parts) < 3 or not parts[2].startswith("HTTP/"):
                # 不是 HTTP：把这一批字节当成原始文本打印，然后等下一批。
                print_payload(peer, head + b"\r\n\r\n" + buf)
                buf = b""
                continue

            method, path, version = parts[0], parts[1], parts[2]
            headers: dict[str, str] = {}
            for line in lines[1:]:
                if b":" in line:
                    key, value = line.split(b":", 1)
                    headers[key.strip().lower().decode("latin-1")] = value.strip().decode("latin-1")

            if headers.get("transfer-encoding", "").lower() == "chunked":
                log(f"[{stamp()}] {peer} 不支持 chunked 请求，已忽略 {method} {path}")
                return

            try:
                length = int(headers.get("content-length", "0") or "0")
            except ValueError:
                length = 0
            if length < 0 or length > MAX_BODY_BYTES:
                log(f"[{stamp()}] {peer} 请求体长度非法：{length}")
                return

            while len(buf) < length:
                chunk = conn.recv(min(65536, length - len(buf)))
                if not chunk:
                    return
                buf += chunk
            body, buf = buf[:length], buf[length:]

            if method in ("POST", "PUT", "PATCH") and body:
                print_payload(peer, body)
            elif method == "GET" and path not in ("/", "/health"):
                log(f"[{stamp()}] {peer} GET {path}")

            keep_alive = version == "HTTP/1.1" and headers.get("connection", "").lower() != "close"
            response = (
                b"HTTP/1.1 200 OK\r\n"
                b"Content-Type: application/json\r\n"
                b"Content-Length: 11\r\n"
                + (b"" if keep_alive else b"Connection: close\r\n")
                + b"\r\n"
                b'{"ok":true}'
            )
            conn.sendall(response)
            if not keep_alive:
                return
    except (ConnectionError, socket.timeout, OSError) as exc:
        log(f"[{stamp()}] {peer} 连接结束：{exc}")
    finally:
        try:
            conn.close()
        except OSError:
            pass


def main() -> int:
    parser = argparse.ArgumentParser(description="打印 Minecraft 模组 POST 过来的事件")
    parser.add_argument("--host", default="127.0.0.1", help="监听地址（默认 127.0.0.1）")
    parser.add_argument("--port", type=int, default=3422, help="监听端口（默认 3422）")
    args = parser.parse_args()

    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((args.host, args.port))
    server.listen(16)
    log(f"事件服务器已启动：http://{args.host}:{args.port}/  （Ctrl+C 退出）")

    try:
        while True:
            conn, addr = server.accept()
            peer = f"{addr[0]}:{addr[1]}"
            thread = threading.Thread(target=serve_connection, args=(conn, peer), daemon=True)
            thread.start()
    except KeyboardInterrupt:
        log("正在退出。")
    finally:
        server.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
