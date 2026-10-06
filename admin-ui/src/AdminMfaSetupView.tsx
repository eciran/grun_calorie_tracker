import { FormEvent, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { formatRequestError, request } from "./api";
import "./admin-mfa-setup.css";
type Setup = { secret: string; otpauthUri: string };
type Verification = { enabled: boolean; recoveryCodes: string[] };

export function AdminMfaSetupView({ token, password, onExit }: {
  token: string; password: string; onExit: (complete: boolean) => void;
}) {
  const [enrollment, setEnrollment] = useState<Setup | null>(null);
  const [codes, setCodes] = useState<string[]>([]);
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const options = { method: "POST", auth: false, headers: { Authorization: `Bearer ${token}` } };
      if (!enrollment) {
        const result = await request<Setup>("/api/v1/auth/admin/mfa/enrollment",
          { ...options, body: { currentPassword: password } });
        if (!result.secret || !result.otpauthUri) throw new Error("MFA setup could not be started. Return to sign in and retry.");
        setEnrollment(result);
      } else {
        const result = await request<Verification>("/api/v1/auth/admin/mfa/enrollment/verify",
          { ...options, body: { code } });
        if (!result.enabled || !Array.isArray(result.recoveryCodes) || !result.recoveryCodes.length) {
          throw new Error("MFA verification could not be confirmed. Return to sign in to check your account.");
        }
        setCodes(result.recoveryCodes);
        setEnrollment(null);
        setCode("");
      }
    } catch (failure) { setError(formatRequestError(failure)); }
    finally { setBusy(false); }
  }

  return <main className="login-page admin-mfa-setup-page">
    <form className="login-card" onSubmit={submit}>
      <img src="/admin-ui/grun/grun-wordmark.svg" alt="GRUN" width={140} />
      <h2>{codes.length ? "Save your recovery codes" : "Secure your admin account"}</h2>
      {codes.length ? <>
        <div className="form-notice">MFA is enabled. Keep these single-use codes somewhere safe.</div>
        <ul className="admin-mfa-setup-recovery">{codes.map(value => <li key={value}><code>{value}</code></li>)}</ul>
        <label className="recovery-confirmation"><input type="checkbox" checked={saved} onChange={event => setSaved(event.target.checked)} /> I saved my recovery codes</label>
        <button className="primary-button" type="button" disabled={!saved} onClick={() => onExit(true)}>Continue to sign in</button>
      </> : <>
        {!enrollment ? <p>MFA setup is required before accessing the admin workspace.</p> : <>
          <div className="admin-mfa-setup-qr"><QRCodeSVG value={enrollment.otpauthUri} size={184} role="img" aria-label="Authenticator enrollment QR code" /></div>
          <label>Setup key<input readOnly value={enrollment.secret} aria-label="Authenticator setup key" /></label>
          <label>Authenticator code<input autoFocus inputMode="numeric" autoComplete="one-time-code" maxLength={6}
            value={code} onChange={event => setCode(event.target.value.replace(/\D/g, "").slice(0, 6))} /></label>
        </>}
        {error && <div className="form-error" role="alert">{error}</div>}
        <button className="primary-button" disabled={busy || Boolean(enrollment && code.length !== 6)} type="submit">
          {busy ? "Working..." : enrollment ? "Verify and enable MFA" : "Set up authenticator"}
        </button>
        <button className="text-button" type="button" disabled={busy} onClick={() => onExit(false)}>Back to sign in</button>
      </>}
    </form>
  </main>;
}
