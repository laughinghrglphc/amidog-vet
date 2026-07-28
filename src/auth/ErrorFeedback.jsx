export function ErrorFeedback({ error }) {
  if (!error) {
    return null
  }

  return (
    <div className="account-feedback account-feedback--error" role="alert">
      <p>{error.message}</p>
      {Object.keys(error.errors).length > 0 && (
        <ul>
          {Object.entries(error.errors).map(([field, message]) => (
            <li key={field}>{message}</li>
          ))}
        </ul>
      )}
    </div>
  )
}
