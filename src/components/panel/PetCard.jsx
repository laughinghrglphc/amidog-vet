import maxImage from '../../assets/panel/max.png'
import annieImage from '../../assets/panel/annie.png'
import { CheckCircleIcon, ChevronRightIcon } from './Icons'

const images = { max: maxImage, annie: annieImage }

function PetCard({ pet, onView = () => {}, compact = false }) {
  return (
    <article className={`registered-pet ${compact ? 'registered-pet--compact' : ''}`}>
      <div className="pet-avatar pet-avatar--large">
        <img src={images[pet.image] ?? maxImage} alt={pet.name} />
      </div>

      <div className="registered-pet__info">
        <h3>{pet.name}</h3>
        <p>{pet.breed}</p>
        <div className="registered-pet__meta">
          <span className="registered-pet__age">{pet.age}</span>
          <span className="pill">{pet.tag}</span>
          <span className="pill pill--status"><CheckCircleIcon /> {pet.status}</span>
        </div>
      </div>

      {!compact && (
        <button className="chevron-button" type="button" aria-label={`Ver ficha de ${pet.name}`} onClick={() => onView(pet)}>
          <ChevronRightIcon />
        </button>
      )}
    </article>
  )
}

export default PetCard
