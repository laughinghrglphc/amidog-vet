document.addEventListener('DOMContentLoaded', () => {
  const toggleBtn = document.getElementById('toggle-password');
  const passwordInput = document.getElementById('password');

  if (toggleBtn && passwordInput) {
    toggleBtn.addEventListener('click', () => {
      const isHidden = passwordInput.type === 'password';
      passwordInput.type = isHidden ? 'text' : 'password';
      toggleBtn.setAttribute('aria-label', isHidden ? 'Ocultar contraseña' : 'Mostrar contraseña');

      // swap eye icon
      toggleBtn.querySelector('.icon-eye-open').style.display = isHidden ? 'none' : 'block';
      toggleBtn.querySelector('.icon-eye-closed').style.display = isHidden ? 'block' : 'none';
    });
  }
});
