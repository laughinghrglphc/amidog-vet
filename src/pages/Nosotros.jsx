import Header from '../components/Header'
import Footer from '../components/Footer'
import HeartBadge from '../components/HeartBadge'

export default function Nosotros() {
  return (
    <>
      <Header />
      <section className="nosotros-section">
        <div className="nosotros-wrapper">
          <div className="hero-banner">
            <div className="hero-text">
              <HeartBadge>NUESTRA ESENCIA</HeartBadge>
              <h2>
                <img src="/assets/img/patitas.png" alt="Patitas" className="patita-izq" />
                Más que una veterinaria, <br />
                <strong>somos parte de tu familia<img src="/assets/img/patitas.png" alt="Patitas" className="patita-der" /></strong>
              </h2>
              <p>Acompañamos a cada mascota con atención cercana, criterio profesional y un cariño que se nota.</p>
            </div>
            <div className="hero-image-container">
              <div className="white-circle-bg"></div>
              <img src="/assets/img/AnimalesPrincipales.png" alt="Mascotas Amidog" className="hero-circle-img" />
              <img src="/assets/img/estrella.png" alt="Estrella" className="deco-img-estrella deco-img-estrella--arriba" />
              <img src="/assets/img/estrella.png" alt="Estrella" className="deco-img-estrella deco-img-estrella--abajo" />
              <img src="/assets/img/patitas.png" alt="Patitas Decorativas" className="deco-img-patitas" />
            </div>
          </div>
          <div className="history-card">
            <div className="history-info">
              <h3>Nuestra historia</h3>
              <p>Amidog nació para hacer que la atención veterinaria se sienta más humana. Creemos en escuchar, explicar y acompañar cada decisión con claridad.</p>
            </div>
            <div className="vertical-divider"></div>
            <div className="stats-grid">
              <div className="stat-box"><span className="number cyan-text">10+</span><span className="label">años acompañando</span></div>
              <div className="stat-box"><span className="number pink-text">3.500+</span><span className="label">mascotas atendidas</span></div>
              <div className="stat-box"><span className="number yellow-text">4,9</span><span className="label">valoración promedio</span></div>
            </div>
          </div>
          <div className="values-block">
            <h3 className="values-title">Lo que nos mueve</h3>
            <div className="values-cards-container">
              <div className="value-card"><div className="icon-holder">❤️</div><h4>Cercanía</h4><p>Te explicamos cada paso con palabras simples.</p></div>
              <div className="value-card"><div className="icon-holder">🛡️</div><h4>Confianza</h4><p>Cuidamos con criterio, honestidad y transparencia.</p></div>
              <div className="value-card"><div className="icon-holder"><img src="/assets/img/patitas.png" alt="" className="value-icon" aria-hidden="true" /></div><h4>Bienestar</h4><p>Pensamos en la salud física y emocional.</p></div>
            </div>
          </div>
        </div>
      </section>
      <Footer />
    </>
  )
}
