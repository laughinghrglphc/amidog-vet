const menuButton = document.querySelector('.menu-button');
const mainNav = document.querySelector('.main-nav');
const form = document.querySelector('#contact-form');
const toast = document.querySelector('#toast');

menuButton?.addEventListener('click', () => {
  const isOpen = menuButton.getAttribute('aria-expanded') === 'true';
  menuButton.setAttribute('aria-expanded', String(!isOpen));
  menuButton.setAttribute('aria-label', isOpen ? 'Abrir menú' : 'Cerrar menú');
  mainNav?.classList.toggle('is-open', !isOpen);
});

mainNav?.addEventListener('click', (event) => {
  if (!(event.target instanceof HTMLAnchorElement)) return;
  mainNav.classList.remove('is-open');
  menuButton?.setAttribute('aria-expanded', 'false');
  menuButton?.setAttribute('aria-label', 'Abrir menú');
});

const fields = {
  nombre: {
    element: document.querySelector('#nombre'),
    error: document.querySelector('#nombre-error'),
    validate(value) {
      if (!value.trim()) return 'Escribe tu nombre.';
      if (value.trim().length < 2) return 'El nombre debe tener al menos 2 caracteres.';
      return '';
    }
  },
  correo: {
    element: document.querySelector('#correo'),
    error: document.querySelector('#correo-error'),
    validate(value) {
      if (!value.trim()) return 'Escribe tu correo electrónico.';
      const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
      if (!emailPattern.test(value.trim())) return 'Escribe un correo válido.';
      return '';
    }
  },
  mensaje: {
    element: document.querySelector('#mensaje'),
    error: document.querySelector('#mensaje-error'),
    validate(value) {
      if (!value.trim()) return 'Escribe tu mensaje.';
      if (value.trim().length < 10) return 'El mensaje debe tener al menos 10 caracteres.';
      return '';
    }
  }
};

function setFieldState(field) {
  if (!field.element || !field.error) return true;
  const message = field.validate(field.element.value);
  field.error.textContent = message;
  field.element.setAttribute('aria-invalid', String(Boolean(message)));
  return !message;
}

Object.values(fields).forEach((field) => {
  field.element?.addEventListener('blur', () => setFieldState(field));
  field.element?.addEventListener('input', () => {
    if (field.element?.getAttribute('aria-invalid') === 'true') setFieldState(field);
  });
});

function showToast(message) {
  if (!toast) return;
  toast.textContent = message;
  toast.classList.add('is-visible');
  window.setTimeout(() => toast.classList.remove('is-visible'), 3600);
}

form?.addEventListener('submit', (event) => {
  event.preventDefault();
  const validationResults = Object.values(fields).map(setFieldState);
  const isValid = validationResults.every(Boolean);

  if (!isValid) {
    const firstInvalid = Object.values(fields).find(
      (field) => field.element?.getAttribute('aria-invalid') === 'true'
    );
    firstInvalid?.element?.focus();
    return;
  }

  showToast('Tu consulta fue registrada correctamente. Te contactaremos pronto.');
  form.reset();
  Object.values(fields).forEach((field) => {
    field.element?.setAttribute('aria-invalid', 'false');
    if (field.error) field.error.textContent = '';
  });
});
