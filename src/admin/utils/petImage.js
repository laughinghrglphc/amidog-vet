export function imageKeyForSpecies(species = '') {
  const value = species.toLocaleLowerCase('es-CL')
  if (value.includes('gato')) return 'cat'
  return 'dog'
}
