# Dynadot and Cloudflare DNS support

PocketHost supports two mutually exclusive authoritative DNS configurations per domain:

- **Dynadot DNS + Dynadot DDNS:** keep the domain registered at Dynadot, select Dynadot DNS nameservers, enable Dynamic DNS for the domain in Dynadot, generate its domain-specific DDNS password, then select Dynadot DNS / DDNS in PocketHost.
- **Cloudflare DNS:** keep the domain registered at Dynadot if desired, delegate its nameservers to Cloudflare, add it as an active Cloudflare zone, and select Cloudflare DNS API in PocketHost with a scoped token (Zone:Read, DNS:Edit).

A domain cannot actively use both providers' authoritative nameservers simultaneously. You may use Dynadot DNS for one domain and Cloudflare DNS for another, or delegate separate subdomains using DNS delegation if you intentionally manage that complexity.

## Dynadot DDNS setup

1. In Dynadot, open My Domains → Manage Domains → Action → DNS Settings.
2. Select Dynadot DNS.
3. Enable Dynamic DNS, generate a DDNS password, and save settings.
4. In PocketHost → Settings → Dynamic DNS, select Dynadot DNS / DDNS.
5. Enter the registered domain and hostname (@ for apex or www for a subdomain), save the domain-specific DDNS password, choose A and/or AAAA, then run a manual sync before enabling background sync.
6. Do not enter your Dynadot account password. The DDNS password is unique to the domain.

PocketHost calls Dynadot's documented HTTPS set_ddns endpoint with the requested record family and address. Dynadot's endpoint can replace conflicting records for the selected hostname. Review existing records before the first update. Dynadot DDNS requires the domain to use Dynadot DNS; it does not work while Cloudflare nameservers are authoritative.

Reference: https://www.dynadot.com/help/question/enable-DDNS

## Cloudflare DNS setup

Use an active Cloudflare zone and a scoped API token with Zone:Read and DNS:Edit. Cloudflare API token storage remains separate from Cloudflare Tunnel credentials. PocketHost preserves its explicit record ownership/adoption policy for Cloudflare A/AAAA updates.

## Network reachability

DDNS only keeps records synchronized with the address observed by the device. It does not create inbound reachability. If the Vivo Y95 is behind CGNAT, IPv4 A records will resolve but unsolicited inbound connections will not reach the phone. A globally routed IPv6 address may work only if the router and device firewall permit inbound TCP 80/443. Otherwise use an explicit public TCP relay/gateway or obtain a public address from the ISP. Cloudflare DNS proxying is not a substitute for a working origin connection and does not automatically make a CGNAT origin reachable.

PocketHost verifies public DNS after updates. A verified DNS answer is not presented as proof that a visitor can connect to the hosting service; use the separate external inbound probe for that.

## Credential handling

The Dynadot DDNS password is encrypted with Android Keystore AES-GCM. The Dynadot API requires the password as a query parameter over HTTPS; do not share device logs containing full request URLs. Cloudflare API tokens continue to use their separate encrypted credential store.