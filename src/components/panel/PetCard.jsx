import maxImage from '../../assets/panel/max.png'
import annieImage from '../../assets/panel/annie.png'
import { clinicPetAgeLabel } from '../../utils/petDates'
import { ChevronRightIcon } from './Icons'

function petImage(species) {
  return species?.toLocaleLowerCase('es-CL').includes('gato')
    ? annieImage
    : maxImage
}

function PetCard({ pet, onView = () => {}, compact = false }) {
  return (
    <article className={`registered-pet ${compact ? 'registered-pet--compact' : ''}`}>
      <div className="pet-avatar pet-avatar--large">
        <img src={petImage(pet.species)} alt={pet.name} />
      </div>

      <div className="registered-pet__info">
        <h3>{pet.name}</h3>
        <p>{pet.breed || 'Sin raza informada'}</p>
        <div className="registered-pet__meta">
          <span className="registered-pet__age">{clinicPetAgeLabel(pet.birthdate)}</span>
          <span className="pill">{pet.species}</span>
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
