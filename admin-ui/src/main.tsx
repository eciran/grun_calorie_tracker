import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import { AdminLocaleProvider } from "./admin/locale";
import "./styles.css";

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <AdminLocaleProvider><App /></AdminLocaleProvider>
  </React.StrictMode>
);
