import React, { useEffect, useMemo, useState } from "react";

const API_BASE = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";
const OIDC_AUTH_URL = import.meta.env.VITE_OIDC_AUTH_URL || "";
const OIDC_CLIENT_ID = import.meta.env.VITE_OIDC_CLIENT_ID || "";
const OIDC_REDIRECT_URI = import.meta.env.VITE_OIDC_REDIRECT_URI || "";
const OIDC_SCOPE = import.meta.env.VITE_OIDC_SCOPE || "openid email profile";

const DEFAULT_FLAGS = [
  {
    key: "gateway_inventory",
    label: "Gateway Inventory UI",
    description: "Expose live gateway inventory widgets."
  },
  {
    key: "tenant_sites",
    label: "Tenant + Site Navigation",
    description: "Enable tenant and site navigation for multi-tenant ops."
  },
  {
    key: "evidence_exports",
    label: "Evidence Exports",
    description: "Allow admins to export evidence packages."
  }
];

const DEFAULT_TENANTS = [{ id: "tenant-local", name: "Local Tenant" }];
const DEFAULT_SITES = [{ id: "tenant-local-default", tenantId: "tenant-local", name: "Default Site" }];

const randomId = () => {
  if (globalThis.crypto?.randomUUID) {
    return crypto.randomUUID();
  }
  return `legacy-${Math.random().toString(16).slice(2)}`;
};

const buildOidcUrl = () => {
  if (!OIDC_AUTH_URL || !OIDC_CLIENT_ID || !OIDC_REDIRECT_URI) {
    return "";
  }
  const params = new URLSearchParams({
    response_type: "code",
    client_id: OIDC_CLIENT_ID,
    redirect_uri: OIDC_REDIRECT_URI,
    scope: OIDC_SCOPE,
    state: randomId()
  });
  return `${OIDC_AUTH_URL}?${params.toString()}`;
};

const decodeJwtPayload = (token) => {
  const parts = token.split(".");
  if (parts.length < 2) {
    return null;
  }
  try {
    const normalized = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    const padding = "=".repeat((4 - (normalized.length % 4)) % 4);
    const payload = JSON.parse(atob(`${normalized}${padding}`));
    return payload;
  } catch (error) {
    return null;
  }
};

const loadSession = () => {
  try {
    const raw = localStorage.getItem("adminWebSession");
    return raw ? JSON.parse(raw) : null;
  } catch (error) {
    return null;
  }
};

const saveSession = (session) => {
  localStorage.setItem("adminWebSession", JSON.stringify(session));
};

const clearSession = () => {
  localStorage.removeItem("adminWebSession");
};

const loadFlags = (tenantId) => {
  try {
    const raw = localStorage.getItem(`adminWebFlags:${tenantId}`);
    if (!raw) {
      return DEFAULT_FLAGS.reduce((acc, flag) => {
        acc[flag.key] = false;
        return acc;
      }, {});
    }
    return JSON.parse(raw);
  } catch (error) {
    return DEFAULT_FLAGS.reduce((acc, flag) => {
      acc[flag.key] = false;
      return acc;
    }, {});
  }
};

const saveFlags = (tenantId, flags) => {
  localStorage.setItem(`adminWebFlags:${tenantId}`, JSON.stringify(flags));
};

export default function App() {
  const [session, setSession] = useState(loadSession);
  const [tenantId, setTenantId] = useState(session?.tenantId || "tenant-local");
  const [correlationId, setCorrelationId] = useState(`corr-${randomId()}`);
  const [activeTab, setActiveTab] = useState("gateways");

  const [loginState, setLoginState] = useState({
    username: "",
    password: "",
    token: "",
    roleOverride: "",
    tenantOverride: ""
  });

  const [gatewayState, setGatewayState] = useState({
    status: "idle",
    items: [],
    error: ""
  });

  const [tenantState, setTenantState] = useState({
    status: "idle",
    tenants: DEFAULT_TENANTS,
    sites: DEFAULT_SITES,
    source: "stub",
    error: ""
  });

  const [flags, setFlags] = useState(loadFlags(tenantId));

  const role = session?.role || "";
  const isAdmin = role === "ADMIN" || role === "FOUNDER";

  const oidcUrl = useMemo(buildOidcUrl, []);

  useEffect(() => {
    setFlags(loadFlags(tenantId));
  }, [tenantId]);

  const updateFlags = (nextFlags) => {
    setFlags(nextFlags);
    saveFlags(tenantId, nextFlags);
  };

  const apiHeaders = () => {
    if (!session?.token) {
      return {};
    }
    return {
      Authorization: `Bearer ${session.token}`,
      "X-Correlation-Id": correlationId
    };
  };

  const fetchJson = async (path) => {
    const response = await fetch(`${API_BASE}${path}`, {
      headers: apiHeaders()
    });
    if (!response.ok) {
      const message = await response.text();
      throw new Error(message || `Request failed: ${response.status}`);
    }
    return response.json();
  };

  const loadGateways = async () => {
    setGatewayState({ status: "loading", items: [], error: "" });
    try {
      const data = await fetchJson(`/admin/web/gateways?tenant_id=${encodeURIComponent(tenantId)}`);
      setGatewayState({ status: "ready", items: data.gateways || [], error: "" });
    } catch (error) {
      setGatewayState({ status: "error", items: [], error: error.message });
    }
  };

  const exportGatewaysCsv = async () => {
    if (!session?.token) {
      return;
    }
    try {
      const response = await fetch(
        `${API_BASE}/admin/web/gateways/export?tenant_id=${encodeURIComponent(tenantId)}`,
        {
          headers: apiHeaders()
        }
      );
      if (!response.ok) {
        const message = await response.text();
        throw new Error(message || `Export failed: ${response.status}`);
      }
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `gateways-${tenantId}.csv`;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      alert(`Export failed: ${error.message}`);
    }
  };

  const loadTenantsAndSites = async () => {
    setTenantState((current) => ({ ...current, status: "loading", error: "" }));
    try {
      const tenantsData = await fetchJson(`/admin/tenants?tenant_id=${encodeURIComponent(tenantId)}`);
      const sitesData = await fetchJson(`/admin/sites?tenant_id=${encodeURIComponent(tenantId)}`);
      setTenantState({
        status: "ready",
        tenants: tenantsData.tenants || [],
        sites: sitesData.sites || [],
        source: "api",
        error: ""
      });
    } catch (error) {
      setTenantState({
        status: "ready",
        tenants: DEFAULT_TENANTS.map((tenant) => ({
          ...tenant,
          id: tenantId,
          name: `Tenant ${tenantId}`
        })),
        sites: DEFAULT_SITES.map((site) => ({
          ...site,
          id: `${tenantId}-default`,
          tenantId,
          name: `Default Site (${tenantId})`
        })),
        source: "stub",
        error: error.message
      });
    }
  };

  const handleDevLogin = async (event) => {
    event.preventDefault();
    const body = {
      username: loginState.username,
      password: loginState.password
    };
    try {
      const response = await fetch(`${API_BASE}/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
      });
      if (!response.ok) {
        const message = await response.text();
        throw new Error(message || "Login failed");
      }
      const data = await response.json();
      const nextSession = {
        token: data.accessToken,
        role: data.role?.toUpperCase() || "",
        username: data.username || loginState.username,
        tenantId: data.tenantId || tenantId
      };
      setSession(nextSession);
      setTenantId(nextSession.tenantId || tenantId);
      saveSession(nextSession);
    } catch (error) {
      alert(`Login failed: ${error.message}`);
    }
  };

  const handleTokenLogin = (event) => {
    event.preventDefault();
    if (!loginState.token.trim()) {
      alert("Paste a token to continue.");
      return;
    }
    const payload = decodeJwtPayload(loginState.token.trim()) || {};
    const roleClaim =
      loginState.roleOverride ||
      payload.role ||
      (Array.isArray(payload.roles) ? payload.roles[0] : payload.roles) ||
      "";
    const tenantClaim = loginState.tenantOverride || payload.tenantId || tenantId;
    const nextSession = {
      token: loginState.token.trim(),
      role: roleClaim ? roleClaim.toUpperCase() : "",
      username: payload.username || payload.sub || "oidc-user",
      tenantId: tenantClaim
    };
    setSession(nextSession);
    setTenantId(nextSession.tenantId || tenantId);
    saveSession(nextSession);
  };

  const handleLogout = () => {
    setSession(null);
    clearSession();
  };

  const refreshAll = async () => {
    await loadGateways();
    await loadTenantsAndSites();
  };

  const handleTenantChange = (value) => {
    setTenantId(value);
    updateFlags(loadFlags(value));
  };

  const handleToggleFlag = (key) => {
    if (!isAdmin) {
      return;
    }
    const nextFlags = { ...flags, [key]: !flags[key] };
    updateFlags(nextFlags);
  };

  return (
    <div className="page">
      <header className="hero">
        <div>
          <p className="eyebrow">NeoGenesis</p>
          <h1>Admin Web Console</h1>
          <p className="subtitle">
            Minimal operations cockpit for gateway inventory, tenant oversight, and feature controls.
          </p>
        </div>
        <div className="session-card">
          <div className="session-row">
            <span className="label">API Base</span>
            <span className="value mono">{API_BASE}</span>
          </div>
          <div className="session-row">
            <span className="label">User</span>
            <span className="value">{session?.username || "Not signed in"}</span>
          </div>
          <div className="session-row">
            <span className="label">Role</span>
            <span className="value">{role || "Unknown"}</span>
          </div>
          <div className="session-row">
            <span className="label">Tenant</span>
            <span className="value mono">{tenantId}</span>
          </div>
          <div className="session-actions">
            <button className="ghost" onClick={() => setCorrelationId(`corr-${randomId()}`)}>
              New Correlation ID
            </button>
            <button className="ghost" onClick={refreshAll} disabled={!session?.token}>
              Refresh Data
            </button>
            {session?.token ? (
              <button className="primary" onClick={handleLogout}>
                Sign Out
              </button>
            ) : null}
          </div>
        </div>
      </header>

      {!session?.token ? (
        <section className="panel">
          <div className="panel-grid">
            <div>
              <h2>Sign In</h2>
              <p>
                Use local credentials for development or paste an OIDC token if you already have one.
              </p>
              {oidcUrl ? (
                <a className="link" href={oidcUrl} rel="noreferrer">
                  Launch OIDC Login
                </a>
              ) : (
                <p className="hint">OIDC auth URL not configured. Use dev login or paste a token.</p>
              )}
            </div>
            <form className="form" onSubmit={handleDevLogin}>
              <h3>Dev Login</h3>
              <label>
                Username
                <input
                  type="text"
                  value={loginState.username}
                  onChange={(event) =>
                    setLoginState((current) => ({ ...current, username: event.target.value }))
                  }
                  placeholder="admin"
                />
              </label>
              <label>
                Password
                <input
                  type="password"
                  value={loginState.password}
                  onChange={(event) =>
                    setLoginState((current) => ({ ...current, password: event.target.value }))
                  }
                  placeholder="••••••••"
                />
              </label>
              <button className="primary" type="submit">
                Sign In
              </button>
              <p className="hint">Uses `POST /auth/login` and stores the token locally.</p>
            </form>
            <form className="form" onSubmit={handleTokenLogin}>
              <h3>OIDC Token</h3>
              <label>
                Access Token
                <textarea
                  rows="4"
                  value={loginState.token}
                  onChange={(event) =>
                    setLoginState((current) => ({ ...current, token: event.target.value }))
                  }
                  placeholder="Paste JWT access token"
                />
              </label>
              <label>
                Role Override
                <input
                  type="text"
                  value={loginState.roleOverride}
                  onChange={(event) =>
                    setLoginState((current) => ({ ...current, roleOverride: event.target.value }))
                  }
                  placeholder="ADMIN"
                />
              </label>
              <label>
                Tenant Override
                <input
                  type="text"
                  value={loginState.tenantOverride}
                  onChange={(event) =>
                    setLoginState((current) => ({ ...current, tenantOverride: event.target.value }))
                  }
                  placeholder="tenant-local"
                />
              </label>
              <button className="primary" type="submit">
                Use Token
              </button>
            </form>
          </div>
        </section>
      ) : null}

      <section className="panel">
        <div className="panel-head">
          <div className="tabs">
            <button
              className={activeTab === "gateways" ? "tab active" : "tab"}
              onClick={() => setActiveTab("gateways")}
            >
              Gateways
            </button>
            <button
              className={activeTab === "tenants" ? "tab active" : "tab"}
              onClick={() => setActiveTab("tenants")}
            >
              Tenants + Sites
            </button>
            <button
              className={activeTab === "flags" ? "tab active" : "tab"}
              onClick={() => setActiveTab("flags")}
            >
              Feature Flags
            </button>
          </div>
          <div className="filters">
            <label>
              Tenant ID
              <input
                type="text"
                value={tenantId}
                onChange={(event) => handleTenantChange(event.target.value)}
              />
            </label>
            <label>
              Correlation ID
              <input type="text" value={correlationId} onChange={(event) => setCorrelationId(event.target.value)} />
            </label>
          </div>
        </div>

        {activeTab === "gateways" ? (
          <div className="panel-body">
            <div className="panel-actions">
              <button className="primary" onClick={loadGateways} disabled={!session?.token}>
                Load Gateways
              </button>
              <button className="ghost" onClick={exportGatewaysCsv} disabled={!session?.token}>
                Export CSV
              </button>
            </div>
            {gatewayState.status === "error" ? (
              <p className="error">Failed to load gateways: {gatewayState.error}</p>
            ) : null}
            <div className="table">
              <div className="table-row table-header">
                <span>ID</span>
                <span>Name</span>
                <span>Tenant</span>
                <span>Created At</span>
              </div>
              {gatewayState.items.length === 0 ? (
                <div className="table-row empty">No gateways loaded.</div>
              ) : (
                gatewayState.items.map((gateway) => (
                  <div className="table-row" key={gateway.id}>
                    <span className="mono">{gateway.id}</span>
                    <span>{gateway.name}</span>
                    <span className="mono">{gateway.tenantId}</span>
                    <span className="mono">{gateway.createdAt}</span>
                  </div>
                ))
              )}
            </div>
          </div>
        ) : null}

        {activeTab === "tenants" ? (
          <div className="panel-body">
            <div className="panel-actions">
              <button className="primary" onClick={loadTenantsAndSites} disabled={!session?.token}>
                Load Tenants + Sites
              </button>
              <span className="hint">Source: {tenantState.source}</span>
            </div>
            {tenantState.error ? (
              <p className="error">API fallback used: {tenantState.error}</p>
            ) : null}
            <div className="split">
              <div>
                <h3>Tenants</h3>
                <div className="table">
                  <div className="table-row table-header">
                    <span>ID</span>
                    <span>Name</span>
                  </div>
                  {tenantState.tenants.map((tenant) => (
                    <div className="table-row" key={tenant.id}>
                      <span className="mono">{tenant.id}</span>
                      <span>{tenant.name}</span>
                    </div>
                  ))}
                </div>
              </div>
              <div>
                <h3>Sites</h3>
                <div className="table">
                  <div className="table-row table-header">
                    <span>ID</span>
                    <span>Name</span>
                    <span>Tenant</span>
                  </div>
                  {tenantState.sites.map((site) => (
                    <div className="table-row" key={site.id}>
                      <span className="mono">{site.id}</span>
                      <span>{site.name}</span>
                      <span className="mono">{site.tenantId}</span>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        ) : null}

        {activeTab === "flags" ? (
          <div className="panel-body">
            <div className="panel-actions">
              <span className="hint">
                {isAdmin
                  ? "Toggle feature flags per tenant. Stored locally for now."
                  : "Admin role required to modify flags."}
              </span>
            </div>
            <div className="flag-grid">
              {DEFAULT_FLAGS.map((flag) => (
                <button
                  key={flag.key}
                  className={flags[flag.key] ? "flag-card active" : "flag-card"}
                  onClick={() => handleToggleFlag(flag.key)}
                  disabled={!isAdmin}
                >
                  <div>
                    <h3>{flag.label}</h3>
                    <p>{flag.description}</p>
                  </div>
                  <span className="pill">{flags[flag.key] ? "Enabled" : "Disabled"}</span>
                </button>
              ))}
            </div>
          </div>
        ) : null}
      </section>

      <footer className="footer">
        <p>
          Admin Web UI requires `Authorization: Bearer` tokens, `tenant_id`, and `X-Correlation-Id` for admin endpoints.
        </p>
      </footer>
    </div>
  );
}
