import Icon from '../ui/Icon'

const toneColors = ['#ff7a1a', '#17b5a4', '#ffd34f', '#d9dee5']
const toneNames = ['orange', 'teal', 'yellow', 'gray']

export default function ServicesCard({ onViewAll, services }) {
  const total = services.reduce((sum, service) => sum + service.count, 0)
  const gradientStops = services.map((service, index) => {
    const start = services
      .slice(0, index)
      .reduce((sum, previous) => sum + previous.percentage, 0)
    const end = Math.min(100, start + service.percentage)
    return `${toneColors[index % toneColors.length]} ${start}% ${end}%`
  })
  const gradient = gradientStops.length
    ? `conic-gradient(${gradientStops.join(', ')})`
    : '#edf0f3'

  return (
    <article className="admin-card admin-services">
      <header className="admin-card__header"><h3>Servicios más solicitados</h3></header>
      <div className="admin-services__content">
        <div
          className="admin-donut"
          role="img"
          aria-label={`${total} reservas totales`}
          style={{ background: gradient }}
        >
          <span><small>Total<br />reservas</small><strong>{total}</strong></span>
        </div>
        <div className="admin-services__legend">
          {services.map((service, index) => (
            <span key={service.name}>
              <i className={`is-${toneNames[index % toneNames.length]}`} />
              <strong>{service.name}</strong>
              <small>{service.percentage}% ({service.count})</small>
            </span>
          ))}
          {!services.length && <small>Sin actividad registrada.</small>}
        </div>
      </div>
      <button type="button" className="admin-link-button" onClick={onViewAll}>
        Ver desglose <Icon name="chevronRight" size={14} />
      </button>
    </article>
  )
}
