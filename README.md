# polyglot

`polyglot` is a small Kotlin localization library focused on strongly typed translations, explicit locale handling and simple integration with or without UI frameworks.

The project is built as Kotlin Multiplatform for JVM, Android and iOS. Locale and translation APIs are available from `commonMain`. Java locale conversions are included in the JVM and Android variants of `polyglot-locale`.

## Why use it

- Strongly typed translations instead of string-key lookups spread across the codebase.
- Translations can stay close to the module, feature or package that owns them.
- Lazy translation registration and resolution.
- Fallback handling for language and regional locales.
- Mapper support for deriving one translation model from another.
- Usable in Compose applications, backend services, desktop tools and command line applications.
- No dependency on a typical UI stack in `polyglot-translation`.
- Hierarchical translation composition from leaf modules up to the application root.

The translation module works in command line applications and UI applications. Keep one localization model across both environments and add UI integration where it is needed.

Another important advantage is the ownership model. A feature can define and keep its own translation classes and bundles locally, and the application can later assemble them into one resolver through module trees. That keeps translations close to the code that actually uses them instead of forcing one global file or one central registry with unrelated strings mixed together.

## Modules

| Gradle module and Maven artifact | Main package | Responsibility |
| --- | --- | --- |
| `polyglot-locale` | `net.igsoft.polyglot.locale` | Locale values, predefined tags, normalization and Java locale conversions. |
| `polyglot-translation` | `net.igsoft.polyglot.translation` | Translation definitions, bundles, modules, mapper rules, registry and resolver. |
| `polyglot-translation-compose` | `net.igsoft.polyglot.translation.compose` | Translation providers and lookups for Compose. |

Dependencies are `polyglot-translation-compose` → `polyglot-translation` → `polyglot-locale`.
Each dependency is exposed through `api`, so consumers add only the artifact they need.
Use `polyglot-locale` independently for locale handling without translation or Compose dependencies.

## Installation

Add Maven Central to your repositories if it is not already configured:

```kotlin
repositories {
    mavenCentral()
}
```

Add the dependency you need:

For standalone locale handling:

```kotlin
dependencies {
    implementation("net.igsoft.polyglot:polyglot-locale:<version>")
}
```

For typed translations:

```kotlin
dependencies {
    implementation("net.igsoft.polyglot:polyglot-translation:<version>")
}
```

For Jetpack Compose or Compose Multiplatform integration:

```kotlin
dependencies {
    implementation("net.igsoft.polyglot:polyglot-translation-compose:<version>")
}
```

## Core concepts

`polyglot-locale` provides `Locale` and `Locales`. The remaining types belong to `polyglot-translation`:

- `Locale`: normalized locale value object based on language tags.
- `Locales`: predefined language, regional and script locales such as `EN`, `EN_US`, `PT_BR` and `ZH_HANT_TW`.
- `Definition`: common type for bundles and modules accepted by the registry.
- `Bundle`: translations for one locale.
- `BundleScope`: local collector of the models created by a bundle.
- `ModuleScope`: local collector of definitions and mapper rules declared by a module.
- `Module`: hierarchical grouping of bundles, nested modules and mapper rules.
- `Registry`: registration and resolution backend.
- `Resolver`: application-facing translation resolver with locale-aware caching.

The library resolves translation objects by Kotlin type, not by string key.

## Using `polyglot-translation`

Define your translation models as regular Kotlin classes:

```kotlin
data class GreetingStrings(
    val hello: String,
    val goodbye: String,
)
```

Register locale bundles:

```kotlin
import net.igsoft.polyglot.locale.Locale
import net.igsoft.polyglot.locale.Locales
import net.igsoft.polyglot.translation.Bundle
import net.igsoft.polyglot.translation.BundleScope

class GreetingBundleEn : Bundle() {
    override val locale: Locale = Locales.EN

    override fun register(translations: BundleScope) {
        translations.add(
            GreetingStrings(
                hello = "Hello",
                goodbye = "Goodbye",
            )
        )
    }
}
```

Resolve translations through `Resolver`:

```kotlin
import net.igsoft.polyglot.translation.Registry
import net.igsoft.polyglot.translation.Resolver

val resolver = Resolver(
    Registry(
        listOf(GreetingBundleEn())
    )
)

val strings = resolver.getTranslationClass<GreetingStrings>()
println(strings.hello)
```

This works the same way in a command line app, desktop app, service or Android app.

## Hierarchical translation structure

The intended way to organize translations is hierarchical and local-first:

- define translation classes near the module or feature that owns them,
- define locale bundles near that module,
- group related bundles in a local `Module`,
- compose higher-level modules until the application root,
- create one `Registry` from top-level modules.

This is especially useful in larger applications because it avoids one giant translation file or one giant registration object.

Example:

```kotlin
import net.igsoft.polyglot.locale.Locale
import net.igsoft.polyglot.locale.Locales
import net.igsoft.polyglot.translation.Bundle
import net.igsoft.polyglot.translation.BundleScope
import net.igsoft.polyglot.translation.Module
import net.igsoft.polyglot.translation.ModuleScope
import net.igsoft.polyglot.translation.Registry
import net.igsoft.polyglot.translation.Resolver

data class SearchStrings(
    val placeholder: String,
    val clear: String,
)

class SearchBundleEn : Bundle() {
    override val locale: Locale = Locales.EN

    override fun register(translations: BundleScope) {
        translations.add(
            SearchStrings(
                placeholder = "Search",
                clear = "Clear",
            )
        )
    }
}

class SearchBundlePl : Bundle() {
    override val locale: Locale = Locales.PL

    override fun register(translations: BundleScope) {
        translations.add(
            SearchStrings(
                placeholder = "Szukaj",
                clear = "Wyczyść",
            )
        )
    }
}

class SearchModule : Module() {
    override fun register(registration: ModuleScope) {
        registration.registerDefinition(SearchBundleEn())
        registration.registerDefinition(SearchBundlePl())
    }
}

class AppModule : Module() {
    override fun register(registration: ModuleScope) {
        registration.registerDefinition(SearchModule())
    }
}

val resolver = Resolver(
    Registry(
        listOf(AppModule())
    )
)
```

That pattern scales from a tiny CLI tool to a large modular app.

## Detailed API behavior

### `Locale`

Use `Locale.of(...)` to create normalized locales from a language tag string:

```kotlin
val localeA = Locale.of("pl-PL")
```

Behavior:

- trims and validates the input language tag,
- normalizes language, script and region casing,
- strips locale extensions,
- accepts well-formed non-standard tags,
- exposes the normalized tag through `languageTag`.

Java locale interop is included in `polyglot-locale` on JVM and Android. Import the conversion extensions and use an alias to distinguish Java's `Locale`:

```kotlin
import net.igsoft.polyglot.locale.Locale
import net.igsoft.polyglot.locale.of
import net.igsoft.polyglot.locale.toJavaLocale
import net.igsoft.polyglot.locale.toLocale
import java.util.Locale as JavaLocale

val localeB = Locale.of(JavaLocale.US)
val locale = JavaLocale.GERMANY.toLocale()
val javaLocale = locale.toJavaLocale()
```

### `Locales`

Use predefined values for common languages, regions and scripts:

```kotlin
val portuguese = Locales.PT
val brazilianPortuguese = Locales.PT_BR
val traditionalChineseTaiwan = Locales.ZH_HANT_TW
```

- values are initialized once when `Locales` is first used; subsequent reads reuse the normalized tags,
- language, region and script values remain distinct: `PT`, `PT_PT` and `PT_BR` represent different locales,
- predefined values and `Locale.of(...)` compare by normalized tag and work interchangeably as registry and cache keys,
- predefined values do not register translations or add entries to `registeredLocales()`,
- use `Locale.of(...)` for tags outside the predefined catalog.

### `Bundle`

`Bundle` defines translations for a single locale.

- one bundle instance has exactly one `locale`,
- implement `register(translations: BundleScope)` to collect the bundle's models,
- `translations.add(instance)` registers one translation object under its Kotlin type,
- `translations.add<T>(instance)` registers a model under an explicitly selected type,
- `translations.add(instance, clazz)` lets you register it explicitly under a selected type.

Create fresh models inside `register()` and register the same translation types on each invocation.
The registry invokes this method lazily and publishes the collected models after it returns successfully.
It retains the bundle and calls the same method again when released models are requested.
Keep models and the collector local to the invocation so the bundle does not retain released translations.

This makes bundles a good fit for leaf-level module translations.

### `Module`

`Module` is the mechanism for hierarchical composition.

- it can register bundles,
- it can register other modules,
- it can declare base conversions, regional inheritance and application overrides,
- it does not represent one locale by itself,
- it is the main tool for building module-level and app-level translation trees.

Implement `register(registration: ModuleScope)` and add definitions with `registration.registerDefinition(...)`.
Bundles and modules implement `Definition`; use this type for mixed collections of definitions.
The registry applies the collected declarations after the entire module tree registers successfully and mapper duplicates are validated.
Bundle models remain lazy. Keep the collector local to `register()`; later additions do not update the registry.

### Mapper rules

Declare base conversions `(S) -> T` and application overrides `(S, T) -> T` through
`ModuleScope.registerMapper(...)` inside `Module.register()`.

| Operation | Declaration | Use |
| --- | --- | --- |
| Base conversion | `registration.registerMapper<S, T> { source -> ... }` | Build a complete `T` from another model; a bundle for `T` is optional. |
| Regional inheritance | `registration.registerMapper<T>(locale = ..., sourceLocale = ...) { base -> ... }` | Build a regional base by copying selected fields from another locale. |
| Application override | `registration.registerMapper<S, T> { source, target -> ... }` | Copy selected fields into an existing `T`, preserving its other translations. |

Both `ModuleScope` and `Registry` provide these generic overloads and equivalent overloads accepting `KClass` values.

The locale parameters have separate roles:

- `locale` selects the target locale where the mapper participates in resolution; omit it for a global mapper,
- `sourceLocale` selects the exact locale from which to resolve the source translation; set it when the source must come from a specific locale.

Use a base conversion to build a complete translation object from another model:

```kotlin
data class CommonStrings(
    val clear: String,
    val back: String,
)

data class SearchFieldStrings(
    val clear: String,
    val back: String,
)

class SearchFieldMapper : Module() {
    override fun register(registration: ModuleScope) {
        registration.registerMapper<CommonStrings, SearchFieldStrings> { strings ->
            SearchFieldStrings(
                clear = strings.clear,
                back = strings.back,
            )
        }
    }
}
```

This is useful when one feature-specific translation model can be built from a shared common translation model.

Base conversion behavior:

- the mapper is registered for a target type and an optional target `locale`,
- omit `locale` to register a global mapper,
- `sourceLocale` selects the exact locale used to resolve the source; omit it to use the locale currently being resolved,
- for each locale in the fallback chain, base resolution tries the localized mapper, global mapper and direct bundle translation in that order,
- base conversions use source bases without application overrides,
- when a mapper cannot resolve its source, resolution continues with the next candidate,
- cycles fail with an error containing the dependency path.

Use a mapper with the same source and target type to override selected fields for a regional locale:

```kotlin
data class AccountStrings(
    val phone: String,
    val save: String,
    val cancel: String,
)

class AccountBundlePt : Bundle() {
    override val locale = Locales.PT

    override fun register(translations: BundleScope) {
        translations.add(
            AccountStrings(
                phone = "Telemóvel",
                save = "Guardar",
                cancel = "Cancelar",
            )
        )
    }
}

class AccountMapper : Module() {
    override fun register(registration: ModuleScope) {
        registration.registerMapper<AccountStrings>(
            locale = Locales.PT_BR,
            sourceLocale = Locales.PT,
        ) {
            it.copy(
                phone = "Celular",
                save = "Salvar",
            )
        }
    }
}

val resolver = Resolver(
    Registry(listOf(AccountBundlePt(), AccountMapper()))
)
val strings = resolver.getTranslationClass<AccountStrings>(Locales.PT_BR)
// AccountStrings(phone=Celular, save=Salvar, cancel=Cancelar)
```

Here, `locale = Locales.PT_BR` selects the regional target and `sourceLocale = Locales.PT` selects its base.
Specify both for same-type regional inheritance so the mapper reads the base locale explicitly.

Use a two-argument mapper to override selected fields of an existing component translation.
For an application-wide override, omit both locale parameters: the mapper applies globally and its source follows
the original request's full fallback chain. Continuing the account example, register the shared save label for `pt`:

```kotlin
data class CommonActionStrings(
    val save: String,
)

class CommonActionBundlePt : Bundle() {
    override val locale = Locales.PT

    override fun register(translations: BundleScope) {
        translations.add(CommonActionStrings(save = "OK"))
    }
}

class AccountApplicationMapper : Module() {
    override fun register(registration: ModuleScope) {
        registration.registerMapper<CommonActionStrings, AccountStrings> { common, account ->
            account.copy(save = common.save)
        }
    }
}

val applicationResolver = Resolver(
    Registry(
        listOf(
            AccountBundlePt(),
            AccountMapper(),
            CommonActionBundlePt(),
            AccountApplicationMapper(),
        )
    )
)
val applicationStrings = applicationResolver.getTranslationClass<AccountStrings>(Locales.PT_BR)
// AccountStrings(phone=Celular, save=OK, cancel=Cancelar)
```

The component's `pt-BR` base supplies `Celular` and `Salvar`. The application override resolves its source
independently through `pt-BR -> pt -> en`, finds `OK` in `pt` and changes only `save`.
The regional phone label and inherited cancel label retain their values.

To limit an application override to `pt-BR`, specify only `locale` inside `Module.register()`:

```kotlin
registration.registerMapper<CommonActionStrings, AccountStrings>(
    locale = Locales.PT_BR,
) { common, account ->
    account.copy(save = common.save)
}
```

The override participates at `pt-BR` in the target fallback chain. Its source still resolves independently through
the original request's full fallback chain. Leave `sourceLocale` omitted for this behavior; setting it to `PT`
would select exactly `pt` even when `pt-BR` common strings are available.

Application override behavior:

- resolve the component base, including regional inheritance, before applying overrides,
- resolve source translations independently, including their application overrides,
- without `sourceLocale`, resolve the source through the original request's full locale chain, including the default locale,
- with `sourceLocale`, resolve the source for that exact locale,
- apply the global override, then the first applicable localized override in the request's fallback chain,
- pass the global override's result to the localized override; apply each at most once per target resolution,
- skip an override when its source is missing; a missing component base remains a missing translation error,
- register at most one mapper per role (base or override), target type and locale; duplicate registrations fail.

Duplicate mapper errors include both registration paths, for example:

```text
Duplicate base mapper [translationClass='AccountStrings', locale='global'] [existing='Registry/SharedModule#1/base#1', incoming='Registry/AppModule#2/SharedModule#1/base#1']
```

Paths identify the complete module hierarchy. Numbers identify declaration attempts within the owning scope,
including failed attempts. Anonymous definitions use `Module` or `Bundle` as their name.
Direct registry mappers use `Registry/mapper#N/base#1` or `Registry/mapper#N/override#1`.
Registration validates conflicts before publishing the collected declarations; a rejected module leaves existing
registrations, cached results and the registry revision unchanged.

Source bundles are registered lazily, including sources outside the requested locale's fallback chain.
Keep transformations immutable, for example with `copy()`, so the base translation remains unchanged.
Localized mapper target locales are included in `registeredLocales()`.

### `Registry`

`Registry` stores bundles and mappers and resolves translation classes internally.

Key behaviors:

- accepts top-level `Definition` values in the constructor,
- supports incremental registration through `registerDefinition(...)`,
- supports direct factories through `registerTranslationClass<T>(locale) { ... }` or the overload accepting `KClass<T>`,
- keeps locale bundles pending and registers them lazily on first use,
- retains each effective bundle translation's owner so released models can be rebuilt without changing registration precedence,
- builds translation bases before applying global and localized application overrides,
- resolves exact locale first and then walks fallback locales such as `pl-PL -> pl`,
- keeps track of known locales through `registeredLocales()`.

### `Resolver`

`Resolver` is the main entry point used by applications.

```kotlin
val resolver = Resolver(Registry(listOf(AppModule())))

resolver.setCurrentLocale(Locales.PL)

val stringsA = resolver.getTranslationClass<SearchStrings>()
val stringsB = resolver.getTranslationClass<SearchStrings>(Locales.EN_US)
val locales = resolver.registeredLocales()
```

Behavior:

- `getTranslationClass<T>()` resolves using the current locale,
- `getTranslationClass<T>(locale)` resolves using an explicit locale,
- equivalent overloads accept `KClass<T>` instead of reified type parameters,
- default fallback locale is English,
- final results are cached by requested locale and translation type,
- registry changes invalidate cached results on the next lookup, including changes to mapper sources,
- changing the current locale with `setCurrentLocale(...)` clears this resolver's cache and releases all bundle model instances held by the registry, including mapper sources,
- the next lookup rebuilds each required bundle once; subsequent lookups reuse its models,
- setting the already active locale preserves the cache and bundle models,
- missing translations throw a detailed error including the locale chain and requested type.

Releasing bundle models preserves translation definitions, mapper definitions and directly registered factories.
Other resolvers sharing the registry retain their cached results until their own cache is invalidated.
Models still referenced by application code or another resolver remain reachable; GC can reclaim them after those references are released.

### Translation diagnostics

Use `DiagnosticResolver` when translation provenance is needed. Pass its `resolver` to the application,
including `AppLocalizationProvider`, so ordinary and diagnostic lookups share the same cache:

```kotlin
import net.igsoft.polyglot.translation.DiagnosticResolver

val diagnostics = DiagnosticResolver(Registry(listOf(AppModule())))
val resolver = diagnostics.resolver
resolver.setCurrentLocale(Locales.PT_BR)

val strings = resolver.getTranslationClass<AccountStrings>()
val result = diagnostics.resolve<AccountStrings>()
check(result.value === strings)
check(result.diagnostics.cacheHit)

val explicit = diagnostics.resolve(AccountStrings::class, Locales.PT_BR)
println(explicit.diagnostics)
```

`resolve<T>()` uses the resolver's current locale; `resolve<T>(locale)` uses an explicit locale.
Both have equivalents accepting `KClass<T>`. Each returns `TranslationResolution<T>` containing the actual
`value` and a `TranslationDiagnostics` snapshot:

- `requestedClass`, `requestedLocale` and `fallbackChain` identify the request and ordered fallback candidates,
- `cacheHit` identifies whether this lookup reused a final model from this resolver's cache,
- `usesEnglishFallback` identifies selection of the default `en` candidate for the target or an implicit override source,
- `origin` describes the successful dependency tree through the sealed `TranslationOrigin` type.

The origin variants are:

| Variant | Provenance |
| --- | --- |
| `BundleOrigin` | Model type, actual locale, bundle class and registration path. |
| `FactoryOrigin` | Model type, actual locale and direct factory registration path. |
| `BaseMapperOrigin` | Target model type, `resolvedLocale`, declared target `locale` (`null` for global), declared `sourceLocale`, registration path and resolved `source`. |
| `OverrideOrigin` | Target model type, declared target `locale`, declared `sourceLocale`, registration path, previous `target` and independently resolved `source`. |

For regional inheritance the tree preserves both locales:

```text
requested: AccountStrings, pt-BR; fallbackChain: [pt-BR, pt, en]
BaseMapperOrigin(locale=pt-BR, resolvedLocale=pt-BR, sourceLocale=pt)
└─ BundleOrigin(locale=pt, bundleClass=AccountBundlePt)
```

An override adds separate `target` and `source` branches. Nested overrides remain in those branches.
Base conversions retain source bases without application overrides, following ordinary resolution semantics.
Explicit `sourceLocale` selects exactly that locale, including sources outside `fallbackChain`.
An explicit English source and language fallback `en-US -> en` do not set `usesEnglishFallback`.
Sources outside the request chain remain visible in the tree.

Diagnostics use the ordinary resolution algorithm and record provenance during the lookup that creates the value.
Cache hits reuse its recorded origin without invoking bundles, factories or mappers again.
Registry changes invalidate values and provenance together on the next lookup. Changing the current locale clears
both caches and releases bundle models under the ordinary lifecycle rules. Other resolvers sharing the registry
retain their own cached values and provenance. Previously returned reports remain historical snapshots.

`Resolver(registry)` allocates no diagnostic cache, traces or origin trees. The shared resolution path checks for
an optional trace. Registration paths are retained by every registry to explain duplicate mapper errors.
`DiagnosticResolver` adds trace work on cache misses and retains an origin tree per cached result, proportional
to its successful dependency occurrences. Diagnostic cache hits allocate only the result and report wrappers;
they reuse the origin tree. Reports retain class identifiers, locale values and paths, without retaining model
instances, definition instances or registration callbacks. `TranslationResolution.value` retains the returned model
like an ordinary lookup result.

Reports describe applied transformations, rather than all rejected candidates or individual field values.
Missing translations, cycles and callback failures retain ordinary exception behavior; failed lookups are not cached.
Create a `DiagnosticResolver` before reproducing an issue: provenance cannot be reconstructed from a cache
created by an independent ordinary resolver.

### Registration API migration

Replace `Registry.registerBundle(definition)` and `ModuleScope.registerBundle(definition)` with
`registerDefinition(definition)`, including callable references such as `registration::registerDefinition`.
The parameter remains `Definition` and accepts both bundles and modules. `BundleScope.add(...)`, constructor
registration, mapper precedence, fallback rules and Compose APIs keep their existing contracts.
The old method is removed without a compatibility wrapper; consumers must recompile against the updated API.
The working version remains `0.4.0` for local development; publish these incompatible changes under a new version.

## Example: command line application

Because `polyglot-translation` has no UI dependency, it can be used directly in small console tools:

```kotlin
fun main() {
    val resolver = Resolver(
        Registry(
            listOf(AppModule())
        )
    )

    resolver.setCurrentLocale(Locale.of("pl-PL"))

    val strings = resolver.getTranslationClass<SearchStrings>()
    println(strings.placeholder)
}
```

This is one of the main strengths of the library: the same translation model can be reused in CLI tools, background jobs, backend services and UI applications.

## Using `polyglot-translation-compose`

Wrap your UI with `AppLocalizationProvider` and resolve typed translations with `rememberTranslations()`:

```kotlin
import androidx.compose.runtime.Composable
import net.igsoft.polyglot.translation.Registry
import net.igsoft.polyglot.translation.Resolver
import net.igsoft.polyglot.translation.compose.AppLocalizationProvider
import net.igsoft.polyglot.translation.compose.rememberTranslations

@Composable
fun App() {
    val resolver = Resolver(
        Registry(
            listOf(GreetingBundleEn())
        )
    )

    AppLocalizationProvider(polyglotResolver = resolver) {
        val strings = rememberTranslations<GreetingStrings>()
        GreetingScreen(strings)
    }
}
```

Compose API:

- `AppLocalizationProvider(polyglotResolver, initialLocale, content)` uses an explicit resolver,
- `rememberTranslations<MyStrings>()` resolves typed translations from the current composition,
- `rememberTranslations<MyStrings>(polyglotResolver)` uses an explicit resolver and the composition locale; both forms also accept `KClass<MyStrings>`,
- translation lookups use `LocalLocale` explicitly and reuse the resolver's revision-aware cache,
- `LocalLocale` exposes the active `Locale`,
- `LocalSetLocale` lets the UI switch locale with `Locale`,
- `LocalPolyglotResolver` exposes the resolver to nested composables.

This keeps the UI integration thin. Locale state lives in Compose, but translation ownership and registration still stay in `polyglot-translation`.
After incremental registration, recompose the consuming content to display the updated translations.

## Build

```bash
./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build --console=plain --no-daemon
```

## Publish to Maven Central

The build uses `com.vanniktech.maven.publish` and is configured for Maven Central publication.

Required Gradle properties can be passed through environment variables:

- `ORG_GRADLE_PROJECT_mavenCentralUsername`
- `ORG_GRADLE_PROJECT_mavenCentralPassword`
- `ORG_GRADLE_PROJECT_signingInMemoryKeyId`
- `ORG_GRADLE_PROJECT_signingInMemoryKey`
- `ORG_GRADLE_PROJECT_signingInMemoryKeyPassword`

Publish and release with:

```bash
./gradlew publishAndReleaseToMavenCentral
```

Versions are derived from the current Git branch:

- `main` and `master` publish the base version,
- any other branch uses `-SNAPSHOT`.

