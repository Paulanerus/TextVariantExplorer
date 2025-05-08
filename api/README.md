# API Development

This ReadMe provides an overview of the TextExplorer API and guides you through developing your own plugin. A sample
implementation is available in [plugins/demo](../plugins/demo).

While using a build tool is not mandatory, this documentation employs [Gradle](https://gradle.org/) in its examples to
align with the project's overall setup.

## Environment setup

1. Install Java 21.
2. Create a new Gradle (Kotlin DSL) project.
3. Download the latest [API release](https://github.com/Paulanerus/TextExplorer/releases/latest) and add the JAR as a
   local dependency:

```kotlin
implementation(files("libs/api-1.6.1.jar"))
```

4. Reload the Gradle project.

## First plugin

To create your first plugin, follow these steps:

1. Create a Kotlin class, e.g., `PluginMain`, and implement the `IPlugin` interface:

```kotlin
class PluginMain : IPlugin {
    override fun init(storageProvider: IStorageProvider) {
        println("Hello world from plugin.")
    }
}
```

The `init` function is executed when the application starts or the plugin is loaded via the interface. Use this function
to initialize external services or perform setup tasks. The StorageProvider instance can be used to access imported
data.

2. Annotate the class with `@PluginMetadata` to declare the plugin's metadata. You can include additional information
   like the version, author, or a short description.

3. Optionally, use the `@PluginOrder` annotation to specify the load order for the plugin.

4. Update the Gradle `jar` task to include the plugin's main class in the JAR file manifest:

```kotlin
tasks.jar {
    manifest {
        attributes["Main-Class"] = "PluginMain" // Adjust the path if necessary
    }
}
```

## Provide data

To connect existing data to your plugin, annotate it with `@RequiresData` and specify the identifier of the data pool.
For example, to connect to `demo_data`, annotate the class with:
`@RequiresData("demo_data")`.

**Currently only data in csv-files is supported.**

## Tagging API

The Tagging API allows you to highlight specific words (e.g., names) within the Tagging View.

To implement this functionality, simply implement the tag function from the Taggable interface in your Plugin Main
class.
This function takes the field name and its corresponding value as parameters.

Additionally, annotate the function with `@ViewFilter`, which specifies a filter name and the fields it accepts. The
alwaysShow field can be used to make certain columns always visible
Optionally, you can use the global parameter to apply the tags to the DiffView.

To highlight the name "Tom" in every field, the implementation would look like this:

```kotlin
@ViewFilter("Name Highlighter", fields = ["quote"], alwaysShow = ["author_id"], global = true)
override fun tag(field: String, value: String): Map<String, Tag> = mapOf("Tom" to Tag("NAME", Color.blue))
```

In this example:

+ Only the quote field is passed to the tag function.
+ The word "Tom" is mapped to the Tag with the identifier `NAME` and the color blue.
+ Every occurrence of "Tom" in the quote field will be highlighted in blue.

## Drawable

The Drawable interface allows you to extend the user interface of your application. Since UI extension capabilities vary
based on the UI framework in use, this interface does not include any pre-defined functions. However, when using the
standard UI implementation with Compose, the UI will invoke functions like:

````kotlin
fun composeContent(entries: List<Map<String, String>>): @Composable () -> Unit = {
    //Compose components
}
````

to extend the user interface.

**Important:** The Drawable interface and the exact structure of the function are required.

## Export plugin

To export the plugin, run the Gradle `jar` task:

```shell
./gradlew jar
```

For Windows use:

```shell
gradlew.bat jar
```

### A full example:

**PluginMain:**

```kotlin
@PluginOrder(3)
@RequiresData("demo_data")
@PluginMetadata("demo", author = "Author", version = "1.0.0", description = "A short description.")
class PluginMain : IPlugin, Taggable {

    override fun init(storageProvider: IStorageProvider) {
        println("Hello world from plugin.")
    }

    @ViewFilter("Name Highlighter", fields = ["quote"], alwaysShow = ["author_id"], global = true)
    override fun tag(field: String, value: String): Map<String, Tag> = mapOf("Tom" to Tag("NAME", Color.blue))
}
```

**build.gradle.kts**

```kotlin
plugins {
    kotlin("jvm") version "2.0.21"
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(files("libs/api-1.6.1.jar"))
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "PluginMain"
    }
}

kotlin {
    jvmToolchain(21)
}
```