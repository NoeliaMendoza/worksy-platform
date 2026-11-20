SonarQube Cloud en GitHub Actions
================================

El workflow ``CI`` analiza Java, TypeScript, Python y archivos de infraestructura
en un solo proyecto de SonarQube Cloud. Se ejecuta después de ``mvn verify`` en
los pushes a ``main`` y los pull requests del mismo repositorio hacia la rama
predeterminada. Los PR de forks y Dependabot omiten el análisis porque GitHub
restringe el acceso al secreto.

Activación
----------

1. En `SonarQube Cloud <https://sonarcloud.io/>`_, inicia sesión con GitHub e
   importa ``NoeliaMendoza/worksy-platform``. Copia las claves del proyecto y
   de la organización de SonarQube Cloud.
2. En **Administration → Analysis Method** de SonarQube Cloud, selecciona
   el análisis con GitHub Actions. Si **Automatic Analysis** está activo,
   desactívalo.
3. En GitHub, abre **Settings → Secrets and variables → Actions**. En
   **Variables**, registra ``SONAR_PROJECT_KEY`` y ``SONAR_ORGANIZATION`` con
   las claves del primer paso. En **Secrets**, crea ``SONAR_TOKEN`` con un
   token de SonarQube Cloud con permiso para analizar el proyecto.
4. Publica los cambios en GitHub. El primer push a ``main`` establece el
   análisis de referencia. En **Actions → CI → Backend (Java 21)**, abre el
   paso **Analizar con SonarQube Cloud** y sigue el enlace que imprime el
   escáner para ver el resultado.

El paso se omite hasta configurar ambas variables de GitHub. La integración
hace análisis estático y muestra el Quality Gate en SonarQube Cloud. Los
reportes de cobertura y el requisito de aprobar el Quality Gate para fusionar
un PR se configuran por separado.
