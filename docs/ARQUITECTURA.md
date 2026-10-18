# Arquitectura inicial de ChiriHome

## 1. Propósito

ChiriHome es el cliente Android de una plataforma doméstica cuyo objetivo es acceder a servicios multimedia alojados en un servidor local.

Este documento describe la intención inicial. No debe interpretarse como una confirmación de que todos los componentes ya estén integrados en este repositorio.

## 2. Componentes conceptuales

- **Aplicación Android:** interfaz de usuario, navegación y experiencia de reproducción.
- **Servidor doméstico:** aloja los servicios y la biblioteca multimedia.
- **Servicio de música:** permite consultar la biblioteca y coordinar la reproducción, según las capacidades y la configuración disponibles.
- **Servicio de vídeo:** permitirá explorar y reproducir archivos de vídeo compatibles.
- **Red doméstica:** conecta la aplicación con el servidor.

## 3. Flujo general

1. La aplicación obtiene la dirección del servidor configurado.
2. Comprueba la conectividad y se comunica con los servicios disponibles.
3. Presenta el contenido que esos servicios exponen.
4. El usuario selecciona música o vídeo.
5. La aplicación solicita la reproducción al componente correspondiente y presenta el estado cuando esté disponible.

Los detalles del protocolo, los endpoints y el flujo de autenticación deben documentarse después de verificar el código y los servicios reales.

## 4. Seguridad

- No incluir contraseñas, tokens, claves privadas ni otros secretos en el repositorio.
- Evitar transmitir credenciales por conexiones no cifradas fuera de un entorno de desarrollo controlado.
- Validar la identidad y los permisos del usuario en los servicios que lo requieran.
- Documentar cómo se configura la conexión sin publicar datos privados de la red doméstica.

## 5. Decisiones pendientes de confirmar

- Estructura definitiva del módulo Android.
- Servicios y API que utilizará la aplicación para música y vídeo.
- Mecanismo de autenticación y renovación de sesión.
- Requisitos de reproducción en segundo plano.
- Estrategia de pruebas de integración con el servidor.

## 6. Mantenimiento del documento

Actualizar esta página cuando una decisión se implemente y se haya verificado. Separar siempre las decisiones confirmadas de las propuestas.
