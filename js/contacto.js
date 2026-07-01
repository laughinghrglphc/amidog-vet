const contactoMenuButton = document.querySelector(
    ".contacto-menu-button"
);

const contactoNavigation = document.querySelector(
    ".contacto-navigation"
);

const contactoNavigationLinks = document.querySelectorAll(
    ".contacto-navigation a"
);

const contactoForm = document.querySelector(
    "#contacto-form"
);

const contactoFormMessage = document.querySelector(
    "#contacto-form-message"
);

const contactoSubmitButton = document.querySelector(
    ".contacto-submit-button"
);

if (contactoMenuButton && contactoNavigation) {
    contactoMenuButton.addEventListener("click", () => {
        const menuIsOpen = contactoNavigation.classList.toggle(
            "contacto-navigation-open"
        );

        contactoMenuButton.classList.toggle(
            "contacto-menu-open",
            menuIsOpen
        );

        contactoMenuButton.setAttribute(
            "aria-expanded",
            String(menuIsOpen)
        );
    });
}

contactoNavigationLinks.forEach((link) => {
    link.addEventListener("click", () => {
        contactoNavigation.classList.remove(
            "contacto-navigation-open"
        );

        contactoMenuButton.classList.remove(
            "contacto-menu-open"
        );

        contactoMenuButton.setAttribute(
            "aria-expanded",
            "false"
        );
    });
});

if (
    contactoForm &&
    contactoFormMessage &&
    contactoSubmitButton
) {
    contactoForm.addEventListener("submit", (event) => {
        event.preventDefault();

        if (!contactoForm.checkValidity()) {
            contactoForm.reportValidity();
            return;
        }

        contactoSubmitButton.disabled = true;
        contactoSubmitButton.textContent = "Enviando...";

        window.setTimeout(() => {
            contactoForm.reset();

            contactoSubmitButton.disabled = false;
            contactoSubmitButton.textContent = "Enviar";

            contactoFormMessage.classList.add(
                "contacto-message-visible"
            );

            window.setTimeout(() => {
                contactoFormMessage.classList.remove(
                    "contacto-message-visible"
                );
            }, 3500);
        }, 600);
    });
}