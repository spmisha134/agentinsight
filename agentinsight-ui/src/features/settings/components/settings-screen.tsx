import React from 'react';

import { Badge, Panel } from '../../../components/ui';
import {
  ProviderDescriptor,
  ProviderHealthStatus,
  ProviderInstance,
  ProviderType,
  setActiveProvider,
  validateProvider,
} from '../../providers/api';

type SettingsScreenProps = {
  activeProvider: ProviderInstance;
  providers: ProviderDescriptor[];
  onProviderChanged: () => void;
};

export function SettingsScreen({ activeProvider, providers, onProviderChanged }: SettingsScreenProps) {
  const [providerType, setProviderType] = React.useState<ProviderType>(activeProvider.providerType);
  const [displayName, setDisplayName] = React.useState(activeProvider.displayName);
  const [homePath, setHomePath] = React.useState(activeProvider.homePath);
  const [healthStatus, setHealthStatus] = React.useState(activeProvider.healthStatus);
  const [sessionsFound, setSessionsFound] = React.useState<number | null>(null);
  const [providerError, setProviderError] = React.useState<string | null>(null);
  const [saving, setSaving] = React.useState(false);
  const selectedProvider = providers.find((provider) => provider.providerType === providerType);
  const canSwitchProvider = selectedProvider?.supportStatus === 'SUPPORTED';
  const healthTone = healthStatus === 'READY' ? 'success' : healthStatus === 'PARTIAL' ? 'warning' : healthStatus === 'UNSUPPORTED' ? 'danger' : 'danger';

  const selectProvider = (provider: ProviderDescriptor) => {
    setProviderType(provider.providerType);
    setDisplayName(provider.displayName);
    setHomePath(provider.providerType === activeProvider.providerType ? activeProvider.homePath : provider.defaultHomePath);
    setHealthStatus(provider.supportStatus === 'SUPPORTED' ? activeProvider.healthStatus : 'UNSUPPORTED');
    setSessionsFound(null);
    setProviderError(provider.supportStatus === 'SUPPORTED' ? null : `${provider.displayName} is ${provider.supportStatus.toLowerCase()} and cannot be activated yet.`);
  };

  const sections = [
    {
      title: 'Source',
      description: 'Codex data is read locally. AgentInsight never writes to Codex-owned files.',
      rows: [
        ['Active provider', activeProvider.displayName],
        ['Home path', activeProvider.homePath],
        ['Provider health', activeProvider.healthStatus],
      ],
    },
    {
      title: 'Import',
      description: 'Import preferences control how local sessions and rollout events are processed.',
      rows: [
        ['Incremental import', 'Enabled'],
        ['Tool call import', 'Enabled'],
        ['Token event import', 'Enabled'],
        ['Raw rollout retention', 'Disabled by default'],
      ],
    },
    {
      title: 'Pricing',
      description: 'Cost estimates use configurable pricing profiles and token events.',
      rows: [
        ['Active pricing profile', 'Default'],
        ['Currency', 'USD'],
        ['Unknown model behavior', 'Use fallback handling'],
      ],
    },
    {
      title: 'Search & Display',
      description: 'Frontend defaults for analytics density, search scope, and time display.',
      rows: [
        ['Search indexing', 'Enabled'],
        ['Default session page size', '12'],
        ['Timezone display', 'Local'],
      ],
    },
    {
      title: 'Privacy',
      description: 'Local-first defaults keep telemetry and external access disabled.',
      rows: [
        ['Telemetry', 'Disabled'],
        ['External links', 'Disabled by default'],
        ['Local file writes', 'Lens database only'],
      ],
    },
  ];

  return (
    <div className="settings-grid">
      <Panel title="Agent Provider" description="Provider configuration controls which local assistant data source analytics read from.">
        <div className="settings-provider-grid">
          {providers.map((provider) => {
            const isSelected = providerType === provider.providerType;
            const isActive = activeProvider.providerType === provider.providerType;
            return (
              <button
                className={`provider-card settings-provider-card ${isSelected ? 'active' : ''}`}
                key={provider.providerType}
                onClick={() => selectProvider(provider)}
                type="button"
              >
                <span className="provider-card-title">{provider.displayName}</span>
                <span className="provider-card-badges">
                  <Badge tone={provider.supportStatus === 'SUPPORTED' ? 'success' : 'warning'}>{provider.supportStatus}</Badge>
                  {isActive && <Badge tone="success">Active</Badge>}
                </span>
                <span className="provider-card-path">{isActive ? activeProvider.homePath : provider.defaultHomePath || 'Manual path'}</span>
              </button>
            );
          })}
        </div>
        <div className="provider-form settings-provider-form">
          <label>
            <span>Provider type</span>
            <select className="select-control" value={providerType} onChange={(event) => setProviderType(event.target.value as ProviderType)}>
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
          <Badge tone={healthTone}>{healthStatus}</Badge>
          {sessionsFound !== null && <span>{sessionsFound} sessions found</span>}
          {providerError && <span>{providerError}</span>}
        </div>
        <div className="provider-actions">
          <button
            className="secondary-button"
            disabled={saving}
            onClick={() => {
              if (!canSwitchProvider) {
                setHealthStatus('UNSUPPORTED' as ProviderHealthStatus);
                setProviderError(`${selectedProvider?.displayName ?? 'This provider'} cannot be validated until it is supported.`);
                return;
              }
              setSaving(true);
              setProviderError(null);
              validateProvider({ providerType, displayName, homePath })
                .then((health) => {
                  setHealthStatus(health.status);
                  setSessionsFound(health.sessionsFound);
                })
                .catch((err) => setProviderError(err.message))
                .finally(() => setSaving(false));
            }}
            type="button"
          >
            Revalidate
          </button>
          <button
            className="refresh-button"
            disabled={saving || !canSwitchProvider}
            onClick={() => {
              setSaving(true);
              setProviderError(null);
              setActiveProvider({ providerType, displayName, homePath })
                .then(onProviderChanged)
                .catch((err) => setProviderError(err.message))
                .finally(() => setSaving(false));
            }}
            type="button"
          >
            Switch Provider
          </button>
        </div>
      </Panel>
      {sections.map((section) => (
        <Panel title={section.title} description={section.description} key={section.title}>
          <div className="settings-list">
            {section.rows.map(([label, value]) => (
              <div className="settings-row" key={label}>
                <span>{label}</span>
                <Badge>{value}</Badge>
              </div>
            ))}
          </div>
        </Panel>
      ))}
      <Panel title="Maintenance" description="Safe operations from the settings specification. Backend action wiring can be added when the settings API slice is implemented.">
        <div className="maintenance-actions">
          <button className="secondary-button" type="button" disabled>Run Import</button>
          <button className="secondary-button" type="button" disabled>Reindex Search</button>
          <button className="secondary-button" type="button" disabled>Recalculate Costs</button>
          <button className="secondary-button danger" type="button" disabled>Reset Settings</button>
        </div>
      </Panel>
    </div>
  );
}
