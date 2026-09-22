import { useState, type ReactNode } from "react";
import { CollapsiblePanel } from "./AdminPrimitives";

export function AiOverviewPanel({ title, description, className, defaultOpen = false, children }: {
  title: string;
  description?: string;
  className?: string;
  defaultOpen?: boolean;
  children: ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);
  return <CollapsiblePanel title={title} description={description} className={`ai-overview-disclosure ${className ?? ""}`} open={open} onToggle={() => setOpen(value => !value)}>{children}</CollapsiblePanel>;
}
