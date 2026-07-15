(function () {
  "use strict";

  const STORAGE_KEYS = {
    pending: ["amidogReservaPendiente", "amidogReserva", "reservaAmiDog"],
    confirmed: "amidogReservaConfirmada",
    scheduleUrl: "amidogHorarioUrl",
  };

  const FIELD_MAP = {
    "resumen-mascota": ["mascota", "tipoMascota", "pet", "petType"],
    "resumen-servicio": ["servicio", "service"],
    "resumen-profesional": ["profesional", "veterinario", "professional", "vet"],
    "resumen-fecha": ["fecha", "date"],
    "resumen-hora": ["hora", "time"],
    "resumen-nombre": ["nombre", "nombreCliente", "name", "clientName"],
    "resumen-telefono": ["telefono", "phone", "tel"],
    "resumen-correo": ["correo", "email", "mail"],
  };

  const getElement = (id) => document.getElementById(id);

  const parseJson = (value) => {
    if (!value) return null;

    try {
      return JSON.parse(value);
    } catch (error) {
      return null;
    }
  };

  const getNestedValue = (source, keys) => {
    if (!source) return "";

    for (const key of keys) {
      if (source[key]) return source[key];
      if (source.contacto && source.contacto[key]) return source.contacto[key];
      if (source.cliente && source.cliente[key]) return source.cliente[key];
      if (source.horario && source.horario[key]) return source.horario[key];
      if (source.cita && source.cita[key]) return source.cita[key];
    }

    return "";
  };

  const getStoredReservation = () => {
    for (const key of STORAGE_KEYS.pending) {
      const storedValue = parseJson(sessionStorage.getItem(key)) || parseJson(localStorage.getItem(key));
      if (storedValue) return storedValue;
    }

    return {};
  };

  const getQueryReservation = () => {
    const params = new URLSearchParams(window.location.search);
    const reservation = {};

    for (const keys of Object.values(FIELD_MAP)) {
      for (const key of keys) {
        if (params.has(key)) {
          reservation[key] = params.get(key);
        }
      }
    }

    return reservation;
  };

  const hydrateSummary = () => {
    const reservation = {
      ...getStoredReservation(),
      ...getQueryReservation(),
    };

    for (const [elementId, keys] of Object.entries(FIELD_MAP)) {
      const element = getElement(elementId);
      const value = getNestedValue(reservation, keys);

      if (element && value) {
        element.textContent = value;
      }
    }
  };

  const getSummaryData = () => {
    return Object.keys(FIELD_MAP).reduce((summary, elementId) => {
      const fieldName = elementId.replace("resumen-", "");
      summary[fieldName] = getElement(elementId)?.textContent.trim() || "";
      return summary;
    }, {});
  };

  const showFeedback = (message) => {
    const feedback = getElement("confirmacion-feedback");

    if (!feedback) return;

    feedback.textContent = message;
    feedback.classList.add("confirmacion-feedback--visible");
  };

  const goBackToSchedule = () => {
    const backButton = getElement("btn-volver");
    const savedScheduleUrl = sessionStorage.getItem(STORAGE_KEYS.scheduleUrl);
    const fallbackUrl = backButton?.dataset.backFallback || "../index.html";

    if (savedScheduleUrl) {
      window.location.href = savedScheduleUrl;
      return;
    }

    if (document.referrer) {
      const referrerUrl = new URL(document.referrer);

      if (referrerUrl.origin === window.location.origin && referrerUrl.href !== window.location.href) {
        window.history.back();
        return;
      }
    }

    window.location.href = fallbackUrl;
  };

  const confirmReservation = () => {
    const confirmButton = getElement("btn-confirmar");
    const reservation = {
      ...getSummaryData(),
      estado: "confirmada",
      confirmadoEn: new Date().toISOString(),
    };

    localStorage.setItem(STORAGE_KEYS.confirmed, JSON.stringify(reservation));
    document.dispatchEvent(new CustomEvent("amidog:reserva-confirmada", { detail: reservation }));

    if (confirmButton) {
      confirmButton.disabled = true;
      confirmButton.classList.add("btn--confirmado");
      confirmButton.textContent = "Reserva confirmada";
    }

    showFeedback("Reserva confirmada. Te contactaremos por WhatsApp o correo electrónico.");
  };

  const initConfirmationPage = () => {
    const backButton = getElement("btn-volver");
    const confirmButton = getElement("btn-confirmar");

    hydrateSummary();

    backButton?.addEventListener("click", goBackToSchedule);
    confirmButton?.addEventListener("click", confirmReservation);
  };

  document.addEventListener("DOMContentLoaded", initConfirmationPage);
})();
