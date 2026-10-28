package com.tabremote.app;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;

/** Tiny HTTP + WebSocket server. Pure Java (no Android classes) so it can be tested on a PC. */
public class WebServer {
    public interface Listener { void onMessage(String msg); }

    private final int port;
    private final byte[] page;
    private final Listener listener;
    private ServerSocket server;
    private volatile boolean running;

    public WebServer(int port, byte[] page, Listener l) { this.port = port; this.page = page; this.listener = l; }

    public void start() throws IOException {
        server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(new InetSocketAddress(port));
        running = true;
        Thread t = new Thread(() -> {
            while (running) {
                try {
                    final Socket s = server.accept();
                    s.setTcpNoDelay(true);
                    new Thread(() -> handle(s)).start();
                } catch (IOException e) { if (!running) return; }
            }
        }, "web-accept");
        t.setDaemon(true);
        t.start();
    }

    public void stop() { running = false; try { if (server != null) server.close(); } catch (IOException ignored) {} }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') sb.append((char) c);
            if (sb.length() > 8192) throw new IOException("line too long");
        }
        if (c == -1 && sb.length() == 0) return null;
        return sb.toString();
    }

    private void handle(Socket s) {
        try {
            s.setSoTimeout(0);
            InputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();
            String req = readLine(in);
            if (req == null) { s.close(); return; }
            String key = null; boolean upgrade = false; String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                String low = line.toLowerCase(Locale.ROOT);
                if (low.startsWith("sec-websocket-key:")) key = line.substring(18).trim();
                if (low.startsWith("upgrade:") && low.contains("websocket")) upgrade = true;
            }
            if (upgrade && key != null) {
                MessageDigest md = MessageDigest.getInstance("SHA-1");
                byte[] h = md.digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("UTF-8"));
                String accept = Base64.getEncoder().encodeToString(h);
                out.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes("UTF-8"));
                out.flush();
                wsLoop(s, in, out);
            } else {
                String path = req.split(" ").length > 1 ? req.split(" ")[1] : "/";
                if (path.equals("/") || path.startsWith("/?") || path.equals("/index.html")) {
                    out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nCache-Control: no-store\r\nContent-Length: " + page.length + "\r\nConnection: close\r\n\r\n").getBytes("UTF-8"));
                    out.write(page);
                } else {
                    out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".getBytes("UTF-8"));
                }
                out.flush();
                s.close();
            }
        } catch (Exception e) {
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    private void wsLoop(Socket s, InputStream in, OutputStream out) throws Exception {
        while (true) {
            int b0 = in.read(); if (b0 < 0) break;
            int b1 = in.read(); if (b1 < 0) break;
            int op = b0 & 0x0F;
            boolean masked = (b1 & 0x80) != 0;
            long len = b1 & 0x7F;
            if (len == 126) len = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
            else if (len == 127) { len = 0; for (int i = 0; i < 8; i++) len = (len << 8) | (in.read() & 0xFF); }
            if (len > 1_000_000) break;
            byte[] mask = new byte[4];
            if (masked) readFully(in, mask);
            byte[] data = new byte[(int) len];
            readFully(in, data);
            if (masked) for (int i = 0; i < data.length; i++) data[i] ^= mask[i & 3];
            if (op == 8) break;
            if (op == 9) { synchronized (out) { out.write(0x8A); out.write(data.length); out.write(data); out.flush(); } }
            else if (op == 1) listener.onMessage(new String(data, "UTF-8"));
        }
        s.close();
    }

    private static void readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) { int n = in.read(buf, off, buf.length - off); if (n < 0) throw new EOFException(); off += n; }
    }
}
