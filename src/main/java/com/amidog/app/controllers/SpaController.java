package com.amidog.app.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Controlador SPA: redirige todas las rutas no manejadas por la API REST
 * hacia el index.html del frontend (React/Vite compilado en static/).
 *
 * Esto permite que React Router gestione el enrutamiento del lado del cliente
 * sin que Spring Boot devuelva errores 404 al acceder directamente a rutas
 * como /login, /mascotas, /panel, /calendario, etc.
 */
@Controller
public class SpaController {

    /**
     * Captura rutas que:
     *  - Son la raíz "/"
     *  - Son rutas de un solo nivel sin extensión de archivo (ej: /login, /panel)
     *  - Son rutas anidadas sin extensión de archivo (ej: /panel/admin, /mascotas/123)
     *
     * Las rutas de la API REST (@RestController) tienen prioridad sobre este handler
     * porque Spring las evalúa primero al ser más específicas.
     * Los archivos estáticos (con extensión como .js, .css, .png) son servidos
     * directamente por Spring Boot desde /static/, sin llegar a este controlador.
     */
    @RequestMapping(value = {
        "/",
        "/{path:[^\\.]*}",
        "/{path:[^\\.]*}/**"
    })
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
