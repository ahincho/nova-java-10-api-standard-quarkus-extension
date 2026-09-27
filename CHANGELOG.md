# Changelog

## [1.2.0](https://github.com/ahincho/nova-java-10-api-standard-quarkus-extension/compare/v1.1.1...v1.2.0) (2026-09-27)


### Features

* initial implementation of Quarkus extension for nova-api-standard ([a3e5bc2](https://github.com/ahincho/nova-java-10-api-standard-quarkus-extension/commit/a3e5bc2621b0de9a77b2d9aa8337756c8bd94985))


### Bug Fixes

* **build:** propagate nova-api-standard as api dep + suppress enforcedPlatform warning ([7721d3e](https://github.com/ahincho/nova-java-10-api-standard-quarkus-extension/commit/7721d3e49532f00b93952b087d8b392c9a268517))
* **ci:** apply checkstyle plugin (was missing, made checkstyleMain task fail) ([a694b60](https://github.com/ahincho/nova-java-10-api-standard-quarkus-extension/commit/a694b6016bc84ab1a8a2c258c2a13fb3e305571f))
* **ci:** grant contents:write to sbom job ([71bfe42](https://github.com/ahincho/nova-java-10-api-standard-quarkus-extension/commit/71bfe42086970023e236fb5399f9277b9f2a76d6))
* **ci:** inject NOVA_PACKAGES_READ_TOKEN into Publish to GitHub Packages step ([fb3c0e5](https://github.com/ahincho/nova-java-10-api-standard-quarkus-extension/commit/fb3c0e57264e61665b7f09e2f5ff37c0c323fdce))
* **ci:** read nova-api-standard from its own repo + accept NOVA_PACKAGES_READ_TOKEN ([756e4a7](https://github.com/ahincho/nova-java-10-api-standard-quarkus-extension/commit/756e4a77b1cc7317d5787fbfecd81a60157009db))
* **ci:** restore the build plugins and move to Quarkus 3.33.3.3 ([dadfbb8](https://github.com/ahincho/nova-java-10-api-standard-quarkus-extension/commit/dadfbb8820acb6f338330ea4025a65f45bc51ebc))
* **deps:** move to Quarkus 3.33.3.3 and nova-api-standard 1.0.2 ([f5df009](https://github.com/ahincho/nova-java-10-api-standard-quarkus-extension/commit/f5df009b0470179dd9ec9f5b8f4389e21b910b91))
* **discovery:** generate META-INF/jandex.idx so Quarkus finds @ServerExceptionMapper ([276a7d5](https://github.com/ahincho/nova-java-10-api-standard-quarkus-extension/commit/276a7d574e0df1c1059dc883fb84ffb7bed93889))
* **owasp:** use NVD mirror + autoUpdate=false to skip full NVD sync ([c0d07a7](https://github.com/ahincho/nova-java-10-api-standard-quarkus-extension/commit/c0d07a76d2cdff04ab8f9790a4afd59e5d77a7df))
* **publish:** add signing plugin to force POM signature flush ([479d9f0](https://github.com/ahincho/nova-java-10-api-standard-quarkus-extension/commit/479d9f0a06835e557d46d810ad626914e16a45ea))
* **publish:** isolate culprit by removing extra plugins ([387880a](https://github.com/ahincho/nova-java-10-api-standard-quarkus-extension/commit/387880aff72bdf5e92ccf1f27ea66f2d9b404422))
* **publish:** minimal config to isolate ghost package bug ([8be482c](https://github.com/ahincho/nova-java-10-api-standard-quarkus-extension/commit/8be482c9e101abe1b816173add39500c8347fa30))
* **publish:** rename artifactId to nova-quarkus-api-ext to avoid GH Packages ghost publish ([39adf86](https://github.com/ahincho/nova-java-10-api-standard-quarkus-extension/commit/39adf862ee92cffa2d0e475a819185eefef4ac12))
* **quarkus:** use @ServerExceptionMapper instead of ExceptionMapper&lt;Throwable&gt; ([37bd083](https://github.com/ahincho/nova-java-10-api-standard-quarkus-extension/commit/37bd083e4f6e711868217ae71088a4f431b0f5f7))


### Documentation

* add SECRETS_SETUP.md checklist for GitHub Actions secrets ([e5e5dac](https://github.com/ahincho/nova-java-10-api-standard-quarkus-extension/commit/e5e5dac3a01099c1cb8e3242477c16e7a2bef57d))
* adopt EPL-2.0 ([bc339a9](https://github.com/ahincho/nova-java-10-api-standard-quarkus-extension/commit/bc339a962b325cb15117db2602d7f55639b2ed54))

## [1.1.1](https://github.com/ahincho/nova-java-api-standard-quarkus-extension/compare/v1.1.0...v1.1.1) (2026-07-14)


### Bug Fixes

* **ci:** apply checkstyle plugin (was missing, made checkstyleMain task fail) ([a694b60](https://github.com/ahincho/nova-java-api-standard-quarkus-extension/commit/a694b6016bc84ab1a8a2c258c2a13fb3e305571f))
* **ci:** read nova-api-standard from its own repo + accept NOVA_PACKAGES_READ_TOKEN ([756e4a7](https://github.com/ahincho/nova-java-api-standard-quarkus-extension/commit/756e4a77b1cc7317d5787fbfecd81a60157009db))
* **quarkus:** use @ServerExceptionMapper instead of ExceptionMapper&lt;Throwable&gt; ([37bd083](https://github.com/ahincho/nova-java-api-standard-quarkus-extension/commit/37bd083e4f6e711868217ae71088a4f431b0f5f7))

## [1.1.0](https://github.com/ahincho/nova-java-api-standard-quarkus-extension/compare/v1.0.0...v1.1.0) (2026-07-14)


### Features

* initial implementation of Quarkus extension for nova-api-standard ([a3e5bc2](https://github.com/ahincho/nova-java-api-standard-quarkus-extension/commit/a3e5bc2621b0de9a77b2d9aa8337756c8bd94985))


### Bug Fixes

* **build:** propagate nova-api-standard as api dep + suppress enforcedPlatform warning ([7721d3e](https://github.com/ahincho/nova-java-api-standard-quarkus-extension/commit/7721d3e49532f00b93952b087d8b392c9a268517))
* **ci:** grant contents:write to sbom job ([71bfe42](https://github.com/ahincho/nova-java-api-standard-quarkus-extension/commit/71bfe42086970023e236fb5399f9277b9f2a76d6))


### Documentation

* add SECRETS_SETUP.md checklist for GitHub Actions secrets ([e5e5dac](https://github.com/ahincho/nova-java-api-standard-quarkus-extension/commit/e5e5dac3a01099c1cb8e3242477c16e7a2bef57d))
