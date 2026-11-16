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

    pub fn register_pending(&self, request_id: Uuid, device_id: Uuid) -> oneshot::Receiver<RelayResponse> {
        let (sender, receiver) = oneshot::channel();
        self.pending.lock().unwrap_or_else(|e| e.into_inner())
            .insert(request_id, PendingResponse { device_id, sender });
        receiver
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

fn skip_dns_name(packet: &[u8], offset: &mut usize) -> Option<()> {
    loop {
        let length = *packet.get(*offset)?;
        if length & 0xc0 == 0xc0 {
            packet.get(*offset + 1)?;
            *offset += 2;
            return Some(());
        }
        *offset += 1;
        if length == 0 { return Some(()); }
        if length & 0xc0 != 0 || length > 63 { return None; }
        let end = (*offset).checked_add(length as usize)?;
        packet.get(*offset..end)?;
        *offset = end;
    }
}

pub fn parse_dns_txt_answers(packet: &[u8]) -> Vec<String> {
    if packet.len() < 12 || packet[3] & 0x0f != 0 { return Vec::new(); }
    let questions = u16::from_be_bytes([packet[4], packet[5]]) as usize;
    let answers = u16::from_be_bytes([packet[6], packet[7]]) as usize;
    if questions > 8 || answers > 256 { return Vec::new(); }
    let mut offset = 12usize;
    for _ in 0..questions {
        if skip_dns_name(packet, &mut offset).is_none() || packet.get(offset..offset + 4).is_none() {
            return Vec::new();
        }
        offset += 4;
    }
    let mut values = Vec::new();
    for _ in 0..answers {
        if skip_dns_name(packet, &mut offset).is_none() || packet.get(offset..offset + 10).is_none() {
            break;
        }
        let record_type = u16::from_be_bytes([packet[offset], packet[offset + 1]]);
        let length = u16::from_be_bytes([packet[offset + 8], packet[offset + 9]]) as usize;
        offset += 10;
        let Some(data) = packet.get(offset..offset.saturating_add(length)) else { break };
        if record_type == 16 {
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
        let socket = UdpSocket::bind("0.0.0.0:0")?;
        socket.set_read_timeout(Some(Duration::from_secs(3)))?;
        socket.set_write_timeout(Some(Duration::from_secs(3)))?;
        socket.send_to(&query, resolver)?;
        let mut response = [0u8; 4096];
        let (length, _) = socket.recv_from(&mut response)?;
        anyhow::ensure!(length >= 12, "truncated DNS response");
        anyhow::ensure!(u16::from_be_bytes([response[0], response[1]]) == id, "DNS transaction ID mismatch");
        anyhow::ensure!(response[2] & 0x80 != 0, "DNS server did not return a response");
        Ok(parse_dns_txt_answers(&response[..length]))
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
        let response_rx = hub.register_pending(request_id, device);
        assert!(hub.complete(device, RelayResponse {
            id: request_id, status: 200, headers: HashMap::new(), body_base64: String::new()
        }));
        assert_eq!(response_rx.blocking_recv().unwrap().status, 200);
        let other_device = Uuid::new_v4();
        let request_id = Uuid::new_v4();
        let response_rx = hub.register_pending(request_id, device);
        assert!(!hub.complete(other_device, RelayResponse {
            id: request_id, status: 200, headers: HashMap::new(), body_base64: String::new()
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
            b'p',b'o',b'c',b'k',b'e',b't',b'h',b'o',b's',0x00,
        ];
        packet[0] = 0x12;
        packet[1] = 0x34;
        assert_eq!(parse_dns_txt_answers(&packet), vec!["pockethos".to_string()]);
    }

    #[test]
    fn relay_body_codec_round_trips_binary() {
        let bytes = [0, 1, 2, 127, 128, 255];
        assert_eq!(decode_body(&encode_body(&bytes)).unwrap(), bytes);
    }
}
