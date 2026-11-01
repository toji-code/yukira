'use client';

import React, { useState, useEffect } from 'react';
import {
  fetchInAppNotifications,
  markNotificationRead,
  fetchNotificationPreferences,
  updateNotificationPreferences,
  fetchWebhooks,
  registerWebhook,
  deleteWebhook,
  InAppNotificationDto,
  NotificationPreferenceDto,
  WebhookConfigDto,
} from '@/lib/api/monitoring';

interface NotificationCentreModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export function NotificationCentreModal({ isOpen, onClose }: NotificationCentreModalProps) {
  const [activeTab, setActiveTab] = useState<'NOTIFICATIONS' | 'PREFERENCES' | 'WEBHOOKS'>('NOTIFICATIONS');
  const [notifications, setNotifications] = useState<InAppNotificationDto[]>([]);
  const [preferences, setPreferences] = useState<NotificationPreferenceDto | null>(null);
  const [webhooks, setWebhooks] = useState<WebhookConfigDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [newWebhookUrl, setNewWebhookUrl] = useState('');

  useEffect(() => {
    if (isOpen) {
      loadData();
    }
  }, [isOpen]);

  async function loadData() {
    setLoading(true);
    try {
      const [notifs, prefs, hooks] = await Promise.all([
        fetchInAppNotifications().catch(() => []),
        fetchNotificationPreferences().catch(() => null),
        fetchWebhooks().catch(() => []),
      ]);
      setNotifications(notifs);
      setPreferences(prefs);
      setWebhooks(hooks);
    } finally {
      setLoading(false);
    }
  }

  async function handleMarkRead(id: number) {
    try {
      await markNotificationRead(id);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, isRead: true, readAt: new Date().toISOString() } : n))
      );
    } catch (e) {
      console.error(e);
    }
  }

  async function handleSavePreferences(e: React.FormEvent) {
    e.preventDefault();
    if (!preferences) return;
    try {
      const updated = await updateNotificationPreferences(preferences);
      setPreferences(updated);
    } catch (e) {
      console.error(e);
    }
  }

  async function handleAddWebhook(e: React.FormEvent) {
    e.preventDefault();
    if (!newWebhookUrl.trim()) return;
    try {
      const hook = await registerWebhook({ webhookUrl: newWebhookUrl.trim() });
      setWebhooks((prev) => [hook, ...prev]);
      setNewWebhookUrl('');
    } catch (e) {
      console.error(e);
    }
  }

  async function handleDeleteWebhook(id: number) {
    try {
      await deleteWebhook(id);
      setWebhooks((prev) => prev.filter((w) => w.id !== id));
    } catch (e) {
      console.error(e);
    }
  }

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4">
      <div className="relative w-full max-w-2xl bg-zinc-950 border border-zinc-800 rounded-xl shadow-2xl flex flex-col max-h-[85vh] overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-zinc-800 bg-zinc-900/40">
          <div>
            <h2 className="text-lg font-semibold text-zinc-100 flex items-center gap-2">
              <span className="w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse" />
              Investor Notification Centre
            </h2>
            <p className="text-xs text-zinc-400 mt-0.5">
              Outbound dispatch, in-app alerts, and HMAC-signed webhook delivery (Tasks 165–170).
            </p>
          </div>
          <button
            onClick={onClose}
            className="text-zinc-400 hover:text-zinc-200 p-1.5 rounded-lg hover:bg-zinc-800/60 transition-colors"
          >
            ✕
          </button>
        </div>

        {/* Navigation Tabs */}
        <div className="flex border-b border-zinc-800 bg-zinc-900/20 px-6">
          <button
            onClick={() => setActiveTab('NOTIFICATIONS')}
            className={`py-3 px-4 text-xs font-medium border-b-2 transition-colors ${
              activeTab === 'NOTIFICATIONS'
                ? 'border-emerald-500 text-emerald-400'
                : 'border-transparent text-zinc-400 hover:text-zinc-200'
            }`}
          >
            In-App Alerts ({notifications.filter((n) => !n.isRead).length})
          </button>
          <button
            onClick={() => setActiveTab('PREFERENCES')}
            className={`py-3 px-4 text-xs font-medium border-b-2 transition-colors ${
              activeTab === 'PREFERENCES'
                ? 'border-emerald-500 text-emerald-400'
                : 'border-transparent text-zinc-400 hover:text-zinc-200'
            }`}
          >
            Channels & Severity
          </button>
          <button
            onClick={() => setActiveTab('WEBHOOKS')}
            className={`py-3 px-4 text-xs font-medium border-b-2 transition-colors ${
              activeTab === 'WEBHOOKS'
                ? 'border-emerald-500 text-emerald-400'
                : 'border-transparent text-zinc-400 hover:text-zinc-200'
            }`}
          >
            Outbound Webhooks ({webhooks.length})
          </button>
        </div>

        {/* Content Body */}
        <div className="flex-1 overflow-y-auto p-6 space-y-4">
          {loading ? (
            <div className="py-12 text-center text-xs text-zinc-500">Loading notification telemetry...</div>
          ) : activeTab === 'NOTIFICATIONS' ? (
            notifications.length === 0 ? (
              <div className="py-12 text-center text-xs text-zinc-500">
                No active notifications. Scheduled monitoring sweeps evaluate conditions on configured cadences.
              </div>
            ) : (
              <div className="space-y-3">
                {notifications.map((n) => (
                  <div
                    key={n.id}
                    className={`p-4 rounded-lg border transition-all ${
                      n.isRead
                        ? 'bg-zinc-900/20 border-zinc-800/60 opacity-70'
                        : 'bg-zinc-900/60 border-zinc-700/80 shadow-sm'
                    }`}
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <div className="flex items-center gap-2">
                          <span
                            className={`px-1.5 py-0.5 rounded text-[10px] font-semibold ${
                              n.severity === 'HIGH'
                                ? 'bg-rose-500/20 text-rose-400 border border-rose-500/30'
                                : n.severity === 'MEDIUM'
                                ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                                : 'bg-sky-500/20 text-sky-400 border border-sky-500/30'
                            }`}
                          >
                            {n.severity}
                          </span>
                          <span className="text-xs font-medium text-zinc-200">{n.title}</span>
                        </div>
                        <p className="text-xs text-zinc-400 mt-1">{n.summary}</p>
                        <span className="text-[10px] text-zinc-500 mt-2 block">
                          {new Date(n.createdAt).toLocaleString()}
                        </span>
                      </div>
                      {!n.isRead && (
                        <button
                          onClick={() => handleMarkRead(n.id)}
                          className="text-[11px] px-2 py-1 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded transition-colors whitespace-nowrap"
                        >
                          Mark Read
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )
          ) : activeTab === 'PREFERENCES' ? (
            preferences ? (
              <form onSubmit={handleSavePreferences} className="space-y-4">
                <div className="space-y-3 bg-zinc-900/30 p-4 rounded-lg border border-zinc-800">
                  <h4 className="text-xs font-semibold text-zinc-200 uppercase tracking-wider">Active Channels</h4>
                  <label className="flex items-center gap-3 text-xs text-zinc-300 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={preferences.inAppEnabled}
                      onChange={(e) => setPreferences({ ...preferences, inAppEnabled: e.target.checked })}
                      className="rounded border-zinc-700 bg-zinc-800 text-emerald-500"
                    />
                    In-App Notification Drawer
                  </label>
                  <label className="flex items-center gap-3 text-xs text-zinc-300 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={preferences.emailEnabled}
                      onChange={(e) => setPreferences({ ...preferences, emailEnabled: e.target.checked })}
                      className="rounded border-zinc-700 bg-zinc-800 text-emerald-500"
                    />
                    Email Delivery (Configured via SMTP)
                  </label>
                  {preferences.emailEnabled && (
                    <input
                      type="email"
                      value={preferences.emailAddress || ''}
                      onChange={(e) => setPreferences({ ...preferences, emailAddress: e.target.value })}
                      placeholder="investor@example.com"
                      className="w-full px-3 py-1.5 text-xs bg-zinc-950 border border-zinc-800 rounded text-zinc-200 focus:outline-none focus:border-emerald-500"
                    />
                  )}
                  <label className="flex items-center gap-3 text-xs text-zinc-300 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={preferences.webhookEnabled}
                      onChange={(e) => setPreferences({ ...preferences, webhookEnabled: e.target.checked })}
                      className="rounded border-zinc-700 bg-zinc-800 text-emerald-500"
                    />
                    Outbound Webhook Triggering
                  </label>
                </div>

                <div className="space-y-3 bg-zinc-900/30 p-4 rounded-lg border border-zinc-800">
                  <h4 className="text-xs font-semibold text-zinc-200 uppercase tracking-wider">Minimum Severity</h4>
                  <select
                    value={preferences.minSeverity}
                    onChange={(e) => setPreferences({ ...preferences, minSeverity: e.target.value })}
                    className="w-full px-3 py-1.5 text-xs bg-zinc-950 border border-zinc-800 rounded text-zinc-200 focus:outline-none focus:border-emerald-500"
                  >
                    <option value="INFO">INFO (All events)</option>
                    <option value="LOW">LOW and above</option>
                    <option value="MEDIUM">MEDIUM and above (Recommended)</option>
                    <option value="HIGH">HIGH only</option>
                  </select>
                </div>

                <button
                  type="submit"
                  className="w-full py-2 bg-emerald-600 hover:bg-emerald-500 text-white rounded text-xs font-medium transition-colors"
                >
                  Save Notification Preferences
                </button>
              </form>
            ) : (
              <div className="text-xs text-zinc-500">Preferences unavailable.</div>
            )
          ) : (
            <div className="space-y-4">
              <form onSubmit={handleAddWebhook} className="flex gap-2">
                <input
                  type="url"
                  value={newWebhookUrl}
                  onChange={(e) => setNewWebhookUrl(e.target.value)}
                  placeholder="https://api.yourdomain.com/webhook/yukira"
                  required
                  className="flex-1 px-3 py-1.5 text-xs bg-zinc-950 border border-zinc-800 rounded text-zinc-200 focus:outline-none focus:border-emerald-500"
                />
                <button
                  type="submit"
                  className="px-4 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium rounded transition-colors whitespace-nowrap"
                >
                  Register Webhook
                </button>
              </form>

              <div className="space-y-2">
                {webhooks.length === 0 ? (
                  <div className="text-xs text-zinc-500 text-center py-6">
                    No webhook endpoints registered. Outbound payloads are HMAC-SHA256 signed.
                  </div>
                ) : (
                  webhooks.map((w) => (
                    <div
                      key={w.id}
                      className="p-3 bg-zinc-900/40 border border-zinc-800 rounded flex items-center justify-between text-xs"
                    >
                      <div className="truncate mr-3">
                        <div className="font-mono text-zinc-200 text-[11px] truncate">{w.webhookUrl}</div>
                        <div className="text-[10px] text-zinc-500 flex items-center gap-2 mt-0.5">
                          <span
                            className={`inline-block w-1.5 h-1.5 rounded-full ${
                              w.status === 'ACTIVE' ? 'bg-emerald-400' : 'bg-rose-400'
                            }`}
                          />
                          Status: {w.status} | Failures: {w.failureCount}
                        </div>
                      </div>
                      <button
                        onClick={() => handleDeleteWebhook(w.id)}
                        className="text-rose-400 hover:text-rose-300 text-[11px] px-2 py-1 rounded bg-rose-950/30 border border-rose-800/40"
                      >
                        Remove
                      </button>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

