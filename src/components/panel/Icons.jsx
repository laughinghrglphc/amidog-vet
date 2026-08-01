function PawShape() {
  return (
    <>
      <ellipse cx="9" cy="8" rx="3.6" ry="4.8" transform="rotate(-28 9 8)" />
      <ellipse cx="17" cy="5.5" rx="3.5" ry="4.8" transform="rotate(-8 17 5.5)" />
      <ellipse cx="24.5" cy="7.8" rx="3.5" ry="4.7" transform="rotate(20 24.5 7.8)" />
      <ellipse cx="29" cy="14.5" rx="3.1" ry="4.2" transform="rotate(38 29 14.5)" />
      <path d="M6.7 22.6c.3-5.6 4.6-10 10-10 5.7 0 10.1 4.8 10.1 10.3 0 4.2-3.1 7.1-6.8 5.3-2.1-1-4.6-1-6.7.1-3.8 2-6.9-.9-6.6-5.7Z" />
    </>
  )
}

export function PawIcon({ className = '' }) {
  return (
    <svg className={className} viewBox="0 0 34 32" aria-hidden="true">
      <PawShape />
    </svg>
  )
}

export function PawTrailIcon({ className = '' }) {
  return (
    <svg className={className} viewBox="0 0 66 58" aria-hidden="true">
      <g transform="translate(1 24) rotate(-18 17 16)">
        <PawShape />
      </g>
      <g transform="translate(31 1) rotate(16 17 16)">
        <PawShape />
      </g>
    </svg>
  )
}

export function UserIcon({ className = '' }) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="8" r="4" />
      <path d="M4.8 20c.7-4.2 3.3-6.5 7.2-6.5s6.5 2.3 7.2 6.5H4.8Z" />
    </svg>
  )
}

export function ChevronDownIcon({ className = '' }) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true">
      <path d="m6 9 6 6 6-6" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export function ChevronRightIcon({ className = '' }) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true">
      <path d="m9 5 7 7-7 7" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}

export function CloseCircleIcon({ className = '' }) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" strokeWidth="1.8" />
      <path d="m9 9 6 6m0-6-6 6" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  )
}

export function CheckCircleIcon({ className = '' }) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" strokeWidth="1.8" />
      <path d="m8 12.2 2.5 2.5 5.6-6" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
