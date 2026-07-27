import React from 'react';

export const KpiCardAdmin = ({ title, value, subtext, colorClass, icon, imgSrc }) => (
  <div className="kpi-card">
    <div className={`kpi-icon ${colorClass}`}>
      {imgSrc ? (
        <img src={imgSrc} alt={title} style={{ width: 26, height: 26, objectFit: 'contain' }} />
      ) : (
        icon
      )}
    </div>
    <div className="kpi-content">
      <span className="kpi-title">{title}</span>
      <span className={`kpi-value ${colorClass === 'teal' ? 'teal' : 'orange'}`}>{value}</span>
      <span className="kpi-subtext">
        <span className="arrow">↑</span> {subtext}
      </span>
    </div>
  </div>
);
