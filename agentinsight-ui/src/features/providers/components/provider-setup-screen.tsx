import { CheckCircle2, CircleAlert, Search } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

import { Badge, LoadingShell, Panel } from '../../../components/ui';
import {
  discoverProviders,
  ProviderDescriptor,
  ProviderDiscoveryResult,
  ProviderHealth,
  ProviderType,
  setActiveProvider,
  validateProvider,
} from '../api';

type ProviderSetupScreenProps = {
  providers: ProviderDescriptor[];
  onActivated: () => void;
};

export function ProviderSetupScreen({ providers, onActivated }: ProviderSetupScreenProps) {
  const [discoveries, setDiscoveries] = useState<ProviderDiscoveryResult[]>([]);
  const [selectedType, setSelectedType] = useState<ProviderType>('CODEX');
  const selectedDescriptor = providers.find((provider) => provider.providerType === selectedType) ?? providers[0];
  const selectedDiscovery = discoveries.find((discovery) => discovery.providerType === selectedType);
  const [displayName, setDisplayName] = useState('Codex');
  const [homePath, setHomePath] = useState('');
  const [health, setHealth] = useState<ProviderHealth | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    discoverProviders()
      .then((results) => {
        setDiscoveries(results);
        const detectedCodex = results.find((result) => result.providerType === 'CODEX');
        if (detectedCodex) {
          setHomePath(detectedCodex.homePath);
          setHealth(detectedCodex.health);
        }
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    if (!selectedDescriptor) {
      return;
    }
    setDisplayName(selectedDescriptor.displayName);
    setHomePath(selectedDiscovery?.homePath || selectedDescriptor.defaultHomePath);
    setHealth(selectedDiscovery?.health ?? null);
  }, [selectedType, selectedDescriptor, selectedDiscovery]);

  const canContinue = selectedDescriptor?.supportStatus === 'SUPPORTED' && health && (health.status === 'READY' || health.status === 'PARTIAL');

  const statusTone = useMemo<'default' | 'success' | 'warning' | 'danger'>(() => {
    if (!health) {
      return 'default';
    }
    if (health.status === 'READY') {
      return 'success';
    }
    if (health.status === 'PARTIAL') {
      return 'warning';
    }
    return 'danger';
  }, [health]);

  const validate = () => {
    if (!selectedDescriptor) {
      return;
    }
    setSaving(true);
    setError(null);
    validateProvider({ providerType: selectedDescriptor.providerType, displayName, homePath })
      .then(setHealth)
      .catch((err) => setError(err.message))
      .finally(() => setSaving(false));
  };

  const continueWithProvider = () => {
    if (!selectedDescriptor) {
      return;
    }
    setSaving(true);
    setError(null);
    setActiveProvider({ providerType: selectedDescriptor.providerType, displayName, homePath })
      .then(onActivated)
      .catch((err) => setError(err.message))
      .finally(() => setSaving(false));
  };

  if (loading) {
    return (
      <main className="standalone-state">
        <LoadingShell />
      </main>
    );
  }

  return (
    <main className="provider-setup">
      <section className="provider-setup-header">
        <p className="eyebrow">AgentInsight</p>
        <h1>Choose Your Agent Provider</h1>
      </section>

      {error && <div className="error">{error}</div>}

      <section className="provider-grid">
        {providers.map((provider) => {
          const discovery = discoveries.find((item) => item.providerType === provider.providerType);
          const isSelected = selectedType === provider.providerType;
          return (
            <button
              className={`provider-card ${isSelected ? 'active' : ''}`}
              aria-disabled={provider.supportStatus !== 'SUPPORTED'}
              key={provider.providerType}
              onClick={() => setSelectedType(provider.providerType)}
              type="button"
            >
              <span className="provider-card-title">{provider.displayName}</span>
              <span className="provider-card-badges">
                <Badge tone={provider.supportStatus === 'SUPPORTED' ? 'success' : 'warning'}>{provider.supportStatus}</Badge>
                <Badge>{discovery?.detected ? 'Detected' : 'Not detected'}</Badge>
              </span>
              <span className="provider-card-path">{discovery?.homePath || provider.defaultHomePath || 'Manual path'}</span>
              <span className="provider-card-meta">{discovery?.health.sessionsFound ?? 0} sessions found</span>
            </button>
          );
        })}
      </section>

      <Panel title="Provider Configuration" description="Validate the provider home path before activating it.">
        <div className="provider-form">
          <label>
            <span>Provider type</span>
            <select className="select-control" value={selectedType} onChange={(event) => setSelectedType(event.target.value as ProviderType)}>
              {providers.map((provider) => (
                <option key={provider.providerType} value={provider.providerType}>
                  {provider.displayName}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span>Display name</span>
            <input value={displayName} onChange={(event) => setDisplayName(event.target.value)} />
          </label>
          <label>
            <span>Home path</span>
            <input value={homePath} onChange={(event) => setHomePath(event.target.value)} />
          </label>
        </div>

        <div className="provider-health-row">
          <Badge tone={statusTone}>{health?.status ?? 'Not validated'}</Badge>
          <span>{health?.sessionsFound ?? 0} sessions found</span>
          {health?.warnings.map((warning) => <span key={warning}><CircleAlert size={14} /> {warning}</span>)}
          {health?.errors.map((item) => <span key={item}><CircleAlert size={14} /> {item}</span>)}
        </div>

        <div className="provider-actions">
          <button className="secondary-button" disabled={saving} onClick={validate} type="button">
            <Search size={16} /> Validate
          </button>
          <button className="refresh-button" disabled={!canContinue || saving} onClick={continueWithProvider} type="button">
            <CheckCircle2 size={16} /> Continue
          </button>
        </div>
      </Panel>
    </main>
  );
}
