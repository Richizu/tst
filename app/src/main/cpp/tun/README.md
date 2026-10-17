hev-socks5-tunnel is pinned to **2.18.0**. Version 2.19.0 enables TUN "offloads"
(TSO/jumbo segments) by default; on Android the VPN file descriptor cannot carry
them and no traffic passes through the tunnel. Do not bump the submodule past
2.18.x without testing on a real device.

`hev-drop-quic.patch` adds a flag that drops UDP/443 (QUIC) inside the tunnel so
TV YouTube clients fall back to TCP, which byedpi can handle.
