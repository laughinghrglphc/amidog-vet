export default function AppointmentHero({ step }) {
  const steps = [
    { number: '✓', label: 'Datos' },
    { number: step >= 2 ? '✓' : '2', label: 'Horario' },
    { number: step >= 3 ? '✓' : '3', label: 'Confirmación' }
  ]

  return (
    <section className="hero">
      <div className="container hero__inner">
        <div>
          <h1 className="hero__titulo">
            <img src="/assets/img/patitas.png" alt="" className="patita-izq" aria-hidden="true" />
            Agenda tu cita para tu mascota
            <img src="/assets/img/patitas.png" alt="" className="patita-der" aria-hidden="true" />
          </h1>
          <p className="hero__texto">Completa los datos y te contactaremos para confirmar la hora.</p>
        </div>
        <ol className="pasos" aria-label="Pasos del agendamiento">
          {steps.map((item, index) => {
            const current = index + 1
            const className = current < step ? 'paso paso--hecho' : current === step ? 'paso paso--activo' : 'paso'
            return (
              <li className={className} aria-current={current === step ? 'step' : undefined} key={item.label}>
                <span className="paso__circulo">{item.number}</span>
                <small>{item.label}</small>
              </li>
            )
          })}
        </ol>
      </div>
    </section>
  )
}
