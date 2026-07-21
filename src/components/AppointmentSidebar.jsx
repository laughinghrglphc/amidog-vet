export default function AppointmentSidebar() {
  return (
    <aside className="confirmacion-tarjeta" aria-labelledby="tarjeta-titulo">
      <h3 className="tarjeta-titulo" id="tarjeta-titulo">Tu cita en AmiDog</h3>
      <div className="mascota-circular">
        <div className="mascota-circular__bg"></div>
        <img src="/assets/img/AnimalesPrincipales.png" alt="Gatitos y cachorro de AmiDog" className="mascota-circular__img" />
        <img src="/assets/img/estrella.png" alt="" className="mascota-circular__star mascota-circular__star--top" aria-hidden="true" />
        <img src="/assets/img/estrella.png" alt="" className="mascota-circular__star mascota-circular__star--bottom" aria-hidden="true" />
        <img src="/assets/img/patitas.png" alt="" className="mascota-circular__paw" aria-hidden="true" />
      </div>
      <ul className="tarjeta-lista-beneficios">
        <li className="beneficio-item"><span className="beneficio-icono" aria-hidden="true">✓</span>Confirmación por WhatsApp o correo</li>
        <li className="beneficio-item"><span className="beneficio-icono" aria-hidden="true">✓</span>Reagenda fácilmente si lo necesitas</li>
        <li className="beneficio-item"><span className="beneficio-icono" aria-hidden="true">✓</span>Atención cercana y sin complicaciones</li>
      </ul>
      <div className="tarjeta-caja-seguridad">
        <h4 className="caja-seguridad-titulo">Reserva rápida y segura</h4>
        <p className="caja-seguridad-texto">Tus datos están protegidos y tu mascota recibirá la mejor atención.</p>
      </div>
    </aside>
  )
}
