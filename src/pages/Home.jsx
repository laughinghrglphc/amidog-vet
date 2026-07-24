import { Link } from 'react-router-dom'
import Header from '../components/Header'
import Footer from '../components/Footer'
import HeartBadge from '../components/HeartBadge'

export default function Home() {
  return (
    <>
      <Header />
      <main>
        <section className="seccion-principal" id="inicio">
          <div className="caja-naranja">
            <div className="info-banner">
              <HeartBadge>VETERINARIA DE CONFIANZA</HeartBadge>
              <h1>
                <img src="/assets/img/patitas.png" alt="Patitas" className="patita-izq" />
                Cuidamos a tu <br />
                <span className="highlight">AmiDog</span>
                <img src="/assets/img/patitas.png" alt="Patitas" className="patita-der" />
              </h1>
              <p>Atención simple, cercana y con mucho cariño para perros, gatos y otras mascotas.</p>
              <div className="botones-banner">
                <a href="#servicios" className="btn btn-primary">Ver Servicios</a>
                <Link to="/contacto" className="btn btn-secondary">Contactar</Link>
              </div>
            </div>
            <div className="contenedor-mascotas-circulo">
              <div className="circulo-blanco-fondo"></div>
              <img src="/assets/img/estrella.png" alt="Estrella" className="deco-img-estrella deco-img-estrella--arriba" />
              <img src="/assets/img/estrella.png" alt="Estrella" className="deco-img-estrella deco-img-estrella--abajo" />
              <img src="/assets/img/patitas.png" alt="Patitas Decorativas" className="deco-img-patitas" />
              <img src="/assets/img/AnimalesPrincipales.png" alt="Mascotas Amidog" className="img-perro-gato" />
            </div>
          </div>
        </section>
        <section className="seccion-servicios" id="servicios">
          <div className="contenedor-titulos">
            <h2>Nuestros servicios</h2>
            <p className="subtitulo">Lo básico para cuidar a tu mascota</p>
          </div>
          <div className="bloque-tarjetas">
            <article className="tarjeta-servicio"><div className="icono-servicio">🩺</div><h3>Consulta general</h3><p>Revisión y orientación para tu mascota</p></article>
            <article className="tarjeta-servicio"><div className="icono-servicio">💉</div><h3>Vacunas</h3><p>Protección y control de vacunas al día</p></article>
            <article className="tarjeta-servicio"><div className="icono-servicio">✂️</div><h3>Peluquería</h3><p>Baño, corte y limpieza básica</p></article>
          </div>
        </section>
      </main>
      <Footer />
    </>
  )
}
