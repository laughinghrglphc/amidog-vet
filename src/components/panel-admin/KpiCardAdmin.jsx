import React from 'react';

export const KpiCardAdmin = ({ title, value, subtext, colorClass }) => (
  <div className="kpi-card">
    <span className="kpi-title">{title}</span>
    <span className={`kpi-value ${colorClass}`}>{value}</span>
    <span className="kpi-subtext">{subtext}</span>
  </div>
);