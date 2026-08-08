import petsHero from '../../assets/panel/pets-hero.png'
import { PawTrailIcon } from './Icons'

function Hero() {
  return (
    <section className="panel-hero" id="inicio">
      <div className="hero__copy">
        <PawTrailIcon className="hero__paw hero__paw--top" />
        <h1>Tus horas</h1>
        <div className="hero__subtitle">
          <p>Revisa tus próximas horas y las mascotas registradas.</p>
          <PawTrailIcon className="hero__paw hero__paw--inline" />
        </div>
      </div>

      <div className="hero__visual" aria-hidden="true">
        <span className="hero__star hero__star--one">✦</span>
        <span className="hero__star hero__star--two">✦</span>
        <img src={petsHero} alt="" />
      </div>
    </section>
  )
}

export default Hero
