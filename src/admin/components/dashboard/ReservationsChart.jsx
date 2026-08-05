import { useMemo, useState } from 'react'
import Icon from '../ui/Icon'

export default function ReservationsChart({ chart, onOpenReport }) {
  const periodKeys = Object.keys(chart)
  const [periodKey, setPeriodKey] = useState(periodKeys[0])
  const dataset = chart[periodKey]

  const graph = useMemo(() => {
    const values = dataset.values.length ? dataset.values : [0]
    const maxValue = Math.max(...values, 1)
    const max = Math.ceil(maxValue / 10) * 10
    const width = 620
    const height = 170
    const divisor = Math.max(values.length - 1, 1)
    const points = values.map((value, index) => ({
      value,
      x: 10 + (index * width) / divisor,
      y: 195 - (value / max) * height,
    }))
    const polyline = points.map((point) => `${point.x},${point.y}`).join(' ')
    const area = `M ${points[0].x} ${points[0].y} ${points.slice(1).map((point) => `L ${point.x} ${point.y}`).join(' ')} L 630 210 L 10 210 Z`
    const ticks = Array.from(
      { length: 6 },
      (_, index) => Math.round(max - (index * max) / 5),
    )

    return { area, points, polyline, ticks }
  }, [dataset])

  return (
    <article className="admin-card admin-weekly-chart">
      <header className="admin-card__header">
        <h3><Icon name="calendar" size={17} /> Reservas</h3>
        <select
          value={periodKey}
          onChange={(event) => setPeriodKey(event.target.value)}
          aria-label="Período del gráfico"
        >
          {periodKeys.map((key) => (
            <option key={key} value={key}>{chart[key].label}</option>
          ))}
        </select>
      </header>

      <div className="admin-weekly-chart__plot">
        <div className="admin-weekly-chart__axis">
          {graph.ticks.map((value, index) => <span key={`${value}-${index}`}>{value}</span>)}
        </div>
        <svg viewBox="0 0 640 220" role="img" aria-labelledby="reservation-chart-title reservation-chart-description">
          <title id="reservation-chart-title">Reservas: {dataset.label}</title>
          <desc id="reservation-chart-description">
            {dataset.labels.map((label, index) => `${label}: ${dataset.values[index]}`).join(', ')}
          </desc>
          <defs>
            <linearGradient id="adminChartFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#f87823" stopOpacity="0.23" />
              <stop offset="100%" stopColor="#f87823" stopOpacity="0.04" />
            </linearGradient>
          </defs>
          {[30, 70, 110, 150, 190].map((y) => (
            <line key={y} x1="0" y1={y} x2="640" y2={y} className="chart-grid-line" />
          ))}
          <path d={graph.area} fill="url(#adminChartFill)" />
          <polyline points={graph.polyline} className="chart-line" />
          {graph.points.map((point, index) => (
            <g key={`${dataset.labels[index] ?? 'empty'}-${index}`}>
              <circle cx={point.x} cy={point.y} r="5" className="chart-point" />
              <title>{dataset.labels[index] ?? dataset.label}: {point.value} reservas</title>
            </g>
          ))}
        </svg>
        <div
          className="admin-weekly-chart__labels"
          style={{
            gridTemplateColumns: `repeat(${Math.max(dataset.labels.length, 1)}, 1fr)`,
          }}
        >
          {dataset.labels.map((label) => <span key={label}>{label}</span>)}
        </div>
      </div>

      <button
        type="button"
        className="admin-link-button"
        onClick={() => onOpenReport(dataset)}
      >
        Ver reporte completo <Icon name="chevronRight" size={14} />
      </button>
    </article>
  )
}
