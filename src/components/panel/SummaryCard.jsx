function SummaryCard({ icon, title, value, detail, action, onAction, variant = 'default' }) {
  return (
    <article className={`summary-card summary-card--${variant}`}>
      <div className="summary-card__icon">{icon}</div>
      <div className="summary-card__content">
        <span className="summary-card__title">{title}</span>
        {detail && <span className="summary-card__detail">{detail}</span>}
        <strong className="summary-card__value">{value}</strong>
        <button type="button" onClick={onAction}>{action}</button>
      </div>
    </article>
  )
}

export default SummaryCard
