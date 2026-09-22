import type { CSSProperties } from "react";

type Locale = "tr" | "en";

export function UserVerificationChart({ verified, unverified, locale }: { verified: number; unverified: number; locale: Locale }) {
  const total = verified + unverified;
  const style = { "--verified-angle": `${total ? Math.round(verified / total * 360) : 0}deg` } as CSSProperties;
  return <div className="users-donut-chart" role="img" aria-label={locale === "tr" ? "E-posta doğrulama dağılımı" : "Email verification distribution"}>
    <div className="users-donut" style={style}><span><strong>{total.toLocaleString()}</strong><small>{locale === "tr" ? "hesap" : "accounts"}</small></span></div>
    <div className="users-chart-legend"><span><i className="verified" />{locale === "tr" ? "Doğrulandı" : "Verified"} <b>{verified}</b></span><span><i />{locale === "tr" ? "Bekliyor" : "Pending"} <b>{unverified}</b></span></div>
  </div>;
}

export function UserActivityChart({ active, inactive, never, locale }: { active: number; inactive: number; never: number; locale: Locale }) {
  const maximum = Math.max(1, active, inactive, never);
  const rows: Array<[string, number]> = locale === "tr" ? [["Son 30 gün aktif", active], ["30+ gün pasif", inactive], ["Hiç aktif olmadı", never]] : [["Active in 30d", active], ["Inactive 30d+", inactive], ["Never active", never]];
  return <div className="users-bar-chart" role="img" aria-label={locale === "tr" ? "Kullanıcı etkinlik dağılımı" : "User activity distribution"}>{rows.map(([label, value], index) => <div key={label}><span>{label}</span><i><b className={`tone-${index}`} style={{ width: `${value / maximum * 100}%` }} /></i><strong>{value}</strong></div>)}</div>;
}
