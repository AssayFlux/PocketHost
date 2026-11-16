use base64::{engine::general_purpose::STANDARD, Engine as _};
use serde::{Deserialize, Serialize};
use std::{
    collections::HashMap,
    net::{SocketAddr, UdpSocket},
    sync::{Arc, Mutex},
    time::Duration,
};
use tokio::sync::{mpsc, oneshot};
use uuid::Uuid;

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct RelayRequest {
    pub id: Uuid,
    pub method: String,
    pub path: String,
    pub hostname: String,
    pub site_id: String,
    pub headers: HashMap<String, String>,
    pub body_base64: String,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct RelayResponse {
    pub id: Uuid,
    pub status: u16,
    #[serde(default)]
    pub headers: HashMap<String, String>,
    #[serde(default)]
    pub body_base64: String,
    #[serde(default)]
    pub set_cookies: Vec<String>,
}

struct PendingResponse {
    device_id: Uuid,
    sender: oneshot::Sender<RelayResponse>,
}

#[derive(Clone, Default)]
pub struct RelayHub {
    sessions: Arc<Mutex<HashMap<Uuid, (Uuid, mpsc::Sender<RelayRequest>)>>>,
    pending: Arc<Mutex<HashMap<Uuid, PendingResponse>>>,
}

impl RelayHub {
    pub fn connect(&self, device_id: Uuid) -> (Uuid, mpsc::Receiver<RelayRequest>) {
        let session_id = Uuid::new_v4();
        let (sender, receiver) = mpsc::channel(64);
        self.sessions.lock().unwrap_or_else(|e| e.into_inner())
            .insert(device_id, (session_id, sender));
        (session_id, receiver)
    }

    pub fn disconnect(&self, device_id: Uuid, session_id: Uuid) {
        let mut sessions = self.sessions.lock().unwrap_or_else(|e| e.into_inner());
        if sessions.get(&device_id).map(|entry| entry.0) == Some(session_id) {
            sessions.remove(&device_id);
        }
    }

    pub fn sender(&self, device_id: Uuid) -> Option<mpsc::Sender<RelayRequest>> {
        self.sessions.lock().unwrap_or_else(|e| e.into_inner())
            .get(&device_id).map(|entry| entry.1.clone())
    }

    pub fn register_pending(&self, request_id: Uuid, device_id: Uuid) -> Option<oneshot::Receiver<RelayResponse>> {
        let mut pending = self.pending.lock().unwrap_or_else(|e| e.into_inner());
        let global_count = pending.len();
        let device_count = pending.values().filter(|entry| entry.device_id == device_id).count();
        if global_count >= 32 || device_count >= 8 { return None; }
        let (sender, receiver) = oneshot::channel();
        pending.insert(request_id, PendingResponse { device_id, sender });
        Some(receiver)
    }

    pub fn complete(&self, device_id: Uuid, response: RelayResponse) -> bool {
        let pending = {
            let mut requests = self.pending.lock().unwrap_or_else(|e| e.into_inner());
            if requests.get(&response.id).map(|pending| pending.device_id) != Some(device_id) {
                return false;
            }
            requests.remove(&response.id)
        };
        pending.map(|pending| pending.sender.send(response).is_ok()).unwrap_or(false)
    }

    pub fn revoke_device(&self, device_id: Uuid) {
        self.sessions.lock().unwrap_or_else(|e| e.into_inner()).remove(&device_id);
        self.pending.lock().unwrap_or_else(|e| e.into_inner())
            .retain(|_, pending| pending.device_id != device_id);
    }

    pub fn cancel_pending(&self, request_id: Uuid) {
        self.pending.lock().unwrap_or_else(|e| e.into_inner()).remove(&request_id);
    }
}

pub struct RelaySessionGuard {
    hub: RelayHub,
    device_id: Uuid,
    session_id: Uuid,
}

impl RelaySessionGuard {
    pub fn new(hub: RelayHub, device_id: Uuid, session_id: Uuid) -> Self {
        Self { hub, device_id, session_id }
    }
}

impl Drop for RelaySessionGuard {
    fn drop(&mut self) {
        self.hub.disconnect(self.device_id, self.session_id);
    }
}

fn read_dns_name(packet: &[u8], offset: &mut usize) -> Option<String> {
    let mut cursor = *offset;
    let mut jumped = false;
    let mut jumps = 0usize;
    let mut labels = Vec::new();
    loop {
        let length = *packet.get(cursor)?;
        if length & 0xc0 == 0xc0 {
            let low = *packet.get(cursor + 1)?;
            let pointer = (((length & 0x3f) as usize) << 8) | low as usize;
            if !jumped { *offset = cursor + 2; }
            cursor = pointer;
            jumped = true;
            jumps += 1;
            if jumps > 16 { return None; }
            continue;
        }
        if length == 0 {
            if !jumped { *offset = cursor + 1; }
            break;
        }
        if length & 0xc0 != 0 || length > 63 { return None; }
        let begin = cursor + 1;
        let finish = begin.checked_add(length as usize)?;
        let label = std::str::from_utf8(packet.get(begin..finish)?).ok()?;
        if !label.bytes().all(|byte| byte.is_ascii_alphanumeric() || byte == b'-' || byte == b'_') { return None; }
        labels.push(label.to_ascii_lowercase());
        cursor = finish;
        if !jumped { *offset = cursor; }
        if labels.iter().map(|label: &String| label.len() + 1).sum::<usize>() > 255 { return None; }
    }
    Some(labels.join("."))
}

pub fn parse_dns_txt_answers(packet: &[u8], expected_name: &str) -> Vec<String> {
    if packet.len() < 12 { return Vec::new(); }
    let flags = u16::from_be_bytes([packet[2], packet[3]]);
    if flags & 0x8000 == 0 || flags & 0x7800 != 0 || flags & 0x0200 != 0 || flags & 0x000f != 0 {
        return Vec::new();
    }
    let questions = u16::from_be_bytes([packet[4], packet[5]]) as usize;
    let answers = u16::from_be_bytes([packet[6], packet[7]]) as usize;
    if questions != 1 || answers > 256 { return Vec::new(); }
    let mut offset = 12usize;
    let Some(question_name) = read_dns_name(packet, &mut offset) else { return Vec::new(); };
    if question_name != expected_name.trim_end_matches('.').to_ascii_lowercase() ||
        packet.get(offset..offset + 4).is_none() ||
        u16::from_be_bytes([packet[offset], packet[offset + 1]]) != 16 ||
        u16::from_be_bytes([packet[offset + 2], packet[offset + 3]]) != 1 {
        return Vec::new();
    }
    offset += 4;
    let mut values = Vec::new();
    for _ in 0..answers {
        let Some(owner) = read_dns_name(packet, &mut offset) else { break };
        if packet.get(offset..offset + 10).is_none() { break; }
        let record_type = u16::from_be_bytes([packet[offset], packet[offset + 1]]);
        let record_class = u16::from_be_bytes([packet[offset + 2], packet[offset + 3]]);
        let length = u16::from_be_bytes([packet[offset + 8], packet[offset + 9]]) as usize;
        offset += 10;
        let Some(data) = packet.get(offset..offset.saturating_add(length)) else { break };
        if record_type == 16 && record_class == 1 && owner == question_name {
            let mut part = 0usize;
            let mut text = Vec::new();
            let mut valid = true;
            while part < data.len() {
                let size = data[part] as usize;
                part += 1;
                if part + size > data.len() { valid = false; break; }
                text.extend_from_slice(&data[part..part + size]);
                part += size;
            }
            if valid {
                if let Ok(value) = String::from_utf8(text) { values.push(value); }
            }
        }
        offset += length;
    }
    values
}

pub async fn dns_txt_records(name: String) -> anyhow::Result<Vec<String>> {
    tokio::task::spawn_blocking(move || {
        let mut query = Vec::with_capacity(512);
        let id_bytes = Uuid::new_v4();
        let id = u16::from_be_bytes([id_bytes.as_bytes()[0], id_bytes.as_bytes()[1]]);
        query.extend_from_slice(&id.to_be_bytes());
        query.extend_from_slice(&[0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00]);
        anyhow::ensure!(name.len() <= 253, "DNS TXT name is too long");
        for label in name.trim_end_matches('.').split('.') {
            anyhow::ensure!(!label.is_empty() && label.len() <= 63, "invalid DNS TXT name");
            query.push(label.len() as u8);
            query.extend_from_slice(label.as_bytes());
        }
        query.push(0);
        query.extend_from_slice(&16u16.to_be_bytes());
        query.extend_from_slice(&1u16.to_be_bytes());

        let resolver: SocketAddr = std::env::var("POCKETHOST_DNS_RESOLVER")
            .unwrap_or_else(|_| "1.1.1.1:53".into()).parse()?;
        let bind_address = if resolver.is_ipv4() { "0.0.0.0:0" } else { "[::]:0" };
        let socket = UdpSocket::bind(bind_address)?;
        socket.set_read_timeout(Some(Duration::from_secs(3)))?;
        socket.set_write_timeout(Some(Duration::from_secs(3)))?;
        socket.send_to(&query, resolver)?;
        let mut response = [0u8; 4096];
        let (length, source) = socket.recv_from(&mut response)?;
        anyhow::ensure!(source == resolver, "DNS response came from an unexpected resolver");
        anyhow::ensure!(length >= 12, "truncated DNS response");
        anyhow::ensure!(u16::from_be_bytes([response[0], response[1]]) == id, "DNS transaction ID mismatch");
        anyhow::ensure!(response[2] & 0x80 != 0, "DNS server did not return a response");
        anyhow::ensure!(response[2] & 0x02 == 0, "DNS response was truncated");
        Ok(parse_dns_txt_answers(&response[..length], &name))
    }).await?
}

pub fn encode_body(bytes: &[u8]) -> String {
    STANDARD.encode(bytes)
}

pub fn decode_body(value: &str) -> anyhow::Result<Vec<u8>> {
    anyhow::ensure!(value.len() <= 14 * 1024 * 1024, "relay body exceeds encoded size limit");
    Ok(STANDARD.decode(value)?)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn relay_sessions_replace_stale_connections_and_scope_responses() {
        let hub = RelayHub::default();
        let device = Uuid::new_v4();
        let (first, _first_rx) = hub.connect(device);
        let (second, _second_rx) = hub.connect(device);
        hub.disconnect(device, first);
        assert!(hub.sender(device).is_some(), "stale session teardown must not remove a newer connection");
        let request_id = Uuid::new_v4();
        let response_rx = hub.register_pending(request_id, device).unwrap();
        assert!(hub.complete(device, RelayResponse {
            id: request_id, status: 200, headers: HashMap::new(), body_base64: String::new(), set_cookies: Vec::new()
        }));
        assert_eq!(response_rx.blocking_recv().unwrap().status, 200);
        let other_device = Uuid::new_v4();
        let request_id = Uuid::new_v4();
        let response_rx = hub.register_pending(request_id, device).unwrap();
        assert!(!hub.complete(other_device, RelayResponse {
            id: request_id, status: 200, headers: HashMap::new(), body_base64: String::new(), set_cookies: Vec::new()
        }));
        hub.cancel_pending(request_id);
        assert!(response_rx.blocking_recv().is_err());
        hub.disconnect(device, second);
        assert!(hub.sender(device).is_none());
    }

    #[test]
    fn parses_dns_txt_records_with_compressed_owner_names() {
        let mut packet = vec![
            0x12,0x34,0x81,0x80,0x00,0x01,0x00,0x01,0x00,0x00,0x00,0x00,
            0x01,b'a',0x07,b'e',b'x',b'a',b'm',b'p',b'l',b'e',0x00,0x00,0x10,0x00,0x01,
            0xc0,0x0c,0x00,0x10,0x00,0x01,0x00,0x00,0x00,0x3c,0x00,0x0a,0x09,
            b'p',b'o',b'c',b'k',b'e',b't',b'h',b'o',b's',
        ];
        packet[0] = 0x12;
        packet[1] = 0x34;
        assert_eq!(parse_dns_txt_answers(&packet, "a.example"), vec!["pockethos".to_string()]);
    }

    #[test]
    fn pending_relay_requests_are_bounded_and_revoked_with_device() {
        let hub = RelayHub::default();
        let device = Uuid::new_v4();
        let other = Uuid::new_v4();
        let mut receivers = Vec::new();
        for _ in 0..8 {
            receivers.push(hub.register_pending(Uuid::new_v4(), device).unwrap());
        }
        assert!(hub.register_pending(Uuid::new_v4(), device).is_none());
        for _ in 0..24 {
            receivers.push(hub.register_pending(Uuid::new_v4(), other).unwrap());
        }
        assert!(hub.register_pending(Uuid::new_v4(), other).is_none());
        assert_eq!(receivers.len(), 32);
        hub.revoke_device(device);
        assert!(receivers.drain(..8).all(|receiver| receiver.blocking_recv().is_err()));
        assert!(hub.register_pending(Uuid::new_v4(), device).is_some());
    }

    #[test]
    fn dns_txt_parser_rejects_wrong_question_and_truncated_response() {
        let mut packet = vec![
            0x12,0x34,0x81,0x80,0x00,0x01,0x00,0x01,0x00,0x00,0x00,0x00,
            0x01,b'a',0x07,b'e',b'x',b'a',b'm',b'p',b'l',b'e',0x00,0x00,0x10,0x00,0x01,
            0xc0,0x0c,0x00,0x10,0x00,0x01,0x00,0x00,0x00,0x3c,0x00,0x0a,0x09,
            b'p',b'o',b'c',b'k',b'e',b't',b'h',b'o',b's',
        ];
        assert!(parse_dns_txt_answers(&packet, "other.example").is_empty());
        packet[2] |= 0x02;
        assert!(parse_dns_txt_answers(&packet, "a.example").is_empty());
    }

    #[test]
    fn relay_body_codec_round_trips_binary() {
        let bytes = [0, 1, 2, 127, 128, 255];
        assert_eq!(decode_body(&encode_body(&bytes)).unwrap(), bytes);
    }
}
