import { clinicDateValue } from './reservation'

export function clinicTodayValue(now = new Date()) {
  return clinicDateValue(now)
}

export function clinicPetAgeLabel(birthdate, now = new Date()) {
  if (!birthdate) return 'Edad no informada'
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(birthdate)
  const today = clinicDateValue(now)
  if (!match || !today) return 'Edad no informada'
  const [, birthYearText, birthMonthText, birthDayText] = match
  const birthYear = Number(birthYearText)
  const birthMonth = Number(birthMonthText)
  const birthDay = Number(birthDayText)
  const validBirth = new Date(Date.UTC(birthYear, birthMonth - 1, birthDay))
  if (
    validBirth.getUTCFullYear() !== birthYear
    || validBirth.getUTCMonth() + 1 !== birthMonth
    || validBirth.getUTCDate() !== birthDay
  ) {
    return 'Edad no informada'
  }
  const [currentYear, currentMonth, currentDay] = today.split('-').map(Number)
  let years = currentYear - birthYear
  const beforeBirthday = currentMonth < birthMonth
    || (currentMonth === birthMonth && currentDay < birthDay)
  if (beforeBirthday) years -= 1
  if (years < 0) return 'Edad no informada'
  return `${years} ${years === 1 ? 'a\u00f1o' : 'a\u00f1os'}`
}
