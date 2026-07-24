import { useEffect } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import Home from './pages/Home'
import Nosotros from './pages/Nosotros'
import Contacto from './pages/Contacto'
import Login from './pages/Login'
import Reservation from './pages/Reservation'
import Calendario from './pages/Calendario'
import Confirmacion from './pages/Confirmacion'
import Panel from './pages/Panel'
import PanelAdmin from './pages/PanelAdmin'

function ScrollManager() {
  const location = useLocation()

  useEffect(() => {
    if (location.hash) {
      window.requestAnimationFrame(() => {
        document.querySelector(location.hash)?.scrollIntoView()
      })
      return
    }

    window.scrollTo(0, 0)
  }, [location.pathname, location.hash])

  return null
}

export default function App() {
  return (
    <>
      <ScrollManager />
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/nosotros" element={<Nosotros />} />
        <Route path="/contacto" element={<Contacto />} />
        <Route path="/login" element={<Login />} />
        <Route path="/reservar" element={<Reservation />} />
        <Route path="/calendario" element={<Calendario />} />
        <Route path="/confirmacion" element={<Confirmacion />} />
        <Route path="/panel" element={<Panel />} />
        <Route path="/panel-admin" element={<PanelAdmin />} />
        <Route path="/pages/nosotros.html" element={<Navigate to="/nosotros" replace />} />
        <Route path="/pages/contacto.html" element={<Navigate to="/contacto" replace />} />
        <Route path="/pages/login.html" element={<Navigate to="/login" replace />} />
        <Route path="/pages/reservation.html" element={<Navigate to="/reservar" replace />} />
        <Route path="/pages/calendario.html" element={<Navigate to="/calendario" replace />} />
        <Route path="/pages/confirmacion.html" element={<Navigate to="/confirmacion" replace />} />
        <Route path="/pages/panel.html" element={<Navigate to="/panel" replace />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  )
}