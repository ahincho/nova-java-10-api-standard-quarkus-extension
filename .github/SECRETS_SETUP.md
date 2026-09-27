# Configuracion de Secrets para GitHub Actions

Este repositorio usa los siguientes secrets en GitHub Actions. **Ninguno es commiteado al repo** por seguridad; deben configurarse manualmente en la UI de GitHub despues de crear el repo.

## Acceso a la configuracion

`https://github.com/ahincho/nova-java-10-api-standard-quarkus-extension/settings/secrets/actions`

Dos scopes:

- **Repository secrets** (este repo unicamente) — preferred para todo lo especifico
- **Environment secrets** (`production`, etc.) — usar para credenciales que cambian entre deployments

## Secrets requeridos

### `NOVA_RELEASE_PAT` (REQUIRED para release-please)

**Que es:** Personal Access Token (classic o fine-grained) que reemplaza al `GITHUB_TOKEN` automatico en el workflow `release-please.yml`.

**Por que se necesita:** GitHub no permite que `GITHUB_TOKEN` dispare otros workflows (e.g., `publish-on-tag.yml` cuando release-please hace push del tag). Sin este PAT, el bootstrap de release queda incompleto.

**Como crearlo:**

1. Ir a `https://github.com/settings/tokens?type=beta` (fine-grained, recomendado) o `?type=personal` (classic).
2. **Fine-grained token** (recomendado):
   - **Name:** `Nova Release PAT - nova-java-api-standard-quarkus-extension`
   - **Expiration:** 1 year (renovar antes)
   - **Resource owner:** `ahincho`
   - **Repository access:** `Only select repositories` → `nova-java-api-standard-quarkus-extension`
   - **Permissions:**
     - **Contents:** Read and write
     - **Pull requests:** Read and write
     - **Metadata:** Read-only (automatico)
3. **Classic token** (alternativa, mas permisivo):
   - **Note:** `Nova Release PAT - nova-java-api-standard-quarkus-extension`
   - **Expiration:** 1 year
   - **Scopes:**
     - `repo` (full)
     - `workflow` (si necesitas editar workflows desde el token)
4. Click **Generate token** y copiar el valor inmediatamente (no se vuelve a mostrar).

**Como configurarlo en el repo:**

1. Ir a `https://github.com/ahincho/nova-java-10-api-standard-quarkus-extension/settings/secrets/actions/new`
2. **Name:** `NOVA_RELEASE_PAT`
3. **Secret:** pegar el token copiado
4. Click **Add secret**.

**Verificacion:** despues de configurar, hacer un commit trivial en `main` (e.g., actualizar README). El workflow `release-please.yml` deberia abrir un PR `chore(main): release 1.0.0` automaticamente.

### `NOVA_PACKAGES_READ_TOKEN` (OPTIONAL, recomendado)

**Que es:** PAT con scope `read:packages` que se usa en workflows de SBOM/OWASP para resolver dependencias cross-repo publicadas en GitHub Packages de otros repos Nova.

**Por que se necesita:** sin el, las dependencias `pe.edu.nova.java.*` de otros repos (e.g., `nova-api-standard:1.0.0`) devuelven 401 al intentar descargarlas en el job SBOM. Los builds normales ya tienen su propio token de publicacion.

**Como crearlo:**

- Mismo procedimiento que `NOVA_RELEASE_PAT` pero con scope **solo de lectura**:
  - **Fine-grained:** Contents: Read-only, Packages: Read-only
  - **Classic:** `repo`, `read:packages`

**Como configurarlo:** mismo path que arriba, nombre `NOVA_PACKAGES_READ_TOKEN`.

**Alternativa sin crear un token aparte:** si el `NOVA_RELEASE_PAT` ya esta configurado, los workflows hacen fallback automatico:
```yaml
packages-read-token: ${{ secrets.NOVA_PACKAGES_READ_TOKEN || secrets.NOVA_RELEASE_PAT }}
```
Pero por seguridad es preferible tener un token de read-only aparte.

## Secrets opcionales

### `NVD_API_KEY` (OPTIONAL, recomendado para velocidad)

**Que es:** API key gratuita del National Vulnerability Database (NIST) que evita el rate limit publico del job OWASP dependency-check.

**Como obtenerla:** registrarse en `https://nvd.nist.gov/developers/request-an-api-key` (email + verificacion).

**Como configurarla:** nombre `NVD_API_KEY`, pegar el key.

## Variables (no secrets)

`https://github.com/ahincho/nova-java-10-api-standard-quarkus-extension/settings/variables/actions`

### `NOVA_PACKAGE_VISIBILITY` (OPTIONAL)

Define si los paquetes Maven publicados van como `public` o `private`. Si no se define, default = `public`.

**Valores validos:** `public`, `private`.

**Reglas de validacion** (workflow `publish-on-tag.yml` falla si hay inconsistencia):
- repo `public` + package `private` → **ERROR** (no se permite)
- repo `private` + package `public` → **ERROR** (no se permite)
- repo `public` + package `public` → OK
- repo `private` + package `private` → OK

Este repo es `public`, asi que el default `public` es correcto. No requiere configuracion.

## Checklist de primer setup

```
[ ] NOVA_RELEASE_PAT configured
[ ] (Optional) NOVA_PACKAGES_READ_TOKEN configured
[ ] (Optional) NVD_API_KEY configured
[ ] (Optional) NOVA_PACKAGE_VISIBILITY variable set
[ ] Hacer un commit trivial para triggerear release-please
[ ] Verificar que se abre PR "chore(main): release 1.0.0"
[ ] Mergear el PR de release
[ ] Verificar que se crea el tag v1.0.0
[ ] Verificar que publish-on-tag.yml se ejecuta y publica el JAR
```

## Troubleshooting

### "GitHub Actions is not permitted to create or approve pull requests"

→ Falta `NOVA_RELEASE_PAT` o el token no tiene scope `pull-requests:write`.

### "Could not HEAD ... Received status code 401" en jobs de SBOM/OWASP

→ Falta `NOVA_PACKAGES_READ_TOKEN` (o `NOVA_RELEASE_PAT` como fallback).

### OWASP tarda mucho (>10 min)

→ Falta `NVD_API_KEY`. Sin el, NIST limita a ~5 requests/30s sin auth.

### Release-please abre PR con version incorrecta

→ Revisar `.release-please-manifest.json` y `.release-please-config.json`. El manifest tiene la "version actual" conocida; el primer PR intenta la primera release >= esa.

### Tag creado pero publish-on-tag no se dispara

→ `GITHUB_TOKEN` automatico no triggerea workflows cuando lo usa release-please. Por eso usamos `NOVA_RELEASE_PAT`.