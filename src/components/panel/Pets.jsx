import PetCard from './PetCard'
import { PawTrailIcon } from './Icons'

function Pets({ pets, onView, onViewAll, onAdd }) {
  return (
    <section className="panel panel--pets" id="nosotros">
      <div className="panel__header">
        <h2><PawTrailIcon /> Mis mascotas registradas</h2>
        <button type="button" onClick={onViewAll}>Ver todas</button>
      </div>

      <div className="panel__body">
        {pets.slice(0, 2).map((pet) => (
          <PetCard key={pet.id} pet={pet} onView={onView} />
        ))}
      </div>

      <button className="outline-action" type="button" onClick={onAdd}>
        Registrar nueva mascota
      </button>
    </section>
  )
}

export default Pets
