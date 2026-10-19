# polyglot

`polyglot` is a small Kotlin localization library focused on strongly typed translations, explicit locale handling and simple integration with or without UI frameworks.

The project is built as Kotlin Multiplatform. The core API is available from `commonMain`, and JVM-specific locale interop is provided separately for JVM consumers.

## Why use it

- Strongly typed translations instead of string-key lookups spread across the codebase.
- Translations can stay close to the module, feature or package that owns them.
- Lazy translation registration and resolution.
- Fallback handling for language and regional locales.
- Mapper support for deriving one translation model from another.
- Usable in Compose applications, backend services, desktop tools and command line applications.
- No dependency on a typical UI stack in `polyglot-core`.
- Hierarchical translation composition from leaf modules up to the application root.

One of the main advantages of the library is that the core module works equally well in simple command line applications and in full UI applications. You can keep one localization model across both environments and add UI integration only where it is actually needed.

Another important advantage is the ownership model. A feature can define and keep its own translation classes and bundles locally, and the application can later assemble them into one resolver through aggregate trees. That keeps translations close to the code that actually uses them instead of forcing one global file or one central registry with unrelated strings mixed together.

## Modules

- `polyglot-core`: translation registry, bundles, mappers, locale model and resolver.
- `polyglot-compose`: Compose integration built on top of `polyglot-core`.

## Installation

Add Maven Central to your repositories if it is not already configured:

```kotlin
repositories {
    mavenCentral()
}
```

Add the dependency you need:

```kotlin
dependencies {
    implementation("net.igsoft.polyglot:polyglot-core:<version>")
}
```

For Jetpack Compose or Compose Multiplatform integration:

```kotlin
dependencies {
    implementation("net.igsoft.polyglot:polyglot-compose:<version>")
}
```

## Core concepts

`polyglot-core` is built around a few small concepts:

- `MsgLocale`: normalized locale value object based on language tags.
- `MsgLocales`: predefined language, regional and script locales such as `EN`, `EN_US`, `PT_BR` and `ZH_HANT_TW`.
- `MsgDefinition`: common type for bundles and aggregates accepted by the registry.
- `MsgBundle`: translations for one locale.
- `MsgTranslations`: local collector of the models created by a bundle.
- `MsgRegistration`: local collector of definitions and mapper rules declared by an aggregate.
- `MsgAggregate`: hierarchical grouping of bundles, nested aggregates and mapper rules.
- `PolyglotRegistry`: registration and resolution backend.
- `PolyglotResolver`: application-facing translation resolver with locale-aware caching.

The library resolves translation objects by Kotlin type, not by string key.

## Using `polyglot-core`

Define your translation models as regular Kotlin classes:

```kotlin
data class GreetingStrings(
    val hello: String,
    val goodbye: String,
)
```

Register locale bundles:

```kotlin
import net.igsoft.polyglot.core.MsgBundle
import net.igsoft.polyglot.core.MsgLocale
import net.igsoft.polyglot.core.MsgLocales
import net.igsoft.polyglot.core.MsgTranslations

class GreetingBundleEn : MsgBundle() {
    override val locale: MsgLocale = MsgLocales.EN

    override fun register(translations: MsgTranslations) {
        translations.add(
            GreetingStrings(
                hello = "Hello",
                goodbye = "Goodbye",
            )
        )
    }
}
```

Resolve translations through `PolyglotResolver`:

```kotlin
import net.igsoft.polyglot.core.PolyglotRegistry
import net.igsoft.polyglot.core.PolyglotResolver

val resolver = PolyglotResolver(
    PolyglotRegistry(
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
- group related bundles in a local `MsgAggregate`,
- compose higher-level aggregates until the application root,
- create one `PolyglotRegistry` from top-level aggregates.

This is especially useful in larger applications because it avoids one giant translation file or one giant registration object.

Example:

```kotlin
data class SearchStrings(
    val placeholder: String,
    val clear: String,
)

class SearchBundleEn : MsgBundle() {
    override val locale: MsgLocale = MsgLocales.EN

    override fun register(translations: MsgTranslations) {
        translations.add(
            SearchStrings(
                placeholder = "Search",
                clear = "Clear",
            )
        )
    }
}

class SearchBundlePl : MsgBundle() {
    override val locale: MsgLocale = MsgLocales.PL

    override fun register(translations: MsgTranslations) {
        translations.add(
            SearchStrings(
                placeholder = "Szukaj",
                clear = "Wyczyść",
            )
        )
    }
}

class SearchAggregate : MsgAggregate() {
    override fun register(registration: MsgRegistration) {
        registration.registerBundle(SearchBundleEn())
        registration.registerBundle(SearchBundlePl())
    }
}

class AppAggregate : MsgAggregate() {
    override fun register(registration: MsgRegistration) {
        registration.registerBundle(SearchAggregate())
    }
}

val resolver = PolyglotResolver(
    PolyglotRegistry(
        listOf(AppAggregate())
    )
)
```

That pattern scales from a tiny CLI tool to a large modular app.

## Detailed API behavior

### `MsgLocale`

Use `MsgLocale.of(...)` to create normalized locales from a language tag string:

```kotlin
val localeA = MsgLocale.of("pl-PL")
```

Behavior:

- trims and validates the input language tag,
- normalizes language, script and region casing,
- strips locale extensions,
- accepts well-formed non-standard tags,
- exposes the normalized tag through `languageTag`.

JVM interop helpers:

```kotlin
import java.util.Locale

val localeB = MsgLocale.of(Locale.US)
val msgLocale = Locale.GERMANY.toMsgLocale()
val javaLocale = msgLocale.toJavaLocale()
```

### `MsgLocales`

Use predefined values for common languages, regions and scripts:

```kotlin
val portuguese = MsgLocales.PT
val brazilianPortuguese = MsgLocales.PT_BR
val traditionalChineseTaiwan = MsgLocales.ZH_HANT_TW
```

- values are initialized once when `MsgLocales` is first used; subsequent reads reuse the normalized tags,
- language, region and script values remain distinct: `PT`, `PT_PT` and `PT_BR` represent different locales,
- predefined values and `MsgLocale.of(...)` compare by normalized tag and work interchangeably as registry and cache keys,
- predefined values do not register translations or add entries to `registeredLocales()`,
- use `MsgLocale.of(...)` for tags outside the predefined catalog.

### `MsgBundle`

`MsgBundle` defines translations for a single locale.

- one bundle instance has exactly one `locale`,
- implement `register(translations: MsgTranslations)` to collect the bundle's models,
- `translations.add(instance)` registers one translation object under its Kotlin type,
- `translations.add<T>(instance)` registers a model under an explicitly selected type,
- `translations.add(instance, clazz)` lets you register it explicitly under a selected type.

Create fresh models inside `register()` and register the same translation types on each invocation.
The registry invokes this method lazily and publishes the collected models after it returns successfully.
It retains the bundle and calls the same method again when released models are requested.
Keep models and the collector local to the invocation so the bundle does not retain released translations.

This makes bundles a good fit for leaf-level module translations.

### `MsgAggregate`

`MsgAggregate` is the mechanism for hierarchical composition.

- it can register bundles,
- it can register other aggregates,
- it can declare base conversions, regional inheritance and application overrides,
- it does not represent one locale by itself,
- it is the main tool for building module-level and app-level translation trees.

Implement `register(registration: MsgRegistration)` and add definitions with `registration.registerBundle(...)`.
Bundles and aggregates implement `MsgDefinition`; use this type for mixed collections of definitions.
The registry applies the collected declarations after the entire aggregate tree registers successfully and mapper duplicates are validated.
Bundle models remain lazy. Keep the collector local to `register()`; later additions do not update the registry.

### Mapper rules

Declare base conversions `(S) -> T` and application overrides `(S, T) -> T` through
`MsgRegistration.registerMapper(...)` inside `MsgAggregate.register()`.

| Operation | Declaration | Use |
| --- | --- | --- |
| Base conversion | `registration.registerMapper<S, T> { source -> ... }` | Build a complete `T` from another model; a bundle for `T` is optional. |
| Regional inheritance | `registration.registerMapper<T>(locale = ..., sourceLocale = ...) { base -> ... }` | Build a regional base by copying selected fields from another locale. |
| Application override | `registration.registerMapper<S, T> { source, target -> ... }` | Copy selected fields into an existing `T`, preserving its other translations. |

Both `MsgRegistration` and `PolyglotRegistry` provide these generic overloads and equivalent overloads accepting `KClass` values.

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

class SearchFieldMapper : MsgAggregate() {
    override fun register(registration: MsgRegistration) {
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

class AccountBundlePt : MsgBundle() {
    override val locale = MsgLocales.PT

    override fun register(translations: MsgTranslations) {
        translations.add(
            AccountStrings(
                phone = "Telemóvel",
                save = "Guardar",
                cancel = "Cancelar",
            )
        )
    }
}

class AccountMapper : MsgAggregate() {
    override fun register(registration: MsgRegistration) {
        registration.registerMapper<AccountStrings>(
            locale = MsgLocales.PT_BR,
            sourceLocale = MsgLocales.PT,
        ) {
            it.copy(
                phone = "Celular",
                save = "Salvar",
            )
        }
    }
}

val resolver = PolyglotResolver(
    PolyglotRegistry(listOf(AccountBundlePt(), AccountMapper()))
)
val strings = resolver.getTranslationClass<AccountStrings>(MsgLocales.PT_BR)
// AccountStrings(phone=Celular, save=Salvar, cancel=Cancelar)
```

Here, `locale = MsgLocales.PT_BR` selects the regional target and `sourceLocale = MsgLocales.PT` selects its base.
Specify both for same-type regional inheritance so the mapper reads the base locale explicitly.

Use a two-argument mapper to override selected fields of an existing component translation.
For an application-wide override, omit both locale parameters: the mapper applies globally and its source follows
the original request's full fallback chain. Continuing the account example, register the shared save label for `pt`:

```kotlin
data class CommonActionStrings(
    val save: String,
)

class CommonActionBundlePt : MsgBundle() {
    override val locale = MsgLocales.PT

    override fun register(translations: MsgTranslations) {
        translations.add(CommonActionStrings(save = "OK"))
    }
}

class AccountApplicationMapper : MsgAggregate() {
    override fun register(registration: MsgRegistration) {
        registration.registerMapper<CommonActionStrings, AccountStrings> { common, account ->
            account.copy(save = common.save)
        }
    }
}

val applicationResolver = PolyglotResolver(
    PolyglotRegistry(
        listOf(
            AccountBundlePt(),
            AccountMapper(),
            CommonActionBundlePt(),
            AccountApplicationMapper(),
        )
    )
)
val applicationStrings = applicationResolver.getTranslationClass<AccountStrings>(MsgLocales.PT_BR)
// AccountStrings(phone=Celular, save=OK, cancel=Cancelar)
```

The component's `pt-BR` base supplies `Celular` and `Salvar`. The application override resolves its source
independently through `pt-BR -> pt -> en`, finds `OK` in `pt` and changes only `save`.
The regional phone label and inherited cancel label retain their values.

To limit an application override to `pt-BR`, specify only `locale` inside `MsgAggregate.register()`:

```kotlin
registration.registerMapper<CommonActionStrings, AccountStrings>(
    locale = MsgLocales.PT_BR,
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

Source bundles are registered lazily, including sources outside the requested locale's fallback chain.
Keep transformations immutable, for example with `copy()`, so the base translation remains unchanged.
Localized mapper target locales are included in `registeredLocales()`.

### `PolyglotRegistry`

`PolyglotRegistry` stores bundles and mappers and resolves translation classes internally.

Key behaviors:

- accepts top-level `MsgDefinition` values in the constructor,
- supports incremental registration through `registerBundle(...)`,
- supports direct factories through `registerTranslationClass<T>(locale) { ... }` or the overload accepting `KClass<T>`,
- keeps locale bundles pending and registers them lazily on first use,
- retains each effective bundle translation's owner so released models can be rebuilt without changing registration precedence,
- builds translation bases before applying global and localized application overrides,
- resolves exact locale first and then walks fallback locales such as `pl-PL -> pl`,
- keeps track of known locales through `registeredLocales()`.

### `PolyglotResolver`

`PolyglotResolver` is the main entry point used by applications.

```kotlin
val resolver = PolyglotResolver(PolyglotRegistry(listOf(AppAggregate())))

resolver.setCurrentLocale(MsgLocales.PL)

val stringsA = resolver.getTranslationClass<SearchStrings>()
val stringsB = resolver.getTranslationClass<SearchStrings>(MsgLocales.EN_US)
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

## Example: command line application

Because `polyglot-core` has no UI dependency, it can be used directly in small console tools:

```kotlin
fun main() {
    val resolver = PolyglotResolver(
        PolyglotRegistry(
            listOf(AppAggregate())
        )
    )

    resolver.setCurrentLocale(MsgLocale.of("pl-PL"))

    val strings = resolver.getTranslationClass<SearchStrings>()
    println(strings.placeholder)
}
```

This is one of the main strengths of the library: the same translation model can be reused in CLI tools, background jobs, backend services and UI applications.

## Using `polyglot-compose`

Wrap your UI with `AppLocalizationProvider` and resolve typed translations with `rememberTranslations()`:

```kotlin
import androidx.compose.runtime.Composable
import net.igsoft.polyglot.core.PolyglotRegistry
import net.igsoft.polyglot.core.PolyglotResolver
import net.igsoft.polyglot.compose.AppLocalizationProvider
import net.igsoft.polyglot.compose.rememberTranslations

@Composable
fun App() {
    val resolver = PolyglotResolver(
        PolyglotRegistry(
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
- `LocalLocale` exposes the active `MsgLocale`,
- `LocalSetLocale` lets the UI switch locale with `MsgLocale`,
- `LocalPolyglotResolver` exposes the resolver to nested composables.

This keeps the UI integration thin. Locale state lives in Compose, but translation ownership and registration still stay in `polyglot-core`.
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

