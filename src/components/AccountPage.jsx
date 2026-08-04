import Header from './Header'
import Footer from './Footer'

export default function AccountPage({ children, heading, intro }) {
  return (
    <>
      <Header />
      <main className="account-page">
        <section className="login-card account-card" aria-labelledby="account-page-title">
          <h1 className="login-card__title" id="account-page-title">{heading}</h1>
          {intro && <p className="login-card__subtitle">{intro}</p>}
          {children}
        </section>
      </main>
      <Footer />
    </>
  )
}
