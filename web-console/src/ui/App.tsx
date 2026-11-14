const cards = [
  { label: "Websites", value: "0", detail: "No sites registered" },
  { label: "Tunnel", value: "Not connected", detail: "Android connector validation pending" },
  { label: "Requests", value: "—", detail: "Metrics API not connected" },
];
export function App() {
  return <main className="shell">
    <header><span className="eyebrow">DESIGNED BY ASSAYFLUX</span><h1>PocketHost Console</h1><p>Your website. Your device. Your control.</p></header>
    <section className="notice"><strong>Interface preview</strong><span>This TypeScript console scaffold is not connected to a PocketHost device or management API.</span></section>
    <section className="grid">{cards.map(card => <article className="tile" key={card.label}><span>{card.label}</span><strong>{card.value}</strong><small>{card.detail}</small></article>)}</section>
    <footer>Rust engine · Kotlin Android · TypeScript console</footer>
  </main>;
}
