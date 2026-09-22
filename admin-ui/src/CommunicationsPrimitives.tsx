import { ReactNode } from "react";

export function CommunicationHero({ eyebrow, title, description, status, actions }: { eyebrow: string; title: string; description: string; status?: ReactNode; actions?: ReactNode }) {
  return <section className="communication-command-hero">
    <div className="communication-command-copy"><p className="eyebrow">{eyebrow}</p><h2>{title}</h2><p>{description}</p></div>
    {(status || actions) && <div className="communication-command-side">{status}{actions && <div className="communication-command-actions">{actions}</div>}</div>}
  </section>;
}

export function CommunicationBars({ items, empty = "No operational data yet." }: { items: Array<{ label: string; value: number; tone?: "primary" | "accent" | "danger" }>; empty?: string }) {
  const max = Math.max(0, ...items.map((item) => item.value));
  if (!items.length) return <div className="communication-bars-empty">{empty}</div>;
  return <div className="communication-bars" role="img" aria-label={items.map((item) => `${item.label}: ${item.value}`).join(", ")}>
    {items.map((item) => <div className="communication-bar" key={item.label}>
      <span>{item.label}</span><div className="communication-bar-track"><i className={item.tone ?? "primary"} style={{ width: `${max ? Math.max(3, item.value / max * 100) : 0}%` }} /></div><strong>{item.value.toLocaleString()}</strong>
    </div>)}
  </div>;
}
