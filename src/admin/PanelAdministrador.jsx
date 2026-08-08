import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import petsHero from '../assets/admin/animals-hero.webp'
import natalyCat from '../assets/admin/cat-peek.webp'
import AgendaCard from './components/dashboard/AgendaCard'
import AppointmentsTable from './components/dashboard/AppointmentsTable'
import DashboardModals from './components/dashboard/DashboardModals'
import MiniCalendar from './components/dashboard/MiniCalendar'
import ReservationsChart from './components/dashboard/ReservationsChart'
import ServicesCard from './components/dashboard/ServicesCard'
import SummaryGrid from './components/dashboard/SummaryGrid'
import Sidebar from './components/layout/Sidebar'
import Topbar from './components/layout/Topbar'
import Icon from './components/ui/Icon'
import useAdminNotifications from './hooks/useAdminNotifications'
import { clinicCalendarDate, formatDashboardDate } from './utils/date'
import { findDashboardResults } from './utils/search'

const navItems = [
  { label: 'Panel', icon: 'home', modal: null },
  { label: 'Reservas', icon: 'calendar', modal: 'appointments' },
  { label: 'Servicios', icon: 'stethoscope', modal: 'services' },
  { label: 'Disponibilidad', icon: 'calendar', modal: 'availability' },
  { label: 'Clientes', icon: 'user', modal: 'clients' },
  { label: 'Mascotas', icon: 'paw', modal: 'pets' },
  { label: 'Notificaciones', icon: 'info', modal: 'notifications' },
  { label: 'Calendario', icon: 'calendar', modal: 'agenda' },
]

export default function PanelAdministrador({
  api,
  availabilityApi,
  data,
  error,
  logoutPending,
  now,
  onLogout,
  onReloadDashboard,
}) {
  const [activeItem, setActiveItem] = useState('Panel')
  const [isMobile, setIsMobile] = useState(
    () => typeof window !== 'undefined' && window.innerWidth <= 900,
  )
  const [modal, setModal] = useState(null)
  const [query, setQuery] = useState('')
  const [sidebarCompact, setSidebarCompact] = useState(false)
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const notifications = useAdminNotifications(api)
  const drawerCloseRef = useRef(null)
  const drawerTriggerRef = useRef(null)
  const mainRef = useRef(null)
  const clinicToday = useMemo(() => clinicCalendarDate(now), [now])

  useEffect(() => {
    const handleResize = () => {
      const mobile = window.innerWidth <= 900
      setIsMobile(mobile)
      if (!mobile) setSidebarOpen(false)
    }

    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  useEffect(() => {
    if (!isMobile) return undefined
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = sidebarOpen ? 'hidden' : previousOverflow
    return () => {
      document.body.style.overflow = previousOverflow
    }
  }, [isMobile, sidebarOpen])

  useEffect(() => {
    if (!isMobile || !sidebarOpen) {
      mainRef.current?.removeAttribute('inert')
      return undefined
    }

    const main = mainRef.current
    const trigger = document.activeElement
    drawerTriggerRef.current = trigger instanceof HTMLElement ? trigger : null
    main?.setAttribute('inert', '')
    drawerCloseRef.current?.focus()

    return () => {
      main?.removeAttribute('inert')
      if (drawerTriggerRef.current?.isConnected) {
        drawerTriggerRef.current.focus()
      }
      drawerTriggerRef.current = null
    }
  }, [isMobile, sidebarOpen])

  const searchResults = useMemo(
    () => findDashboardResults(query, { appointments: data.appointments }),
    [data.appointments, query],
  )
  const closeModal = useCallback(() => setModal(null), [])
  const openModal = useCallback((type, extra = {}) => {
    setModal({ type, ...extra })
  }, [])

  const handleNavigation = (item) => {
    setActiveItem(item.label)
    setSidebarOpen(false)
    if (!item.modal) {
      setModal(null)
      document.getElementById('dashboard-top')?.scrollIntoView({
        behavior: 'smooth',
        block: 'start',
      })
      return
    }
    openModal(item.modal, item.modal === 'agenda' ? { date: clinicToday } : {})
  }

  const handleSearchResult = (result) => {
    setQuery('')
    openModal('appointment', { id: result.id })
  }

  const handleMenuButton = () => {
    if (isMobile) setSidebarOpen((current) => !current)
    else setSidebarCompact((current) => !current)
  }
  const firstName = data.profile.name.trim().split(/\s+/)[0]

  return (
    <div
      className={`admin-dashboard ${sidebarCompact ? 'is-sidebar-compact' : ''}`}
      id="dashboard-top"
    >
      <Sidebar
        activeItem={activeItem}
        closeButtonRef={drawerCloseRef}
        isMobile={isMobile}
        items={navItems}
        onClose={() => setSidebarOpen(false)}
        onNavigate={handleNavigation}
        open={sidebarOpen}
        unreadCount={notifications.unreadCount}
      />

      {sidebarOpen && (
        <button
          type="button"
          className="admin-sidebar-backdrop"
          aria-label="Cerrar menú"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      <main className="admin-main" ref={mainRef}>
        <Topbar
          isMobile={isMobile}
          logoutPending={logoutPending}
          onLogout={onLogout}
          onMenuClick={handleMenuButton}
          onOpenProfile={() => openModal('profile')}
          onQueryChange={setQuery}
          onResultSelect={handleSearchResult}
          profile={data.profile}
          query={query}
          searchResults={searchResults}
          sidebarOpen={sidebarOpen}
        />

        <div className="admin-content">
          {error && (
            <div className="admin-inline-error" role="alert">
              <Icon name="info" size={18} />
              {error}
            </div>
          )}

          <section className="admin-welcome">
            <div>
              <h1>Bienvenida, {firstName}</h1>
              <p>Aquí tienes un resumen claro de lo que sucede hoy en AmiDog.</p>
            </div>
            <time className="admin-welcome__date" dateTime={now.toISOString()}>
              <i aria-hidden="true" />{formatDashboardDate(now)}
            </time>
            <div className="admin-welcome__pets" aria-hidden="true">
              <span className="admin-welcome__paw admin-welcome__paw--one"><Icon name="paw" size={18} strokeWidth={2.2} /></span>
              <span className="admin-welcome__paw admin-welcome__paw--two"><Icon name="paw" size={14} strokeWidth={2.2} /></span>
              <span className="admin-welcome__sparkle admin-welcome__sparkle--left">✦</span>
              <img src={petsHero} alt="" />
              <span className="admin-welcome__sparkle admin-welcome__sparkle--right">✦</span>
            </div>
          </section>

          <SummaryGrid
            stats={data.stats}
            onOpenAppointments={() => openModal('appointments')}
          />

          <section className="admin-dashboard-grid admin-dashboard-grid--primary">
            <ReservationsChart
              chart={data.chart}
              onOpenReport={(dataset) => openModal('report', { dataset })}
            />
            <AppointmentsTable
              appointments={data.appointments}
              now={now}
              onOpen={(id) => openModal('appointment', { id })}
              onViewAll={() => openModal('appointments')}
            />
          </section>

          <section className="admin-dashboard-grid admin-dashboard-grid--secondary">
            <ServicesCard
              services={data.services}
              onViewAll={() => openModal('services')}
            />
            <AgendaCard
              schedule={data.schedule}
              onOpenAppointment={(id) => openModal('appointment', { id })}
              onViewCalendar={() => openModal('agenda', { date: clinicToday })}
            />
            <article className="admin-card admin-cat-peek" aria-hidden="true">
              <img src={natalyCat} alt="" loading="lazy" />
            </article>
            <MiniCalendar
              now={now}
              onOpenAgenda={(date) => openModal('agenda', { date })}
            />
          </section>
        </div>
      </main>

      <DashboardModals
        api={api}
        availabilityApi={availabilityApi}
        data={data}
        modal={modal}
        notificationResource={notifications}
        now={now}
        onClose={closeModal}
        onOpenAppointment={(id) => openModal('appointment', { id })}
        onReloadDashboard={onReloadDashboard}
      />
    </div>
  )
}
