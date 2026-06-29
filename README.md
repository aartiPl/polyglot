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
- `MsgLocales`: a few predefined locales such as `ENGLISH`, `US`, `POLISH` and `POLISH_POLAND`.
- `MsgBundle`: translations for one locale.
- `MsgAggregate`: hierarchical grouping of bundles and nested aggregates.
- `MsgMapper`: mapping from one translation type to another.
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

class GreetingBundleEn : MsgBundle() {
    override val locale: MsgLocale = MsgLocales.EN

    override fun register() {
        add(
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

val strings = resolver.getTranslationClass(GreetingStrings::class)
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

    override fun register() {
        add(
            SearchStrings(
                placeholder = "Search",
                clear = "Clear",
            )
        )
    }
}

class SearchBundlePl : MsgBundle() {
    override val locale: MsgLocale = MsgLocales.PL

    override fun register() {
        add(
            SearchStrings(
                placeholder = "Szukaj",
                clear = "Wyczyść",
            )
        )
    }
}

class SearchAggregate : MsgAggregate() {
    override fun register() {
        addBundle(SearchBundleEn())
        addBundle(SearchBundlePl())
    }
}

class AppAggregate : MsgAggregate() {
    override fun register() {
        addBundle(SearchAggregate())
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

### `MsgBundle`

`MsgBundle` defines translations for a single locale.

- one bundle instance has exactly one `locale`,
- `add(instance)` registers one translation object under its Kotlin type,
- `add(instance, clazz)` lets you register it explicitly under a selected type.

This makes bundles a good fit for leaf-level module translations.

### `MsgAggregate`

`MsgAggregate` is the mechanism for hierarchical composition.

- it can register bundles,
- it can register other aggregates,
- it does not represent one locale by itself,
- it is the main tool for building module-level and app-level translation trees.

### `MsgMapper`

`MsgMapper` lets you derive one translation model from another:

```kotlin
data class CommonStrings(
    val clear: String,
    val back: String,
)

data class SearchFieldStrings(
    val clear: String,
    val back: String,
)

class SearchFieldMapper : MsgMapper() {
    override fun register() {
        add<CommonStrings, SearchFieldStrings> { strings ->
            SearchFieldStrings(
                clear = strings.clear,
                back = strings.back,
            )
        }
    }
}
```

This is useful when one feature-specific translation model can be built from a shared common translation model.

Behavior:

- the mapper is registered for a target type,
- when a source translation is available for the resolved locale, the mapper is preferred,
- direct bundle translations are used when the mapper can not resolve the target type,
- cyclic mapper chains are stopped and reported as missing translations.

### `PolyglotRegistry`

`PolyglotRegistry` stores bundles and mappers and resolves translation classes internally.

Key behaviors:

- accepts top-level aggregates in the constructor,
- supports incremental registration through `registerBundle(...)`,
- keeps locale bundles pending and registers them lazily on first use,
- prefers mapped translations over direct bundle translations when a mapper can resolve the target type,
- resolves exact locale first and then walks fallback locales such as `pl-PL -> pl`,
- keeps track of known locales through `registeredLocales()`.

### `PolyglotResolver`

`PolyglotResolver` is the main entry point used by applications.

```kotlin
val resolver = PolyglotResolver(PolyglotRegistry(listOf(AppAggregate())))

resolver.setCurrentLocale(MsgLocales.PL)

val stringsA = resolver.getTranslationClass(SearchStrings::class)
val stringsB = resolver.getTranslationClass(SearchStrings::class, MsgLocales.EN_US)
val locales = resolver.registeredLocales()
```

Behavior:

- `getTranslationClass(clazz)` resolves using the current locale,
- `getTranslationClass(clazz, locale)` resolves using an explicit locale,
- default fallback locale is English,
- results are cached by locale and translation type,
- changing the current locale with `setCurrentLocale(...)` clears the cache,
- missing translations throw a detailed error including the locale chain and requested type.

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

    val strings = resolver.getTranslationClass(SearchStrings::class)
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
- `LocalLocale` exposes the active `MsgLocale`,
- `LocalSetLocale` lets the UI switch locale with `MsgLocale`,
- `LocalPolyglotResolver` exposes the resolver to nested composables.

This keeps the UI integration thin. Locale state lives in Compose, but translation ownership and registration still stay in `polyglot-core`.

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

