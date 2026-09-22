import { Component, ReactNode } from "react";
import { useAdminLocale } from "./locale";

// Keep the shell available when a page render or lazy chunk fails. Reset on navigation.
export class PageBoundary extends Component<{ children: ReactNode; fallback: ReactNode }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  render() { return this.state.failed ? this.props.fallback : this.props.children; }
}
export function PageFailure() {
  const { t } = useAdminLocale();
  return <div className="async-state error-state" role="alert"><p>{t("failed")}</p><button className="ghost-button" type="button" onClick={() => window.location.reload()}>{t("reload")}</button></div>;
}
