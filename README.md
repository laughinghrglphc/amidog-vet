# amidog-vet

Calendario de citas — AmiDog Veterinaria.

## Estilos (SASS)

```
styles/
├── main.scss          # Punto de entrada
├── _variables.scss    # Tokens, breakpoints y mixins
├── _base.scss         # Reset + utilidades
├── _layout.scss       # Header, footer (compartido)
├── _components.scss   # Botones compartidos
└── _calendario.scss   # Estilos exclusivos de pages/calendario.html
```

Compilar CSS:

```bash
npm run build:css
```

Modo watch:

```bash
npm run watch:css
```

La página `pages/calendario.html` enlaza `styles/main.css`.
